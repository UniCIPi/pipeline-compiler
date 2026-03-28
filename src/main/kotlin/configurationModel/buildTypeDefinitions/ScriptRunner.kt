package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.bringauto.pipelinecompiler.pipeline.Artifact
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.Parameter

/**
 * Script runner is a special build step that inlines a script into the build configuration.
 * It is useful for defining single-use-case build steps without creating a new build type.
 */
data class ScriptRunner(
    /**
     * Name of the build configuration
     */
    val configurationName: String,
    /**
     * List of build steps to execute in order. Each build step is a script.
     */
    val scripts: List<ScriptSpecification>,
    /**
     * If set, the ssh-agent feature will be enabled and the specified key will be added to the agent.
     * The key (with the same name) has to be defined in the target platform.
     */
    val sshAgentKeyName: String = "",
    /**
     * List of { source, destination? }. Defines artifacts published by the build step.
     */
    val publishArtifacts: List<Artifact> = listOf(),
    /**
     * List of parameters that can be used in the build configuration.
     * Can be any of the types implementing Parameter.
     * @see Parameter for details.
     */
    val parameters: List<Parameter> = listOf()
) : IBuildTypeDefinition

/**
 * Specifications of a script that will be executed and its environment.
 */
data class ScriptSpecification(
    /**
     * Name of the build step.
     */
    val stepName: String,
    /**
     * Script file name. The name is searched in `scripts` directory adjacent to the build configuration file.
     */
    val file: String,
    /**
     * Docker image to be used for the build step.
     */
    val dockerImage: String = "",
    /**
     * Additional arguments to be passed to the `docker run` command.
     */
    val dockerArguments: String = "",
    /**
     * Allow Docker in Docker for this build step. May be required for some build steps that need to run Docker commands.
     * May be a security risk, use with caution.
     */
    val allowDockerInDocker: Boolean = false,

    /**
     * List of conditions for the build step to be executed.
     * @see BuildStep.RunCondition
     */
    val runConditions: List<BuildStep.RunCondition> = emptyList(),
)