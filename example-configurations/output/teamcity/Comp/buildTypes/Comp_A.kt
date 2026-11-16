@file:Suppress("RedundantVisibilityModifier")

package Comp.buildTypes

import Comp.vcsRoots.Comp_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.matrix
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Comp_A : BuildType({
	name = "A"
	type = REGULAR
	vcs {
		root(Comp_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "s1"
			scriptContent = """
					|cat p.txt
					|echo "%dep.Comp_B.Q%"
					|echo "%M%"
					|""".trimMargin()
			dockerImage = "debian:12"
		}
	}
	triggers {
		vcs {
			triggerRules = """
				+:src/**
			""".trimIndent()
			clearBuildParams()
			branchFilter = "+:main"
		}
		schedule {
			schedulingPolicy = cron {
				minutes = "0"
				hours = "3"
				dayOfMonth = "*"
				month = "*"
				dayOfWeek = "*"
			}
			withPendingChangesOnly = false
			triggerBuild = always()
			clearBuildParams()
			buildParams {
				param("reverse.dep.Comp_B.P", "sched")
			}
			branchFilter = "+:main"
		}
	}
	dependencies {
		dependency(Comp_B) {
			snapshot {
				onDependencyFailure = FAIL_TO_START
				onDependencyCancel = FAIL_TO_START
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = "+:p.txt"
			}
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Comp_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
		matrix {
			param(
				"M", listOf(
					value("x"),
					value("y")
				)
			)
			groupArtifactsByBuild = false
		}
	}
})
