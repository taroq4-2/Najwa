package org.najwa.app.ui

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.NajwaRuntime
import org.najwa.app.R

@Composable fun AdhanSoundSettings(runtime: NajwaRuntime,open: (Intent)->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var refresh by remember {mutableIntStateOf(0)}
    val choice=remember(refresh) {runtime.notifications.sounds.selected()}
    val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(choice.channelId)
    val name=when {
        choice.error!=null->context.getString(R.string.sound_unavailable_title)
        channel!=null && channel.sound==null->context.getString(R.string.sound_phone_silent)
        choice.uri!=null && channel?.sound!=null && channel.sound.toString()!=choice.uri->context.getString(R.string.sound_phone_changed)
        choice.fileName==null->context.getString(if(choice.uri==null)R.string.sound_phone_default else R.string.sound_alarm_default)
        else->choice.name
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) {
            busy=true;message=null
            scope.launch {
                try {
                    val imported=withContext(Dispatchers.IO) {runtime.notifications.sounds.importAudio(uri)}
                    refresh++;message=context.getString(R.string.sound_saved,imported.name)
                } catch(e: Exception) {message=context.getString(R.string.sound_import_failed)}
                finally {busy=false}
            }
        }
    }
    // Collapsed by default: the current sound is visible in the header, details on demand.
    ExpandableSettings("صوت الأذان",name,initiallyExpanded=choice.error!=null) {
    AdhanSoundContent(name,choice.fileName!=null || choice.error!=null,busy,message ?: choice.error?.let {context.getString(R.string.sound_unavailable_detail)},framed=false,
        onChoose={try {picker.launch(arrayOf("audio/*","application/ogg"))} catch(_: Exception) {message=context.getString(R.string.sound_picker_failed)}},
        onReset={
            busy=true
            scope.launch {
                try {withContext(Dispatchers.IO) {runtime.notifications.sounds.reset()};refresh++;message=context.getString(R.string.sound_reset_done)}
                catch(e: Exception) {message=context.getString(R.string.sound_reset_failed)}
                finally {busy=false}
            }
        },
        onSystem={open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,runtime.notifications.adhanChannel))})
    }
}

@Composable fun AdhanSoundContent(name: String,custom: Boolean,busy: Boolean,message: String?,onChoose: ()->Unit,onReset: ()->Unit,onSystem: ()->Unit) =
    AdhanSoundContent(name,custom,busy,message,true,onChoose,onReset,onSystem)

@Composable fun AdhanSoundContent(name: String,custom: Boolean,busy: Boolean,message: String?,framed: Boolean,onChoose: ()->Unit,onReset: ()->Unit,onSystem: ()->Unit) {
    val c=LocalPalette.current
    val frame: @Composable (@Composable ColumnScope.()->Unit)->Unit = {body->
        if(framed)SettingsCard("صوت الأذان","اختر تسجيلًا من ملفات هاتفك",body)
        else Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=body)
    }
    frame {
        if(framed)Text(name,color=c.mint,fontSize=15.sp)
        else Text(uiText("اختر تسجيلًا من ملفات هاتفك"),color=c.secondary,fontSize=13.sp)
        if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(uiText("جارٍ فحص الملف وحفظه…"),color=c.secondary)}
        else {
            Action("اختيار ملف أذان من الهاتف",Modifier.fillMaxWidth(),true,onChoose)
            if(custom)TextButton(onClick=onReset) {Text(uiText("العودة إلى نغمة المنبّه الافتراضية"))}
            TextButton(onClick=onSystem) {Text(uiText("إعدادات صوت الأذان في الهاتف"))}
        }
        Text(uiText("MP3 أو M4A أو WAV وغيرها من الصيغ التي يدعمها هاتفك، بحد أقصى 32 ميغابايت. تُحفظ نسخة داخل نجوى لتبقى متاحة بعد نقل الملف الأصلي."),color=c.secondary,fontSize=12.sp)
        Text(uiText("يستخدم التنبيه مستوى صوت المنبّه. مدة تشغيل الصوت وعدم الإزعاج تخضع لإعدادات Android."),color=c.secondary,fontSize=12.sp)
        Text(uiText("اختيار الصوت لا يفعّل التنبيهات تلقائيًا؛ تأكد من تشغيل «تنبيه الأذان» أعلى الصفحة."),color=c.secondary,fontSize=12.sp)
        message?.let {Text(it,color=c.amber,fontSize=13.sp)}
    }
}
