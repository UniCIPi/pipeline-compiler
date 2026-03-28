package com.bringauto.pipelinecompiler.pipeline

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.CMake
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.GoPackage
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.GoTest
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ZephyrBuild
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ZephyrTests
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.PrintVariable
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.Composite
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.DockerBuildAndPush
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.KubectlApply
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ScriptRunner


/**
 * Build configuration encapsulates multiple coherent build steps that are executed in order.
 * It provides additional build metadata such as parameters, triggers, dependencies, and features,
 * which the platform‑specific generator transforms to the target CI system (e.g., TeamCity Kotlin DSL).
 *
 * See doc/FormalModel.md for the formal definition and invariants (section “Build Configuration”).
 * Key invariants summarized from the model:
 * - Name uniqueness: Each build configuration name is unique within the pipeline scope.
 * - Disk space: [freeDiskSpaceGb] ≥ 0.
 * - Steps presence: For type REGULAR or DEPLOYMENT, [buildSteps] must be non‑empty; for COMPOSITE it may be empty.
 * - Parameter uniqueness: Parameter names are unique within the configuration.
 * - Dependencies resolvable: All [dependencies] refer to existing builds.
 **/
interface IBuildConfiguration {
    /**
     * Name of the build configuration. Serves as an identifier.
     */
    val name: String

    /**
     * List of build steps - each build step is a script that will be executed in order in the build process.
     */
    val buildSteps: List<BuildStep>

    /**
     * List of builds this build configuration depends on.
     */
    val dependencies: List<Dependency>

    /**
     * Required free disk space in GB on the runner for this build.
     * Must be a non‑negative integer
     */
    val freeDiskSpaceGb: Int

    /**
     * Triggers that will start this build configuration. The build is also triggered when any dependency is triggered.
     */
    val triggers: List<Trigger> get() = emptyList()

    /**
     * Build matrix for this build configuration. A map of environment variable names to their value sets.
     * The configuration runs once for each Cartesian product of the value sets (see doc/FormalModel.md, “Build Matrix”).
     *
     * To use a matrix value in scripts or parameters, use the placeholder `%pipelineGetParam(matrix.key)%`.
     */
    val matrix: Map<String, List<String>> get() = emptyMap()

    /**
     * List of parameters available to the build configuration. Depending on the target platform and the
     * specific parameter type, some may be user‑settable for each build configuration execution (e.g., text/select) while others are internal.
     * Names must be unique within the configuration.
     */
    val parameters: List<Parameter> get() = emptyList()

    /**
     * List of artifacts that will be created by this build configuration.
     */
    val publishArtifacts: List<Artifact> get() = emptyList()

    /**
     * List of Docker connection IDs that this build step requires
     */
    val dockerConnections: List<String> get() = emptyList()

    /**
     * SSH Agent key to use for this build configuration.
     * If defined, the SSH Agent feature will be enabled for this build configuration. Must be supported by the platform.
     * IMPORTANT: The key gives access to private repositories. Use with caution and only with scripts you trust.
     */
    val sshAgentKeyName: String get() = ""

    /**
     * Build number pattern for this build configuration.
     * If defined, the Build Number feature will be enabled (subject to platform support).
     * The pattern is platform‑specific; use simple, portable tokens when possible.
     */
    var buildNumberPattern: String? get() = null; set(value) {}

    /**
     * Type of the build configuration.
     * @see Type
     */
    val type: Type get() = Type.REGULAR

    /// BuildStep factory
    companion object {
        fun createBuildConfiguration(buildStep: BuildStepConfiguration): IBuildConfiguration {
            val buildType = buildStep.buildTypeDefinition
            val buildConfiguration: IBuildConfiguration = when (buildType) {
                is CMake -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.CMake(buildStep, buildType)
                is GoPackage -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.GoPackage(buildStep, buildType)
                is GoTest -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.GoTest(buildStep, buildType)
                is ZephyrBuild -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ZephyrBuild(buildStep, buildType)
                is ZephyrTests -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ZephyrTests(buildStep, buildType)
                is PrintVariable -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.example.PrintVariable(buildStep, buildType)
                is Composite -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.Composite(buildStep, buildType)
                is ScriptRunner -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ScriptRunner(buildStep, buildType)
                is DockerBuildAndPush -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.DockerBuildAndPush(buildStep, buildType)
                is KubectlApply -> com.bringauto.pipelinecompiler.pipeline.buildConfiguration.KubectlApply(buildStep, buildType)
                else -> throw IllegalArgumentException("Unable to create Build configuration: Unknown build type: $buildType")
            }
            return buildConfiguration
        }
    }

    enum class Type {
        /**
         * Regular build configuration
         */
        REGULAR,
        /**
         * Aggregation build configuration. Automatically publishes all Dependency artifacts. Doesn't checkout repository.
         */
        COMPOSITE,
        /**
         * Build configuration with special permissions. Only one instance can run at a time. Might have different access rights. Might not be supported by all platforms.
         */
        DEPLOYMENT
    }
}