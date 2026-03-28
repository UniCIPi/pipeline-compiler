package com.bringauto.pipelinecompiler.pipeline

/**
 * Declares a relationship to another build configuration
 *
 * Semantics
 * - The current configuration may start only after all dependencies have completed successfully.
 * - [failureAction] controls how dependency failure/cancellation affects the defining build.
 * - [artifacts] lists artifact paths to fetch from the dependency build.
 */
data class Dependency(
    /**
     * Name of the build configuration of dependency
     * @referenceTo com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration.name
     */
    val buildConfigurationName: String,
    /**
     * Action when the dependency fails or is cancelled
     */
    val failureAction: FailureAction,
    /**
     * Artifacts to be used from the dependency
     */
    val artifacts: List<Artifact>?
) {
    enum class FailureAction {
        IGNORE, // Proceed as if dependency succeeded; do not fail the dependent build.
        ADD_PROBLEM, // Proceed, but mark a problem/warning in the dependent build (platform-specific behavior).
        FAIL, // Fail the dependent build immediately if dependency failed/cancelled.
        CANCEL // Cancel the dependent build if dependency failed/cancelled.
    }
}