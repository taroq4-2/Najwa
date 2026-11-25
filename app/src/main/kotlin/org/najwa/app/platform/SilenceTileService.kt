package org.najwa.app.platform

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.najwa.app.MANUAL_SILENCE_MINUTES
import org.najwa.app.MainActivity
import org.najwa.app.NajwaApplication
import org.najwa.app.R
import org.najwa.app.prayerExecutor
import java.time.Instant

/** Quick Settings tile: silence the phone for half an hour, or end the current silence. */
class SilenceTileService: TileService() {
    private val runtime get()=(application as NajwaApplication).runtime

    override fun onStartListening() {
        super.onStartListening()
        prayerExecutor.execute {
            val active=runtime.activeSilence()
            val ready=runtime.canSilenceNow()
            android.os.Handler(mainLooper).post {render(active?.deadline,ready)}
        }
    }

    private fun render(until: Long?,ready: Boolean) {
        val tile=qsTile ?: return
        val strings=prayerLanguage()
        tile.label=strings.getString(R.string.tile_label)
        tile.state=when {
            until!=null -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if(Build.VERSION.SDK_INT>=29)tile.subtitle=when {
            until!=null -> strings.getString(R.string.tile_until,strings.prayerTime(Instant.ofEpochMilli(until),runtime.preferences))
            !ready -> strings.getString(R.string.tile_setup)
            else -> strings.getString(R.string.tile_minutes,MANUAL_SILENCE_MINUTES)
        }
        tile.contentDescription=listOfNotNull(tile.label,if(Build.VERSION.SDK_INT>=29)tile.subtitle else null).joinToString(" · ")
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        prayerExecutor.execute {
            val active=runtime.activeSilence()
            when {
                active!=null -> runtime.restore()
                runtime.canSilenceNow() -> runtime.manualSilence()
                else -> {android.os.Handler(mainLooper).post {openApp()};return@execute}
            }
            val now=runtime.activeSilence()
            android.os.Handler(mainLooper).post {render(now?.deadline,runtime.canSilenceNow())}
        }
    }

    /** Permissions are missing: open Najwa's silence screen to set them up. */
    private fun openApp() {
        val intent=Intent(this,MainActivity::class.java).setAction(ACTION_SILENCE_NOW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if(Build.VERSION.SDK_INT>=34) {
            startActivityAndCollapse(PendingIntent.getActivity(this,704,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else @Suppress("DEPRECATION","StartActivityAndCollapseDeprecated") startActivityAndCollapse(intent)
    }
}
