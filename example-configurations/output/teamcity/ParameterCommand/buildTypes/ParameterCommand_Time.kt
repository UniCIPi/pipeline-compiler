@file:Suppress("RedundantVisibilityModifier")

package ParameterCommand.buildTypes

import ParameterCommand.vcsRoots.ParameterCommand_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object ParameterCommand_Time : BuildType({
	name = "Time"
	type = REGULAR
	vcs {
		root(ParameterCommand_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("CMD", "date")
	}
	steps {
		script {
			name = "s1"
			scriptContent = """
					|echo "Current time: ${'$'}(%CMD%)"
					|""".trimMargin()
			dockerImage = "docker.io/library/alpine:3.20"
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${ParameterCommand_VCS.id}"
			provider = gitlab {
				authType = vcsRoot()
			}
		}
	}
})
