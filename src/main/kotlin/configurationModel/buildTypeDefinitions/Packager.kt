package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonProperty

data class Packager(
    @JsonProperty("context-repository")
    val contextRepository: String
) : IBuildTypeDefinition
