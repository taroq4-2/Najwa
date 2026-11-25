package org.najwa.app.ui

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.najwa.app.R
import org.najwa.app.platform.DaySummary
import org.najwa.app.platform.WorshipSettings
import org.najwa.app.platform.WorshipSettingsStore
import org.najwa.app.platform.WorshipStore
import org.najwa.app.platform.WorshipSummary
import org.najwa.core.DailyGoal
import org.najwa.core.EvidenceStatus
import org.najwa.core.GoalKind
import org.najwa.core.VirtueKind
import java.text.NumberFormat
import java.util.Locale

private val numbers: NumberFormat get()=NumberFormat.getIntegerInstance(Locale.US)
private fun num(v: Long)=numbers.format(v)

/** The system "remove animations" setting; animations then jump to their end state. */
@Composable fun reducedMotion(): Boolean {
    val context=LocalContext.current
    return remember {runCatching {Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f}.getOrDefault(false)}
}

/**
 * The luminous dhikr path: points along a gentle curve light up as repetitions are confirmed.
 * At most 33 points are drawn; for larger counts each point stands for several repetitions,
 * while the number shown is always the exact count.
 */
@Composable fun LuminousPath(done: Int,total: Int,a: Accent,modifier: Modifier=Modifier,large: Boolean=false) {
    val c=LocalPalette.current
    val reduced=reducedMotion()
    val points=total.coerceIn(1,33)
    val target=if(total<=0) 0f else done.coerceIn(0,total)*points/total.toFloat()
    val lit by animateFloatAsState(target,if(reduced) snap() else tween(180),label="path")
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val left=(total-done).coerceAtLeast(0)
    val description=stringResource(R.string.path_description,done,total)
    Column(modifier.semantics {contentDescription=description},horizontalAlignment=Alignment.CenterHorizontally) {
        if(large) {
            Text(String.format(Locale.ROOT,"%d",done),color=c.text,fontSize=56.sp,fontWeight=FontWeight.Bold)
            Text(stringResource(R.string.path_of,total),color=c.secondary,fontSize=13.sp)
            Spacer(Modifier.height(8.dp))
        }
        Box(contentAlignment=Alignment.CenterEnd) {
            Canvas(Modifier.width(if(large)280.dp else 150.dp).height(if(large)64.dp else 38.dp)) {
                val w=size.width;val h=size.height
                val pad=if(large)10.dp.toPx() else 6.dp.toPx()
                // A shallow arc echoing Najwa's orbit lines; quadratic Bézier from start to end.
                val start=Offset(pad,pad);val end=Offset(w-pad,pad);val ctrl=Offset(w/2,h*1.6f-pad)
                fun at(t: Float)=Offset((1-t)*(1-t)*start.x+2*(1-t)*t*ctrl.x+t*t*end.x,(1-t)*(1-t)*start.y+2*(1-t)*t*ctrl.y+t*t*end.y)
                val path=Path().apply {moveTo(start.x,start.y);quadraticTo(ctrl.x,ctrl.y,end.x,end.y)}
                drawPath(path,c.track,style=Stroke(1.5.dp.toPx()))
                val r=(if(large)4.5.dp else 3.dp).toPx()
                for(i in 0 until points) {
                    val t=if(points==1) .5f else i/(points-1f)
                    val p=at(if(rtl)1-t else t)
                    val glow=(lit-i).coerceIn(0f,1f)
                    if(glow>0f) {
                        drawCircle(a.ink.copy(alpha=.22f*glow),r*2.4f,p)
                        drawCircle(a.ink.copy(alpha=.35f+.65f*glow),r,p)
                    } else drawCircle(c.track,r*.72f,p)
                }
            }
            if(!large)Box(Modifier.size(34.dp).background(if(left==0)a.ink else a.tint,CircleShape),contentAlignment=Alignment.Center) {
                Text(String.format(Locale.ROOT,"%d",left),color=if(left==0)c.bg else c.text,fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
        }
    }
}

/** One compact card on the home screen: today's prayers, reading and adhkar, and — when rules are active — the hoped-for reward. */
@Composable fun DaySummaryCard(settings: WorshipSettings,refresh: Int,onPrayers: ()->Unit,onQuran: ()->Unit,onAdhkar: (Int)->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val s=remember(refresh) {runCatching {WorshipSummary.build(context,settings)}.getOrNull()} ?: return
    var details by rememberSaveable {mutableStateOf(false)}
    val gold=accent("amber");val teal=accent("teal");val blue=accent("blue")
    Panel(Modifier.fillMaxWidth().padding(bottom=14.dp),radius=24) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.day_title),Modifier.weight(1f),color=c.text,fontSize=17.sp,fontWeight=FontWeight.Bold)
                if(s.preview)Text(stringResource(R.string.worship_preview_badge),color=gold.ink,fontSize=11.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.background(gold.tint,CircleShape).padding(horizontal=10.dp,vertical=3.dp))
            }
            // Reward and goal share one block so the same number never appears twice.
            val goal=s.goal
            if(goal!=null || s.virtues.hasanat!=null) Surface(onClick={details=true},shape=RoundedCornerShape(18.dp),color=gold.tint) {
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    if(goal!=null) {
                        Text(stringResource(R.string.day_goal_title),color=gold.ink,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Text(goalValue(s.goalKind,goal.value),color=c.text,fontSize=20.sp,fontWeight=FontWeight.Bold)
                        val shown by animateFloatAsState(goal.permille/1000f,tween(400),label="goal")
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
                            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(gold.ink,CircleShape))
                        }
                        Text(if(goal.reached)stringResource(R.string.day_goal_reached) else stringResource(R.string.day_goal_left,num(goal.remaining),num(goal.target)),
                            color=c.secondary,fontSize=12.sp)
                    } else {
                        Text(stringResource(R.string.day_reward_title),color=gold.ink,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Text(stringResource(R.string.day_unit_hasanat,num(s.virtues.hasanat ?: 0)),color=c.text,fontSize=20.sp,fontWeight=FontWeight.Bold)
                    }
                    if(s.virtues.hasanat!=null)Text(stringResource(R.string.day_reward_note),color=c.secondary,fontSize=11.sp)
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DayTile(stringResource(R.string.day_prayers_title),String.format(Locale.ROOT,"%d/5",s.prayed),"check",teal,Modifier.weight(1f),onPrayers)
                DayTile(stringResource(R.string.day_quran_title),String.format(Locale.ROOT,"%d/%d",s.pages,s.pageGoal),"mushaf",blue,Modifier.weight(1f),onQuran)
                val morningFirst=!s.morning.complete
                val r=if(morningFirst)s.morning else s.evening
                DayTile(stringResource(if(morningFirst)R.string.adhkar_morning else R.string.adhkar_evening),
                    if(r.complete)stringResource(R.string.day_routine_done) else stringResource(R.string.day_routine_left,r.left),
                    if(r.complete)"shield" else if(morningFirst)"sun" else "moon",accent(if(morningFirst)"amber" else "violet"),Modifier.weight(1f)) {onAdhkar(if(morningFirst)0 else 1)}
            }
            if(s.virtues.qualified.isNotEmpty()) Surface(onClick={details=true},shape=RoundedCornerShape(16.dp),color=c.surface,border=BorderStroke(1.dp,c.border)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Glyph("sparkle",c.secondary,Modifier.size(18.dp))
                    Text(stringResource(R.string.day_forgiveness_line,s.virtues.expiationActions),Modifier.weight(1f),color=c.text,fontSize=13.sp)
                    Text(stringResource(R.string.day_details),color=c.mint,fontSize=13.sp,fontWeight=FontWeight.Bold)
                }
            }
        }
    }
    if(details)EvidenceDialog(s) {details=false}
}

