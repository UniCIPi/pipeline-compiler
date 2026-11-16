@file:Suppress("RedundantVisibilityModifier")

package Templates_Service.buildTypes

import Templates_Service.vcsRoots.Templates_Service_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.XmlReport.XmlReportType
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.xmlReport
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.matrix
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Templates_Service_Build : BuildType({
	name = "Build"
	type = REGULAR
	artifactRules = "+:_build/artifacts/**"
	vcs {
		root(Templates_Service_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "build"
			scriptContent = """
					|rm -rf _build
					|mkdir _build && cd _build
					|cmake -DBUILD_TESTS=ON -DCMAKE_BUILD_TYPE=Release ../
					|cmake --build . --parallel ${'$'}(nproc)
					""".trimMargin()
			dockerImage = "%BUILD_IMAGE%"
		}
		script {
			name = "test"
			scriptContent = "ctest --extra-verbose --output-junit junit-report.xml"
			workingDir = "_build"
			dockerImage = "%BUILD_IMAGE%"
		}
		script {
			name = "package"
			scriptContent = """
					|cpack -B artifacts
					|rm -rf artifacts/_CPack_Packages
					""".trimMargin()
			workingDir = "_build"
			dockerImage = "%BUILD_IMAGE%"
		}
	}
	triggers {
		vcs {
			clearBuildParams()
			branchFilter = "+:*"
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Templates_Service_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
		matrix {
			param(
				"BUILD_IMAGE", listOf(
					value("registry.example.com/org/build-ubuntu:24.04"),
					value("registry.example.com/org/build-debian:12")
				)
			)
			groupArtifactsByBuild = false
		}
		xmlReport {
			reportType = XmlReportType.JUNIT
			rules = "_build/junit-report.xml"
		}
	}
})
