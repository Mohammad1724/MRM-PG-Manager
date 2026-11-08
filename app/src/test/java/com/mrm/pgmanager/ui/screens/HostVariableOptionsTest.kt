package com.mrm.pgmanager.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostVariableOptionsTest {
    private val variables = listOf(
        HostVariableOption("USERNAME", "User name"),
        HostVariableOption("EXPIRE_DATE", "Expiry date"),
        HostVariableOption("ADMIN_USERNAME", "Admin user name")
    )

    @Test fun `search matches token or localized description without changing values`() {
        assertEquals(variables, filterHostVariables(variables, "  "))
        assertEquals(listOf(variables[1]), filterHostVariables(variables, "expire_date"))
        assertEquals(listOf(variables[1]), filterHostVariables(variables, "{EXPIRE_DATE}"))
        assertEquals(listOf(variables[1]), filterHostVariables(variables, "  EXPIRY  "))
        assertEquals(listOf(variables[0], variables[2]), filterHostVariables(variables, "user name"))
        assertTrue(filterHostVariables(variables, "missing").isEmpty())
        assertEquals("{ADMIN_USERNAME}", variables[2].token)
    }

    @Test fun `column split is balanced and keeps every variable in order`() {
        val thirteen = (1..13).map { HostVariableOption("KEY_$it", "Value $it") }
        val columns = hostVariableColumns(thirteen, twoColumns = true)
        assertEquals(listOf(7, 6), columns.map { it.size })
        assertEquals(thirteen, columns.flatten())
        assertEquals(listOf(thirteen), hostVariableColumns(thirteen, twoColumns = false))
        assertEquals(listOf(variables.take(1)), hostVariableColumns(variables.take(1), twoColumns = true))
        assertEquals(listOf(emptyList<HostVariableOption>()), hostVariableColumns(emptyList(), twoColumns = true))
    }

    @Test fun `compact columns adapt to width and text size`() {
        assertFalse(useTwoHostVariableColumns(widthDp = 259f, fontScale = 1f))
        assertTrue(useTwoHostVariableColumns(widthDp = 260f, fontScale = 1.3f))
        assertFalse(useTwoHostVariableColumns(widthDp = 360f, fontScale = 1.31f))
        assertTrue(useTwoHostVariableColumns(widthDp = 360f, fontScale = 1f))
    }
}
