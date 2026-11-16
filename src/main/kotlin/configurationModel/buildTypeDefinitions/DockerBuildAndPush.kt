package fanci.compiler.configurationModel.buildTypeDefinitions

/**
 * Build and push Docker image.
 * @see fanci.compiler.pipeline.buildConfiguration.DockerBuildAndPush
 */
data class DockerBuildAndPush(
    /**
     * Relative path to Dockerfile from the dockerContextDir.
     */
    var dockerFilePath: String = "./Dockerfile",

    /**
     * Relative path to the directory containing the docker build context (PATH)
     */
    var dockerContextDir: String = ".",

    /**
     * If defined, the configuration will use a build script instead of `docker build ...` command.
     * Defines the path to the script. The script is called with the argument %fanciGetParam(IMAGE_NAME)%:%fanciGetParam(IMAGE_TAG)%
     */
    var dockerBuildScript: String?,

    /**
     * Name of the Docker image to be built. Including a full repository path, excluding tag
     */
    var imageName: String,
    /**
     * Value of IMAGE_TAG parameter
     * This value can be overridden in triggers with: parameter.name: IMAGE_TAG && parameter.value: <value>
     */
    var imageTag: String,
    /**
     * Docker image tag, which is used for the build step.
     */
    var dockerVersion: String = "latest"
) : IBuildTypeDefinition
