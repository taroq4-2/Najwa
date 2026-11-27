package org.najwa.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.najwa.app.R
import org.najwa.core.Tajweed

/**
 * Colours for long reading (mushaf and adhkar), chosen for comfort rather than brand: warm paper with dark brown ink
 * by day, warm charcoal with soft beige ink at night, or your own background and text colours.
 */
enum class ReadingMode {PAPER,NIGHT,CUSTOM}

data class ReadingColors(val page: Color,val ink: Color,val muted: Color,val marker: Color,val frame: Color,val band: Color,val accentInk: Color,
    /** Around the page, like the table a mushaf rests on. */ val desk: Color)

/** The background and text colours of the custom reading mode (loaded from the device, see [ReadingPrefs]). */
object CustomReading {
    @Volatile var page: Int=0xFFF3E7C9.toInt()
    @Volatile var ink: Int=0xFF2B2219.toInt()
    /** Background choices: light papers and tints, then dark ones. */
    val pages=listOf(0xFFFFFFFF,0xFFFBF6EA,0xFFF3E7C9,0xFFEAF3EC,0xFFE8EEF6,0xFFF6E9EE,0xFF2A2A2A,0xFF1F1C18,0xFF0E2A22,0xFF0F1C2E,0xFF000000).map {it.toInt()}
    /** Text choices, dark then light. */
    val inks=listOf(0xFF111111,0xFF2B2219,0xFF12362A,0xFF14233A,0xFF4A2A12,0xFFFFFFFF,0xFFE6DCC8,0xFFEAD9A8,0xFFCFEBDD,0xFFD6E4F5).map {it.toInt()}
}

/** WCAG contrast ratio between two colours (1 to 21). */
fun contrastRatio(a: Color,b: Color): Float {
    val l1=a.luminance();val l2=b.luminance()
    return (maxOf(l1,l2)+.05f)/(minOf(l1,l2)+.05f)
}

fun readingColors(mode: ReadingMode)=when(mode) {
    ReadingMode.PAPER->ReadingColors(page=Color(0xFFFBF6EA),ink=Color(0xFF2B2219),muted=Color(0xFF7B6B57),marker=Color(0xFF9C6E35),
        frame=Color(0xFFE3D5B8),band=Color(0xFFF2E7CF),accentInk=Color(0xFF7A5222),desk=Color(0xFFECE2CC))
    ReadingMode.NIGHT->ReadingColors(page=Color(0xFF1F1C18),ink=Color(0xFFE6DCC8),muted=Color(0xFFA39682),marker=Color(0xFFC4A06A),
        frame=Color(0xFF3A342C),band=Color(0xFF2A2620),accentInk=Color(0xFFD9B985),desk=Color(0xFF151310))
    ReadingMode.CUSTOM->customColors(Color(CustomReading.page),Color(CustomReading.ink))
}

/** The rest of the palette follows from your two colours: muted text, frame and band blend them; gold suits both. */
private fun customColors(page: Color,ink: Color): ReadingColors {
    val dark=page.luminance()<.4f
    val gold=if(dark) Color(0xFFD2AE72) else Color(0xFF94672E)
    return ReadingColors(page=page,ink=ink,muted=lerp(ink,page,.42f),marker=gold,frame=lerp(page,ink,.16f),band=lerp(page,ink,.06f),
        accentInk=lerp(gold,ink,.25f),desk=lerp(page,if(dark)Color.Black else Color(0xFF8A7A60),.12f))
}

/** Tajweed colours on this page: grey, green, red and blue, lighter on dark backgrounds. */
fun tajweedColor(rule: Tajweed.Rule,r: ReadingColors): Color {
    val dark=r.page.luminance()<.4f
    return when(rule) {
        Tajweed.Rule.SILENT -> lerp(r.ink,r.page,.55f)
        Tajweed.Rule.NASAL -> if(dark) Color(0xFF6FD39C) else Color(0xFF13874F)
        Tajweed.Rule.MADD -> if(dark) Color(0xFFF2857B) else Color(0xFFC62F33)
        Tajweed.Rule.QALQALAH -> if(dark) Color(0xFF7FB8F2) else Color(0xFF1E64B4)
    }
}

