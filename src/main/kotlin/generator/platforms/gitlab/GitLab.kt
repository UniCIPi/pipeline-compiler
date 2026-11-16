package fanci.compiler.generator.platforms.gitlab

import fanci.compiler.common.ProjectPath
import fanci.compiler.configurationModel.SubmoduleStrategy
import fanci.compiler.generator.DependencyManager
import fanci.compiler.generator.IPlatform
import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.FinishBuildTrigger
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.Parameter
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.PullRequestBranch
import fanci.compiler.pipeline.ScheduleTrigger
import fanci.compiler.pipeline.Trigger
import fanci.compiler.pipeline.VcsTrigger
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import java.io.File
import java.nio.file.Path
import kotlin.collections.plusAssign

/**
 * Staging directory of the published artifacts, inside the project directory as GitLab requires.
 * The artifact functions skip it when searching for files, so the staged files are never matched again.
 */
internal const val artifactDirectory = ".artifacts/"

/**
 * Variable identifying the schedule trigger which started a scheduled pipeline, see [GitLab.createScheduleRule].
 */
internal const val scheduleVariable = "__PIPELINE_GENERATOR_SCHEDULE"

/**
 * Regular expression matching every relative path, the translation of the pattern `**`.
 */
private const val anyPathRegex = ".*"

/**
 * POSIX shell functions handling artifact files with the TeamCity artifact path semantics.
 * Patterns are given as extended regular expressions created by [GitLab.toArtifactRegex],
 * so the functions need only `sh`, `find`, `sed`, `grep` and `cp`, available in every common image.
 * They succeed when nothing matches, also under `set -o pipefail` used by the GitLab runner.
 *
 * `artifact_list` prints the files under the base directory `$1` whose path relative to it matches `$2`,
 * except the files in the staging directory [artifactDirectory].
 *
 * `copy_artifact` copies the files under the base directory `$1` matching `$2` to the absolute destination `$3`
 * and prints the copied paths. The base is dropped from the copied paths, the matched paths are kept.
 * When `$4`, the pattern without wildcards, denotes a directory, the content of the directory is copied.
 */
private val copyArtifactFunction = """
    artifact_list() (
      cd "${'$'}{1:-.}" 2>/dev/null || exit 0
      find . -path ./${artifactDirectory.trimEnd('/')} -prune -o \( -type f -o -type l \) -print | sed 's|^\./||' |
        { grep -E -- "^(${'$'}2)${'$'}" || [ ${'$'}? -eq 1 ]; } | while IFS= read -r f; do
          if [ -f "${'$'}f" ]; then printf '%s\n' "${'$'}f"; fi
        done
    )
    copy_artifact() (
      cd "${'$'}{1:-.}" 2>/dev/null || exit 0
      if [ -n "${'$'}4" ] && [ -d "${'$'}4" ]; then cd "${'$'}4" && set -- . '$anyPathRegex' "${'$'}3"; fi
      artifact_list . "${'$'}2" | while IFS= read -r f; do
        mkdir -p "${'$'}3/${'$'}(dirname "${'$'}f")" && cp "${'$'}f" "${'$'}3/${'$'}f" && printf '%s\n' "${'$'}f"
      done
    )
""".trimIndent()

class GitLab(override val dependencyManager: DependencyManager, envFile: File?) : IPlatform {
    val environmentHelper: GitLabEnvironmentHelper = GitLabEnvironmentHelper(envFile)

    override val platformParameters: Map<String, String> = mapOf(
        IPlatform.StandardParameter.FANCI_BUILD_BRANCH.name to "CI_COMMIT_REF_NAME",
        IPlatform.StandardParameter.FANCI_BUILD_NUMBER.name to "CI_PIPELINE_ID",
        IPlatform.StandardParameter.FANCI_BUILD_COUNTER.name to "CI_PIPELINE_ID", // Counter is not implemented in GitLab
        IPlatform.StandardParameter.FANCI_DEFAULT_BRANCH.name to "CI_DEFAULT_BRANCH",
        IPlatform.StandardParameter.FANCI_COMPONENT_URL.name to "CI_REPOSITORY_URL",
        IPlatform.StandardParameter.FANCI_CHECKOUT_DIR.name to "CI_PROJECT_DIR",
    )

    override fun generatePipeline(pipelineSettings: PipelineSettings, targetDirectory: Path): String {
        // Generate into a separate directory per component
        val componentDir = targetDirectory.resolve(normalizeDirName(pipelineSettings.componentName))
        componentDir.toFile().mkdirs()

        val filePath = componentDir.resolve(".gitlab-ci.yml")
        val file = filePath.toFile()

        val yamlMapper = YAMLMapper()
        yamlMapper.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
        // Prefer literal block style for multiline strings ("|" in YAML)
        yamlMapper.configure(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true)
        // Do not split long lines (keep rules:if expressions on a single line)
        yamlMapper.configure(YAMLGenerator.Feature.SPLIT_LINES, false)
        val yamlData = mutableMapOf<String, Any>()

        // Keep GitLab stages minimal; the DAG is expressed via needs
        yamlData["stages"] = listOf("default")

        val variablesMap = generateSubmoduleStrategy(pipelineSettings)
        if (variablesMap.isNotEmpty()) {
            yamlData["variables"] = variablesMap
        }

        // Compute external dependencies aggregated per external component and create trigger jobs
        val externalAgg = computeExternalDependencyAggregation(pipelineSettings)

        // Map build configuration name -> list of additional needs referencing aggregated trigger jobs
        externalNeedsForBuild = buildExternalNeedsForBuildMap(pipelineSettings, externalAgg)

        validateParameterVariableNames(pipelineSettings)

        // Generate build configurations settings
        pipelineSettings.buildConfigurations.forEach { bc ->
            val jobs = generateJobs(bc, pipelineSettings)
            yamlData.putAll(jobs)
        }

        // Add trigger jobs to YAML
        externalAgg.values.forEach { agg ->
            yamlData[agg.triggerJobName] = agg.job
        }

        // Minimize quotes so YAML doesn't over-escape expressions
        yamlMapper.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
        file.writeBytes(yamlMapper.writeValueAsBytes(yamlData))

        return filePath.toString()
    }

    override fun parameterGetFormat(parameterName: String): String {
        val parameterNameSanitized = parameterName.replace(".", "_")
        if (ProjectPath.isFullPath(parameterNameSanitized)) {
            return "${'$'}{${computeParamFullName("", parameterNameSanitized, dependencyManager.currentComponent)}}"
        }
        // Produce ${VARNAME} for GitLab
        return "${'$'}{${parameterNameSanitized}}"
    }