@Composable private fun goalValue(kind: GoalKind,value: Long)=when(kind) {
    GoalKind.HASANAT->stringResource(R.string.day_unit_hasanat,num(value))
    GoalKind.QURAN_PAGES->stringResource(R.string.day_unit_pages,num(value))
    GoalKind.ADHKAR_ITEMS->stringResource(R.string.day_unit_adhkar,num(value))
}

@Composable private fun DayTile(label: String,value: String,glyph: String,a: Accent,modifier: Modifier,onClick: ()->Unit) {
    val c=LocalPalette.current
    Surface(onClick=onClick,shape=RoundedCornerShape(18.dp),color=c.surface,border=BorderStroke(1.dp,c.border),modifier=modifier.fillMaxHeight()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            IconBadge(glyph,a,34)
            Text(value,color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold,maxLines=2)
            Text(label,color=c.secondary,fontSize=11.sp,maxLines=2)
        }
    }
}

/** Localized one-line summary of each rule (pending review with the rule itself). */
private val virtueText=mapOf(
    "quran.letters" to R.string.virtue_quran_letters,
    "prayer.five" to R.string.virtue_prayer_five,
    "prayer.jamaah" to R.string.virtue_prayer_jamaah,
    "dhikr.tasbih_bihamdihi.100" to R.string.virtue_dhikr_tasbih_bihamdihi_100,
    "dhikr.tahlil.100" to R.string.virtue_dhikr_tahlil_100,
    "dhikr.tahlil.10" to R.string.virtue_dhikr_tahlil_10,
    "dhikr.tasbih.100" to R.string.virtue_dhikr_tasbih_100,
    "routine.after_prayer.100" to R.string.virtue_routine_after_prayer_100,
    "dhikr.salawat" to R.string.virtue_dhikr_salawat,
    "dhikr.kursi.sleep" to R.string.virtue_dhikr_kursi_sleep,
    "dhikr.muawwidhat.morning" to R.string.virtue_dhikr_muawwidhat_morning,
    "dhikr.muawwidhat.evening" to R.string.virtue_dhikr_muawwidhat_evening,
    "dhikr.bismillah.morning" to R.string.virtue_dhikr_bismillah_morning,
    "dhikr.bismillah.evening" to R.string.virtue_dhikr_bismillah_evening,
    "dhikr.kalimat.evening" to R.string.virtue_dhikr_kalimat_evening,
    "dhikr.sayyid_istighfar.morning" to R.string.virtue_dhikr_sayyid_istighfar_morning,
    "dhikr.sayyid_istighfar.evening" to R.string.virtue_dhikr_sayyid_istighfar_evening)

