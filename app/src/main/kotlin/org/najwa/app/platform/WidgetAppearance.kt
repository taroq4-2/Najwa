package org.najwa.app.platform

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import org.najwa.core.Preferences
import org.najwa.core.WidgetTheme

/** [prayer] colours the "prayer in progress" stage. [soon] marks the last minutes before adhan: warm coral, distinct from emerald (adhan) and gold (iqama). */
data class WidgetColors(val light: Boolean,val text: Int,val secondary: Int,val adhan: Int,val iqama: Int,val soon: Int,val prayer: Int)

fun widgetColors(context: Context,p: Preferences): WidgetColors {
    val light=when(p.widgetTheme) {
        WidgetTheme.LIGHT->true
        WidgetTheme.DARK->false
        WidgetTheme.AUTO-> {
            val hint=if(Build.VERSION.SDK_INT>=27)runCatching {
                context.getSystemService(WallpaperManager::class.java)?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.let {
                    if(Build.VERSION.SDK_INT>=31)it.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
                    else androidx.core.graphics.ColorUtils.calculateLuminance(it.primaryColor.toArgb())>.55
                }
            }.getOrNull() else null
            hint ?: (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES)
        }
    }
    fun color(hex: String)=Color.parseColor(hex)
    return if(light)WidgetColors(true,color("#19352E"),color("#405C50"),color("#126D53"),color("#81551E"),color("#B4471B"),color("#1F6E8C"))
        else WidgetColors(false,color("#F6F4EC"),color("#B8CBC0"),color("#A5E8C4"),color("#F0C787"),color("#FF9E6D"),color("#8FD0E6"))
}
