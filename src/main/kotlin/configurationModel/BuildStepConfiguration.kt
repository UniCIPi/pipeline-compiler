package com.bringauto.pipelinecompiler.configurationModel

import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(using = BuildStepConfigurationDeserializer::class)
data class BuildStepConfiguration(
    /**
     * Specific BuildType configuration.
     */
    val buildTypeDefinition: IBuildTypeDefinition,
    /**
     * List of dependencies required for this build step. Creates build chains. Set by user in configuration.
     * Must be supported by every build configuration.
     */
    val dependencies: List<Dependency>,
    /**
     * Triggers for this build step. Can be defined for any build type.
     */
    val triggers: List<com.bringauto.pipelinecompiler.pipeline.Trigger>,
    /**
     * Required disk space for the build step in GB.
     */
    val freeSpaceGb: Int,
    /**
     * List of Docker connection IDs that this build step requires.
     */
    val dockerConnections: List<String>,

    /**
     * Build number format.
     *
     * Not supported by GitLab CI/CD
     */
    val buildNumberPattern: String? = null
)