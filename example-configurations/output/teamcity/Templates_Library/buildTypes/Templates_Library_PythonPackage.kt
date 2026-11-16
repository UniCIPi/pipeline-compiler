@file:Suppress("RedundantVisibilityModifier")

package Templates_Library.buildTypes

import Templates_Library.vcsRoots.Templates_Library_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.XmlReport.XmlReportType
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.xmlReport
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.matrix
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Templates_Library_PythonPackage : BuildType({
	name = "Python Package"
	type = REGULAR
	artifactRules = "+:artifacts/**"
	vcs {
		root(Templates_Library_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "Lint"
			scriptContent = "ruff check ."
			dockerImage = "%IMAGE%"
		}
		script {
			name = "Install"
			scriptContent = "poetry install"
			dockerImage = "%IMAGE%"
		}
		script {
			name = "Type check"
			scriptContent = "poetry run mypy ."
			dockerImage = "%IMAGE%"
		}
		script {
			name = "Test"
			scriptContent = "poetry run pytest --verbose --junitxml=junit-report.xml"
			dockerImage = "%IMAGE%"
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
			vcsRootExtId = "${Templates_Library_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
		matrix {
			param(
				"IMAGE", listOf(
					value("registry.example.com/org/python-poetry:3.12"),
					value("registry.example.com/org/python-poetry:3.13")
				)
			)
			groupArtifactsByBuild = false
		}
		xmlReport {
			reportType = XmlReportType.JUNIT
			rules = "junit-report.xml"
		}
	}
})
