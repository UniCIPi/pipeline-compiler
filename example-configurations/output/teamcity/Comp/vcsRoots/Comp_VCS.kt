@file:Suppress("RedundantVisibilityModifier")

package Comp.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object Comp_VCS : GitVcsRoot({
	name = "Comp"
	url = "https://github.com/org/comp.git"
	branch = "main"
	branchSpec = """
			|refs/heads/*
			|refs/(tags/*)
			""".trimMargin()

	useTagsAsBranches = true
	authMethod = anonymous()
	checkoutSubmodules = IGNORE
})
