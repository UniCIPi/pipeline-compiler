@file:Suppress("RedundantVisibilityModifier")

package Project_SubProject_ComponentB.buildTypes

import Project_SubProject_ComponentB.vcsRoots.Project_SubProject_ComponentB_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.HIDDEN
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.matrix

public object Project_SubProject_ComponentB_GoPackage : BuildType({
	name = "Go Package"
	type = REGULAR
	artifactRules = "+:%PACKAGE_NAME%.zip"
	vcs {
		root(Project_SubProject_ComponentB_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		text("PACKAGE_NAME", "", description = "", label = "", display = HIDDEN)
	}
	steps {
		script {
			name = "build"
			scriptContent = """
					|#!/bin/bash
					|set -e
					|
					|VERSION=${'$'}(sed -E -n 's/version=([^=]+)/\1/p' < version.txt)
					|MODULE_NAME=${'$'}(go list -m | awk -F/ '{print ${'$'}NF}')
					|PACKAGE_NAME=${'$'}{MODULE_NAME}_v${'$'}{VERSION}_%SYSTEM%-%ARCHITECTURE%
					|
					|if [[ -d ${'$'}{PACKAGE_NAME}/ ]]; then
					|  echo "ERROR: directory ${'$'}{PACKAGE_NAME}/ already exist." >&2
					|  exit 1
					|fi
					|if [ -f "${'$'}{PACKAGE_NAME}.zip" ]; then
					|  echo "File ${'$'}{PACKAGE_NAME}.zip already exists. Please remove it before running the script." >&2
					|  exit 1
					|fi
					|
					|export GOFLAGS="-buildvcs=false -a -ldflags=-w"
					|CGO_ENABLED=0 GOOS=%SYSTEM% GOARCH=%ARCHITECTURE% go build -o ./${'$'}{PACKAGE_NAME}/bin/ ./cmd/...
					|
					|echo "##teamcity[setParameter name='PACKAGE_NAME' value='${'$'}PACKAGE_NAME']"
					""".trimMargin()
			dockerImage = "golang:1.24"
		}
		script {
			name = "package"
			scriptContent = """
					|#!/bin/bash
					|set -e
					|if [ -d "resources" ]; then
					|    cp -r resources/* ./%PACKAGE_NAME%/etc/
					|fi
					|
					|if [ -d "doc" ]; then
					|    cp -r doc ./%PACKAGE_NAME%/
					|fi
					|
					|cp LICENSE ./%PACKAGE_NAME%/
					|cp README.md ./%PACKAGE_NAME%/
					|cp version.txt ./%PACKAGE_NAME%/
					|
					|go install github.com/Melky-Phoe/gozip/cmd/gozip@v1.0.0
					|gozip -c "%PACKAGE_NAME%.zip" "./%PACKAGE_NAME%/"
					""".trimMargin()
			dockerImage = "golang:1.24"
		}
	}
	dependencies {
		dependency(Project_SubProject_ComponentB_GoTest) {
			snapshot {
				onDependencyFailure = FAIL_TO_START
				onDependencyCancel = FAIL_TO_START
				synchronizeRevisions = true
				reuseBuilds = NO
			}
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Project_SubProject_ComponentB_VCS.id}"
			provider = gitlab {
				authType = vcsRoot()
			}
		}
		matrix {
			param(
				"ARCHITECTURE", listOf(
					value("amd64"),
					value("arm64")
				)
			)
			param(
				"SYSTEM", listOf(
					value("linux"),
					value("windows")
				)
			)
			groupArtifactsByBuild = false
		}
	}
})
