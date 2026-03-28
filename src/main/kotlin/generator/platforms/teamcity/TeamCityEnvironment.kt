package com.bringauto.pipelinecompiler.generator.platforms.teamcity

import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * TeamCityEnvironmentParser is responsible for parsing the TeamCity environment configuration file.
 * It implements the IEnvironmentHelper interface.
 *
 * @param envFile The file containing the TeamCity environment configuration.
 */
class TeamCityEnvironmentParser(envFile: File) {
    private var configData: TeamCityEnvironment = ObjectMapper().registerKotlinModule().readValue<TeamCityEnvironment>(envFile)

    fun getSshKeyName(repository: String): String {
        // 1. Find key where repositoryContains is not null and is contained in repository
        configData.sshKeys.firstOrNull {
            it.repositoryContains != null && repository.contains(it.repositoryContains)
        }?.let { return it.name }

        // 2. Find universal key (repositoryContains is null)
        configData.sshKeys.firstOrNull { it.repositoryContains == null }?.let { return it.name }

        // 3. Not found
        throw IllegalArgumentException("No SSH key found for repository: $repository")
    }

    fun isSshKeyDefined(sshKeyName: String): Boolean {
        return configData.sshKeys.any { it.name == sshKeyName }
    }

    fun areDockerRegistriesDefined(dockerRegistries: List<String>): Boolean {
        dockerRegistries.forEach { dockerRegistryId ->
            if (configData.dockerRegistries.none { it.name == dockerRegistryId }) {
                return false
            }
        }
        return true
    }

    fun getDockerRegistryNames(buildConfiguration: IBuildConfiguration): String {
        if (buildConfiguration.buildSteps.all { it.dockerImage.isBlank() }) {
            return ""
        }
        /// docker image can be defined in variable, matrix, a script -> its not possible to filter by repositoryContains
        return configData.dockerRegistries.joinToString(",") { it.name }
    }
}

data class TeamCityEnvironment(
    val sshKeys: List<SshKey> = emptyList(),
    val dockerRegistries: List<DockerRegistry> = emptyList(),
    val versionedSettingsToken: List<VersionedSettingsToken> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SshKey(
    val repositoryContains: String? = null,
    val name: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DockerRegistry(
    val name: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VersionedSettingsToken(
    val name: String,
    val description: String
)