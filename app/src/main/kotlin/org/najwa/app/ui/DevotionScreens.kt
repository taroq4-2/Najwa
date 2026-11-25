package org.najwa.app.ui

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
private fun n(value: Int)=String.format(Locale.ROOT,"%d",value)

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

@Composable fun AdhkarScreen(initialTab: Int,onBack: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val vibrator=remember {context.getSystemService(Vibrator::class.java)}
    var tab by rememberSaveable {mutableIntStateOf(initialTab.coerceIn(0,AdhkarCategory.entries.lastIndex))}
    val category=AdhkarCategory.entries[tab]
    val items=category.items()
    var remaining by remember(tab) {mutableStateOf(AdhkarProgressStore.remaining(context,category.key,items.size) ?: items.map {it.count})}
    var streak by remember(tab) {mutableIntStateOf(AdhkarProgressStore.streak(context,category.key))}
    var scale by remember {mutableFloatStateOf(AdhkarProgressStore.textScale(context))}
    val a=accent(category.tone)
    fun save(next: List<Int>) {
        remaining=next
        AdhkarProgressStore.saveRemaining(context,category.key,next)
        streak=AdhkarProgressStore.streak(context,category.key)
    }

    BackLink(stringResource(R.string.tools_back),onBack)
    PageHeading(stringResource(R.string.adhkar_title),stringResource(R.string.adhkar_intro))
    // Six categories as coloured tiles, three per row.
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        AdhkarCategory.entries.chunked(3).forEach {row->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach {cat->
                    val ca=accent(cat.tone)
                    val selected=cat==category
                    Surface(onClick={tab=cat.ordinal},shape=RoundedCornerShape(18.dp),color=if(selected)ca.tint else c.surface,
                        border=BorderStroke(if(selected)1.5.dp else 1.dp,if(selected)ca.ink else c.border),
                        modifier=Modifier.weight(1f).fillMaxHeight().semantics {this.selected=selected}) {
                        Column(Modifier.padding(vertical=12.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Glyph(cat.glyph,ca.ink,Modifier.size(24.dp))
                            Text(stringResource(cat.title),color=if(selected)ca.ink else c.text,fontSize=13.sp,maxLines=1,
                                fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    val done=remaining.count {it==0}
    val shown by animateFloatAsState(done/items.size.toFloat(),tween(300),label="adhkar")
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
                Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(a.ink,CircleShape))
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.adhkar_progress,done,items.size),color=c.secondary,fontSize=12.sp)
                if(streak>0)Text(stringResource(R.string.streak_days,n(streak)),color=a.ink,fontSize=12.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.background(a.tint,CircleShape).padding(horizontal=10.dp,vertical=3.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        // Text size, remembered for every category.
        TextButton(onClick={scale=(scale-.1f).coerceAtLeast(.8f);AdhkarProgressStore.setTextScale(context,scale)},Modifier.size(48.dp),
            contentPadding=PaddingValues(0.dp)) {Text("أ−",color=c.mint,fontSize=15.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_smaller)})}
        TextButton(onClick={scale=(scale+.1f).coerceAtMost(1.6f);AdhkarProgressStore.setTextScale(context,scale)},Modifier.size(48.dp),
            contentPadding=PaddingValues(0.dp)) {Text("أ+",color=c.mint,fontSize=19.sp,modifier=Modifier.semantics {contentDescription=context.getString(R.string.adhkar_larger)})}
    }
    Spacer(Modifier.height(12.dp))
    if(done==items.size) {
        Panel(Modifier.fillMaxWidth().padding(bottom=12.dp),radius=22,selected=true) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).background(c.green,CircleShape),contentAlignment=Alignment.Center) {Glyph("check",c.bg,Modifier.size(24.dp))}
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adhkar_done_title,stringResource(category.title)),color=c.text,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.adhkar_done_text),color=c.secondary,fontSize=13.sp)
                }
            }
        }
    }
    items.forEachIndexed {i,dhikr->
        val left=remaining.getOrElse(i) {0}
        if(left==0) {
            // Completed: folded to one quiet line, so the next dhikr rises under your finger.
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(horizontal=14.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Glyph("check",a.ink,Modifier.size(18.dp))
                Text(dhikr.text.lineSequence().last(),Modifier.weight(1f),color=c.secondary,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        } else Surface(onClick={
                val next=remaining.toMutableList().also {it[i]=left-1}
                buzz(vibrator,left==1)
                save(next)
            },shape=RoundedCornerShape(22.dp),color=c.surface,border=BorderStroke(1.dp,c.border),
            modifier=Modifier.fillMaxWidth().padding(bottom=10.dp).animateContentSize()) {
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(dhikr.text,color=c.text,fontSize=(19*scale).sp,lineHeight=(34*scale).sp,
                    style=LocalTextStyle.current.copy(textDirection=TextDirection.Rtl,textAlign=TextAlign.Start))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(dhikr.source,Modifier.weight(1f),color=c.secondary,fontSize=12.sp)
                    CountRing(dhikr.count-left,dhikr.count,a)
                }
            }
        }
    }
    TextButton(onClick={save(items.map {it.count})}) {Text(stringResource(R.string.adhkar_restart))}
    Text(stringResource(R.string.adhkar_source_note),color=c.secondary,fontSize=11.sp)
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

