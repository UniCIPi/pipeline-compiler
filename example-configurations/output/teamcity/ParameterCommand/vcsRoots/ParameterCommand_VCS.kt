@file:Suppress("RedundantVisibilityModifier")

package ParameterCommand.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object ParameterCommand_VCS : GitVcsRoot({
	name = "ParameterCommand"
	url = "https://gitlab.example.com/fanci/examples.git"
	branch = "main"
	branchSpec = """
			|refs/heads/*
			|refs/(tags/*)
			""".trimMargin()

	useTagsAsBranches = true
	authMethod = anonymous()
	checkoutSubmodules = IGNORE
})
