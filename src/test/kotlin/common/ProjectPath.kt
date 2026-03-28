package common

import com.bringauto.pipelinecompiler.common.ProjectPath
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ProjectPathTest {

    @Test
    fun `test parse with valid input`() {
        val projectPath = ProjectPath.parse("::segment1::segment2")
        assertEquals(listOf("segment1", "segment2"), projectPath.segments)
    }

    @Test
    fun `test parse with invalid input missing delimiter`() {
        val exception = assertThrows<IllegalArgumentException> {
            ProjectPath.parse("segment1::segment2")
        }
        assertEquals("ProjectPath must start with '::' (was 'segment1::segment2')", exception.message)
    }

    @Test
    fun `test of with multiple parts`() {
        val projectPath = ProjectPath.of("segment1", "segment2")
        assertEquals(listOf("segment1", "segment2"), projectPath.segments)
    }

    @Test
    fun `test of with invalid blank part`() {
        val exception = assertThrows<IllegalArgumentException> {
            ProjectPath.of("segment1", " ")
        }
        assertEquals("Segment must not be blank", exception.message)
    }

    @Test
    fun `test toString with non-empty segments`() {
        val projectPath = ProjectPath.of("segment1", "segment2")
        assertEquals("::segment1::segment2", projectPath.toString())
    }

    @Test
    fun `test toString with empty segments`() {
        val projectPath = ProjectPath.of()
        assertEquals("::", projectPath.toString())
    }

    @Test
    fun `test withoutLast with multiple segments`() {
        val projectPath = ProjectPath.of("segment1", "segment2", "segment3")
        assertEquals(ProjectPath.of("segment1", "segment2"), projectPath.withoutLast())
    }

    @Test
    fun `test withoutLast with single segment`() {
        val projectPath = ProjectPath.of("segment1")
        assertEquals(ProjectPath.of(), projectPath.withoutLast())
    }

    @Test
    fun `test withoutLast with empty segments`() {
        val projectPath = ProjectPath.of()
        assertEquals(ProjectPath.of(), projectPath.withoutLast())
    }

    @Test
    fun `test last for valid path`() {
        val projectPath = ProjectPath.of("segment1", "segment2", "segment3")
        assertEquals("segment3", projectPath.last())
    }

    @Test
    fun `test last for empty path`() {
        val projectPath = ProjectPath.of()
        val exception = assertThrows<IllegalArgumentException> {
            projectPath.last()
        }
        assertEquals("Root path '::' has no last segment.", exception.message)
    }

    @Test
    fun `test append valid segment`() {
        val projectPath = ProjectPath.of("segment1", "segment2")
        val updatedProjectPath = projectPath.append("segment3")
        assertEquals(ProjectPath.of("segment1", "segment2", "segment3"), updatedProjectPath)
    }

    @Test
    fun `test append invalid blank segment`() {
        val projectPath = ProjectPath.of("segment1")
        val exception = assertThrows<IllegalArgumentException> {
            projectPath.append(" ")
        }
        assertEquals("Segment must not be blank", exception.message)
    }

    @Test
    fun `test append invalid segment with delimiter`() {
        val projectPath = ProjectPath.of("segment1")
        val exception = assertThrows<IllegalArgumentException> {
            projectPath.append("segment::3")
        }
        assertEquals("Segment must not contain '::': 'segment::3'", exception.message)
    }

    @Test
    fun `test startsWith true case`() {
        val projectPath = ProjectPath.of("segment1", "segment2", "segment3")
        val otherPath = ProjectPath.of("segment1", "segment2")
        assertTrue(projectPath.startsWith(otherPath))
    }

    @Test
    fun `test startsWith false case`() {
        val projectPath = ProjectPath.of("segment1", "segment2", "segment3")
        val otherPath = ProjectPath.of("segment2", "segment3")
        assertFalse(projectPath.startsWith(otherPath))
    }

    @Test
    fun `test startsWith empty other path`() {
        val projectPath = ProjectPath.of("segment1", "segment2", "segment3")
        val otherPath = ProjectPath.of()
        assertTrue(projectPath.startsWith(otherPath))
    }

    @Test
    fun `test fromDirectoryHierarchy with valid input`() {
        val rootFile = File("/root")
        val yamlFile = File("/root/subdir/file.yaml")
        val projectPath = ProjectPath.fromDirectoryHierarchy(rootFile, yamlFile)
        assertEquals(ProjectPath.of("subdir"), projectPath)
    }

    @Test
    fun `test canonicalize with mixed input`() {
        val result = ProjectPath.canonicalize("::Segment1::SEGMENT2::segment3")
        assertEquals("::segment1::segment2::segment3", result)
    }

    @Test
    fun `test isFullPath valid case`() {
        assertTrue(ProjectPath.isFullPath("::segment1::segment2"))
    }

    @Test
    fun `test isFullPath invalid case`() {
        assertFalse(ProjectPath.isFullPath("segment1"))
    }

    @Test
    fun `test equalsCanonical true case`() {
        assertTrue(ProjectPath.equalsCanonical("::segment1::segment2", "::Segment1::SEGMENT2"))
    }

    @Test
    fun `test equalsCanonical false case`() {
        assertFalse(ProjectPath.equalsCanonical("::segment1::segment3", "::segment1::segment2"))
    }
}