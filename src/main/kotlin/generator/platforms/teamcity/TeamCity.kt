package com.bringauto.pipelinecompiler.generator.platforms.teamcity

import com.bringauto.pipelinecompiler.common.ProjectPath
import com.bringauto.pipelinecompiler.generator.DependencyManager
import com.bringauto.pipelinecompiler.generator.IPlatform
import com.bringauto.pipelinecompiler.generator.platforms.teamcity.NamingUtils.createVcsRootClassName
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.PipelineSettings
import com.squareup.kotlinpoet.ClassName
import java.io.File
import java.nio.file.Path

/**
 * TeamCity platform generator.
 *
 * This class implements the generating of TeamCity pipeline configuration files.
 * It uses KotlinPoet to generate the necessary Kotlin code
 * for the TeamCity build configurations, VCS roots, and project settings.
 */
class TeamCity(override val dependencyManager: DependencyManager, envFile: File?) : IPlatform {
    val environmentHelper = envFile?.let { TeamCityEnvironmentParser(envFile) }
    /**
     * The name of the TeamCity project (component). Normalized to PascalCase.
     */
    private lateinit var normalizedProjectName: String

    /**
     * The class name for the VCS root, which is used in the generated Kotlin code.
     * It is derived from the normalized project name.
     */
    private lateinit var vcsRootClassName: ClassName

    /**
     * A map of build type names to their corresponding ClassName objects.
     * This is used to generate the build configurations in the Kotlin code.
     */
    private lateinit var buildTypesClassName: MutableMap<String, ClassName>
    private lateinit var pipelineSettings: PipelineSettings

    override fun generatePipeline(pipelineSettings: PipelineSettings, targetDirectory: Path): String {
        this.pipelineSettings = pipelineSettings
        normalizedProjectName = NamingUtils.normalizeName(pipelineSettings.componentName)
        vcsRootClassName = createVcsRootClassName(pipelineSettings.componentName)
        buildTypesClassName = mutableMapOf()
        checkEnvFile()

        createBuildConfigurationsClassNames(pipelineSettings.buildConfigurations)

        generateVcsRoot(targetDirectory)
        generateProject(targetDirectory)
        pipelineSettings.buildConfigurations.forEach {
            generateBuildConfiguration(it, targetDirectory)
        }

        return "TeamCity pipeline"
    }

    private fun checkEnvFile() {
        for (buildConfiguration in pipelineSettings.buildConfigurations) {
            if (environmentHelper != null && environmentHelper.areDockerRegistriesDefined(buildConfiguration.dockerConnections)) {
                throw IllegalArgumentException("Docker registry $buildConfiguration.dockerConnections is not defined in the environment file.")
            }
        }
        // TODO check SSH keys...
    }

    override val platformParameters: Map<String, String> = mapOf(
        IPlatform.StandardParameter.PIPELINE_BUILD_BRANCH.name to "teamcity.build.branch",
        IPlatform.StandardParameter.PIPELINE_BUILD_NUMBER.name to "build.number",
        IPlatform.StandardParameter.PIPELINE_BUILD_COUNTER.name to "build.counter",
        IPlatform.StandardParameter.PIPELINE_DEFAULT_BRANCH.name to "vcsroot.branch",
        IPlatform.StandardParameter.PIPELINE_COMPONENT_URL.name to "vcsroot.url"
    )

    /**
     * Get parameter format.
     */
    override fun parameterGetFormat(parameterName: String): String {
        if (ProjectPath.isFullPath(parameterName)) {
            val projectPath = ProjectPath.parse(parameterName)
            val normalized = NamingUtils.normalizeName(ProjectPath.projectOf(projectPath).toString())
            val param = projectPath.last()
//            return "${'$'}{$normalized.depParamRefs[\"$last\"]}" not working because of $
            // Need real full ID of the build configuration - relative to <root_project> of teamcity, not current versioned settings
            return "%dep.$normalized.$param%"
            // Solution might be to replace variables after creating files -> Easy to find all variables and replace them
            // generatePipeline can return all created files
        } else {
            return "%$parameterName%"
        }
    }

    /**
     * TeamCity uses the format `echo "##teamcity[setParameter name='variableName' value='value']"`
     * to set a parameter in the build.
     *
     * IMPORTANT: The parameter value is only updated at the end of the build step -> the value is updated only in the subsequent steps.
     */
    override fun parameterSetFormat(parameterName: String, value: String): String {
        if (ProjectPath.isFullPath(parameterName)) {
            throw IllegalArgumentException("Setting a parameter by full path is not supported in TeamCity: $parameterName\n" +
                    "Overriding arguments from preceding builds is allowed in Parameter.name. See ComponentChaining.md document for more information.")
        }
        return "echo \"##teamcity[setParameter name='$parameterName' value='$value']\""
    }

    /**
     * Creates a VCS root file
     */
    private fun generateVcsRoot(targetDirectory: Path) {
        // TODO verify vcs auth in environment file
        /*var sshKeyName = ""
        if (!pipelineSettings.sshKeyName.isBlank()) {
            if (!environmentHelper.isSshKeyDefined(pipelineSettings.sshKeyName)) {
                throw IllegalArgumentException("SSH key $sshKeyName is not defined in the environment file.")
            }
            sshKeyName = pipelineSettings.sshKeyName
        }*/
        val defaultBranch = pipelineSettings.defaultBranch
        val vcsFileSpec = KotlinBuilder.generateVcsFileSpec(
            vcsRootClassName,
            normalizedProjectName,
            pipelineSettings.vcsRootUrl,
            defaultBranch,
            pipelineSettings.vcsAuthMethod
        )
        vcsFileSpec.writeTo(targetDirectory)
    }

    /**
     * Generates the TeamCity project file.
     * This file contains the project settings and references to the VCS root and build configurations.
     */
    private fun generateProject(targetDirectory: Path) {
        val projectFileSpec = KotlinBuilder.generateProjectFileSpec(
            NamingUtils.extractProjectName(pipelineSettings.componentName),
            normalizedProjectName,
            vcsRootClassName,
            buildTypesClassName.values
        )
        projectFileSpec.writeTo(targetDirectory)
    }

    /**
     * Creates a build configuration file for the given build configuration.
     */
    private fun generateBuildConfiguration(buildConfiguration: IBuildConfiguration, targetDirectory: Path) {
        val className = buildTypesClassName[buildConfiguration.name]
            ?: throw IllegalArgumentException("Build configuration class name for ${buildConfiguration.name} not found")

        val buildConfigurationFileSpec = KotlinBuilder.generateBuildConfigurationFileSpec(
            buildConfiguration,
            className,
            vcsRootClassName,
            dependencyManager
        )
        buildConfigurationFileSpec.writeTo(targetDirectory)
    }

    /**
     * Creates a map of build configuration names to their corresponding ClassName objects.
     * This is used to generate paths and imports correctly
     */
    private fun createBuildConfigurationsClassNames(buildConfiguration: List<IBuildConfiguration>) {
        buildConfiguration.forEach { config ->
            val buildTypeName = "${normalizedProjectName}_${NamingUtils.normalizeName(config.name)}"
            val className = ClassName("${normalizedProjectName}.buildTypes", buildTypeName)
            buildTypesClassName.put(config.name, className)
        }
    }

}