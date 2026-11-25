package org.najwa.app.ui

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import org.najwa.app.R

/**
 * Colours for long reading (mushaf and adhkar), chosen for comfort rather than brand:
 * warm paper with dark brown ink by day, warm charcoal with soft beige ink at night. Contrast stays high
 * enough to read (about 12:1 and 11:1) without the glare of pure white on dark green.
 */
enum class ReadingMode {PAPER,NIGHT}

data class ReadingColors(val page: Color,val ink: Color,val muted: Color,val marker: Color,val frame: Color,val band: Color,val accentInk: Color)

fun readingColors(mode: ReadingMode)=when(mode) {
    ReadingMode.PAPER->ReadingColors(page=Color(0xFFFBF6EA),ink=Color(0xFF2B2219),muted=Color(0xFF7B6B57),marker=Color(0xFF9C6E35),
        frame=Color(0xFFE3D5B8),band=Color(0xFFF2E7CF),accentInk=Color(0xFF7A5222))
    ReadingMode.NIGHT->ReadingColors(page=Color(0xFF1F1C18),ink=Color(0xFFE6DCC8),muted=Color(0xFFA39682),marker=Color(0xFFC4A06A),
        frame=Color(0xFF3A342C),band=Color(0xFF2A2620),accentInk=Color(0xFFD9B985))
}

/** One reading mode for the mushaf and the adhkar, remembered on this device. */
object ReadingPrefs {
    private fun prefs(context: Context)=context.getSharedPreferences("reading",Context.MODE_PRIVATE)
    fun mode(context: Context)=runCatching {ReadingMode.valueOf(prefs(context).getString("mode",null) ?: "")}.getOrDefault(ReadingMode.PAPER)
    fun setMode(context: Context,mode: ReadingMode)=prefs(context).edit().putString("mode",mode.name).apply()
}

/** A round button that switches between paper and night reading. */
@Composable fun ReadingModeButton(mode: ReadingMode,onChange: (ReadingMode)->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val next=if(mode==ReadingMode.PAPER) ReadingMode.NIGHT else ReadingMode.PAPER
    val label=context.getString(if(next==ReadingMode.NIGHT) R.string.reading_night else R.string.reading_paper)
    Surface(onClick={ReadingPrefs.setMode(context,next);onChange(next)},shape=CircleShape,color=c.selected,
        modifier=Modifier.size(44.dp).semantics {contentDescription=label}) {
        Box(contentAlignment=Alignment.Center) {Glyph(if(next==ReadingMode.NIGHT)"moon" else "sun",c.mint,Modifier.size(20.dp))}
    }
}
