package org.najwa.app.ui

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import org.najwa.app.NajwaRuntime
import org.najwa.app.platform.PrayerNotifications
import org.najwa.app.platform.openSystemSettings
import org.najwa.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.najwa.app.R
import org.najwa.app.prayerDispatcher
import java.time.Instant

@Composable fun NotificationScreen(runtime: NajwaRuntime,p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit,resumed: Int=0,onSound: ()->Unit={}) {
    val context=LocalContext.current
    val c=LocalPalette.current
    var error by remember {mutableStateOf<String?>(null)}
    fun open(intent: Intent) {if(!openSystemSettings(context,intent))error="تعذّر فتح الإعدادات. افتح إعدادات نجوى من إعدادات الهاتف."}
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading("تنبيهات الصلاة","الأذان والإقامة وتذكير يوم الجمعة")
    val (notificationsAllowed,exactAlarmsAllowed)=remember(resumed) {
        runtime.notifications.granted to runtime.alarms.exactAccess
    }
    var scheduled by remember(resumed,p) {mutableStateOf(runtime.nextNotification)}
    LaunchedEffect(runtime,resumed,p) {
        while(true) {
            scheduled=runtime.nextNotification
            delay(15_000)
        }
    }
    NotificationReadiness(
        notificationsAllowed,exactAlarmsAllowed,
        onNotifications={open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},
        onAlarms={open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}
    )
    BackgroundReadiness(resumed)
    val next=scheduled?.takeIf {it.at>Instant.now() && notificationsAllowed}
    val nextLabel=next?.let {
        val label=when(it.action) {
            PrayerAction.ADHAN -> uiText("تنبيه الأذان")
            PrayerAction.IQAMA -> uiText("تذكير الإقامة")
            PrayerAction.FRIDAY -> uiText("تذكير يوم الجمعة")
            PrayerAction.PRE_ADHAN -> stringResource(R.string.pre_adhan_title)
            PrayerAction.LIVE -> stringResource(R.string.live_setting)
            PrayerAction.REMINDER -> stringResource(R.string.diag_reminder)
            PrayerAction.SILENCE -> uiText("الصمت التلقائي")
        }
        "$label · ${localizedPrayerName(it.event.prayer,it.event.time,p)} · ${localizedUiTime(it.at,p)}"
    }
    val scope=rememberCoroutineScope()
    var testResult by remember {mutableStateOf<Int?>(null)}
    var testing by remember {mutableStateOf(false)}
    fun test(adhan: Boolean) {
        if(testing)return
        testing=true
        scope.launch {
            val sent=withContext(prayerDispatcher) {runtime.testNotification(adhan)}
            testResult=if(sent)R.string.alerts_test_sent else R.string.alerts_test_blocked
            testing=false
        }
    }
    SettingsCard(stringResource(R.string.alerts_status_title)) {
        Text(uiText("التنبيه القادم"),color=c.secondary,fontSize=13.sp)
        Text(nextLabel ?: uiText("لا يوجد تنبيه جاهز حاليًا"),color=c.text)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={test(true)},enabled=!testing,modifier=Modifier.weight(1f)) {Text(stringResource(R.string.alerts_test_adhan))}
            OutlinedButton(onClick={test(false)},enabled=!testing,modifier=Modifier.weight(1f)) {Text(stringResource(R.string.alerts_test_iqama))}
        }
        testResult?.let {Text(stringResource(it),color=if(it==R.string.alerts_test_sent)c.mint else c.amber,fontSize=13.sp)}
        if(testResult==R.string.alerts_test_sent)Text(stringResource(R.string.alerts_test_stop_hint),color=c.secondary,fontSize=12.sp)
    }
    val channels=remember(resumed,p) {
        listOfNotNull(
            if(p.adhanNotifications && p.adhanPrayers.isNotEmpty()) Triple(PrayerNotifications.ADHAN,"تنبيه الأذان",runtime.notifications.adhanChannel) else null,
            if(p.iqamaNotifications && p.iqamaPrayers.isNotEmpty()) Triple(PrayerNotifications.IQAMA,"تذكير الإقامة",PrayerNotifications.IQAMA) else null,
            if(p.fridayReminder) Triple(PrayerNotifications.FRIDAY,"تذكير يوم الجمعة",PrayerNotifications.FRIDAY) else null,
            if(p.preAdhanReminder && p.adhanPrayers.isNotEmpty()) Triple(PrayerNotifications.PRE_ADHAN,context.getString(R.string.pre_adhan_title),PrayerNotifications.PRE_ADHAN) else null
        ).filterNot {runtime.notifications.enabled(it.first)}
    }
    if(notificationsAllowed && channels.isNotEmpty())SettingsCard("قنوات محجوبة في الهاتف") {
        for((_,label,channel) in channels)Action(label,Modifier.fillMaxWidth()) {
            open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel))
        }
    }
    NotificationPreferences(p,onSave)
    Panel(Modifier.fillMaxWidth()) {SettingsDestination(stringResource(R.string.section_adhan),stringResource(R.string.section_adhan_summary),"speaker",onSound)}
    Spacer(Modifier.height(14.dp))
    ExpandableSettings("ظهور الإشعارات","الإقامة والجمعة وشاشة القفل") {
        PreferenceSwitch("تفاصيل شاشة القفل","يتيح عرض اسم المدينة وتفاصيل الصلاة عند سماح الهاتف",p.showLockScreenDetails) {
            onSave(p.copy(showLockScreenDetails=it))
        }
        Action("إعدادات إشعار الإقامة",Modifier.fillMaxWidth()) {open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,PrayerNotifications.IQAMA))}
        Action("إعدادات إشعار الجمعة",Modifier.fillMaxWidth()) {open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,PrayerNotifications.FRIDAY))}
        Text(uiText("ظهور نافذة أعلى الشاشة وعلى شاشة القفل يتبع إعدادات إشعارات هاتفك. الإقامة مرئية دون صوت افتراضيًا."),color=c.secondary,fontSize=12.sp)
    }
    ExpandableSettings("حالة التنبيهات والدعم","ملخص محلي يمكنك نسخه") {
        var copied by remember {mutableStateOf(false)}
        val report=listOf(
            "Najwa ${org.najwa.app.BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE}",
            "${uiText("إذن الإشعارات")} · ${uiText(if(notificationsAllowed)"متاح" else "محجوب")}",
            "${uiText("المنبّهات الدقيقة")} · ${uiText(if(exactAlarmsAllowed)"متاح" else "محجوب")}",
            "${uiText("إذن التحكم في الرنين")} · ${uiText(if(runtime.audio.policyAccess)"متاح" else "محجوب")}",
            runtime.lastDispatch() ?: uiText("لا يوجد سجل حديث")
        ).joinToString("\n")
        Text(report,color=c.secondary,fontSize=13.sp)
        Action("نسخ تقرير الحالة",Modifier.fillMaxWidth()) {
            val clipboard=context.getSystemService(ClipboardManager::class.java)
            if(clipboard!=null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Najwa diagnostics",report))
                copied=true
            }
        }
        if(copied)Text(uiText("تم نسخ التقرير"),color=c.mint)
    }
    error?.let { Text(it,color=c.amber) }
}