/** What today's records relate to, with the evidence and its review state. Never a count of erased sins. */
@Composable fun EvidenceDialog(s: DaySummary,onDismiss: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.evidence_title))},text={
        Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reward_disclaimer),color=c.secondary,fontSize=12.sp)
            if(s.virtues.qualified.isEmpty())Text(stringResource(R.string.evidence_none),color=c.text,fontSize=14.sp)
            s.virtues.qualified.forEach {q->
                val rule=q.rule
                val res=virtueText[rule.id] ?: 0
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Glyph(when {rule.has(VirtueKind.PROTECTION)->"shield";rule.has(VirtueKind.EXPIATION)->"sparkle";else->"star"},c.mint,Modifier.size(16.dp))
                        Text(if(res!=0)stringResource(res) else rule.id,Modifier.weight(1f),color=c.text,fontSize=14.sp,fontWeight=FontWeight.Bold)
                        if(q.times>1 && !rule.perUnit)Text("×${q.times}",color=c.secondary,fontSize=12.sp)
                    }
                    Text(rule.evidence.joinToString(" · ") {"${it.collection} ${it.reference}"},color=c.secondary,fontSize=12.sp)
                    Text(stringResource(if(rule.status==EvidenceStatus.APPROVED)R.string.evidence_approved else R.string.evidence_pending),
                        color=if(rule.status==EvidenceStatus.APPROVED)c.mint else accent("amber").ink,fontSize=11.sp)
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.close))}})
}

