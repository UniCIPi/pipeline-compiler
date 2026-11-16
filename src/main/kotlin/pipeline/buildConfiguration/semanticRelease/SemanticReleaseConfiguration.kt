package fanci.compiler.pipeline.buildConfiguration.semanticRelease

import fanci.compiler.configurationModel.buildTypeDefinitions.SemanticRelease
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins.IPlugin
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.presets.BringAutoPreset
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Class for managing semantic release configuration and plugins
 *
 * semantic-release https://semantic-release.gitbook.io/semantic-release is a tool for fully automated version management and package publishing.
 * It uses conventional commits to determine the next version number and generate release notes.
 * It is configured via plugins and release branches.
 *
 * This class is responsible for creating the semantic release configuration of a component from the fields of
 * the SemanticRelease build type.
 *
 * This class can be inherited to create a custom plugin preset. @see BringAutoPreset
 * Configuring plugins serve for defining commit messages types, changelog generation, publishing, etc.
 */
abstract class SemanticReleaseConfiguration(
    protected val pipeline: PipelineSettings,
    protected val config: SemanticRelease
) {
    abstract val releaseBranches: List<ReleaseBranch>
    val repositoryUrl = pipeline.vcsRootUrl
    protected abstract val pluginsList: MutableList<IPlugin>
    protected fun addPlugin(plugin: IPlugin) {
        pluginsList.add(plugin)
    }

    /**
     * Create a JSON configuration for semantic-release
     * This JSON can be pasted into .releaserc file, where semantic-release will load it
     */
    fun serializeToJson(): String {
        val mapper = jacksonObjectMapper()
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)

        // Handle plugins serialization
        val pluginsJson: List<Any> = pluginsList.map { plugin ->
            val cfg = plugin.serializeConfiguration()
            if (cfg.isEmpty()) {
                plugin.name
            } else {
                listOf(plugin.name, cfg)
            }
        }
        val root = mapOf(
            "branches" to releaseBranches,
            "repositoryUrl" to repositoryUrl,
            "plugins" to pluginsJson
        )
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    companion object {
        /**
         * Factory method to create a SemanticRelease instance based on the provided configuration
         */
        fun createFromSettings(
            pipeline: PipelineSettings,
            config: SemanticRelease
        ): SemanticReleaseConfiguration = when (config.preset) {
            "BringAuto" -> BringAutoPreset(pipeline, config)
            else                  -> throw IllegalArgumentException("Unknown preset: '${config.preset}'")
        }
    }
}
