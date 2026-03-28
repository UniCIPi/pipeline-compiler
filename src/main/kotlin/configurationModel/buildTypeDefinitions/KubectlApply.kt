package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.bringauto.pipelinecompiler.pipeline.BuildStep

/**
 * Apply a list of files to a kubernetes cluster.
 *
 * Kubeconfig file is required and should be defined as secret parameter in platform.
 */
data class KubectlApply(
    /**
     * List of files that will be applied.
     * Can define condition to apply a file
     */
    val files: List<ApplyFile>,
    /**
     * Content of the kubeconfig file.
     * !! IMPORTANT SECURITY NOTE !!: Store this file as a secret parameter in the platform. Reference the parameter!!
     */
    val kubeconfig: String = "%pipelineGetParam(kubeconfig)%"
) : IBuildTypeDefinition

data class ApplyFile(
    /**
     * relative path to the .yaml file in the repository.
     */
    val path: String,
    /**
     * condition to apply the file.
     */
    val condition: BuildStep.RunCondition? = null
)