package org.najwa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.najwa.app.R
import org.najwa.app.platform.AyahMarks
import org.najwa.app.platform.Counts
import org.najwa.app.platform.Mushaf
import org.najwa.app.platform.MushafSearch
import org.najwa.app.platform.MushafLayout
import org.najwa.app.platform.QuranStore

/** Reading view: the printed page, or the page's verses in large flowing text. */
enum class MushafView {PAGE,LARGE}

object MushafViewPrefs {
    private fun prefs(context: android.content.Context)=context.getSharedPreferences("reading",android.content.Context.MODE_PRIVATE)
    fun view(context: android.content.Context)=runCatching {MushafView.valueOf(prefs(context).getString("view",null) ?: "")}.getOrDefault(MushafView.PAGE)
    fun setView(context: android.content.Context,v: MushafView)=prefs(context).edit().putString("view",v.name).apply()
    /** Flowing text sizes (sp): pinching below the smallest returns to the printed page. */
    const val MIN_SIZE=22f
    const val MAX_SIZE=60f
    const val DOUBLE_TAP_SIZE=34f
    /** The printed page's text, in sp, as the start of a pinch. */
    const val PAGE_SIZE=24f
    fun largeSize(context: android.content.Context)=prefs(context).getFloat("largeSize",32f).coerceIn(MIN_SIZE,MAX_SIZE)
    fun setLargeSize(context: android.content.Context,size: Float)=prefs(context).edit().putFloat("largeSize",size.coerceIn(MIN_SIZE,MAX_SIZE)).apply()
}

/**
 * Large reading: the same page's verses as flowing text in a large size, with headings and verse numbers. Easier on
 * the eyes, and read verse by verse by screen readers.
 */
@Composable fun LargeReadingPage(d: Mushaf.Data,page: Int,r: ReadingColors,size: Float,marks: PageDecor,onDoubleTap: ()->Unit,onLongPress: (Int)->Unit) {
    val verses=d.page(page)
    val base=d.firstIndex(page)
    // Verses run on in one paragraph per surah, as in a printed mushaf; a surah that starts here opens with its heading.
    val runs=remember(page) {
        val out=mutableListOf<MutableList<Int>>()
        verses.forEachIndexed {n,v->if(v.aya==1 || out.isEmpty())out+=mutableListOf(n) else out.last()+=n}
        out
    }
    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(r.page).verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(Mushaf.arabicDigits(page),color=r.muted,fontSize=13.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
        runs.forEach {run->
            val first=verses[run.first()]
            if(first.aya==1) {
                val s=d.surahs[first.sura-1]
                Surface(shape=RoundedCornerShape(12.dp),color=r.band,border=BorderStroke(.8.dp,r.frame),modifier=Modifier.fillMaxWidth()) {
                    Text("سورة ${s.name}",color=r.accentInk,fontSize=(size*.8f).sp,fontFamily=QuranFont,textAlign=TextAlign.Center,modifier=Modifier.padding(vertical=6.dp))
                }
                if(first.sura!=1 && first.sura!=9)Text(d.basmalah,color=r.ink,fontSize=size.sp,fontFamily=QuranFont,
                    textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
            }
            val text=buildAnnotatedString {
                run.forEach {n->
                    val v=verses[n];val index=base+n
                    val tone=when {
                        index==marks.flash -> r.marker.copy(alpha=.3f)
                        index in marks.highlighted -> r.marker.copy(alpha=.16f)
                        else -> androidx.compose.ui.graphics.Color.Unspecified
                    }
                    val start=length
                    val verseStart=length
                    val verseText=d.displayText(v)
                    withStyle(SpanStyle(background=tone)) {append(verseText)}
                    if(marks.tajweed)org.najwa.core.Tajweed.spans(verseText,verseEnd=true).forEach {s->
                        addStyle(SpanStyle(color=tajweedColor(s.rule,r)),verseStart+s.start,verseStart+s.end)}
                    // The Complex font's own verse ornament.
                    withStyle(SpanStyle(color=r.marker,background=tone)) {append(MushafLayout.VERSE_MARK_GAP+MushafLayout.verseMark(v.aya))}
                    if(index in marks.bookmarked)withStyle(SpanStyle(color=r.marker)) {append(" ◆")}
                    addStringAnnotation("verse",index.toString(),start,length)
                    append(" ")
                }
            }
            var layout by remember {mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)}
            Text(text,color=r.ink,fontSize=size.sp,lineHeight=(size*2f).sp,fontFamily=QuranFont,
                style=LocalTextStyle.current.copy(textDirection=TextDirection.Rtl,textAlign=TextAlign.Justify),
                onTextLayout={layout=it},
                modifier=Modifier.fillMaxWidth().pointerInput(text) {
                    detectTapGestures(onDoubleTap={onDoubleTap()},onLongPress={pos->
                        val offset=layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                        text.getStringAnnotations("verse",offset,offset).firstOrNull()?.item?.toIntOrNull()?.let(onLongPress)
                    })
                })
        }
    }
}

