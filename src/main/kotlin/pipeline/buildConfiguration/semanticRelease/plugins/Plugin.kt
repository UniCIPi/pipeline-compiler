package fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins

data class Plugin(
    override val name: String,
    private val config: Map<String, Any> = emptyMap()
) : IPlugin {
    override fun serializeConfiguration(): Map<String, Any> = config
}