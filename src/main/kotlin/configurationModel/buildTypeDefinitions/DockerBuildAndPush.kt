package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

/**
 * Build and push Docker image.
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.DockerBuildAndPush
 */
data class DockerBuildAndPush(
    /**
     * Relative path to Dockerfile from the repository root.
     */
    var dockerFilePath: String = "./Dockerfile",
    /**
     * Name of the Docker image to be built. Including a full repository path, excluding tag
     */
    var imageName: String,
    /**
     * Value of IMAGE_TAG parameter
     * This value can be overridden in triggers with: parameter.name: IMAGE_TAG && parameter.value: <value>
     */
    var imageTag: String,
    /**
     * Docker image tag, which is used for the build step.
     */
    var dockerVersion: String = "latest"
) : IBuildTypeDefinition
