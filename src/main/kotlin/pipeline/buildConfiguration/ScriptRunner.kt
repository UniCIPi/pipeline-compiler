package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ScriptRunner
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ScriptSpecification
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import java.io.File


/**
 * Special script runner build configuration.
 * The script content is stored outside of the pipeline-settings.yaml in a file.
 * It is used to add script content from a file for custom one-purpose build scripts.
 */
class ScriptRunner(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: ScriptRunner)
    : AbstractBuildConfiguration(buildStepConfiguration)
{
    override val name = buildTypeDefinition.configurationName

    override val buildSteps: MutableList<BuildStep> = mutableListOf()

    override val publishArtifacts = buildTypeDefinition.publishArtifacts

    override val sshAgentKeyName = buildTypeDefinition.sshAgentKeyName

    override val parameters = buildTypeDefinition.parameters

    /**
     * Used save information about the script files that can be added later using addScriptFile
     */
    val scriptSpecifications = buildTypeDefinition.scripts

    fun addScriptFile(scriptSpecification: ScriptSpecification, scriptFile: File) {
        buildSteps.add(BuildStep(
            scriptSpecification.stepName,
            scriptFile.readText(),
            scriptSpecification.dockerImage,
            allowDockerInDocker = scriptSpecification.allowDockerInDocker,
            dockerRunParameters = scriptSpecification.dockerArguments,
            runConditions = scriptSpecification.runConditions,
        ))
    }
}