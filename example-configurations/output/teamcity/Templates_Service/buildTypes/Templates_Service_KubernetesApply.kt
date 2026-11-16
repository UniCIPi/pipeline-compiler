@file:Suppress("RedundantVisibilityModifier")

package Templates_Service.buildTypes

import Templates_Service.vcsRoots.Templates_Service_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.DEPLOYMENT
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Templates_Service_KubernetesApply : BuildType({
	name = "Kubernetes Apply"
	type = DEPLOYMENT
	vcs {
		root(Templates_Service_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("KUBECONFIG_FILE", "./kubeconfig")
	}
	steps {
		script {
			name = "apply kubeconfig"
			scriptContent = "echo \"%kubeconfig%\" > %KUBECONFIG_FILE%"
			dockerImage = "alpine/kubectl:latest"
		}
		script {
			name = "Apply deploy/service.yaml"
			scriptContent = "KUBECONFIG=%KUBECONFIG_FILE% kubectl apply -f deploy/service.yaml"
			dockerImage = "alpine/kubectl:latest"
		}
	}
	triggers {
		vcs {
			clearBuildParams()
			branchFilter = "+:main"
		}
	}
	dependencies {
		dependency(Templates_Service_DockerBuildAndPush) {
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
	}
})
