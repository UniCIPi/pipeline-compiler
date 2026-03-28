package com.bringauto.pipelinecompiler.generator.platforms.gitlab

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

class GitLabEnvironmentHelper(envFile: File?) {
    private var configData: GitLabEnvironment = envFile?.let { ObjectMapper(YAMLFactory()).registerKotlinModule().readValue<GitLabEnvironment>(envFile) } ?: GitLabEnvironment()

    fun areTagsDefined(): Boolean {
        return configData.tags.isNotEmpty()
    }

    fun getTags(): List<String> {
        return configData.tags
    }

    fun getDindConfiguration(): Dind? {
        return configData.dind
    }
}

data class GitLabEnvironment(
    val tags: List<String> = emptyList(),
    val dind: Dind? = null,

)

data class Dind(
    val tags: List<String> = emptyList(),
    val dockerHost: String? = null,
    val dockerCertDir: String? = null,
)