    override fun parameterSetFormat(parameterName: String, value: String): String {
        // GitLab recommends exporting to a dotenv file to share across jobs; it outputs a shell assignment
        // The generator publishes artifacts:reports:dotenv at each build configuration
        return "echo \"${parameterName}=${value}\" >> build.env"
    }

    fun generateRules(buildConfiguration: IBuildConfiguration, pipelineSettings: PipelineSettings): List<Map<String, Any>> {
        val collectedRules = collectRulesRecursive(buildConfiguration, pipelineSettings, mutableSetOf())
        val rules = collectedRules.distinct().toMutableList()

        // The job runs in pipelines started by hand (Run pipeline button or API) also when no trigger matches,
        // and in no other pipeline. First match wins, so these rules must be last.
        rules += mapOf(
            "if" to "\$CI_PIPELINE_SOURCE == 'web' || \$CI_PIPELINE_SOURCE == 'api'",
            "when" to "on_success"
        )
        rules += mapOf("when" to "never")
        return rules
    }

    private fun collectRulesRecursive(
        bc: IBuildConfiguration,
        settings: PipelineSettings,
        visited: MutableSet<String>
    ): List<Map<String, Any>> {
        if (!visited.add(bc.name)) return emptyList()

        val rules = mutableListOf<Map<String, Any>>()

        rules += createRulesFromExternalDependents(bc.name)
        rules += getRulesFromLocalDependents(settings, bc, visited)
        rules += createRulesFromTriggers(bc, settings)

        return rules
    }

    // ... existing code ...
    private fun createRulesFromTriggers(bc: IBuildConfiguration, settings: PipelineSettings): MutableList<Map<String, Any>> {
        val rules = mutableListOf<Map<String, Any>>()

        // Unnamed schedule triggers are numbered, so reordering the named ones does not change their identifiers
        var unnamedScheduleIndex = 0
        val scheduleIds = mutableSetOf<String>()
        bc.triggers.forEach { trigger ->
            if (trigger is VcsTrigger) {
                if (watchesAnotherComponent(trigger, settings)) {
                    reportOmittedVcsTrigger(trigger, bc.name, settings)
                } else {
                    rules.addAll(createVcsRules(trigger, bc.name, settings))
                }
            } else if (trigger is ScheduleTrigger) {
                val scheduleId = "${bc.name}-${trigger.name ?: ++unnamedScheduleIndex}"
                require(scheduleIds.add(scheduleId)) {
                    "[${bc.name}] Schedule triggers share the identifier '$scheduleId'. " +
                            "Rename the schedule trigger named '${scheduleId.removePrefix("${bc.name}-")}'."
                }
                rules.add(createScheduleRule(trigger, bc.name, scheduleId))
            } else if (trigger is FinishBuildTrigger) {
                println("WARNING: [${bc.name}] Finish build trigger is not supported by GitLab CI.")
            }
        }
        return rules
    }

    /**
     * A GitLab pipeline is created only by events in the repository holding its definition, so a VCS trigger
     * can watch only the repository of the component owning the build configuration.
     */
    private fun watchesAnotherComponent(trigger: VcsTrigger, settings: PipelineSettings): Boolean =
        trigger.watchedComponent != null &&
                !ProjectPath.equalsCanonical(trigger.watchedComponent, settings.componentName)

    private fun reportOmittedVcsTrigger(trigger: VcsTrigger, bcName: String, settings: PipelineSettings) {
        // Rules are collected again for every dependency of the build configuration, report the trigger once
        if (reportedOmittedTriggers.add("${settings.componentName}::$bcName::${trigger.watchedComponent}")) {
            println("WARNING: [${bcName}] VCS trigger watching the component '${trigger.watchedComponent}' is omitted. " +
                    "GitLab CI can watch only the repository of the component owning the build configuration " +
                    "('${settings.componentName}').")
        }
    }

    /**
     * Creates the rule of a schedule trigger.
     *
     * GitLab schedules are defined outside the pipeline definition, so the rule cannot tell which schedule
     * started the pipeline. Every schedule trigger is therefore identified by the value [scheduleId]
     * of the variable [scheduleVariable]: `<build configuration>-<name>` for a named trigger,
     * `<build configuration>-<n>` for the n-th unnamed trigger of the build configuration.
     * The schedule created in GitLab has to set this variable, otherwise it starts no job.
     * The target branch is given by the schedule itself, so the branch filters of the trigger only tell
     * which schedules to create and do not restrict the rule.
     *
     * TODO Create a script creating the schedules through the GitLab API instead of printing them.
     * TODO Discuss matching the schedule description (`CI_PIPELINE_SCHEDULE_DESCRIPTION`, GitLab 17.8+) instead of
     *  [scheduleVariable]. It saves the variable setup, but a retitled schedule gets disconnected. Matching the
     *  identifier as a prefix of the description keeps it readable, the variable can stay as a fallback for older GitLab.
     */
    fun createScheduleRule(trigger: ScheduleTrigger, bcName: String, scheduleId: String): Map<String, Any> {
        val condition = "\$CI_PIPELINE_SOURCE == 'schedule' && \$$scheduleVariable == '$scheduleId'"

        // Rules are collected again for every dependency of the build configuration, report the schedule once
        if (reportedSchedules.add("${dependencyManager.currentComponent}::$scheduleId")) {
            val targetBranches = trigger.includeBranches.ifEmpty { listOf("<any branch>") }
            println("INFO: [${bcName}] Manual action required: Create a schedule in GitLab -> Build -> Pipeline schedules.")
            println("      Cron: '${trigger.cron}'")
            println("      Target branch: ${targetBranches.joinToString(", ")}" +
                    if (targetBranches.size > 1) " (one schedule per branch)" else "")
            if (trigger.excludeBranches.isNotEmpty()) {
                println("      Excluded branches: ${trigger.excludeBranches.joinToString(", ")}")
            }
            println("      Variable: $scheduleVariable = $scheduleId")
        }

        return createRule(condition, "on_success", emptyList(), createVariablesForTrigger(bcName, trigger))
    }

