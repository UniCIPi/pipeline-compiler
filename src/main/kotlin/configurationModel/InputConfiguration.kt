package fanci.compiler.configurationModel

import fanci.compiler.pipeline.PullRequestConfiguration
import fanci.compiler.pipeline.VcsAuthMethod

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
 * The entity contains build configurations for the component.
 */
data class Component(
    val name: String,
    val repository: String,
    val defaultBranch: String = "main",
    val vcsAuthMethod: VcsAuthMethod? = null,
    val submoduleStrategy: SubmoduleStrategy = SubmoduleStrategy.none,
    /**
     * List of build configurations of the component.
     */
    val buildConfigurations: List<BuildConfigurationDefinition>,
    /**
     * Pull request (merge request) support of the component.
     * Pull requests are supported by default; the section only narrows the defaults or disables the support.
     */
    val pullRequest: PullRequestConfiguration? = null
)

data class InputConfiguration(
    val variables: Map<String, String>?, // NOT USED
    val component: Component
)
