package com.bringauto.pipelinecompiler.configurationModel

import com.bringauto.pipelinecompiler.pipeline.VcsAuthMethod

/**
 * Git submodule checkout strategy.
 */
enum class SubmoduleStrategy {
    /**
     * Checkout all submodules recursively.
     */
    recursive,
    /**
     * Checkout only the top-level submodule.
     */
    shallow,
    /**
     * Do not checkout any submodules.
     */
    none
}

/**
 * Define a component in the system.
 * A component is a repository.
 * It can be a library, an application, or any other codebase that is part of the system.
 *
 * The entity contains build steps for the component.
 */
data class Component(
    val name: String,
    val repository: String,
    val defaultBranch: String = "main",
    val vcsAuthMethod: VcsAuthMethod? = null,
    val submoduleStrategy: SubmoduleStrategy,
    val build: List<BuildStepConfiguration>
)

// Not used
data class SystemDefinition(
    val enabled: Boolean,
    val releaseVersionFormat: String,
    val components: List<String>,
    val automaticRelease: AutomaticRelease?
) {
    data class AutomaticRelease(
        val enabled: Boolean,
        val cron: String
    )
}

// Not used
data class ReleaseStep(
    val script: String
)

data class InputConfiguration(
    val variables: Map<String, String>?, // NOT USED
    val component: Component,
    val systemDefinition: SystemDefinition?, // NOT USED
    val releaseSteps: Map<String, ReleaseStep>? // NOT USED
)
