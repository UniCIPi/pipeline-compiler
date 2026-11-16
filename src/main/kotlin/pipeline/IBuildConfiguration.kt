package fanci.compiler.pipeline

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.CMake
import fanci.compiler.configurationModel.buildTypeDefinitions.GoPackage
import fanci.compiler.configurationModel.buildTypeDefinitions.GoTest
import fanci.compiler.configurationModel.buildTypeDefinitions.PrintVariable
import fanci.compiler.configurationModel.buildTypeDefinitions.Composite
import fanci.compiler.configurationModel.buildTypeDefinitions.DockerBuildAndPush
import fanci.compiler.configurationModel.buildTypeDefinitions.KubectlApply
import fanci.compiler.configurationModel.buildTypeDefinitions.ScriptRunner
import fanci.compiler.configurationModel.buildTypeDefinitions.SemanticRelease
import fanci.compiler.configurationModel.buildTypeDefinitions.PythonPackage
import fanci.compiler.configurationModel.buildTypeDefinitions.PyPiPublish


/**
 * Build configuration encapsulates multiple coherent build steps that are executed in order.
 * It provides additional build metadata such as parameters, triggers, dependencies, and features,
 * which the platform‑specific generator transforms to the target CI system (e.g., TeamCity Kotlin DSL).
 *
 * It represents a build configuration of the formal model.
 * Key invariants:
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
     * Docker image defining the execution environment of this build configuration.
     * It realises the container of the formal model; Docker is the concrete containerisation used here.
     * Every build step of the configuration is executed in it.
     * Empty means the build runs directly on the agent.
     *
     * Mutable, because the parameter placeholders in the image name are replaced by the target platform format.
     */
    var dockerImage: String get() = ""; set(value) {}

    /**
     * Triggers that will start this build configuration. The build is also triggered when any dependency is triggered.
     */
    val triggers: List<Trigger> get() = emptyList()

    /**
     * Build matrix for this build configuration. A map of parameter names to their value sets.
     * The configuration runs once for each element of the Cartesian product of the value sets.
     *
     * To use a matrix value in scripts or parameters, use the placeholder `%fanciGetParam(<matrix.key>)%`.
     */
    val buildMatrix: Map<String, List<String>> get() = emptyMap()

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
     * List of JUnit XML test reports produced by this build configuration, to be published as
     * platform test reports (TeamCity `xmlReport` feature / GitLab `artifacts.reports.junit`).
     */
    val junitReports: List<JUnitReport> get() = emptyList()

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
        /**
         * Create the build configuration of the given definition.
         *
         * @param component Component owning the build configuration, for the templates adapting to its repository.
         */
        fun createBuildConfiguration(
            buildConfigurationDefinition: BuildConfigurationDefinition,
            component: PipelineSettings
        ): IBuildConfiguration {
            val buildType = buildConfigurationDefinition.buildTypeDefinition
            val buildConfiguration: IBuildConfiguration = when (buildType) {
                is CMake -> fanci.compiler.pipeline.buildConfiguration.CMake(buildConfigurationDefinition, buildType)
                is GoPackage -> fanci.compiler.pipeline.buildConfiguration.GoPackage(buildConfigurationDefinition, buildType)
                is GoTest -> fanci.compiler.pipeline.buildConfiguration.GoTest(buildConfigurationDefinition, buildType)
                is PrintVariable -> fanci.compiler.pipeline.buildConfiguration.example.PrintVariable(buildConfigurationDefinition, buildType)
                is Composite -> fanci.compiler.pipeline.buildConfiguration.Composite(buildConfigurationDefinition, buildType)
                is ScriptRunner -> fanci.compiler.pipeline.buildConfiguration.ScriptRunner(buildConfigurationDefinition, buildType)
                is DockerBuildAndPush -> fanci.compiler.pipeline.buildConfiguration.DockerBuildAndPush(buildConfigurationDefinition, buildType)
                is KubectlApply -> fanci.compiler.pipeline.buildConfiguration.KubectlApply(buildConfigurationDefinition, buildType)
                is SemanticRelease -> fanci.compiler.pipeline.buildConfiguration.SemanticRelease(buildConfigurationDefinition, buildType, component)
                is PythonPackage -> fanci.compiler.pipeline.buildConfiguration.PythonPackage(buildConfigurationDefinition, buildType)
                is PyPiPublish -> fanci.compiler.pipeline.buildConfiguration.PyPiPublish(buildConfigurationDefinition, buildType)
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