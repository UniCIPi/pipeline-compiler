package fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins

sealed interface IPlugin {
    val name: String
    fun serializeConfiguration(): Map<String, Any>
}