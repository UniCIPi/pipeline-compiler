package fanci.compiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Runs Go tests using the specified Go version.
 *
 * @see fanci.compiler.pipeline.buildConfiguration.GoTest
 */
data class GoTest(
    @JsonProperty("go-version")
    val goVersion: String = "1.24",
) : IBuildTypeDefinition