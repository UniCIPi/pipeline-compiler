@file:Suppress("RedundantVisibilityModifier")

package ArtifactPaths_OutputArtifacts.buildTypes

import ArtifactPaths_OutputArtifacts.vcsRoots.ArtifactPaths_OutputArtifacts_VCS
import jetbrains.buildServer.configs.kotlin.BuildType
import jetbrains.buildServer.configs.kotlin.BuildTypeSettings.Type.REGULAR
import jetbrains.buildServer.configs.kotlin.CheckoutMode.ON_AGENT
import jetbrains.buildServer.configs.kotlin.buildSteps.script

public object ArtifactPaths_OutputArtifacts_Publish : BuildType({
	name = "Publish"
	type = REGULAR
	artifactRules = """
			|+:out/nested/deep/proof.txt
			|+:out/nested/deep/proof.txt => 01-file
			|+:out/nested => 02-directory
			|+:out/nested/** => 03-directory-double-star
			|+:out/nested/deep/*.txt => 04-star-in-file-name
			|+:out/*/deep/proof.txt => 05-star-as-directory
			|+:out/nest*/deep/proof.txt => 06-star-in-directory-name
			|+:out/**/proof.txt => 07-double-star-in-middle
			|+:**/proof.txt => 08-double-star-leading
			|+:hidden => 09-hidden-directory
			|+:hidden/** => 10-hidden-double-star
			|+:hidden/*.txt => 11-hidden-star
			|+:hidden/.dot.txt => 12-hidden-explicit-file
			|+:hidden/.dotdir/* => 13-hidden-explicit-directory
			|+:hidden/.* => 14-hidden-dot-star
			|+:out/neste?/deep/proof.txt => 15-question-mark
			|+:empty-dir/* => 16-empty-dir-star
			|+:empty-dir => 17-empty-dir
			|+:pkg/* => packages
			""".trimMargin()
	vcs {
		root(ArtifactPaths_OutputArtifacts_VCS)
		cleanCheckout = true
		checkoutMode = ON_AGENT
		showDependenciesChanges = true
	}
	steps {
		script {
			name = "Create the files"
			scriptContent = """
					|set -eu
					|mkdir -p out/nested/deep
					|echo "proof" > out/nested/deep/proof.txt
					|mkdir -p .hidden-out
					|echo "proof" > .hidden-out/proof.txt
					|mkdir -p hidden/.dotdir
					|echo "visible" > hidden/visible.txt
					|echo "dot" > hidden/.dot.txt
					|echo "inside" > hidden/.dotdir/inside.txt
					|mkdir -p empty-dir
					|mkdir -p archive-content/inner/sub pkg
					|echo "a" > archive-content/inner/a.txt
					|echo "b" > archive-content/inner/sub/b.txt
					|echo "h" > archive-content/inner/.h.txt
					|tar -czf pkg/bundle.tgz -C archive-content inner
					|apk add --no-cache zip > /dev/null
					|(cd archive-content && zip -qr ../pkg/bundle.zip inner)
					|find out .hidden-out hidden pkg -type f | sort
					|find empty-dir -type d -empty
					|""".trimMargin()
			dockerImage = "docker.io/library/alpine:3.20"
		}
	}
	features {
	}
})
