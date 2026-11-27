package org.najwa.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.R
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.runtime.snapshots.SnapshotStateMap
import org.najwa.app.platform.AyahMarks
import org.najwa.app.platform.Counts
import org.najwa.app.platform.DaySummary
import org.najwa.app.platform.Mushaf
import org.najwa.app.platform.MushafLayout
import org.najwa.app.platform.QuranStore
import org.najwa.app.platform.WorshipSettingsStore
import org.najwa.app.platform.WorshipSummary
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The font the pages are set in: the King Fahd Complex Hafs font (KFGQPC), drawn by the Madinah mushaf calligrapher. */
val QuranFont=FontFamily(Font(R.font.kfgqpc_hafs))

/**
 * The mushaf reader: the 604 pages of the Madinah mushaf as full printed pages, fifteen justified lines in an
 * ornamented frame, turned right to left. Pinching or double-tapping enlarges the text itself, which flows anew. Where you stop is kept; a page joins
 * today's portion when you move on after reading it (or with the record button), never by flicking past it.
 */
@Composable fun MushafScreen(startPage: Int?,openIndex: Boolean,onBack: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val data by produceState<Mushaf.Data?>(null) {value=withContext(Dispatchers.IO) {runCatching {Mushaf.load(context)}.getOrNull()}}
    // The first search builds a normalised index of every verse; prepare it quietly while you read.
    LaunchedEffect(data) {data?.let {d: Mushaf.Data->withContext(Dispatchers.Default) {runCatching {org.najwa.app.platform.MushafSearch.warm(d)}}}}
    val english=LocalConfiguration.current.locales[0].language!="ar"
    val appDark=LocalPalette.current.dark
    var mode by remember(appDark) {mutableStateOf(ReadingPrefs.resolve(context,appDark))}
    // Bumped when custom colours change while the mode stays "custom".
    var styleTick by remember {mutableIntStateOf(0)}
    val r=remember(mode,styleTick) {readingColors(mode)}
    var tajweed by remember {mutableStateOf(ReadingPrefs.tajweed(context))}
    var appearance by remember {mutableStateOf(false)}
    var index by rememberSaveable {mutableStateOf(openIndex)}
    var plan by rememberSaveable {mutableStateOf(false)}
    var search by rememberSaveable {mutableStateOf(false)}
    var chrome by rememberSaveable {mutableStateOf(false)}
    var quran by remember {mutableStateOf(QuranStore.read(context))}
    var readToday by remember {mutableStateOf(QuranStore.readToday(context))}
    var summary by remember {mutableStateOf<DaySummary?>(null)}
    var toast by remember {mutableStateOf<Pair<String,Boolean>?>(null)}
    var auto by remember {mutableStateOf(QuranStore.autoRecord(context))}
    var view by remember {mutableStateOf(MushafViewPrefs.view(context))}
    var largeSize by remember {mutableFloatStateOf(MushafViewPrefs.largeSize(context))}
    var memorize by rememberSaveable {mutableStateOf(false)}
    val revealed=remember {mutableStateMapOf<Int,Int>()}
    var verseAction by remember {mutableStateOf<Int?>(null)}
    var flash by remember {mutableStateOf<Int?>(null)}
    var marksVersion by remember {mutableIntStateOf(0)}
    var khatmaMoment by remember {mutableStateOf(false)}
    // One place to stop: the reader opens at the khatma bookmark (the index also offers the last page you opened).
    val bookmarkPage=(quran.page+1).coerceIn(1,Mushaf.PAGES)
    val pager=rememberPagerState(initialPage=((startPage ?: bookmarkPage)-1).coerceIn(0,Mushaf.PAGES-1)) {Mushaf.PAGES}
    val page=pager.currentPage+1
    // The size of the flowing text while a pinch is under way starts from here.
    var pinchBase by remember {mutableFloatStateOf(0f)}
    fun toPage() {view=MushafView.PAGE;MushafViewPrefs.setView(context,view)}
    fun toLarge(size: Float) {largeSize=size.coerceIn(MushafViewPrefs.MIN_SIZE,MushafViewPrefs.MAX_SIZE);view=MushafView.LARGE}
    val bookmarked=remember(marksVersion) {AyahMarks.bookmarks(context)}
    val highlighted=remember(marksVersion) {AyahMarks.highlights(context)}

    fun refresh() {
        quran=QuranStore.read(context);readToday=QuranStore.readToday(context)
        scope.launch {summary=withContext(Dispatchers.IO) {runCatching {WorshipSummary.build(context,WorshipSettingsStore.read(context))}.getOrNull()}}
    }
    fun record(p: Int,automatic: Boolean) {
        QuranStore.readPage(context,p)
        refresh()
        toast=context.getString(R.string.mushaf_auto_recorded,Counts.format(context,p.toLong())) to true
        if(QuranStore.takeKhatmaMoment(context))khatmaMoment=true
        if(!automatic && p<Mushaf.PAGES)scope.launch {pager.animateScrollToPage(p)}
    }
    LaunchedEffect(Unit) {
        refresh()
        // Once: say how to bring the tools back.
        val prefs=context.getSharedPreferences("reading",android.content.Context.MODE_PRIVATE)
        if(!prefs.getBoolean("tapHint",false)) {toast=context.getString(R.string.mushaf_tap_hint) to false;prefs.edit().putBoolean("tapHint",true).apply()}
    }
    // Reading is full screen: the status and navigation bars hide with the tools and return with them (or a swipe).
    val view0=androidx.compose.ui.platform.LocalView.current
    DisposableEffect(chrome) {
        var ctx=view0.context
        while(ctx is android.content.ContextWrapper && ctx !is android.app.Activity)ctx=ctx.baseContext
        val controller=(ctx as? android.app.Activity)?.window?.let {androidx.core.view.WindowCompat.getInsetsController(it,view0)}
        val bars=androidx.core.view.WindowInsetsCompat.Type.systemBars()
        if(chrome) controller?.show(bars) else {
            controller?.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(bars)
        }
        onDispose {controller?.show(bars)}
    }
    LaunchedEffect(toast) {if(toast!=null) {delay(4200);toast=null}}
    LaunchedEffect(flash) {if(flash!=null) {delay(3500);flash=null}}

    // Where you are is saved on every turn. Moving forward after time on a page records it, once a day.
    // Time with the screen off or the app in the background does not count as reading.
    var entered by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
    var previous by remember {mutableIntStateOf(page)}
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var pausedAt=0L
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
            when(event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> pausedAt=SystemClock.elapsedRealtime()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> if(pausedAt>0) {entered+=SystemClock.elapsedRealtime()-pausedAt;pausedAt=0}
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(page) {
        QuranStore.setLastViewed(context,page)
        val dwell=SystemClock.elapsedRealtime()-entered
        if(auto && page==previous+1 && dwell>=QuranStore.autoRecordMillis && previous !in readToday) record(previous,true)
        previous=page;entered=SystemClock.elapsedRealtime()
    }
    // A slow ring shows the page being read; when full, turning the page will record it.
    var dwellShown by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(page,auto,readToday) {
        dwellShown=0f
        if(!auto || page in readToday)return@LaunchedEffect
        while(dwellShown<1f) {delay(1000);dwellShown=((SystemClock.elapsedRealtime()-entered).toFloat()/QuranStore.autoRecordMillis).coerceAtMost(1f)}
    }

    Box(Modifier.fillMaxSize().background(r.desk)) {
        val d=data
        if(d==null) {
            Text(stringResource(R.string.mushaf_loading),color=r.muted,fontSize=14.sp,modifier=Modifier.align(Alignment.Center))
            return@Box
        }
        // The page fills the screen; a tap brings the tools over it and another tap hides them again.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            // Pages always turn right to left, whatever the interface language.
            HorizontalPager(pager,modifier=Modifier.fillMaxSize(),beyondViewportPageCount=1,key={it}) {i->
                val p=i+1
                val decor=PageDecor(bookmarked,highlighted,flash,if(memorize) revealed[p] ?: 0 else null,tajweed)
                // Pinch out on the printed page and the verses flow in large text; pinch in far enough and the page returns.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {detectTapGestures(onTap={chrome=!chrome})}
                    .textPinch(onStart={pinchBase=if(view==MushafView.LARGE) largeSize else MushafViewPrefs.PAGE_SIZE},
                        onScale={f->
                            val size=pinchBase*f
                            if(view==MushafView.PAGE) {if(f>1.08f)toLarge(size)} else largeSize=size.coerceIn(MushafViewPrefs.MIN_SIZE-4f,MushafViewPrefs.MAX_SIZE)
                        },
                        onEnd={
                            if(view==MushafView.LARGE && largeSize<MushafViewPrefs.MIN_SIZE) toPage()
                            else if(view==MushafView.LARGE) {MushafViewPrefs.setLargeSize(context,largeSize);MushafViewPrefs.setView(context,view)}
                        })
                    .padding(horizontal=8.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
                    if(view==MushafView.LARGE) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        LargeReadingPage(d,p,r,largeSize,decor,onDoubleTap={toPage()}) {verse->verseAction=verse}
                    }
                    else MushafSheet(d,p,r,bookmark=p==quran.page+1,read=p in readToday,decor=decor,onDoubleTap={toLarge(MushafViewPrefs.DOUBLE_TAP_SIZE)},
                        onTap={word->
                            // Memorization: each tap shows the next word; otherwise a tap shows or hides the tools.
                            if(memorize && word!=null) revealed[p]=(revealed[p] ?: 0)+1 else chrome=!chrome
                        },
                        onLongPress={word,endOfVerse->
                            if(memorize) {if(endOfVerse>=0) revealed[p]=maxOf(revealed[p] ?: 0,endOfVerse+1)}
                            else if(word!=null) verseAction=word.token.verse
                        })
                }
            }
        }
        AnimatedVisibility(chrome,modifier=Modifier.align(Alignment.TopCenter),
            enter=fadeIn()+slideInVertically {-it},exit=fadeOut()+slideOutVertically {-it}) {
            Surface(color=r.desk.copy(alpha=.97f),shape=RoundedCornerShape(bottomStart=24.dp,bottomEnd=24.dp),shadowElevation=6.dp,
                modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.statusBarsPadding().padding(bottom=12.dp)) {
                    ReaderTopBar(d,page,r,english,mode,onBack=onBack,onIndex={index=true},onPlan={plan=true},onSearch={search=true},
                        onAppearance={appearance=true})
                    if(view==MushafView.LARGE)Row(Modifier.padding(horizontal=16.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.mushaf_text_size),color=r.muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                        RoundAction("minus",stringResource(R.string.adhkar_smaller),r) {largeSize=(largeSize-3f).coerceAtLeast(MushafViewPrefs.MIN_SIZE);MushafViewPrefs.setLargeSize(context,largeSize)}
                        Spacer(Modifier.width(8.dp))
                        RoundAction("plus",stringResource(R.string.adhkar_larger),r) {largeSize=(largeSize+3f).coerceAtMost(MushafViewPrefs.MAX_SIZE);MushafViewPrefs.setLargeSize(context,largeSize)}
                    }
                    WirdStrip(quran,summary,r,english)
                }
            }
        }
        AnimatedVisibility(chrome,modifier=Modifier.align(Alignment.BottomCenter),
            enter=fadeIn()+slideInVertically {it},exit=fadeOut()+slideOutVertically {it}) {
            Surface(color=r.desk.copy(alpha=.97f),shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),shadowElevation=6.dp,
                modifier=Modifier.fillMaxWidth()) {
                Box(Modifier.navigationBarsPadding().padding(vertical=4.dp)) {
                    ReaderBottomBar(page,page in readToday,auto,dwellShown,view==MushafView.LARGE,r,onRecord={record(page,false)},onResetZoom={toPage()})
                }
            }
        }
        // A quiet confirmation with undo after every recorded page (read out by screen readers).
        toast?.let {(text,undo)->
            Surface(shape=RoundedCornerShape(50),color=r.ink,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom=if(chrome)84.dp else 20.dp,start=24.dp,end=24.dp).semantics {liveRegion=androidx.compose.ui.semantics.LiveRegionMode.Polite}) {
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(start=18.dp,end=6.dp)) {
                    Text(text,color=r.page,fontSize=13.sp,modifier=Modifier.padding(vertical=12.dp))
                    if(undo)TextButton(onClick={QuranStore.undoLast(context);refresh();toast=null}) {
                        Text(stringResource(R.string.undo),color=r.marker,fontWeight=FontWeight.Bold,fontSize=13.sp)
                    }
                }
            }
        }
        if(index)MushafIndex(d,quran,english,r,onDismiss={index=false}) {p->index=false;scope.launch {pager.scrollToPage(p-1)}}
        if(plan)PlanDialog(quran,auto,r,english,view,memorize,onGoal={refresh()},
            onAuto={auto=it;QuranStore.setAutoRecord(context,it)},
            onView={view=it;MushafViewPrefs.setView(context,it)},
            onMemorize={memorize=it;revealed.clear()}) {plan=false}
        if(search)MushafSearchDialog(d,r,onDismiss={search=false}) {hit->
            search=false;flash=hit.verse;scope.launch {pager.scrollToPage(hit.page-1)}}
        verseAction?.let {v->VerseActions(d,v,r,onDismiss={verseAction=null}) {marksVersion++}}
        if(appearance)ReadingAppearanceDialog(mode,tajweed,onMode={mode=it;styleTick++},onTajweed={tajweed=it}) {appearance=false}
        if(khatmaMoment)KhatmaMoment(r,onNext={khatmaMoment=false;scope.launch {pager.scrollToPage(0)};plan=true}) {khatmaMoment=false}
    }
}

