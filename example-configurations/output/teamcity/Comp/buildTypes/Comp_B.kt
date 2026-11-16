@file:Suppress("RedundantVisibilityModifier")

package Comp.buildTypes

import Comp.vcsRoots.Comp_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Comp_B : BuildType({
	name = "B"
	type = REGULAR
	artifactRules = "+:p.txt"
	vcs {
		root(Comp_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("P", "p0")
		param("Q", "q0")
	}
	steps {
		script {
			name = "s1"
			scriptContent = """
					|echo "%P%" > p.txt
					|echo "##teamcity[setParameter name='Q' value='q1']"
					|""".trimMargin()
			dockerImage = "debian:12"
		}
	}
	triggers {
		vcs {
			clearBuildParams()
			buildParams {
				param("P", "%teamcity.build.branch%")
			}
			branchFilter = """
					|+:*
					|-:doc*
					""".trimMargin()
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Comp_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
	}
})
