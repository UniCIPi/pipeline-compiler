@file:Suppress("RedundantVisibilityModifier")

package Parameters

import Parameters.buildTypes.Parameters_Command
import Parameters.buildTypes.Parameters_References
import Parameters.vcsRoots.Parameters_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "parameters"
	id("Parameters")

	vcsRoot(Parameters_VCS)

	buildType(Parameters_Command)
	buildType(Parameters_References)
})
