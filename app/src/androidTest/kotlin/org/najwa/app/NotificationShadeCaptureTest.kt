package org.najwa.app

import android.Manifest
import android.app.NotificationManager
import android.graphics.Bitmap
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.core.*
import java.io.File
import java.time.Instant

/** Captures the actual SystemUI notification shade, not just the app's RemoteViews. */
@RunWith(AndroidJUnit4::class)
class NotificationShadeCaptureTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use {it.readBytes()}
    }
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if(node==null)return emptyList()
        return listOf(node)+(0 until node.childCount).flatMap {nodes(node.getChild(it))}
    }
    private fun capture(name: String) {
        SystemClock.sleep(700)
        val expectedTitles=setOf(context.prayerLanguage().getString(R.string.system_adhan_test),
            context.prayerLanguage().getString(R.string.system_iqama_test))
        val title=nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {it.text?.toString() in expectedTitles}
        var root: AccessibilityNodeInfo?=null
        var ancestor=title?.parent
        val maxHeight=(context.resources.displayMetrics.density*252).toInt()
        repeat(6) {
            val node=ancestor ?: return@repeat
            val rectangle=android.graphics.Rect().also {node.getBoundsInScreen(it)}
            // Only the app's custom LinearLayout; never save a whole SystemUI row or panel.
            if(node.className?.toString()=="android.widget.LinearLayout" && rectangle.height()<=maxHeight &&
                nodes(node).count {it.text?.toString() in expectedTitles}==1)root=node
            ancestor=node.parent
        }
        assertNotNull("Najwa notification card must be visible before capture",root)
        val bounds=android.graphics.Rect()
        root!!.getBoundsInScreen(bounds)
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("SystemUI screenshot",bitmap)
        assertTrue("Only save the Najwa card",bounds.width()>0 && bounds.height()>0 &&
            bounds.left>=0 && bounds.top>=0 && bounds.right<=bitmap!!.width && bounds.bottom<=bitmap.height)
        val card=Bitmap.createBitmap(bitmap!!,bounds.left,bounds.top,bounds.width(),bounds.height())
        try {
            File(context.filesDir,"$name.png").outputStream().use {card.compress(Bitmap.CompressFormat.PNG,100,it)}
            // Export no other notification text or window hierarchy.
            File(context.filesDir,"$name.txt").writeText("Najwa card only: ${bounds.width()}x${bounds.height()}")
        } finally {card.recycle();bitmap.recycle()}
    }
    @Test fun captureRealShadeForAdhanAndIqama() {
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        instrumentation.uiAutomation.serviceInfo=info
        if(Build.VERSION.SDK_INT>=33)shell("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        val languagePrefs=context.getSharedPreferences("interface-language",0)
        val originalLanguage=languagePrefs.getString("language",null)
        languagePrefs.edit().putString("language","ar").commit()
        val manager=context.getSystemService(NotificationManager::class.java)
        val notifications=PrayerNotifications(context)
        val event=PrayerEvent(Prayer.DHUHR,Instant.now())
        try {
            for(action in listOf(PrayerAction.ADHAN,PrayerAction.IQAMA)) {
                manager.cancel(499)
                val result=notifications.show(ScheduledPrayer(event,action,event.time),Preferences(),test=true)
                assertEquals(NotificationDelivery.POSTED,result)
                shell("cmd statusbar expand-notifications")
                SystemClock.sleep(1200)
                val phase=if(action==PrayerAction.ADHAN)"adhan" else "iqama"
                capture("shade-$phase-initial")
                // Use SystemUI's own affordance; never force expansion with notification flags.
                val toggle=nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull {
                    it.isClickable && (it.viewIdResourceName?.endsWith("/expand_button")==true ||
                        it.contentDescription?.toString()?.lowercase() in listOf("expand","collapse"))
                }
                assertNotNull("System expansion control",toggle)
                if(toggle!=null) {
                    assertTrue(toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    capture("shade-$phase-toggled")
                }
            }
        } finally {
            languagePrefs.edit().apply {if(originalLanguage==null)remove("language") else putString("language",originalLanguage)}.commit()
            manager.cancel(499)
            shell("cmd statusbar collapse")
        }
    }
}
