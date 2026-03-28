package com.bringauto.pipelinecompiler.generator.platforms.gitlab

import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration

/**
 * Centralized renderer for mapping formal Build Steps into a GitLab job's script and image.
 *
 * Two modes:
 *  - experimental = true: legacy/previous approach that wraps per-step dockerImage via `docker run` (requires DinD).
 *  - experimental = false: new approach that enforces at most one distinct docker image across steps and sets job-level `image:`.
 */
class BuildStepsRenderer {
    data class RenderedSteps(
        val script: List<String>,
        val jobImage: String? = null,
        val needsDinD: Boolean = false
    )

    fun render(bc: IBuildConfiguration): RenderedSteps {
        val usedImages = bc.buildSteps.mapNotNull { it.dockerImage.takeIf { img -> img.isNotBlank() } }.toSet()
        require(usedImages.size <= 1) { "Only one docker image is allowed in a build configuration for GitLab. This is considered an error, as the generated script cannot guarantee correctness.\nExiting process." }
        val needsDinD = bc.buildSteps.any { it.allowDockerInDocker }
        val jobImage = usedImages.firstOrNull()
        val script = mutableListOf<String>()
        bc.buildSteps.forEach { step ->
            val block = mutableListOf<String>()
            // Visible section markers for readability
            block += "echo \"== ${step.name} ==\""
            block += "if [ -f build.env ]; then set -a; . build.env; set +a; fi"
            if (step.workDir.isNotBlank()) {
                block += "cd \"\$CI_PROJECT_DIR/${step.workDir}\""
            }
            val lines = step.script.lines()
            if (lines.isEmpty()) {
                block += ":"
            } else {
                block.addAll(lines)
            }
            if (step.workDir.isNotBlank()) {
                block += "cd \"\$CI_PROJECT_DIR/\""
            }
            // One YAML script item as a literal block (mapper will use | for multi-line)
            script += block.joinToString("\n")
        }
        return RenderedSteps(script = script, jobImage = jobImage, needsDinD = needsDinD)
    }
}