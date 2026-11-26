package org.najwa.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.najwa.app.R
import org.najwa.app.platform.AdhkarProgressStore
import org.najwa.app.platform.Counts
import org.najwa.app.platform.WorshipRecorder
import org.najwa.app.platform.WorshipSettingsStore
import org.najwa.core.RoutineState
import org.najwa.app.platform.QuranStore
import org.najwa.app.platform.TasbeehStore
import org.najwa.app.platform.TasbeehWidget
import org.najwa.app.platform.juzOfPage
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Set while the tasbeeh screen counts with the volume keys; MainActivity forwards the key presses here. */
object VolumeKeyCounter {@Volatile var onPress: (()->Unit)?=null}

/** Same digits as the rest of the counter (Latin), whatever the interface language. */
/** Counts in the digits chosen in Appearance. */
private fun n(value: Int)=Counts.format(Counts.lastArabic,value.toLong())

@Composable private fun OnResume(block: ()->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(block)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)latest()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer)}
    }
}

/** A rounded choice chip. */
@Composable fun Pill(label: String,selected: Boolean,modifier: Modifier=Modifier,glyph: String?=null,onClick: ()->Unit) {
    val c=LocalPalette.current
    Surface(onClick=onClick,shape=CircleShape,color=if(selected)c.green else c.surface,border=BorderStroke(1.dp,if(selected)c.green else c.border),
        modifier=modifier.heightIn(min=44.dp).semantics {this.selected=selected}) {
        Row(Modifier.padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(glyph!=null)Glyph(glyph,if(selected)c.bg else c.mint,Modifier.size(16.dp))
            Text(uiText(label),color=if(selected)c.bg else c.text,fontSize=14.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,maxLines=1)
        }
    }
}

/** A round icon button with its label underneath. */
@Composable private fun RoundAction(glyph: String,label: String,active: Boolean=false,onClick: ()->Unit) {
    val c=LocalPalette.current
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp),
        modifier=Modifier.widthIn(min=72.dp).clip(RoundedCornerShape(16.dp)).clickable(role=Role.Button,onClick=onClick).padding(6.dp)) {
        Box(Modifier.size(52.dp).background(if(active)c.green else c.selected,CircleShape),contentAlignment=Alignment.Center) {
            Glyph(glyph,if(active)c.bg else c.mint,Modifier.size(22.dp))
        }
        Text(label,color=c.secondary,fontSize=12.sp,textAlign=TextAlign.Center,maxLines=1)
    }
}

private fun buzz(vibrator: Vibrator?,strong: Boolean) {
    runCatching {
        vibrator?.vibrate(if(strong) VibrationEffect.createWaveform(longArrayOf(0,60,70,140),-1)
            else VibrationEffect.createOneShot(16,90))
    }
}

// ───────────────────────────── Tasbeeh ─────────────────────────────

