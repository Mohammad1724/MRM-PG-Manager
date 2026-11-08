package com.mrm.pgmanager.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRefreshSecurityTest {
    @Test fun `widget lifecycle is exported but the manual refresh receiver is private`() {
        val context = RuntimeEnvironment.getApplication()
        val pm = context.packageManager
        val provider = pm.getReceiverInfo(ComponentName(context, PanelWidgetProvider::class.java), 0)
        val refresh = pm.getReceiverInfo(ComponentName(context, WidgetRefreshReceiver::class.java), 0)
        assertTrue(provider.exported)
        assertFalse(refresh.exported)
    }

    @Test fun `widget refresh intent targets the private receiver`() {
        val context = RuntimeEnvironment.getApplication()
        val intent = PanelWidgetProvider.refreshIntent(context, 42)
        assertEquals(ComponentName(context, WidgetRefreshReceiver::class.java), intent.component)
        assertEquals(PanelWidgetProvider.ACTION_REFRESH, intent.action)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }
}
