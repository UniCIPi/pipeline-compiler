package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.*
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.ZephyrBuild

/**
 * Build CMake Zephyr Project in zephyr environment.
 * Outputs installed files for each { BOARD;BUILD_IMAGE } set as artifact.
 */
class ZephyrBuild(buildStep: BuildStepConfiguration, override val buildTypeDefinition: ZephyrBuild) : AbstractBuildConfiguration(buildStep) {
    override val name = "Zephyr build"
    override val matrix = mapOf(
        "BOARD" to buildTypeDefinition.boards,
        "BUILD_IMAGE" to buildTypeDefinition.imageMatrix
    )
    override val publishArtifacts = listOf(
        Artifact(source = "artifacts/")
    )
    private val workDir = "_build"
    override val buildSteps = createBuildSteps()


    private fun createBuildSteps(): List<BuildStep> {
        return listOf(
            BuildStep(
                name = "Build",
                script = """
                    set -e
                    rm -rf $workDir
                    mkdir $workDir && cd $workDir
                    cmake -DBOARD=%pipelineGetParam(BOARD)% -DCMAKE_PREFIX_PATH=/workdir/zephyr/ ..
                    cmake --build .
                """.trimIndent(),
                dockerImage = "%pipelineGetParam(BUILD_IMAGE)%"
            ),
            BuildStep(
                name = "Install",
                script = """
                    set -e
                    mkdir -p ../artifacts/%pipelineGetParam(BOARD)%/
                    cmake -DBRINGAUTO_INSTALL=ON -DCMAKE_INSTALL_PREFIX=../artifacts/%pipelineGetParam(BOARD)% ..
                    cmake --build . --target install
                """.trimIndent(),
                dockerImage = "%pipelineGetParam(BUILD_IMAGE)%",
                workDir = workDir,
            )
        )
    }
}