@file:Suppress("RedundantVisibilityModifier")

package ParameterCommand

import ParameterCommand.buildTypes.ParameterCommand_Time
import ParameterCommand.vcsRoots.ParameterCommand_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "parameter-command"
	id("ParameterCommand")

	vcsRoot(ParameterCommand_VCS)

	buildType(ParameterCommand_Time)
})
