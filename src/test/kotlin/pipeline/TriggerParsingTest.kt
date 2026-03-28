package com.bringauto.pipelinecompiler.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
            trigger-rules:
              - vcs-root-component: MainRepo
                include-paths:
                  - src/**
                  - tests/**
                exclude-paths:
                  - src/generated/**
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
        
        // Verify trigger rules
        assertEquals(1, vcs.triggerRules.size)
        val rule = vcs.triggerRules.first()
        assertEquals("MainRepo", rule.vcsRootComponent)
        assertEquals(2, rule.includePaths.size)
        assertEquals("src/**", rule.includePaths[0])
        assertEquals("tests/**", rule.includePaths[1])
        assertEquals(1, rule.excludePaths.size)
        assertEquals("src/generated/**", rule.excludePaths[0])
        
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
    fun `parse vcs trigger with multiple trigger rules`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - vcs-root-component: Frontend
                include-paths:
                  - frontend/**
              - vcs-root-component: Backend
                include-paths:
                  - backend/**
              - vcs-root-component: Docs
                exclude-paths:
                  - docs/internal/**
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger
        
        assertEquals(3, vcs.triggerRules.size)
        assertEquals("Frontend", vcs.triggerRules[0].vcsRootComponent)
        assertEquals(listOf("frontend/**"), vcs.triggerRules[0].includePaths)
        assertEquals("Backend", vcs.triggerRules[1].vcsRootComponent)
        assertEquals(listOf("backend/**"), vcs.triggerRules[1].includePaths)
        assertEquals("Docs", vcs.triggerRules[2].vcsRootComponent)
        assertEquals(listOf("docs/internal/**"), vcs.triggerRules[2].excludePaths)
    }

    @Test
    fun `vcs trigger rule with only include paths should succeed`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - vcs-root-component: Repo
                include-paths:
                  - src/**
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger
        assertEquals(1, vcs.triggerRules.size)
        assertEquals(1, vcs.triggerRules[0].includePaths.size)
        assertTrue(vcs.triggerRules[0].excludePaths.isEmpty())
    }

    @Test
    fun `vcs trigger rule with only exclude paths should succeed`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - vcs-root-component: Repo
                exclude-paths:
                  - generated/**
        """.trimIndent()

        val trigger: Trigger = mapper.readValue(yaml)
        assertIs<VcsTrigger>(trigger)
        val vcs = trigger as VcsTrigger
        assertEquals(1, vcs.triggerRules.size)
        assertTrue(vcs.triggerRules[0].includePaths.isEmpty())
        assertEquals(1, vcs.triggerRules[0].excludePaths.size)
    }

    @Test
    fun `vcs trigger rule without paths should fail`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - vcs-root-component: Repo
        """.trimIndent()

        val exception = assertFails { 
            mapper.readValue<Trigger>(yaml) 
        }
        assertTrue(exception.message?.contains("TriggerRule must define at least one path") ?: false)
    }

    @Test
    fun `vcs trigger rule with empty path lists should fail`() {
        val yaml = """
            type: vcs
            trigger-rules:
              - vcs-root-component: Repo
                include-paths: []
                exclude-paths: []
        """.trimIndent()

        val exception = assertFails { 
            mapper.readValue<Trigger>(yaml) 
        }
        assertTrue(exception.message?.contains("TriggerRule must define at least one path") ?: false)
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
