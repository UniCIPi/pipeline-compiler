package fanci.compiler.generator

import fanci.compiler.generator.platforms.gitlab.GitLab
import fanci.compiler.generator.platforms.teamcity.TeamCity
import fanci.compiler.pipeline.PipelineSettings
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
     * These names are used in the pipeline settings (e.g. %fanciGetParam(FANCI_BUILD_BRANCH)%)
     */
    enum class StandardParameter {
        /** current build branch */
        FANCI_BUILD_BRANCH,
        /** current build job build number */
        FANCI_BUILD_NUMBER,
        /** counter of build configurations executions */
        FANCI_BUILD_COUNTER,
        /** default branch of build job owning component repository */
        FANCI_DEFAULT_BRANCH,
        /** owning component's repository URL address */
        FANCI_COMPONENT_URL,
        /** The full path, where the repository is cloned when build configuration is run */
        FANCI_CHECKOUT_DIR,
    }

    /**
     * Generate a pipeline configuration files for the given settings.
     */
    fun generatePipeline(pipelineSettings: PipelineSettings, targetDirectory: Path): String

    /**
     * Define the platform Variable GETer format.
     *
     * The format used to replace the get parameter placeholders (`%fanciGetParam(VAR)%`) in the pipeline settings
     * @see fanci.compiler.generator.ParameterReplacer.replaceParametersInPipeline
     * @sample TeamCity.parameterGetFormat
     */
    fun parameterGetFormat(parameterName: String): String

    /**
     * Define the platform Variable SETter format.
     *
     * The format used to replace the set parameter placeholders (`%fanciSetParam(VAR)%`) in the pipeline settings
     * @see fanci.compiler.generator.ParameterReplacer.replaceParametersInPipeline
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