/** The reading mode, custom colours and tajweed colouring, for the mushaf and the adhkar, remembered on this device. */
object ReadingPrefs {
    private fun prefs(context: Context)=context.getSharedPreferences("reading",Context.MODE_PRIVATE)
    fun mode(context: Context)=runCatching {ReadingMode.valueOf(prefs(context).getString("mode",null) ?: "")}.getOrDefault(ReadingMode.PAPER)
    /** Choosing a mode by hand ends "follow the app theme". */
    fun setMode(context: Context,mode: ReadingMode)=prefs(context).edit().putString("mode",mode.name).putBoolean("follow",false).apply()
    /** Reading colours follow the app: night when the app is dark, paper when it is light. */
    fun follows(context: Context)=prefs(context).getBoolean("follow",false)
    fun setFollows(context: Context,on: Boolean)=prefs(context).edit().putBoolean("follow",on).apply()
    fun resolve(context: Context,appDark: Boolean): ReadingMode {
        loadCustom(context)
        return if(follows(context)) (if(appDark) ReadingMode.NIGHT else ReadingMode.PAPER) else mode(context)
    }
    fun loadCustom(context: Context) {
        val p=prefs(context)
        CustomReading.page=p.getInt("custom_page",CustomReading.page)
        CustomReading.ink=p.getInt("custom_ink",CustomReading.ink)
    }
    fun setCustom(context: Context,page: Int,ink: Int) {
        CustomReading.page=page;CustomReading.ink=ink
        prefs(context).edit().putInt("custom_page",page).putInt("custom_ink",ink).apply()
    }
    fun tajweed(context: Context)=prefs(context).getBoolean("tajweed",false)
    fun setTajweed(context: Context,on: Boolean)=prefs(context).edit().putBoolean("tajweed",on).apply()
}

/** A round button that opens the reading appearance panel (used by the adhkar). */
@Composable fun ReadingModeButton(mode: ReadingMode,onChange: (ReadingMode)->Unit) {
    val c=LocalPalette.current
    var open by remember {mutableStateOf(false)}
    val label=stringResource(R.string.reading_appearance)
    Surface(onClick={open=true},shape=CircleShape,color=c.selected,modifier=Modifier.size(44.dp).semantics {contentDescription=label}) {
        Box(contentAlignment=Alignment.Center) {Glyph("palette",c.mint,Modifier.size(20.dp))}
    }
    if(open)ReadingAppearanceDialog(mode,tajweed=null,onMode=onChange,onTajweed={}) {open=false}
}

/**
 * Reading appearance: paper, night or your own colours (background and text, with a live preview and a warning when
 * the contrast is too low to read comfortably), and — in the mushaf — the tajweed colouring with its legend.
 * [tajweed] is null where colouring does not apply (the adhkar).
 */
