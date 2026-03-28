package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.bringauto.pipelinecompiler.pipeline.Parameter

/**
 * Composite build type definition.
 *
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.Composite
 */
class Composite(
    /**
     * List of parameters for the composite build.
     * Used for user input and overriding dependency parameters.
     */
    val parameters: List<Parameter> = emptyList()
) : IBuildTypeDefinition