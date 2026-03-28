package com.bringauto.pipelinecompiler.generator.platforms.gitlab

import com.bringauto.pipelinecompiler.common.ProjectPath
import com.bringauto.pipelinecompiler.configurationModel.SubmoduleStrategy
import com.bringauto.pipelinecompiler.generator.DependencyManager
import com.bringauto.pipelinecompiler.generator.IPlatform
import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.pipeline.FinishBuildTrigger
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.Parameter
import com.bringauto.pipelinecompiler.pipeline.PipelineSettings
import com.bringauto.pipelinecompiler.pipeline.ScheduleTrigger
import com.bringauto.pipelinecompiler.pipeline.Trigger
import com.bringauto.pipelinecompiler.pipeline.VcsTrigger
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import java.io.File
import java.nio.file.Path
import kotlin.collections.plusAssign

private const val artifactDirectory = "_artifacts/"

class GitLab(override val dependencyManager: DependencyManager, envFile: File?) : IPlatform {
    val environmentHelper: GitLabEnvironmentHelper = GitLabEnvironmentHelper(envFile)

    override val platformParameters: Map<String, String> = mapOf(
        IPlatform.StandardParameter.PIPELINE_BUILD_BRANCH.name to "CI_COMMIT_REF_NAME",
        IPlatform.StandardParameter.PIPELINE_BUILD_NUMBER.name to "CI_PIPELINE_ID",
        IPlatform.StandardParameter.PIPELINE_BUILD_COUNTER.name to "CI_PIPELINE_ID", // Counter is not implemented in GitLab
        IPlatform.StandardParameter.PIPELINE_DEFAULT_BRANCH.name to "CI_DEFAULT_BRANCH",
        IPlatform.StandardParameter.PIPELINE_COMPONENT_URL.name to "CI_REPOSITORY_URL",
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

        if (rules.isEmpty()) {
            // Default: run on success when no specific triggers are defined
            rules += mapOf("when" to "on_success")
        } else {
            // Add a final catch-all manual fallback so users can run the job manually if no rule matched
            // (Only add when there are conditional rules; first-match wins, so this must be last.)
            rules += mapOf(
                "when" to "manual",
                "allow_failure" to true
            )
        }
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
        rules += createRulesFromTriggers(bc)

        return rules
    }

    // ... existing code ...
    private fun createRulesFromTriggers(bc: IBuildConfiguration): MutableList<Map<String, Any>> {
        val rules = mutableListOf<Map<String, Any>>()

        bc.triggers.forEach { trigger ->
            if (trigger is VcsTrigger) {
                rules.addAll(createVcsRules(trigger, bc.name))
            } else if (trigger is ScheduleTrigger) {
                // Schedules must be configured in UI
                rules.add(createScheduleRule(trigger, bc.name))
            } else if (trigger is FinishBuildTrigger) {
                println("WARNING: [${bc.name}] Finish build trigger is not supported by GitLab CI.")
            }
        }
        return rules
    }

    fun createScheduleRule(trigger: ScheduleTrigger, bcName: String): Map<String, Any> {
        println("INFO: [${bcName}] Manual action required: Create a schedule in GitLab -> Build -> Pipeline schedules.")
        println("      Cron: '${trigger.cron}'")

        return createRule("\$CI_PIPELINE_SOURCE == 'schedule'", "on_success", emptyList(), createVariablesForTrigger(bcName, trigger))
    }

