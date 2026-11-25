package org.najwa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.R
import org.najwa.app.platform.Mushaf
import org.najwa.app.platform.QuranStore

/**
 * The mushaf reader: the 604 pages of the Madinah mushaf, swiped right to left like a printed copy.
 * Reading counts only when you confirm a page; opening or turning pages records nothing.
 */
@Composable fun MushafScreen(startPage: Int?,openIndex: Boolean,onBack: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val data by produceState<Mushaf.Data?>(null) {value=withContext(Dispatchers.IO) {runCatching {Mushaf.load(context)}.getOrNull()}}
    val english=LocalConfiguration.current.locales[0].language!="ar"
    var mode by remember {mutableStateOf(ReadingPrefs.mode(context))}
    var scale by remember {mutableFloatStateOf(QuranStore.readerScale(context))}
    var index by rememberSaveable {mutableStateOf(openIndex)}
    var readToday by remember {mutableStateOf(QuranStore.readToday(context))}
    var message by remember {mutableStateOf<String?>(null)}
    val pager=rememberPagerState(initialPage=((startPage ?: QuranStore.lastViewed(context))-1).coerceIn(0,Mushaf.PAGES-1)) {Mushaf.PAGES}
    val page=pager.currentPage+1
    LaunchedEffect(page) {QuranStore.setLastViewed(context,page);message=null}

    BackLink(stringResource(R.string.mushaf_back),onBack)
    val d=data
    if(d==null) {
        Text(stringResource(R.string.mushaf_loading),color=c.secondary,fontSize=14.sp,modifier=Modifier.padding(vertical=40.dp))
        return
    }
    // Where you are: surah, juz and page, with the index and text size at hand.
    val surahs=d.surahsOn(page)
    Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=4.dp)) {
        Column(Modifier.weight(1f)) {
            // One surah: "سورة الكهف"; two on a page: just their names, so the title never gets cut.
            val title=if(surahs.size==1) (if(english)surahs[0].latin else context.getString(R.string.mushaf_surah,surahs[0].name))
                else surahs.joinToString(" · ") {if(english)it.latin else it.name}
            Text(title,color=c.text,fontSize=19.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(stringResource(R.string.mushaf_where,d.juzOf(page),page),color=c.secondary,fontSize=13.sp)
        }
        TextButton(onClick={scale=(scale-.1f).coerceAtLeast(.8f);QuranStore.setReaderScale(context,scale)},Modifier.size(44.dp),contentPadding=PaddingValues(0.dp)) {
            Text("أ−",color=c.mint,fontSize=15.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_smaller)})}
        TextButton(onClick={scale=(scale+.1f).coerceAtMost(1.8f);QuranStore.setReaderScale(context,scale)},Modifier.size(44.dp),contentPadding=PaddingValues(0.dp)) {
            Text("أ+",color=c.mint,fontSize=19.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_larger)})}
        ReadingModeButton(mode) {mode=it}
        Spacer(Modifier.width(6.dp))
        Surface(onClick={index=true},shape=CircleShape,color=c.selected,modifier=Modifier.size(44.dp)) {
            Box(contentAlignment=Alignment.Center) {Glyph("mushaf",c.mint,Modifier.size(20.dp))}
        }
    }
    Spacer(Modifier.height(10.dp))
    // Pages always turn right to left, whatever the interface language.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HorizontalPager(pager,modifier=Modifier.fillMaxWidth(),beyondViewportPageCount=1,verticalAlignment=Alignment.Top) {i->
            MushafPage(d,i+1,scale,readingColors(mode))
        }
    }
    Spacer(Modifier.height(12.dp))
    // Confirmation: the only way reading is recorded.
    if(page in readToday) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph("check",c.mint,Modifier.size(18.dp))
            Text(stringResource(R.string.mushaf_confirmed),Modifier.weight(1f),color=c.text,fontSize=14.sp)
            TextButton(onClick={QuranStore.readPage(context,page);readToday=QuranStore.readToday(context);message=context.getString(R.string.mushaf_recorded)}) {
                Text(stringResource(R.string.mushaf_again))
            }
        }
    } else Button(onClick={
            QuranStore.readPage(context,page);readToday=QuranStore.readToday(context)
            message=context.getString(R.string.mushaf_recorded)
            if(page<Mushaf.PAGES)scope.launch {pager.animateScrollToPage(page)}
        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp),
        colors=ButtonDefaults.buttonColors(containerColor=c.green,contentColor=c.bg)) {
        Text(stringResource(R.string.mushaf_confirm),fontSize=15.sp,fontWeight=FontWeight.Bold)
    }
    message?.let {Text(it,color=c.mint,fontSize=13.sp,modifier=Modifier.padding(top=6.dp))}
    Text(stringResource(R.string.mushaf_hint),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
    Text(stringResource(R.string.mushaf_source),color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=4.dp,bottom=16.dp))

    val gold=accent("amber")
    if(index)Dialog(onDismissRequest={index=false}) {
        Surface(shape=RoundedCornerShape(24.dp),color=c.surface,modifier=Modifier.fillMaxWidth().heightIn(max=620.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.mushaf_index),color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                // Thirty juz as a compact grid, then every surah.
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    (1..30).chunked(10).forEach {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach {j->
                                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(gold.tint)
                                    .clickable {index=false;scope.launch {pager.scrollToPage(d.pageOfJuz(j)-1)}},contentAlignment=Alignment.Center) {
                                    Text(Mushaf.arabicDigits(j).takeUnless {english} ?: "$j",color=gold.ink,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.mushaf_juz_hint),color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=4.dp,bottom=8.dp))
                LazyColumn(Modifier.weight(1f,false)) {
                    items(d.surahs) {s->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {index=false;scope.launch {pager.scrollToPage(s.page-1)}}
                            .padding(horizontal=8.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(34.dp).background(c.selected,CircleShape),contentAlignment=Alignment.Center) {
                                Text("${s.number}",color=c.mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(if(english)s.latin else s.name,color=c.text,fontSize=16.sp,fontWeight=FontWeight.Bold)
                                Text(stringResource(R.string.mushaf_surah_info,stringResource(if(s.meccan)R.string.mushaf_meccan else R.string.mushaf_medinan),s.verses),
                                    color=c.secondary,fontSize=12.sp)
                            }
                            Text(stringResource(R.string.mushaf_page_short,s.page),color=c.secondary,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One mushaf page in the reading colours: a warm sheet with a fine double frame, a quiet surah band with the
 * basmalah, and verse markers in a muted tone. Text is right-aligned (no justification gaps), large and well spaced.
 */
@Composable private fun MushafPage(d: Mushaf.Data,page: Int,scale: Float,r: ReadingColors) {
    val verses=d.page(page)
    val basmalah=d.verses.first().text
    Surface(shape=RoundedCornerShape(22.dp),color=r.page,border=BorderStroke(1.dp,r.frame),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp).border(BorderStroke(.6.dp,r.frame),RoundedCornerShape(18.dp))) {
            Column(Modifier.padding(horizontal=18.dp,vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                // Verses are grouped into runs; a new surah starts with its band and the basmalah.
                val runs=mutableListOf<MutableList<Mushaf.Verse>>()
                verses.forEach {v-> if(v.aya==1 || runs.isEmpty())runs+=mutableListOf(v) else runs.last()+=v}
                runs.forEach {run->
                    val first=run.first()
                    if(first.aya==1) {
                        val s=d.surahs[first.sura-1]
                        Box(Modifier.fillMaxWidth().padding(top=6.dp,bottom=10.dp).clip(RoundedCornerShape(12.dp)).background(r.band)
                            .border(BorderStroke(.6.dp,r.frame),RoundedCornerShape(12.dp)).padding(vertical=9.dp),contentAlignment=Alignment.Center) {
                            Text("سُورَةُ ${s.name}",color=r.accentInk,fontSize=(19*scale).sp,fontWeight=FontWeight.Bold)
                        }
                        if(first.sura!=1 && first.sura!=9)
                            Text(basmalah,color=r.ink,fontSize=(22*scale).sp,textAlign=TextAlign.Center,modifier=Modifier.padding(bottom=8.dp))
                    }
                    Text(buildAnnotatedString {
                        run.forEach {v->
                            append(d.displayText(v))
                            withStyle(SpanStyle(color=r.marker,fontSize=(17*scale).sp)) {append(" ﴿${Mushaf.arabicDigits(v.aya)}﴾ ")}
                        }
                    },color=r.ink,fontSize=(23*scale).sp,lineHeight=(50*scale).sp,
                        style=LocalTextStyle.current.copy(textDirection=TextDirection.Rtl,textAlign=TextAlign.Start),modifier=Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                Text(Mushaf.arabicDigits(page),color=r.muted,fontSize=13.sp,
                    modifier=Modifier.clip(CircleShape).background(r.band).padding(horizontal=16.dp,vertical=4.dp))
            }
        }
    }
}
