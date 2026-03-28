package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.*
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.CMake

/**
 * Build, test and package a CMake project.
 */
class CMake(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: CMake)
    : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "CMake"
    override val matrix = mapOf("BUILD_IMAGE" to buildTypeDefinition.imageMatrix)

    private val workingDirectory = "_build"
    /**
     * All .zip files in the working directory will be collected as artifacts. This can be extended for other file types if needed.
     */
    override val publishArtifacts = if (buildTypeDefinition.createPackage) listOf(Artifact(source = "${workingDirectory}/*.zip")) else emptyList()
    override val buildSteps = createBuildSteps(buildTypeDefinition)

    private fun createBuildSteps(config: CMake): List<BuildStep> {
        val parameters = config.cmakeParameters.entries.joinToString(" ") { "-D${it.key}=${it.value}" }

        val buildSteps = mutableListOf(
            BuildStep(
            name = "build",
            script = """
                rm -rf $workingDirectory
                mkdir $workingDirectory && cd $workingDirectory
                cmake $parameters -DCMAKE_BUILD_TYPE=${config.buildType.name} ${config.cmakeListsDir}
                cmake --build .
            """.trimIndent(),
            dockerImage = "%pipelineGetParam(BUILD_IMAGE)%"

        ))

        if (config.enableTests) {
            buildSteps.add(BuildStep(
                name = "test",
                script = """
                    ctest --extra-verbose
                """.trimIndent(),
                dockerImage = "%pipelineGetParam(BUILD_IMAGE)%",
                workDir = workingDirectory
            ))
        }
        if (config.createPackage) {
            buildSteps.add(
                BuildStep(
                    name = "package",
                    script = """
                    cpack
                """.trimIndent(),
                    dockerImage = "%pipelineGetParam(BUILD_IMAGE)%",
                    workDir = workingDirectory
                )
            )
        }
        return buildSteps
    }
}