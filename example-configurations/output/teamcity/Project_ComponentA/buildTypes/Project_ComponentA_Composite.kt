@file:Suppress("RedundantVisibilityModifier")

package Project_ComponentA.buildTypes

import Project_ComponentA.vcsRoots.Project_ComponentA_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.COMPOSITE
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.ADD_PROBLEM
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.PROMPT
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Project_ComponentA_Composite : BuildType({
	name = "Composite"
	type = COMPOSITE
	vcs {
		root(Project_ComponentA_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		text("reverse.dep.Project_ComponentA_DeployProject.DEPLOY_ADDRESS", "192.168.1.90", description = "Address to deploy to", label = "Deploy address", display = PROMPT)
	}
	steps {
	}
	dependencies {
		dependency(Project_ComponentA_DeployProject) {
			snapshot {
				onDependencyFailure = ADD_PROBLEM
				onDependencyCancel = ADD_PROBLEM
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = "+:* => deploy/"
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
