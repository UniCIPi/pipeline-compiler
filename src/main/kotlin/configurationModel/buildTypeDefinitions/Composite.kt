package fanci.compiler.configurationModel.buildTypeDefinitions

import fanci.compiler.pipeline.Parameter

/**
 * Composite build type definition.
 *
 * @see fanci.compiler.pipeline.buildConfiguration.Composite
 */
class Composite(
    /**
     * List of parameters for the composite build.
     * Used for user input and overriding dependency parameters.
     */
    val parameters: List<Parameter> = emptyList()
) : IBuildTypeDefinition