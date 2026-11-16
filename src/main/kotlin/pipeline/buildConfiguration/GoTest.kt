package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.JUnitReport
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.GoTest

/**
 * Test a Go application.
 * @param buildTypeDefinition GoTest configuration
 */
class GoTest(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: GoTest)
    : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Go Test"
    // The whole build configuration runs in the Go toolchain image.
    override var dockerImage = "golang:${buildTypeDefinition.goVersion}"
    private val junitReportFile = "junit-report.xml"
    override val junitReports = listOf(JUnitReport(path = junitReportFile))
    override val buildSteps = listOf<BuildStep>(
        BuildStep(
            name = "test",
            script = """
                    set -o pipefail
                    go install github.com/jstemmer/go-junit-report/v2@latest
                    go test -v ./... 2>&1 | go-junit-report -set-exit-code > $junitReportFile
                """.trimIndent()
        )
    )

}