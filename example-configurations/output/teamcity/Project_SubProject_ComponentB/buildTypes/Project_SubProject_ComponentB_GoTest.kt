@file:Suppress("RedundantVisibilityModifier")

package Project_SubProject_ComponentB.buildTypes

import Project_SubProject_ComponentB.vcsRoots.Project_SubProject_ComponentB_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildFeatures.XmlReport.XmlReportType
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.xmlReport
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object Project_SubProject_ComponentB_GoTest : BuildType({
	name = "Go Test"
	type = REGULAR
	vcs {
		root(Project_SubProject_ComponentB_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "test"
			scriptContent = """
					|set -o pipefail
					|go install github.com/jstemmer/go-junit-report/v2@latest
					|go test -v ./... 2>&1 | go-junit-report -set-exit-code > junit-report.xml
					""".trimMargin()
			dockerImage = "golang:1.24"
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Project_SubProject_ComponentB_VCS.id}"
			provider = gitlab {
				authType = vcsRoot()
			}
		}
		xmlReport {
			reportType = XmlReportType.JUNIT
			rules = "junit-report.xml"
		}
	}
})