@Composable fun TasbeehScreen(onBack: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val vibrator=remember {context.getSystemService(Vibrator::class.java)}
    var s by remember {mutableStateOf(TasbeehStore.read(context))}
    var today by remember {mutableIntStateOf(TasbeehStore.today(context))}
    var week by remember {mutableIntStateOf(TasbeehStore.week(context))}
    var message by remember {mutableStateOf<String?>(null)}
    var confirmReset by rememberSaveable {mutableStateOf(false)}
    val pulse=remember {Animatable(1f)}
    fun stats() {today=TasbeehStore.today(context);week=TasbeehStore.week(context)}
    // The home-screen widget may have counted while the app was away.
    OnResume {s=TasbeehStore.read(context);stats()}
    fun change(next: TasbeehStore.State) {s=next;TasbeehStore.write(context,next);TasbeehWidget.update(context)}
    fun tap() {
        val (next,outcome)=TasbeehStore.tap(context)
        s=next;stats()
        if(next.haptics)buzz(vibrator,outcome!=TasbeehStore.Outcome.COUNTED)
        message=when(outcome) {
            TasbeehStore.Outcome.SEQUENCE->context.getString(R.string.tasbeeh_sequence_done)
            TasbeehStore.Outcome.ROUND->if(next.sequenceMode) Adhkar.tasbeeh[next.currentPhrase] else context.getString(R.string.tasbeeh_round_done,n(next.rounds))
            else->message
        }
        scope.launch {pulse.snapTo(1.08f);pulse.animateTo(1f,spring(dampingRatio=Spring.DampingRatioMediumBouncy,stiffness=Spring.StiffnessMedium))}
        TasbeehWidget.update(context)
    }
    LaunchedEffect(message) {if(message!=null) {delay(2600);message=null}}
    // Volume keys count while this screen is open (when chosen).
    val counting by rememberUpdatedState(::tap)
    DisposableEffect(s.volumeKeys) {
        VolumeKeyCounter.onPress=if(s.volumeKeys) {{counting()}} else null
        onDispose {VolumeKeyCounter.onPress=null}
    }
    val view=LocalView.current
    DisposableEffect(s.keepAwake) {view.keepScreenOn=s.keepAwake;onDispose {view.keepScreenOn=false}}

    BackLink(stringResource(R.string.tools_back),onBack)
    PageHeading(stringResource(R.string.tasbeeh_title),stringResource(R.string.tasbeeh_intro))

    // Mode: the after-prayer sequence, or a fixed target.
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Pill(stringResource(R.string.tasbeeh_after_prayer),s.sequenceMode,glyph="mosque") {change(s.copy(sequenceMode=true,step=0,count=0))}
        listOf(33,99,100,0).forEach {t->
            Pill(if(t==0)stringResource(R.string.tasbeeh_open) else n(t),!s.sequenceMode && s.target==t) {change(s.copy(sequenceMode=false,target=t,count=0))}
        }
    }
    Spacer(Modifier.height(10.dp))
    if(!s.sequenceMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Adhkar.tasbeeh.forEachIndexed {i,phrase->Pill(phrase,s.phrase==i) {change(s.copy(phrase=i,count=0))}}
    }
    Spacer(Modifier.height(20.dp))

    val target=s.currentTarget
    val progress=if(target>0) s.count/target.toFloat() else (s.count%33)/33f
    val shown by animateFloatAsState(progress,tween(260),label="tasbeeh")
    val label=stringResource(R.string.tasbeeh_tap,s.count)
    Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
        Box(Modifier.size(272.dp).scale(pulse.value).clip(CircleShape).clickable(role=Role.Button) {tap()}.semantics {contentDescription=label},
            contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val w=14.dp.toPx()
                val r=(size.minDimension-w)/2
                val tl=Offset(center.x-r,center.y-r)
                drawCircle(c.selected,r-w/2,center)
                drawArc(c.track,0f,360f,false,tl,Size(r*2,r*2),style=Stroke(w))
                drawArc(c.green,-90f,360f*shown,false,tl,Size(r*2,r*2),style=Stroke(w,cap=StrokeCap.Round))
                // A bead riding at the tip of the ring.
                val a=Math.toRadians(-90.0+360.0*shown)
                drawCircle(c.mint,w*0.75f,Offset(center.x+r*cos(a).toFloat(),center.y+r*sin(a).toFloat()))
            }
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Text(Adhkar.tasbeeh[s.currentPhrase],color=c.text,fontSize=19.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(horizontal=36.dp))
                Text(n(s.count),color=c.mint,fontSize=68.sp,fontWeight=FontWeight.Bold)
                Text(if(target>0)stringResource(R.string.tasbeeh_of,n(target)) else stringResource(R.string.tasbeeh_open),color=c.secondary,fontSize=14.sp)
            }
        }
    }
    // The three steps of the after-prayer tasbeeh.
    if(s.sequenceMode) Row(Modifier.fillMaxWidth().padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        TasbeehStore.sequence.forEachIndexed {i,(phrase,count)->
            val fill=when {i<s.step->1f;i==s.step->s.count/count.toFloat();else->0f}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.track)) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(c.green,CircleShape))
                }
                Text("${Adhkar.tasbeeh[phrase]} ${n(count)}",color=if(i==s.step)c.mint else c.secondary,fontSize=12.sp,maxLines=1,
                    fontWeight=if(i==s.step)FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min=34.dp).padding(top=10.dp),contentAlignment=Alignment.Center) {
        AnimatedVisibility(message!=null,enter=fadeIn(),exit=fadeOut()) {
            Text(message.orEmpty(),color=c.mint,fontSize=15.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        }
    }
    Text(stringResource(R.string.tasbeeh_stats,n(today),n(week),n(s.total)),color=c.secondary,fontSize=13.sp,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
        RoundAction("undo",stringResource(R.string.tasbeeh_undo)) {s=TasbeehStore.undo(context);stats();TasbeehWidget.update(context)}
        RoundAction("restore",stringResource(R.string.tasbeeh_reset)) {change(s.copy(count=0,step=0))}
        RoundAction("vibrate",stringResource(R.string.tasbeeh_haptics),s.haptics) {change(s.copy(haptics=!s.haptics))}
        RoundAction("speaker",stringResource(R.string.tasbeeh_volume_short),s.volumeKeys) {change(s.copy(volumeKeys=!s.volumeKeys))}
    }
    Spacer(Modifier.height(18.dp))
    ExpandableSettings(stringResource(R.string.tasbeeh_options),stringResource(R.string.tasbeeh_options_summary)) {
        PreferenceSwitch(stringResource(R.string.tasbeeh_volume_keys),stringResource(R.string.tasbeeh_volume_keys_help),s.volumeKeys) {change(s.copy(volumeKeys=it))}
        PreferenceSwitch(stringResource(R.string.tasbeeh_keep_awake),stringResource(R.string.tasbeeh_keep_awake_help),s.keepAwake) {change(s.copy(keepAwake=it))}
        PreferenceSwitch(stringResource(R.string.tasbeeh_haptics),stringResource(R.string.tasbeeh_haptics_help),s.haptics) {change(s.copy(haptics=it))}
        Text(stringResource(R.string.tasbeeh_widget_hint),color=c.secondary,fontSize=12.sp)
        TextButton(onClick={confirmReset=true}) {Text(stringResource(R.string.tasbeeh_reset_all),color=c.amber)}
    }
    if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text(stringResource(R.string.tasbeeh_reset_all))},
        text={Text(stringResource(R.string.tasbeeh_reset_all_confirm))},
        confirmButton={TextButton(onClick={confirmReset=false;change(s.copy(count=0,total=0,rounds=0,step=0))}) {Text(stringResource(R.string.tasbeeh_reset_all),color=c.amber)}},
        dismissButton={TextButton(onClick={confirmReset=false}) {Text(stringResource(R.string.action_cancel))}})
}

