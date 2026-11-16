package fanci.compiler.pipeline.buildConfiguration

import fanci.compiler.pipeline.*
import fanci.compiler.pipeline.DisplayType
import fanci.compiler.pipeline.TextParameter
import fanci.compiler.configurationModel.BuildConfigurationDefinition
import fanci.compiler.configurationModel.buildTypeDefinitions.GoPackage

/**
 * Go build configuration.
 *
 * Build and package Go application.
 *
 * @param buildTypeDefinition GoBuild configuration
 */
class GoPackage(buildConfigurationDefinition: BuildConfigurationDefinition, override val buildTypeDefinition: GoPackage) :
    AbstractBuildConfiguration(buildConfigurationDefinition) {
    override val name = "Go Package"
    override val buildMatrix = mapOf(
        "ARCHITECTURE" to buildTypeDefinition.archList.map { it.name },
        "SYSTEM" to buildTypeDefinition.systemList.map { it.name }
    )
    // The whole build configuration runs in the Go toolchain image.
    override var dockerImage = "golang:${buildTypeDefinition.goVersion}"
    override val parameters = listOf(
        TextParameter(
            name = "PACKAGE_NAME",
            value = "",
            displayType = DisplayType.HIDDEN,
        )
    )
    override val publishArtifacts = listOf(Artifact(source = "%fanciGetParam(PACKAGE_NAME)%.zip"))
    override val buildSteps = listOf(
        BuildStep(
            name = "build",
            // CGO_ENABLED=0 provides statically linked binaries
            // buildvcs=false disables VCS information in the binary, necessary for building private repositories, to which the runner doesn't have access during the build.
            // -ldflags '-w' reduces the binary size by stripping debug information.
            script = """
                #!/bin/bash
                set -e
                
                VERSION=${'$'}(sed -E -n 's/version=([^=]+)/\1/p' < version.txt)
                MODULE_NAME=${'$'}(go list -m | awk -F/ '{print ${'$'}NF}')
                PACKAGE_NAME=${'$'}{MODULE_NAME}_v${'$'}{VERSION}_%fanciGetParam(SYSTEM)%-%fanciGetParam(ARCHITECTURE)%
                
                if [[ -d ${'$'}{PACKAGE_NAME}/ ]]; then
                  echo "ERROR: directory ${'$'}{PACKAGE_NAME}/ already exist." >&2
                  exit 1
                fi
                if [ -f "${'$'}{PACKAGE_NAME}.zip" ]; then
                  echo "File ${'$'}{PACKAGE_NAME}.zip already exists. Please remove it before running the script." >&2
                  exit 1
                fi
                
                export GOFLAGS="-buildvcs=false -a -ldflags=-w"
                CGO_ENABLED=0 GOOS=%fanciGetParam(SYSTEM)% GOARCH=%fanciGetParam(ARCHITECTURE)% go build -o ./${'$'}{PACKAGE_NAME}/bin/ ./cmd/...
                
                %fanciSetParam(PACKAGE_NAME, ${'$'}PACKAGE_NAME)%
            """.trimIndent()
        ),
        BuildStep(
            name = "package",
            script = """
                #!/bin/bash
                set -e
                if [ -d "resources" ]; then
                    cp -r resources/* ./%fanciGetParam(PACKAGE_NAME)%/etc/
                fi
                
                if [ -d "doc" ]; then
                    cp -r doc ./%fanciGetParam(PACKAGE_NAME)%/
                fi
                
                cp LICENSE ./%fanciGetParam(PACKAGE_NAME)%/
                cp README.md ./%fanciGetParam(PACKAGE_NAME)%/
                cp version.txt ./%fanciGetParam(PACKAGE_NAME)%/
                
                go install github.com/Melky-Phoe/gozip/cmd/gozip@v1.0.0
                gozip -c "%fanciGetParam(PACKAGE_NAME)%.zip" "./%fanciGetParam(PACKAGE_NAME)%/"
            """.trimIndent()
        )
    )
}