package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.configurationModel.InputConfiguration

/**
 * NOT IMPLEMENTED YET
 */
class SystemRelease(inputConfiguration: InputConfiguration) : IBuildConfiguration {
    // TODO get whole pipeline configuration. It will extract all needed information from it
    // This breaks encapsulation a little.
    override val name = "System Release"
    override val buildSteps: List<BuildStep> = systemReleaseBuildStep(inputConfiguration)
    override val dependencies: MutableList<Dependency> = mutableListOf()
    override val matrix = emptyMap<String, List<String>>()
//    override val artifacts = emptyList<String>() // TODO way to pass failed build for rerun
    // TODO checkout rules??
    override var freeDiskSpaceGb: Int = 0

    private fun systemReleaseBuildStep(inputConfiguration: InputConfiguration): List<BuildStep> {
        if (inputConfiguration.systemDefinition == null || !inputConfiguration.systemDefinition.enabled) {
            throw IllegalArgumentException("System definition is not enabled") // TODO its valid to have no system definition
        }
        val systemDefinition = inputConfiguration.systemDefinition
        // TODO search for components, create json config with component versions -> bind to variables

        if (systemDefinition.automaticRelease != null && systemDefinition.automaticRelease.enabled) {
//            triggers[TriggerType.SCHEDULE] = systemDefinition.automaticRelease.cron
            // TODO isAutomatic flag for script - this has to be added in run -> can i get trigger?
            // Maybe if no variables are set, then it is automatic and the script will get them?
        }
        return listOf(BuildStep(
            name = "System Release",
            script = """
            #!/bin/bash
            create-release -format ${systemDefinition.releaseVersionFormat} 
            echo "Creating release"
        """.trimIndent(),
            dockerImage = "dockerhub.com/bringauto/system-release"))
    }

    // TODO must add variables with prompt - should this be done in generate?
}