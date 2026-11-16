package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.KubectlApply
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.Parameter

/**
 * Generate a Kubernetes apply command for each file in the configuration.
 * Needs a kubeconfig file for authentication.
 */
class KubectlApply(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: KubectlApply)
    : AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Kubernetes Apply"
    override var dockerImage = "alpine/kubectl:latest"

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
                        echo "${config.kubeconfig}" > %fanciGetParam(KUBECONFIG_FILE)%
                    """.trimIndent()
                )
            )
            addAll(
                config.files.map { file ->
                    BuildStep(
                        name = "Apply ${file.path}",
                        script = """
                            KUBECONFIG=%fanciGetParam(KUBECONFIG_FILE)% kubectl apply -f ${file.path}
                        """.trimIndent(),
                        runConditions = file.condition?.let { listOf(it) } ?: emptyList(),
                    )
                }
            )
        }
    }
}