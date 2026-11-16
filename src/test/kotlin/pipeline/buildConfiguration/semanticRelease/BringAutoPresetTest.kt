package fanci.compiler.pipeline.buildConfiguration.semanticRelease

import fanci.compiler.configurationModel.Component
import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.configurationModel.buildTypeDefinitions.SemanticRelease
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.presets.BringAutoPreset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BringAutoPresetTest {

    private fun createPipelineSettings(repository: String = "https://github.com/example/repo.git"): PipelineSettings {
        val component = Component(
            name = "test-component",
            repository = repository,
            buildConfigurations = listOf()
        )
        return PipelineSettings(InputConfiguration(variables = null, component = component))
    }

    private fun createConfig(versionFilePath: String?, preset: String = "BringAuto"): SemanticRelease {
        return SemanticRelease(
            preset = preset,
            versionFilePath = versionFilePath,
            repoAccessTokenId = "token"
        )
    }

    @Test
    fun `test addVersioningScheme with version txt`() {
        val preset = BringAutoPreset(createPipelineSettings(), createConfig("version.txt"))

        val plugins = preset.serializeToJson()
        assertTrue(plugins.contains("version.txt"))
        assertTrue(plugins.contains("semantic-release-replace-plugin"))
    }

    @Test
    fun `test addVersioningScheme with pyproject toml`() {
        val preset = BringAutoPreset(createPipelineSettings(), createConfig("pyproject.toml"))

        val plugins = preset.serializeToJson()
        assertTrue(plugins.contains("pyproject.toml"))
        assertTrue(plugins.contains("semantic-release-replace-plugin"))
    }

    @Test
    fun `test addVersioningScheme with unknown extension`() {
        val preset = BringAutoPreset(createPipelineSettings(), createConfig("unknown.file"))

        val plugins = preset.serializeToJson()
        assertFalse(plugins.contains("semantic-release-replace-plugin"))
    }

    @Test
    fun `preset is selected by its name`() {
        val configuration = SemanticReleaseConfiguration.createFromSettings(createPipelineSettings(), createConfig(null))
        assertTrue(configuration is BringAutoPreset)
    }

    @Test
    fun `unknown preset is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticReleaseConfiguration.createFromSettings(createPipelineSettings(), createConfig(null, preset = "Unknown"))
        }
    }
}
