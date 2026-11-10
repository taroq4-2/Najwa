package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.app.NajwaRuntime
import org.najwa.core.*
import java.time.Instant

@Composable fun SilenceScreen(runtime: NajwaRuntime,p: Preferences,s: Session?,now: Instant,onSave: (Preferences)->Unit,onAccess: ()->Unit,onRestore: ()->Unit) {
    SilenceContent(p,s,now,runtime.audio.capable() && runtime.alarms.exactAccess,runtime.audio.fixed,onSave,onAccess,onRestore)
}

@Composable fun SilenceContent(p: Preferences,s: Session?,now: Instant,ready: Boolean,fixed: Boolean,onSave: (Preferences)->Unit,onAccess: ()->Unit,onRestore: ()->Unit) {
    val c=LocalPalette.current
    val activationLabel=stringResource(R.string.en_silent_enable)
    PageHeading(stringResource(R.string.en_silent_title),stringResource(R.string.en_silent_intro))
    SettingsCard(stringResource(R.string.en_silent_activation)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(p.autoSilent)stringResource(R.string.en_silent_on) else stringResource(R.string.en_silent_enable),color=c.text,fontSize=16.sp)
                Text(when {fixed->stringResource(R.string.en_silent_fixed);!ready->stringResource(R.string.en_silent_permissions);p.enabled.isEmpty()->stringResource(R.string.en_silent_choose_prayer);else->stringResource(R.string.en_silent_timing)},color=c.secondary,fontSize=12.sp)
            }
            Switch(p.autoSilent,{if(it&&!ready)onAccess() else onSave(p.copy(autoSilent=it))},modifier=Modifier.semantics {contentDescription=activationLabel},enabled=!fixed,colors=silenceSwitchColors())
        }
        Text(stringResource(R.string.en_silent_alarm_icon),color=c.secondary,fontSize=12.sp)
    }
    if(s!=null && s.state !in listOf(SessionState.RESTORED,SessionState.USER_OVERRIDE,SessionState.IDLE)) {
        val active=s.state==SessionState.ACTIVE
        SettingsCard(if(active)stringResource(R.string.en_silent_active_session) else stringResource(R.string.en_silent_check_restore)) {
            if(active)CountdownDisplay(((s.deadline-now.toEpochMilli())/1000).coerceAtLeast(0),c.mint)
            Text(stringResource(R.string.en_silent_restore_info,localizedUiTime(Instant.ofEpochMilli(s.deadline),p),modeName(s.original)),color=c.secondary,fontSize=13.sp)
            if(!active)Text(stringResource(R.string.en_silent_check_phone),color=c.amber,fontSize=13.sp)
            Action(stringResource(R.string.en_silent_restore_now),Modifier.fillMaxWidth(),true,onRestore)
        }
    }
    PrayerDurations(stringResource(R.string.en_silent_prayers_title),stringResource(R.string.en_silent_prayers_help),p.silenceMinutes,defaultSilenceMinutes(),5,
        enabledPrayers=p.enabled,onEnabled={prayer,enabled->onSave(p.copy(enabled=if(enabled)p.enabled+prayer else p.enabled-prayer))}) {onSave(p.copy(silenceMinutes=it))}
    SettingsCard(stringResource(R.string.en_silent_phone_mode)) {
        val modes=listOf(Ringer.SILENT,Ringer.VIBRATE)
        val modeLabels=mapOf(Ringer.SILENT to modeName(Ringer.SILENT),Ringer.VIBRATE to modeName(Ringer.VIBRATE))
        OptionGrid(modes,p.mode,{modeLabels.getValue(it)},2) {onSave(p.copy(mode=it))}
        Text(stringResource(R.string.en_silent_no_audio_change),color=c.secondary,fontSize=12.sp)
    }
}
@Composable
private fun modeName(mode: Ringer)=stringResource(when(mode) {
    Ringer.SILENT->R.string.en_silent_mode_silent
    Ringer.VIBRATE->R.string.en_silent_mode_vibrate
    Ringer.NORMAL->R.string.en_silent_mode_normal
})
@Composable private fun silenceSwitchColors() = LocalPalette.current.let { c -> SwitchDefaults.colors(checkedThumbColor=c.bg,checkedTrackColor=c.green,uncheckedThumbColor=c.secondary,uncheckedTrackColor=c.track,uncheckedBorderColor=c.border) }
