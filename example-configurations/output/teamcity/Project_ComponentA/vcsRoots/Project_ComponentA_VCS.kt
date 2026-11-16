@file:Suppress("RedundantVisibilityModifier")

package Project_ComponentA.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object Project_ComponentA_VCS : GitVcsRoot({
	name = "Project_ComponentA"
	url = "https://gitlab.example.com/project/component-a.git"
	branch = "main"
	branchSpec = """
			|refs/heads/*
			|refs/(tags/*)
			""".trimMargin()

	useTagsAsBranches = true
	authMethod = anonymous()
	checkoutSubmodules = IGNORE
})