/** Worship companion settings: master switch, home card, goal, widget strip, preview (trial only) and deleting the records. */
@Composable fun WorshipCompanionSettings() {
    val c=LocalPalette.current
    val context=LocalContext.current
    var w by remember {mutableStateOf(WorshipSettingsStore.read(context))}
    fun save(next: WorshipSettings) {w=next;WorshipSettingsStore.write(context,next)}
    var confirmDelete by remember {mutableStateOf(false)}
    var custom by remember {mutableStateOf(false)}
    SettingsCard(stringResource(R.string.companion_title),stringResource(R.string.companion_help)) {
        PreferenceSwitch(stringResource(R.string.companion_enable),stringResource(R.string.companion_enable_help),w.enabled) {save(w.copy(enabled=it))}
        if(w.enabled) {
            PreferenceSwitch(stringResource(R.string.companion_summary),stringResource(R.string.companion_summary_help),w.summary) {save(w.copy(summary=it))}
            PreferenceSwitch(stringResource(R.string.companion_focus),stringResource(R.string.companion_focus_help),w.focus) {save(w.copy(focus=it))}
        }
    }
    if(w.enabled)SettingsCard(stringResource(R.string.goal_title),stringResource(R.string.goal_help)) {
        OptionGrid(GoalKind.entries.toList(),w.goalKind,{context.getString(when(it) {
            GoalKind.HASANAT->R.string.goal_kind_hasanat;GoalKind.QURAN_PAGES->R.string.goal_kind_pages;GoalKind.ADHKAR_ITEMS->R.string.goal_kind_adhkar})},3) {
            save(w.copy(goalKind=it,goal=0))
        }
        if(w.goalKind==GoalKind.HASANAT && !w.previewActive)
            Text(stringResource(R.string.goal_hasanat_pending),color=accent("amber").ink,fontSize=12.sp)
        val presets=when(w.goalKind) {GoalKind.HASANAT->DailyGoal.PRESETS;GoalKind.QURAN_PAGES->listOf(2L,5L,10L,20L);GoalKind.ADHKAR_ITEMS->listOf(10L,20L,30L,50L)}
        OptionGrid(listOf(0L)+presets,if(w.goal in presets || w.goal==0L) w.goal else -1L,{if(it==0L)context.getString(R.string.goal_off) else num(it)},5) {save(w.copy(goal=it))}
        TextButton(onClick={custom=true}) {Text(stringResource(R.string.goal_custom))}
        Text(stringResource(R.string.goal_note),color=c.secondary,fontSize=12.sp)
    }
    if(w.enabled)SettingsCard(stringResource(R.string.widget_day_settings),stringResource(R.string.widget_day_settings_help)) {
        PreferenceSwitch(stringResource(R.string.widget_day_goal),"",w.widgetReward) {save(w.copy(widgetReward=it))}
        PreferenceSwitch(stringResource(R.string.widget_day_forgiveness),"",w.widgetExpiation) {save(w.copy(widgetExpiation=it))}
        PreferenceSwitch(stringResource(R.string.widget_day_adhkar),"",w.widgetAdhkar) {save(w.copy(widgetAdhkar=it))}
    }
    SettingsCard(stringResource(R.string.companion_review_title),stringResource(R.string.reward_disclaimer)) {
        Text(stringResource(R.string.companion_review_help),color=c.secondary,fontSize=12.sp)
        if(WorshipSettingsStore.previewAvailable)
            PreferenceSwitch(stringResource(R.string.companion_preview),stringResource(R.string.companion_preview_help),w.preview) {save(w.copy(preview=it))}
        TextButton(onClick={confirmDelete=true}) {Text(stringResource(R.string.companion_delete),color=accent("coral").ink)}
    }
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text(stringResource(R.string.companion_delete))},
        text={Text(stringResource(R.string.companion_delete_help))},
        confirmButton={TextButton(onClick={WorshipStore.clear(context);org.najwa.app.platform.DayWidget.update(context);confirmDelete=false}) {Text(stringResource(R.string.companion_delete_confirm))}},
        dismissButton={TextButton(onClick={confirmDelete=false}) {Text(stringResource(R.string.action_cancel))}})
    if(custom) {
        var text by rememberSaveable {mutableStateOf(if(w.goal>0)w.goal.toString() else "")}
        val value=text.toLongOrNull()
        val valid=value!=null && DailyGoal.valid(value)
        AlertDialog(onDismissRequest={custom=false},title={Text(stringResource(R.string.goal_custom))},text={
            OutlinedTextField(text,{text=it.filter(Char::isDigit).take(10)},singleLine=true,isError=text.isNotEmpty() && !valid,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        },confirmButton={TextButton(enabled=valid,onClick={save(w.copy(goal=value ?: 0));custom=false}) {Text(stringResource(R.string.action_save))}},
            dismissButton={TextButton(onClick={custom=false}) {Text(stringResource(R.string.action_cancel))}})
    }
}