    private fun createVcsRules(trigger: VcsTrigger, bcName: String, settings: PipelineSettings): List<Map<String, Any>> {
        val rules = mutableListOf<Map<String, Any>>()

        // Split patterns into Merge requests, Tags and Branches
        val (mergeRequestIncludes, refIncludes) = trigger.includeBranches.partition { PullRequestBranch.isPullRequestPattern(it) }
        val (mergeRequestExcludes, refExcludes) = trigger.excludeBranches.partition { PullRequestBranch.isPullRequestPattern(it) }
        val (tagIncludes, branchIncludes) = refIncludes.partition { it.startsWith("tags/") }
        val (tagExcludes, branchExcludes) = refExcludes.partition { it.startsWith("tags/") }

        val cleanedTagIncludes = tagIncludes.map { it.removePrefix("tags/") }
        val cleanedTagExcludes = tagExcludes.map { it.removePrefix("tags/") }

        val changes = pathsFromRules(trigger)

        // 1. Check for "Run on Everything" wildcard cases
        val isWildcardOnly = refIncludes.singleOrNull() == "*"
        val noFilters = trigger.includeBranches.isEmpty() && trigger.excludeBranches.isEmpty()

        if (noFilters || (isWildcardOnly && trigger.excludeBranches.isEmpty())) {
            rules.add(createRule("\$CI_PIPELINE_SOURCE == 'push'", "on_success", changes, createVariablesForTrigger(bcName, trigger)))
            // A trigger that does not restrict branches covers merge requests as well
            rules.addAll(
                createMergeRequestRules(trigger, bcName, settings, listOf(PullRequestBranch.ALL), "on_success", true)
            )
            return rules
        }

        // 2. Generate Exclusion Rules (when: never) - High Priority
        rules.addAll(createMergeRequestRules(trigger, bcName, settings, mergeRequestExcludes, "never", false))

        if (branchExcludes.isNotEmpty()) {
            buildRefRegex(branchExcludes, emptyList())?.let { regex ->
                rules.add(
                    createRule(
                        "\$CI_PIPELINE_SOURCE == 'push' && \$CI_COMMIT_BRANCH =~ /$regex/",
                        "never",
                        changes,
                        createVariablesForTrigger(bcName, trigger)
                    )
                )
            }
        }

        if (cleanedTagExcludes.isNotEmpty()) {
            buildRefRegex(cleanedTagExcludes, emptyList())?.let { regex ->
                rules.add(
                    createRule(
                        "\$CI_PIPELINE_SOURCE == 'push' && \$CI_COMMIT_TAG && \$CI_COMMIT_TAG =~ /$regex/",
                        "never",
                        changes,
                        createVariablesForTrigger(bcName, trigger)
                    )
                )
            }
        }

        // 3. Generate Inclusion Rules (branches)
        val branchCondition = resolveIncludeCondition(
            patterns = branchIncludes,
            variable = "\$CI_COMMIT_BRANCH",
            requireVariableExistence = false // Branch usually doesn't need explicit non-null check in regex context per original logic
        )
        if (branchCondition != null) {
            rules.add(createRule("\$CI_PIPELINE_SOURCE == 'push' && $branchCondition", "on_success", changes, createVariablesForTrigger(bcName, trigger)))
        }

        // 4. Generate Inclusion Rules (tags)
        val tagCondition = resolveIncludeCondition(
            patterns = cleanedTagIncludes,
            variable = "\$CI_COMMIT_TAG",
            requireVariableExistence = true // Tags often explicitly check existence before regex
        )
        if (tagCondition != null) {
            rules.add(createRule("\$CI_PIPELINE_SOURCE == 'push' && $tagCondition", "on_success", changes, createVariablesForTrigger(bcName, trigger)))
        }

        // 5. Generate Inclusion Rules (merge requests)
        rules.addAll(createMergeRequestRules(trigger, bcName, settings, mergeRequestIncludes, "on_success", true))

        return rules
    }

    /**
     * Creates rules for merge request pipelines from the `mr/` branch patterns of a trigger.
     *
     * GitLab needs no build feature for merge requests; a merge request pipeline is recognized by the
     * `merge_request_event` pipeline source. The source and target branch filters of the component
     * `pull-request` section are appended as additional conditions of the rule.
     *
     * @param patterns `mr/` patterns of the trigger. A wildcard selector covers all merge requests,
     *                 `mr/<iid>` narrows the rule down to concrete merge requests.
     * @param applyPullRequestFilters whether the component source/target branch filters restrict the rule.
     *                 Exclusion rules must not be restricted by them, otherwise merge requests outside of
     *                 the filtered scope would not be excluded.
     */
    private fun createMergeRequestRules(
        trigger: VcsTrigger,
        bcName: String,
        settings: PipelineSettings,
        patterns: List<String>,
        whenClause: String,
        applyPullRequestFilters: Boolean
    ): List<Map<String, Any>> {
        val configuration = settings.pullRequestConfiguration
        if (patterns.isEmpty() || !configuration.enabled) {
            return emptyList()
        }

        val conditions = mutableListOf("\$CI_PIPELINE_SOURCE == 'merge_request_event'")
        conditions += refNameConditions(
            "\$CI_MERGE_REQUEST_IID",
            patterns.map { PullRequestBranch.selectorOf(it) },
            emptyList()
        )
        if (applyPullRequestFilters) {
            conditions += refNameConditions(
                "\$CI_MERGE_REQUEST_SOURCE_BRANCH_NAME",
                configuration.includeSourceBranches,
                configuration.excludeSourceBranches
            )
            conditions += refNameConditions(
                "\$CI_MERGE_REQUEST_TARGET_BRANCH_NAME",
                configuration.includeTargetBranches,
                configuration.excludeTargetBranches
            )
        }

        return listOf(
            createRule(
                conditions.joinToString(" && "),
                whenClause,
                pathsFromRules(trigger),
                createVariablesForTrigger(bcName, trigger)
            )
        )
    }

    /**
     * Builds the conditions restricting a GitLab predefined variable to the given include/exclude patterns.
     * Patterns that do not restrict anything (`*`) produce no condition.
     */
    private fun refNameConditions(variable: String, include: List<String>, exclude: List<String>): List<String> {
        val conditions = mutableListOf<String>()
        resolveIncludeCondition(include, variable, requireVariableExistence = false)
            // A bare variable is only an existence check, which restricts nothing here
            ?.takeIf { it != variable }
            ?.let { conditions.add(it) }
        if (exclude.isNotEmpty()) {
            buildRefRegex(exclude, emptyList())?.let { conditions.add("$variable !~ /$it/") }
        }
        return conditions
    }