/* ───────────────────────── Chrome ───────────────────────── */

@Composable private fun RoundAction(glyph: String,label: String,r: ReadingColors,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=CircleShape,color=r.band,border=BorderStroke(.8.dp,r.frame),
        modifier=Modifier.size(42.dp).semantics {contentDescription=label}) {
        Box(contentAlignment=Alignment.Center) {Glyph(glyph,r.accentInk,Modifier.size(19.dp))}
    }
}

@Composable private fun ReaderTopBar(d: Mushaf.Data,page: Int,r: ReadingColors,english: Boolean,mode: ReadingMode,
                                    onBack: ()->Unit,onIndex: ()->Unit,onPlan: ()->Unit,onSearch: ()->Unit,onAppearance: ()->Unit) {
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        RoundAction("back",stringResource(R.string.mushaf_back),r,onBack)
        Column(Modifier.weight(1f).padding(horizontal=4.dp)) {
            val surahs=d.surahsOn(page)
            val title=if(english) surahs.joinToString(" · ") {it.latin}
                else if(surahs.size==1) context.getString(R.string.mushaf_surah_label,surahs[0].name) else surahs.joinToString(" · ") {it.name}
            Text(title,color=r.ink,fontSize=if(english)17.sp else 21.sp,fontFamily=if(english)null else QuranFont,
                fontWeight=if(english)FontWeight.Bold else null,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(Counts.digits(context,stringResource(R.string.mushaf_where,d.juzOf(page),page)),color=r.muted,fontSize=12.sp)
        }
        RoundAction("search",stringResource(R.string.mushaf_search),r,onSearch)
        RoundAction("mushaf",stringResource(R.string.mushaf_index),r,onIndex)
        // Reading appearance: paper, night or your colours, and tajweed colouring.
        RoundAction("palette",stringResource(R.string.reading_appearance),r,onAppearance)
        RoundAction("calendar",stringResource(R.string.mushaf_settings),r,onPlan)
    }
}

