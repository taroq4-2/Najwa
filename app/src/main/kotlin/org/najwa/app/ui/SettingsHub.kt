package org.najwa.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.BuildConfig
import org.najwa.app.MainActivity
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.appLanguage
import org.najwa.app.changeAppLanguage
import org.najwa.app.platform.HealthIssue
import org.najwa.app.platform.healthScore
import org.najwa.app.prayerDispatcher
import org.najwa.app.supportedLanguages
import org.najwa.core.*

/** Live phone readiness used by the quick settings card; null in previews. */
data class SettingsStatus(val alertsReady: Boolean,val silenceReady: Boolean,val silenceFixed: Boolean)

private data class SettingsEntry(val title: String,val subtitle: String,val icon: String,val open: ()->Unit)

/** Settings home: a status header, three quick switches, then six coloured sections. Each option lives in
 * exactly one section; the first screen shows where things are, and every detail is one tap away. */
@Composable fun SettingsScreen(p: Preferences,onSave: (Preferences)->Unit,onCity: ()->Unit,onNotifications: ()->Unit = {},onWidget: ()->Unit = {},onInformation: ()->Unit = {},onCalculation: ()->Unit = {},onAppearance: ()->Unit = {},onSilence: ()->Unit = {},
    status: SettingsStatus?=null,onSilenceSetup: ()->Unit=onSilence,onBackup: ()->Unit={},onNavigate: (Screen)->Unit={},
    health: List<HealthIssue>?=null,onFix: (HealthIssue)->Unit={}) {
    val c=LocalPalette.current
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    var languageDialog by rememberSaveable {mutableStateOf(false)}
    var whatsNew by rememberSaveable {mutableStateOf(false)}
    var share by rememberSaveable {mutableStateOf(false)}
    PageHeading(stringResource(R.string.settings_title),stringResource(R.string.settings_subtitle))
    var query by rememberSaveable {mutableStateOf("")}
    val sections=listOf(
        SettingsSection(stringResource(R.string.section_times),"${p.city.name} · ${localizedMethodName(p.method)}","pin","teal",onCalculation),
        SettingsSection(stringResource(R.string.section_adhan),stringResource(R.string.section_adhan_summary),"speaker","amber") {onNavigate(Screen.SOUND)},
        SettingsSection(stringResource(R.string.section_alerts),stringResource(R.string.section_alerts_summary,p.adhanPrayers.size.takeIf {p.adhanNotifications} ?: 0,
            p.iqamaPrayers.size.takeIf {p.iqamaNotifications} ?: 0),"bell","blue",onNotifications),
        SettingsSection(stringResource(R.string.section_mosque),stringResource(R.string.section_mosque_summary),"mosque","violet") {onNavigate(Screen.MOSQUES)},
        SettingsSection(stringResource(R.string.section_worship),stringResource(R.string.section_worship_summary),"moon","pink") {onNavigate(Screen.SUNNAH)},
        SettingsSection(stringResource(R.string.section_display),"${uiText(p.theme.label)} · ${activity?.let {supportedLanguages.getValue(appLanguage(it))} ?: ""}","palette","mint",onAppearance),
    )
    // Search reaches every section and the pages inside them.
    val searchable=sections.map {SettingsEntry(it.title,it.subtitle,it.glyph,it.open)}+listOfNotNull(
        SettingsEntry(uiText("الصمت أثناء الصلاة"),uiText("التفعيل والمدة واستعادة الرنين"),"sound",onSilence),
        SettingsEntry(uiText("ويدجت الشاشة الرئيسية"),stringResource(R.string.widget_summary),"widget",onWidget),
        if(activity!=null)SettingsEntry(stringResource(R.string.app_language_label),supportedLanguages.getValue(appLanguage(activity)),"globe") {languageDialog=true} else null,
        SettingsEntry(stringResource(R.string.ramadan_title),stringResource(R.string.ramadan_help),"moon") {onNavigate(Screen.SUNNAH)},
        SettingsEntry(stringResource(R.string.second_city_title),stringResource(R.string.second_city_help),"globe",onCalculation),
        SettingsEntry(stringResource(R.string.log_title),stringResource(R.string.log_setting_help),"check") {onNavigate(Screen.PRAYER_LOG)},
        SettingsEntry(stringResource(R.string.live_setting_title),stringResource(R.string.live_setting_help),"clock") {onNavigate(Screen.MOSQUES)},
        SettingsEntry(stringResource(R.string.data_title),stringResource(R.string.data_summary),"data") {onNavigate(Screen.ADVANCED)},
        SettingsEntry(stringResource(R.string.backup_title),stringResource(R.string.backup_summary),"restore",onBackup),
        SettingsEntry(uiText("معلومات التطبيق"),uiText("من نحن · تواصل معنا · الخصوصية"),"shield",onInformation))
    OutlinedTextField(query,{query=it},placeholder={Text(stringResource(R.string.settings_search))},singleLine=true,
        shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().padding(bottom=14.dp),
        leadingIcon={Glyph("search",c.secondary,Modifier.size(18.dp))})
    val term=citySearchKey(query)
    if(term.isNotEmpty()) {
        val matches=searchable.filter {citySearchKey(it.title+" "+it.subtitle).contains(term)}
        if(matches.isEmpty())Text(stringResource(R.string.settings_search_none),color=c.secondary,modifier=Modifier.padding(8.dp))
        else Panel(Modifier.fillMaxWidth()) {matches.forEachIndexed {i,e->if(i>0)SettingsDivider();SettingsDestination(e.title,e.subtitle,e.icon,e.open)}}
    } else {
        StatusHeader(p,health,onFix,onCity)
        if(status!=null) {
            // Quick switches: the three things people turn on and off most.
            Row(Modifier.fillMaxWidth().padding(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                QuickSwitch(stringResource(R.string.system_adhan),"bell",p.adhanNotifications,Modifier.weight(1f)) {onSave(p.copy(adhanNotifications=it))}
                QuickSwitch(stringResource(R.string.system_iqama),"clock",p.iqamaNotifications,Modifier.weight(1f)) {onSave(p.copy(iqamaNotifications=it))}
                QuickSwitch(stringResource(R.string.quick_silence),"sound",p.autoSilent,Modifier.weight(1f),enabled=!status.silenceFixed) {
                    if(it && !status.silenceReady)onSilenceSetup() else onSave(p.copy(autoSilent=it))
                }
            }
        }
        // Six sections, two per row (one per row at very large text).
        val perRow=if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f)1 else 2
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            sections.chunked(perRow).forEach {row->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    row.forEach {SectionTile(it,Modifier.weight(1f).fillMaxHeight())}
                    repeat(perRow-row.size) {Spacer(Modifier.weight(1f))}
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel(stringResource(R.string.settings_group_more))
        Panel(Modifier.fillMaxWidth()) {
            SettingsDestination(stringResource(R.string.data_title),stringResource(R.string.data_summary),"data") {onNavigate(Screen.ADVANCED)}
            SettingsDivider()
            SettingsDestination(stringResource(R.string.whats_new_title),stringResource(R.string.whats_new_summary,BuildConfig.VERSION_NAME),"star") {whatsNew=true}
            SettingsDivider()
            SettingsDestination(stringResource(R.string.share_app_title),stringResource(R.string.share_app_summary),"share") {share=true}
            SettingsDivider()
            SettingsDestination(uiText("معلومات التطبيق"),uiText("من نحن · تواصل معنا · الخصوصية"),"shield",onInformation)
        }
    }
    Text("${stringResource(R.string.system_brand)} · ${BuildConfig.VERSION_NAME}",color=c.secondary,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    if(languageDialog && activity!=null)LanguageDialog(activity) {languageDialog=false}
    if(whatsNew)WhatsNewDialog {whatsNew=false}
    if(share)QrDialog(stringResource(R.string.share_app_title),SHARE_LINK,stringResource(R.string.share_app_text)) {share=false}
}

private data class SettingsSection(val title: String,val subtitle: String,val glyph: String,val tone: String,val open: ()->Unit)

@Composable private fun SectionTile(s: SettingsSection,modifier: Modifier) {
    val c=LocalPalette.current
    val a=accent(s.tone)
    Surface(onClick=s.open,shape=RoundedCornerShape(22.dp),color=c.surface,border=androidx.compose.foundation.BorderStroke(1.dp,c.border),modifier=modifier) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            IconBadge(s.glyph,a,44)
            Text(s.title,color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold,maxLines=2)
            Text(s.subtitle,color=c.secondary,fontSize=12.sp,lineHeight=17.sp,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun QuickSwitch(label: String,glyph: String,on: Boolean,modifier: Modifier,enabled: Boolean=true,onChange: (Boolean)->Unit) {
    val c=LocalPalette.current
    Surface(onClick={if(enabled)onChange(!on)},shape=RoundedCornerShape(18.dp),color=if(on)c.selected else c.surface,
        border=androidx.compose.foundation.BorderStroke(1.dp,if(on)c.green else c.border),
        modifier=modifier.heightIn(min=64.dp).semantics {role=androidx.compose.ui.semantics.Role.Switch;stateDescription=if(on)"on" else "off";contentDescription=label}) {
        Column(Modifier.padding(vertical=10.dp,horizontal=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Glyph(glyph,if(on)c.mint else c.secondary,Modifier.size(20.dp))
            Text(label,color=if(on)c.mint else c.secondary,fontSize=13.sp,fontWeight=if(on)FontWeight.Bold else FontWeight.Normal,maxLines=1)
            Text(stringResource(if(on)R.string.settings_on else R.string.settings_off),color=c.secondary,fontSize=11.sp)
        }
    }
}

/** City, method and alert readiness in one card; problems show their fix right here. */
@Composable private fun StatusHeader(p: Preferences,issues: List<HealthIssue>?,onFix: (HealthIssue)->Unit,onCity: ()->Unit) {
    val c=LocalPalette.current
    val ok=issues.isNullOrEmpty()
    Panel(Modifier.fillMaxWidth(),radius=24,selected=ok) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp),
                modifier=Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick=onCity)) {
                Box(Modifier.size(52.dp).background(if(ok)c.green else c.amber.copy(alpha=.2f),androidx.compose.foundation.shape.CircleShape),contentAlignment=Alignment.Center) {
                    if(issues==null)Glyph("pin",c.bg) else Text("${healthScore(issues)}",color=if(ok)c.bg else c.amber,fontWeight=FontWeight.Bold,fontSize=17.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.city.name,color=c.text,fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Text(if(issues==null)localizedMethodName(p.method) else stringResource(if(ok)R.string.health_ok else R.string.health_needs),
                        color=if(ok)c.secondary else c.amber,fontSize=12.sp)
                }
            }
            issues?.forEach {issue->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(healthText(issue)),Modifier.weight(1f),color=c.text,fontSize=13.sp)
                    TextButton(onClick={onFix(issue)}) {Text(stringResource(R.string.settings_fix))}
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable fun LanguageDialog(activity: MainActivity,onDismiss: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.app_language_label))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            val current=appLanguage(activity)
            supportedLanguages.forEach {(code,name)->
                OptionButton(name,code==current,Modifier.fillMaxWidth()) {onDismiss();if(code!=current)changeAppLanguage(activity,code)}
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(uiText("إغلاق"))}})
}

