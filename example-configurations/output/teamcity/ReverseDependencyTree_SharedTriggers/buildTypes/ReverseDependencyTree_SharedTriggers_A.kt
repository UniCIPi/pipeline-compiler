@file:Suppress("RedundantVisibilityModifier")

package ReverseDependencyTree_SharedTriggers.buildTypes

import ReverseDependencyTree_SharedTriggers.vcsRoots.ReverseDependencyTree_SharedTriggers_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object ReverseDependencyTree_SharedTriggers_A : BuildType({
	name = "A"
	type = REGULAR
	vcs {
		root(ReverseDependencyTree_SharedTriggers_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("BUILD_ID", "unset")
	}
	steps {
		script {
			name = "Report the build and the used build of B"
			scriptContent = """
					|set -e
					|echo "A: build %build.number% on branch %teamcity.build.branch% at ${'$'}(date -u +%H:%M:%S)"
					|echo "A: uses B build %dep.ReverseDependencyTree_SharedTriggers_B.BUILD_ID%"
					|echo "##teamcity[setParameter name='BUILD_ID' value='%build.number%']"
					|""".trimMargin()
			dockerImage = "docker.io/library/debian:12"
		}
	}
	triggers {
		vcs {
			clearBuildParams()
			buildParams {
				param("reverse.dep.ReverseDependencyTree_SharedTriggers_B.StartedBy", "A")
			}
			branchFilter = "+:main"
		}
		vcs {
			clearBuildParams()
			buildParams {
				param("reverse.dep.ReverseDependencyTree_SharedTriggers_B.StartedBy", "A")
			}
			branchFilter = "+:fix-*"
		}
		schedule {
			schedulingPolicy = cron {
				minutes = "*/10"
				hours = "*"
				dayOfMonth = "*"
				month = "*"
				dayOfWeek = "*"
			}
			withPendingChangesOnly = false
			triggerBuild = always()
			clearBuildParams()
			buildParams {
				param("reverse.dep.ReverseDependencyTree_SharedTriggers_B.StartedBy", "A-schedule")
			}
			branchFilter = "+:main"
		}
	}
	dependencies {
		dependency(ReverseDependencyTree_SharedTriggers_B) {
			snapshot {
				onDependencyFailure = FAIL_TO_START
				onDependencyCancel = FAIL_TO_START
				synchronizeRevisions = true
				reuseBuilds = NO
			}
		}
	}
	features {
	}
})