/** Western digits in other languages, Arabic-Indic digits in Arabic, both with thousands separators. */
private fun num(english: Boolean,n: Long): String =
    NumberFormat.getIntegerInstance(if(english) Locale.US else Locale.forLanguageTag("ar-EG")).format(n)

/**
 * Today's portion as a row of beads, one per page of the goal, with the khatma's progress and the day's hoped-for
 * hasanat against the target (letters instead when no numeric rule is active).
 */
@Composable private fun WirdStrip(q: QuranStore.State,s: DaySummary?,r: ReadingColors,english: Boolean) {
    val context=LocalContext.current
    val read=s?.pages ?: QuranStore.today(context)
    val goal=q.goal
    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).padding(top=10.dp)) {
        Row(verticalAlignment=Alignment.Bottom) {
            Text(stringResource(R.string.mushaf_wird_title),color=r.accentInk,fontSize=13.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mushaf_wird_pages,Counts.format(context,read.toLong()),Counts.format(context,goal.toLong())),color=r.ink,fontSize=13.sp)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.mushaf_khatma,q.page*100/QuranStore.PAGES),color=r.muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(7.dp))
        val shown by animateFloatAsState((read.toFloat()/goal).coerceIn(0f,1f),tween(500),label="wird")
        Canvas(Modifier.fillMaxWidth().height(9.dp)) {
            val beads=goal.coerceAtMost(40)
            if(goal<=40) {
                val gap=3.dp.toPx()
                val w=(size.width-gap*(beads-1))/beads
                val rtl=layoutDirection==LayoutDirection.Rtl
                for(i in 0 until beads) {
                    val x=if(rtl) size.width-(i+1)*w-i*gap else i*(w+gap)
                    val fill=((shown*goal)-i).coerceIn(0f,1f)
                    drawRoundRect(r.frame,Offset(x,0f),Size(w,size.height),CornerRadius(size.height/2))
                    if(fill>0f) {
                        val fw=w*fill
                        drawRoundRect(r.marker,Offset(if(rtl)x+w-fw else x,0f),Size(fw,size.height),CornerRadius(size.height/2))
                    }
                }
            } else {
                drawRoundRect(r.frame,Offset.Zero,size,CornerRadius(size.height/2))
                val fw=size.width*shown
                drawRoundRect(r.marker,Offset(if(layoutDirection==LayoutDirection.Rtl)size.width-fw else 0f,0f),Size(fw,size.height),CornerRadius(size.height/2))
            }
        }
        Spacer(Modifier.height(6.dp))
        val line=when {
            read>=goal -> stringResource(R.string.mushaf_wird_done)
            s?.virtues?.hasanat!=null && s.hasanatTarget!=null ->
                stringResource(R.string.mushaf_hasanat,Counts.format(context,s.virtues.hasanat ?: 0))+" · "+stringResource(R.string.mushaf_hasanat_target,Counts.format(context,s.hasanatTarget))
            s?.letters!=null && s.wirdLetters!=null -> stringResource(R.string.mushaf_letters_target,Counts.format(context,s.letters),Counts.format(context,s.wirdLetters))
            else -> stringResource(R.string.mushaf_wird_left,Counts.format(context,(goal-read).toLong()))
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(line,color=r.muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
            if(s?.preview==true && s.virtues.hasanat!=null && read<goal) {
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.worship_preview_badge),color=r.marker,fontSize=10.sp,
                    modifier=Modifier.border(.6.dp,r.marker,RoundedCornerShape(50)).padding(horizontal=6.dp,vertical=1.dp))
            }
        }
    }
}

