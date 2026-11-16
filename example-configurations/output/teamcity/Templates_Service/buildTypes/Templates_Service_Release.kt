@file:Suppress("RedundantVisibilityModifier")

package Templates_Service.buildTypes

import Templates_Service.vcsRoots.Templates_Service_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.DEPLOYMENT
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.HIDDEN
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.NORMAL
import jetbrains.buildServer.configs.kotlin.ParameterDisplay.PROMPT
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

public object Templates_Service_Release : BuildType({
	name = "Release"
	type = DEPLOYMENT
	vcs {
		root(Templates_Service_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	params {
		password("TOKEN", "release-token", description = "Token with write_api access to the repository", label = "", display = NORMAL)
		select("DRY_RUN", "true", description = "When true, the build doesn't publish a release", label = "Dry run", display = PROMPT, options = listOf("true", "false"), allowMultiple = false)
		text("RELEASE_RC_CONTENT", "ewogICJicmFuY2hlcyIgOiBbIHsKICAgICJuYW1lIiA6ICJtYWluIgogIH0sIHsKICAgICJuYW1lIiA6ICIrKFswLTldKT8oLnsrKFswLTldKSx4fSkueCIKICB9IF0sCiAgInJlcG9zaXRvcnlVcmwiIDogImh0dHBzOi8vZ2l0aHViLmNvbS9vcmcvc2VydmljZS5naXQiLAogICJwbHVnaW5zIiA6IFsgWyAiQHNlbWFudGljLXJlbGVhc2UvY29tbWl0LWFuYWx5emVyIiwgewogICAgInByZXNldCIgOiAiY29udmVudGlvbmFsY29tbWl0cyIsCiAgICAicmVsZWFzZVJ1bGVzIiA6IFsgewogICAgICAicmVsZWFzZSIgOiAibWFqb3IiLAogICAgICAiYnJlYWtpbmciIDogdHJ1ZQogICAgfSwgewogICAgICAidHlwZSIgOiAiZmVhdCIsCiAgICAgICJyZWxlYXNlIiA6ICJtaW5vciIKICAgIH0sIHsKICAgICAgInR5cGUiIDogImZpeCIsCiAgICAgICJyZWxlYXNlIiA6ICJwYXRjaCIKICAgIH0sIHsKICAgICAgInR5cGUiIDogImRvY3MiLAogICAgICAicmVsZWFzZSIgOiAicGF0Y2giCiAgICB9LCB7CiAgICAgICJ0eXBlIiA6ICJwZXJmIiwKICAgICAgInJlbGVhc2UiIDogInBhdGNoIgogICAgfSwgewogICAgICAidHlwZSIgOiAicmV2ZXJ0IiwKICAgICAgInJlbGVhc2UiIDogInBhdGNoIgogICAgfSwgewogICAgICAidHlwZSIgOiAiY2hvcmUiLAogICAgICAicmVsZWFzZSIgOiAicGF0Y2giCiAgICB9LCB7CiAgICAgICJ0eXBlIiA6ICJjaG9yZSIsCiAgICAgICJzY29wZSIgOiAicmVsZWFzZSIsCiAgICAgICJyZWxlYXNlIiA6IGZhbHNlCiAgICB9LCB7CiAgICAgICJzY29wZSIgOiAibm8tcmVsZWFzZSIsCiAgICAgICJyZWxlYXNlIiA6IGZhbHNlCiAgICB9IF0KICB9IF0sIFsgIkBzZW1hbnRpYy1yZWxlYXNlL3JlbGVhc2Utbm90ZXMtZ2VuZXJhdG9yIiwgewogICAgInByZXNldCIgOiAiY29udmVudGlvbmFsY29tbWl0cyIsCiAgICAicHJlc2V0Q29uZmlnIiA6IHsKICAgICAgInR5cGVzIiA6IFsgewogICAgICAgICJ0eXBlIiA6ICJmZWF0IiwKICAgICAgICAic2VjdGlvbiIgOiAiTmV3IEZlYXR1cmVzIPCfmoAiCiAgICAgIH0sIHsKICAgICAgICAidHlwZSIgOiAiZml4IiwKICAgICAgICAic2VjdGlvbiIgOiAiQnVnIEZpeGVzIPCfkJvvuI8iCiAgICAgIH0sIHsKICAgICAgICAidHlwZSIgOiAiZG9jcyIsCiAgICAgICAgInNlY3Rpb24iIDogIkRvY3VtZW50YXRpb24gQ2hhbmdlcyDwn5OdIgogICAgICB9LCB7CiAgICAgICAgInR5cGUiIDogImNob3JlIiwKICAgICAgICAic2VjdGlvbiIgOiAiTWlzY2VsbGFuZW91cyBDaG9yZXMg8J+ShSIKICAgICAgfSwgewogICAgICAgICJ0eXBlIiA6ICJ0ZXN0IiwKICAgICAgICAic2VjdGlvbiIgOiAiVGVzdHMg8J+Toe+4jyIKICAgICAgfSwgewogICAgICAgICJ0eXBlIiA6ICJwZXJmIiwKICAgICAgICAic2VjdGlvbiIgOiAiUGVyZm9ybWFuY2UgSW1wcm92ZW1lbnRzIPCfmoTvuI8iCiAgICAgIH0sIHsKICAgICAgICAidHlwZSIgOiAiYnVpbGQiLAogICAgICAgICJzZWN0aW9uIiA6ICJCdWlsZCBzeXN0ZW0g8J+boO+4jyIKICAgICAgfSwgewogICAgICAgICJ0eXBlIiA6ICJjaSIsCiAgICAgICAgInNlY3Rpb24iIDogIkJ1aWxkIHN5c3RlbSDwn5ug77iPIgogICAgICB9IF0KICAgIH0KICB9IF0sIFsgIkBzZW1hbnRpYy1yZWxlYXNlL2NoYW5nZWxvZyIsIHsKICAgICJjaGFuZ2Vsb2dGaWxlIiA6ICJDSEFOR0VMT0cubWQiCiAgfSBdLCBbICJzZW1hbnRpYy1yZWxlYXNlLXJlcGxhY2UtcGx1Z2luIiwgewogICAgInJlcGxhY2VtZW50cyIgOiBbIHsKICAgICAgImZpbGVzIiA6IFsgInZlcnNpb24udHh0IiBdLAogICAgICAiZnJvbSIgOiAiXnZlcnNpb249LioiLAogICAgICAidG8iIDogInZlcnNpb249JHtuZXh0UmVsZWFzZS52ZXJzaW9ufSIsCiAgICAgICJyZXN1bHRzIiA6IFsgewogICAgICAgICJmaWxlIiA6ICJ2ZXJzaW9uLnR4dCIsCiAgICAgICAgImhhc0NoYW5nZWQiIDogdHJ1ZSwKICAgICAgICAibnVtTWF0Y2hlcyIgOiAxLAogICAgICAgICJudW1SZXBsYWNlbWVudHMiIDogMQogICAgICB9IF0sCiAgICAgICJjb3VudE1hdGNoZXMiIDogdHJ1ZQogICAgfSBdCiAgfSBdLCBbICJAc2VtYW50aWMtcmVsZWFzZS9naXRodWIiLCB7CiAgICAiYXNzZXRzIiA6IFsgewogICAgICAicGF0aCIgOiAiQ0hBTkdFTE9HLm1kIgogICAgfSBdCiAgfSBdLCBbICJAc2VtYW50aWMtcmVsZWFzZS9naXQiLCB7CiAgICAiYXNzZXRzIiA6IFsgInZlcnNpb24udHh0IiwgIkNIQU5HRUxPRy5tZCIgXSwKICAgICJtZXNzYWdlIiA6ICJjaG9yZShyZWxlYXNlKTogJHtuZXh0UmVsZWFzZS52ZXJzaW9ufSBbc2tpcCBjaV1cXG4ke25leHRSZWxlYXNlLm5vdGVzfSIKICB9IF0gXQp9", description = "Base64-encoded .releaserc.json; decoded at runtime by the build step", label = "Content of the .releaserc.json file (base64)", display = HIDDEN)
	}
	steps {
		script {
			name = "Create .releaserc.json"
			scriptContent = """
					|#!/bin/bash
					|set -e
					|printf '%s' "%RELEASE_RC_CONTENT%" | base64 -d > .releaserc.json
					""".trimMargin()
			dockerImage = "registry.example.com/org/semantic-release:25"
		}
		script {
			name = "semantic-release"
			scriptContent = """
					|#!/bin/bash
					|
					|export GH_TOKEN=%TOKEN%
					|DRY_RUN_FLAG=""
					|if [ %DRY_RUN% = true ]; then
					|  DRY_RUN_FLAG="--dry-run"
					|fi
					|
					|semantic-release ${'$'}DRY_RUN_FLAG
					""".trimMargin()
			dockerImage = "registry.example.com/org/semantic-release:25"
		}
	}
	triggers {
		vcs {
			triggerRules = """
				-:version.txt
				-:CHANGELOG.md
			""".trimIndent()
			clearBuildParams()
			buildParams {
				param("DRY_RUN", "false")
			}
			branchFilter = """
					|+:main
					|+:*.x
					""".trimMargin()
		}
	}
	dependencies {
		dependency(Templates_Service_Build) {
			snapshot {
				onDependencyFailure = FAIL_TO_START
				onDependencyCancel = FAIL_TO_START
				synchronizeRevisions = true
				reuseBuilds = NO
			}
		}
	}
	features {
		pullRequests {
			vcsRootExtId = "${Templates_Service_VCS.id}"
			provider = github {
				authType = vcsRoot()
			}
		}
	}
})
