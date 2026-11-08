package org.najwa.app.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AdhanSound(val channelId: String,val name: String,val uri: String?=null,val fileName: String?=null,val error: String?=null)

/** Imported audio is private and survives renaming/deleting the original document.
 * Only the dedicated audio directory can be shared with Android's notification player. */
class AdhanSounds(private val context: Context,private val validate: (File)->Unit=::validateAudio) {
    companion object { const val UNAVAILABLE_CHANNEL="adhan_selection_unavailable" }
    private val store=JsonFile(context,"adhan-sound.json")
    private val manager=context.getSystemService(NotificationManager::class.java)
    fun selected(): AdhanSound {
        return try {
            val j=store.read() ?: return AdhanSound(PrayerNotifications.ADHAN,"صوت الأذان المحدد في الهاتف")
            val channel=j.getString("channel")
            val name=j.getString("name")
            require(channel==PrayerNotifications.ADHAN || channel.matches(Regex("adhan_selected_[a-f0-9-]+")))
            require(name.isNotBlank())
            val uri=j.optString("uri").takeIf {it.isNotEmpty()}
            val file=j.optString("file").takeIf {it.isNotEmpty()}
            require(file==null || file.matches(Regex("[a-f0-9-]+\\.(audio|mp3|m4a|wav|ogg|aac|flac|opus|amr|3gp|mp4)")))
            AdhanSound(channel,name,uri,file)
        } catch(_: Exception) {
            // Keep the settings screen usable and do not silently change a user's
            // selected sound to an audible default after corrupted local storage.
            AdhanSound(UNAVAILABLE_CHANNEL,"الصوت المحفوظ يحتاج إلى إعادة اختيار",error="تعذّر قراءة اختيار الصوت المحفوظ. اختر ملفًا من جديد أو اضغط العودة إلى نغمة المنبّه الافتراضية. يبقى تنبيه الأذان الصوتي متوقفًا حتى تختار.")
        }
    }
    fun importAudio(uri: Uri): AdhanSound {
        require(uri.scheme=="content") {"اختر ملفًا من تطبيق الملفات."}
        val type=context.contentResolver.getType(uri)
        require(type==null || type.startsWith("audio/") || type=="application/ogg" || type=="application/octet-stream") {"الملف المختار ليس ملفًا صوتيًا."}
        val name=runCatching {context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0) else null}}.getOrNull()?.take(120) ?: "أذان من الهاتف"
        val directory=File(context.filesDir,"adhan").apply {check(isDirectory || mkdirs()) {"تعذّر حفظ ملف الأذان."}}
        val formats=setOf("mp3","m4a","wav","ogg","aac","flac","opus","amr","3gp","mp4")
        val extension=name.substringAfterLast('.',"").lowercase().takeIf {it in formats}
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.takeIf {it in formats} ?: "audio"
        val file=File(directory,"${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(uri)?.use {input->
                file.outputStream().use {output->
                    val buffer=ByteArray(8192);var total=0L
                    while(true) {val n=input.read(buffer);if(n<0)break;total+=n;require(total<=32L*1024*1024) {"اختر ملفًا أصغر من 32 ميغابايت."};output.write(buffer,0,n)}
                    require(total>0) {"الملف الصوتي فارغ."}
                }
            } ?: error("تعذّر قراءة الملف المختار.")
            validate(file)
            val soundUri=FileProvider.getUriForFile(context,"${context.packageName}.adhan.files",file)
            return activate(name,soundUri,file.name)
        } catch(e: Exception) {file.delete();throw e}
    }
    fun reset(): AdhanSound = activate("نغمة المنبّه الافتراضية",RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),null)
    private fun activate(name: String,uri: Uri,fileName: String?): AdhanSound {
        val previous=selected()
        val old=manager.getNotificationChannel(previous.channelId)
        check(old?.importance!=NotificationManager.IMPORTANCE_NONE) {"قناة الأذان متوقفة في الهاتف. فعّلها من إعدادات إشعارات الأذان ثم اختر الصوت."}
        // Android freezes a channel's sound after creation. A user's explicit new
        // selection gets a new channel while keeping the current visibility settings.
        val id="adhan_selected_${UUID.randomUUID()}"
        val channel=NotificationChannel(id,"تنبيه الأذان",old?.importance ?: NotificationManager.IMPORTANCE_HIGH).apply {
            description="صوت الأذان: $name"
            setSound(uri,AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            enableVibration(old?.shouldVibrate() ?: false)
            old?.let {
                vibrationPattern=it.vibrationPattern;enableVibration(it.shouldVibrate());enableLights(it.shouldShowLights());lightColor=it.lightColor;setShowBadge(it.canShowBadge());lockscreenVisibility=it.lockscreenVisibility
                if(manager.isNotificationPolicyAccessGranted)setBypassDnd(it.canBypassDnd())
            }
        }
        manager.createNotificationChannel(channel)
        val choice=AdhanSound(id,name,uri.toString(),fileName)
        try {store.write(JSONObject().put("channel",id).put("name",name).put("uri",uri.toString()).put("file",fileName ?: ""))}
        catch(e: Exception) {manager.deleteNotificationChannel(id);throw e}
        // Keep the legacy channel so upgrades never reset a user's old channel controls.
        runCatching {if(previous.channelId.startsWith("adhan_selected_"))manager.deleteNotificationChannel(previous.channelId)}
        runCatching {previous.fileName?.takeIf {it.matches(Regex("[a-f0-9-]+\\.(audio|mp3|m4a|wav|ogg|aac|flac|opus|amr|3gp|mp4)"))}?.let {File(context.filesDir,"adhan/$it").delete()}}
        return choice
    }
}

private fun validateAudio(file: File) {
    val metadata=MediaMetadataRetriever()
    try {
        metadata.setDataSource(file.absolutePath)
        val duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val audio=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        require(duration>0 && audio=="yes") {"تعذّر تشغيل الملف. اختر تسجيلًا صوتيًا صالحًا مثل MP3 أو M4A أو WAV."}
    } catch(e: IllegalArgumentException) {throw e}
    catch(e: Exception) {throw IllegalArgumentException("تعذّر قراءة الصوت. جرّب ملف MP3 أو M4A أو WAV آخر.",e)}
    finally {metadata.release()}
}
