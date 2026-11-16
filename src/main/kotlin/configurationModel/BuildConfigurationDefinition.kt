package fanci.compiler.configurationModel

import fanci.compiler.pipeline.Dependency
import fanci.compiler.pipeline.ScheduleTrigger
import fanci.compiler.configurationModel.buildTypeDefinitions.IBuildTypeDefinition
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

/**
 * One entry of the component's `build-configurations` list, as written by the user.
 *
 * It carries the fields common to every build configuration and delegates the type-specific
 * fields to [buildTypeDefinition]. It is the input-layer counterpart of
 * [fanci.compiler.pipeline.IBuildConfiguration].
 */
@JsonDeserialize(using = BuildConfigurationDefinitionDeserializer::class)
data class BuildConfigurationDefinition(
    /**
     * Specific BuildType configuration.
     */
    val buildTypeDefinition: IBuildTypeDefinition,
    /**
     * List of dependencies required for this build configuration. Creates build chains. Set by user in configuration.
     * Must be supported by every build configuration.
     */
    val dependencies: List<Dependency>,
    /**
     * Triggers for this build configuration. Can be defined for any build type.
     */
    val triggers: List<fanci.compiler.pipeline.Trigger>,
    /**
     * Required disk space for the build configuration in GB.
     */
    val freeDiskSpaceGb: Int,
    /**
     * Docker image defining the execution environment of the whole build configuration.
     * Every build step that does not define its own image is executed in it.
     */
    val dockerImage: String = "",
    /**
     * List of Docker connection IDs that this build configuration requires.
     */
    val dockerConnections: List<String>,

    /**
     * Build number format.
     *
     * Not supported by GitLab CI/CD
     */
    val buildNumberPattern: String? = null
) {
    init {
        val duplicateScheduleNames = triggers
            .filterIsInstance<ScheduleTrigger>()
            .mapNotNull { it.name }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateScheduleNames.isEmpty()) {
            "Schedule trigger names must be unique within a build configuration: ${duplicateScheduleNames.joinToString(", ")}"
        }
    }
}