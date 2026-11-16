@file:Suppress("RedundantVisibilityModifier")

package ReverseDependencyTree_SharedTriggers.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object ReverseDependencyTree_SharedTriggers_VCS : GitVcsRoot({
	name = "ReverseDependencyTree_SharedTriggers"
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
