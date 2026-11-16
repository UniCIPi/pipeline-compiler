package fanci.compiler.pipeline

import fanci.compiler.common.VCSHelper
import fanci.compiler.common.VCSType
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.InputConfiguration
import fanci.compiler.configurationModel.SubmoduleStrategy

/**
 * PipelineSettings holds the normalized configuration for a pipeline/component.
 * It is parsed from the [InputConfiguration] and contains all information needed to generate a build pipeline for a component.
 *
 * It represents a component of the formal model with its build configurations. Invariants:
 * - [defaultBranch] must be non‑empty
 * - [vcsRootUrl] should be a valid absolute URI (stored here as String; validated upstream)
 */
class PipelineSettings(inputConfiguration: InputConfiguration) {
    /**
     * Fully Qualified Name of the component.
     */
    val componentName: String = inputConfiguration.component.name


    /**
     * Version Control System (VCS) root URL of the repository.
     */
    val vcsRootUrl: String = inputConfiguration.component.repository

    /**
     * Default branch for the VCS root.
     */
    val defaultBranch: String = inputConfiguration.component.defaultBranch

    /**
     * Authentication method for accessing the VCS root (token or uploaded SSH key), if configured.
     */
    var vcsAuthMethod: VcsAuthMethod? = inputConfiguration.component.vcsAuthMethod

    /**
     * Submodule strategy for the VCS root.
     * This defines if and how submodules are handled during the checkout process.
     */
    val submoduleStrategy: SubmoduleStrategy = inputConfiguration.component.submoduleStrategy

    /**
     * VCS host system hosting the repository, resolved from the [vcsRootUrl].
     * Branch naming of pull requests and the available platform integrations depend on it.
     */
    val vcsType: VCSType = VCSHelper.resolveType(VCSHelper.extractHostname(vcsRootUrl))

    /**
     * Pull request (merge request) support of the component.
     * Defaults to support of all pull requests when the component does not configure it.
     *
     * Mutable, because the parameter placeholders of its token are replaced by the target platform format.
     */
    var pullRequestConfiguration: PullRequestConfiguration =
        inputConfiguration.component.pullRequest ?: PullRequestConfiguration()

    /**
     * List of BuildConfigurations for the component.
     * Each BuildConfiguration represents one comprehensive CICD process in the component build pipeline.
     */
    val buildConfigurations: List<IBuildConfiguration> = createBuildConfigurations(inputConfiguration.component.buildConfigurations)

    /**
     * Creates a list of BuildConfigurations from the given BuildConfigurationDefinition list.
     */
    fun createBuildConfigurations(buildConfigurationDefinitions: List<BuildConfigurationDefinition>): List<IBuildConfiguration> =
        buildConfigurationDefinitions.map { definition -> IBuildConfiguration.createBuildConfiguration(definition, this) }
}