    /**
     * Helper to build the conditional string for Includes.
     * Determines if we should use existence check (*), literal equality, or regex.
     */
    private fun resolveIncludeCondition(
        patterns: List<String>,
        variable: String,
        requireVariableExistence: Boolean
    ): String? {
        if (patterns.isEmpty()) return null

        fun isAllToken(s: String): Boolean = s.trim() == "*" || s.trim() == "**"
        fun isLiteral(s: String): Boolean = !(s.contains('*') || s.contains('?') || s.contains('[') || s.contains(']'))

        val hasAllToken = patterns.any { isAllToken(it) }

        return when {
            hasAllToken -> variable
            patterns.size == 1 && isLiteral(patterns[0]) -> "$variable == '${patterns[0]}'"
            else -> {
                val regex = buildRefRegex(patterns, emptyList()) ?: return null
                if (requireVariableExistence) "$variable && $variable =~ /$regex/" else "$variable =~ /$regex/"
            }
        }
    }

    private fun createRule(condition: String, whenClause: String, changes: List<String>, variables: Map<String, String>): Map<String, Any> {
        val rule = mutableMapOf<String, Any>(
            "if" to condition,
            "when" to whenClause,
        )
        if (changes.isNotEmpty()) {
            rule["changes"] = changes
        }
        if (variables.isNotEmpty()) {
            rule["variables"] = variables
        }
        return rule
    }

    // ... existing code ...
    private fun createVariablesForTrigger(bcName: String, trigger: Trigger): Map<String, String> {
        val variables = mutableMapOf<String, String>()
        trigger.parameters.forEach { param ->
            variables[computeParamFullName(bcName, param.name, dependencyManager.currentComponent)] = param.value
        }
        return variables
    }

    /**
     * Inherit rules from all local dependents
     * A build configuration (B) must run if any other build configuration (A) depends on it and A runs.
     * So B inherits rules from A. (Dependency direction: A -> B, Rule inheritance: A -> B)
     */
    private fun getRulesFromLocalDependents(
        settings: PipelineSettings,
        bc: IBuildConfiguration,
        visited: MutableSet<String>
    ): MutableList<Map<String, Any>> {
        val rules: MutableList<Map<String, Any>> = mutableListOf()
        val dependents = settings.buildConfigurations.filter { otherBc ->
            otherBc.dependencies.any { dep ->
                val depName = dep.buildConfigurationName
                val targetLocalName = if (ProjectPath.isFullPath(depName)) {
                    val targetProject = dependencyManager.getProject(depName)
                    if (targetProject == settings.componentName) {
                        dependencyManager.getBuildType(depName)
                    } else {
                        null
                    }
                } else {
                    depName
                }
                targetLocalName == bc.name
            }
        }

        dependents.forEach { dependentBc ->
            rules.addAll(collectRulesRecursive(dependentBc, settings, visited))
        }
        return rules
    }

    /**
     * Create trigger rules from external components that depend on this build.
     * If component B depends on component A's build configuration [buildConfigurationName], it triggers the pipeline of A with STARTED_BY=toEnvToken(B.name),
     * allows this job to run (CI_PIPELINE_SOURCE == 'pipeline').
     */
    private fun createRulesFromExternalDependents(buildConfigurationName: String): MutableList<Map<String, Any>> {
        val rules: MutableList<Map<String, Any>> = mutableListOf()
        val externals = dependencyManager.getExternalDependentsOf(buildConfigurationName)
        externals.forEach { extComponent ->
            val token = toEnvToken(extComponent)
            rules += mapOf(
                "if" to "\$CI_PIPELINE_SOURCE == 'pipeline' && \$STARTED_BY == '${token}'",
                "when" to "on_success"
            )
        }
        return rules
    }

    fun generateArtifacts(buildConfiguration: IBuildConfiguration): Map<String, Any> {
        val artifactsMap = mutableMapOf<String, Any>()
        // Only jobs staging files get paths - GitLab fails the upload when no file matches
        if (stagesFiles(buildConfiguration)) {
            // Publish under namespaced directory .artifacts/<BC>/** so consumers can cleanly scope and clean
            artifactsMap["paths"] = listOf("${getArtifactDirectoryOfBuild(buildConfiguration.name)}**")
        }
        artifactsMap["when"] = "always"
        return artifactsMap
    }

    fun generateSubmoduleStrategy(pipelineSettings: PipelineSettings): Map<String, String> {
        val strategyMap = mutableMapOf<String, String>()
        when(pipelineSettings.submoduleStrategy) {
            SubmoduleStrategy.recursive -> strategyMap["GIT_SUBMODULE_STRATEGY"] = "recursive"
            SubmoduleStrategy.shallow -> strategyMap["GIT_SUBMODULE_STRATEGY"] = "shallow"
            SubmoduleStrategy.none -> {
                strategyMap["GIT_SUBMODULE_STRATEGY"] = "none"
                return strategyMap
            }
        }
        strategyMap["GIT_SUBMODULE_DEPTH"] = "1"
        strategyMap["GIT_STRATEGY"] = "clone" // clean clone, fetch doesnt remove untracked files, none doesnt clone
        strategyMap["GIT_SUBMODULE_FORCE_HTTPS"] = "false" // default value, left here for easy override
        return strategyMap
    }

    // -------------- Helpers --------------

    /**
     * True if the job copies files into its staging directory: it publishes artifacts,
     * or it is a composite consuming artifacts of its dependencies.
     */
    private fun stagesFiles(buildConfiguration: IBuildConfiguration): Boolean {
        val isComposite = buildConfiguration.type == IBuildConfiguration.Type.COMPOSITE
        return buildConfiguration.publishArtifacts.isNotEmpty() ||
            (isComposite && buildConfiguration.dependencies.any { !it.artifacts.isNullOrEmpty() })
    }

    private fun normalizeDirName(componentName: String): String =
        componentName.replace("::", "_").replace("/", "_")

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun getArtifactDirectoryOfBuild(buildConfigurationName: String): String {
        return "$artifactDirectory${sanitizeFileName(buildConfigurationName)}/"
    }

    /**
     * Splits an artifact source path into the base directory and the pattern relative to it.
     * The base consists of the whole segments before the first segment containing a wildcard,
     * or of all segments except the last one when the path has no wildcard.
     * Only the part of a path matched by the pattern is kept when copying, as in TeamCity.
     */
    private fun splitArtifactSource(source: String): Pair<String, String> {
        val segments = source.trimEnd('/').split('/')
        val firstWildcard = segments.indexOfFirst { it.contains('*') || it.contains('?') }
        val baseSize = if (firstWildcard == -1) segments.size - 1 else firstWildcard
        return segments.take(baseSize).joinToString("/") to segments.drop(baseSize).joinToString("/")
    }

    /**
     * Creates the shell call of `copy_artifact` copying the files matching [source] under [sourceRoot] to [destination].
     */
    private fun copyArtifactCall(sourceRoot: String, source: String, destination: String): String {
        val (base, pattern) = splitArtifactSource(source)
        val literal = if (hasWildcard(pattern)) "" else pattern
        return "copy_artifact \"$sourceRoot$base\" ${shellQuote(toArtifactRegex(pattern))} \"$destination\" ${shellQuote(literal)}"
    }

