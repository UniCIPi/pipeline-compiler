@file:Suppress("RedundantVisibilityModifier")

package ReverseDependencyTree_SharedTriggers.buildTypes

import ReverseDependencyTree_SharedTriggers.vcsRoots.ReverseDependencyTree_SharedTriggers_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object ReverseDependencyTree_SharedTriggers_B : BuildType({
	name = "B"
	type = REGULAR
	vcs {
		root(ReverseDependencyTree_SharedTriggers_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("BUILD_ID", "unset")
		param("StartedBy", "default")
	}
	steps {
		script {
			name = "Report the build"
			scriptContent = """
					|set -eu
					|echo "B: build %build.number% on branch %teamcity.build.branch% at ${'$'}(date -u +%H:%M:%S)"
					|echo "B: StartedBy = %StartedBy%"
					|echo "##teamcity[setParameter name='BUILD_ID' value='%build.number%']"
					|""".trimMargin()
			dockerImage = "docker.io/library/debian:12"
		}
	}
	features {
	}
})
