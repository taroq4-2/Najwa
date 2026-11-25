package org.najwa.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.platform.AdhanSounds

/** Which adhan sound the phone will actually play. */
private sealed interface SoundChoice {
    data class BuiltIn(val index: Int): SoundChoice
    data object Alarm: SoundChoice
    data object Custom: SoundChoice
    data object Silent: SoundChoice
    data object Unavailable: SoundChoice
}

@Composable fun AdhanSoundSettings(runtime: NajwaRuntime,open: (Intent)->Unit,fajr: Boolean=false) {
    val context=LocalContext.current
    val c=LocalPalette.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var refresh by remember {mutableIntStateOf(0)}
    val sounds=if(fajr)runtime.notifications.fajrSounds else runtime.notifications.sounds
    val choice=remember(refresh) {sounds.selected()}
    val channel=remember(refresh) {context.getSystemService(NotificationManager::class.java).getNotificationChannel(choice.channelId)}
    val current: SoundChoice=when {
        choice.error!=null -> SoundChoice.Unavailable
        channel!=null && channel.sound==null -> SoundChoice.Silent
        else -> AdhanSounds.builtInIndex(channel?.sound?.toString() ?: choice.uri)?.let {SoundChoice.BuiltIn(it)}
            ?: if(choice.fileName!=null)SoundChoice.Custom else SoundChoice.Alarm
    }
    val adhanNames=AdhanSounds.BUILT_IN.associateWith {stringResource(if(it==1)R.string.sound_adhan_1 else R.string.sound_adhan_2)}
    val name=when(current) {
        is SoundChoice.BuiltIn -> adhanNames.getValue(current.index)
        SoundChoice.Custom -> choice.name
        SoundChoice.Silent -> stringResource(R.string.sound_phone_silent)
        SoundChoice.Unavailable -> stringResource(R.string.sound_unavailable_title)
        SoundChoice.Alarm -> stringResource(R.string.sound_alarm_default)
    }
    // Preview plays through the media volume and stops when the screen closes.
    var previewing by remember {mutableStateOf<Int?>(null)}
    val player=remember {arrayOfNulls<MediaPlayer>(1)}
    fun stopPreview() {player[0]?.runCatching {stop();release()};player[0]=null;previewing=null}
    DisposableEffect(Unit) {onDispose {stopPreview()}}
    fun preview(index: Int) {
        if(previewing==index) {stopPreview();return}
        stopPreview()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(context,AdhanSounds.builtInUri(context,index))
                setOnCompletionListener {stopPreview()}
                prepare();start()
            }
        }.onSuccess {player[0]=it;previewing=index}.onFailure {message=context.getString(R.string.sound_preview_failed)}
    }
    fun change(done: Int,failed: Int,action: ()->Unit) {
        busy=true;message=null;stopPreview()
        scope.launch {
            try {withContext(Dispatchers.IO) {action()};refresh++;message=context.getString(done)}
            catch(_: Exception) {message=context.getString(failed)}
            finally {busy=false}
        }
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri: Uri?->
        if(uri!=null) {
            busy=true;message=null;stopPreview()
            scope.launch {
                try {
                    val imported=withContext(Dispatchers.IO) {sounds.importAudio(uri)}
                    refresh++;message=context.getString(R.string.sound_saved,imported.name)
                } catch(_: Exception) {message=context.getString(R.string.sound_import_failed)}
                finally {busy=false}
            }
        }
    }
    ExpandableSettings(stringResource(if(fajr)R.string.fajr_adhan_title else R.string.sound_title),name,initiallyExpanded=choice.error!=null || fajr) {
        Text(stringResource(R.string.sound_choose_help),color=c.secondary,fontSize=13.sp)
        AdhanSounds.BUILT_IN.forEach {index->
            SoundRow(adhanNames.getValue(index),stringResource(if(index==1)R.string.sound_adhan_1_detail else R.string.sound_adhan_2_detail),
                current==SoundChoice.BuiltIn(index),!busy,
                preview=if(previewing==index)stringResource(R.string.sound_preview_stop) else stringResource(R.string.sound_preview),
                onPreview={preview(index)}) {change(R.string.sound_selected,R.string.sound_reset_failed) {sounds.builtIn(index)}}
        }
        SoundRow(stringResource(R.string.sound_alarm_default),stringResource(R.string.sound_alarm_detail),current==SoundChoice.Alarm,!busy) {
            change(R.string.sound_reset_done,R.string.sound_reset_failed) {sounds.reset()}
        }
        SoundRow(if(current==SoundChoice.Custom)choice.name else stringResource(R.string.sound_from_phone),stringResource(R.string.sound_from_phone_detail),
            current==SoundChoice.Custom,!busy) {
            try {picker.launch(arrayOf("audio/*","application/ogg"))} catch(_: Exception) {message=context.getString(R.string.sound_picker_failed)}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let {Text(it,color=c.mint,fontSize=13.sp)}
        if(current==SoundChoice.Unavailable)Text(stringResource(R.string.sound_unavailable_detail),color=c.amber,fontSize=13.sp)
        TextButton(onClick={open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID,choice.channelId))}) {Text(stringResource(R.string.sound_system_settings))}
        Text(stringResource(R.string.sound_volume_note),color=c.secondary,fontSize=12.sp)
    }
}

@Composable private fun SoundRow(title: String,detail: String,selected: Boolean,enabled: Boolean,preview: String?=null,onPreview: ()->Unit={},onSelect: ()->Unit) {
    val c=LocalPalette.current
    Surface(onClick=onSelect,enabled=enabled,modifier=Modifier.fillMaxWidth().semantics {this.selected=selected},shape=RoundedCornerShape(16.dp),
        color=if(selected)c.selected else c.surface,border=BorderStroke(1.dp,if(selected)c.green else c.border)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            RadioButton(selected=selected,onClick=onSelect,enabled=enabled)
            Column(Modifier.weight(1f)) {
                Text(title,color=if(selected)c.mint else c.text,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,fontSize=15.sp)
                Text(detail,color=c.secondary,fontSize=12.sp)
            }
            if(preview!=null)TextButton(onClick=onPreview,enabled=enabled) {Text(preview,fontSize=13.sp)}
        }
    }
}
