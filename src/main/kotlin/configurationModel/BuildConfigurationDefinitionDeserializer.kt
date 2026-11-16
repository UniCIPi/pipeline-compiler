package fanci.compiler.configurationModel

import fanci.compiler.pipeline.Dependency
import fanci.compiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Deserializer for BuildConfigurationDefinition.
 * This class is responsible for converting input YAML data into a BuildConfigurationDefinition object.
 * It strips the fields common to every build configuration (dependencies, triggers, free-disk-space-gb,
 * docker-image, docker-connections, build-number-pattern) from the node and maps the remaining fields to the specific BuildTypeDefinition.
 */
class BuildConfigurationDefinitionDeserializer : JsonDeserializer<BuildConfigurationDefinition>() {
    private val mapper = jacksonObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)


    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): BuildConfigurationDefinition {
        val node: JsonNode = parser.codec.readTree(parser)

        val dependencies = node["dependencies"]?.let {
            mapper.treeToValue(it, Array<Dependency>::class.java).toList()
        } ?: emptyList()

        val freeDiskSpaceGb = node["free-disk-space-gb"]?.asInt() ?: 0

        val dockerImage = node["docker-image"]?.asText() ?: ""

        val dockerConnections = node["docker-connections"]?.let {
            mapper.treeToValue(it, Array<String>::class.java).toList()
        } ?: emptyList()

        val buildNumberPattern = node["build-number-pattern"]?.asText()

        val triggers = node["triggers"]?.let {
            mapper.treeToValue(it, Array<fanci.compiler.pipeline.Trigger>::class.java).toList()
        } ?: emptyList()

        // Remove general fields from the node before mapping to IBuildType
        if (node is ObjectNode) {
            node.remove("dependencies")
            node.remove("triggers")
            node.remove("free-disk-space-gb")
            node.remove("docker-image")
            node.remove("docker-connections")
            node.remove("build-number-pattern")
        }

        // Extract buildType
        val buildType = mapper.treeToValue(node, IBuildTypeDefinition::class.java)

        return BuildConfigurationDefinition(
            buildType,
            dependencies,
            triggers,
            freeDiskSpaceGb,
            dockerImage,
            dockerConnections,
            buildNumberPattern
        )
    }
}