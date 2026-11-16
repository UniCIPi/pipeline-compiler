package fanci.compiler.generator.platforms.teamcity

import fanci.compiler.common.ProjectPath
import fanci.compiler.common.VCSType
import fanci.compiler.configurationModel.SubmoduleStrategy
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.PullRequestBranch
import fanci.compiler.pipeline.PullRequestConfiguration
import fanci.compiler.pipeline.VcsAuthMethod
import fanci.compiler.generator.DependencyManager
import fanci.compiler.generator.platforms.teamcity.NamingUtils.createVcsRootClassName
import fanci.compiler.pipeline.Artifact
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.JUnitReport
import fanci.compiler.pipeline.Parameter
import fanci.compiler.pipeline.SelectParameter
import fanci.compiler.pipeline.TextParameter
import fanci.compiler.pipeline.Trigger
import fanci.compiler.pipeline.VcsTrigger
import fanci.compiler.pipeline.ScheduleTrigger
import fanci.compiler.pipeline.FinishBuildTrigger
import fanci.compiler.pipeline.SecretParameter
import fanci.compiler.pipeline.RefreshableToken
import fanci.compiler.pipeline.Token
import fanci.compiler.pipeline.UploadedKey
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.field.CronFieldName
import com.cronutils.parser.CronParser
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import kotlin.collections.iterator

/**
 * Builder for TeamCity Kotlin DSL scripts.
 * KotlinPoet FileSpec and CodeBlock extensions functions to generate TeamCity Kotlin DSL scripts.
 */
internal object KotlinBuilder {

