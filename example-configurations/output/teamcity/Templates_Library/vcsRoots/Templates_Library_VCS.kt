@file:Suppress("RedundantVisibilityModifier")

package Templates_Library.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object Templates_Library_VCS : GitVcsRoot({
	name = "Templates_Library"
	url = "https://github.com/org/library.git"
	branch = "main"
	branchSpec = """
			|refs/heads/*
			|refs/(tags/*)
			""".trimMargin()

	useTagsAsBranches = true
	authMethod = anonymous()
	checkoutSubmodules = IGNORE
})
