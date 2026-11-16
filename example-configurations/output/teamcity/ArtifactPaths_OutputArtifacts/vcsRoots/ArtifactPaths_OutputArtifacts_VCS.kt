@file:Suppress("RedundantVisibilityModifier")

package ArtifactPaths_OutputArtifacts.vcsRoots

import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot.CheckoutSubmodules.IGNORE

public object ArtifactPaths_OutputArtifacts_VCS : GitVcsRoot({
	name = "ArtifactPaths_OutputArtifacts"
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