    /**
     * Generates a VCS root file specification.
     * By default:
     * - Uses tags as branches
     * Supports:
     * - SSH key authentication
     */
    internal fun generateVcsFileSpec(
        className: ClassName,
        normalizedName: String,
        vcsRootUrl: String,
        defaultBranch: String,
        authMethod: VcsAuthMethod?,
        submoduleStrategy: SubmoduleStrategy
    ): FileSpec {
        return FileSpec.builder(className)
            .suppressWarningTypes("RedundantVisibilityModifier")
            .indent("\t") // set file indentation to tab
            .addType(
                TypeSpec.objectBuilder(className)
                    .superclass(ClassName("jetbrains.buildServer.configs.kotlin.vcs", "GitVcsRoot"))
                    .addSuperclassConstructorParameter(
                        CodeBlock.builder()
                            .addStatement("{")
                            .indent()
                            .addStatement("name = %S", normalizedName)
                            .addStatement("url = %S", vcsRootUrl)
                            .addStatement("branch = %S", defaultBranch)
                            .addStatement(
                                "branchSpec = %S\n", """
                                |refs/heads/*
                                |refs/(tags/*)
                            """.trimMargin()
                            )
                            .addStatement("useTagsAsBranches = true")
                            .addAuthMethod(authMethod)
                            .addStatement("checkoutSubmodules = %T", ClassName("jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules", translateSubmoduleStrategy(submoduleStrategy)))
                            .unindent()
                            .add("}")
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /**
     * Generate Project file specification.
     * Project encapsulates VCS roots and build configurations for a component.
     */
    internal fun generateProjectFileSpec(
        projectName: String,
        normalizedName: String,
        vcsRootClassName: ClassName,
        buildTypesClassNames: Collection<ClassName>
    ): FileSpec {
        val packageName = normalizedName
        val className = "Project"

        val codeBlockBuilder = CodeBlock.builder()
            .addStatement("{")
            .indent()
            .addStatement("name = %S", projectName)
            .addStatement("id(%S)\n", normalizedName)
            .addStatement("vcsRoot(%T)\n", vcsRootClassName)
        buildTypesClassNames.forEach { step ->
            codeBlockBuilder.addStatement("buildType(%T)", step)
        }
        codeBlockBuilder.unindent()
            .add("}")

        return FileSpec.builder(packageName, className)
            .suppressWarningTypes("RedundantVisibilityModifier")
            .indent("\t")
            .addType(
                TypeSpec.objectBuilder(className)
                    .superclass(ClassName("jetbrains.buildServer.configs.kotlin", "Project"))
                    .addSuperclassConstructorParameter(codeBlockBuilder.build())
                    .build()
            )
            .build()
    }

    /**
     * Generates a build configuration file specification.
     * Build configuration encapsulates multiple coherent build steps, that are executed in order, and defines build features
     */
    internal fun generateBuildConfigurationFileSpec(
        buildConfiguration: IBuildConfiguration,
        className: ClassName,
        vcsRootClassName: ClassName,
        dependencyManager: DependencyManager,
        pipelineSettings: PipelineSettings
    ): FileSpec {
        val fileSpecBuilder = FileSpec.builder(className)
            .suppressWarningTypes("RedundantVisibilityModifier")
            .addBuildConfigurationImports(buildConfiguration)
            .indent("\t") // set file indentation to tab

        return fileSpecBuilder
            .addType(
                TypeSpec.objectBuilder(className)
                    .superclass(ClassName("jetbrains.buildServer.configs.kotlin", "BuildType"))
                    .addSuperclassConstructorParameter(
                        CodeBlock.builder()
                            .add("{\n")
                            .indent()
                            .add("name = %S\n", buildConfiguration.name)
                            .addBuildType(buildConfiguration.type)
                            .addBuildNumberPattern(buildConfiguration.buildNumberPattern)
                            .addArtifactsRules(buildConfiguration.publishArtifacts)
                            .addVcsRootToBuildConfiguration(vcsRootClassName)
                            .addParameters(buildConfiguration.parameters)
                            .addBuildSteps(buildConfiguration.buildSteps, buildConfiguration.dockerImage, dependencyManager)
                            .addTriggers(buildConfiguration.triggers, dependencyManager, pipelineSettings)
                            .addDependencies(buildConfiguration.dependencies, dependencyManager)
                            .addFeatures(buildConfiguration, vcsRootClassName, pipelineSettings)
                            .unindent()
                            .add("}")
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /**
     * Adds necessary kotlin imports for build steps and triggers.
     */
    private fun FileSpec.Builder.addBuildConfigurationImports(buildConfiguration: IBuildConfiguration): FileSpec.Builder {
        this.addImport("jetbrains.buildServer.configs.kotlin.buildSteps", "script")

        // Add imports for triggers if they are present
        buildConfiguration.triggers.forEach { trigger ->
            when (trigger) {
                is ScheduleTrigger -> this.addImport("jetbrains.buildServer.configs.kotlin.triggers", "schedule")
                is VcsTrigger -> this.addImport("jetbrains.buildServer.configs.kotlin.triggers", "vcs")
                is FinishBuildTrigger -> this.addImport(
                    "jetbrains.buildServer.configs.kotlin.triggers",
                    "finishBuildTrigger"
                )

                else -> {}
            }
        }

        return this
    }

    private fun CodeBlock.Builder.addVcsRootToBuildConfiguration(
        vcsRootClassName: ClassName
    ): CodeBlock.Builder {
        addStatement("vcs {")
        indent()
        addStatement("root(%T)", vcsRootClassName)
        addStatement("cleanCheckout = true")
        // Force Agent side checkout. Otherwise, the `.git` directory is not present in the work dir
        addStatement("checkoutMode = %T", ClassName("jetbrains.buildServer.configs.kotlin.CheckoutMode", "ON_AGENT"))
        addStatement("showDependenciesChanges = true")
        unindent()
        addStatement("}")
        return this
    }

    private fun CodeBlock.Builder.addAuthMethod(authMethod: VcsAuthMethod?): CodeBlock.Builder {
        when (authMethod) {
            null -> this.addStatement("authMethod = anonymous()")
            is RefreshableToken -> {
                this.addStatement("authMethod = token {")
                    .indent()
                    .addStatement("tokenId = %S", authMethod.tokenId)
                authMethod.username?.let { this.addStatement("userName = %S", it) }
                this.unindent()
                this.addStatement("}")
            }

            is Token -> {
                this.addStatement("authMethod = password {")
                    .indent()
                    .addStatement("userName = %S", authMethod.username)
                    .addStatement("password = %S", authMethod.tokenId)
                    .unindent()
                    .addStatement("}")
            }

            is UploadedKey -> {
                this.addStatement("authMethod = uploadedKey {")
                    .indent()
                    .addStatement("uploadedKey = %S", authMethod.name)
                    .unindent()
                    .addStatement("}")
            }
        }
        return this
    }

    /**
     * Adds build steps to the BuildConfiguration Codeblock.
     *
     * TeamCity has no configuration-wide docker image, so the image of the build configuration is
     * emitted on every step that does not define an image of its own.
     */
    private fun CodeBlock.Builder.addBuildSteps(
        buildSteps: List<BuildStep>,
        buildConfigurationDockerImage: String,
        dependencyManager: DependencyManager
    ): CodeBlock.Builder {
        this.addStatement("steps {")
            .indent()
        for (step in buildSteps) {
            this.addStatement("script {")
                .indent()
                .addStatement("name = %S", step.name)
            if (step.runConditions.isNotEmpty()) {
                this.addStatement("conditions {")
                    .indent()
                for (cond in step.runConditions) {
                    val left = resolveConditionParameter(cond.parameterName, dependencyManager)
                    when (cond.operation) {
                        BuildStep.Operation.Equal -> this.addStatement("equals(%S, %S)", left, cond.value)
                        BuildStep.Operation.NotEqual -> this.addStatement("doesNotEqual(%S, %S)", left, cond.value)
                        BuildStep.Operation.Contains -> this.addStatement("contains(%S, %S)", left, cond.value)
                        BuildStep.Operation.NotContains -> this.addStatement("doesNotContain(%S, %S)", left, cond.value)
                    }
                }
                this.unindent()
                    .addStatement("}")
            }
            this.addStatement("scriptContent = %S", step.script)
            if (step.workDir.isNotBlank()) {
                this.addStatement("workingDir = %S", step.workDir)
            }
            if (buildConfigurationDockerImage.isNotBlank()) {
                this.addStatement("dockerImage = %S", buildConfigurationDockerImage)

                // Add docker run arguments only if an image is set
                var dockerRunParameters = ""
                if (step.dockerRunParameters.isNotBlank()) {
                    dockerRunParameters = step.dockerRunParameters
                }
                if (step.allowDockerInDocker) {
                    dockerRunParameters = "$dockerRunParameters -v /var/run/docker.sock:/var/run/docker.sock"
                }
                if (dockerRunParameters.isNotBlank()) {
                    this.addStatement("dockerRunParameters = %S", dockerRunParameters)
                }
            }
            if (step.executionMode != BuildStep.ExecutionMode.OnSuccess) {
                this.addStatement(
                    "executionMode = %T.%L",
                    ClassName("jetbrains.buildServer.configs.kotlin.BuildStep", "ExecutionMode"),
                    translateExecutionMode(step.executionMode)
                )
            }

            this.unindent()
                .addStatement("}")
        }
        this.unindent()
            .addStatement("}")
        return this
    }

    // Resolve parameter names for step conditions.
    // TeamCity uses actual parameter names; when a full project path is provided, map it to dep.<Full_Path> form.
    private fun resolveConditionParameter(parameterName: String, dependencyManager: DependencyManager): String {
        if (ProjectPath.isFullPath(parameterName)) {
            // TODO should check if parameter exists first
            val projectPath = ProjectPath.parse(parameterName)
            val normalized = NamingUtils.normalizeName(ProjectPath.projectOf(projectPath).toString())
            val param = projectPath.last()
            // Need real full ID of the build configuration - relative to <root_project> of teamcity, not current versioned settings
            return "dep.$normalized.$param"
        }
        return parameterName
    }

    /**
     * Adds build features to the BuildConfiguration Codeblock.
     */
    private fun CodeBlock.Builder.addFeatures(
        buildConfiguration: IBuildConfiguration,
        vcsRootClassName: ClassName,
        pipelineSettings: PipelineSettings
    ): CodeBlock.Builder {
        this.addStatement("features {")
            .indent()
        this.addPullRequests(vcsRootClassName, pipelineSettings)
        if (buildConfiguration.buildMatrix.isNotEmpty()) {
            this.addMatrix(buildConfiguration.buildMatrix)
        }
        if (buildConfiguration.freeDiskSpaceGb > 0) {
            this.addFreeDiskSpace(buildConfiguration.freeDiskSpaceGb)
        }
        if (buildConfiguration.dockerConnections.isNotEmpty()) {
            this.addDockerRegistryConnections(buildConfiguration.dockerConnections)
        }
        if (buildConfiguration.sshAgentKeyName.isNotBlank()) {
            this.addSshAgent(buildConfiguration.sshAgentKeyName)
        }
        if (buildConfiguration.junitReports.isNotEmpty()) {
            this.addJUnitReports(buildConfiguration.junitReports)
        }
        this.unindent()
            .addStatement("}")
        return this
    }

    /**
     * Adds the pull requests feature to the BuildConfiguration Codeblock.
     *
     * The feature makes TeamCity monitor pull requests of the component VCS root and exposes every
     * pull request as a logical branch (`merge-requests/<id>` for GitLab, `pull/<id>` for GitHub),
     * so that triggers can react to it via the `mr/` branch pattern.
     *
     * The feature is skipped when the component disabled the pull request support or when the VCS host
     * system has no TeamCity pull request provider. Both cases are reported by [TeamCity.checkPullRequestSupport].
     */
    private fun CodeBlock.Builder.addPullRequests(
        vcsRootClassName: ClassName,
        pipelineSettings: PipelineSettings
    ): CodeBlock.Builder {
        val configuration = pipelineSettings.pullRequestConfiguration
        if (!configuration.enabled) {
            return this
        }
        val provider = translatePullRequestProvider(pipelineSettings.vcsType) ?: return this

        this.addStatement("%T {", ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures", "pullRequests"))
            .indent()
            .addStatement("vcsRootExtId = \"\${%T.id}\"", vcsRootClassName)
            .addStatement("provider = %L {", provider)
            .indent()
            .addPullRequestAuthType(configuration)

        // TeamCity pull request filters accept fully qualified VCS branch names only
        buildBranchRefFilter(configuration.includeSourceBranches, configuration.excludeSourceBranches)?.let {
            this.addStatement("filterSourceBranch = %S", it)
        }
        buildBranchRefFilter(configuration.includeTargetBranches, configuration.excludeTargetBranches)?.let {
            this.addStatement("filterTargetBranch = %S", it)
        }

        return this.unindent()
            .addStatement("}")
            .unindent()
            .addStatement("}")
    }

    /**
     * Adds the authentication used to read pull requests from the VCS host system API.
     *
     * A token configured in the component pull request section wins. Otherwise the credentials of the
     * VCS root are reused, which is valid only for token based VCS authentication methods.
     */
    private fun CodeBlock.Builder.addPullRequestAuthType(configuration: PullRequestConfiguration): CodeBlock.Builder {
        val tokenId = configuration.tokenId
        if (tokenId != null) {
            return this.addStatement("authType = token {")
                .indent()
                .addStatement("token = %S", tokenId)
                .unindent()
                .addStatement("}")
        }
        // Reuse the token of the VCS root. Non-token authentication methods are reported as a warning.
        return this.addStatement("authType = vcsRoot()")
    }

    /**
     * Builds a TeamCity branch filter from include/exclude branch patterns.
     * Patterns are qualified to full VCS references, because pull request filters do not accept logical branch names.
     *
     * @return null when no filter is defined and the platform default (all branches) applies
     */
    private fun buildBranchRefFilter(include: List<String>, exclude: List<String>): String? {
        if (include.isEmpty() && exclude.isEmpty()) {
            return null
        }
        val lines = mutableListOf<String>()
        // Without an explicit include, everything not excluded stays included
        if (include.isEmpty()) {
            lines.add("+:*")
        }
        include.forEach { lines.add("+:${toBranchRef(it)}") }
        exclude.forEach { lines.add("-:${toBranchRef(it)}") }
        return lines.joinToString("\n")
    }

    /** Qualifies a branch name to a full VCS reference, keeping already qualified references untouched. */
    private fun toBranchRef(branch: String): String {
        val trimmed = branch.trim()
        return if (trimmed.startsWith("refs/")) trimmed else "refs/heads/$trimmed"
    }

    /**
     * Adds a matrix feature to the BuildConfiguration Codeblock.
     * The Matrix Builds feature is used to define a build configuration with different sets of parameters/parameter values.
     * Running this build configuration creates a batch of separate builds, each running its own parameter set.
     * These builds can run in parallel.
     */
    private fun CodeBlock.Builder.addMatrix(matrix: Map<String, List<String>>): CodeBlock.Builder {
        this.addStatement("%T {", ClassName("jetbrains.buildServer.configs.kotlin", "matrix"))
            .indent()
        for ((key, value) in matrix) {
            this.addStatement("param(")
                .indent()
                .addStatement("\"${key}\", listOf(")
                .indent()
                .add(value.joinToString(separator = ",\n", postfix = "\n") { "value(\"$it\")" })
                .unindent()
                .addStatement(")")
                .unindent()
                .addStatement(")")
        }
        /** Do not group publish artifacts to separate directories based on the parameter values.
         *  It is the build script's responsibility to ensure the artifacts are not overwriting */
        this.addStatement("groupArtifactsByBuild = false")
            .unindent()
            .addStatement("}")
        return this
    }

    /**
     * Adds a free disk space feature to the BuildConfiguration Codeblock.
     * It is a build feature ensuring certain free disk space on the agent before the build
     * by deleting files managed by the TeamCity agent.
     */
    private fun CodeBlock.Builder.addFreeDiskSpace(freeDiskSpaceGb: Int): CodeBlock.Builder {
        return this.addStatement(
            "%T {", ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures", "freeDiskSpace")
        )
            .indent()
            .addStatement("requiredSpace = %S", "${freeDiskSpaceGb}gb")
            .addStatement("failBuild = true")
            .unindent()
            .addStatement("}")
    }

    /**
     * Adds Docker registry connections to the BuildConfiguration Codeblock.
     * It is a build feature that allows the build to log in to a Docker registry.
     * @param dockerRegistries: a list of Docker registry IDs defined in Project Connections
     */
    private fun CodeBlock.Builder.addDockerRegistryConnections(dockerRegistries: List<String>): CodeBlock.Builder {

        return this.addStatement(
            "%T {", ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures", "dockerRegistryConnections")
        )
            .indent()
            .addStatement("loginToRegistry = on {")
            .indent()
            .addStatement("dockerRegistryId = %S", dockerRegistries.joinToString(separator = ","))
            .unindent()
            .addStatement("}")
            .unindent()
            .addStatement("}")
    }

    /**
     * Adds SSH agent feature to the BuildConfiguration Codeblock.
     * It is a build feature that allows the build to use SSH keys for authentication.
     * @param sshKeyName: the name of the SSH key, defined in TeamCity, that will be passed to the build
     */
    private fun CodeBlock.Builder.addSshAgent(sshKeyName: String): CodeBlock.Builder {
        return this.addStatement(
            "%T {", ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures", "sshAgent")
        )
            .indent()
            .addStatement("teamcitySshKey = %S", sshKeyName)
            .unindent()
            .addStatement("}")
    }

    /**
     * Adds one xmlReport build feature per JUnit report to the BuildConfiguration Codeblock.
     * It is a build feature that publishes JUnit XML test results, making them visible in the TeamCity UI.
     */
    private fun CodeBlock.Builder.addJUnitReports(reports: List<JUnitReport>): CodeBlock.Builder {
        reports.forEach { report ->
            this.addStatement(
                "%T {", ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures", "xmlReport")
            )
                .indent()
                .addStatement(
                    "reportType = %T.JUNIT",
                    ClassName("jetbrains.buildServer.configs.kotlin.buildFeatures.XmlReport", "XmlReportType")
                )
                .addStatement("rules = %S", report.path)
                .unindent()
                .addStatement("}")
        }
        return this
    }

    /**
     * Adds dependencies to the BuildConfiguration Codeblock.
     */
    private fun CodeBlock.Builder.addDependencies(
        dependencies: List<Dependency>, dependencyManager: DependencyManager
    ): CodeBlock.Builder {
        if (dependencies.isEmpty()) {
            return this
        }
        this.addStatement("dependencies {")
            .indent()
        for (dependency in dependencies) {

            val dependencyName = dependencyManager.findDependency(dependency.buildConfigurationName)
            val dependencyType = getBuildConfigurationClassName(
                dependencyManager.getProject(dependencyName),
                dependencyManager.getBuildType(dependencyName)
            )

            this.addStatement("dependency(%T) {", dependencyType)
                .indent()
                .addStatement("snapshot {")
                .indent()
                .addStatement(
                    "onDependencyFailure = %T", ClassName(
                        "jetbrains.buildServer.configs.kotlin.FailureAction",
                        translateFailureAction(dependency.failureAction)
                    )
                )
                .addStatement(
                    "onDependencyCancel = %T", ClassName(
                        "jetbrains.buildServer.configs.kotlin.FailureAction",
                        translateFailureAction(dependency.failureAction)
                    )
                )
                .addStatement("synchronizeRevisions = true")
            if (!dependency.reuseBuilds) {
                this.addStatement("reuseBuilds = %T", ClassName("jetbrains.buildServer.configs.kotlin.ReuseBuilds", "NO"))
            }
            this.unindent()
                .addStatement("}")

            if (!dependency.artifacts.isNullOrEmpty()) {
                this.addStatement("artifacts {")
                    .indent()
                val artifactRules = mutableListOf<String>()
                for (artifact in dependency.artifacts) {
                    artifactRules.add("+:${artifact.source}${artifact.destination?.let { " => $it" } ?: ""}")
                }
                this.addStatement("artifactRules = %S", artifactRules.joinToString(separator = "\n"))
                    .unindent()
                    .addStatement("}")
            }
            this.unindent()
                .addStatement("}")
        }
        this.unindent()
            .addStatement("}")
        return this
    }

    /**
     * Adds parameters to the BuildConfiguration Codeblock.
     * Parameters are used to define variables that can be used in build steps, triggers, and other parts of the build configuration.
     */
    private fun CodeBlock.Builder.addParameters(parameters: List<Parameter>): CodeBlock.Builder {
        if (parameters.isNotEmpty()) {
            this.addStatement("params {")
                .indent()
            for (param in parameters) {
                this.addParameterStatement(param)
            }
            this.unindent()
                .addStatement("}")
        }
        return this
    }

    /**
     * Emits a single parameter statement based on the parameter type.
     * Extracted for reuse across different places (e.g., trigger buildParams).
     */
    private fun CodeBlock.Builder.addParameterStatement(param: Parameter): CodeBlock.Builder {
        var paramPath = param.name
        if (ProjectPath.isFullPath(param.name)) {
            val projectPath = ProjectPath.parse(param.name)
            paramPath = "reverse.dep.${
                // Need real full ID of the build configuration - relative to <root_project> of teamcity, not current versioned settings
                NamingUtils.normalizeName(
                    ProjectPath.projectOf(projectPath).toString()
                )
            }.${projectPath.last()}"
        }
        when (param) {
            is SelectParameter -> {
                this.addStatement(
                    "select(%S, %S, description = %S, label = %S, display = %T, options = listOf(%L), allowMultiple = %L)",
                    paramPath,
                    param.value,
                    param.description,
                    param.label,
                    ClassName(
                        "jetbrains.buildServer.configs.kotlin.ParameterDisplay",
                        translateParameterDisplay(param.displayType)
                    ),
                    param.options.joinToString(", ") { "\"$it\"" },
                    param.allowMultiple
                )
            }

            is TextParameter -> {
                this.addStatement(
                    "text(%S, %S, description = %S, label = %S, display = %T)",
                    paramPath,
                    param.value,
                    param.description,
                    param.label,
                    ClassName(
                        "jetbrains.buildServer.configs.kotlin.ParameterDisplay",
                        translateParameterDisplay(param.displayType)
                    )
                )
            }

            is SecretParameter -> {
                this.addStatement(
                    "password(%S, %S, description = %S, label = %S, display = %T)",
                    paramPath,
                    param.value,
                    param.description,
                    param.label,
                    ClassName(
                        "jetbrains.buildServer.configs.kotlin.ParameterDisplay",
                        translateParameterDisplay(param.displayType)
                    )
                )
            }

            else -> {
                this.addStatement("param(%S, %S)", paramPath, param.value)
            }
        }
        return this
    }

    /**
     * Add artifact rules to the BuildConfiguration Codeblock.
     * Artifact rules define which files are produced by the build and how they are stored.
     */
    private fun CodeBlock.Builder.addArtifactsRules(artifactRules: List<Artifact>): CodeBlock.Builder {
        val artifactLines = artifactRules.map { (source, target) ->
            "+:${source}${target?.let { " => $it" } ?: ""}"
        }
        if (artifactLines.isEmpty()) {
            return this
        }
        return this.addStatement("artifactRules = %S", artifactLines.joinToString(separator = "\n"))
    }

    /**
     * Adds a build number pattern to the BuildConfiguration Codeblock.
     * The build number pattern defines how the build number is generated.
     */
    private fun CodeBlock.Builder.addBuildNumberPattern(buildNumberPattern: String?): CodeBlock.Builder {
        if (buildNumberPattern.isNullOrBlank()) {
            return this
        }
        return this.addStatement("buildNumberPattern = %S", buildNumberPattern)
    }

    /**
     * Adds triggers to the BuildConfiguration Codeblock.
     * Triggers are used to define when the build configuration shall execute.
     */
    private fun CodeBlock.Builder.addTriggers(
        triggers: List<Trigger>,
        dependencyManager: DependencyManager,
        pipelineSettings: PipelineSettings
    ): CodeBlock.Builder {
        if (triggers.isEmpty()) return this
        val blocks = triggers.mapNotNull { trigger ->
            when (trigger) {
                is ScheduleTrigger -> buildCronTrigger(trigger, pipelineSettings)
                is VcsTrigger -> buildVcsTrigger(trigger, pipelineSettings)
                is FinishBuildTrigger -> buildFinishBuildTrigger(trigger, dependencyManager, pipelineSettings)
                else -> null
            }
        }
        if (blocks.isEmpty()) return this
        this.addStatement("triggers {")
            .indent()
        blocks.forEach { this.add(it) }
        this.unindent()
            .add("}\n")
        return this
    }

    /**
     * Builds a VCS trigger.
     *
     * A trigger watching another component restricts every rule to the VCS root of that component and watches
     * the changes in dependencies, so TeamCity sees the commits of that VCS root.
     * TeamCity includes all changes when no include rule is given, so without include paths, an include rule
     * for all paths of the watched VCS root is added. Otherwise, the changes in all VCS roots of the dependencies
     * would trigger the build.
     */
    private fun buildVcsTrigger(vcsTrigger: VcsTrigger, pipelineSettings: PipelineSettings): CodeBlock {
        val block = CodeBlock.Builder()
        block.addStatement("vcs {")
            .indent()

        // TODO TeamCity watches only the VCS roots of the build configuration and of its snapshot dependencies.
        //  The trigger never fires when the watched component is not a dependency of the build configuration.
        val rootPrefix = vcsTrigger.watchedComponent?.let {
            CodeBlock.builder().add("root=\${").add("%T", createVcsRootClassName(it)).add(".id}").build()
        }
        val conditions = listOfNotNull(rootPrefix, vcsTrigger.commitMessageRegex?.let { CodeBlock.of("comment=%L", it) })
        val rulePrefix = CodeBlock.builder()
        if (conditions.isNotEmpty()) {
            rulePrefix.add(conditions.joinToCode(";")).add(":")
        }

        val triggerRulesBlocks = mutableListOf<CodeBlock>()
        if (rootPrefix != null && vcsTrigger.includePaths.isEmpty()) {
            triggerRulesBlocks.add(CodeBlock.builder().add("+:").add(rootPrefix).add(":**").build())
        }
        vcsTrigger.includePaths.forEach { path ->
            triggerRulesBlocks.add(CodeBlock.builder().add("+:").add(rulePrefix.build()).add(path).build())
        }
        vcsTrigger.excludePaths.forEach { path ->
            triggerRulesBlocks.add(CodeBlock.builder().add("-:").add(rulePrefix.build()).add(path).build())
        }
        if (triggerRulesBlocks.isNotEmpty()) {
            block.addStatement("triggerRules = \"\"\"")
                .indent()
            triggerRulesBlocks.forEach { line ->
                block.add(line).add("\n")
            }
            block.unindent()
                .addStatement("\"\"\".trimIndent()")
        }
        if (rootPrefix != null) {
            block.addStatement("watchChangesInDependencies = true")
        }
        block.addTriggerCommonProperties(vcsTrigger, pipelineSettings)
            .unindent()
            .addStatement("}")
        return block.build()
    }

    private fun buildCronTrigger(scheduleTrigger: ScheduleTrigger, pipelineSettings: PipelineSettings): CodeBlock {
        val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
        val parser = CronParser(cronDefinition)
        val cron = parser.parse(scheduleTrigger.cron)
        val block = CodeBlock.Builder()
        block.addStatement("schedule {")
            .indent()
            .addStatement("schedulingPolicy = cron {")
            .indent()
        val minutes = cron.retrieve(CronFieldName.MINUTE).expression.asString()
        val hours = cron.retrieve(CronFieldName.HOUR).expression.asString()
        val daysOfMonth = cron.retrieve(CronFieldName.DAY_OF_MONTH).expression.asString()
        val months = cron.retrieve(CronFieldName.MONTH).expression.asString()
        val daysOfWeek = cron.retrieve(CronFieldName.DAY_OF_WEEK).expression.asString()
        block.addStatement("minutes = %S", minutes)
            .addStatement("hours = %S", hours)
            .addStatement("dayOfMonth = %S", daysOfMonth)
            .addStatement("month = %S", months)
            .addStatement("dayOfWeek = %S", daysOfWeek)
            .unindent()
            .addStatement("}")
        block.addStatement("withPendingChangesOnly = %L", scheduleTrigger.withPendingChangesOnly)
            .addStatement("triggerBuild = always()")
        // Common trigger properties within schedule block
        block.addTriggerCommonProperties(scheduleTrigger, pipelineSettings)
            .unindent()
            .addStatement("}")
        return block.build()
    }

    private fun buildFinishBuildTrigger(
        finishTrigger: FinishBuildTrigger,
        dependencyManager: DependencyManager,
        pipelineSettings: PipelineSettings
    ): CodeBlock {
        val block = CodeBlock.Builder()

        // Resolve referenced build configuration similarly to dependencies resolution
        val dependencyName = dependencyManager.findDependency(finishTrigger.buildConfigurationName)
        val buildTypeClass = getBuildConfigurationClassName(
            dependencyManager.getProject(dependencyName),
            dependencyManager.getBuildType(dependencyName)
        )

        block.addStatement("finishBuildTrigger {")
            .indent()
            .addStatement("buildType = \"\${%T.id}\"", buildTypeClass)
        if (finishTrigger.successfulOnly) {
            block.addStatement("successfulOnly = true")
        }
        // Add common trigger properties (e.g., buildParams, branchFilter) if provided
        block.addTriggerCommonProperties(finishTrigger, pipelineSettings)
            .unindent()
            .addStatement("}")
        return block.build()
    }

    private fun CodeBlock.Builder.addTriggerCommonProperties(
        trigger: Trigger,
        pipelineSettings: PipelineSettings
    ): CodeBlock.Builder {
        this.addStatement("clearBuildParams()")
        if (trigger.parameters.isNotEmpty()) {
            this.addStatement("buildParams {")
                .indent()
            trigger.parameters.forEach { param ->
                this.addParameterStatement(param)
            }
            this.unindent()
                .addStatement("}")
        }
        val include = translateBranchPatterns(trigger.includeBranches, pipelineSettings)
        val exclude = translateBranchPatterns(trigger.excludeBranches, pipelineSettings)
        if (include.isNotEmpty() || exclude.isNotEmpty()) {
            val lines = mutableListOf<String>()
                include.forEach { lines.add("+:$it") }
                exclude.forEach { lines.add("-:$it") }
            this.addStatement("branchFilter = %S", lines.joinToString("\n"))
        } else {
            // If no branch is defined, all are included
            this.addStatement("branchFilter = %S", "+:*")
        }
        return this
    }

    /**
     * Translates platform-independent branch patterns to TeamCity logical branch names.
     *
     * Pull request patterns (`mr/`) are mapped to the branch naming of the VCS host system, every other
     * pattern is kept as is. Pull request patterns are dropped when the component has no usable pull
     * request support, because the corresponding branches never appear in TeamCity.
     * The reason is reported once per component by [TeamCity.checkPullRequestSupport].
     */
    private fun translateBranchPatterns(patterns: List<String>, pipelineSettings: PipelineSettings): List<String> {
        return patterns.mapNotNull { pattern ->
            when {
                !PullRequestBranch.isPullRequestPattern(pattern) -> pattern
                !pipelineSettings.pullRequestConfiguration.enabled -> null
                else -> PullRequestBranch.translate(pattern, pipelineSettings.vcsType)
            }
        }
    }

    /**
     * Adds a build type to the BuildConfiguration.
     */
    private fun CodeBlock.Builder.addBuildType(buildType: IBuildConfiguration.Type): CodeBlock.Builder {
        return this.addStatement(
            "type = %T",
            ClassName("jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type", translateBuildType(buildType))
        )
    }


    /**
     * Convert the failure action enum to TeamCity format.
     */
    private fun translateFailureAction(failureAction: Dependency.FailureAction): String {
        return when (failureAction) {
            Dependency.FailureAction.IGNORE -> "IGNORE"
            Dependency.FailureAction.ADD_PROBLEM -> "ADD_PROBLEM"
            Dependency.FailureAction.FAIL -> "FAIL_TO_START"
        }
    }

    /**
     * Convert the parameter display type enum to TeamCity format.
     */
    private fun translateParameterDisplay(parameterDisplay: DisplayType): String {
        return when (parameterDisplay) {
            DisplayType.NORMAL -> "NORMAL"
            DisplayType.PROMPT -> "PROMPT"
            DisplayType.HIDDEN -> "HIDDEN"
        }
    }

    /**
     * Convert the build type enum to TeamCity format.
     */
    private fun translateBuildType(type: IBuildConfiguration.Type): String {
        return when (type) {
            IBuildConfiguration.Type.REGULAR -> "REGULAR"
            IBuildConfiguration.Type.COMPOSITE -> "COMPOSITE"
            IBuildConfiguration.Type.DEPLOYMENT -> "DEPLOYMENT"
        }
    }

    /**
     * Convert the VCS host system to the name of the TeamCity pull request provider.
     * TeamCity implements pull request providers for a limited set of host systems only.
     *
     * @return null when TeamCity has no pull request provider for the host system
     */
    internal fun translatePullRequestProvider(vcsType: VCSType): String? {
        return when (vcsType) {
            VCSType.GITLAB -> "gitlab"
            VCSType.GITHUB -> "github"
            VCSType.GITEA, VCSType.UNKNOWN -> null
        }
    }

    private fun translateSubmoduleStrategy(strategy: SubmoduleStrategy): String {
        return when (strategy) {
            SubmoduleStrategy.recursive -> "SUBMODULES_CHECKOUT"
            SubmoduleStrategy.shallow -> "NON_RECURSIVE_CHECKOUT"
            SubmoduleStrategy.none -> "IGNORE"
        }
    }

    private fun translateExecutionMode(mode: BuildStep.ExecutionMode): String {
        return when (mode) {
            BuildStep.ExecutionMode.OnSuccess -> "DEFAULT"
            BuildStep.ExecutionMode.Always -> "ALWAYS"
            BuildStep.ExecutionMode.OnFailure -> "RUN_ONLY_ON_FAILURE"
        }
    }

    /**
     * Adds a Suppress annotation to the FileSpec.
     */
    private fun FileSpec.Builder.suppressWarningTypes(vararg types: String): FileSpec.Builder {
        if (types.isEmpty()) {
            return this
        }

        val format = "%S,".repeat(types.count())
            .trimEnd(',')
        return this.addAnnotation(
            AnnotationSpec.builder(ClassName("", "Suppress"))
                .addMember(format, *types)
                .build()
        )
    }

    fun getBuildConfigurationClassName(projectName: String, buildConfigurationName: String): ClassName {
        val normalizedProjectName = NamingUtils.normalizeName(projectName)
        val buildTypeName = "${normalizedProjectName}_${NamingUtils.normalizeName(buildConfigurationName)}"
        return ClassName("${normalizedProjectName}.buildTypes", buildTypeName)
    }
}