    private fun hasWildcard(pattern: String): Boolean = pattern.contains('*') || pattern.contains('?')

    /**
     * Translates an artifact path pattern to an extended regular expression matching the same relative paths.
     * `*` matches any part of a name, `?` one character of a name and the segment `**` any number of names.
     * As in TeamCity, the wildcards also match hidden names.
     */
    internal fun toArtifactRegex(pattern: String): String {
        val segments = pattern.split('/')
        return segments.withIndex().joinToString("") { (index, segment) ->
            val isLast = index == segments.lastIndex
            when {
                segment == "**" && isLast -> anyPathRegex
                segment == "**" -> "([^/]*/)*"
                else -> segmentRegex(segment) + if (isLast) "" else "/"
            }
        }
    }

    /**
     * Translates one segment of an artifact path pattern to an extended regular expression.
     */
    private fun segmentRegex(segment: String): String = segment.map { char ->
        when (char) {
            '*' -> "[^/]*"
            '?' -> "[^/]"
            in ".[]()+{}|^$\\" -> "\\$char"
            else -> char.toString()
        }
    }.joinToString("")

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Convert any string to a safe environment token: only [A-Za-z0-9_], everything else becomes underscore.
     */
    private fun toEnvToken(s: String): String = s.replace(Regex("[^A-Za-z0-9_]"), "_")

    /**
     * Compute the environment variable key for a parameter in a given build configuration.
     *
     * Rules:
     *  - For parameters that reference another build via FQN (contain ::), use
     *    "<ReferencedBuildName>_<ParamName>", both tokens sanitized to env style.
     *    Example: "::Project::Build Configuration::Parameter"
     *    becomes "Project_Build_configuration_Parameter".
     *  - Otherwise, fallback to "<ThisBuildName>_<ParamName>" (sanitized).
     */
    private fun computeParamFullName(bcName: String, paramName: String, currentComponent: ProjectPath): String {
        if (ProjectPath.isFullPath(paramName)) {
            return toEnvToken(paramName)
        }
        val fullParamName = currentComponent.append(bcName).append(paramName)
        return toEnvToken(fullParamName.toString())
    }

    /**
     * Local shell variable name used within the job scripts (without project/build prefixes).
     * For FQN params, use only the last segment (param name). Otherwise use the whole name.
     */
    private fun computeLocalVarName(paramName: String): String {
        return if (ProjectPath.isFullPath(paramName)) {
            val fullPath = ProjectPath.parse(paramName)
            toEnvToken(fullPath.last())
        } else {
            toEnvToken(paramName)
        }
    }
    
    /**
     * Compile include/exclude wildcards to a single anchored regex, or return null if includes are empty and excludes cover everything.
     */
    private fun buildRefRegex(include: List<String>, exclude: List<String>): String? {
        fun globToRegex(glob: String): String = glob
            .replace(".", "\\.")
            .replace("+", "\\+")
            .replace("?", ".")
            .replace("*", ".*")
            .replace("/", "\\/")
            .let { "^${it}$" }

        val incPart = if (include.isEmpty()) ".*" else include.joinToString("|") { globToRegex(it).removePrefix("^").removeSuffix("$") }
        val excPart = if (exclude.isEmpty()) null else exclude.joinToString("|") { globToRegex(it).removePrefix("^").removeSuffix("$") }

        return if (excPart == null) {
            "^(${incPart})$"
        } else {
            // Negative lookahead for excluded refs
            "^(?!(${excPart}))(${incPart})$"
        }
    }

    /**
     * Collects path patterns for GitLab rules:changes from the VCS trigger.
     * Excludes are not directly supported by GitLab's 'changes' list; we include only includePaths.
     */
    private fun pathsFromRules(trig: VcsTrigger): List<String> {
        return trig.includePaths.distinct()
    }

    /**
     * Fails when two different parameters map to the same environment variable.
     * The mapping replaces every character outside [A-Za-z0-9_] by an underscore, so e.g. "P-1" and "P_1"
     * would silently share one variable.
     * Full names are checked across the whole component, local names within each build configuration.
     */
    private fun validateParameterVariableNames(pipelineSettings: PipelineSettings) {
        val currentComponent = dependencyManager.currentComponent
        val fullNames = mutableMapOf<String, MutableSet<String>>()
        pipelineSettings.buildConfigurations.forEach { bc ->
            val localNames = mutableMapOf<String, MutableSet<String>>()
            bc.parameters.forEach { p ->
                val original = if (ProjectPath.isFullPath(p.name)) p.name else currentComponent.append(bc.name).append(p.name).toString()
                fullNames.getOrPut(computeParamFullName(bc.name, p.name, currentComponent)) { mutableSetOf() } += original
                localNames.getOrPut(computeLocalVarName(p.name)) { mutableSetOf() } += p.name
            }
            localNames.filterValues { it.size > 1 }.forEach { (variable, names) ->
                throw IllegalArgumentException(
                    "[${bc.name}] Parameters ${names.joinToString(", ") { "'$it'" }} map to the same GitLab variable '$variable'."
                )
            }
        }
        fullNames.filterValues { it.size > 1 }.forEach { (variable, names) ->
            throw IllegalArgumentException(
                "Parameters ${names.joinToString(", ") { "'$it'" }} map to the same GitLab variable '$variable'."
            )
        }
    }

    private fun aliasParametersShell(bc: IBuildConfiguration): List<String> {
        val lines = mutableListOf<String>()
        bc.parameters.forEach { p ->
            val srcKey = computeParamFullName(bc.name, p.name, dependencyManager.currentComponent)
            val src = "${'$'}{${srcKey}}"
            val dst = computeLocalVarName(p.name)
            // Quoted, some sh implementations split the value of an export argument into words
            lines += "export ${dst}=\"${src}\""
        }
        return lines
    }

    private fun sourceDependencyDotenv(): List<String> = listOf(
        "# Load dotenv from dependencies if present",
        "if [ -f build.env ]; then set -a; . build.env; set +a; fi"
    )

