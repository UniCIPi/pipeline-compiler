package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

/**
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.CMake
 */
data class CMake(
    /**
     * List of docker images in which the build is run.
     */
    val imageMatrix: List<String>,
    /**
     *  Cmake build type, e.g. Release or Debug.
     */
    val buildType: CmakeBuildType,
    /**
     * Run ctest after the build.
     * Building tests must be enabled (e.g. by parameters).
     */
    val enableTests: Boolean,
    /**
     * Run cpack after the build.
     * Building packages must be enabled (e.g. by parameters).
     */
    val createPackage: Boolean,
    /**
     * Additional parameters for the cmake command.
     * The prefix "-D" is added automatically.
     */
    val cmakeParameters: Map<String, String>,
    /**
     * Directory containing the CMakeLists.txt file.
     * Relative to the working directory.
     */
    val cmakeListsDir: String = "../",
) : IBuildTypeDefinition

enum class CmakeBuildType {
    Release,
    Debug
}