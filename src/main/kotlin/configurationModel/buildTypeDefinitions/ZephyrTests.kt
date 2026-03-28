package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Configuration for Zephyr CMake tests.
 * This class holds the image matrix for running tests across different Docker images.
 *
 * @param imageMatrix List of Docker image names to run tests against.
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.ZephyrTests
 */
class ZephyrTests(
    @JsonProperty("image-matrix")
    val imageMatrix: List<String>
) : IBuildTypeDefinition