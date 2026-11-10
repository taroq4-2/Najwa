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
import java.time.Instant

@Composable fun NotificationScreen(runtime: NajwaRuntime,p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit,resumed: Int=0) {
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
    val next=scheduled?.takeIf {it.at>Instant.now() && notificationsAllowed && exactAlarmsAllowed}
    val nextLabel=next?.let {
        val label=when(it.action) {
            PrayerAction.ADHAN -> uiText("تنبيه الأذان")
            PrayerAction.IQAMA -> uiText("تذكير الإقامة")
            PrayerAction.FRIDAY -> uiText("تذكير يوم الجمعة")
            PrayerAction.SILENCE -> uiText("الصمت التلقائي")
        }
        "$label · ${localizedPrayerName(it.event.prayer)} · ${localizedUiTime(it.at,p)}"
    }
    SettingsCard("التنبيه القادم") {
        Text(nextLabel ?: uiText("لا يوجد تنبيه جاهز حاليًا"),color=c.text)
    }
    val channels=remember(resumed,p) {
        listOfNotNull(
            if(p.adhanNotifications && p.adhanPrayers.isNotEmpty()) Triple(PrayerNotifications.ADHAN,"تنبيه الأذان",runtime.notifications.adhanChannel) else null,
            if(p.iqamaNotifications && p.iqamaPrayers.isNotEmpty()) Triple(PrayerNotifications.IQAMA,"تذكير الإقامة",PrayerNotifications.IQAMA) else null,
            if(p.fridayReminder) Triple(PrayerNotifications.FRIDAY,"تذكير يوم الجمعة",PrayerNotifications.FRIDAY) else null
        ).filterNot {runtime.notifications.enabled(it.first)}
    }
    if(notificationsAllowed && channels.isNotEmpty())SettingsCard("قنوات محجوبة في الهاتف") {
        for((_,label,channel) in channels)Action(label,Modifier.fillMaxWidth()) {
            open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel))
        }
    }
    NotificationPreferences(p,onSave)
    AdhanSoundSettings(runtime,::open)
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
            Text(uiText("اسمح للمنبّهات الدقيقة كي تعمل تنبيهات الصلاة في موعدها."),color=c.text)
            Action("فتح إعدادات المنبّهات الدقيقة",Modifier.fillMaxWidth(),onClick=onAlarms)
        }
    }
}

@Composable fun NotificationPreferences(p: Preferences,onSave: (Preferences)->Unit) {
    val c=LocalPalette.current
    SettingsCard("التنبيهات") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(uiText("تنبيه الأذان"),color=c.text);Text(uiText("إشعار مع صوت للصلوات المختارة"),color=c.secondary,fontSize=12.sp)}
            Switch(p.adhanNotifications,{onSave(p.copy(adhanNotifications=it))},modifier=Modifier.semantics {contentDescription="تنبيه الأذان"})
        }
        HorizontalDivider(color=c.border)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(uiText("تذكير الإقامة"),color=c.text);Text(uiText("إشعار مرئي دون صوت أو اهتزاز"),color=c.secondary,fontSize=12.sp)}
            Switch(p.iqamaNotifications,{onSave(p.copy(iqamaNotifications=it))},modifier=Modifier.semantics {contentDescription="تذكير الإقامة"})
        }
        Text(uiText("التنبيهات مستقلة عن الصمت التلقائي، ولا تشمل الشروق."),color=c.secondary,fontSize=12.sp)
        Text(uiText("عند تفعيلها قد يعرض الهاتف رمز المنبّه وموعد تنبيه الصلاة التالي."),color=c.secondary,fontSize=12.sp)
    }
    ExpandableSettings("تخصيص التنبيهات لكل صلاة","اختر الأذان والإقامة لكل صلاة") {
        Text(uiText("تُطبّق الاختيارات عند تشغيل مفاتيح التنبيهات أعلاه."),color=c.secondary,fontSize=12.sp)
        for(prayer in dailyPrayers) {
            val prayerName=localizedPrayerName(prayer)
            Text(prayerName,color=c.text,style=MaterialTheme.typography.titleMedium)
            AlertPrayerSwitch("${uiText("تنبيه الأذان")} · $prayerName",prayer in p.adhanPrayers) {selected->
                onSave(p.copy(adhanPrayers=if(selected)p.adhanPrayers+prayer else p.adhanPrayers-prayer))
            }
            AlertPrayerSwitch("${uiText("تذكير الإقامة")} · $prayerName",prayer in p.iqamaPrayers) {selected->
                onSave(p.copy(iqamaPrayers=if(selected)p.iqamaPrayers+prayer else p.iqamaPrayers-prayer))
            }
            if(prayer!=Prayer.ISHA)HorizontalDivider(color=c.border)
        }
    }
    SettingsCard("صلاة الجمعة","تذكير أسبوعي قبل وقت الظهر المحسوب في مدينتك") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(uiText("تذكير يوم الجمعة"),color=c.text,modifier=Modifier.weight(1f))
            Switch(p.fridayReminder,{onSave(p.copy(fridayReminder=it))},modifier=Modifier.semantics {contentDescription="تذكير يوم الجمعة"})
        }
        if(p.fridayReminder) {
            Text(uiText("وقت التذكير قبل الظهر"),color=c.text)
            val leads=(listOf(5,15,DEFAULT_FRIDAY_LEAD_MINUTES,45,60)+p.fridayLeadMinutes).distinct().sorted()
            OptionGrid(leads,p.fridayLeadMinutes,{"$it دقيقة"},3) {onSave(p.copy(fridayLeadMinutes=it))}
        }
        Text(uiText("وقت الظهر تقديري، وقد يختلف موعد الخطبة والصلاة حسب المسجد."),color=c.secondary,fontSize=12.sp)
    }
    PrayerDurations("الإقامة بعد الأذان","حدّد المدة لكل صلاة حسب مسجدك. هذه تذكيرات حسب إعدادك وليست مواقيت إقامة معتمدة.",p.iqamaMinutes,defaultIqamaMinutes(),1) {onSave(p.copy(iqamaMinutes=it))}
}

@Composable
private fun AlertPrayerSwitch(label: String,selected: Boolean,onChange: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),color=LocalPalette.current.text,fontSize=14.sp)
        Switch(selected,onChange,modifier=Modifier.semantics {contentDescription=label})
    }
}