// ───────────────────────────── Adhkar ─────────────────────────────

enum class AdhkarCategory(val key: String,val title: Int,val glyph: String,val tone: String,val items: ()->List<Dhikr>) {
    MORNING("morning",R.string.adhkar_morning,"sun","amber",{Adhkar.morning}),
    EVENING("evening",R.string.adhkar_evening,"moon","blue",{Adhkar.evening}),
    AFTER_PRAYER("after",R.string.adhkar_after_prayer,"beads","teal",{Adhkar.afterPrayer}),
    SLEEP("sleep",R.string.adhkar_sleep,"bed","violet",{Adhkar.sleep}),
    WAKING("waking",R.string.adhkar_waking,"sunrise","coral",{Adhkar.waking}),
    MOSQUE("mosque",R.string.adhkar_mosque,"mosque","mint",{Adhkar.mosque}),
}

/**
 * The adhkar tab (a root of the bottom bar). Each dhikr is a card of its own, turned like pages: its text in a
 * classical naskh (Quranic passages in the Complex's font), and under it one large counter. Tapping the card or the
 * counter counts; a finished dhikr turns to the next by itself. A list view keeps the whole category on one page.
 */
@Composable fun AdhkarScreen(initialTab: Int) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val vibrator=remember {context.getSystemService(Vibrator::class.java)}
    val worship=remember {WorshipSettingsStore.read(context)}
    val companion=worship.enabled
    val ui=remember {context.getSharedPreferences("adhkar_view",android.content.Context.MODE_PRIVATE)}
    var listView by remember {mutableStateOf(ui.getBoolean("list",false))}
    var tab by rememberSaveable {mutableIntStateOf(initialTab.coerceIn(0,AdhkarCategory.entries.lastIndex))}
    val category=AdhkarCategory.entries[tab]
    val items=category.items()
    var remaining by remember(tab) {mutableStateOf(AdhkarProgressStore.remaining(context,category.key,items.size) ?: items.map {it.count})}
    var streak by remember(tab) {mutableIntStateOf(AdhkarProgressStore.streak(context,category.key))}
    var scale by remember {mutableFloatStateOf(AdhkarProgressStore.textScale(context))}
    // Taps that Undo can take back, newest last (this sitting only; every count is already saved).
    val history=remember(tab) {mutableStateListOf<Int>()}
    // Focus (from the companion settings): no numbers or statistics around the dhikr.
    val focus=companion && worship.focus
    var confirmRestart by remember {mutableStateOf(false)}
    var statusTick by remember {mutableIntStateOf(0)}
    // Dhikr texts use the calm reading colours (paper or night), like the mushaf.
    val appDark=LocalPalette.current.dark
    var mode by remember(appDark) {mutableStateOf(ReadingPrefs.resolve(context,appDark))}
    val r=readingColors(mode)
    val a=accent("teal")
    val pager=key(tab) {rememberPagerState(remaining.indexOfFirst {it>0}.coerceAtLeast(0)) {items.size}}
    fun save(next: List<Int>) {
        val before=remaining
        remaining=next
        AdhkarProgressStore.saveRemaining(context,category.key,next)
        streak=AdhkarProgressStore.streak(context,category.key)
        // Worship records follow only changes in which items are complete (a completion, or an undo that reopens one).
        if(companion && before.map {it==0}!=next.map {it==0})WorshipRecorder.onAdhkar(context,category.key,items,next)
        statusTick++
    }
    fun tap(i: Int) {
        val left=remaining.getOrElse(i) {0}
        if(left<=0)return
        buzz(vibrator,left==1)
        history.add(i)
        val next=remaining.toMutableList().also {it[i]=left-1}
        save(next)
        // Finished: after a breath, the next open dhikr slides in.
        if(left==1 && !listView) {
            val open=(i+1 until items.size).firstOrNull {next[it]>0} ?: (0 until i).firstOrNull {next[it]>0}
            if(open!=null)scope.launch {delay(520);pager.animateScrollToPage(open)}
        }
    }
    fun undo() {
        val i=history.removeLastOrNull() ?: return
        if(remaining[i]<items[i].count)save(remaining.toMutableList().also {it[i]=it[i]+1})
        if(!listView)scope.launch {pager.animateScrollToPage(i)}
    }

    // Title, with the switch between cards and the list.
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.adhkar_title),color=c.text,fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.semantics {heading()})
            Text(stringResource(R.string.adhkar_intro),color=c.secondary,fontSize=13.sp)
        }
        Row(Modifier.clip(CircleShape).background(c.surface).border(1.dp,c.border,CircleShape).padding(3.dp)) {
            listOf(false to stringResource(R.string.adhkar_view_cards),true to stringResource(R.string.adhkar_view_list)).forEach {(list,label)->
                val on=listView==list
                Text(label,Modifier.clip(CircleShape).background(if(on)c.selected else Color.Transparent)
                    .selectable(on,role=Role.Tab) {listView=list;ui.edit().putBoolean("list",list).apply()}
                    .padding(horizontal=12.dp,vertical=7.dp),color=if(on)c.mint else c.secondary,fontSize=12.sp,fontWeight=if(on)FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // The six categories as one scrolling row of pills; a tick marks a category finished today.
    val statuses=remember(statusTick) {AdhkarCategory.entries.associateWith {cat->
        val list=cat.items()
        AdhkarProgressStore.remaining(context,cat.key,list.size)?.let {st->RoutineState(st.count {it==0},list.size)}
    }}
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        AdhkarCategory.entries.forEach {cat->
            val selected=cat==category
            val complete=statuses[cat]?.complete==true
            Row(Modifier.clip(CircleShape).background(if(selected)c.mint else c.surface).border(1.dp,if(selected)c.mint else c.border,CircleShape)
                .selectable(selected,role=Role.Tab) {tab=cat.ordinal}.padding(horizontal=14.dp,vertical=9.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                Glyph(if(complete)"check" else cat.glyph,if(selected)c.bg else c.mint,Modifier.size(17.dp))
                Text(stringResource(cat.title),color=if(selected)c.bg else c.text,fontSize=13.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,maxLines=1)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val done=remaining.count {it==0}
    // Where you stand: one segment per dhikr (finished, current, ahead).
    if(!focus) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                items.indices.forEach {i->
                    val color by androidx.compose.animation.animateColorAsState(when {
                        remaining[i]==0 -> a.ink
                        !listView && i==pager.currentPage -> a.ink.copy(alpha=.45f)
                        else -> c.track
                    },tween(250),label="segment")
                    Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(color))
                }
            }
            Text(stringResource(R.string.day_of_five,n(done),n(items.size)),color=c.secondary,fontSize=12.sp)
            if(streak>0)Text(stringResource(R.string.streak_days,n(streak)),color=a.ink,fontSize=11.sp,fontWeight=FontWeight.Bold,
                modifier=Modifier.background(a.tint,CircleShape).padding(horizontal=9.dp,vertical=3.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
    if(done==items.size) {
        Panel(Modifier.fillMaxWidth().padding(bottom=14.dp),radius=22,selected=true) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).background(c.green,CircleShape),contentAlignment=Alignment.Center) {Glyph(if(companion)"shield" else "check",c.bg,Modifier.size(24.dp))}
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adhkar_done_title,stringResource(category.title)),color=c.text,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text(stringResource(if(companion && (category==AdhkarCategory.MORNING || category==AdhkarCategory.EVENING))R.string.routine_done_blessing else R.string.adhkar_done_text),
                        color=c.secondary,fontSize=13.sp)
                }
            }
        }
    }
    if(!listView) {
        val height=(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp*.42f).coerceIn(280f,520f).dp
        HorizontalPager(pager,Modifier.fillMaxWidth().height(height),pageSpacing=12.dp,key={"$tab-$it"}) {i->
            DhikrCard(items[i],remaining.getOrElse(i) {0},i,items.size,r,a,scale,focus) {tap(i)}
        }
        Spacer(Modifier.height(18.dp))
        // One large counter for the dhikr in view, between previous and next.
        val i=pager.currentPage.coerceIn(0,items.lastIndex)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            RoundIcon("back",stringResource(R.string.adhkar_previous),i>0) {scope.launch {pager.animateScrollToPage(i-1)}}
            DhikrCounter(remaining.getOrElse(i) {0},items[i].count,a,focus) {tap(i)}
            RoundIcon("chevron",stringResource(R.string.adhkar_next),i<items.lastIndex) {scope.launch {pager.animateScrollToPage(i+1)}}
        }
    } else items.forEachIndexed {i,dhikr->
        val left=remaining.getOrElse(i) {0}
        if(left==0) {
            // Completed: folded to one quiet line, so the next dhikr rises under your finger.
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Glyph("check",a.ink,Modifier.size(18.dp))
                Text(dhikr.text.lineSequence().last(),Modifier.weight(1f),color=c.secondary,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,fontFamily=dhikrFont(dhikr.text))
            }
        } else Surface(onClick={tap(i)},shape=RoundedCornerShape(22.dp),color=r.page,border=BorderStroke(1.dp,r.frame),
            modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).animateContentSize()) {
            Column(Modifier.padding(horizontal=20.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(dhikr.text,color=r.ink,fontSize=(21*scale).sp,lineHeight=(42*scale).sp,fontFamily=dhikrFont(dhikr.text),
                    style=LocalTextStyle.current.copy(textDirection=TextDirection.Rtl,textAlign=TextAlign.Start))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(dhikr.source,Modifier.weight(1f),color=r.muted,fontSize=12.sp)
                    CountRing(dhikr.count-left,dhikr.count,a)
                }
            }
        }
    }
    // Tools: undo, text size, reading colours and starting over.
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).border(1.dp,c.border,RoundedCornerShape(20.dp)).padding(horizontal=6.dp,vertical=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
        TextButton(onClick={undo()},enabled=history.isNotEmpty()) {Glyph("undo",if(history.isNotEmpty())c.mint else c.track,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(stringResource(R.string.undo),fontSize=13.sp)}
        TextButton(onClick={scale=(scale-.1f).coerceAtLeast(.8f);AdhkarProgressStore.setTextScale(context,scale)},Modifier.size(48.dp),
            contentPadding=PaddingValues(0.dp)) {Text("أ−",color=c.mint,fontSize=15.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_smaller)})}
        TextButton(onClick={scale=(scale+.1f).coerceAtMost(1.6f);AdhkarProgressStore.setTextScale(context,scale)},Modifier.size(48.dp),
            contentPadding=PaddingValues(0.dp)) {Text("أ+",color=c.mint,fontSize=19.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_larger)})}
        ReadingModeButton(mode) {mode=it}
        val restartLabel=stringResource(R.string.adhkar_restart)
        TextButton(onClick={
            // Starting over loses today's progress in this category, so ask first when there is any.
            if(companion && remaining.indices.any {remaining[it]<items[it].count})confirmRestart=true
            else {save(items.map {it.count});scope.launch {pager.scrollToPage(0)}}
        },Modifier.semantics {contentDescription=restartLabel}) {Glyph("restore",c.mint,Modifier.size(18.dp))}
    }
    Text(stringResource(R.string.adhkar_source_note),color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
    if(confirmRestart)AlertDialog(onDismissRequest={confirmRestart=false},title={Text(stringResource(R.string.adhkar_restart))},
        text={Text(stringResource(R.string.restart_confirm_text))},
        confirmButton={TextButton(onClick={
            // A new session: what was completed stays recorded; the next full reading is a separate record.
            WorshipRecorder.onAdhkarRestart(context,category.key)
            history.clear()
            save(items.map {it.count})
            scope.launch {pager.scrollToPage(0)}
            confirmRestart=false
        }) {Text(stringResource(R.string.restart_confirm))}},
        dismissButton={TextButton(onClick={confirmRestart=false}) {Text(stringResource(R.string.action_cancel))}})
}

