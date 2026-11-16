package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.ScriptRunner
import fanci.compiler.configurationModel.buildTypeDefinitions.StepSpecification
import fanci.compiler.pipeline.BuildStep


/**
 * Special script runner build configuration.
 * The script of a build step is either stored outside of the pipeline-settings.yaml in a file,
 * or defined directly in the step configuration.
 * It is used to add custom one-purpose build steps without creating a dedicated build type.
 */
class ScriptRunner(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: ScriptRunner)
    : AbstractBuildConfiguration(buildConfigurationDefinition)
{
    override val name = buildTypeDefinition.configurationName

    override val buildSteps: MutableList<BuildStep> = mutableListOf()

    override val publishArtifacts = buildTypeDefinition.publishArtifacts

    override val junitReports = buildTypeDefinition.junitReports

    override val sshAgentKeyName = buildTypeDefinition.sshAgentKeyName

    override val parameters = buildTypeDefinition.parameters

    override val buildMatrix = buildTypeDefinition.matrix

    /**
     * Used to save information about the build steps whose scripts can be added later using addStep
     */
    val stepSpecifications = buildTypeDefinition.steps

    /**
     * Creates a build step from its specification and the given script content.
     *
     * @param stepSpecification specification of the build step and its environment
     * @param script content of the script executed by the build step
     */
    fun addStep(stepSpecification: StepSpecification, script: String) {
        buildSteps.add(BuildStep(
            stepSpecification.stepName,
            script,
            allowDockerInDocker = stepSpecification.allowDockerInDocker,
            dockerRunParameters = stepSpecification.dockerArguments,
            runConditions = stepSpecification.runConditions,
            executionMode = stepSpecification.executionMode,
        ))
    }
}