    private fun generateJobs(bc: IBuildConfiguration, pipelineSettings: PipelineSettings): Map<String, GitLabJob> {
        val isMatrix = bc.buildMatrix.isNotEmpty()
        val isComposite = bc.type == IBuildConfiguration.Type.COMPOSITE
        // If matrix, the worker is named ":run", otherwise it keeps the original name
        val runJobName = if (isMatrix) "${bc.name}:run" else bc.name
        val runJob = GitLabJob(stage = "default")

        // --- 1. Render Build Steps & Docker Configuration ---
        val renderer = BuildStepsRenderer()
        val rendered = renderer.render(bc)
        rendered.jobImage?.let { img -> runJob.image = img }

        if (rendered.needsDinD) {
            val dindConfiguration = environmentHelper.getDindConfiguration()
            if (dindConfiguration == null) {
                println("WARNING: Docker in docker is needed for build configuration ${bc.name}, but no configuration was found in the environment file.")
            } else {
                runJob.tags = dindConfiguration.tags
                if (dindConfiguration.dockerHost != null) {
                    runJob.variables += mapOf("DOCKER_HOST" to dindConfiguration.dockerHost)
                }
                if (dindConfiguration.dockerCertDir != null) {
                    runJob.variables += mapOf("DOCKER_TLS_CERTDIR" to dindConfiguration.dockerCertDir)
                }
            }
            runJob.services = listOf("docker:dind")
        } else {
            if (environmentHelper.areTagsDefined()) {
                runJob.tags = environmentHelper.getTags()
            }
        }

        // --- 2. Matrix Configuration ---
        if (isMatrix) {
            runJob.parallel = mapOf("matrix" to listOf(bc.buildMatrix))
        }

        // --- 3. Variables & Scripts ---
        val prefixedVars = bc.parameters.associate { p ->
            computeParamFullName(bc.name, p.name, dependencyManager.currentComponent) to p.value
        }
        if (prefixedVars.isNotEmpty()) {
            runJob.variables += prefixedVars
        }
        if (isComposite) {
            runJob.variables += mapOf("GIT_CHECKOUT" to "none")
        }
        val before = mutableListOf<String>()
        before += aliasParametersShell(bc)

        if (bc.freeDiskSpaceGb > 0) {
            before += """
                echo "Checking available disk space... Required: ${bc.freeDiskSpaceGb} GB"
                available_kb=${'$'}(df -P -k . | tail -1 | awk '{print ${'$'}4}')
                required_kb=${'$'}((${bc.freeDiskSpaceGb} * 1024 * 1024))
                if [ "${'$'}available_kb" -lt "${'$'}required_kb" ]; then
                    echo "Error: Not enough disk space. Available: ${'$'}((${'$'}available_kb / 1024 / 1024)) GB"
                    exit 1
                fi
            """.trimIndent()
        }

        before.addAll(sourceDependencyDotenv())
        before += generateDependencyArtifactsCopy(bc)
        runJob.beforeScript = before

            // For Composite builds we must NOT emit a script section
        if (rendered.script.isNotEmpty()) {
            runJob.script = rendered.script
        } else if (isComposite) {
            runJob.script = listOf("echo \"Aggregating results\"")
        }

        // --- 4. After Script & Artifacts ---
        val after = mutableListOf<String>(
            "if [ -f build.env ]; then set -a; . build.env; set +a; fi"
        )

        // A. Stage Artifacts
        // Composite builds stage the consumed artifacts already when copying them from dependencies.
        if (bc.publishArtifacts.isNotEmpty()) {
            // Regular/Deployment: stage artifacts explicitly published by this build configuration
            val artifactPath = getArtifactDirectoryOfBuild(bc.name)
            after += "echo '# Staging published artifacts'"
            after += copyArtifactFunction
            bc.publishArtifacts.forEach { a ->
                after += copyArtifactCall("", a.source, "\$CI_PROJECT_DIR/$artifactPath${a.destination ?: ""}")
            }
        }

        // B. Export Parameters (Only for non-matrix jobs, per request)
        if (!isMatrix && bc.parameters.isNotEmpty()) {
            after += "echo '# Exporting parameters for ${bc.name}'"
            bc.parameters.forEach { p ->
                val full = computeParamFullName(bc.name, p.name, dependencyManager.currentComponent)
                val local = computeLocalVarName(p.name)
                after += """
                    echo "$full=${'$'}{$local}" >> build.env
                """.trimIndent()
            }
        }

        if (after.isNotEmpty()) {
            runJob.afterScript = after
        }

        // C. Configure Artifacts Object
        if (isComposite || bc.publishArtifacts.isNotEmpty() || bc.parameters.isNotEmpty() || bc.junitReports.isNotEmpty()) {
            val artifacts = generateArtifacts(bc).toMutableMap()
            val reports = mutableMapOf<String, Any>()
            // Add dotenv report only if we actually exported parameters
            if (bc.parameters.isNotEmpty()) {
                reports["dotenv"] = "build.env"
            }
            if (bc.junitReports.isNotEmpty()) {
                reports["junit"] = bc.junitReports.map { it.path }
            }
            if (reports.isNotEmpty()) {
                artifacts["reports"] = reports
            }
            runJob.artifacts = artifacts
        }

        // --- 5. Rules & Needs ---
        runJob.rules = generateRules(bc, pipelineSettings)

        val parentNeeds = getDependencyNeedsInCurrentComponent(pipelineSettings, bc)
        val extraExternalNeeds = externalNeedsForBuild[bc.name].orEmpty()
        val combinedNeeds = mutableListOf<Map<String, Any>>()
        if (parentNeeds.isNotEmpty()) combinedNeeds += parentNeeds
        if (extraExternalNeeds.isNotEmpty()) combinedNeeds += extraExternalNeeds
        if (combinedNeeds.isNotEmpty()) runJob.needs = combinedNeeds

        val result = mutableMapOf(runJobName to runJob)

        // --- 6. Aggregate Job (Matrix Only) ---
        if (isMatrix) {
            val aggJobName = bc.name
            val aggJob = GitLabJob(stage = "default")
            
            // Needs the Run job and its artifacts
            aggJob.needs = listOf(
                mapOf(
                    "job" to runJobName,
                    "artifacts" to true,
                    "optional" to false
                )
            )
            aggJob.tags = runJob.tags

            aggJob.script = listOf("echo 'Aggregating artifacts for ${bc.name}'")

            // Collect all artifacts produced by the matrix runs (already staged in namespace)
            if (stagesFiles(bc)) {
                aggJob.artifacts = mutableMapOf(
                    "paths" to listOf("${getArtifactDirectoryOfBuild(bc.name)}**"),
                    "when" to "always"
                )
            }
            aggJob.rules = generateRules(bc, pipelineSettings)

            result[aggJobName] = aggJob
        }

        return result
    }

    // region External upstream aggregation
    private data class ExternalAggregation(
        val externalComponent: String,
        val neededUpstreamJobs: Set<String>,
        val triggerJobName: String,
        val job: GitLabJob,
        val localTriggeringBuilds: Set<String>
    )