@Composable private fun ReaderBottomBar(page: Int,read: Boolean,auto: Boolean,dwell: Float,zoomed: Boolean,r: ReadingColors,
                                       onRecord: ()->Unit,onResetZoom: ()->Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(read) {
            Seal(r,Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mushaf_read_today),color=r.accentInk,fontSize=13.sp,fontWeight=FontWeight.Bold)
        } else if(auto) {
            // The reading ring: fills over twenty seconds on the page.
            Canvas(Modifier.size(22.dp)) {
                val st=2.dp.toPx()
                drawCircle(r.frame,size.minDimension/2-st/2,style=Stroke(st))
                drawArc(r.marker,-90f,360f*dwell,false,topLeft=Offset(st/2,st/2),size=Size(size.width-st,size.height-st),style=Stroke(st,cap=androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.mushaf_auto_title),color=r.muted,fontSize=12.sp)
        }
        Spacer(Modifier.weight(1f))
        if(zoomed)TextButton(onClick=onResetZoom) {Text(stringResource(R.string.mushaf_view_page),color=r.accentInk,fontSize=13.sp)}
        else if(!read)OutlinedButton(onClick=onRecord,shape=RoundedCornerShape(50),border=BorderStroke(1.dp,r.marker),
            contentPadding=PaddingValues(horizontal=16.dp,vertical=6.dp)) {
            Text(stringResource(R.string.mushaf_record),color=r.accentInk,fontSize=13.sp,fontWeight=FontWeight.Bold)
        }
    }
}

/** A small seal with a tick, used for pages read today. */
@Composable private fun Seal(r: ReadingColors,modifier: Modifier) {
    Canvas(modifier) {drawSeal(center,size.minDimension/2,r)}
}

private fun DrawScope.drawSeal(c: Offset,radius: Float,r: ReadingColors) {
    drawPath(star(c,radius,radius*.82f,8),r.marker)
    drawCircle(r.page,radius*.62f,c)
    val p=Path().apply {
        moveTo(c.x-radius*.32f,c.y+radius*.02f);lineTo(c.x-radius*.08f,c.y+radius*.26f);lineTo(c.x+radius*.34f,c.y-radius*.24f)
    }
    drawPath(p,r.marker,style=Stroke(radius*.16f,cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** An n-pointed star (two radii), the motif of the frame corners, verse markers and seals. */
private fun star(c: Offset,outer: Float,inner: Float,points: Int,turn: Float=0f)=Path().apply {
    for(i in 0 until points*2) {
        val a=PI*i/points-PI/2+turn
        val rad=if(i%2==0) outer else inner
        val x=c.x+(rad*cos(a)).toFloat();val y=c.y+(rad*sin(a)).toFloat()
        if(i==0)moveTo(x,y) else lineTo(x,y)
    }
    close()
}

/* ───────────────────────── Zoom ───────────────────────── */

/**
 * Two fingers size the text, not the picture: the gesture reports the running scale factor, and the screen turns it
 * into a font size for the flowing text (the printed page opens it). One finger is left to turn and scroll pages.
 */
private fun Modifier.textPinch(onStart: ()->Unit,onScale: (Float)->Unit,onEnd: ()->Unit)=this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed=false)
        var factor=1f
        var pinching=false
        do {
            val event=awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            if(event.changes.count {it.pressed}>=2) {
                if(!pinching) {pinching=true;onStart()}
                factor*=event.calculateZoom()
                onScale(factor)
                event.changes.forEach {it.consume()}
            }
        } while(event.changes.any {it.pressed})
        if(pinching)onEnd()
    }
}

/* ───────────────────────── The printed page ───────────────────────── */

private const val REF=100f
/** Opening pages: rows and type this much larger. */
private const val OPENING=1.3f

/** Widths of words with the page font at the reference size; one instance per thread. */
private class PaintMeasure(typeface: Typeface): MushafLayout.Measure {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;textSize=REF}
    override val space=paint.measureText(" ")*1.15f
    private val cache=HashMap<String,Float>()
    fun word(text: String)=cache.getOrPut(text) {paint.measureText(text)}
    override fun width(token: MushafLayout.Token)=word(token.text)
}
private const val MARKER=1.02f
private const val MARKER_GAP=.16f

private data class Geometry(val w: Float,val h: Float,val frame: Rect,val text: Rect,val row: Float)

private fun geometry(w: Float,h: Float,dp: Float): Geometry {
    val pad=8*dp;val top=30*dp;val bottom=32*dp;val band=7*dp;val gapX=12*dp;val gapY=10*dp
    val frame=Rect(pad,top,w-pad,h-bottom)
    val text=Rect(frame.left+band+gapX,frame.top+band+gapY,frame.right-band-gapX,frame.bottom-band-gapY)
    return Geometry(w,h,frame,text,text.height/MushafLayout.LINES)
}

/** One word as placed on the page: its drawn text, where it sits, its verse marker, and its order on the page. */
private class Placed(val token: MushafLayout.Token,val text: String,val left: Float,val right: Float,val wordLeft: Float,val markerX: Float?,val ordinal: Int,
                     val mark: String?=null,val markLeft: Float=0f)

/** A laid-out page with every line justified to the full width (kashida included) and every word placed. */
private class Typeset(val page: MushafLayout.Page,val rows: List<List<Placed>?>,val top: Float,val row: Float,val left: Float,val right: Float) {
    val words get()=rows.sumOf {it?.size ?: 0}
    /** The word under a point of the page, if any (the nearest word on that line). */
    fun wordAt(x: Float,y: Float): Placed? {
        val i=((y-top)/row).toInt()
        val line=rows.getOrNull(i) ?: return null
        return line.minByOrNull {p->if(x in p.left..p.right) 0f else minOf(kotlin.math.abs(x-p.left),kotlin.math.abs(x-p.right))}
    }
    fun ordinalEndOfVerse(verse: Int)=rows.filterNotNull().flatten().filter {it.token.verse==verse}.maxOfOrNull {it.ordinal} ?: -1
}

