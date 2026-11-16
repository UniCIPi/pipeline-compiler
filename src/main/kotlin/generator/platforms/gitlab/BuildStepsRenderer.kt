package fanci.compiler.generator.platforms.gitlab

import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.IBuildConfiguration

/**
 * Centralized renderer for mapping formal Build Steps into a GitLab job's script and image.
 *
 * Two modes:
 * The docker image of the build configuration becomes the job-level `image:`; individual steps
 * have no image of their own.
 */
class BuildStepsRenderer {
    data class RenderedSteps(
        val script: List<String>,
        val jobImage: String? = null,
        val needsDinD: Boolean = false
    )

    fun render(bc: IBuildConfiguration): RenderedSteps {
        val needsDinD = bc.buildSteps.any { it.allowDockerInDocker }
        val jobImage = bc.dockerImage.takeIf { it.isNotBlank() }
        val script = mutableListOf<String>()

        val hasNonDefaultExecutionMode = bc.buildSteps.any { it.executionMode != BuildStep.ExecutionMode.OnSuccess }

        if (!hasNonDefaultExecutionMode) {
            bc.buildSteps.forEach { step -> script += renderSimpleStep(step) }
        } else {
            script += "BUILD_STATUS=\"success\""
            bc.buildSteps.forEach { step -> script += renderTrackedStep(step) }
            script += "if [ \"\$BUILD_STATUS\" = \"failed\" ]; then exit 1; fi"
        }

        return RenderedSteps(script = script, jobImage = jobImage, needsDinD = needsDinD)
    }

    /**
     * Renders a step with no execution-mode tracking. Script lines are not indented,
     * so heredocs and other start-of-line constructs work correctly.
     */
    private fun renderSimpleStep(step: BuildStep): String {
        val block = mutableListOf<String>()
        block += "echo \"== ${step.name} ==\""
        block += "if [ -f build.env ]; then set -a; . build.env; set +a; fi"
        if (step.workDir.isNotBlank()) {
            block += "cd \"\$CI_PROJECT_DIR/${step.workDir}\""
        }
        val lines = step.script.lines()
        if (lines.isEmpty()) block += ":" else block.addAll(lines)
        if (step.workDir.isNotBlank()) {
            block += "cd \"\$CI_PROJECT_DIR/\""
        }
        return block.joinToString("\n")
    }

    /**
     * Indents each line by 4 spaces, but suspends indentation inside heredoc blocks so that
     * the heredoc body and its terminator stay at their original column and are parsed correctly
     * by the shell.
     */
    private fun indentLinesPreservingHeredocs(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        val heredocDelimiters = ArrayDeque<String>()
        val heredocStart = Regex("""<<-?\s*['"]?([A-Za-z_][A-Za-z0-9_]*)['"]?""")

        for (line in lines) {
            if (heredocDelimiters.isNotEmpty()) {
                result += line
                if (line.trim() == heredocDelimiters.last()) heredocDelimiters.removeLast()
            } else {
                result += "    $line"
                heredocStart.findAll(line).forEach { heredocDelimiters.addLast(it.groupValues[1]) }
            }
        }
        return result
    }

    /**
     * Renders a step inside a subshell with BUILD_STATUS tracking,
     * used when the build configuration contains Always or OnFailure steps.
     */
    private fun renderTrackedStep(step: BuildStep): String {
        val block = mutableListOf<String>()
        val condition = when (step.executionMode) {
            BuildStep.ExecutionMode.OnSuccess -> "[ \"\$BUILD_STATUS\" = \"success\" ]"
            BuildStep.ExecutionMode.Always -> "true"
            BuildStep.ExecutionMode.OnFailure -> "[ \"\$BUILD_STATUS\" = \"failed\" ]"
        }
        block += "if $condition; then"
        block += "  ("
        block += "    echo \"== ${step.name} ==\""
        block += "    if [ -f build.env ]; then set -a; . build.env; set +a; fi"
        if (step.workDir.isNotBlank()) {
            block += "    cd \"\$CI_PROJECT_DIR/${step.workDir}\""
        }
        val lines = step.script.lines()
        if (lines.isEmpty()) block += "    :" else block.addAll(indentLinesPreservingHeredocs(lines))
        block += "  ) || BUILD_STATUS=\"failed\""
        block += "fi"
        val isRecoveryMode = step.executionMode == BuildStep.ExecutionMode.Always ||
            step.executionMode == BuildStep.ExecutionMode.OnFailure
        if (!isRecoveryMode) {
            block += "if [ \"\$BUILD_STATUS\" = \"failed\" ]; then exit 1; fi"
        }
        return block.joinToString("\n")
    }
}