/** Quranic passages (Uthmani spelling, with the wasla alif) take the Complex's font; the rest a classical naskh. */
private fun dhikrFont(text: String)=if('ٱ' in text) QuranFont else FontFamily.Serif

/** One dhikr as a page: its place and repetitions, the text centred, a small ornament, and its source. */
@Composable private fun DhikrCard(d: Dhikr,left: Int,index: Int,total: Int,r: ReadingColors,a: Accent,scale: Float,focus: Boolean,onTap: ()->Unit) {
    val finished=left==0
    val shape=RoundedCornerShape(28.dp)
    Surface(onClick=onTap,shape=shape,color=r.page,border=BorderStroke(1.dp,if(finished)a.ink.copy(alpha=.5f) else r.frame),modifier=Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(!focus)Text(stringResource(R.string.day_of_five,n(index+1),n(total)),color=r.muted,fontSize=12.sp)
                Spacer(Modifier.weight(1f))
                if(finished)Row(Modifier.background(a.tint,CircleShape).padding(horizontal=10.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {Glyph("check",a.ink,Modifier.size(14.dp));Text(stringResource(R.string.day_routine_done),color=a.ink,fontSize=12.sp,fontWeight=FontWeight.Bold)}
                else if(d.count>1)Text(stringResource(R.string.adhkar_times,n(d.count)),color=r.accentInk,fontSize=12.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.border(1.dp,r.frame,CircleShape).padding(horizontal=10.dp,vertical=4.dp))
            }
            // Short texts sit in the middle of the card; long ones scroll inside it.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val min=maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().heightIn(min=min).padding(vertical=10.dp),verticalArrangement=Arrangement.Center) {
                        Text(d.text,Modifier.fillMaxWidth().graphicsLayer {alpha=if(finished).55f else 1f},color=r.ink,fontSize=(21*scale).sp,lineHeight=(42*scale).sp,
                            fontFamily=dhikrFont(d.text),style=LocalTextStyle.current.copy(textDirection=TextDirection.Rtl,textAlign=TextAlign.Center))
                    }
                }
            }
            // A thin ornament, then the source.
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(r.frame))
                Box(Modifier.padding(horizontal=10.dp).size(7.dp).graphicsLayer {rotationZ=45f}.background(r.marker))
                Box(Modifier.weight(1f).height(1.dp).background(r.frame))
            }
            Text(d.source,Modifier.fillMaxWidth(),color=r.muted,fontSize=12.sp,textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

/** The large counter: a ring that fills with each repetition, what is left in the middle, a gentle press. */
@Composable private fun DhikrCounter(left: Int,total: Int,a: Accent,focus: Boolean,onTap: ()->Unit) {
    val c=LocalPalette.current
    val scope=rememberCoroutineScope()
    val press=remember {Animatable(1f)}
    val shown by animateFloatAsState(if(total<=0) 0f else (total-left)/total.toFloat(),tween(240),label="counter")
    val label=stringResource(R.string.adhkar_count_label,n(left))
    Box(Modifier.size(132.dp).graphicsLayer {scaleX=press.value;scaleY=press.value}.clip(CircleShape)
        .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(a.tint,c.surface)))
        .clickable(role=Role.Button,onClickLabel=label) {onTap();scope.launch {press.snapTo(.93f);press.animateTo(1f,spring(dampingRatio=.45f,stiffness=600f))}}
        .semantics {contentDescription=label},contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val w=6.dp.toPx();val rad=size.minDimension/2-w/2
            drawCircle(c.track,rad,style=Stroke(w))
            drawArc(a.ink,-90f,360f*shown,false,Offset(center.x-rad,center.y-rad),Size(rad*2,rad*2),style=Stroke(w,cap=StrokeCap.Round))
        }
        if(left==0)Glyph("check",a.ink,Modifier.size(42.dp))
        else Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text(n(left),color=a.ink,fontSize=38.sp,fontWeight=FontWeight.Bold,lineHeight=42.sp)
            Text(if(left==total || focus)stringResource(R.string.adhkar_tap_count) else stringResource(R.string.path_of,total),color=c.secondary,fontSize=11.sp)
        }
    }
}