private fun typeset(page: MushafLayout.Page,g: Geometry,typeface: Typeface): Typeset {
    val fontSize=REF*page.scale
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;textSize=fontSize}
    val space=paint.measureText(" ")*1.15f
    val kashida=paint.measureText("ـ")
    val blockWidth=if(page.compact) g.text.width*MushafLayout.OPENING_WIDTH else g.text.width
    val row=if(page.compact) g.row*OPENING else g.row
    val left=g.text.left+(g.text.width-blockWidth)/2
    val right=left+blockWidth
    val top=if(page.compact) g.text.top+(g.text.height-page.rows.size*row)/2 else g.text.top
    val cache=HashMap<String,Float>()
    var ordinal=0
    val rows=page.rows.map {r->
        (r as? MushafLayout.Line)?.let {line->
            val j=MushafLayout.justify(line.tokens,blockWidth,space,kashida,{0f}) {s->cache.getOrPut(s) {paint.measureText(s)}}
            // Right to left: every line from the right edge (a single word is centred).
            var x=if(line.tokens.size==1) (left+right)/2+j.widths[0]/2 else right
            line.tokens.mapIndexed {k,t->
                val full=j.widths[k]
                // Right to left: the word, then the verse ornament at the token's left end.
                val mark=t.aya?.let {MushafLayout.verseMark(it)}
                val text=if(mark!=null) j.texts[k].substringBefore(MushafLayout.VERSE_MARK_GAP+mark) else j.texts[k]
                val word=if(mark!=null) cache.getOrPut(text) {paint.measureText(text)} else full
                val markW=if(mark!=null) cache.getOrPut(mark) {paint.measureText(mark)} else 0f
                val placed=Placed(t,text,x-full,x,x-word,if(mark!=null) x-full+markW/2 else null,ordinal++,mark,x-full)
                x-=full+(if(k<line.tokens.lastIndex) j.gap else 0f)
                placed
            }
        }
    }
    return Typeset(page,rows,top,row,left,right)
}

private object PageCache {
    private val map=object: LinkedHashMap<String,Typeset>(16,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Typeset>?)=size>30
    }
    @Synchronized fun get(key: String)=map[key]
    @Synchronized fun put(key: String,p: Typeset) {map[key]=p}
}

/** What the reader adds to a page: verse marks and highlights, a search result to flash, and memorization progress. */
data class PageDecor(val bookmarked: Set<Int> = emptySet(),val highlighted: Set<Int> = emptySet(),val flash: Int? = null,val revealed: Int? = null,
                     /** Colour the letters by tajweed rule (see [org.najwa.core.Tajweed]). */ val tajweed: Boolean=false)

@Composable private fun MushafSheet(d: Mushaf.Data,page: Int,r: ReadingColors,bookmark: Boolean,read: Boolean,decor: PageDecor,
                                   onDoubleTap: ()->Unit,
                                   onTap: (Placed?)->Unit,onLongPress: (Placed?,Int)->Unit) {
    val context=LocalContext.current
    val density=LocalDensity.current.density
    val typeface=remember {ResourcesCompat.getFont(context,R.font.kfgqpc_hafs) ?: Typeface.SERIF}
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        // A printed page proportion, as large as the space allows.
        val maxW=constraints.maxWidth.toFloat();val maxH=constraints.maxHeight.toFloat()
        val w=minOf(maxW,maxH*.72f);val h=minOf(maxH,w/.5f)
        val g=remember(w,h) {geometry(w,h,density)}
        fun keyOf(p: Int)="$p:${g.text.width.toInt()}:${g.row.toInt()}"
        fun build(p: Int)=typeset(MushafLayout.layout(d,p,g.text.width,g.row*.47f/REF*(if(p<=2) OPENING else 1f),PaintMeasure(typeface)),g,typeface)
        val key=keyOf(page)
        val layout by produceState(PageCache.get(key),key) {
            if(value==null)value=withContext(Dispatchers.Default) {build(page).also {PageCache.put(key,it)}}
            // Prepare the pages two away as well, so quick turning never shows an empty frame.
            withContext(Dispatchers.Default) {
                listOf(page+2,page-2).filter {it in 1..Mushaf.PAGES && PageCache.get(keyOf(it))==null}.forEach {PageCache.put(keyOf(it),build(it))}
            }
        }
        val paints=remember(typeface,r) {SheetPaints(typeface,r)}
        val wDp=with(LocalDensity.current) {w.toDp()};val hDp=with(LocalDensity.current) {h.toDp()}
        // For screen readers: the page, its surahs and its verses, read in order.
        val description=remember(page) {
            val names=d.surahsOn(page).joinToString("، ") {context.getString(R.string.mushaf_surah_label,it.name)}
            val text=d.page(page).joinToString(" ") {"${d.displayText(it)} (${it.aya})"}
            context.getString(R.string.mushaf_page_desc,Counts.format(context,page.toLong()),names,text)
        }
        Canvas(Modifier.size(wDp,hDp).semantics {contentDescription=description}
            .pointerInput(page) {detectTapGestures(onTap={pt->onTap(layout?.wordAt(pt.x,pt.y))},onDoubleTap={onDoubleTap()},onLongPress={pt->
                val set=layout;val word=set?.wordAt(pt.x,pt.y)
                onLongPress(word,if(word!=null && set!=null) set.ordinalEndOfVerse(word.token.verse) else -1)})}) {
            drawSheet(g,page,r,bookmark,read,d,paints)
            layout?.let {drawPageText(it,d,r,paints,decor)}
        }
    }
}

private class SheetPaints(typeface: Typeface,r: ReadingColors) {
    val text=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {this.typeface=typeface;color=r.ink.toArgb()}
    val marker=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;color=r.accentInk.toArgb();textAlign=Paint.Align.CENTER}
    val margin=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;color=r.muted.toArgb()}
    val heading=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.typeface=typeface;color=r.accentInk.toArgb();textAlign=Paint.Align.CENTER}
    val ornament=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {this.typeface=typeface;color=r.marker.toArgb()}
}

/**
 * One word in tajweed colours: each coloured letter group is drawn as a run shaped with the whole word as context, so
 * the letters stay joined; the next word on the page is read for noon and tanween rules at the word's end.
 */
private fun drawTajweedWord(nc: android.graphics.Canvas,w: Placed,set: Typeset,baseline: Float,paint: Paint,r: ReadingColors) {
    val text=w.text
    val words=set.rows.filterNotNull().flatten()
    val at=words.indexOf(w)
    val following=words.getOrNull(at+1)?.text
    val spans=org.najwa.core.Tajweed.spans(text,following,verseEnd=w.token.aya!=null)
    if(spans.isEmpty()) {nc.drawText(text,w.wordLeft,baseline,paint);return}
    val base=paint.color
    val width=paint.getRunAdvance(text,0,text.length,0,text.length,true,text.length)
    // Runs in logical order: plain stretches between the coloured spans.
    val runs=ArrayList<Triple<Int,Int,Int>>()
    var pos=0
    spans.forEach {s->
        if(s.start>pos)runs+=Triple(pos,s.start,base)
        runs+=Triple(s.start,s.end,tajweedColor(s.rule,r).toArgb())
        pos=s.end
    }
    if(pos<text.length)runs+=Triple(pos,text.length,base)
    runs.forEach {(start,end,color)->
        // Right to left: a run's right edge is the advance from the word's start to the run's start.
        val left=w.wordLeft+width-paint.getRunAdvance(text,0,text.length,0,text.length,true,end)
        paint.color=color
        nc.drawTextRun(text,start,end,0,text.length,left,baseline,true,paint)
    }
    paint.color=base
}

