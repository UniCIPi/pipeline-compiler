package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.Composite
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.IBuildConfiguration

/**
 * The composite build configuration is used to aggregate the results of a build chain in one build configuration.
 *
 * It acts as a single place for all inputs and outputs.
 * It exposes all dependency artifacts as its own build artifacts.
 * It should define all triggers of the build-chain. When a trigger is fired, all dependencies in the build chain are run.
 * It can override the parameters of dependencies.
 */
class Composite(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: Composite)
    : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Composite"
    override val buildSteps = listOf<BuildStep>()
    override val parameters = buildTypeDefinition.parameters
    override val type = IBuildConfiguration.Type.COMPOSITE
    /** TODO not done:
     * No VCS roots - maybe not necessary
     * Should check if has any trigger and put Warning if not?
     */

}