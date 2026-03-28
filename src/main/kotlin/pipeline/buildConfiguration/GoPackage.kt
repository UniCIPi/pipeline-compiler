package com.bringauto.pipelinecompiler.pipeline.buildConfiguration

import com.bringauto.pipelinecompiler.pipeline.*
import com.bringauto.pipelinecompiler.pipeline.DisplayType
import com.bringauto.pipelinecompiler.pipeline.TextParameter
import com.bringauto.pipelinecompiler.configurationModel.BuildStepConfiguration
import com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions.GoPackage

/**
 * Go build configuration.
 *
 * Build and package Go application.
 *
 * @param buildTypeDefinition GoBuild configuration
 */
class GoPackage(buildStepConfiguration: BuildStepConfiguration, override val buildTypeDefinition: GoPackage) :
    AbstractBuildConfiguration(buildStepConfiguration) {
    override val name = "Go Package"
    override val matrix = mapOf(
        "ARCHITECTURE" to buildTypeDefinition.archList.map { it.name },
        "SYSTEM" to buildTypeDefinition.systemList.map { it.name }
    )
    private val dockerImage = "golang:${buildTypeDefinition.goVersion}"
    override val parameters = listOf(
        TextParameter(
            name = "PACKAGE_NAME",
            value = "",
            displayType = DisplayType.HIDDEN,
        )
    )
    override val publishArtifacts = listOf(Artifact(source = "%pipelineGetParam(PACKAGE_NAME)%.zip"))
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
                PACKAGE_NAME=${'$'}{MODULE_NAME}_v${'$'}{VERSION}_%pipelineGetParam(SYSTEM)%-%pipelineGetParam(ARCHITECTURE)%
                
                if [[ -d ${'$'}{PACKAGE_NAME}/ ]]; then
                  echo "ERROR: directory ${'$'}{PACKAGE_NAME}/ already exist." >&2
                  exit 1
                fi
                if [ -f "${'$'}{PACKAGE_NAME}.zip" ]; then
                  echo "File ${'$'}{PACKAGE_NAME}.zip already exists. Please remove it before running the script." >&2
                  exit 1
                fi
                
                export GOFLAGS="-buildvcs=false -a -ldflags=-w"
                CGO_ENABLED=0 GOOS=%pipelineGetParam(SYSTEM)% GOARCH=%pipelineGetParam(ARCHITECTURE)% go build -o ./${'$'}{PACKAGE_NAME}/bin/ ./cmd/...
                
                %pipelineSetParam(PACKAGE_NAME, ${'$'}PACKAGE_NAME)%
            """.trimIndent(),
            dockerImage = dockerImage
        ),
        BuildStep(
            name = "package",
            script = """
                #!/bin/bash
                set -e
                if [ -d "resources" ]; then
                    cp -r resources/* ./%pipelineGetParam(PACKAGE_NAME)%/etc/
                fi
                
                if [ -d "doc" ]; then
                    cp -r doc ./%pipelineGetParam(PACKAGE_NAME)%/
                fi
                
                cp LICENSE ./%pipelineGetParam(PACKAGE_NAME)%/
                cp README.md ./%pipelineGetParam(PACKAGE_NAME)%/
                cp version.txt ./%pipelineGetParam(PACKAGE_NAME)%/
                
                go install github.com/Melky-Phoe/gozip/cmd/gozip@v1.0.0
                gozip -c "%pipelineGetParam(PACKAGE_NAME)%.zip" "./%pipelineGetParam(PACKAGE_NAME)%/"
            """.trimIndent(),
            dockerImage = dockerImage
        )
    )
}