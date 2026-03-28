package com.bringauto.pipelinecompiler.configurationModel

import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Deserializer for BuildStepConfiguration.
 * This class is responsible for converting input YAML data into a BuildStepConfiguration object.
 * It handles adding general fields (dependencies, workDir, freeSpaceGb, dockerConnections) into specific BuildTypeDefinition.
 */
class BuildStepConfigurationDeserializer : JsonDeserializer<BuildStepConfiguration>() {
    private val mapper = jacksonObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)


    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): BuildStepConfiguration {
        val node: JsonNode = parser.codec.readTree(parser)

        val dependencies = node["dependencies"]?.let {
            mapper.treeToValue(it, Array<Dependency>::class.java).toList()
        } ?: emptyList()

        val freeSpaceGb = node["free-space-gb"]?.asInt() ?: 0

        val dockerConnections = node["docker-connections"]?.let {
            mapper.treeToValue(it, Array<String>::class.java).toList()
        } ?: emptyList()

        val buildNumberPattern = node["build-number-pattern"]?.asText()

        val triggers = node["triggers"]?.let {
            mapper.treeToValue(it, Array<com.bringauto.pipelinecompiler.pipeline.Trigger>::class.java).toList()
        } ?: emptyList()

        // Remove general fields from the node before mapping to IBuildType
        if (node is ObjectNode) {
            node.remove("dependencies")
            node.remove("triggers")
            node.remove("free-space-gb")
            node.remove("docker-connections")
            node.remove("build-number-pattern")
        }

        // Extract buildType
        val buildType = mapper.treeToValue(node, IBuildTypeDefinition::class.java)

        return BuildStepConfiguration(buildType, dependencies, triggers, freeSpaceGb, dockerConnections, buildNumberPattern)
    }
}