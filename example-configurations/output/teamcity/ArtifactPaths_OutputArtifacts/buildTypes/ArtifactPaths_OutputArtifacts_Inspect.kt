@file:Suppress("RedundantVisibilityModifier")

package ArtifactPaths_OutputArtifacts.buildTypes

import ArtifactPaths_OutputArtifacts.vcsRoots.ArtifactPaths_OutputArtifacts_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.FailureAction.FAIL_TO_START
import jetbrains.buildServer.configs.kotlin.ReuseBuilds.NO
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object ArtifactPaths_OutputArtifacts_Inspect : BuildType({
	name = "Inspect"
	type = REGULAR
	vcs {
		root(ArtifactPaths_OutputArtifacts_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "Print the artifact trees"
			scriptContent = """
					|set -eu
					|find archive downloaded hidden received -type f | sort
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
						|+:** => received
						|+:05-star-as-directory/nested/deep/proof.txt => downloaded/01-file
						|+:05-star-as-directory/nested => downloaded/02-directory
						|+:05-star-as-directory/nested/** => downloaded/03-directory-double-star
						|+:05-star-as-directory/nested/deep/*.txt => downloaded/04-star-in-file-name
						|+:05-star-as-directory/*/deep/proof.txt => downloaded/05-star-as-directory
						|+:05-star-as-directory/nest*/deep/proof.txt => downloaded/06-star-in-directory-name
						|+:05-star-as-directory/**/proof.txt => downloaded/07-double-star-in-middle
						|+:05-*/nested/deep/proof.txt => downloaded/08-star-leading
						|+:12-hidden-explicit-file/.dot.txt => hidden/01-explicit-file
						|+:12-hidden-explicit-file/* => hidden/02-star
						|+:13-hidden-explicit-directory => hidden/03-explicit-directory
						|+:14-hidden-dot-star/.* => hidden/04-dot-star
						|+:packages/bundle.tgz!inner/** => archive/01-tgz-double-star
						|+:packages/bundle.zip!inner/** => archive/02-zip-double-star
						|+:packages/bundle.*!inner/*.txt => archive/03-wildcard-package
						|+:packages/bundle.tgz!inner/.h.txt => archive/04-hidden-explicit
						|+:packages/bundle.tgz!inner/sub => archive/05-directory
						""".trimMargin()
			}
		}
	}
	features {
	}
})
