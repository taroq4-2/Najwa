package org.najwa.app.ui

import android.content.Intent
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

@Composable fun NotificationScreen(runtime: NajwaRuntime,p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit) {
    val context=LocalContext.current
    val c=LocalPalette.current
    var error by remember {mutableStateOf<String?>(null)}
    fun open(intent: Intent) {if(!openSystemSettings(context,intent))error="تعذّر فتح الإعدادات. افتح إعدادات نجوى من إعدادات الهاتف."}
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading("تنبيهات الصلاة","الأذان والإقامة وتذكير يوم الجمعة")
    NotificationPreferences(p,onSave)
    AdhanSoundSettings(runtime,::open)
    ExpandableSettings("ظهور الإشعارات","الإقامة والجمعة وشاشة القفل") {
        Action("إعدادات إشعار الإقامة",Modifier.fillMaxWidth()) {open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,PrayerNotifications.IQAMA))}
        Action("إعدادات إشعار الجمعة",Modifier.fillMaxWidth()) {open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,PrayerNotifications.FRIDAY))}
        Text("ظهور نافذة أعلى الشاشة وعلى شاشة القفل يتبع إعدادات إشعارات هاتفك. الإقامة مرئية دون صوت افتراضيًا.",color=c.secondary,fontSize=12.sp)
    }
    error?.let { Text(it,color=c.amber) }
}

@Composable fun NotificationPreferences(p: Preferences,onSave: (Preferences)->Unit) {
    val c=LocalPalette.current
    SettingsCard("التنبيهات") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("تنبيه الأذان",color=c.text);Text("إشعار مع صوت للصلوات الخمس",color=c.secondary,fontSize=12.sp)}
            Switch(p.adhanNotifications,{onSave(p.copy(adhanNotifications=it))},modifier=Modifier.semantics {contentDescription="تنبيه الأذان"})
        }
        HorizontalDivider(color=c.border)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text("تذكير الإقامة",color=c.text);Text("إشعار مرئي دون صوت أو اهتزاز",color=c.secondary,fontSize=12.sp)}
            Switch(p.iqamaNotifications,{onSave(p.copy(iqamaNotifications=it))},modifier=Modifier.semantics {contentDescription="تذكير الإقامة"})
        }
        Text("التنبيهات مستقلة عن الصمت التلقائي، ولا تشمل الشروق.",color=c.secondary,fontSize=12.sp)
        Text("عند تفعيلها قد يعرض الهاتف رمز المنبّه وموعد تنبيه الصلاة التالي.",color=c.secondary,fontSize=12.sp)
    }
    SettingsCard("صلاة الجمعة","تذكير أسبوعي قبل وقت الظهر المحسوب في مدينتك") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("تذكير يوم الجمعة",color=c.text,modifier=Modifier.weight(1f))
            Switch(p.fridayReminder,{onSave(p.copy(fridayReminder=it))},modifier=Modifier.semantics {contentDescription="تذكير يوم الجمعة"})
        }
        if(p.fridayReminder) {
            Text("وقت التذكير قبل الظهر",color=c.text)
            val leads=(listOf(5,15,DEFAULT_FRIDAY_LEAD_MINUTES,45,60)+p.fridayLeadMinutes).distinct().sorted()
            OptionGrid(leads,p.fridayLeadMinutes,{"$it دقيقة"},3) {onSave(p.copy(fridayLeadMinutes=it))}
        }
        Text("وقت الظهر تقديري، وقد يختلف موعد الخطبة والصلاة حسب المسجد.",color=c.secondary,fontSize=12.sp)
    }
    PrayerDurations("الإقامة بعد الأذان","حدّد المدة لكل صلاة حسب مسجدك. هذه تذكيرات حسب إعدادك وليست مواقيت إقامة معتمدة.",p.iqamaMinutes,defaultIqamaMinutes(),1) {onSave(p.copy(iqamaMinutes=it))}
}
