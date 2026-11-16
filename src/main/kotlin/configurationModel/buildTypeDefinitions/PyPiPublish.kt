package fanci.compiler.configurationModel.buildTypeDefinitions

/**
 * Build a Python package with Poetry and publish it to a package index.
 *
 * The `docker-image` of the build configuration must contain Python and Poetry.
 *
 * @see fanci.compiler.pipeline.buildConfiguration.PyPiPublish
 */
data class PyPiPublish(
    /**
     * Name of the build configuration.
     */
    val configurationName: String = "PyPI Publish",

    /**
     * ID of the API token of the package index. The token is stored as a secret in the target platform.
     */
    val tokenId: String,

    /**
     * URL of the package index the package is uploaded to. PyPI is used when not set.
     */
    val repositoryUrl: String? = null,
) : IBuildTypeDefinition {
    init {
        require(configurationName.isNotBlank()) {
            "Configuration name of a PyPiPublish build configuration cannot be blank."
        }
    }
}
