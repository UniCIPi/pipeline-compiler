@file:Suppress("RedundantVisibilityModifier")

package Project_ComponentA

import Project_ComponentA.buildTypes.Project_ComponentA_Composite
import Project_ComponentA.buildTypes.Project_ComponentA_DeployProject
import Project_ComponentA.vcsRoots.Project_ComponentA_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "ComponentA"
	id("Project_ComponentA")

	vcsRoot(Project_ComponentA_VCS)

	buildType(Project_ComponentA_DeployProject)
	buildType(Project_ComponentA_Composite)
})