/** Paper, binding shade, the ornamented frame, margin titles, page number, bookmark ribbon and the read seal. */
private fun DrawScope.drawSheet(g: Geometry,page: Int,r: ReadingColors,bookmark: Boolean,read: Boolean,d: Mushaf.Data,p: SheetPaints) {
    val dp=density
    // Paper resting on the desk.
    drawRoundRect(Color.Black.copy(alpha=if(r.page.lum()>.5f).06f else .3f),Offset(0f,2*dp),size,CornerRadius(6*dp))
    drawRoundRect(r.page,Offset.Zero,size,CornerRadius(6*dp))
    // The binding: right-hand (odd) pages are bound on their left edge, left-hand pages on their right.
    val bindLeft=page%2==1
    val shade=Color.Black.copy(alpha=if(r.page.lum()>.5f).07f else .22f)
    val bw=22*dp
    drawRect(Brush.horizontalGradient(if(bindLeft) listOf(shade,Color.Transparent) else listOf(Color.Transparent,shade),
        startX=if(bindLeft)0f else size.width-bw,endX=if(bindLeft)bw else size.width),
        topLeft=Offset(if(bindLeft)0f else size.width-bw,0f),size=Size(bw,size.height))

    // Frame: outer rule, a band with a chain of small lozenges, inner rule, star rosettes at the corners.
    val f=g.frame;val band=7*dp
    drawRect(r.band,f.topLeft,f.size)
    drawRect(r.page,Offset(f.left+band,f.top+band),Size(f.width-2*band,f.height-2*band))
    drawRect(r.marker,f.topLeft,f.size,style=Stroke(1.1f*dp))
    drawRect(r.marker,Offset(f.left+band,f.top+band),Size(f.width-2*band,f.height-2*band),style=Stroke(.7f*dp))
    val chain=r.marker.copy(alpha=.55f)
    fun lozenge(x: Float,y: Float) {
        val s=1.9f*dp
        drawPath(Path().apply {moveTo(x,y-s);lineTo(x+s,y);lineTo(x,y+s);lineTo(x-s,y);close()},chain)
    }
    val step=9*dp
    var x=f.left+band*2;while(x<f.right-band*1.5f) {lozenge(x,f.top+band/2);lozenge(x,f.bottom-band/2);x+=step}
    var y=f.top+band*2;while(y<f.bottom-band*1.5f) {lozenge(f.left+band/2,y);lozenge(f.right-band/2,y);y+=step}
    listOf(f.topLeft,Offset(f.right,f.top),Offset(f.left,f.bottom),Offset(f.right,f.bottom)).forEach {c->
        drawPath(star(c,8.5f*dp,5.2f*dp,8),r.page)
        drawPath(star(c,8.5f*dp,5.2f*dp,8),r.marker,style=Stroke(.9f*dp))
        drawCircle(r.marker,1.6f*dp,c)
    }

    // Margins: the surah at the outer top corner, the juz at the other; the page number in a medallion below.
    drawIntoCanvas {cv->
        val nc=cv.nativeCanvas
        p.margin.textSize=13*dp
        p.margin.textAlign=Paint.Align.RIGHT
        val names=d.surahsOn(page).joinToString(" · ") {"سورة ${it.name}"}
        nc.drawText(names,f.right-2*dp,f.top-10*dp,p.margin)
        p.margin.textAlign=Paint.Align.LEFT
        nc.drawText(MushafLayout.juzName(d.juzOf(page)),f.left+2*dp,f.top-10*dp,p.margin)
    }
    val pc=Offset(size.width/2,f.bottom+16*dp)
    drawPath(star(pc,12.5f*dp,10.4f*dp,8),r.band)
    drawPath(star(pc,12.5f*dp,10.4f*dp,8),r.marker,style=Stroke(.8f*dp))
    drawIntoCanvas {cv->
        p.marker.textSize=12.5f*dp
        cv.nativeCanvas.drawText(Mushaf.arabicDigits(page),pc.x,pc.y+4.3f*dp,p.marker)
    }
    if(read)drawSeal(Offset(pc.x-34*dp,pc.y),8*dp,r)

    // The khatma bookmark: a ribbon hanging over the top edge at the page you reached.
    if(bookmark) {
        val rx=f.left+f.width*.3f;val rw=13*dp;val rh=40*dp
        val ribbon=Path().apply {moveTo(rx,0f);lineTo(rx+rw,0f);lineTo(rx+rw,rh);lineTo(rx+rw/2,rh-5*dp);lineTo(rx,rh);close()}
        drawPath(ribbon,Color(0xFF8E3B2E).copy(alpha=if(r.page.lum()>.5f) 1f else .85f))
        drawLine(Color.White.copy(alpha=.25f),Offset(rx+2.5f*dp,0f),Offset(rx+2.5f*dp,rh-3*dp),.8f*dp)
    }
}

private fun Color.lum()=(.2126f*red+.7152f*green+.0722f*blue)

/**
 * The page's rows on the fifteen-line grid: surah headings, basmalah and justified lines with verse markers, with
 * highlighted and bookmarked verses, a flashed search result, and hidden words in memorization mode.
 */