/** A quiet round button for previous / next. */
@Composable private fun RoundIcon(glyph: String,label: String,enabled: Boolean,onClick: ()->Unit) {
    val c=LocalPalette.current
    Box(Modifier.size(52.dp).clip(CircleShape).background(c.surface).border(1.dp,c.border,CircleShape)
        .clickable(enabled=enabled,role=Role.Button,onClickLabel=label,onClick=onClick).semantics {contentDescription=label},
        contentAlignment=Alignment.Center) {Glyph(glyph,if(enabled)c.mint else c.track,Modifier.size(20.dp))}
}

/** A small ring that fills with each repetition and shows how many are left. */
@Composable private fun CountRing(done: Int,total: Int,a: Accent) {
    val c=LocalPalette.current
    val shown by animateFloatAsState(done/total.toFloat(),tween(220),label="ring")
    Box(Modifier.size(50.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w=4.dp.toPx();val r=size.minDimension/2-w/2
            drawCircle(a.tint,r)
            drawCircle(c.track,r,style=Stroke(w))
            drawArc(a.ink,-90f,360f*shown,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(w,cap=StrokeCap.Round))
        }
        Text(n(total-done),color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold)
    }
}

// ───────────────────────────── Quran reading ─────────────────────────────

/** The mushaf tab: continue reading, the khatma plan and the day's portion (a root of the bottom bar). */
@Composable fun QuranScreen(onMushaf: (Int?,Boolean)->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    var q by remember {mutableStateOf(QuranStore.read(context))}
    var today by remember {mutableIntStateOf(QuranStore.today(context))}
    var streak by remember {mutableIntStateOf(QuranStore.streak(context))}
    var pageDialog by rememberSaveable {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val week=remember(today) {QuranStore.days(context).filterKeys {!it.isBefore(org.najwa.app.platform.WorshipDay.today(context).minusDays(6))}.values.sum()}
    val companion=remember {WorshipSettingsStore.read(context).enabled}
    var canUndo by remember {mutableStateOf(companion && WorshipRecorder.lastQuranRange(context)!=null)}
    val letters=remember(today) {if(companion) org.najwa.app.platform.WorshipStore.entries(context,org.najwa.app.platform.WorshipDay.today(context))
        .filter {it.action==org.najwa.app.platform.WorshipStore.QURAN_RANGE}.map {org.najwa.app.platform.QuranPageLetters.letters(context,it.from,it.count.toInt())}
        .takeIf {it.isNotEmpty() && it.all {n->n!=null}}?.sumOf {it ?: 0L} else null}
    fun refresh(next: QuranStore.State,before: QuranStore.State) {
        val earlier=today
        q=next;today=QuranStore.today(context);streak=QuranStore.streak(context)
        canUndo=companion && WorshipRecorder.lastQuranRange(context)!=null
        message=if(next.khatmas>before.khatmas)context.getString(R.string.quran_khatma_done)
            else if(earlier<next.goal && today>=next.goal)context.getString(R.string.quran_goal_done) else null
    }
    // Pages read outside the app. Right after reading in the mushaf, ask first, so the same reading is not counted twice.
    var confirmOutside by remember {mutableStateOf<Int?>(null)}
    fun addOutside(pages: Int,confirmed: Boolean=false) {
        val recent=QuranStore.recentMushafPages(context)
        if(!confirmed && recent>0) {confirmOutside=pages;return}
        val before=q;refresh(QuranStore.addPages(context,pages),before)
    }
    val a=accent("teal")
    PageHeading(stringResource(R.string.quran_title),stringResource(R.string.mushaf_intro))
    // The mushaf itself: continue where you stopped, or choose a surah or juz.
    val last=remember {QuranStore.lastViewed(context)}
    Panel(Modifier.fillMaxWidth().padding(bottom=14.dp),radius=24,selected=true) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                IconBadge("mushaf",accent("amber"),46)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mushaf_title),color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.mushaf_continue_hint,n(last),n(juzOfPage(last))),color=c.secondary,fontSize=13.sp)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={onMushaf(null,false)},modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(16.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=c.green,contentColor=c.bg)) {Text(stringResource(R.string.mushaf_continue),fontWeight=FontWeight.Bold)}
                OptionButton(stringResource(R.string.mushaf_index),false,Modifier.weight(1f)) {onMushaf(null,true)}
            }
        }
    }
    Panel(Modifier.fillMaxWidth(),radius=24) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            val progress by animateFloatAsState(q.page/QuranStore.PAGES.toFloat(),tween(400),label="khatma")
            Box(Modifier.size(112.dp),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w=10.dp.toPx();val r=size.minDimension/2-w/2
                    drawArc(c.track,0f,360f,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(w))
                    drawArc(a.ink,-90f,360f*progress,false,Offset(center.x-r,center.y-r),Size(r*2,r*2),style=Stroke(w,cap=StrokeCap.Round))
                }
                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("${n((progress*100).toInt())}%",color=c.text,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.quran_of_khatma),color=c.secondary,fontSize=11.sp)
                }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(q.page==0)stringResource(R.string.quran_start) else stringResource(R.string.quran_khatma_at,n(q.page),n(juzOfPage(q.page))),
                    color=c.text,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(stringResource(R.string.quran_khatmas,n(q.khatmas)),color=c.secondary,fontSize=13.sp)
                if(streak>0)Text(stringResource(R.string.streak_days,n(streak)),color=a.ink,fontSize=12.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.background(a.tint,CircleShape).padding(horizontal=10.dp,vertical=3.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsCard(stringResource(R.string.quran_today_title),stringResource(R.string.quran_today_pages,Counts.format(context,today.toLong()),Counts.pages(context,q.goal))) {
        val shown by animateFloatAsState((today/q.goal.toFloat()).coerceAtMost(1f),tween(300),label="today")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(if(today>=q.goal)c.green else a.ink,CircleShape))
        }
        // Reading in the app's mushaf records itself; these buttons are for a printed mushaf or memory.
        Text(stringResource(R.string.quran_add_outside),color=c.text,fontSize=14.sp,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.quran_add_outside_help),color=c.secondary,fontSize=12.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(1,2,5,10).forEach {pages->
                // A left-to-right mark keeps the plus before the number in Arabic layouts.
                OptionButton("‎+${n(pages)}",false,Modifier.weight(1f)) {addOutside(pages)}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OptionButton(stringResource(R.string.quran_add_juz),false,Modifier.weight(1f)) {addOutside(20)}
            OptionButton(stringResource(R.string.quran_set_page),false,Modifier.weight(1f)) {pageDialog=true}
        }
        AnimatedVisibility(message!=null) {Text(message.orEmpty(),color=c.mint,fontSize=14.sp,fontWeight=FontWeight.Bold)}
        Text(stringResource(R.string.quran_week,n(week)),color=c.secondary,fontSize=12.sp)
        if(letters!=null && letters>0)Text(stringResource(R.string.quran_letters_today,java.text.NumberFormat.getIntegerInstance(Locale.US).format(letters)),color=c.secondary,fontSize=12.sp)
        // A mistaken entry can be taken back; it restores the page, the day's count and its record.
        if(canUndo)TextButton(onClick={val before=q;QuranStore.undoLast(context)?.let {refresh(it,before);message=null}}) {
            Text(stringResource(R.string.quran_undo),color=c.mint)
        }
    }
    confirmOutside?.let {pages->
        AlertDialog(onDismissRequest={confirmOutside=null},
            text={Text(stringResource(R.string.quran_recent_mushaf,Counts.pages(context,QuranStore.recentMushafPages(context))))},
            confirmButton={TextButton(onClick={confirmOutside=null;addOutside(pages,true)}) {Text(stringResource(R.string.quran_add_anyway))}},
            dismissButton={TextButton(onClick={confirmOutside=null}) {Text(stringResource(R.string.action_cancel))}})
    }
    // Completed khatmas, newest first.
    val log=remember(q.khatmas) {QuranStore.khatmaLog(context)}
    if(log.isNotEmpty())SettingsCard(stringResource(R.string.khatma_log_title)) {
        log.take(12).forEachIndexed {i,(date,days)->
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(Counts.digits(context,date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
                    .withLocale(context.resources.configuration.locales[0]))),Modifier.weight(1f),color=c.text,fontSize=14.sp)
                if(days!=null)Text(stringResource(R.string.count_days,Counts.format(context,days.toLong())),color=c.secondary,fontSize=12.sp)
                Spacer(Modifier.width(8.dp))
                Text("#${n(log.size-i)}",color=c.mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
    SettingsCard(stringResource(R.string.quran_goal),stringResource(R.string.quran_goal_help)) {
        // The same khatma plan as in Settings and the mushaf.
        KhatmaPlanPicker(q.goal,appPlanColors()) {q=QuranStore.read(context);org.najwa.app.platform.DayWidget.update(context)}
    }
    if(pageDialog) {
        var text by rememberSaveable {mutableStateOf(if(q.page>0)q.page.toString() else "")}
        val value=text.toIntOrNull()
        val valid=value!=null && value in 0..QuranStore.PAGES
        AlertDialog(onDismissRequest={pageDialog=false},title={Text(stringResource(R.string.quran_set_page_title))},text={
            OutlinedTextField(text,{text=it.filter(Char::isDigit).take(3)},label={Text(stringResource(R.string.quran_page_label))},singleLine=true,isError=text.isNotEmpty() && !valid,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        },confirmButton={TextButton(enabled=valid,onClick={val before=q;refresh(QuranStore.moveTo(context,value ?: 0),before);pageDialog=false}) {Text(stringResource(R.string.action_save))}},
            dismissButton={TextButton(onClick={pageDialog=false}) {Text(stringResource(R.string.action_cancel))}})
    }
}