@Composable
internal fun NotificationReadiness(notificationsAllowed: Boolean,exactAlarmsAllowed: Boolean,onNotifications: ()->Unit,onAlarms: ()->Unit) {
    if(notificationsAllowed && exactAlarmsAllowed)return
    val c=LocalPalette.current
    SettingsCard("تفعيل التنبيهات من الهاتف") {
        if(!notificationsAllowed) {
            Text(uiText("إشعارات نجوى متوقفة في إعدادات الهاتف."),color=c.text)
            Action("فتح إعدادات إشعارات نجوى",Modifier.fillMaxWidth(),onClick=onNotifications)
        }
        if(!exactAlarmsAllowed) {
            Text(stringResource(R.string.exact_alarm_approximate),color=c.text)
            Action("فتح إعدادات المنبّهات الدقيقة",Modifier.fillMaxWidth(),onClick=onAlarms)
        }
    }
}

@Composable fun NotificationPreferences(p: Preferences,onSave: (Preferences)->Unit) {
    val c=LocalPalette.current
    val adhanLabel=uiText("تنبيه الأذان")
    val iqamaLabel=uiText("تذكير الإقامة")
    val fridayLabel=uiText("تذكير يوم الجمعة")
    // 1) The three main switches together; everything else is in collapsible sections.
    SettingsCard(stringResource(R.string.alerts_main_title),stringResource(R.string.alerts_main_subtitle)) {
        AlertSwitch(adhanLabel,uiText("إشعار مع صوت للصلوات المختارة"),p.adhanNotifications) {onSave(p.copy(adhanNotifications=it))}
        HorizontalDivider(color=c.border)
        AlertSwitch(iqamaLabel,uiText("إشعار مرئي دون صوت أو اهتزاز"),p.iqamaNotifications) {onSave(p.copy(iqamaNotifications=it))}
        HorizontalDivider(color=c.border)
        AlertSwitch(fridayLabel,stringResource(R.string.alerts_friday_subtitle),p.fridayReminder) {onSave(p.copy(fridayReminder=it))}
        HorizontalDivider(color=c.border)
        AlertSwitch(stringResource(R.string.pre_adhan_title),stringResource(R.string.pre_adhan_subtitle),p.preAdhanReminder) {onSave(p.copy(preAdhanReminder=it))}
        if(p.preAdhanReminder) {
            val choices=(listOf(5,DEFAULT_PRE_ADHAN_MINUTES,15,20,30)+p.preAdhanMinutes).distinct().sorted()
            val ctx=LocalContext.current
            OptionGrid(choices,p.preAdhanMinutes,{ctx.getString(R.string.minutes_short,it)},3) {onSave(p.copy(preAdhanMinutes=it))}
        }
        HorizontalDivider(color=c.border)
        AlertSwitch(stringResource(R.string.jumuah_title),stringResource(R.string.jumuah_subtitle),p.jumuah) {onSave(p.copy(jumuah=it))}
        if(p.fridayReminder) {
            Text(uiText("وقت التذكير قبل الظهر"),color=c.text,fontSize=14.sp)
            val leads=(listOf(5,15,DEFAULT_FRIDAY_LEAD_MINUTES,45,60)+p.fridayLeadMinutes).distinct().sorted()
            OptionGrid(leads,p.fridayLeadMinutes,{"$it دقيقة"},3) {onSave(p.copy(fridayLeadMinutes=it))}
            Text(uiText("وقت الظهر تقديري، وقد يختلف موعد الخطبة والصلاة حسب المسجد."),color=c.secondary,fontSize=12.sp)
        }
        Text(uiText("التنبيهات مستقلة عن الصمت التلقائي، ولا تشمل الشروق."),color=c.secondary,fontSize=12.sp)
    }
    // 2) Per-prayer selection as a compact table (prayer · adhan · iqama).
    ExpandableSettings(stringResource(R.string.alerts_prayers_title),
        stringResource(R.string.alerts_prayers_summary,p.adhanPrayers.size,p.iqamaPrayers.size)) {
        Text(uiText("تُطبّق الاختيارات عند تشغيل مفاتيح التنبيهات أعلاه."),color=c.secondary,fontSize=12.sp)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.system_adhan),Modifier.width(72.dp),color=c.secondary,fontSize=12.sp,textAlign=TextAlign.Center)
            Text(stringResource(R.string.system_iqama),Modifier.width(72.dp),color=c.secondary,fontSize=12.sp,textAlign=TextAlign.Center)
        }
        for(prayer in dailyPrayers) {
            val prayerName=localizedPrayerName(prayer)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(prayerName,Modifier.weight(1f),color=c.text,fontSize=15.sp)
                AlertPrayerCheck("$adhanLabel · $prayerName",prayer in p.adhanPrayers,p.adhanNotifications) {selected->
                    onSave(p.copy(adhanPrayers=if(selected)p.adhanPrayers+prayer else p.adhanPrayers-prayer))
                }
                AlertPrayerCheck("$iqamaLabel · $prayerName",prayer in p.iqamaPrayers,p.iqamaNotifications) {selected->
                    onSave(p.copy(iqamaPrayers=if(selected)p.iqamaPrayers+prayer else p.iqamaPrayers-prayer))
                }
            }
        }
    }
}