private fun DrawScope.drawPageText(set: Typeset,d: Mushaf.Data,r: ReadingColors,p: SheetPaints,decor: PageDecor) {
    val dp=density
    val page=set.page
    val fontSize=REF*page.scale
    p.text.textSize=fontSize
    val row=set.row;val left=set.left;val right=set.right;val top=set.top
    // Compact opening pages sit in the middle of the frame inside their own fine border.
    if(page.compact) {
        val box=Rect(left-12*dp,top-10*dp,right+12*dp,top+page.rows.size*row+10*dp)
        drawRoundRect(r.marker,box.topLeft,box.size,CornerRadius(14*dp),style=Stroke(.8f*dp))
        drawRoundRect(r.marker.copy(alpha=.5f),Offset(box.left+4*dp,box.top+4*dp),Size(box.width-8*dp,box.height-8*dp),CornerRadius(11*dp),style=Stroke(.5f*dp))
    }
    val markerW=MARKER*fontSize
    page.rows.forEachIndexed {i,line->
        val rowTop=top+i*row
        val cy=rowTop+row/2
        val baseline=cy+fontSize*.24f
        when(line) {
            is MushafLayout.Heading -> drawHeading(d.surahs[line.surah-1],left,right,cy,row,r,p)
            is MushafLayout.Basmalah -> drawIntoCanvas {cv->
                p.text.textAlign=Paint.Align.CENTER
                cv.nativeCanvas.drawText(d.basmalah,(left+right)/2,baseline,p.text)
                p.text.textAlign=Paint.Align.LEFT
            }
            is MushafLayout.Line -> {
                val words=set.rows[i] ?: return@forEachIndexed
                // Highlights first, under the words: one soft band per verse across its words on this line.
                words.groupBy {it.token.verse}.forEach {(v,ws)->
                    val tone=when {
                        v==decor.flash -> r.marker.copy(alpha=.32f)
                        v in decor.highlighted -> r.marker.copy(alpha=.17f)
                        else -> null
                    }
                    if(tone!=null) {
                        val l=ws.minOf {it.left}-3*dp;val rr=ws.maxOf {it.right}+3*dp
                        drawRoundRect(tone,Offset(l,rowTop+row*.14f),Size(rr-l,row*.72f),CornerRadius(8*dp))
                    }
                }
                drawIntoCanvas {cv->
                    val nc=cv.nativeCanvas
                    p.text.textAlign=Paint.Align.LEFT
                    words.forEach {w->
                        val hidden=decor.revealed!=null && w.ordinal>=decor.revealed
                        if(hidden) {
                            // Memorization: the word's place, without the word.
                            drawRoundRect(r.band,Offset(w.wordLeft,cy-fontSize*.32f),Size(w.right-w.wordLeft,fontSize*.5f),CornerRadius(fontSize*.25f))
                            drawRoundRect(r.frame,Offset(w.wordLeft,cy-fontSize*.32f),Size(w.right-w.wordLeft,fontSize*.5f),CornerRadius(fontSize*.25f),style=Stroke(.8f*dp))
                        } else if(decor.tajweed) drawTajweedWord(nc,w,set,baseline,p.text,r)
                        else nc.drawText(w.text,w.wordLeft,baseline,p.text)
                        val aya=w.token.aya
                        if(aya!=null && w.mark!=null && w.markerX!=null) {
                            // The verse ornament from the Complex's font, in the marker colour.
                            p.ornament.textSize=fontSize
                            nc.drawText(w.mark,w.markLeft,baseline,p.ornament)
                            val c=Offset(w.markerX,baseline-fontSize*.3f)
                            // A bookmarked verse: a small ribbon over its marker.
                            if(w.token.verse in decor.bookmarked) {
                                val rw=markerW*.32f;val rh=markerW*.5f;val x0=c.x-rw/2;val y0=c.y-markerW*.95f
                                drawPath(Path().apply {moveTo(x0,y0);lineTo(x0+rw,y0);lineTo(x0+rw,y0+rh);lineTo(x0+rw/2,y0+rh*.72f);lineTo(x0,y0+rh);close()},Color(0xFF8E3B2E))
                            }
                        }
                    }
                }
            }
        }
    }
}


/** A surah heading: a framed band with a pointed cartouche holding the name, its verse count and Meccan or Medinan. */
private fun DrawScope.drawHeading(s: Mushaf.Surah,left: Float,right: Float,cy: Float,row: Float,r: ReadingColors,p: SheetPaints) {
    val dp=density
    val h=row*.8f
    val top=cy-h/2
    drawRect(r.band,Offset(left,top),Size(right-left,h))
    drawRect(r.marker,Offset(left,top),Size(right-left,h),style=Stroke(.9f*dp))
    drawRect(r.marker.copy(alpha=.5f),Offset(left+3*dp,top+3*dp),Size(right-left-6*dp,h-6*dp),style=Stroke(.5f*dp))
    // Cartouche with pointed ends.
    val name="سورة ${s.name}"
    p.heading.textSize=h*.5f
    val nw=p.heading.measureText(name)
    val cw=maxOf(nw+h*1.6f,(right-left)*.42f).coerceAtMost((right-left)*.8f)
    val cx=(left+right)/2
    val cart=Path().apply {
        moveTo(cx-cw/2,cy);quadraticTo(cx-cw/2+h*.45f,cy-h*.62f,cx-cw/2+h*.9f,cy-h*.62f);lineTo(cx+cw/2-h*.9f,cy-h*.62f)
        quadraticTo(cx+cw/2-h*.45f,cy-h*.62f,cx+cw/2,cy);quadraticTo(cx+cw/2-h*.45f,cy+h*.62f,cx+cw/2-h*.9f,cy+h*.62f)
        lineTo(cx-cw/2+h*.9f,cy+h*.62f);quadraticTo(cx-cw/2+h*.45f,cy+h*.62f,cx-cw/2,cy);close()
    }
    drawPath(cart,r.page)
    drawPath(cart,r.marker,style=Stroke(1.1f*dp))
    drawIntoCanvas {cv->
        val nc=cv.nativeCanvas
        nc.drawText(name,cx,cy+h*.17f,p.heading)
        // Verse count and place of revelation in the band's ends.
        p.margin.textAlign=Paint.Align.CENTER
        val side=(right-left-cw)/4
        val count="آياتها ${Mushaf.arabicDigits(s.verses)}"
        // As large as the band's end allows, never touching the cartouche.
        p.margin.textSize=h*.3f
        val room=side*2-8*dp
        val widest=maxOf(p.margin.measureText(count),p.margin.measureText("مدنية"))
        if(widest>room)p.margin.textSize*=room/widest
        nc.drawText(count,right-side,cy+h*.1f,p.margin)
        nc.drawText(if(s.meccan)"مكية" else "مدنية",left+side,cy+h*.1f,p.margin)
    }
    listOf(cx-cw/2,cx+cw/2).forEach {x->drawCircle(r.marker,1.8f*dp,Offset(x,cy))}
}

/* ───────────────────────── Index and plan ───────────────────────── */

