@file:Suppress("RedundantVisibilityModifier")

package Templates_Service.buildTypes

import Templates_Service.vcsRoots.Templates_Service_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.DEPLOYMENT
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.NORMAL
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.dockerRegistryConnections
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Templates_Service_DockerBuildAndPush : BuildType({
	name = "Docker Build and Push"
	type = DEPLOYMENT
	vcs {
		root(Templates_Service_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		text("IMAGE_TAG", "%teamcity.build.branch%", description = "Image tag", label = "", display = NORMAL)
		text("IMAGE_NAME", "registry.example.com/org/service", description = "Image name", label = "", display = NORMAL)
	}
	steps {
		script {
			name = "Remove prefix with slashes from image tag"
			conditions {
				contains("IMAGE_TAG", "/")
			}
			scriptContent = """
					|TAG="%IMAGE_TAG%"
					|echo "Removed slash from IMAGE_TAG"
					|echo "New tag = ${'$'}{TAG##*/}"
					|echo "##teamcity[setParameter name='IMAGE_TAG' value='${'$'}{TAG##*/}']"
					""".trimMargin()
		}
		script {
			name = "build"
			scriptContent = "docker build -f \"Dockerfile\" -t \"%IMAGE_NAME%:%IMAGE_TAG%\" ."
		}
		script {
			name = "push"
			scriptContent = "docker push \"%IMAGE_NAME%:%IMAGE_TAG%\""
		}
	}
	dependencies {
		dependency(Templates_Service_Build) {
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
			vcsRootExtId = "${Templates_Service_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
		dockerRegistryConnections {
			loginToRegistry = on {
				dockerRegistryId = "registry"
			}
		}
	}
})