/** Search the mushaf: results by surah and verse, each opening its page with the verse lit for a moment. */
@Composable fun MushafSearchDialog(d: Mushaf.Data,r: ReadingColors,onDismiss: ()->Unit,onOpen: (MushafSearch.Hit)->Unit) {
    val context=LocalContext.current
    var query by remember {mutableStateOf("")}
    val hits by produceState(emptyList<MushafSearch.Hit>(),query) {
        value=if(query.trim().length<2) emptyList() else withContext(Dispatchers.Default) {MushafSearch.search(d,query)}
    }
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(26.dp),color=r.page,border=BorderStroke(1.dp,r.frame),modifier=Modifier.fillMaxWidth().heightIn(max=640.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.mushaf_search),color=r.ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(query,{query=it},singleLine=true,placeholder={Text(stringResource(R.string.mushaf_search_hint),fontSize=13.sp)},
                    modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=r.marker,unfocusedBorderColor=r.frame,
                        focusedTextColor=r.ink,unfocusedTextColor=r.ink,cursorColor=r.marker))
                Spacer(Modifier.height(8.dp))
                if(query.trim().length>=2)Text(if(hits.isEmpty()) stringResource(R.string.mushaf_search_none)
                    else stringResource(R.string.mushaf_search_count,Counts.format(context,hits.size.toLong())),color=r.muted,fontSize=12.sp)
                LazyColumn(Modifier.weight(1f,false)) {
                    items(hits) {h->
                        val v=d.verses[h.verse]
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {onOpen(h)}.padding(horizontal=6.dp,vertical=8.dp)) {
                            Text("${d.surahs[h.sura-1].name} · ${Mushaf.arabicDigits(h.aya)} · ${stringResource(R.string.mushaf_page_short,h.page)}",
                                color=r.accentInk,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            Text(d.displayText(v),color=r.ink,fontSize=17.sp,fontFamily=QuranFont,maxLines=2,
                                overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A verse as a shareable card: the text in the King Fahd Complex font on mushaf paper, its reference and the app's
 * name, drawn on the device (nothing is uploaded) and shared through the share sheet you choose.
 */
internal fun verseCard(context: android.content.Context,text: String,reference: String): java.io.File {
    val typeface=androidx.core.content.res.ResourcesCompat.getFont(context,R.font.kfgqpc_hafs) ?: android.graphics.Typeface.SERIF
    val width=1080;val pad=110
    val ink=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;textSize=60f;color=0xFF2A2418.toInt()}
    val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,ink,width-pad*2)
        .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setLineSpacing(26f,1f)
        .setTextDirection(android.text.TextDirectionHeuristics.RTL).build()
    val height=pad*2+layout.height+190
    val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap)
    canvas.drawColor(0xFFF8F3E6.toInt())
    val frame=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {style=android.graphics.Paint.Style.STROKE;color=0xFFC9A86A.toInt();strokeWidth=4f}
    canvas.drawRoundRect(36f,36f,width-36f,height-36f,34f,34f,frame)
    frame.strokeWidth=1.5f
    canvas.drawRoundRect(52f,52f,width-52f,height-52f,26f,26f,frame)
    canvas.save();canvas.translate(pad.toFloat(),pad.toFloat());layout.draw(canvas);canvas.restore()
    val small=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;textAlign=android.graphics.Paint.Align.CENTER}
    small.textSize=40f;small.color=0xFF8A6A2F.toInt()
    canvas.drawText(reference,width/2f,(pad+layout.height+90).toFloat(),small)
    small.textSize=28f;small.color=0xFF9C927E.toInt();small.typeface=android.graphics.Typeface.DEFAULT
    canvas.drawText("نجوى · Najwa",width/2f,(height-70).toFloat(),small)
    val dir=java.io.File(context.cacheDir,"shared").apply {mkdirs()}
    val file=java.io.File(dir,"najwa-verse.png")
    file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    bitmap.recycle()
    return file
}