@Composable fun ReadingAppearanceDialog(mode: ReadingMode,tajweed: Boolean?,onMode: (ReadingMode)->Unit,onTajweed: (Boolean)->Unit,onDismiss: ()->Unit) {
    val context=LocalContext.current
    val c=LocalPalette.current
    var chosen by remember {mutableStateOf(mode)}
    var page by remember {mutableIntStateOf(CustomReading.page)}
    var ink by remember {mutableIntStateOf(CustomReading.ink)}
    var colouring by remember {mutableStateOf(tajweed==true)}
    val preview=if(chosen==ReadingMode.CUSTOM) customColors(Color(page),Color(ink)) else readingColors(chosen)
    val ratio=contrastRatio(preview.page,preview.ink)
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.reading_appearance))},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(ReadingMode.PAPER to R.string.reading_mode_paper,ReadingMode.NIGHT to R.string.reading_mode_night,ReadingMode.CUSTOM to R.string.reading_mode_custom).forEach {(m,label)->
                        val on=chosen==m
                        Text(stringResource(label),Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if(on)c.selected else c.surface)
                            .border(1.dp,if(on)c.mint else c.border,RoundedCornerShape(14.dp)).clickable(role=Role.RadioButton) {chosen=m}
                            .semantics {selected=on}.padding(vertical=10.dp),
                            color=if(on)c.mint else c.text,fontSize=13.sp,fontWeight=if(on)FontWeight.Bold else FontWeight.Normal,textAlign=TextAlign.Center)
                    }
                }
                if(chosen==ReadingMode.CUSTOM) {
                    Text(stringResource(R.string.reading_background),color=c.secondary,fontSize=12.sp)
                    Swatches(CustomReading.pages,page) {page=it}
                    Text(stringResource(R.string.reading_text_colour),color=c.secondary,fontSize=12.sp)
                    Swatches(CustomReading.inks,ink) {ink=it}
                }
                // Live preview, with the tajweed colours when chosen.
                val sample="بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ ٱلۡحَمۡدُ لِلَّهِ رَبِّ ٱلۡعَٰلَمِينَ مِّن رَّبِّهِمۡ"
                val styled=buildAnnotatedString {
                    append(sample)
                    if(colouring)Tajweed.spans(sample).forEach {s->addStyle(SpanStyle(color=tajweedColor(s.rule,preview)),s.start,s.end)}
                }
                Text(styled,Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(preview.page).border(1.dp,preview.frame,RoundedCornerShape(14.dp))
                    .padding(14.dp),color=preview.ink,fontSize=20.sp,lineHeight=38.sp,fontFamily=QuranFont,textAlign=TextAlign.Center)
                if(ratio<4.5f)Text(stringResource(R.string.reading_low_contrast),color=c.amber,fontSize=12.sp)
                if(tajweed!=null) {
                    HorizontalDivider(color=c.border)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.tajweed_title),color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold)
                            Text(stringResource(R.string.tajweed_help),color=c.secondary,fontSize=12.sp)
                        }
                        Switch(colouring,{colouring=it})
                    }
                    if(colouring)Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf(Tajweed.Rule.SILENT to R.string.tajweed_silent,Tajweed.Rule.NASAL to R.string.tajweed_nasal,
                            Tajweed.Rule.MADD to R.string.tajweed_madd,Tajweed.Rule.QALQALAH to R.string.tajweed_qalqalah).forEach {(rule,label)->
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(12.dp).background(tajweedColor(rule,readingColors(ReadingMode.PAPER)),CircleShape))
                                Text(stringResource(label),color=c.text,fontSize=13.sp)
                            }
                        }
                        Text(stringResource(R.string.tajweed_note),color=c.secondary,fontSize=11.sp)
                    }
                }
            }
        },
        confirmButton={TextButton(onClick={
            if(chosen==ReadingMode.CUSTOM)ReadingPrefs.setCustom(context,page,ink)
            ReadingPrefs.setMode(context,chosen)
            onMode(chosen)
            if(tajweed!=null) {ReadingPrefs.setTajweed(context,colouring);onTajweed(colouring)}
            onDismiss()
        }) {Text(stringResource(R.string.reading_apply))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.action_cancel))}})
}

@Composable private fun Swatches(colors: List<Int>,current: Int,onPick: (Int)->Unit) {
    val c=LocalPalette.current
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        colors.forEach {argb->
            val on=argb==current
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(argb))
                .border(if(on)3.dp else 1.dp,if(on)c.mint else c.border,CircleShape)
                .clickable(role=Role.RadioButton,onClickLabel="#%06X".format(argb and 0xFFFFFF)) {onPick(argb)}
                .semantics {selected=on})
        }
    }
}
