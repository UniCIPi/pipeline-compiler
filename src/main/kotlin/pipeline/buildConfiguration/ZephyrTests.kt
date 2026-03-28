package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.Dependency
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ZephyrTests

/**
 * Test Zephyr application using CMake and CTest.
 */
class ZephyrTests(buildStep: BuildStepConfiguration, override val buildTypeDefinition: ZephyrTests) : AbstractBuildConfiguration(buildStep) {
    override val name = "Zephyr tests"
    override val matrix = mapOf("BUILD_IMAGE" to buildTypeDefinition.imageMatrix)
    override val dependencies: MutableList<Dependency> = mutableListOf()
    override val freeDiskSpaceGb = buildStep.freeSpaceGb.takeIf { it > 0 } ?: 50
    private val workDir = "_build"

    override val buildSteps: List<BuildStep> = listOf(
        BuildStep(
            name = "Build tests",
            script = """
                set -e
                rm -rf _build
                mkdir _build && cd _build
                cmake -DBRINGAUTO_TESTS=ON ..
                cmake --build .
            """.trimIndent(),
            dockerImage = "%pipelineGetParam(BUILD_IMAGE)%"
        ),
        BuildStep(
            name = "Run tests",
            script = """
                set -e
                ctest --extra-verbose
            """.trimIndent(),
            dockerImage = "%pipelineGetParam(BUILD_IMAGE)%",
            workDir = workDir
        )
    )

}