@Composable fun QuranScreen(onBack: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    var q by remember {mutableStateOf(QuranStore.read(context))}
    var today by remember {mutableIntStateOf(QuranStore.today(context))}
    var streak by remember {mutableIntStateOf(QuranStore.streak(context))}
    var pageDialog by rememberSaveable {mutableStateOf(false)}
    var message by remember {mutableStateOf<String?>(null)}
    val week=remember(today) {QuranStore.days(context).filterKeys {!it.isBefore(java.time.LocalDate.now().minusDays(6))}.values.sum()}
    fun refresh(next: QuranStore.State,before: QuranStore.State) {
        val earlier=today
        q=next;today=QuranStore.today(context);streak=QuranStore.streak(context)
        message=if(next.khatmas>before.khatmas)context.getString(R.string.quran_khatma_done)
            else if(earlier<next.goal && today>=next.goal)context.getString(R.string.quran_goal_done) else null
    }
    val a=accent("teal")
    BackLink(stringResource(R.string.tools_back),onBack)
    PageHeading(stringResource(R.string.quran_title),stringResource(R.string.quran_intro))
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
                Text(if(q.page==0)stringResource(R.string.quran_start) else stringResource(R.string.quran_position,n(q.page),n(juzOfPage(q.page))),
                    color=c.text,fontSize=16.sp,fontWeight=FontWeight.Bold)
                Text(stringResource(R.string.quran_khatmas,n(q.khatmas)),color=c.secondary,fontSize=13.sp)
                if(streak>0)Text(stringResource(R.string.streak_days,n(streak)),color=a.ink,fontSize=12.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.background(a.tint,CircleShape).padding(horizontal=10.dp,vertical=3.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsCard(stringResource(R.string.quran_today_title),stringResource(R.string.quran_today,n(today),n(q.goal))) {
        val shown by animateFloatAsState((today/q.goal.toFloat()).coerceAtMost(1f),tween(300),label="today")
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(if(today>=q.goal)c.green else a.ink,CircleShape))
        }
        Text(stringResource(R.string.quran_add),color=c.text,fontSize=14.sp,fontWeight=FontWeight.Bold)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(1,2,5,10).forEach {pages->
                // A left-to-right mark keeps the plus before the number in Arabic layouts.
                OptionButton("‎+${n(pages)}",false,Modifier.weight(1f)) {val before=q;refresh(QuranStore.addPages(context,pages),before)}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OptionButton(stringResource(R.string.quran_add_juz),false,Modifier.weight(1f)) {val before=q;refresh(QuranStore.addPages(context,20),before)}
            OptionButton(stringResource(R.string.quran_set_page),false,Modifier.weight(1f)) {pageDialog=true}
        }
        AnimatedVisibility(message!=null) {Text(message.orEmpty(),color=c.mint,fontSize=14.sp,fontWeight=FontWeight.Bold)}
        Text(stringResource(R.string.quran_week,n(week)),color=c.secondary,fontSize=12.sp)
    }
    SettingsCard(stringResource(R.string.quran_goal),stringResource(R.string.quran_goal_help)) {
        OptionGrid(listOf(1,2,4,5,10,20),q.goal,{context.getString(R.string.quran_pages,n(it))},3) {QuranStore.setGoal(context,it);q=QuranStore.read(context)}
        val days=(QuranStore.PAGES-q.page+q.goal-1)/q.goal
        Text(stringResource(R.string.quran_estimate,n(days)),color=c.secondary,fontSize=13.sp)
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