    private fun createVcsRules(trigger: VcsTrigger, bcName: String): List<Map<String, Any>> {
        val rules = mutableListOf<Map<String, Any>>()

        // Split patterns into Tags and Branches
        val (tagIncludes, branchIncludes) = trigger.includeBranches.partition { it.startsWith("tags/") }
        val (tagExcludes, branchExcludes) = trigger.excludeBranches.partition { it.startsWith("tags/") }

        val cleanedTagIncludes = tagIncludes.map { it.removePrefix("tags/") }
        val cleanedTagExcludes = tagExcludes.map { it.removePrefix("tags/") }

        val changes = pathsFromRules(trigger)

        // 1. Check for "Run on Everything" wildcard cases
        val isWildcardOnly = trigger.includeBranches.singleOrNull() == "*"
        val noFilters = trigger.includeBranches.isEmpty() && trigger.excludeBranches.isEmpty()

        if (noFilters || (isWildcardOnly && trigger.excludeBranches.isEmpty())) {
            rules.add(createRule("\$CI_PIPELINE_SOURCE == 'push'", "on_success", changes, createVariablesForTrigger(bcName, trigger)))
            return rules
        }

        // 2. Generate Exclusion Rules (when: never) - High Priority
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

        return rules
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

    fun generateArtifacts(buildStep: IBuildConfiguration): Map<String, Any> {
        val artifactsMap = mutableMapOf<String, Any>()
        // Publish under namespaced directory _artifacts/<BC>/** so consumers can cleanly scope and clean
        artifactsMap["paths"] = listOf("${getArtifactDirectoryOfBuild(buildStep.name)}**")
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

    private fun normalizeDirName(componentName: String): String =
        componentName.replace("::", "_").replace("/", "_")

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun getArtifactDirectoryOfBuild(buildConfigurationName: String): String {
        return "$artifactDirectory${sanitizeFileName(buildConfigurationName)}/"
    }

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
     * Collects path patterns for GitLab rules:changes from VCS trigger rules.
     * Excludes are not directly supported by GitLab's 'changes' list; we include only includePaths.
     */
    private fun pathsFromRules(trig: VcsTrigger): List<String> {
        val includes = trig.triggerRules.flatMap { it.includePaths }
        return includes.distinct()
    }

    private fun aliasParametersShell(bc: IBuildConfiguration): List<String> {
        val lines = mutableListOf<String>()
        bc.parameters.forEach { p ->
            val srcKey = computeParamFullName(bc.name, p.name, dependencyManager.currentComponent)
            val src = "${'$'}{${srcKey}}"
            val dst = computeLocalVarName(p.name)
            lines += "export ${dst}=${src}"
        }
        return lines
    }

    private fun sourceDependencyDotenv(): List<String> = listOf(
        "# Load dotenv from dependencies if present",
        "if [ -f build.env ]; then set -a; . build.env; set +a; fi"
    )

    private fun generateJobs(bc: IBuildConfiguration, pipelineSettings: PipelineSettings): Map<String, GitLabJob> {
        val isMatrix = bc.matrix.isNotEmpty()
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
            runJob.parallel = mapOf("matrix" to listOf(bc.matrix))
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
        val artifactPath = getArtifactDirectoryOfBuild(bc.name)
        if (isComposite) {
            // For Composite builds, publish ALL artifacts that were consumed from dependencies
            // We copy consumed artifacts (based on dependency artifact mappings) into our namespaced artifacts dir.
            after += "echo '# Staging consumed dependency artifacts (Composite)'"
//            after += "mkdir -p \"${artifactPath}\""
            bc.dependencies.forEach { dep ->
                dep.artifacts?.forEach { a ->
                    // If a destination was specified in the dependency mapping, publish from that destination directory
                    // otherwise publish files by their original source patterns copied into the working directory.
                    val targetPath = if (a.destination != null) "${artifactPath}${a.destination}" else artifactPath
                    val stageSrc = if (a.destination != null) "${a.destination}**" else a.source
                    after += """
                        mkdir -p "$targetPath"
                        for f in ${stageSrc}; do cp -r "${'$'}f" "$targetPath" || true; done
                    """.trimIndent()
                }
            }
        } else if (bc.publishArtifacts.isNotEmpty()) {
            // Regular/Deployment: stage artifacts explicitly published by this build configuration
            after += "echo '# Staging published artifacts'"
            bc.publishArtifacts.forEach { a ->
                val dst = a.destination?.let { a.destination } ?: ""
                // Use explicit quoting for bash variables to ensure safe handling
                after += "mkdir -p \"${artifactPath}/${dst}\""
                after += """
                        for f in ${a.source}; do cp -r "${'$'}f" "${artifactPath}" || true; done
                    """.trimIndent()
            }
        }

        // B. Export Parameters (Only for non-matrix jobs, per request)
        if (!isMatrix && bc.parameters.isNotEmpty()) {
            after += "echo '# Exporting parameters for ${bc.name}'"
            bc.parameters.forEach { p ->
                val full = computeParamFullName(bc.name, p.name, dependencyManager.currentComponent)
                val local = computeLocalVarName(p.name)
                after += """
                    echo "$full=$local" >> build.env
                """.trimIndent()
            }
        }

        if (after.isNotEmpty()) {
            runJob.afterScript = after
        }

        // C. Configure Artifacts Object
        if (isComposite || bc.publishArtifacts.isNotEmpty() || bc.parameters.isNotEmpty()) {
            val artifacts = generateArtifacts(bc).toMutableMap()
            // Add dotenv report only if we actually exported parameters
            if (bc.parameters.isNotEmpty()) {
                artifacts["reports"] = mutableMapOf("dotenv" to "build.env")
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
            aggJob.artifacts = mutableMapOf(
                "paths" to listOf("${getArtifactDirectoryOfBuild(bc.name)}/**"),
                "when" to "always"
            )
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
            tags = listOf("shared-fi"),
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

                    Dependency.FailureAction.FAIL,
                    Dependency.FailureAction.CANCEL -> false
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
            val needsArtifacts = !dep.artifacts.isNullOrEmpty()

            val optionalNeed = when (dep.failureAction) {
                Dependency.FailureAction.IGNORE,
                Dependency.FailureAction.ADD_PROBLEM -> true

                Dependency.FailureAction.FAIL,
                Dependency.FailureAction.CANCEL -> false
            }

            needs += mapOf<String, Any>(
                "job" to targetName,
                "artifacts" to needsArtifacts,
                "optional" to optionalNeed
            )
        }
        return needs
    }

    /** Generate shell lines to implement Dependency Artifacts copy phase per (s,d) mapping. */
    private fun generateDependencyArtifactsCopy(bc: IBuildConfiguration): List<String> {
        val lines = mutableListOf<String>()
        if (bc.dependencies.isEmpty()) return lines
        lines += "# Copy dependency artifacts per (s,d) with archive extraction"
        bc.dependencies.forEach { dep ->
            val buildName = if (ProjectPath.isFullPath(dep.buildConfigurationName)) {
                toEnvToken(dep.buildConfigurationName)
            } else {
                dep.buildConfigurationName
            }
            val srcBuildConfiguration = getArtifactDirectoryOfBuild(buildName)
            dep.artifacts?.forEach { art ->
                val src = "${srcBuildConfiguration}${art.source}"
                val dst = art.destination ?: "."
                if (src.contains("!")) {
                    // Archive extraction: <packagePath>!<pathInArchive>
                    val parts = src.split("!", limit = 2)
                    val pkg = parts[0]
                    val inner = parts[1]
                    // Try common archive types
                    lines += """
                        mkdir -p "$dst"
                        tmpdir="_tempArtifactDir"
                        mkdir ${'$'}tmpdir
                        if [[ "$pkg" == *.zip ]]; then
                          unzip -o "$pkg" -d "${'$'}tmpdir"
                        elif [[ "$pkg" == *.tar.gz || "$pkg" == *.tgz ]]; then
                          tar -xpf "$pkg" -C "${'$'}tmpdir"
                        elif [[ "$pkg" == *.tar ]]; then
                          tar -xpf "$pkg" -C "${'$'}tmpdir"
                        fi
                        
                        mkdir -p "$dst/"
                        ( cd "${'$'}tmpdir" && cp -a --parents $inner ../$dst/ )
                        rm -rf "${'$'}tmpdir"
                    """.trimIndent()
                } else {
                    // update to cp -a --parents, but make sure to namespace direcoty
                    lines += """
                        mkdir -p "$dst/"
                        for f in ${src}; do cp -r "${'$'}f" "$dst" || true; done
                    """.trimIndent()
                }
            }
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