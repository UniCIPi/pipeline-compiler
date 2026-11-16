@file:Suppress("RedundantVisibilityModifier")

package Templates_Library.buildTypes

import Templates_Library.vcsRoots.Templates_Library_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.DEPLOYMENT
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.NORMAL
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Templates_Library_PyPIPublish : BuildType({
	name = "PyPI Publish"
	type = DEPLOYMENT
	artifactRules = "+:dist/*"
	vcs {
		root(Templates_Library_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		password("PYPI_TOKEN", "pypi-token", description = "API token of the package index", label = "", display = NORMAL)
	}
	steps {
		script {
			name = "Build"
			scriptContent = "poetry build"
			dockerImage = "registry.example.com/org/python-poetry:3.13"
		}
		script {
			name = "Publish"
			scriptContent = """
					|export POETRY_PYPI_TOKEN_PYPI=%PYPI_TOKEN%
					|poetry publish
					""".trimMargin()
			dockerImage = "registry.example.com/org/python-poetry:3.13"
		}
	}
	triggers {
		vcs {
			clearBuildParams()
			branchFilter = "+:v*"
		}
	}
	dependencies {
		dependency(Templates_Library_PythonPackage) {
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
			vcsRootExtId = "${Templates_Library_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
	}
})
