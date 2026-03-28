package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.KubectlApply
import com.bringauto.pipelinecompiler.pipeline.BuildStep
import com.bringauto.pipelinecompiler.pipeline.IBuildConfiguration
import com.bringauto.pipelinecompiler.pipeline.Parameter

/**
 * Generate a Kubernetes apply command for each file in the configuration.
 * Needs a kubeconfig file for authentication.
 */
class KubectlApply(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: KubectlApply)
    : AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Kubernetes Apply"

    override val buildSteps = createBuildSteps(buildTypeDefinition)

    override val parameters = listOf(
        Parameter(
            name = "KUBECONFIG_FILE",
            value = "./kubeconfig",
        )
    )

    override val type = IBuildConfiguration.Type.DEPLOYMENT

    private fun createBuildSteps(config: KubectlApply): List<BuildStep> {
        return buildList {
            add(
                BuildStep(
                    name = "apply kubeconfig",
                    script = """
                        echo "${config.kubeconfig}" > %pipelineGetParam(KUBECONFIG_FILE)%
                    """.trimIndent()
                )
            )
            addAll(
                config.files.map { file ->
                    BuildStep(
                        name = "Apply ${file.path}",
                        script = """
                            KUBECONFIG=%pipelineGetParam(KUBECONFIG_FILE)% kubectl apply -f ${file.path}
                        """.trimIndent(),
                        dockerImage = "alpine/kubectl:latest",
                        runConditions = file.condition?.let { listOf(it) } ?: emptyList(),
                    )
                }
            )
        }
    }
}