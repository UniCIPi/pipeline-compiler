package fanci.compiler.pipeline

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser

/**
 * Base class for all triggers used in pipelines.
 *
 * Use the `type` field in YAML to select a concrete trigger implementation.
 * There is no default trigger; omitting `type` will cause deserialization to fail.
 *
 * By default, all branches are included in the trigger.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = VcsTrigger::class, name = "vcs"),
    JsonSubTypes.Type(value = ScheduleTrigger::class, name = "schedule"),
    JsonSubTypes.Type(value = FinishBuildTrigger::class, name = "finish-build"),
)
open class Trigger(
    /** Optional list of parameters specific to this trigger. */
    open val parameters: List<Parameter> = emptyList(),
    /**
     * Branches to include for this trigger (easier to understand than TeamCity +: syntax).
     * The semantic of included branches is determined by the concrete trigger type.
     */
    open val includeBranches: List<String> = emptyList(),
    /** Branches to exclude for this trigger (easier to understand than TeamCity -: syntax). */
    open val excludeBranches: List<String> = emptyList(),
)

/**
 * Trigger that reacts to changes in the repository of one component. Allows filtering by branch and changed files.
 *
 * Included branches: Only changes in the included branches will trigger the build.
 */
data class VcsTrigger(
    /**
     * Fully qualified name of the component whose repository is watched.
     * When not set, the repository of the component owning the build configuration is watched.
     */
    val watchedComponent: String? = null,
    /**
     * Paths to track.
     * Supports Ant-style wildcards.
     * Only changes in tracked paths will trigger the build.
     *
     * Unless an include path is specified, all changes will be tracked.
     */
    val includePaths: List<String> = emptyList(),
    /** Paths to ignore. Supports Ant-style wildcards. */
    val excludePaths: List<String> = emptyList(),
    /**
     * When set, this regex is added as a conjunctive condition to every include and exclude path.
     * Uses Java regex; the commit message is matched with contains semantics (not full match).
     */
    val commitMessageRegex: String? = null,
    override val includeBranches: List<String> = emptyList(),
    override val excludeBranches: List<String> = emptyList(),
    override val parameters: List<Parameter> = emptyList(),
) : Trigger(parameters) {
    init {
        require(watchedComponent == null || watchedComponent.isNotBlank()) {
            "Watched component cannot be blank. Remove the field to watch the component owning the build configuration."
        }
    }
}
/**
 * Trigger based on UNIX cron expression.
 *
 * Platforms must transform UNIX format to their supported format.
 *
 * GitLab doesn't support schedule trigger fully. The schedule definition must be added in GitLab UI. https://docs.gitlab.com/ci/pipelines/schedules/
 *
 * Included branches: all branches that will be run when the trigger fires
 */
data class ScheduleTrigger(
    val cron: String,
    /**
     * Optional name of the trigger, unique within the build configuration.
     *
     * On GitLab, the name identifies the pipeline schedule starting the build configuration, which protects
     * the schedule from being disconnected when the triggers are reordered. Unnamed schedule triggers are
     * identified by their order instead. On other platforms the name is used for identification only.
     */
    val name: String? = null,
    val withPendingChangesOnly: Boolean = false,
    override val includeBranches: List<String> = emptyList(),
    override val excludeBranches: List<String> = emptyList(),
    override val parameters: List<Parameter> = emptyList(),
) : Trigger(parameters) {
    init {
        require(cron.isNotBlank()) {
            "Cron expression cannot be blank"
        }
        require(name == null || name.isNotBlank()) {
            "Schedule trigger name cannot be blank. Remove the field to identify the trigger by its order."
        }

        try {
            val cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
            val parser = CronParser(cronDefinition)
            parser.parse(cron)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cron expression: '$cron'. ${e.message}\nUse UNIX-style expression", e)
        }
    }
}

/**
 * Trigger that starts after build X finishes.
 * The build X is referenced by its fully qualified name in buildConfigurationName.
 *
 * Included branches: The trigger only fires when build X finishes in a branch that is included in the trigger's included branches.
 */
data class FinishBuildTrigger(
    /** Fully qualified name of the build to wait for **/
    val buildConfigurationName: String,
    val successfulOnly: Boolean = false,
    override val includeBranches: List<String> = emptyList(),
    override val excludeBranches: List<String> = emptyList(),
    override val parameters: List<Parameter> = emptyList(),
) : Trigger(parameters)
