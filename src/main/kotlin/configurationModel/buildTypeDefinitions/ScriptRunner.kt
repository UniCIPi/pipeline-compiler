package fanci.compiler.configurationModel.buildTypeDefinitions

import fanci.compiler.pipeline.Artifact
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.JUnitReport
import fanci.compiler.pipeline.Parameter

/**
 * Script runner is a special build type that inlines scripts into the build steps of a build configuration.
 * It is useful for defining single-use-case build steps without creating a new build type.
 */
data class ScriptRunner(
    /**
     * Name of the build configuration
     */
    val configurationName: String,
    /**
     * List of build steps to execute in order.
     */
    val steps: List<StepSpecification>,
    /**
     * If set, the ssh-agent feature will be enabled and the specified key will be added to the agent.
     * The key (with the same name) has to be defined in the target platform.
     */
    val sshAgentKeyName: String = "",
    /**
     * List of { source, destination? }. Defines artifacts published by the build step.
     */
    val publishArtifacts: List<Artifact> = listOf(),
    /**
     * List of JUnit XML test reports produced by the steps, to be published as platform test reports.
     * The step script itself is responsible for producing the report file(s) at the given path(s).
     */
    val junitReports: List<JUnitReport> = listOf(),
    /**
     * List of parameters that can be used in the build configuration.
     * Can be any of the types implementing Parameter.
     * @see Parameter for details.
     */
    val parameters: List<Parameter> = listOf(),

    /**
     * Build matrix for this build configuration. A map of matrix parameters
     */
    val matrix: Map<String, List<String>> = mapOf()
) : IBuildTypeDefinition

/**
 * Specification of a build step and its script.
 *
 * The execution environment is defined by the `docker-image` of the build configuration;
 * a build step has no image of its own.
 *
 * The script of the step is defined either by [file] or by [scriptContent]. The two options are
 * disjunctive - exactly one of them has to be defined.
 */
data class StepSpecification(
    /**
     * Name of the build step.
     */
    val stepName: String,
    /**
     * Script file name. The name is searched in `scripts` directory adjacent to the build configuration file.
     * Mutually exclusive with [scriptContent].
     */
    val file: String = "",
    /**
     * Script content defined directly in the step configuration.
     * Mutually exclusive with [file].
     */
    val scriptContent: String = "",
    /**
     * Additional arguments to be passed to the `docker run` command.
     * Only used if the build configuration defines a docker image.
     */
    val dockerArguments: String = "",
    /**
     * Allow Docker in Docker for this build step. May be required for some build steps that need to run Docker commands.
     * May be a security risk, use with caution.
     */
    val allowDockerInDocker: Boolean = false,

    /**
     * List of conditions for the build step to be executed.
     * @see BuildStep.RunCondition
     */
    val runConditions: List<BuildStep.RunCondition> = emptyList(),

    /**
     * Execution mode of the build step.
     */
    val executionMode: BuildStep.ExecutionMode = BuildStep.ExecutionMode.OnSuccess,
) {
    init {
        require(file.isNotEmpty() || scriptContent.isNotEmpty()) {
            "Build step '$stepName': either 'file' or 'script-content' has to be defined."
        }
        require(file.isEmpty() || scriptContent.isEmpty()) {
            "Build step '$stepName': 'file' and 'script-content' are mutually exclusive, define only one of them."
        }
    }

    /**
     * True if the script of this step is stored in a file that has to be resolved and inlined.
     */
    val isScriptInFile: Boolean
        get() = file.isNotEmpty()
}
