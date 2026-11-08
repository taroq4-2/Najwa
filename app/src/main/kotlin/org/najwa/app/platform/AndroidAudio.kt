package org.najwa.app.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import org.najwa.core.Ringer
import org.najwa.core.RingerPort

class AndroidAudio(context: Context): RingerPort {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val notifications=context.getSystemService(NotificationManager::class.java)
    val policyAccess get() = runCatching {notifications?.isNotificationPolicyAccessGranted == true}.getOrDefault(false)
    val fixed get() = runCatching {audio?.isVolumeFixed != false}.getOrDefault(true)
    override fun capable() = policyAccess && !fixed
    override fun current() = when(audio.ringerMode) { AudioManager.RINGER_MODE_SILENT->Ringer.SILENT; AudioManager.RINGER_MODE_VIBRATE->Ringer.VIBRATE; else->Ringer.NORMAL }
    override fun set(mode: Ringer) {
        check(capable()) { "ringer_access_unavailable" }
        audio.ringerMode=when(mode) { Ringer.SILENT->AudioManager.RINGER_MODE_SILENT; Ringer.VIBRATE->AudioManager.RINGER_MODE_VIBRATE; Ringer.NORMAL->AudioManager.RINGER_MODE_NORMAL }
    }
}
