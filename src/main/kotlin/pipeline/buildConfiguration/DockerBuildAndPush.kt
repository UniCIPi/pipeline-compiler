package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.DockerBuildAndPush
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.TextParameter
import kotlin.io.path.Path

/**
 * Build, test and package a CMake project.
 */
class DockerBuildAndPush(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: DockerBuildAndPush)
    : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Docker Build and Push"

    override val buildSteps = createBuildSteps(buildTypeDefinition)

    override val type = IBuildConfiguration.Type.DEPLOYMENT

    override val parameters = listOf(
        TextParameter(
            name = "IMAGE_TAG",
            displayType = DisplayType.NORMAL,
            value = buildTypeDefinition.imageTag,
            description = "Image tag"
        ),
        TextParameter(
            name = "IMAGE_NAME",
            displayType = DisplayType.NORMAL,
            value = buildTypeDefinition.imageName,
            description = "Image name"
        )
    )

    private fun createBuildSteps(config: DockerBuildAndPush): List<BuildStep> {
        val buildSteps = mutableListOf(
            /** If the Image tag contains slashes, the build fails. */
            BuildStep(
                name = "Remove prefix with slashes from image tag",
                script = """
                    TAG="%fanciGetParam(IMAGE_TAG)%"
                    echo "Removed slash from IMAGE_TAG"
                    echo "New tag = ${"$"}{TAG##*/}"
                    %fanciSetParam(IMAGE_TAG, ${"$"}{TAG##*/})%
                """.trimIndent(),
                runConditions = listOf(
                    BuildStep.RunCondition(
                        parameterName = "IMAGE_TAG",
                        operation = BuildStep.Operation.Contains,
                        value = "/",
                    )
                )
            )
        )

        if (config.dockerBuildScript == null) {
            buildSteps.add(
                BuildStep(
                    name = "build",
                    script = """
                        docker build -f "${
                        Path(
                            config.dockerContextDir,
                            config.dockerFilePath
                        ).normalize()
                    }" -t "%fanciGetParam(IMAGE_NAME)%:%fanciGetParam(IMAGE_TAG)%" ${config.dockerContextDir}
                    """.trimIndent()
                ),
            )
        } else {
            buildSteps.add(
                BuildStep(
                    name = "build",
                    script = """
                        bash ${config.dockerBuildScript} "%fanciGetParam(IMAGE_NAME)%" "%fanciGetParam(IMAGE_TAG)%"
                    """.trimIndent()
                )
            )
        }

        buildSteps.add(
            BuildStep(
                name = "push",
                script = """
                    docker push "%fanciGetParam(IMAGE_NAME)%:%fanciGetParam(IMAGE_TAG)%"
                """.trimIndent()
            ))

        return buildSteps
    }
}