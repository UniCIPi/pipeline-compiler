@file:Suppress("RedundantVisibilityModifier")

package Project_SubProject_ComponentB.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.SUBMODULES_CHECKOUT

public object Project_SubProject_ComponentB_VCS : GitVcsRoot({
	name = "Project_SubProject_ComponentB"
	url = "https://gitlab.example.com/project/subproject/component-b.git"
	branch = "main"
	branchSpec = """
			|refs/heads/*
			|refs/(tags/*)
			""".trimMargin()

	useTagsAsBranches = true
	authMethod = anonymous()
	checkoutSubmodules = SUBMODULES_CHECKOUT
})