@Composable
private fun AlertSwitch(title: String,subtitle: String,checked: Boolean,onChange: (Boolean)->Unit) {
    val c=LocalPalette.current
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {Text(title,color=c.text);Text(subtitle,color=c.secondary,fontSize=12.sp)}
        Switch(checked,onChange,modifier=Modifier.semantics {contentDescription=title})
    }
}

/** Dimmed (but still editable) when the matching main switch is off. */
@Composable
private fun AlertPrayerCheck(label: String,selected: Boolean,active: Boolean,onChange: (Boolean)->Unit) {
    Box(Modifier.width(72.dp).alpha(if(active)1f else .5f),contentAlignment=Alignment.Center) {
        Checkbox(selected,onChange,modifier=Modifier.semantics {contentDescription=label})
    }
}

/** Iqama: minutes after each adhan, the countdown and the live card. Shown under the mosques. */
@Composable fun IqamaSettings(p: Preferences,onSave: (Preferences)->Unit) {
    val c=LocalPalette.current
    Spacer(Modifier.height(18.dp))
    SettingsCard(stringResource(R.string.iqama_section_title),stringResource(R.string.iqama_section_help)) {
        PreferenceSwitch(uiText("عداد الإقامة"),uiText("بعد الأذان، يعرض المتبقي على الإقامة في الرئيسية والويدجت"),p.showIqamaCountdown) {onSave(p.copy(showIqamaCountdown=it))}
        PreferenceSwitch(stringResource(R.string.live_setting),stringResource(R.string.live_setting_switch_help),p.liveIqama) {onSave(p.copy(liveIqama=it))}
        Text(uiText("أخضر حتى الأذان، وذهبي أثناء انتظار الإقامة."),color=c.secondary,fontSize=12.sp)
    }
    ExpandableSettings(stringResource(R.string.alerts_iqama_times_title),stringResource(R.string.alerts_iqama_times_summary),initiallyExpanded=p.activeMosque==null) {
        PrayerDurations("الإقامة بعد الأذان","حدّد المدة لكل صلاة حسب مسجدك. هذه تذكيرات حسب إعدادك وليست مواقيت إقامة معتمدة.",p.iqamaMinutes,defaultIqamaMinutes(),1,framed=false) {onSave(p.copy(iqamaMinutes=it))}
    }
}
