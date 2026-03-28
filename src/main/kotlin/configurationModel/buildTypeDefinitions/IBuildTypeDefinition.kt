package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Define name for each build type definition. The name is used in configuration file at key 'type'.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CMake::class, name = "CMake"),
    JsonSubTypes.Type(value = GoTest::class, name = "GoTest"),
    JsonSubTypes.Type(value = GoPackage::class, name = "GoPackage"),
    JsonSubTypes.Type(value = PrintVariable::class, name = "PrintVariable"),
    JsonSubTypes.Type(value = Packager::class, name = "Packager"),
    JsonSubTypes.Type(value = StateSmurfTest::class, name = "StateSmurfTest"),
    JsonSubTypes.Type(value = ZephyrBuild::class, name = "ZephyrBuild"),
    JsonSubTypes.Type(value = ZephyrTests::class, name = "ZephyrTests"),
    JsonSubTypes.Type(value = Composite::class, name = "Composite"),
    JsonSubTypes.Type(value = ScriptRunner::class, name = "ScriptRunner"),
    JsonSubTypes.Type(value = DockerBuildAndPush::class, name = "DockerBuildAndPush"),
    JsonSubTypes.Type(value = KubectlApply::class, name = "KubectlApply"),
    // Add other build types as needed
)
/**
 * Interface for all build type definitions.
 * Each build type definition shall have a name and can contain additional parameters, for specific configurations.
 */
sealed interface IBuildTypeDefinition {
}