    // Precomputed mapping: local build name -> list of needs maps
    private var externalNeedsForBuild: Map<String, List<Map<String, Any>>> = emptyMap()

    /** Identifiers of the schedules already reported to the user, see [createScheduleRule]. */
    private val reportedSchedules = mutableSetOf<String>()
    private val reportedOmittedTriggers = mutableSetOf<String>()
    private fun computeExternalDependencyAggregation(pipelineSettings: PipelineSettings): Map<String, ExternalAggregation> {
        val currentComponent = pipelineSettings.componentName

        // Group dependencies by external component
        val externalDependencies = collectExternalDependencies(pipelineSettings, currentComponent)

        return externalDependencies.mapValues { (extComponent, data) ->
            createExternalAggregation(
                extComponent = extComponent,
                data = data,
                pipelineSettings = pipelineSettings
            )
        }
    }

    private data class ExternalDependencyData(
        val upstreamJobs: MutableSet<String> = mutableSetOf(),
        val localTriggeringBuilds: MutableSet<String> = mutableSetOf()
    )

    /** Collects dependencies on external components. Returns map["ExternalComponentName"] -> JobsInThatComponent */
    private fun collectExternalDependencies(
        pipelineSettings: PipelineSettings,
        currentComponent: String
    ): Map<String, ExternalDependencyData> {
        val externalDependencies = mutableMapOf<String, ExternalDependencyData>()

        pipelineSettings.buildConfigurations.forEach { bc ->
            bc.dependencies.forEach { dep ->
                val depName = dep.buildConfigurationName
                if (ProjectPath.isFullPath(depName)) {
                    val targetComponent = dependencyManager.getProject(depName)
                    if (targetComponent != currentComponent) {
                        val data = externalDependencies.getOrPut(targetComponent) { ExternalDependencyData() }
                        data.upstreamJobs += depName
                        data.localTriggeringBuilds += bc.name
                    }
                }
            }
        }

        return externalDependencies
    }

    private fun createExternalAggregation(
        extComponent: String,
        data: ExternalDependencyData,
        pipelineSettings: PipelineSettings
    ): ExternalAggregation {
        val triggeringLocal = data.localTriggeringBuilds.firstOrNull() ?: "External"

        // Resolve repository information
        val anyUpstreamJob = data.upstreamJobs.firstOrNull()
            ?: throw IllegalStateException("No upstream jobs collected for external component '$extComponent'")
        // We now store the full path, so we can look up the repo directly
        val repoInfo = dependencyManager.getRepositoryOf(anyUpstreamJob)

        // Get rules from triggering build configuration
        val rules = pipelineSettings.buildConfigurations
            .firstOrNull { it.name == triggeringLocal }
            ?.let { generateRules(it, pipelineSettings) }

        // Collect variables to propagate
        val variablesToPropagate = collectVariablesToPropagate(
            localBuildNames = data.localTriggeringBuilds,
            buildConfigurations = pipelineSettings.buildConfigurations
        )

        // Create GitLab trigger job
        val (jobName, job) = GitLabApi.createTriggerUpstreamDependencyJobMultiFromRepo(
            triggeredComponentName = extComponent,
            repositoryUrl = repoInfo.repositoryUrl,
            // Normalize the full upstream job names for GitLab using toEnvToken
            neededBuildJobs = data.upstreamJobs.map { toEnvToken(it) },
            defaultBranch = repoInfo.defaultBranch,
            startedByComponent = toEnvToken(pipelineSettings.componentName),
            tags = if (environmentHelper.areTagsDefined()) environmentHelper.getTags() else null,
            rules = rules,
            variablesFromEnv = variablesToPropagate
        )

        // Configure job to receive dotenv from triggering builds if needed
        if (variablesToPropagate.isNotEmpty() && data.localTriggeringBuilds.isNotEmpty()) {
            job.needs = data.localTriggeringBuilds.map { mapOf("job" to it, "artifacts" to true) }
        }

        return ExternalAggregation(
            externalComponent = extComponent,
            neededUpstreamJobs = data.upstreamJobs,
            triggerJobName = jobName,
            job = job,
            localTriggeringBuilds = data.localTriggeringBuilds
        )
    }

    private fun collectVariablesToPropagate(
        localBuildNames: Set<String>,
        buildConfigurations: List<IBuildConfiguration>
    ): List<Pair<String, String>> {
        val allLocalBCsByName = buildConfigurations.associateBy { it.name }
        val variablesToPropagate = mutableListOf<Pair<String, String>>()

        localBuildNames.forEach { localName ->
            val localBC = allLocalBCsByName[localName] ?: return@forEach

            // Helper to collect variable if it's not strictly local (has namespace)
            fun collectIfExternal(param: Parameter) {
                if (!ProjectPath.isFullPath(param.name) || ProjectPath.parse(param.name).withoutLast().withoutLast() == dependencyManager.currentComponent) {
                    return
                }

                val envKey = computeParamFullName(localName, param.name, dependencyManager.currentComponent)
                variablesToPropagate += envKey to param.value
            }

            // Check variables defined in parameters (job.variables)
            localBC.parameters.forEach { param ->
                collectIfExternal(param)
            }

            // Check variables defined in triggers (job.rules.variables)
            localBC.triggers.forEach { trigger ->
                trigger.parameters.forEach { param ->
                    collectIfExternal(param)
                }
            }
        }

        return variablesToPropagate.distinct()
    }

    private fun buildExternalNeedsForBuildMap(
        pipelineSettings: PipelineSettings,
        externalAgg: Map<String, ExternalAggregation>
    ): Map<String, List<Map<String, Any>>> {
        if (externalAgg.isEmpty()) return emptyMap()
        val res = mutableMapOf<String, MutableList<Map<String, Any>>>()
        pipelineSettings.buildConfigurations.forEach { bc ->
            val needsList = mutableListOf<Map<String, Any>>()

            // Only add needs for external components this build directly depends on,
            // and apply failureAction mapping to "optional".
            bc.dependencies.forEach { dep ->
                val dn = dep.buildConfigurationName
                if (!ProjectPath.isFullPath(dn)) return@forEach

                val targetComponent = dependencyManager.getProject(dn)
                val agg = externalAgg[targetComponent] ?: return@forEach

                val optionalNeed = when (dep.failureAction) {
                    Dependency.FailureAction.IGNORE,
                    Dependency.FailureAction.ADD_PROBLEM -> true

                    Dependency.FailureAction.FAIL -> false
                }

                val need = mapOf<String, Any>(
                    "job" to agg.triggerJobName,
                    "artifacts" to true,
                    "optional" to optionalNeed
                )
                if (!needsList.contains(need)) needsList += need
            }

            if (needsList.isNotEmpty()) res[bc.name] = needsList
        }
        return res
    }
    // endregion

