package com.bringauto.pipelinecompiler.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ParameterParsingTest {

    private val mapper: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerKotlinModule()
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    @Test
    fun `parse text parameter yaml into TextParameter`() {
        val yaml = """
            type: text
            name: message
            value: hello
            display-type: PROMPT
            label: Greeting
            description: Enter greeting
        """.trimIndent()

        val param: Parameter = mapper.readValue(yaml)
        assertIs<TextParameter>(param)
        assertEquals("message", param.name)
        assertEquals("hello", param.value)
        // Additional fields
        assertEquals(DisplayType.PROMPT, (param as TextParameter).displayType)
        assertEquals("Greeting", param.label)
        assertEquals("Enter greeting", param.description)
    }

    @Test
    fun `parse select parameter yaml into SelectParameter`() {
        val yaml = """
            type: select
            name: color
            value: blue
            display-type: NORMAL
            label: Color
            description: Pick a color
            options:
              - red
              - green
              - blue
            allow-multiple: true
        """.trimIndent()

        val param: Parameter = mapper.readValue(yaml)
        assertIs<SelectParameter>(param)
        assertEquals("color", param.name)
        assertEquals("blue", param.value)
        val sp = param as SelectParameter
        assertEquals(DisplayType.NORMAL, sp.displayType)
        assertEquals("Color", sp.label)
        assertEquals("Pick a color", sp.description)
        assertEquals(listOf("red", "green", "blue"), sp.options)
        assertEquals(true, sp.allowMultiple)
    }

    @Test
    fun `parse yaml without type into base Parameter`() {
        val yaml = """
            name: simple
            value: 42
        """.trimIndent()

        val param: Parameter = mapper.readValue(yaml)
        // defaultImpl should be Parameter
        assertIs<Parameter>(param)
        // but not any subclass
        // Kotlin's assertIs can't assert negative; check class equality
        assertEquals(Parameter::class, param::class)
        assertEquals("simple", param.name)
        assertEquals("42", param.value)
    }
}