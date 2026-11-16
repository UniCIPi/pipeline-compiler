@file:Suppress("RedundantVisibilityModifier")

package Parameters.buildTypes

import Parameters.vcsRoots.Parameters_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Parameters_Command : BuildType({
	name = "Command"
	type = REGULAR
	vcs {
		root(Parameters_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("TIME_CMD", "date | tr a-z A-Z")
	}
	steps {
		script {
			name = "s1"
			scriptContent = """
					|echo "${'$'}(eval "%TIME_CMD%")"
					|""".trimMargin()
			dockerImage = "debian:12"
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Parameters_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
	}
})