const val SHARE_LINK="https://taroq4-2.github.io/Najwa/"

@Composable private fun HealthCard(issues: List<HealthIssue>,onFix: (HealthIssue)->Unit) {
    val c=LocalPalette.current
    val score=healthScore(issues)
    Panel(Modifier.fillMaxWidth(),selected=issues.isEmpty()) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).background(if(issues.isEmpty())c.green else c.amber.copy(alpha=.2f),RoundedCornerShape(18.dp)),contentAlignment=Alignment.Center) {
                    Text("$score",color=if(issues.isEmpty())c.bg else c.amber,fontWeight=FontWeight.Bold,fontSize=18.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.health_title),color=c.text,fontWeight=FontWeight.Bold,fontSize=16.sp)
                    Text(stringResource(if(issues.isEmpty())R.string.health_ok else R.string.health_needs),color=c.secondary,fontSize=12.sp)
                }
            }
            issues.forEach {issue->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(stringResource(healthText(issue)),Modifier.weight(1f),color=c.text,fontSize=13.sp)
                    TextButton(onClick={onFix(issue)}) {Text(stringResource(R.string.settings_fix))}
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}
private fun healthText(issue: HealthIssue)=when(issue) {
    HealthIssue.NOTIFICATIONS->R.string.health_notifications
    HealthIssue.CHANNELS->R.string.health_channels
    HealthIssue.EXACT_ALARMS->R.string.health_exact
    HealthIssue.RINGER_ACCESS->R.string.health_ringer
    HealthIssue.BATTERY->R.string.health_battery
    HealthIssue.SOUND->R.string.health_sound
}

