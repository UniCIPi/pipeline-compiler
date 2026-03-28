package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

data class StateSmurfTest(
    val executable: String,
    val scenarios: List<String>
) : IBuildTypeDefinition
