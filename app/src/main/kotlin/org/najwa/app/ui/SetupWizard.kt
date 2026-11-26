package org.najwa.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.platform.openSystemSettings
import org.najwa.core.Method
import org.najwa.core.Preferences

/** What the person chose in the wizard; applied once at the end. */
data class SetupChoices(val adhan: Boolean,val iqama: Boolean,val friday: Boolean,val silence: Boolean)

private const val STEPS=4

/**
 * First-run setup in four short steps: place → features → permissions → summary.
 * Every choice here is also available (in more detail) in Settings; "Advanced
 * customization" finishes setup and opens Settings directly.
 */
@Composable fun SetupWizard(runtime: NajwaRuntime,prefs: Preferences,cityConfirmed: Boolean,
    onChooseCity: ()->Unit,onConfirmCity: ()->Unit,onSave: (Preferences)->Unit,
    onFinish: (SetupChoices,Boolean)->Unit,onSkip: ()->Unit) {
    val context=LocalContext.current
    val c=LocalPalette.current
    var step by rememberSaveable {mutableIntStateOf(0)}
    var adhan by rememberSaveable {mutableStateOf(true)}
    var iqama by rememberSaveable {mutableStateOf(true)}
    var friday by rememberSaveable {mutableStateOf(true)}
    var silence by rememberSaveable {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    var asked by rememberSaveable {mutableStateOf(false)}
    val request=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        org.najwa.app.prayerExecutor.execute {runtime.reconcile()};revision++
    }
    fun open(intent: Intent) {if(!openSystemSettings(context,intent))error=context.getString(R.string.en_setup_system_error)}
    fun askNotifications() {
        val permission=android.Manifest.permission.POST_NOTIFICATIONS
        val rationale=(context as? android.app.Activity)?.shouldShowRequestPermissionRationale(permission)==true
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED && (!asked || rationale)) {
            asked=true;request.launch(permission)
        } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    }
    // Read live each composition: the page recomposes every second and on resume.
    val notificationsOk=revision.let {runtime.notifications.granted}
    val exactOk=runtime.alarms.exactAccess
    val policyOk=runtime.audio.policyAccess
    val fixed=runtime.audio.fixed
    val alerts=adhan || iqama || friday

    StepHeader(step)
    when(step) {
        0 -> {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).background(c.selected,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {Mark(Modifier.size(36.dp))}
                Column(Modifier.weight(1f)) {
                    WizardTitle(stringResource(R.string.en_setup_welcome))
                    Text(stringResource(R.string.setup_tagline),color=c.secondary,fontSize=13.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            SettingsCard(stringResource(R.string.en_setup_city),stringResource(R.string.en_setup_city_help)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Glyph("pin",c.mint)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prefs.city.name,color=c.text,fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text(prefs.city.zone,color=c.secondary,fontSize=12.sp)
                    }
                    TextButton(onClick=onChooseCity) {Text(stringResource(R.string.setup_change))}
                }
                Text(uiText("طريقة الحساب"),color=c.secondary,fontSize=13.sp)
                val methods=Method.entries.associateWith {localizedMethodName(it)}
                Choice(prefs.method,Method.entries.toList(),methods::getValue) {onSave(prefs.copy(method=it))}
                Text(uiText("حساب العصر"),color=c.secondary,fontSize=13.sp)
                Choice(prefs.hanafi,listOf(false,true),{if(it)"حنفي · مثلي الظل" else "قياسي · مثل الظل"}) {onSave(prefs.copy(hanafi=it))}
            }
            Action(stringResource(R.string.setup_continue),Modifier.fillMaxWidth(),true) {
                if(!cityConfirmed)onConfirmCity()
                step=1
            }
        }
        1 -> {
            WizardTitle(stringResource(R.string.setup_features_title))
            Text(stringResource(R.string.setup_features_help),color=c.secondary,fontSize=13.sp,modifier=Modifier.padding(top=6.dp,bottom=18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    FeatureRow("bell",uiText("تنبيه الأذان"),uiText("إشعار مع صوت للصلوات المختارة"),adhan) {adhan=it}
                    FeatureRow("dot",uiText("تذكير الإقامة"),uiText("إشعار مرئي دون صوت أو اهتزاز"),iqama) {iqama=it}
                    FeatureRow("check",uiText("تذكير يوم الجمعة"),stringResource(R.string.alerts_friday_subtitle),friday) {friday=it}
                    FeatureRow("sound",stringResource(R.string.settings_quick_silence),
                        if(fixed)stringResource(R.string.en_setup_fixed_volume) else stringResource(R.string.settings_quick_silence_help),
                        silence && !fixed,enabled=!fixed) {silence=it}
                }
            }
            Spacer(Modifier.height(16.dp))
            NavigationButtons(onBack={step=0}) {step=2}
        }
        2 -> {
            WizardTitle(stringResource(R.string.setup_permissions_title))
            Text(stringResource(R.string.setup_permissions_help),color=c.secondary,fontSize=13.sp,modifier=Modifier.padding(top=6.dp,bottom=18.dp))
            val needed=buildList {
                if(alerts)add(PermissionItem(R.string.en_setup_notifications,R.string.en_setup_notifications_help,R.string.en_setup_allow_notifications,notificationsOk) {askNotifications()})
                if(alerts || silence)add(PermissionItem(R.string.en_setup_exact,R.string.en_setup_exact_help,R.string.en_setup_open_exact,exactOk) {
                    open(if(Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}"))
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
                })
                if(silence && !fixed)add(PermissionItem(R.string.en_setup_policy,R.string.en_setup_policy_help,R.string.en_setup_open_policy,policyOk) {
                    open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                })
            }
            if(needed.isEmpty())Text(stringResource(R.string.setup_permissions_none),color=c.text)
            needed.forEach {PermissionRow(it)}
            if(needed.any {!it.granted})Text(stringResource(R.string.setup_permissions_pending),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(bottom=12.dp))
            error?.let {Text(it,color=c.amber,fontSize=13.sp)}
            NavigationButtons(onBack={step=1}) {step=3}
        }
        else -> {
            WizardTitle(stringResource(R.string.setup_done_title))
            Text(stringResource(R.string.setup_done_help),color=c.secondary,fontSize=13.sp,modifier=Modifier.padding(top=6.dp,bottom=18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SummaryRow("pin",prefs.city.name,localizedMethodName(prefs.method),true)
                    val alertsReady=notificationsOk && exactOk
                    if(adhan)SummaryRow("bell",uiText("تنبيه الأذان"),null,alertsReady)
                    if(iqama)SummaryRow("dot",uiText("تذكير الإقامة"),null,alertsReady)
                    if(friday)SummaryRow("check",uiText("تذكير يوم الجمعة"),null,alertsReady)
                    if(silence && !fixed)SummaryRow("sound",stringResource(R.string.settings_quick_silence),null,policyOk && exactOk)
                }
            }
            // The Quran portion is chosen here too, so the worship companion starts from the person's own plan.
            Spacer(Modifier.height(18.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setup_plan_title),color=c.text,fontSize=16.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(stringResource(R.string.setup_plan_help),color=c.secondary,fontSize=12.sp)
                    val planGoal=remember {org.najwa.app.platform.QuranStore.read(context).goal}
                    KhatmaPlanPicker(planGoal,appPlanColors()) {}
                }
            }
            Spacer(Modifier.height(18.dp))
            Action(stringResource(R.string.setup_start),Modifier.fillMaxWidth(),true) {onFinish(SetupChoices(adhan,iqama,friday,silence && !fixed),false)}
            Spacer(Modifier.height(8.dp))
            Action(stringResource(R.string.setup_advanced),Modifier.fillMaxWidth()) {onFinish(SetupChoices(adhan,iqama,friday,silence && !fixed),true)}
            TextButton(onClick={step=2},modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.setup_back))}
        }
    }
    if(step==0) {
        Spacer(Modifier.height(6.dp))
        TextButton(onClick=onSkip,modifier=Modifier.fillMaxWidth()) {Text(stringResource(R.string.en_setup_skip),color=c.secondary)}
    }
}

