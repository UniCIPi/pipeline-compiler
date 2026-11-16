@file:Suppress("RedundantVisibilityModifier")

package Project_SubProject_ComponentB.buildTypes

import Project_SubProject_ComponentB.vcsRoots.Project_SubProject_ComponentB_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.COMPOSITE
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.ADD_PROBLEM
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Project_SubProject_ComponentB_Composite : BuildType({
	name = "Composite"
	type = COMPOSITE
	vcs {
		root(Project_SubProject_ComponentB_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
	}
	dependencies {
		dependency(Project_SubProject_ComponentB_GoPackage) {
			snapshot {
				onDependencyFailure = ADD_PROBLEM
				onDependencyCancel = ADD_PROBLEM
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = "+:* => package/"
			}
		}
		dependency(Project_SubProject_ComponentB_GoTest) {
			snapshot {
				onDependencyFailure = ADD_PROBLEM
				onDependencyCancel = ADD_PROBLEM
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = "+:test-results.xml => test-results/"
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
	}
})
