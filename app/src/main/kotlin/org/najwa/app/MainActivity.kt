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
    override fun onResume() { super.onResume(); (application as NajwaApplication).runtime.reconcile(); org.najwa.app.platform.PrayerWidget.updateAll(this); resumed.intValue++ }
}

internal fun appLanguage(context: Context): String =
    context.getSharedPreferences("interface-language",Context.MODE_PRIVATE).getString("language","ar") ?: "ar"

internal fun changeAppLanguage(activity: MainActivity,language: String) {
    activity.getSharedPreferences("interface-language",Context.MODE_PRIVATE).edit().putString("language",language).apply()
    activity.recreate()
}
