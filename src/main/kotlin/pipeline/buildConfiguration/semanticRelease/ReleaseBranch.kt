package fanci.compiler.pipeline.buildConfiguration.semanticRelease

data class ReleaseBranch(
    val name: String,
    val channel: String? = null,
    val range: String? = null,
    val prerelease: String? = null
) {
    init {
        require(!(range != null && prerelease != null)) { "Range and prerelease cannot be both specified" }
    }

    /**
     * This regex matches all maintenance branch names N.N.x and N.x
     * It automatically marks all these branches as maintenance and applies conditions to them.
     * @see [Maintenance branches](https://semantic-release.gitbook.io/semantic-release/usage/workflow-configuration#maintenance-branches)
     */
    companion object {
        const val ALL_MAINTENANCE_BRANCHES_DEFINITION: String = "+([0-9])?(.{+([0-9]),x}).x" // TODO how to filter branches in VCS
    }
}