@Composable fun WhatsNewDialog(onDismiss: ()->Unit) {
    val c=LocalPalette.current
    val items=listOf(R.string.new17_1,R.string.new17_2,R.string.new17_3,R.string.new16_1,R.string.new16_2,R.string.new16_3,R.string.new16_4,R.string.new16_5,R.string.new16_6,
        R.string.new_1,R.string.new_2,R.string.new_3,R.string.new_5)
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.whats_new_heading,BuildConfig.VERSION_NAME))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items.forEach {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {Text("•",color=c.mint);Text(stringResource(it),color=c.text,fontSize=14.sp)}}
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.whats_new_ok))}})
}

// ───────────────────────────── Ramadan & sunnah ─────────────────────────────

@Composable fun SunnahScreen(p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    val ctx=LocalContext.current
    BackLink(stringResource(R.string.settings_back),onBack)
    PageHeading(stringResource(R.string.section_worship),stringResource(R.string.worship_intro))
    SettingsCard(stringResource(R.string.worship_daily_title),stringResource(R.string.worship_daily_help)) {
        PreferenceSwitch(stringResource(R.string.dua_setting),stringResource(R.string.dua_setting_help),p.dailyDua) {onSave(p.copy(dailyDua=it))}
        PreferenceSwitch(stringResource(R.string.log_setting),stringResource(R.string.log_setting_help),p.prayerLog) {onSave(p.copy(prayerLog=it))}
        if(p.prayerLog)PreferenceSwitch(stringResource(R.string.log_check_setting),stringResource(R.string.log_check_setting_help),p.logCheckIn) {onSave(p.copy(logCheckIn=it))}
    }
    WorshipCompanionSettings()
    SettingsCard(stringResource(R.string.ramadan_title),stringResource(R.string.ramadan_help)) {
        PreferenceSwitch(stringResource(R.string.ramadan_mode),stringResource(R.string.ramadan_mode_help),p.ramadanMode) {onSave(p.copy(ramadanMode=it))}
        if(p.ramadanMode) {
            PreferenceSwitch(stringResource(R.string.suhoor_title),stringResource(R.string.suhoor_help),p.suhoorReminder) {onSave(p.copy(suhoorReminder=it))}
            if(p.suhoorReminder)OptionGrid((listOf(30,45,60,90)+p.suhoorMinutes).distinct().sorted(),p.suhoorMinutes,{ctx.getString(R.string.minutes_short,it)},4) {onSave(p.copy(suhoorMinutes=it))}
            PreferenceSwitch(stringResource(R.string.taraweeh_title),stringResource(R.string.taraweeh_help),p.taraweehReminder) {onSave(p.copy(taraweehReminder=it))}
            if(p.taraweehReminder)OptionGrid((listOf(0,15,20,30,45)+p.taraweehMinutes).distinct().sorted(),p.taraweehMinutes,{if(it==0)ctx.getString(R.string.at_isha) else ctx.getString(R.string.minutes_short,it)},5) {onSave(p.copy(taraweehMinutes=it))}
        }
    }
    SettingsCard(stringResource(R.string.extra_times_title),stringResource(R.string.extra_times_help)) {
        PreferenceSwitch(stringResource(R.string.extra_times_show),stringResource(R.string.extra_times_show_help),p.showExtraTimes) {onSave(p.copy(showExtraTimes=it))}
        PreferenceSwitch(stringResource(R.string.sunrise_reminder),stringResource(R.string.sunrise_reminder_help),p.sunriseReminder) {onSave(p.copy(sunriseReminder=it))}
        if(p.sunriseReminder)OptionGrid((listOf(10,15,20,30)+p.sunriseLeadMinutes).distinct().sorted(),p.sunriseLeadMinutes,{ctx.getString(R.string.minutes_short,it)},4) {onSave(p.copy(sunriseLeadMinutes=it))}
        PreferenceSwitch(stringResource(R.string.duha_reminder),stringResource(R.string.duha_reminder_help),p.duhaReminder) {onSave(p.copy(duhaReminder=it))}
        PreferenceSwitch(stringResource(R.string.qiyam_reminder),stringResource(R.string.qiyam_reminder_help),p.qiyamReminder) {onSave(p.copy(qiyamReminder=it))}
    }
    SettingsCard(stringResource(R.string.sunnah_reminders_title),stringResource(R.string.sunnah_reminders_help)) {
        PreferenceSwitch(stringResource(R.string.adhkar_reminders),stringResource(R.string.adhkar_reminders_help),p.adhkarReminders) {onSave(p.copy(adhkarReminders=it))}
        PreferenceSwitch(stringResource(R.string.kahf_reminder),stringResource(R.string.kahf_reminder_help),p.kahfReminder) {onSave(p.copy(kahfReminder=it))}
        PreferenceSwitch(stringResource(R.string.fasting_reminders),stringResource(R.string.fasting_reminders_help),p.fastingReminders) {onSave(p.copy(fastingReminders=it))}
    }
    Text(stringResource(R.string.sunnah_note),color=c.secondary,fontSize=12.sp)
}

