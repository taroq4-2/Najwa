package org.najwa.app

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import org.najwa.app.ui.NajwaApp
import org.najwa.app.ui.InterfaceLanguage
import java.util.Locale

class MainActivity: ComponentActivity() {
    private val resumed=mutableIntStateOf(0)
    private val homeRequest=mutableIntStateOf(0)
    override fun attachBaseContext(base: Context) {
        val locale=Locale.forLanguageTag(appLanguage(base))
        val config=Configuration(base.resources.configuration).apply {setLocale(locale)}
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { InterfaceLanguage {NajwaApp((application as NajwaApplication).runtime,resumed.intValue,homeRequest.intValue)} } }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("open_home",false))homeRequest.intValue++}
    override fun onResume() {
        super.onResume()
        val app=application as NajwaApplication
        prayerExecutor.execute {
            app.runtime.reconcile()
            org.najwa.app.platform.PrayerWidget.updateAll(app)
            runOnUiThread { if(!isDestroyed)resumed.intValue++ }
        }
    }
}

internal val supportedLanguages = linkedMapOf(
    "ar" to "العربية", "en" to "English", "id" to "Bahasa Indonesia",
    "ur" to "اردو", "bn" to "বাংলা", "hi" to "हिन्दी", "tr" to "Türkçe"
)

internal fun appLanguage(context: Context): String =
    context.getSharedPreferences("interface-language",Context.MODE_PRIVATE).getString("language","ar")
        ?.takeIf {it in supportedLanguages} ?: "ar"

internal fun changeAppLanguage(activity: MainActivity,language: String) {
    require(language in supportedLanguages)
    activity.getSharedPreferences("interface-language",Context.MODE_PRIVATE).edit().putString("language",language).apply()
    val app=activity.application as NajwaApplication
    prayerExecutor.execute { app.runtime.notifications.updateChannels() }
    activity.recreate()
}
