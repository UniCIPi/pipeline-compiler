package com.bringauto.pipelinecompiler.generator

import com.bringauto.pipelinecompiler.generator.platforms.gitlab.GitLab
import com.bringauto.pipelinecompiler.generator.platforms.teamcity.TeamCity
import com.bringauto.pipelinecompiler.pipeline.PipelineSettings
import java.io.File
import java.nio.file.Path

/**
 * Interface for platform-specific pipeline generators.
 *
 * This interface defines the methods required to generate pipeline configuration files for different platforms
 * Each platform will implement this interface to provide its own specific logic
 * for generating the pipeline configurations.
 */
interface IPlatform {
    /**
     * dependency manager handles dependencies chains in build configurations
     */
    val dependencyManager: DependencyManager

    /**
     * Dictionary of platform-specific parameter mappings.
     *
     * Maps generic parameter names (used in pipeline settings) to platform-specific variable names.
     * These mappings are applied before the parameter formatting functions.
     * @see StandardParameter
     * @sample GitLab.platformParameters
     */
    val platformParameters: Map<String, String>
    /**
     * Standard parameters that should be supported by all platforms.
     * These names are used in the pipeline settings (e.g. %pipelineGetParam(PIPELINE_BUILD_BRANCH)%)
     */
    enum class StandardParameter {
        /** current build branch */
        PIPELINE_BUILD_BRANCH,
        /** current build job build number */
        PIPELINE_BUILD_NUMBER,
        /** counter of build configurations executions */
        PIPELINE_BUILD_COUNTER,
        /** default branch of build job owning component repository */
        PIPELINE_DEFAULT_BRANCH,
        /** owning component's repository URL address */
        PIPELINE_COMPONENT_URL
    }

    /**
     * Generate a pipeline configuration files for the given settings.
     */
    fun generatePipeline(pipelineSettings: PipelineSettings, targetDirectory: Path): String

    /**
     * Define the platform Variable GETer format.
     *
     * The format used to replace the get parameter placeholders (`%pipelineGetParam(VAR)%`) in the pipeline settings
     * @see com.bringauto.pipelinecompiler.generator.ParameterReplacer.replaceParametersInPipeline
     * @sample TeamCity.parameterGetFormat
     */
    fun parameterGetFormat(parameterName: String): String

    /**
     * Define the platform Variable SETter format.
     *
     * The format used to replace the set parameter placeholders (`%pipelineSetParam(VAR)%`) in the pipeline settings
     * @see com.bringauto.pipelinecompiler.generator.ParameterReplacer.replaceParametersInPipeline
     * @sample TeamCity.parameterSetFormat
     *
     */
    fun parameterSetFormat(parameterName: String, value: String): String

    /**
     * Enum representing the types of platforms supported.
     */
    enum class PlatformType {
        GitLab,
        TeamCity
    }

    /**
     * Platform generator factory as part of interface
     */
    companion object {
        fun createPlatform(
            type: PlatformType,
            dependencyManager: DependencyManager,
            platformEnvFile: File? // File can be passed to describe target platform environment
        ): IPlatform {
            val platform = when (type) {
                PlatformType.GitLab -> GitLab(dependencyManager, platformEnvFile)
                PlatformType.TeamCity -> TeamCity(dependencyManager, platformEnvFile)
            }
            // Check that all required pipeline parameters are implemented
            StandardParameter.entries.forEach { param ->
                require(platform.platformParameters.containsKey(param.name)) {
                    "Platform ${this::class.simpleName} does not implement required standard parameter: ${param.name}"
                }
            }
            return platform
        }
    }
}