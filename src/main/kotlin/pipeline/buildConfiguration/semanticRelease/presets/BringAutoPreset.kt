package fanci.compiler.pipeline.buildConfiguration.semanticRelease.presets

import fanci.compiler.common.VCSHelper
import fanci.compiler.common.VCSType
import fanci.compiler.configurationModel.buildTypeDefinitions.SemanticRelease
import fanci.compiler.pipeline.PipelineSettings
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.ReleaseBranch
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.SemanticReleaseConfiguration
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins.IPlugin
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins.Plugin
import fanci.compiler.pipeline.buildConfiguration.semanticRelease.plugins.ReplacePlugin

/**
 * Release management based on Conventional Commits.
 *
 * Releases are created from the default branch and from the maintenance branches `N.x` and `N.N.x`.
 * The commit type selects the released version, the release notes are written to `CHANGELOG.md`,
 * the version file is updated, and the release is published in the VCS host system of the repository.
 */
class BringAutoPreset(
    pipeline: PipelineSettings,
    config: SemanticRelease
) : SemanticReleaseConfiguration(pipeline, config) {
    override val pluginsList: MutableList<IPlugin> = mutableListOf()
    override val releaseBranches: List<ReleaseBranch> = listOf(
        ReleaseBranch(pipeline.defaultBranch),
        ReleaseBranch(ReleaseBranch.ALL_MAINTENANCE_BRANCHES_DEFINITION)
    )

    init {
        addPlugin(
            Plugin(
                name = "@semantic-release/commit-analyzer",
                config = mapOf(
                    "preset" to "conventionalcommits",
                    "releaseRules" to listOf(
                        mapOf("release" to "major", "breaking" to true),
                        mapOf("type" to "feat", "release" to "minor"),
                        mapOf("type" to "fix", "release" to "patch"),
                        mapOf("type" to "docs", "release" to "patch"),
                        mapOf("type" to "perf", "release" to "patch"),
                        mapOf("type" to "revert", "release" to "patch"),
                        mapOf("type" to "chore", "release" to "patch"),
                        mapOf("type" to "chore", "scope" to "release", "release" to false), // ignore message "chore(release): nextVersion" created when pushing changes
                        mapOf("scope" to "no-release", "release" to false)
                    ),
                )
            )
        )
        addPlugin(
            Plugin(
                name = "@semantic-release/release-notes-generator",
                config = mapOf(
                    "preset" to "conventionalcommits",
                    "presetConfig" to mapOf(
                        "types" to listOf(
                            mapOf("type" to "feat", "section" to "New Features \uD83D\uDE80"),
                            mapOf("type" to "fix", "section" to "Bug Fixes \uD83D\uDC1B\uFE0F"),
                            mapOf("type" to "docs", "section" to "Documentation Changes \uD83D\uDCDD"),
                            mapOf("type" to "chore", "section" to "Miscellaneous Chores \uD83D\uDC85"),
                            mapOf("type" to "test", "section" to "Tests \uD83D\uDCE1\uFE0F"),
                            mapOf("type" to "perf", "section" to "Performance Improvements \uD83D\uDE84\uFE0F"),
                            mapOf("type" to "build", "section" to "Build system \uD83D\uDEE0\uFE0F"),
                            mapOf("type" to "ci", "section" to "Build system \uD83D\uDEE0\uFE0F"),
                        )
                    )
                )
            )
        )

        addPlugin(
            Plugin(
                name = "@semantic-release/changelog",
                config = mapOf(
                    "changelogFile" to "CHANGELOG.md"
                )
            )
        )

        addVersioningScheme(config.versionFilePath)
        createGitHostingPlugin(pipeline.vcsRootUrl)

        addPlugin(
            Plugin(
                name = "@semantic-release/git",
                config = mapOf(
                    "assets" to listOfNotNull(config.versionFilePath, "CHANGELOG.md"),
                    "message" to "chore(release): \${nextRelease.version} [skip ci]\\n\${nextRelease.notes}"
                )
            )
        )
    }

    /**
     * Add the plugin publishing the release in the VCS host system of the repository.
     */
    private fun createGitHostingPlugin(repository: String) {
        val hostname = VCSHelper.extractHostname(repository)
        val vcsType = VCSHelper.resolveType(hostname)

        val plugin = when (vcsType) {
            VCSType.GITHUB -> Plugin(
                "@semantic-release/github",
                mapOf("assets" to listOf(mapOf("path" to "CHANGELOG.md")))
            )

            VCSType.GITLAB -> Plugin(
                "@semantic-release/gitlab",
                mapOf(
                    "gitlabUrl" to prefixHttpsToHostname(hostname!!),
                    "assets" to listOf(mapOf("path" to "CHANGELOG.md"))
                )
            )

            VCSType.GITEA -> Plugin(
                "@saithodev/semantic-release-gitea",
                mapOf(
                    "giteaUrl" to prefixHttpsToHostname(hostname!!),
                    "assets" to listOf(mapOf("path" to "CHANGELOG.md"))
                )
            )

            else -> return
        }
        addPlugin(plugin)
    }

    /**
     * Configure replace plugin based on used technology or file path
     */
    private fun addVersioningScheme(versionFilePath: String?) {
        if (versionFilePath == null) {
            println("Semantic release: No version file path provided, skipping version replacement")
            return
        }
        val plugin = when {
            versionFilePath == "version.txt" -> ReplacePlugin.versionTxt()
            versionFilePath.endsWith(".toml") -> ReplacePlugin.toml(versionFilePath)
            versionFilePath.endsWith("os-release") -> ReplacePlugin.osRelease(versionFilePath)
            else -> return
        }
        addPlugin(plugin)
    }

    /**
     * Some plugins require hostname prefixed with https
     */
    private fun prefixHttpsToHostname(hostname: String): String =
        "https://$hostname"
}
