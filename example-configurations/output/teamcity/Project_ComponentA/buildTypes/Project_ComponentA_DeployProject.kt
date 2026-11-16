@file:Suppress("RedundantVisibilityModifier")

package Project_ComponentA.buildTypes

import Project_ComponentA.vcsRoots.Project_ComponentA_VCS
import Project_SubProject_ComponentB.buildTypes.Project_SubProject_ComponentB_Composite
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.ADD_PROBLEM
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Project_ComponentA_DeployProject : BuildType({
	name = "Deploy Project"
	type = REGULAR
	artifactRules = "+:deploy-details.txt"
	vcs {
		root(Project_ComponentA_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("DEPLOY_MACHINE", "linux-amd64")
		param("DEPLOY_ADDRESS", "192.168.1.100")
	}
	steps {
		script {
			name = "Deploy"
			scriptContent = "echo \"This is a script that deploys ComponentA to %DEPLOY_ADDRESS%\""
		}
	}
	dependencies {
		dependency(Project_SubProject_ComponentB_Composite) {
			snapshot {
				onDependencyFailure = ADD_PROBLEM
				onDependencyCancel = ADD_PROBLEM
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = "+:*%DEPLOY_MACHINE%.tar => ComponentB/"
			}
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Project_ComponentA_VCS.id}"
			provider = gitlab {
				authType = vcsRoot()
			}
		}
	}
})
