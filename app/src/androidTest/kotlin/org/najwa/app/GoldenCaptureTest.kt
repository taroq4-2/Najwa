package org.najwa.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class) class GoldenCaptureTest {
    @Test fun captureBothThemesAtDefinedViewport() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        for(light in listOf(false,true)) {
            ActivityScenario.launch<GoldenActivity>(Intent(context,GoldenActivity::class.java).putExtra("light",light)).use { scenario ->
                instrumentation.waitForIdleSync()
                val done=CountDownLatch(1);var result=-1
                var image: Bitmap?=null
                scenario.onActivity { activity ->
                    val r=activity.contentRect ?: throw AssertionError("No layout")
                    assertEquals("Use density 2 and a 390dp wide device",780,r.width())
                    assertEquals("Capture must be 844dp high",1688,r.height())
                    assertTrue("Device content must fit without hiding system bars",r.bottom<=activity.window.decorView.height)
                    image=Bitmap.createBitmap(r.width(),r.height(),Bitmap.Config.ARGB_8888)
                    PixelCopy.request(activity.window,r,image!!,{ result=it;done.countDown() },Handler(Looper.getMainLooper()))
                }
                assertTrue(done.await(10,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result)
                val file=File(context.filesDir,if(light)"home-light.png" else "home-dark.png")
                file.outputStream().use {image!!.compress(Bitmap.CompressFormat.PNG,100,it)}
                image!!.recycle()
            }
        }
    }
}
