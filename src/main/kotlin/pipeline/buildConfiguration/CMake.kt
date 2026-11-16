package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.pipeline.*
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.CMake

/**
 * Build, test and package a CMake project.
 */
class CMake(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: CMake)
    : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = buildTypeDefinition.name
    override var dockerImage = "%fanciGetParam(BUILD_IMAGE)%"
    override val buildMatrix = mapOf("BUILD_IMAGE" to buildTypeDefinition.imageMatrix)

    private val workingDirectory = "_build"
    private val packageDirectory = "artifacts"
    private val junitReportFile = "junit-report.xml"
    /**
     * All files cpack places in the package directory will be collected as artifacts.
     */
    override val publishArtifacts = if (buildTypeDefinition.createPackage) listOf(Artifact(source = "$workingDirectory/$packageDirectory/**")) else emptyList()
    override val junitReports = if (buildTypeDefinition.enableTests) listOf(JUnitReport(path = "$workingDirectory/$junitReportFile")) else emptyList()
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
                cmake --build . --parallel $(nproc)
            """.trimIndent()

        ))

        if (config.enableTests) {
            buildSteps.add(BuildStep(
                name = "test",
                script = """
                    ctest --extra-verbose --output-junit $junitReportFile
                """.trimIndent(),
                workDir = workingDirectory
            ))
        }
        if (config.createPackage) {
            buildSteps.add(
                BuildStep(
                    name = "package",
                    script = """
                    cpack -B $packageDirectory
                    rm -rf $packageDirectory/_CPack_Packages
                """.trimIndent(),
                    workDir = workingDirectory
                )
            )
        }
        return buildSteps
    }
}