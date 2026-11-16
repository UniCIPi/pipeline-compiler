package fanci.compiler.pipeline

import fanci.compiler.configurationModel.InputConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fanci.compiler.pipeline.buildConfiguration.PyPiPublish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PyPiPublishParsingTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private fun publish(fields: String): PyPiPublish {
        val yaml = """
            |component:
            |  name: "::library"
            |  repository: "https://github.com/org/library.git"
            |  build-configurations:
            |  - type: PyPiPublish
            |${fields.trimIndent().prependIndent("    ")}
        """.trimMargin()
        val input: InputConfiguration = mapper.readValue(yaml)
        return assertIs<PyPiPublish>(PipelineSettings(input).buildConfigurations.single())
    }

    @Test
    fun `package is built and uploaded to PyPI by default`() {
        val configuration = publish(
            """
            docker-image: "python-poetry:3.13"
            token-id: "pypi-token"
            """
        )
        assertEquals("PyPI Publish", configuration.name)
        assertEquals("python-poetry:3.13", configuration.dockerImage)
        assertEquals(listOf("Build", "Publish"), configuration.buildSteps.map { it.name })
        assertTrue(configuration.buildSteps.last().script.contains("POETRY_PYPI_TOKEN_PYPI=%fanciGetParam(PYPI_TOKEN)%"))
        assertEquals(listOf("dist/*"), configuration.publishArtifacts.map { it.source })
    }

    @Test
    fun `token is a secret parameter referencing the token id`() {
        val token = assertIs<SecretParameter>(publish("token-id: \"pypi-token\"").parameters.single())
        assertEquals("PYPI_TOKEN", token.name)
        assertEquals("pypi-token", token.value)
    }

    @Test
    fun `repository url selects another package index`() {
        val configuration = publish(
            """
            token-id: "index-token"
            repository-url: "https://packages.example.com/legacy/"
            """
        )
        val script = configuration.buildSteps.last().script
        assertTrue(script.contains("poetry config repositories.target \"https://packages.example.com/legacy/\""))
        assertTrue(script.contains("poetry publish --repository target"))
    }

    @Test
    fun `token id is required`() {
        assertFails { publish("docker-image: \"python-poetry:3.13\"") }
    }
}
