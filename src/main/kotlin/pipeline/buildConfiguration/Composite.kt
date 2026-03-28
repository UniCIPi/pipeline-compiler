package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.Composite
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration

/**
 * The composite build configuration is used to aggregate the results of a build chain in one build configuration.
 *
 * It acts as a single place for all inputs and outputs.
 * It exposes all dependency artifacts as its own build artifacts.
 * It should define all triggers of the build-chain. When a trigger is fired, all dependencies in the build chain are run.
 * It can override the parameters of dependencies.
 */
class Composite(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: Composite)
    : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Composite"
    override val buildSteps = listOf<BuildStep>()
    override val parameters = buildTypeDefinition.parameters
    override val type = IBuildConfiguration.Type.COMPOSITE
    /** TODO not done:
     * No VCS roots - maybe not necessary
     * Should check if has any trigger and put Warning if not?
     */

}