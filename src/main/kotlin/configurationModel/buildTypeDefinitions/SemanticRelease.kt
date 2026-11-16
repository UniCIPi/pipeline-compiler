package fanci.compiler.configurationModel.buildTypeDefinitions

/**
 * Release of the component by [semantic-release](https://semantic-release.gitbook.io/semantic-release).
 *
 * It is defined in `build-configurations` like any other build type. The fields common to every build
 * configuration keep their meaning:
 * - `docker-image` is required and must contain semantic-release with the plugins used by the [preset],
 * - `dependencies` are the build configurations that must pass before the release is created,
 * - `triggers` replace the default trigger of the template when defined.
 *
 * @see fanci.compiler.pipeline.buildConfiguration.SemanticRelease
 */
data class SemanticRelease(
    /**
     * Name of the build configuration.
     */
    val configurationName: String = "Component release",

    /**
     * Sets preset for semantic release. Each company/system can have different preset for component release management.
     */
    val preset: String,

    /**
     * Path to the file holding the version of the component, e.g. version.txt, pyproject.toml, Cargo.toml.
     * The release writes the new version into it. No file is updated when not set.
     */
    val versionFilePath: String? = null,

    /**
     * ID of the token with write access to the repository. The release uses it to push the new tag and
     * to create the release in the VCS host system.
     *
     * semantic-release 25.0.3 does not support OAuth tokens.
     */
    val repoAccessTokenId: String,
) : IBuildTypeDefinition {
    init {
        require(configurationName.isNotBlank()) {
            "Configuration name of a SemanticRelease build configuration cannot be blank."
        }
    }
}
