package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition

/**
 * Abstract base class for build configurations.
 * Initialize common properties and methods for all build configurations.
 *
 * @param definition Input-layer definition of the build configuration.
 */
abstract class AbstractBuildConfiguration(definition: BuildConfigurationDefinition)
    : IBuildConfiguration {
    /**
     * BuildTypeDefinition to override in subclasses.
     * Serves to provide the specific build type configuration, so the inheritor can use specific properties safely.
     */
    abstract val buildTypeDefinition: IBuildTypeDefinition

    override val dependencies: List<Dependency> = definition.dependencies
    override val freeDiskSpaceGb: Int = definition.freeDiskSpaceGb
    override var dockerImage: String = definition.dockerImage
    override val triggers = definition.triggers
    override val dockerConnections: List<String> = definition.dockerConnections
    override var buildNumberPattern = definition.buildNumberPattern
}
