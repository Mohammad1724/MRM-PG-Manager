package com.mrm.pgmanager.ui.dialogs

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class CompactGlassFieldTransformationTest {
    @Test fun `password keyboard types mask the displayed characters`() {
        val value = AnnotatedString("private-123")
        listOf(KeyboardType.Password, KeyboardType.NumberPassword).forEach { type ->
            val displayed = compactFieldVisualTransformation(type).filter(value).text.text
            assertEquals("•".repeat(value.length), displayed)
            assertEquals("private-123", value.text)
        }
    }

    @Test fun `ordinary input keeps its visible text`() {
        val value = AnnotatedString("example")
        assertEquals("example", compactFieldVisualTransformation(KeyboardType.Text).filter(value).text.text)
    }
}
