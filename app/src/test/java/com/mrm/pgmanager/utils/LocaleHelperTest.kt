package com.mrm.pgmanager.utils

import android.content.res.Configuration
import android.view.View
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocaleHelperTest {
    @Test fun explicitLanguagePreservesFontScaleAndWindowConfiguration() {
        val configuration = Configuration().apply {
            fontScale = 1.7f
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 320
            densityDpi = 420
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }

        LocaleHelper.applyLanguageToOverride(configuration, "fa")

        assertEquals("fa", configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, configuration.layoutDirection)
        assertEquals(1.7f, configuration.fontScale, 0.001f)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, configuration.orientation)
        assertEquals(320, configuration.screenWidthDp)
        assertEquals(420, configuration.densityDpi)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, configuration.uiMode)
    }

    @Test fun systemLanguageDoesNotReplaceTheSuppliedConfiguration() {
        val configuration = Configuration().apply {
            fontScale = 1.5f
            setLocale(Locale("ar"))
        }
        LocaleHelper.applyLanguageToOverride(configuration, "system")
        assertEquals("ar", configuration.locales[0].language)
        assertEquals(1.5f, configuration.fontScale, 0.001f)
    }

    @Test fun languageDirectionFollowsSelection() {
        assertTrue(LocaleHelper.isRtl("fa", Locale.ENGLISH))
        assertFalse(LocaleHelper.isRtl("en", Locale("fa")))
        assertTrue(LocaleHelper.isRtl("system", Locale("fa")))
        assertFalse(LocaleHelper.isRtl("system", Locale.ENGLISH))
    }
}
