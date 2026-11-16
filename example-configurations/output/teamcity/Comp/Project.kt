@file:Suppress("RedundantVisibilityModifier")

package Comp

import Comp.buildTypes.Comp_A
import Comp.buildTypes.Comp_B
import Comp.vcsRoots.Comp_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "comp"
	id("Comp")

	vcsRoot(Comp_VCS)

	buildType(Comp_B)
	buildType(Comp_A)
})
