package org.najwa.app

import android.content.ContextWrapper
import android.content.Intent
import android.content.ActivityNotFoundException
import android.provider.Settings
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.najwa.app.platform.*

class SystemActionsTest {
    @get:Rule val paparazzi=Paparazzi(theme="android:Theme.Material.Light.NoActionBar")
    @Test fun missingVendorPermissionActivityFallsBackToAppDetails() {
        val actions=mutableListOf<Intent>()
        val context=object: ContextWrapper(paparazzi.context) {
            override fun startActivity(intent: Intent) {
                actions+=intent
                if(actions.size==1)throw ActivityNotFoundException()
            }
        }
        assertTrue(openSystemSettings(context,Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
        assertEquals(2,actions.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,actions.last().action)
        assertTrue(actions.last().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
    @Test fun settingsSecurityFailureAndMissingEmailAppNeverEscapeToUi() {
        val context=object: ContextWrapper(paparazzi.context) {
            override fun startActivity(intent: Intent) {throw SecurityException("blocked by device")}
        }
        assertFalse(openSystemSettings(context,Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)))
        assertFalse(launchExternal(context,contactEmailIntent("اقتراح","هاتف","تفاصيل")))
    }
    @Test fun emailOnlyOpensComposerForTheRequestedAddress() {
        val intent=contactEmailIntent("اقتراح","هاتف","تفاصيل")
        assertEquals(Intent.ACTION_SENDTO,intent.action)
        assertTrue(intent.data.toString().startsWith("mailto:contact@os73.com?"))
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("تفاصيل"))
    }
}
