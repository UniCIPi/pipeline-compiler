package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Configuration for Zephyr Cmake builds.
 *
 * A build is started for each combination of board and image.
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ZephyrBuild
 */
data class ZephyrBuild (
    val boards: List<String>,
    @JsonProperty("image-matrix")
    val imageMatrix: List<String>
) : IBuildTypeDefinition