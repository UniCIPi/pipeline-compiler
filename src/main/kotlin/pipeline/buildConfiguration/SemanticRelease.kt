package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.common.VCSType
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.SemanticRelease
import fanci.compiler.pipeline.BuildStep
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.IBuildConfiguration
import fanci.compiler.pipeline.Parameter
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.SecretParameter
import fanci.compiler.pipeline.SelectParameter
import fanci.compiler.pipeline.TextParameter
import fanci.compiler.pipeline.Trigger
import fanci.compiler.pipeline.UploadedKey
import fanci.compiler.pipeline.VcsTrigger
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.ReleaseBranch
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.SemanticReleaseConfiguration
import java.util.Base64

/**
 * Run semantic-release to create a release of the component.
 *
 * The build configuration is a dry run by default: the parameter `DRY_RUN` is `true`, so a build started
 * by hand or as a dependency only reports the release it would create. The default trigger of the template
 * watches the release branches of the preset and sets `DRY_RUN` to `false`. Triggers defined in the input
 * configuration replace the default trigger.
 *
 * The semantic-release configuration is generated from the preset and passed to the build in the parameter
 * `RELEASE_RC_CONTENT`.
 *
 * This build configuration requires matching UID between the run docker image and build agent on TeamCity.
 *
 * This build configuration requires write access to the VCS repository.
 *
 * @param component Component owning the build configuration. Its repository and default branch define
 * the release branches and the plugin of the VCS host system.
 */
class SemanticRelease(
    buildConfigurationDefinition: BuildConfigurationDefinition,
    override val buildTypeDefinition: SemanticRelease,
    component: PipelineSettings
) : AbstractBuildConfiguration(buildConfigurationDefinition) {
    init {
        require(buildConfigurationDefinition.dockerImage.isNotBlank()) {
            "SemanticRelease build configuration '${buildTypeDefinition.configurationName}' requires 'docker-image' " +
                    "with semantic-release and the plugins of the preset '${buildTypeDefinition.preset}' installed."
        }
    }

    private val releaseConfiguration = SemanticReleaseConfiguration.createFromSettings(component, buildTypeDefinition)

    override val name = buildTypeDefinition.configurationName

    override val type = IBuildConfiguration.Type.DEPLOYMENT

    override val triggers: List<Trigger> =
        buildConfigurationDefinition.triggers.ifEmpty { listOf(createReleaseTrigger()) }

    override val parameters = listOf(
        SecretParameter(
            "TOKEN",
            buildTypeDefinition.repoAccessTokenId,
            DisplayType.NORMAL,
            "",
            "Token with write_api access to the repository"
        ),
        SelectParameter(
            "DRY_RUN", "true", DisplayType.PROMPT, "Dry run",
            "When true, the build doesn't publish a release", listOf("true", "false")
        ),
        TextParameter(
            "RELEASE_RC_CONTENT",
            Base64.getEncoder().encodeToString(releaseConfiguration.serializeToJson().toByteArray()),
            displayType = DisplayType.HIDDEN,
            label = "Content of the .releaserc.json file (base64)",
            description = "Base64-encoded .releaserc.json; decoded at runtime by the build step"
        )
    )

    override val sshAgentKeyName = (component.vcsAuthMethod as? UploadedKey)?.name ?: ""

    /**
     * Name of the environment variable that will be used to store the token for authentication.
     * Each VCS host system needs a different environment variable name for authentication.
     */
    val tokenName: String = when (component.vcsType) {
        VCSType.GITLAB -> "GL_TOKEN"
        VCSType.GITHUB -> "GH_TOKEN"
        VCSType.GITEA -> "GITEA_TOKEN"
        VCSType.UNKNOWN -> "TOKEN"
    }

    override val buildSteps = createBuildSteps()

    /**
     * Default trigger of the template: a commit to a release branch creates a release.
     * Changes of the files written by the release itself are ignored, so a release does not start another one.
     */
    private fun createReleaseTrigger(): Trigger = VcsTrigger(
        includeBranches = releaseConfiguration.releaseBranches.map { branch ->
            if (branch.name == ReleaseBranch.ALL_MAINTENANCE_BRANCHES_DEFINITION) {
                // The micromatch pattern of semantic-release has to be written as a branch pattern of the input language.
                "*.x"
            } else {
                branch.name
            }
        },
        parameters = listOf(Parameter(name = "DRY_RUN", value = "false")),
        excludePaths = listOfNotNull(buildTypeDefinition.versionFilePath, "CHANGELOG.md")
    )

    private fun createBuildSteps(): List<BuildStep> {
        return listOf(
            BuildStep(
                name = "Create .releaserc.json",
                script = """
                    #!/bin/bash
                    set -e
                    printf '%s' "%fanciGetParam(RELEASE_RC_CONTENT)%" | base64 -d > .releaserc.json
                """.trimIndent()
            ),
            BuildStep(
                name = "semantic-release",
                script = """
                    #!/bin/bash

                    export ${tokenName}=%fanciGetParam(TOKEN)%
                    DRY_RUN_FLAG=""
                    if [ %fanciGetParam(DRY_RUN)% = true ]; then
                      DRY_RUN_FLAG="--dry-run"
                    fi

                    semantic-release ${'$'}DRY_RUN_FLAG
                """.trimIndent()
            )
        )
    }

}
