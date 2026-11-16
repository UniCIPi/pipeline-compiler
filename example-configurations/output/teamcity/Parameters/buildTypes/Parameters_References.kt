@file:Suppress("RedundantVisibilityModifier")

package Parameters.buildTypes

import Parameters.vcsRoots.Parameters_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Parameters_References : BuildType({
	name = "References"
	type = REGULAR
	vcs {
		root(Parameters_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		param("N", "app")
		param("BR", "%teamcity.build.branch%")
		param("PACKAGE", "%N%-%BR%")
	}
	steps {
		script {
			name = "s1"
			scriptContent = """
					|echo "%PACKAGE%"
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
