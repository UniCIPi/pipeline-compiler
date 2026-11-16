@file:Suppress("RedundantVisibilityModifier")

package Templates_Library

import Templates_Library.buildTypes.Templates_Library_PyPIPublish
import Templates_Library.buildTypes.Templates_Library_PythonPackage
import Templates_Library.vcsRoots.Templates_Library_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "Library"
	id("Templates_Library")

	vcsRoot(Templates_Library_VCS)

	buildType(Templates_Library_PythonPackage)
	buildType(Templates_Library_PyPIPublish)
})
