package org.najwa.app

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.najwa.app.platform.ACTION_CHOOSE_CITY
import org.najwa.app.platform.ACTION_QIBLA
import org.najwa.app.platform.ACTION_SILENCE_NOW
import org.najwa.app.platform.ACTION_ADHKAR
import org.najwa.app.platform.ACTION_HEALTH
import org.najwa.app.platform.ACTION_PRAYER_LOG
import org.najwa.app.platform.ACTION_TASBEEH
import org.najwa.app.platform.ACTION_QURAN
import org.najwa.app.platform.prayerLanguage
import org.najwa.app.ui.NajwaApp
import org.najwa.app.ui.InterfaceLanguage
import java.util.Locale

class MainActivity: ComponentActivity() {
    private val resumed=mutableIntStateOf(0)
    private val homeRequest=mutableIntStateOf(0)
    private val action=mutableStateOf<String?>(null)
    private val actionData=mutableStateOf<String?>(null)
    private val actionRequest=mutableIntStateOf(0)
    override fun attachBaseContext(base: Context) {
        val locale=Locale.forLanguageTag(appLanguage(base))
        val config=Configuration(base.resources.configuration).apply {setLocale(locale)}
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if(savedInstanceState==null)handle(intent)
        setContent { InterfaceLanguage {NajwaApp((application as NajwaApplication).runtime,resumed.intValue,homeRequest.intValue,action.value,actionRequest.intValue,actionData.value)} }
        prayerExecutor.execute {publishShortcuts(applicationContext)}
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent);setIntent(intent)
        if(intent.getBooleanExtra("open_home",false))homeRequest.intValue++
        handle(intent)
    }
    private fun handle(intent: Intent?) {
        intent ?: return
        // A shared mosque profile (najwa://mosque#code) opens the mosque screen with an import preview.
        if(intent.action==Intent.ACTION_VIEW && intent.data?.scheme=="najwa" && intent.data?.host=="mosque") {
            action.value=org.najwa.app.ui.ACTION_MOSQUE;actionData.value=intent.dataString;actionRequest.intValue++;return
        }
        // A card's own button opened the app: close the card.
        intent.getIntExtra("cancel_notification",-1).takeIf {it>=0}?.let {getSystemService(android.app.NotificationManager::class.java).cancel(it)}
        val requested=intent.action?.takeIf {it in setOf(ACTION_SILENCE_NOW,ACTION_QIBLA,ACTION_CHOOSE_CITY,ACTION_ADHKAR,ACTION_HEALTH,ACTION_PRAYER_LOG,ACTION_TASBEEH,ACTION_QURAN)} ?: return
        action.value=requested
        actionData.value=when(requested) {
            // Silencing on request of another app needs your confirmation; Najwa's own shortcut and tile go straight through.
            ACTION_SILENCE_NOW -> if(org.najwa.app.platform.LaunchToken.matches(this,intent)) null else "confirm"
            ACTION_ADHKAR -> (if(intent.getBooleanExtra("now",false)) {
                val (day,_)=org.najwa.app.platform.WorshipDay.times(this,org.najwa.app.platform.WorshipDay.today(this))
                val fajr=day.events.firstOrNull {it.prayer==org.najwa.core.Prayer.FAJR}?.time
                val asr=day.events.firstOrNull {it.prayer==org.najwa.core.Prayer.ASR}?.time
                if(fajr!=null && asr!=null && org.najwa.core.DayEngine.adhkarOfTheMoment(java.time.Instant.now(),fajr,asr)==org.najwa.core.StepKind.MORNING_ADHKAR) 0 else 1
            } else intent.getIntExtra("adhkar",0)).toString()
            // A page opens the mushaf there (the wird reminder at the bookmark, Al-Kahf on Friday).
            ACTION_QURAN -> (if(intent.getBooleanExtra("bookmark",false)) (org.najwa.app.platform.QuranStore.read(this).page+1).coerceIn(1,604)
                else intent.getIntExtra("page",0)).takeIf {it>0}?.toString()
            else -> null
        }
        actionRequest.intValue++
    }
    /** The tasbeeh screen can count with the volume keys (when chosen there). */
    override fun onKeyDown(keyCode: Int,event: android.view.KeyEvent?): Boolean {
        val counter=org.najwa.app.ui.VolumeKeyCounter.onPress
        if(counter!=null && (keyCode==android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if(event?.repeatCount==0)counter()
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    override fun onKeyUp(keyCode: Int,event: android.view.KeyEvent?): Boolean {
        if(org.najwa.app.ui.VolumeKeyCounter.onPress!=null && (keyCode==android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode==android.view.KeyEvent.KEYCODE_VOLUME_DOWN))return true
        return super.onKeyUp(keyCode,event)
    }
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

/** Android 13+ keeps a per-app language in system Settings; Najwa follows it when set. */
internal fun appLanguage(context: Context): String {
    if(Build.VERSION.SDK_INT>=33) {
        val system=runCatching {context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales}.getOrNull()
        val tag=system?.takeIf {!it.isEmpty}?.get(0)?.language?.let {if(it=="in")"id" else it}
        if(tag!=null && tag in supportedLanguages)return tag
    }
    return context.getSharedPreferences("interface-language",Context.MODE_PRIVATE).getString("language","ar")
        ?.takeIf {it in supportedLanguages} ?: "ar"
}

internal fun changeAppLanguage(activity: MainActivity,language: String) {
    require(language in supportedLanguages)
    activity.getSharedPreferences("interface-language",Context.MODE_PRIVATE).edit().putString("language",language).apply()
    val app=activity.application as NajwaApplication
    prayerExecutor.execute {
        app.runtime.notifications.updateChannels()
        publishShortcuts(app)
    }
    if(Build.VERSION.SDK_INT>=33) {
        // Keeps system Settings › Apps › Najwa › Language in step; Android recreates the activity.
        runCatching {activity.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales=LocaleList.forLanguageTags(language)}
            .onFailure {activity.recreate()}
    } else activity.recreate()
}

/** Long-press shortcuts on the launcher icon, in the app language. */
internal fun publishShortcuts(context: Context) {
    runCatching {
        val strings=context.prayerLanguage()
        fun shortcut(id: String,action: String,short: Int,long: Int,icon: Int,extra: String?=null)=ShortcutInfoCompat.Builder(context,id)
            .setShortLabel(strings.getString(short)).setLongLabel(strings.getString(long))
            .setIcon(IconCompat.createWithResource(context,icon))
            .setIntent(Intent(context,MainActivity::class.java).setAction(action).apply {if(extra!=null)putExtra(extra,true)}
                .putExtra(org.najwa.app.platform.LaunchToken.EXTRA,org.najwa.app.platform.LaunchToken.get(context))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            .build()
        ShortcutManagerCompat.setDynamicShortcuts(context,listOf(
            shortcut("silence_now",ACTION_SILENCE_NOW,R.string.shortcut_silence_short,R.string.shortcut_silence_long,R.drawable.ic_shortcut_silence),
            // Continue at the khatma bookmark; the adhkar of this moment (morning or evening).
            shortcut("quran",ACTION_QURAN,R.string.shortcut_quran_short,R.string.shortcut_quran_long,R.drawable.ic_shortcut_quran,"bookmark"),
            shortcut("adhkar_now",ACTION_ADHKAR,R.string.shortcut_adhkar_short,R.string.shortcut_adhkar_long,R.drawable.ic_shortcut_adhkar,"now"),
            shortcut("qibla",ACTION_QIBLA,R.string.shortcut_qibla_short,R.string.shortcut_qibla_long,R.drawable.ic_shortcut_qibla),
            shortcut("tasbeeh",ACTION_TASBEEH,R.string.shortcut_tasbeeh_short,R.string.shortcut_tasbeeh_long,R.drawable.ic_shortcut_tasbeeh),
            shortcut("prayer_log",ACTION_PRAYER_LOG,R.string.shortcut_log_short,R.string.shortcut_log_long,R.drawable.ic_shortcut_log)))
    }
}
