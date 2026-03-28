package com.bringauto.pipelinecompiler.pipeline

import com.bringauto.pipelinecompiler.configurationModel.InputConfiguration
import com.bringauto.pipelinecompiler.configurationModel.SubmoduleStrategy

/**
 * PipelineSettings holds the normalized configuration for a pipeline/component.
 * It is parsed from the [InputConfiguration] and contains all information needed to generate a build pipeline for a component.
 *
 * See doc/FormalModel.md (section “Pipeline”) for the abstract definition and invariants, e.g.:
 * - [defaultBranch] must be non‑empty
 * - [vcsRootUrl] should be a valid absolute URI (stored here as String; validated upstream)
 */
class PipelineSettings(inputConfiguration: InputConfiguration) {
    /**
     * Fully Qualified Name of the component.
     */
    val componentName: String = inputConfiguration.component.name

    /**
     * List of BuildConfigurations for the component.
     * Each BuildConfiguration represents one comprehensive CICD process in the component build pipeline.
     */
    val buildConfigurations: List<IBuildConfiguration> = inputConfiguration.component.build.map { buildStep -> IBuildConfiguration.createBuildConfiguration(buildStep) }

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
    val vcsAuthMethod: VcsAuthMethod? = inputConfiguration.component.vcsAuthMethod

    /**
     * Submodule strategy for the VCS root.
     * This defines if and how submodules are handled during the checkout process.
     */
    val submoduleStrategy: SubmoduleStrategy = inputConfiguration.component.submoduleStrategy
}