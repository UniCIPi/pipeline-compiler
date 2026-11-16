package fanci.compiler.pipeline

/**
 * A single, scriptable unit of work within an [IBuildConfiguration].
 *
 * Notes
 * - Steps execute sequentially in the order declared.
 * - Every step runs in the docker image of its build configuration; a step has no image of its own.
 * - Use [dockerRunParameters] to pass extra `docker run` flags if needed.
 * - [allowDockerInDocker] enables running Docker inside the container (security‑sensitive).
 * - [workDir] sets the working directory for the script.
 * - [runConditions] allow conditional execution.
 *
 * It refines the build script of a build configuration of the formal model: the scripts of the build steps
 * are executed in order.
 */
data class BuildStep(
    /**
     * Identifier of the build step.
     */
    val name: String,
    /**
     * Shell script to be executed in the build step.
     */
    var script: String,
    /**
     * Working directory of the build step, where the script will be executed.
     */
    var workDir: String = "",

    /**
     * Additional arguments to be passed to the Docker command.
     * Only used if the build configuration defines a docker image.
     */
    var dockerRunParameters: String = "",

    /**
     * Allow Docker in Docker for this build step. May be required for some build steps that need to run Docker commands.
     * May be a security risk, use with caution.
     */
    val allowDockerInDocker: Boolean = false,

    /**
     * Conditions for the build step to be executed. If empty, the step always runs.
     * All conditions must be met for the step to run.
     * Some platforms may emulate this via script `if` clauses.
     * @see RunCondition
     */
    val runConditions: List<RunCondition> = emptyList(),

    /**
     * Execution mode of the build step.
     * TODO feature not tested, not described in formal model / maybe as condition?
     */
    val executionMode: ExecutionMode = ExecutionMode.OnSuccess
) {

    /**
     * Execution mode of the build step.
     */
    enum class ExecutionMode {
        /**
         * Run if all previous steps finished successfully.
         */
        OnSuccess,

        /**
         * Run even if some previous steps failed.
         */
        Always,

        /**
         * Run only if some previous step failed.
         */
        OnFailure,
    }

    /**
     * Conditions for the build step to be executed.
     * For each condition, the value of the named parameter is compared to the specified value using the specified operation.
     * @see Operation
     */
    class RunCondition(
        var parameterName: String,
        val operation: Operation,
        var value: String,
    )

    /**
     * Comparison operations for run conditions.
     */
    enum class Operation {
        Equal,
        NotEqual,
        Contains,
        NotContains
    }
}
