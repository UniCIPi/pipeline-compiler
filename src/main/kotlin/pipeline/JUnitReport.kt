package fanci.compiler.pipeline

/**
 * A JUnit XML test report produced by a build configuration, to be published as a test report
 * on the target platform (TeamCity `xmlReport` feature / GitLab `artifacts.reports.junit`).
 *
 * [path] is a path or glob pattern, relative to the checkout directory, e.g. `_build/junit-report.xml`.
 */
data class JUnitReport(
    var path: String
)
