package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

data class PrintVariable(
    val configurationVariable: String,
    val promptVariable: String
) : IBuildTypeDefinition