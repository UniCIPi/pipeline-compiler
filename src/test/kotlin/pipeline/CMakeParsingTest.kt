package fanci.compiler.pipeline

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.Component
import fanci.compiler.configurationModel.InputConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CMakeParsingTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    private val component = PipelineSettings(
        InputConfiguration(
            variables = null,
            component = Component(
                name = "::comp",
                repository = "https://github.com/org/comp.git",
                buildConfigurations = emptyList()
            )
        )
    )

    private fun buildConfiguration(nameLine: String): IBuildConfiguration {
        val yaml = """
            type: CMake
            $nameLine
            image-matrix:
              - ubuntu:24.04
            build-type: Release
            enable-tests: true
            create-package: false
            cmake-parameters:
              BUILD_TESTS: "ON"
        """.trimIndent()
        val definition: BuildConfigurationDefinition = mapper.readValue(yaml)
        return IBuildConfiguration.createBuildConfiguration(definition, component)
    }

    @Test
    fun `name defaults to CMake`() {
        assertEquals("CMake", buildConfiguration("").name)
    }

    @Test
    fun `name sets the build configuration name`() {
        val configuration = buildConfiguration("name: \"Build\"")
        assertEquals("Build", configuration.name)
    }

    @Test
    fun `blank name is rejected`() {
        assertFails { buildConfiguration("name: \" \"") }
    }
}