// ───────────────────────────── Adhan sound & playback ─────────────────────────────

@Composable fun SoundScreen(runtime: NajwaRuntime,p: Preferences,onSave: (Preferences)->Unit,open: (android.content.Intent)->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    val scope=rememberCoroutineScope()
    var fajrOwn by remember {mutableStateOf(runtime.notifications.fajrSounds.hasOwn())}
    var testResult by remember {mutableStateOf<Int?>(null)}
    BackLink(stringResource(R.string.settings_back),onBack)
    PageHeading(stringResource(R.string.sound_screen_title),stringResource(R.string.sound_screen_intro))
    AdhanSoundSettings(runtime,open)
    SettingsCard(stringResource(R.string.fajr_adhan_title),stringResource(R.string.fajr_adhan_help)) {
        PreferenceSwitch(stringResource(R.string.fajr_adhan_switch),stringResource(R.string.fajr_adhan_switch_help),fajrOwn) {on->
            scope.launch {
                withContext(Dispatchers.IO) {runCatching {if(on)runtime.notifications.fajrSounds.builtIn(2) else runtime.notifications.fajrSounds.clear()}}
                fajrOwn=runtime.notifications.fajrSounds.hasOwn()
            }
        }
    }
    if(fajrOwn)AdhanSoundSettings(runtime,open,fajr=true)
    SettingsCard(stringResource(R.string.enhanced_title),stringResource(R.string.enhanced_help)) {
        PreferenceSwitch(stringResource(R.string.enhanced_switch),stringResource(R.string.enhanced_switch_help),p.enhancedAdhan) {onSave(p.copy(enhancedAdhan=it))}
        if(p.enhancedAdhan) {
            PreferenceSwitch(stringResource(R.string.enhanced_fade),stringResource(R.string.enhanced_fade_help),p.adhanFadeIn) {onSave(p.copy(adhanFadeIn=it))}
            PreferenceSwitch(stringResource(R.string.enhanced_flip),stringResource(R.string.enhanced_flip_help),p.flipToStop) {onSave(p.copy(flipToStop=it))}
            PreferenceSwitch(stringResource(R.string.enhanced_calls),stringResource(R.string.enhanced_calls_help),p.quietDuringCalls) {onSave(p.copy(quietDuringCalls=it))}
            PreferenceSwitch(stringResource(R.string.enhanced_headphones),stringResource(R.string.enhanced_headphones_help),p.headphonesOnly) {onSave(p.copy(headphonesOnly=it))}
            Text(stringResource(R.string.enhanced_volume_key),color=c.secondary,fontSize=12.sp)
        }
    }
    SettingsCard(stringResource(R.string.access_title),stringResource(R.string.access_help)) {
        PreferenceSwitch(stringResource(R.string.access_announce),stringResource(R.string.access_announce_help),p.announceAdhan) {onSave(p.copy(announceAdhan=it,enhancedAdhan=p.enhancedAdhan || it))}
        PreferenceSwitch(stringResource(R.string.access_flash),stringResource(R.string.access_flash_help),p.flashAlert) {onSave(p.copy(flashAlert=it,enhancedAdhan=p.enhancedAdhan || it))}
        Text(stringResource(R.string.access_note),color=c.secondary,fontSize=12.sp)
    }
    Action(stringResource(R.string.alerts_test_adhan),Modifier.fillMaxWidth(),true) {
        scope.launch {testResult=if(withContext(prayerDispatcher) {runtime.testNotification(true)})R.string.alerts_test_sent else R.string.alerts_test_blocked}
    }
    testResult?.let {Text(stringResource(it),color=if(it==R.string.alerts_test_sent)c.mint else c.amber,fontSize=13.sp,modifier=Modifier.padding(top=8.dp))}
}

