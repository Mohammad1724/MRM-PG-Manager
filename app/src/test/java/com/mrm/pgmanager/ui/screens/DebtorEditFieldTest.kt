package com.mrm.pgmanager.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.model.PanelUser
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class DebtorEditFieldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `labelled amount and note fields still save their values`() {
        val user = PanelUser(id = 3L, username = "alpha", status = "active", usedTraffic = 0L, dataLimit = 0L, expire = null, createdAt = null)
        var saved: Pair<Long, String>? = null
        rule.setContent {
            DebtorEditDialog(user = user, existing = null, currency = "USD", onDismiss = {},
                onSave = { amount, notes -> saved = amount to notes }, onClear = {})
        }
        val context = RuntimeEnvironment.getApplication()
        rule.onNodeWithContentDescription(context.getString(R.string.us_debt_amount_hint)).performTextInput("12345")
        rule.onNodeWithContentDescription(context.getString(R.string.us_debt_note_hint)).performTextInput("Invoice")
        rule.onNodeWithText(context.getString(R.string.us_debt_mark)).performClick()
        rule.runOnIdle { assertEquals(12345L to "Invoice", saved) }
    }
}
