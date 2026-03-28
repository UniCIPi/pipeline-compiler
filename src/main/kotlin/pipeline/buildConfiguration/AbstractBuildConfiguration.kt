package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition

/**
 * Abstract base class for build configurations.
 * Initialize common properties and methods for all build configurations.
 *
 * @param config Configuration model for the build step.
 */
abstract class AbstractBuildConfiguration(config: BuildStepConfiguration)
    : IBuildConfiguration {
    /**
     * BuildTypeDefinition to override in subclasses.
     * Serves to provide the specific build type configuration, so the inheritor can use specific properties safely.
     */
    abstract val buildTypeDefinition: IBuildTypeDefinition

    override val dependencies: List<Dependency> = config.dependencies
    override val freeDiskSpaceGb: Int = config.freeSpaceGb
    override val triggers = config.triggers
    override val dockerConnections: List<String> = config.dockerConnections
    override var buildNumberPattern = config.buildNumberPattern
}
