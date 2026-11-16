package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.PyPiPublish
import fanci.compiler.pipeline.Artifact
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.SecretParameter

/**
 * Build the source and wheel distributions of a Python package with Poetry and upload them to a package index.
 *
 * The distributions are also published as output artifacts of the build configuration.
 */
class PyPiPublish(
    buildConfigurationDefinition: BuildConfigurationDefinition,
    override val buildTypeDefinition: PyPiPublish
) : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = buildTypeDefinition.configurationName

    override val type = IBuildConfiguration.Type.DEPLOYMENT

    override val parameters = listOf(
        SecretParameter(
            "PYPI_TOKEN",
            buildTypeDefinition.tokenId,
            DisplayType.NORMAL,
            "",
            "API token of the package index"
        )
    )

    override val publishArtifacts = listOf(Artifact(source = "dist/*"))

    override val buildSteps = listOf(
        BuildStep(
            name = "Build",
            script = """
                poetry build
            """.trimIndent()
        ),
        BuildStep(
            name = "Publish",
            script = createPublishScript(buildTypeDefinition.repositoryUrl)
        )
    )

    /**
     * Poetry reads the token of a package index from the environment variable `POETRY_PYPI_TOKEN_<INDEX NAME>`.
     */
    private fun createPublishScript(repositoryUrl: String?): String =
        if (repositoryUrl == null) {
            """
                export POETRY_PYPI_TOKEN_PYPI=%fanciGetParam(PYPI_TOKEN)%
                poetry publish
            """.trimIndent()
        } else {
            """
                poetry config repositories.target "$repositoryUrl"
                export POETRY_PYPI_TOKEN_TARGET=%fanciGetParam(PYPI_TOKEN)%
                poetry publish --repository target
            """.trimIndent()
        }
}
