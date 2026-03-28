package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.DockerBuildAndPush
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.DisplayType
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.TextParameter

/**
 * Build, test and package a CMake project.
 */
class DockerBuildAndPush(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: DockerBuildAndPush)
    : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Docker Build and Push"

    override val buildSteps = createBuildSteps(buildTypeDefinition)

    override val type = IBuildConfiguration.Type.DEPLOYMENT

    override val parameters = listOf(
        TextParameter(
            name = "IMAGE_TAG",
            displayType = DisplayType.NORMAL,
            value = buildTypeDefinition.imageTag,
            description = "Image tag"
        )
    )

    private fun createBuildSteps(config: DockerBuildAndPush): List<BuildStep> {
        val buildSteps = mutableListOf(
            BuildStep(
                name = "build",
                script = """
                    docker build -f "${config.dockerFilePath}" -t "${config.imageName}:%pipelineGetParam(IMAGE_TAG)%" .
                """.trimIndent()
            ),
            BuildStep(
                name = "push",
                script = """
                    docker push "${config.imageName}:%pipelineGetParam(IMAGE_TAG)%"
                """.trimIndent()
            )
        )

        return buildSteps
    }
}