    private fun getDependencyNeedsInCurrentComponent(
        pipelineSettings: PipelineSettings,
        bc: IBuildConfiguration
    ): List<Map<String, Any>> {
        val needs = mutableListOf<Map<String, Any>>()
        bc.dependencies.forEach { dep ->
            val depName = dep.buildConfigurationName
            // If dependency is fully-qualified, include it as a need only when it targets the same component.
            // External component dependencies are handled by the aggregated trigger job logic elsewhere.
            val targetName = if (ProjectPath.isFullPath(depName)) {
                val targetProject = dependencyManager.getProject(depName)
                if (targetProject == pipelineSettings.componentName) {
                    // Same component: use local build type name
                    dependencyManager.getBuildType(depName)
                } else {
                    return@forEach
                    // Different component: skip here; external trigger job adds appropriate needs.

                }
            } else {
                // Relative names are always local to the current component
                depName
            }
            val optionalNeed = when (dep.failureAction) {
                Dependency.FailureAction.IGNORE,
                Dependency.FailureAction.ADD_PROBLEM -> true

                Dependency.FailureAction.FAIL -> false
            }

            needs += mapOf<String, Any>(
                "job" to targetName,
                // Always download artifacts - the dotenv report carrying the dependency parameters is one of them
                "artifacts" to true,
                "optional" to optionalNeed
            )
        }
        return needs
    }

    /**
     * Generate shell lines to implement Dependency Artifacts copy phase per (s,d) mapping.
     * As in TeamCity, the job fails when a rule, or the archive path of an archive rule, matches no file,
     * after reporting all such rules of the dependency. Otherwise it fails when an archive contains
     * no file matching the path in the archive.
     */
    private fun generateDependencyArtifactsCopy(bc: IBuildConfiguration): List<String> {
        val artifactDependencies = bc.dependencies.filter { !it.artifacts.isNullOrEmpty() }
        if (artifactDependencies.isEmpty()) return emptyList()
        // Composite publishes the consumed artifacts, so it copies them directly into its staging directory
        val destinationRoot = if (bc.type == IBuildConfiguration.Type.COMPOSITE) {
            "\$CI_PROJECT_DIR/${getArtifactDirectoryOfBuild(bc.name)}"
        } else {
            "\$CI_PROJECT_DIR/"
        }
        val lines = mutableListOf("# Copy dependency artifacts per (s,d) with archive extraction", copyArtifactFunction)
        artifactDependencies.forEach { dep ->
            val buildName = if (ProjectPath.isFullPath(dep.buildConfigurationName)) {
                toEnvToken(dep.buildConfigurationName)
            } else {
                dep.buildConfigurationName
            }
            val srcBuildConfiguration = getArtifactDirectoryOfBuild(buildName)
            lines += "missing_artifacts=''"
            lines += "missing_extraction=''"
            dep.artifacts!!.forEach { art ->
                val dst = "$destinationRoot${art.destination ?: ""}"
                if (art.source.contains("!")) {
                    // Archive extraction: <packagePath>!<pathInArchive>, the path in the archive follows the same rule
                    val (pkg, inner) = art.source.split("!", limit = 2)
                    val (pkgBase, pkgPattern) = splitArtifactSource(pkg)
                    val pkgDirectory = "$srcBuildConfiguration$pkgBase"
                    val pkgPrefix = if (pkgBase.isEmpty()) "" else "$pkgBase/"
                    lines += "packages=${'$'}(artifact_list \"$pkgDirectory\" ${shellQuote(toArtifactRegex(pkgPattern))})"
                    lines += "if [ -z \"${'$'}packages\" ]; then missing_artifacts=\"${'$'}missing_artifacts,\"${shellQuote(pkg)}; fi"
                    // Prints the archives containing no matching file
                    lines += """
                        unmatched=${'$'}(printf '%s\n' "${'$'}packages" | while IFS= read -r f; do
                          if [ -z "${'$'}f" ]; then continue; fi
                          tmpdir=${'$'}(mktemp -d)
                          case "${'$'}f" in
                            *.zip|*.jar) unzip -qo "$pkgDirectory/${'$'}f" -d "${'$'}tmpdir" ;;
                            *.tar.gz|*.tgz|*.tar) tar -xpf "$pkgDirectory/${'$'}f" -C "${'$'}tmpdir" ;;
                          esac
                          copied=${'$'}(${copyArtifactCall("${'$'}tmpdir/", inner, dst)})
                          rm -rf "${'$'}tmpdir"
                          if [ -z "${'$'}copied" ]; then printf '%s\n' ${shellQuote(pkgPrefix)}"${'$'}f"; fi
                        done)
                        if [ -n "${'$'}unmatched" ] && [ -z "${'$'}missing_extraction" ]; then
                          missing_extraction="No files matched for extraction patterns ["${shellQuote(inner)}"] while unpacking [${'$'}(printf '%s\n' "${'$'}unmatched" | head -n 1)]"
                        fi
                    """.trimIndent()
                } else {
                    lines += "copied=${'$'}(${copyArtifactCall(srcBuildConfiguration, art.source, dst)})"
                    lines += "if [ -z \"${'$'}copied\" ]; then missing_artifacts=\"${'$'}missing_artifacts,\"${shellQuote(art.source)}; fi"
                }
            }
            lines += "if [ -n \"${'$'}missing_artifacts\" ]; then " +
                "echo \"No files matched for patterns \\\"${'$'}{missing_artifacts#,}\\\" from \"${shellQuote(dep.buildConfigurationName)}; exit 1; fi"
            lines += "if [ -n \"${'$'}missing_extraction\" ]; then " +
                "echo \"${'$'}missing_extraction from \"${shellQuote(dep.buildConfigurationName)}; exit 1; fi"
        }
        return lines
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class GitLabJob(
        var stage: String? = null,
        var image: String? = null,
        var tags: List<String>? = null,
        var variables: Map<String, String> = emptyMap(),
        var services: List<String>? = null,
        var beforeScript: List<String>? = null,
        var script: List<String>? = null,
        var afterScript: List<String>? = null,
        var rules: List<Map<String, Any>>? = null,
        var artifacts: MutableMap<String, Any>? = null,
        var needs: List<Any>? = null,
        var trigger: Map<String, Any>? = null,
        var parallel: Map<String, Any>? = null
    )
}