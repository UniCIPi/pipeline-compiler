package fanci.compiler.pipeline

import fanci.compiler.common.VCSType

/**
 * Pull request (merge request) support of a component.
 *
 * Pull request support is enabled for every component by default and covers **all** pull requests.
 * The optional `component.pull-request` YAML section only narrows the default or turns the support off.
 *
 * Branch filters are matched against the *source* and *target* branch of the pull request, not against
 * the pull request branch itself. Use the `mr/` prefix in trigger branches (see [PullRequestBranch])
 * to control which build configurations actually start for a pull request.
 */
data class PullRequestConfiguration(
    /**
     * Turns the pull request support off for the whole component when set to false.
     * No pull request build feature (TeamCity) and no merge request rules (GitLab) are generated.
     */
    val enabled: Boolean = true,
    /**
     * Token used to read pull requests from the VCS host API.
     *
     * When omitted, the credentials of the component VCS root are reused, which requires the component
     * [VcsAuthMethod] to be a [Token] or a [RefreshableToken]. For any other authentication method
     * (SSH key, anonymous) the token has to be configured here, otherwise a warning is emitted.
     *
     * When set, this token takes precedence over the VCS root credentials.
     * Supports the `%fanciGetParam(...)%` clause, which is resolved by the ParameterReplacer.
     */
    val tokenId: String? = null,
    /** Source branches of the pull request to watch. Empty means all source branches. */
    val includeSourceBranches: List<String> = emptyList(),
    /** Source branches of the pull request to ignore. */
    val excludeSourceBranches: List<String> = emptyList(),
    /** Target branches of the pull request to watch. Empty means all target branches. */
    val includeTargetBranches: List<String> = emptyList(),
    /** Target branches of the pull request to ignore. */
    val excludeTargetBranches: List<String> = emptyList(),
) {
    init {
        require(tokenId == null || tokenId.isNotBlank()) {
            "Pull request token-id cannot be blank. Remove the field to reuse the VCS root credentials."
        }
    }

    /** True when no source or target branch is filtered, i.e. every pull request is covered. */
    fun watchesAllPullRequests(): Boolean =
        includeSourceBranches.isEmpty() && excludeSourceBranches.isEmpty() &&
                includeTargetBranches.isEmpty() && excludeTargetBranches.isEmpty()
}

/**
 * Platform-independent notation of pull request branches.
 *
 * A pull request branch is written as `mr/<selector>` in trigger `include-branches` / `exclude-branches`,
 * analogously to the `tags/` prefix. The generator translates the prefix to the branch naming of the
 * concrete VCS host system, because a merge request of GitLab is exposed under a different reference
 * than a pull request of GitHub.
 *
 * The selector `*` covers all pull requests, the selector `42` covers the pull request with id 42.
 */
object PullRequestBranch {
    /** Prefix marking a pull request branch pattern in the platform-independent model. */
    const val PREFIX = "mr/"

    /** Pattern covering every pull request. */
    const val ALL = "mr/*"

    fun isPullRequestPattern(pattern: String): Boolean = pattern.trim().startsWith(PREFIX)

    /** Part of the pattern behind the [PREFIX], i.e. the pull request selector (`*` or a concrete id). */
    fun selectorOf(pattern: String): String = pattern.trim().removePrefix(PREFIX)

    /**
     * Prefix of the branch under which the VCS host system exposes its pull requests.
     * Returns null when the host system is not recognized and the translation cannot be done.
     */
    fun logicalBranchPrefix(vcsType: VCSType): String? = when (vcsType) {
        // GitLab publishes merge requests as refs/merge-requests/<id>/head
        VCSType.GITLAB -> "merge-requests/"
        // GitHub and Gitea publish pull requests as refs/pull/<id>/head
        VCSType.GITHUB, VCSType.GITEA -> "pull/"
        VCSType.UNKNOWN -> null
    }

    /**
     * Translates a `mr/` pattern to the branch naming of [vcsType].
     * Patterns that are not pull request patterns are returned unchanged.
     *
     * @return null when the pattern is a pull request pattern that cannot be translated for [vcsType]
     */
    fun translate(pattern: String, vcsType: VCSType): String? {
        if (!isPullRequestPattern(pattern)) {
            return pattern
        }
        val prefix = logicalBranchPrefix(vcsType) ?: return null
        return prefix + selectorOf(pattern)
    }
}