// ───────────────────────────── Advanced tools ─────────────────────────────

/** City and country in the reader's language (never a raw zone id such as Asia/Riyadh). */
@Composable fun SecondCityCard(p: Preferences,onSave: (Preferences)->Unit,onSecondCity: ()->Unit) {
    val c=LocalPalette.current
    SettingsCard(stringResource(R.string.second_city_title),stringResource(R.string.second_city_help)) {
        val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
        val place=p.secondCity?.let {city->
            val country=city.countryCode?.let {java.util.Locale("",it).getDisplayCountry(locale)}?.takeIf {it.isNotBlank()}
                ?: java.util.TimeZone.getTimeZone(city.zone).getDisplayName(false,java.util.TimeZone.LONG,locale)
            "${city.name} · $country"
        }
        Text(place ?: stringResource(R.string.second_city_none),color=c.text)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Action(stringResource(R.string.second_city_choose),Modifier.weight(1f),true,onSecondCity)
            if(p.secondCity!=null)Action(stringResource(R.string.second_city_remove),Modifier.weight(1f)) {onSave(p.copy(secondCity=null))}
        }
    }
}

/** Backup and automation: the data side of Najwa. */
@Composable fun AdvancedScreen(p: Preferences,onSave: (Preferences)->Unit,onSecondCity: ()->Unit,onBackup: ()->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    BackLink(stringResource(R.string.settings_back),onBack)
    PageHeading(stringResource(R.string.data_title),stringResource(R.string.data_intro))
    SettingsCard(stringResource(R.string.backup_title),stringResource(R.string.backup_summary)) {
        Action(stringResource(R.string.backup_open),Modifier.fillMaxWidth(),true,onClick=onBackup)
    }
    SettingsCard(stringResource(R.string.automation_title),stringResource(R.string.automation_help)) {
        PreferenceSwitch(stringResource(R.string.automation_switch),stringResource(R.string.automation_switch_help),p.broadcastEvents) {onSave(p.copy(broadcastEvents=it))}
        if(p.broadcastEvents)Text("org.najwa.action.PRAYER_EVENT\nprayer = fajr | dhuhr | asr | maghrib | isha\nstage = before_adhan | adhan | iqama | silence",
            color=c.mint,fontSize=12.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,
            modifier=Modifier.background(c.bg,RoundedCornerShape(12.dp)).padding(12.dp))
    }
    Text(stringResource(R.string.watch_note),color=c.secondary,fontSize=12.sp)
}
