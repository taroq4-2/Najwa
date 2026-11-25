package org.najwa.app.platform

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.najwa.core.Preferences
import java.util.Locale

/** Najwa's own adhan player (optional "enhanced adhan"): gentle fade-in, stops when the phone is
 * turned face down or a volume key is pressed, stays quiet during calls, can play only through
 * headphones, can announce the prayer and flash the torch. Runs as a short media foreground service. */
class AdhanPlayer: Service(),SensorEventListener {
    companion object {
        private const val ACTION_PLAY="org.najwa.adhan.PLAY"
        private const val ACTION_STOP="org.najwa.adhan.STOP"
        private const val MAX_MS=7*60_000L

        /** Returns false when Android refuses to start playback (the caller then uses the channel sound). */
        fun start(context: Context,uri: Uri,notificationId: Int,notification: Notification,p: Preferences,announcement: String?): Boolean {
            val audio=context.getSystemService(AudioManager::class.java)
            val inCall=audio.mode==AudioManager.MODE_IN_CALL || audio.mode==AudioManager.MODE_IN_COMMUNICATION
            val headphones=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {it.type in headphoneTypes}
            val silentPlayback=(p.quietDuringCalls && inCall) || (p.headphonesOnly && !headphones)
            val intent=Intent(context,AdhanPlayer::class.java).setAction(ACTION_PLAY)
                .putExtra("uri",uri.toString()).putExtra("id",notificationId).putExtra("notification",notification)
                .putExtra("fade",p.adhanFadeIn).putExtra("flip",p.flipToStop).putExtra("flash",p.flashAlert)
                .putExtra("silent",silentPlayback).putExtra("announce",announcement)
            context.startForegroundService(intent)
            return true
        }
        fun stop(context: Context) {
            runCatching {context.startService(Intent(context,AdhanPlayer::class.java).setAction(ACTION_STOP))}
        }
        private val headphoneTypes=buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES);add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if(Build.VERSION.SDK_INT>=31) {add(AudioDeviceInfo.TYPE_BLE_HEADSET)}
        }
    }

    private val handler=Handler(Looper.getMainLooper())
    private var player: MediaPlayer?=null
    private var session: MediaSession?=null
    private var tts: TextToSpeech?=null
    private var sensors: SensorManager?=null
    private var faceDown=0
    private var volume=1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action!=ACTION_PLAY) {finish();return START_NOT_STICKY}
        val notification=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra("notification",Notification::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra("notification")
        val id=intent.getIntExtra("id",0)
        if(notification==null) {stopSelf();return START_NOT_STICKY}
        if(Build.VERSION.SDK_INT>=29) startForeground(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(id,notification)
        release()
        handler.postDelayed({finish()},MAX_MS)
        if(intent.getBooleanExtra("flash",false))flashTorch()
        if(intent.getBooleanExtra("silent",false)) {
            // During a call (or without headphones when required): a short vibration instead of sound.
            getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,400,250,400,250,700),-1))
            handler.postDelayed({finish()},4_000)
            return START_NOT_STICKY
        }
        val uri=Uri.parse(intent.getStringExtra("uri") ?: run {finish();return START_NOT_STICKY})
        val fade=intent.getBooleanExtra("fade",true)
        if(intent.getBooleanExtra("flip",true))watchFlip()
        listenToVolumeKeys()
        val announce=intent.getStringExtra("announce")
        if(announce.isNullOrBlank()) play(uri,fade) else speak(announce) {play(uri,fade)}
        return START_NOT_STICKY
    }

    private fun play(uri: Uri,fade: Boolean) {
        runCatching {
            player=MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@AdhanPlayer,uri)
                setOnCompletionListener {finish()}
                setOnErrorListener {_,_,_->finish();true}
                volume=if(fade) .12f else 1f
                setVolume(volume,volume)
                prepare();start()
            }
            if(fade)rampUp()
        }.onFailure {finish()}
    }

    /** About ten seconds from a whisper to full alarm volume. */
    private fun rampUp() {
        handler.postDelayed(object: Runnable {
            override fun run() {
                val p=player ?: return
                volume=(volume+.06f).coerceAtMost(1f)
                runCatching {p.setVolume(volume,volume)}
                if(volume<1f)handler.postDelayed(this,600)
            }
        },600)
    }

    private fun speak(text: String,then: ()->Unit) {
        var done=false
        fun next() {if(!done) {done=true;handler.post(then)}}
        handler.postDelayed({next()},8_000)
        tts=TextToSpeech(this) {status->
            val engine=tts
            if(status!=TextToSpeech.SUCCESS || engine==null) {next();return@TextToSpeech}
            engine.language=resources.configuration.locales[0] ?: Locale("ar")
            engine.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            engine.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {next()}
                @Deprecated("Deprecated in Java") override fun onError(id: String?) {next()}
            })
            if(engine.speak(text,TextToSpeech.QUEUE_FLUSH,null,"adhan")!=TextToSpeech.SUCCESS)next()
        }
    }

    /** Pressing a volume key stops the adhan instead of changing the alarm volume. */
    private fun listenToVolumeKeys() {
        runCatching {
            session=MediaSession(this,"najwa-adhan").apply {
                // Android delivers volume and media-button events only to a session that has a callback.
                setCallback(object: MediaSession.Callback() {
                    override fun onMediaButtonEvent(intent: Intent): Boolean {handler.post {finish()};return true}
                    override fun onPause() {handler.post {finish()}}
                    override fun onStop() {handler.post {finish()}}
                },handler)
                setPlaybackToRemote(object: VolumeProvider(VOLUME_CONTROL_RELATIVE,1,1) {
                    override fun onAdjustVolume(direction: Int) {if(direction!=0)handler.post {finish()}}
                })
                setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
                isActive=true
            }
        }
    }

    private fun watchFlip() {
        sensors=getSystemService(SensorManager::class.java)
        sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {sensors?.registerListener(this,it,SensorManager.SENSOR_DELAY_NORMAL)}
    }
    override fun onSensorChanged(event: SensorEvent) {
        // Screen facing the table: z is close to -9.8 for a moment.
        faceDown=if(event.values[2]< -8.5f) faceDown+1 else 0
        if(faceDown>=4)finish()
    }
    override fun onAccuracyChanged(sensor: Sensor?,accuracy: Int) {}

    private fun flashTorch() {
        val camera=getSystemService(CameraManager::class.java) ?: return
        val id=runCatching {camera.cameraIdList.firstOrNull {camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)==true}}.getOrNull() ?: return
        repeat(6) {i->handler.postDelayed({runCatching {camera.setTorchMode(id,i%2==0)}},i*350L)}
        handler.postDelayed({runCatching {camera.setTorchMode(id,false)}},6*350L)
    }

    private fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching {player?.stop()};runCatching {player?.release()};player=null
        runCatching {session?.release()};session=null
        runCatching {tts?.stop();tts?.shutdown()};tts=null
        runCatching {sensors?.unregisterListener(this)};sensors=null
        faceDown=0
    }

    /** Ends playback. The adhan card stays in the shade for its iqama countdown, unless the live
     * iqama card already shows it (then one card is enough). */
    private fun finish() {
        release()
        val live=runCatching {getSystemService(android.app.NotificationManager::class.java).activeNotifications.any {it.notification.channelId==PrayerNotifications.LIVE}}.getOrDefault(false)
        if(live) stopForeground(STOP_FOREGROUND_REMOVE)
        else if(Build.VERSION.SDK_INT>=33) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false)
        stopSelf()
    }

    override fun onDestroy() {release();super.onDestroy()}
}
