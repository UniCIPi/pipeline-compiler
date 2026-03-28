package com.bringauto.pipelinecompiler.configurationModel.buildTypeDefinitions

/**
 * Builds and Packages Go code using the specified Go version.
 *
 * @see com.bringauto.pipelinecompiler.pipeline.buildConfiguration.GoPackage
 */
data class GoPackage(
    val goVersion: String = "1.24",

    val systemList: List<GoSystem> = listOf(GoSystem.linux, GoSystem.windows),

    val archList: List<GoArch> = listOf(GoArch.amd64, GoArch.arm64, GoArch.arm)
) : IBuildTypeDefinition {
    init {
        require(systemList.isNotEmpty()) { "systemList must not be empty" }
        require(archList.isNotEmpty()) { "archList must not be empty" }
    }
}


/**
 * All supported operating systems for Go
 *
 * IMPORTANT: Not all architectures/system combinations are supported.
 * See https://go.dev/doc/install/source#environment for details.
 */
enum class GoSystem {
    linux,
    windows,
    aix,
    android,
    darwin,
    dragonfly,
    freebsd,
    illumos,
    ios,
    js,
    netbsd,
    openbsd,
    plan9,
    solaris
}

/**
 * All supported architectures for Go
 *
 * IMPORTANT: Not all architectures/system combinations are supported.
 * See https://go.dev/doc/install/source#environment for details.
 */
enum class GoArch {
    amd64,
    arm64,
    arm,
    mips,
    mips64,
    mips64le,
    mipsle,
    ppc64,
    ppc64le,
    riscv64,
    s390x,
    wasm
}

