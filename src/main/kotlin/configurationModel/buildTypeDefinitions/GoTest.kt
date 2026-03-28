package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Runs Go tests using the specified Go version.
 *
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.GoTest
 */
data class GoTest(
    @JsonProperty("go-version")
    val goVersion: String = "1.24",
) : IBuildTypeDefinition