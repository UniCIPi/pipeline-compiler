package common

import fanci.compiler.common.VCSHelper
import kotlin.test.Test
import kotlin.test.assertEquals

class GitLabApiParseTest {

    @Test
    fun `parse https url`() {
        val p = VCSHelper.parseApi("https://gitlab.example.com/group/sub/project.git")
        assertEquals("https://gitlab.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/sub/project", p.targetProject)
    }

    @Test
    fun `parse http url without git suffix`() {
        val p = VCSHelper.parseApi("http://gitlab.example.com/group/project")
        assertEquals("http://gitlab.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/project", p.targetProject)
    }

    @Test
    fun `parse scp like ssh url`() {
        val p = VCSHelper.parseApi("git@gitlab.example.com:group/sub/project.git")
        assertEquals("https://gitlab.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/sub/project", p.targetProject)
    }

    @Test
    fun `parse ssh scheme url with port`() {
        val p = VCSHelper.parseApi("ssh://git@gitlab.example.com:1999/group/sub/project.git")
        assertEquals("https://gitlab.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/sub/project", p.targetProject)
    }

    @Test
    fun `parse git+ssh scheme url`() {
        val p = VCSHelper.parseApi("git+ssh://git@gitlab.example.com/group/project.git")
        assertEquals("https://gitlab.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/project", p.targetProject)
    }

    @Test
    fun `parse ssh url with underscore host and port`() {
        val p = VCSHelper.parseApi("ssh://git@gitlab._internal.example.com:1999/group/subgroup/project.git")
        assertEquals("https://gitlab._internal.example.com/api/v4", p.apiBaseUrl)
        assertEquals("group/subgroup/project", p.targetProject)
    }
}