/** What a long press on a verse offers: bookmark, highlight, share as an image, copy. */
@Composable fun VerseActions(d: Mushaf.Data,verse: Int,r: ReadingColors,onDismiss: ()->Unit,onChanged: ()->Unit) {
    val context=LocalContext.current
    val v=d.verses[verse]
    val surah=d.surahs[v.sura-1]
    var marked by remember {mutableStateOf(verse in AyahMarks.bookmarks(context))}
    var lit by remember {mutableStateOf(verse in AyahMarks.highlights(context))}
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(26.dp),color=r.page,border=BorderStroke(1.dp,r.frame),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("${surah.name} · ${Mushaf.arabicDigits(v.aya)}",color=r.accentInk,fontSize=14.sp,fontWeight=FontWeight.Bold)
                Text(d.displayText(v),color=r.ink,fontSize=20.sp,fontFamily=QuranFont,maxLines=6,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    VerseChip(stringResource(if(marked) R.string.mushaf_mark_remove else R.string.mushaf_mark_add),marked,r,Modifier.weight(1f)) {
                        AyahMarks.toggle(context,AyahMarks.Kind.BOOKMARK,verse);marked=!marked;onChanged()}
                    VerseChip(stringResource(R.string.mushaf_highlight),lit,r,Modifier.weight(1f)) {
                        AyahMarks.toggle(context,AyahMarks.Kind.HIGHLIGHT,verse);lit=!lit;onChanged()}
                }
                VerseChip(stringResource(R.string.mushaf_share_image),false,r,Modifier.fillMaxWidth()) {
                    val reference="${surah.name} ${Mushaf.arabicDigits(v.aya)}"
                    runCatching {shareImage(context,verseCard(context,d.displayText(v)+" "+MushafLayout.verseMark(v.aya),reference),reference)}
                    onDismiss()
                }
                VerseChip(stringResource(R.string.mushaf_copy),false,r,Modifier.fillMaxWidth()) {
                    val clip=context.getSystemService(android.content.ClipboardManager::class.java)
                    clip?.setPrimaryClip(android.content.ClipData.newPlainText("ayah","${d.displayText(v)} ﴿${Mushaf.arabicDigits(v.aya)}﴾ [${surah.name}: ${Mushaf.arabicDigits(v.aya)}]"))
                    android.widget.Toast.makeText(context,context.getString(R.string.mushaf_copied),android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        }
    }
}

@Composable private fun VerseChip(text: String,on: Boolean,r: ReadingColors,modifier: Modifier,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(16.dp),color=if(on)r.marker else r.band,border=BorderStroke(.8.dp,if(on)r.marker else r.frame),modifier=modifier) {
        Text(text,color=if(on)r.page else r.accentInk,fontSize=14.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.padding(vertical=10.dp))
    }
}

/** Bookmarked verses, for the index. */
@Composable fun MarksList(d: Mushaf.Data,r: ReadingColors,onOpen: (Int)->Unit) {
    val context=LocalContext.current
    val marks=remember {AyahMarks.bookmarks(context).sorted()}
    Text(stringResource(R.string.mushaf_marks),color=r.muted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
    if(marks.isEmpty())Text(stringResource(R.string.mushaf_marks_none),color=r.muted,fontSize=12.sp)
    marks.take(20).forEach {i->
        val v=d.verses[i]
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {onOpen(d.pageOf(i))}.padding(horizontal=6.dp,vertical=6.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("◆",color=r.marker,fontSize=14.sp)
            Text("${d.surahs[v.sura-1].name} · ${Mushaf.arabicDigits(v.aya)}",color=r.ink,fontSize=14.sp,modifier=Modifier.weight(1f))
            Text(stringResource(R.string.mushaf_page_short,d.pageOf(i)),color=r.muted,fontSize=12.sp)
        }
    }
}

/**
 * The moment a khatma ends: thanks, how long it took, a Quranic supplication (Al-Baqarah 127), and the next khatma.
 */
@Composable fun KhatmaMoment(r: ReadingColors,onNext: ()->Unit,onDismiss: ()->Unit) {
    val context=LocalContext.current
    val q=QuranStore.read(context)
    val last=QuranStore.khatmaLog(context).firstOrNull()
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(28.dp),color=r.page,border=BorderStroke(1.dp,r.marker),modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("۞",color=r.marker,fontSize=34.sp,fontFamily=QuranFont)
                Text(stringResource(R.string.khatma_done_title),color=r.ink,fontSize=22.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
                Text(stringResource(R.string.khatma_done_text,Counts.format(context,q.khatmas.toLong())),color=r.muted,fontSize=14.sp,textAlign=TextAlign.Center)
                last?.second?.let {Text(stringResource(R.string.khatma_done_days,stringResource(R.string.count_days,Counts.format(context,it.toLong()))),color=r.muted,fontSize=13.sp)}
                Text("رَبَّنَا تَقَبَّلْ مِنَّآ ۖ إِنَّكَ أَنتَ ٱلسَّمِيعُ ٱلْعَلِيمُ",color=r.ink,fontSize=24.sp,fontFamily=QuranFont,textAlign=TextAlign.Center,
                    modifier=Modifier.padding(vertical=6.dp))
                Text("البقرة ١٢٧",color=r.muted,fontSize=12.sp)
                Button(onClick=onNext,shape=RoundedCornerShape(50),colors=ButtonDefaults.buttonColors(containerColor=r.marker,contentColor=r.page)) {
                    Text(stringResource(R.string.khatma_next),fontWeight=FontWeight.Bold)
                }
                TextButton(onClick=onDismiss) {Text(stringResource(R.string.mushaf_done),color=r.accentInk)}
            }
        }
    }
}
