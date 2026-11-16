@file:Suppress("RedundantVisibilityModifier")

package ArtifactPaths_OutputArtifacts.buildTypes

import ArtifactPaths_OutputArtifacts.vcsRoots.ArtifactPaths_OutputArtifacts_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object ArtifactPaths_OutputArtifacts_MissingArtifacts : BuildType({
	name = "MissingArtifacts"
	type = REGULAR
	vcs {
		root(ArtifactPaths_OutputArtifacts_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "Print the downloaded files"
			scriptContent = """
					|set -eu
					|if [ -d none ]; then find none | sort; else echo "nothing downloaded"; fi
					|""".trimMargin()
			dockerImage = "docker.io/library/alpine:3.20"
		}
	}
	dependencies {
		dependency(ArtifactPaths_OutputArtifacts_Publish) {
			snapshot {
				onDependencyFailure = FAIL_TO_START
				onDependencyCancel = FAIL_TO_START
				synchronizeRevisions = true
				reuseBuilds = NO
			}
			artifacts {
				artifactRules = """
						|+:05-star-as-directory/nested/deep/missing.txt => none/01-file
						|+:missing/*.txt => none/02-star
						|+:packages/missing.tgz!inner/** => none/03-archive
						""".trimMargin()
			}
		}
	}
	features {
	}
})
