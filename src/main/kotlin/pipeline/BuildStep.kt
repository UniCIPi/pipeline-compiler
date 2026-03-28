package com.bringauto.pipelinecompiler.pipeline

/**
 * A single, scriptable unit of work within an [IBuildConfiguration].
 *
 * Notes
 * - Steps execute sequentially in the order declared.
 * - A step may run inside a container image via [dockerImage].
 * - Use [dockerRunParameters] to pass extra `docker run` flags if needed.
 * - [allowDockerInDocker] enables running Docker inside the container (security‑sensitive).
 * - [workDir] sets the working directory for the script.
 * - [runConditions] allow conditional execution.
 *
 * See doc/FormalModel.md (section “Build Step”) for the abstract definition.
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
     * Docker image in which the build step will be executed.
     */
    var dockerImage: String = "",

    /**
     * Working directory of the build step, where the script will be executed.
     */
    var workDir: String = "",

    /**
     * Additional arguments to be passed to the Docker command. Only used if [dockerImage] is set.
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
    val runConditions: List<RunCondition> = emptyList()
) {

    /**
     * Conditions for the build step to be executed.
     * For each condition, the value of the named parameter is compared to the specified value using the specified operation.
     * @see Operation
     */
    class RunCondition(
        var parameterName: String,
        val operation: Operation,
        val value: String,
    )

    /**
     * Comparison operations for run conditions.
     */
    enum class Operation {
        Equal,
        NotEqual,
        Contain,
        NotContain
    }
}
