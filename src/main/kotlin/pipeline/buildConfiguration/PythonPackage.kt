package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.pipeline.*
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.PythonPackage

/**
 *
 * @param buildTypeDefinition PythonPackage configuration
 */
class PythonPackage(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: PythonPackage) :
    AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Python Package"
    override var dockerImage = "%fanciGetParam(IMAGE)%"
    override val buildMatrix = mapOf(
        "IMAGE" to buildTypeDefinition.imageMatrix
    )

    override val publishArtifacts =
        listOf(Artifact(source = "artifacts/**"))
    private val junitReportFile = "junit-report.xml"
    override val junitReports = if (buildTypeDefinition.enableTests) listOf(JUnitReport(path = junitReportFile)) else emptyList()
    override val buildSteps = CreateBuildSteps()

    fun CreateBuildSteps(): List<BuildStep> {
        val buildSteps = mutableListOf<BuildStep>(
            BuildStep(
                name = "Lint",
                script = """
                    ruff check .
                """.trimIndent()
            ),
            BuildStep(
                name = "Install",
                script = """
                    poetry install
                """.trimIndent()
            ),
        )
        if (buildTypeDefinition.enableMypy) {
            buildSteps.add(
                BuildStep(
                    name = "Type check",
                    script = """
                        poetry run mypy .
                    """.trimIndent()
                )
            )
        }
        if (buildTypeDefinition.enableTests) {
            buildSteps.add(
                BuildStep(
                    name = "Test",
                    script = """
                        poetry run pytest --verbose --junitxml=$junitReportFile
                    """.trimIndent()
                )
            )
        }
        if (buildTypeDefinition.enableShivPackage) {
            buildSteps.add(
                BuildStep(
                    name = "Shiv Package",
                    script = """
                        PYTHON_VER=$(python3 --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
                        mkdir -p "artifacts/py${'$'}PYTHON_VER/"
                        poetry export -f requirements.txt --output requirements.txt --without-hashes
                        poetry run shiv -o "artifacts/py${'$'}PYTHON_VER/${buildTypeDefinition.packageName}" -e ${buildTypeDefinition.packageModule} -r requirements.txt .
                    """.trimIndent()
                )
            )
        }
        return buildSteps
    }
}