private data class PermissionItem(val title: Int,val help: Int,val action: Int,val granted: Boolean,val onGrant: ()->Unit)

@Composable private fun StepHeader(step: Int) {
    val c=LocalPalette.current
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) {i->
            Box(Modifier.weight(1f).height(5.dp).background(if(i<=step)c.green else c.track,RoundedCornerShape(3.dp)))
        }
    }
    Text(stringResource(R.string.setup_step,step+1,STEPS),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(top=8.dp,bottom=18.dp))
}

@Composable private fun WizardTitle(text: String) {
    Text(text,color=LocalPalette.current.text,fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.semantics {heading()})
}

@Composable private fun FeatureRow(icon: String,title: String,subtitle: String,checked: Boolean,enabled: Boolean=true,onChange: (Boolean)->Unit) {
    val c=LocalPalette.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(c.selected,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {Glyph(icon,c.mint)}
        Column(Modifier.weight(1f)) {
            Text(title,color=c.text,fontWeight=FontWeight.Bold,fontSize=15.sp)
            Text(subtitle,color=c.secondary,fontSize=12.sp)
        }
        Switch(checked,onChange,enabled=enabled,modifier=Modifier.semantics {contentDescription=title})
    }
}

@Composable private fun PermissionRow(item: PermissionItem) {
    val c=LocalPalette.current
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(item.title),Modifier.weight(1f),color=c.text,fontWeight=FontWeight.Bold,fontSize=15.sp)
                Text(stringResource(if(item.granted)R.string.en_setup_granted else R.string.en_setup_not_granted),
                    color=if(item.granted)c.mint else c.amber,fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
            Text(stringResource(item.help),color=c.secondary,fontSize=12.sp)
            if(!item.granted)Action(stringResource(item.action),Modifier.fillMaxWidth(),true,item.onGrant)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable private fun SummaryRow(icon: String,title: String,detail: String?,ready: Boolean) {
    val c=LocalPalette.current
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Glyph(icon,c.mint)
        Column(Modifier.weight(1f)) {
            Text(title,color=c.text,fontSize=15.sp)
            detail?.let {Text(it,color=c.secondary,fontSize=12.sp)}
        }
        Text(if(ready)"✓" else stringResource(R.string.setup_needs_permission),color=if(ready)c.mint else c.amber,fontSize=13.sp,fontWeight=FontWeight.Bold)
    }
}

@Composable private fun NavigationButtons(onBack: ()->Unit,onNext: ()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Action(stringResource(R.string.setup_back),Modifier.weight(1f),onClick=onBack)
        Action(stringResource(R.string.setup_next),Modifier.weight(2f),true,onNext)
    }
}
