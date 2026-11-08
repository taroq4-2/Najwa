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

@Composable fun AdhanSoundSettings(runtime: NajwaRuntime,open: (Intent)->Unit) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    var refresh by remember {mutableIntStateOf(0)}
    val choice=remember(refresh) {runtime.notifications.sounds.selected()}
    val channel=context.getSystemService(NotificationManager::class.java).getNotificationChannel(choice.channelId)
    val name=when {
        choice.error!=null->choice.name
        channel?.sound==null->"بدون صوت حسب إعدادات الهاتف"
        choice.uri!=null && channel.sound.toString()!=choice.uri->"صوت معدّل من إعدادات الهاتف"
        else->choice.name
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null) {
            busy=true;message=null
            scope.launch {
                try {
                    val imported=withContext(Dispatchers.IO) {runtime.notifications.sounds.importAudio(uri)}
                    refresh++;message="تم حفظ ${imported.name}"
                } catch(e: Exception) {message=e.message ?: "تعذّر حفظ الصوت. اختر ملفًا آخر."}
                finally {busy=false}
            }
        }
    }
    AdhanSoundContent(name,choice.fileName!=null || choice.error!=null,busy,message ?: choice.error,
        onChoose={try {picker.launch(arrayOf("audio/*","application/ogg"))} catch(_: Exception) {message="تعذّر فتح تطبيق الملفات. تأكد من توفر مدير ملفات على هاتفك."}},
        onReset={
            busy=true
            scope.launch {
                try {withContext(Dispatchers.IO) {runtime.notifications.sounds.reset()};refresh++;message="أُعيد صوت المنبّه الافتراضي."}
                catch(e: Exception) {message=e.message ?: "تعذّر تغيير الصوت."}
                finally {busy=false}
            }
        },
        onSystem={open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,runtime.notifications.adhanChannel))})
}

@Composable fun AdhanSoundContent(name: String,custom: Boolean,busy: Boolean,message: String?,onChoose: ()->Unit,onReset: ()->Unit,onSystem: ()->Unit) {
    val c=LocalPalette.current
    SettingsCard("صوت الأذان","اختر تسجيلًا من ملفات هاتفك") {
        Text(name,color=c.mint,fontSize=15.sp)
        if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Text("جارٍ فحص الملف وحفظه…",color=c.secondary)}
        else {
            Action("اختيار ملف أذان من الهاتف",Modifier.fillMaxWidth(),true,onChoose)
            if(custom)TextButton(onClick=onReset) {Text("العودة إلى نغمة المنبّه الافتراضية")}
            TextButton(onClick=onSystem) {Text("إعدادات صوت الأذان في الهاتف")}
        }
        Text("MP3 أو M4A أو WAV وغيرها من الصيغ التي يدعمها هاتفك، بحد أقصى 32 ميغابايت. تُحفظ نسخة داخل نجوى لتبقى متاحة بعد نقل الملف الأصلي.",color=c.secondary,fontSize=12.sp)
        Text("يستخدم التنبيه مستوى صوت المنبّه. مدة تشغيل الصوت وعدم الإزعاج تخضع لإعدادات Android.",color=c.secondary,fontSize=12.sp)
        Text("اختيار الصوت لا يفعّل التنبيهات تلقائيًا؛ تأكد من تشغيل «تنبيه الأذان» أعلى الصفحة.",color=c.secondary,fontSize=12.sp)
        message?.let {Text(it,color=c.amber,fontSize=13.sp)}
    }
}
