package org.najwa.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.app.NajwaRuntime
import org.najwa.app.platform.openSystemSettings
import org.najwa.core.City

@Composable fun SectionTitle(text: String) { Text(text,color=LocalPalette.current.secondary,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=10.dp)) }
@Composable fun PermissionsScreen(runtime: NajwaRuntime,onDone: ()->Unit,onSkip: ()->Unit,onBack: ()->Unit=onDone,firstRun: Boolean=false,
    city: City?=null,cityConfirmed: Boolean=true,onChooseCity: ()->Unit={},onConfirmCity: ()->Unit={},
    wantsSilence: Boolean=false,onWantsSilence: (Boolean)->Unit={}) {
    val context=LocalContext.current
    var error by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    var askedOnFirstRun by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val request=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {runtime.reconcile();revision++}
    fun notifications() {
        val permission=android.Manifest.permission.POST_NOTIFICATIONS
        val rationale=(context as? android.app.Activity)?.shouldShowRequestPermissionRationale(permission)==true
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED && (!askedOnFirstRun || rationale)) {
            askedOnFirstRun=true
            request.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if(!runtime.notifications.granted)
            openSystemSettings(context,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    }
    val notificationGranted=remember(revision,runtime.notifications.granted) {runtime.notifications.granted}
    PermissionsContent(runtime.audio.policyAccess,runtime.alarms.exactAccess,runtime.audio.fixed,error,
        onOpen={index->
            val intent=if(index==0)Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                else if(Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}"))
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
            if(!openSystemSettings(context,intent))error=context.getString(R.string.en_setup_system_error)
        },onDone,onSkip,onBack,firstRun,notificationGranted,::notifications,city,cityConfirmed,onChooseCity,onConfirmCity,wantsSilence,onWantsSilence)
}
@Composable fun PermissionsContent(policy: Boolean,exact: Boolean,fixed: Boolean,error: String?,onOpen: (Int)->Unit,onDone: ()->Unit,onSkip: ()->Unit,onBack: ()->Unit,firstRun: Boolean=false,notificationsGranted: Boolean=true,onNotifications: ()->Unit={},
    city: City?=null,cityConfirmed: Boolean=true,onChooseCity: ()->Unit={},onConfirmCity: ()->Unit={},
    wantsSilence: Boolean=false,onWantsSilence: (Boolean)->Unit={}) {
    val c=LocalPalette.current
    if(!firstRun)TextButton(onClick=onBack) {Text(stringResource(R.string.en_setup_back_silence))}
    Spacer(Modifier.height(30.dp))
    Text(if(firstRun)stringResource(R.string.en_setup_welcome) else stringResource(R.string.en_setup_required),fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=c.text)
    if(firstRun && city!=null) {
        SettingsCard(stringResource(R.string.en_setup_city), stringResource(R.string.en_setup_city_help)) {
            Text(city.name,color=c.text,fontWeight=FontWeight.Bold)
            Text(city.zone,color=c.secondary)
            if(!cityConfirmed)Action(stringResource(R.string.en_setup_confirm_city,city.name),Modifier.fillMaxWidth(),true,onConfirmCity)
            Action(stringResource(R.string.en_setup_choose_city),Modifier.fillMaxWidth(),onClick=onChooseCity)
        }
        if(!cityConfirmed) {
            Action(stringResource(R.string.en_setup_skip),Modifier.fillMaxWidth(),onClick=onSkip)
            return
        }
    }
    if(firstRun && !fixed) {
        PreferenceSwitch(stringResource(R.string.en_setup_silent_option),stringResource(R.string.en_setup_silent_option_help),
            wantsSilence,onWantsSilence)
        Spacer(Modifier.height(16.dp))
    }
    val needsPolicy=(!firstRun || wantsSilence) && !policy && !fixed
    val step=when {
        firstRun && !notificationsGranted -> 2
        !firstRun && needsPolicy -> 0
        !exact && (firstRun || !fixed) -> 1
        needsPolicy -> 0
        else -> null
    }
    error?.let {Text(it,color=c.amber)}
    if(fixed && !firstRun)Text(stringResource(R.string.en_setup_fixed_volume),color=c.amber)
    val setupLabel=when {
        firstRun && wantsSilence && notificationsGranted && exact && policy && !fixed -> stringResource(R.string.en_setup_done_both)
        firstRun && notificationsGranted && exact -> stringResource(R.string.en_setup_done_alerts)
        firstRun && wantsSilence && exact && policy && !fixed -> stringResource(R.string.en_setup_done_silence)
        firstRun -> stringResource(R.string.en_setup_done)
        policy && exact && !fixed -> stringResource(R.string.en_setup_done_silence)
        else -> stringResource(R.string.en_setup_back_silence)
    }
    if(step!=null) {
        val (title,help,action)=when(step) {
            2 -> Triple(R.string.en_setup_notifications,R.string.en_setup_notifications_help,R.string.en_setup_allow_notifications)
            1 -> Triple(R.string.en_setup_exact,R.string.en_setup_exact_help,R.string.en_setup_open_exact)
            else -> Triple(R.string.en_setup_policy,R.string.en_setup_policy_help,R.string.en_setup_open_policy)
        }
        SettingsCard(stringResource(title),stringResource(help)) {
            Action(stringResource(action),Modifier.fillMaxWidth(),true) {
                if(step==2)onNotifications() else onOpen(step)
            }
        }
        if(firstRun && exact && (notificationsGranted || wantsSilence && policy && !fixed))
            Action(setupLabel,Modifier.fillMaxWidth(),onClick=onDone)
    } else Action(setupLabel,Modifier.fillMaxWidth(),true,onDone)
    Spacer(Modifier.height(10.dp));Action(stringResource(R.string.en_setup_skip),Modifier.fillMaxWidth(),onClick=onSkip)
}