@Composable private fun MushafIndex(d: Mushaf.Data,q: QuranStore.State,english: Boolean,r: ReadingColors,onDismiss: ()->Unit,onPage: (Int)->Unit) {
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(26.dp),color=r.page,border=BorderStroke(1.dp,r.frame),modifier=Modifier.fillMaxWidth().heightIn(max=640.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.mushaf_index),color=r.ink,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                // Back to the khatma bookmark first.
                val resume=(q.page+1).coerceIn(1,Mushaf.PAGES)
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(r.band).border(.8.dp,r.frame,RoundedCornerShape(16.dp))
                    .clickable {onPage(resume)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Canvas(Modifier.size(width=12.dp,height=22.dp)) {
                        drawPath(Path().apply {moveTo(0f,0f);lineTo(size.width,0f);lineTo(size.width,size.height);lineTo(size.width/2,size.height*.78f);lineTo(0f,size.height);close()},Color(0xFF8E3B2E))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.mushaf_resume,if(english)"$resume" else Mushaf.arabicDigits(resume)),color=r.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Text(stringResource(R.string.mushaf_khatma_mark),color=r.muted,fontSize=12.sp)
                    }
                }
                // The last page you opened, when it differs from the bookmark.
                val context=LocalContext.current
                val last=QuranStore.lastViewed(context)
                if(last!=resume)TextButton(onClick={onPage(last)}) {
                    Text(stringResource(R.string.mushaf_last_opened,Counts.format(context,last.toLong())),color=r.accentInk,fontSize=13.sp)
                }
                MarksList(d,r,onPage)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.mushaf_juz_hint),color=r.muted,fontSize=12.sp,modifier=Modifier.padding(bottom=6.dp))
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    (1..30).chunked(10).forEach {row->
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            row.forEach {j->
                                val here=d.juzOf(resume)==j
                                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(if(here)r.marker else r.band)
                                    .clickable {onPage(d.pageOfJuz(j))},contentAlignment=Alignment.Center) {
                                    Text(if(english)"$j" else Mushaf.arabicDigits(j),color=if(here)r.page else r.accentInk,fontSize=13.sp,fontWeight=FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.weight(1f,false)) {
                    items(d.surahs) {s->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {onPage(s.page)}
                            .padding(horizontal=6.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawPath(star(center,size.minDimension/2,size.minDimension/2*.84f,8),r.band)
                                    drawPath(star(center,size.minDimension/2,size.minDimension/2*.84f,8),r.marker,style=Stroke(1.dp.toPx()))
                                }
                                Text(if(english)"${s.number}" else Mushaf.arabicDigits(s.number),color=r.accentInk,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                if(english)Text(s.latin,color=r.ink,fontSize=16.sp,fontWeight=FontWeight.Bold)
                                else Text(s.name,color=r.ink,fontSize=21.sp,fontFamily=QuranFont)
                                Text(stringResource(R.string.mushaf_surah_info,stringResource(if(s.meccan)R.string.mushaf_meccan else R.string.mushaf_medinan),s.verses),
                                    color=r.muted,fontSize=12.sp)
                            }
                            Text(stringResource(R.string.mushaf_page_short,s.page),color=r.muted,fontSize=12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun StepButton(text: String,label: String,r: ReadingColors,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=CircleShape,color=r.band,border=BorderStroke(.8.dp,r.frame),
        modifier=Modifier.size(44.dp).semantics {contentDescription=label}) {
        Box(contentAlignment=Alignment.Center) {Text(text,color=r.accentInk,fontSize=22.sp,fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun PlanDialog(q: QuranStore.State,auto: Boolean,r: ReadingColors,english: Boolean,view: MushafView,memorize: Boolean,
                                  onGoal: (Int)->Unit,onAuto: (Boolean)->Unit,onView: (MushafView)->Unit,onMemorize: (Boolean)->Unit,onDismiss: ()->Unit) {
    val context=LocalContext.current
    var goal by remember {mutableIntStateOf(q.goal)}
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(26.dp),color=r.page,border=BorderStroke(1.dp,r.frame),modifier=Modifier.fillMaxWidth()) {
            // Scrolls when the dialog is taller than the screen, so "Done" is always reachable.
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)) {
                Text(stringResource(R.string.mushaf_plan_title),color=r.ink,fontSize=19.sp,fontWeight=FontWeight.Bold)
                Text(stringResource(R.string.mushaf_plan_intro),color=r.muted,fontSize=13.sp,modifier=Modifier.padding(top=4.dp,bottom=14.dp))
                // The same plan as in Settings and on the Quran page.
                KhatmaPlanPicker(goal,readingPlanColors(r)) {goal=it;onGoal(it)}
                HorizontalDivider(color=r.frame,modifier=Modifier.padding(vertical=14.dp))
                // How the page is shown: the printed page or large flowing text; and memorization mode.
                Text(stringResource(R.string.mushaf_view),color=r.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(vertical=8.dp)) {
                    listOf(MushafView.PAGE to R.string.mushaf_view_page,MushafView.LARGE to R.string.mushaf_reflow).forEach {(v,label)->
                        val on=view==v
                        Surface(onClick={onView(v)},shape=RoundedCornerShape(16.dp),color=if(on)r.marker else r.band,border=BorderStroke(.8.dp,if(on)r.marker else r.frame),
                            modifier=Modifier.weight(1f)) {
                            Text(stringResource(label),color=if(on)r.page else r.ink,fontSize=14.sp,fontWeight=FontWeight.Bold,
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center,modifier=Modifier.padding(vertical=10.dp))
                        }
                    }
                }
                Text(stringResource(R.string.mushaf_reflow_help),color=r.muted,fontSize=12.sp)
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mushaf_memorize),color=r.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Text(stringResource(R.string.mushaf_memorize_help),color=r.muted,fontSize=12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    var m by remember {mutableStateOf(memorize)}
                    Switch(m,{m=it;onMemorize(it)},colors=SwitchDefaults.colors(checkedTrackColor=r.marker,checkedThumbColor=r.page,
                        uncheckedTrackColor=r.band,uncheckedBorderColor=r.frame,uncheckedThumbColor=r.muted))
                }
                HorizontalDivider(color=r.frame,modifier=Modifier.padding(vertical=14.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.mushaf_auto_title),color=r.ink,fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Text(stringResource(R.string.mushaf_auto_text),color=r.muted,fontSize=12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    var on by remember {mutableStateOf(auto)}
                    Switch(on,{on=it;onAuto(it)},colors=SwitchDefaults.colors(checkedTrackColor=r.marker,checkedThumbColor=r.page,
                        uncheckedTrackColor=r.band,uncheckedBorderColor=r.frame,uncheckedThumbColor=r.muted))
                }
                Text(stringResource(R.string.mushaf_hint),color=r.muted,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
                Text(stringResource(R.string.mushaf_source),color=r.muted,fontSize=10.sp,modifier=Modifier.padding(top=6.dp))
                TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)) {Text(stringResource(R.string.mushaf_done),color=r.accentInk,fontWeight=FontWeight.Bold)}
            }
        }
    }
}
