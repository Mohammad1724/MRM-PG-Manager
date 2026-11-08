package com.mrm.pgmanager.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.mrm.pgmanager.R
import com.mrm.pgmanager.data.storage.SessionStore
import com.mrm.pgmanager.ui.designsystem.MotionPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class BackupPasswordFieldTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var store: SessionStore

    @Before fun setUp() {
        MotionPrefs.reduceMotion = true
        SessionStore.resetPrefsForTests()
        SessionStore.prefsFactory = { it.getSharedPreferences("backup_password_field_test", Context.MODE_PRIVATE) }
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("backup_password_field_test", Context.MODE_PRIVATE).edit().clear().commit()
        store = SessionStore(context)
    }

    @After fun tearDown() {
        SessionStore.resetPrefsForTests()
        MotionPrefs.reduceMotion = false
    }

    @Test fun `long backup password is masked and stored exactly as entered`() {
        val password = "a".repeat(70) + "-suffix"
        rule.setContent {
            val scope = rememberCoroutineScope()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                BackupSection(store = store, scope = scope, appVersion = "test", onRequestRestore = {})
            }
        }
        val label = RuntimeEnvironment.getApplication().getString(R.string.set_bk_password)
        val field = rule.onNodeWithContentDescription(label).performScrollTo()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        field.performTextInput(password)
        val displayed = field.fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        rule.runOnIdle {
            assertEquals(password, store.readBackupPassword())
            assertEquals("•".repeat(password.length), displayed)
        }
    }
}
