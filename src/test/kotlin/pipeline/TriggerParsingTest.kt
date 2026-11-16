package fanci.compiler.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFails
import kotlin.test.assertTrue

class TriggerParsingTest {
    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    @Test
    fun `parse vcs trigger with full structure`() {
        val yaml = """
            type: vcs
            watched-component: ::Project::MainRepo
            include-paths:
              - src/**
              - tests/**
            exclude-paths:
              - src/generated/**
            commit-message-regex: \[skip ci\]
            include-branches:
              - main
              - develop
              - feature/*
            parameters:
              - type: text
                name: buildType
                value: release
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger

        // Verify watched component and paths
        assertEquals("::Project::MainRepo", vcs.watchedComponent)
        assertEquals(listOf("src/**", "tests/**"), vcs.includePaths)
        assertEquals(listOf("src/generated/**"), vcs.excludePaths)
        assertEquals("\\[skip ci\\]", vcs.commitMessageRegex)

        // Verify branch filters via new schema
        assertEquals(listOf("main", "develop", "feature/*"), vcs.includeBranches)
        assertTrue(vcs.excludeBranches.isEmpty())

        // Verify parameters
        assertEquals(1, vcs.parameters.size)
        val param = vcs.parameters.first()
        assertIs<TextParameter>(param)
        assertEquals("buildType", param.name)
        assertEquals("release", param.value)
    }

    @Test
    fun `minimal vcs trigger watches all paths of the owning component`() {
        val yaml = """
            type: vcs
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger
        assertNull(vcs.watchedComponent)
        assertTrue(vcs.includePaths.isEmpty())
        assertTrue(vcs.excludePaths.isEmpty())
        assertNull(vcs.commitMessageRegex)
    }

    @Test
    fun `vcs trigger watching another component without include paths should succeed`() {
        val yaml = """
            type: vcs
            watched-component: ::Project::Docs
            exclude-paths:
              - "**/*.md"
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger
        assertEquals("::Project::Docs", vcs.watchedComponent)
        assertTrue(vcs.includePaths.isEmpty())
        assertEquals(listOf("**/*.md"), vcs.excludePaths)
    }

    @Test
    fun `vcs trigger with blank watched component should fail`() {
        val yaml = """
            type: vcs
            watched-component: " "
        """.trimIndent()

        val exception = assertFails {
            mapper.readValue<Trigger>(yaml)
        }
        assertTrue(exception.message?.contains("Watched component cannot be blank") ?: false)
    }

    @Test
    fun `vcs trigger with trigger rules should fail`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - include-paths:
                  - src/**
        """.trimIndent()

        assertFails { mapper.readValue<Trigger>(yaml) }
    }

    @Test
    fun `parse cron trigger with valid unix-style expression`() {
        val yaml = """
            type: schedule
            cron: "0 0 * * *"
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<ScheduleTrigger>(trigger)
        assertEquals("0 0 * * *", (trigger as ScheduleTrigger).cron)
    }

    @Test
    fun `parse schedule trigger with name`() {
        val yaml = """
            type: schedule
            name: nightly
            cron: "0 0 * * *"
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<ScheduleTrigger>(trigger)
        assertEquals("nightly", (trigger as ScheduleTrigger).name)
    }

    @Test
    fun `schedule trigger with blank name should fail`() {
        val yaml = """
            type: schedule
            name: " "
            cron: "0 0 * * *"
        """.trimIndent()

        val exception = assertFails {
            mapper.readValue<Trigger>(yaml)
        }
        assertTrue(exception.message?.contains("Schedule trigger name cannot be blank") ?: false)
    }

    @Test
    fun `cron trigger with invalid expression should fail`() {
        val yaml = """
            type: schedule
            cron: "invalid cron"
        """.trimIndent()

        val exception = assertFails { 
            mapper.readValue<Trigger>(yaml) 
        }
        assertTrue(exception.message?.contains("Invalid cron expression") ?: false)
    }

    @Test
    fun `cron trigger with blank expression should fail`() {
        val yaml = """
            type: schedule
            cron: ""
        """.trimIndent()

        val exception = assertFails { 
            mapper.readValue<Trigger>(yaml) 
        }
        assertTrue(exception.message?.contains("Cron expression cannot be blank") ?: false)
    }

    @Test
    fun `parse finish build trigger with parameters`() {
        val yaml = """
            type: finish-build
            build-configuration-name: ::Project::OtherConfiguration
            parameters:
              - type: text
                name: runMode
                value: auto
                display-type: HIDDEN
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<FinishBuildTrigger>(trigger)
        val fb = trigger as FinishBuildTrigger
        assertEquals("::Project::OtherConfiguration", fb.buildConfigurationName)
        // parameters nested
        assertEquals(1, fb.parameters.size)
        val p = fb.parameters.first()
        assertIs<TextParameter>(p)
        assertEquals("runMode", p.name)
        assertEquals("auto", p.value)
    }

    @Test
    fun `missing type should fail`() {
        val yaml = """
            branch: main
        """.trimIndent()

        assertFails { mapper.readValue<Trigger>(yaml) }
    }
}
