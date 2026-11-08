package org.najwa.app.platform

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import org.najwa.core.Preferences
import org.najwa.core.WidgetTheme

data class WidgetColors(val light: Boolean,val text: Int,val secondary: Int,val adhan: Int,val iqama: Int)

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
    return if(light)WidgetColors(true,color("#142B35"),color("#36565E"),color("#006B50"),color("#805015"))
        else WidgetColors(false,color("#F5F8FC"),color("#D0DFE1"),color("#60DFBA"),color("#F0C477"))
}
