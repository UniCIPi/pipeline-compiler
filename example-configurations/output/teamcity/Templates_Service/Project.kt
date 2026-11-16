@file:Suppress("RedundantVisibilityModifier")

package Templates_Service

import Templates_Service.buildTypes.Templates_Service_Build
import Templates_Service.buildTypes.Templates_Service_DockerBuildAndPush
import Templates_Service.buildTypes.Templates_Service_KubernetesApply
import Templates_Service.buildTypes.Templates_Service_Release
import Templates_Service.vcsRoots.Templates_Service_VCS
import jetbrains.buildServer.configs.kotlin.Project

public object Project : Project({
	name = "Service"
	id("Templates_Service")

	vcsRoot(Templates_Service_VCS)

	buildType(Templates_Service_Build)
	buildType(Templates_Service_DockerBuildAndPush)
	buildType(Templates_Service_KubernetesApply)
	buildType(Templates_Service_Release)
})
