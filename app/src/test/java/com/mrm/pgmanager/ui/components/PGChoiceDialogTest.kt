package com.mrm.pgmanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PGChoiceDialogTest {
    private val choices = listOf(
        PGChoiceOption("", "No selection"),
        PGChoiceOption("alpha", "Alpha inbound"),
        PGChoiceOption("future", "Unknown (future)"),
        PGChoiceOption("alpha", "Duplicate alpha")
    )

    @Test fun `search trims whitespace and ignores case without altering values`() {
        assertEquals(listOf(PGChoiceOption("alpha", "Alpha inbound")), filterChoiceOptions(choices, "  ALPHA  "))
        assertEquals(listOf(PGChoiceOption("future", "Unknown (future)")), filterChoiceOptions(choices, "future"))
    }

    @Test fun `empty search keeps the first choice for every stable value`() {
        assertEquals(choices.take(3), filterChoiceOptions(choices, ""))
        assertEquals(emptyList<PGChoiceOption>(), filterChoiceOptions(choices, "missing"))
    }
}
