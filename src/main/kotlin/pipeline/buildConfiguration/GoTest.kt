package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.GoTest

/**
 * Test a Go application.
 * @param buildTypeDefinition GoTest configuration
 */
class GoTest(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: GoTest)
    : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Go Test"
    private val dockerImage = "golang:${buildTypeDefinition.goVersion}"
    override val buildSteps = listOf<BuildStep>(
        BuildStep(
            name = "test",
            script = """
                    go test -v ./...
                """.trimIndent(),
            dockerImage = dockerImage
        )
    )

}