package org.najwa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.najwa.app.R
import org.najwa.app.platform.Counts
import org.najwa.app.platform.QuranStore
import org.najwa.app.platform.prayerNameResource
import org.najwa.core.Preferences
import org.najwa.core.Prayer
import org.najwa.core.WIRD_PRAYERS
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Colours for the plan picker, so the same control sits in the app's settings and on the mushaf page. */
data class PlanColors(val surface: Color,val selected: Color,val ink: Color,val muted: Color,val accent: Color,val onAccent: Color,val border: Color)

@Composable fun appPlanColors(): PlanColors {
    val c=LocalPalette.current
    return PlanColors(c.surface,c.selected,c.text,c.secondary,c.mint,c.bg,c.border)
}
fun readingPlanColors(r: ReadingColors)=PlanColors(r.band,r.band,r.ink,r.muted,r.marker,r.page,r.frame)

/** Khatma lengths offered everywhere, with their names. */
val khatmaOptions=listOf(7 to R.string.mushaf_plan_7,10 to R.string.mushaf_plan_10,15 to R.string.mushaf_plan_15,
    20 to R.string.mushaf_plan_20,30 to R.string.mushaf_plan_30,60 to R.string.mushaf_plan_60)

/**
 * The one khatma plan: pick how long the khatma takes (a dated plan whose daily portion adapts if you fall behind),
 * or fine-tune pages a day; see the day it ends. Used by Settings, the Quran page and the mushaf, all writing the same plan.
 */
@Composable fun KhatmaPlanPicker(goal: Int,colors: PlanColors,onChanged: (Int)->Unit) {
    val context=LocalContext.current
    var current by remember(goal) {mutableIntStateOf(goal)}
    var planDays by remember {mutableIntStateOf(QuranStore.planDays(context))}
    var version by remember {mutableIntStateOf(0)}
    fun refresh() {current=QuranStore.read(context).goal;planDays=QuranStore.planDays(context);version++;onChanged(current)}
    val position=remember(version) {QuranStore.read(context).page}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        khatmaOptions.chunked(3).forEach {row->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                row.forEach {(days,label)->
                    // Pages a day to finish in this many days from where you are now.
                    val pages=((QuranStore.PAGES-position)+days-1)/days
                    val on=planDays==days
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if(on)colors.accent else colors.surface)
                        .border(.8.dp,if(on)colors.accent else colors.border,RoundedCornerShape(16.dp))
                        .clickable {QuranStore.setPlan(context,days);refresh()}.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(stringResource(label),color=if(on)colors.onAccent else colors.ink,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
                        Text(Counts.pages(context,pages.coerceAtLeast(1)),color=if(on)colors.onAccent.copy(alpha=.85f) else colors.muted,fontSize=11.sp,maxLines=1)
                    }
                }
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(top=4.dp)) {
            PlanStep("−",stringResource(R.string.mushaf_plan_less),colors) {QuranStore.setGoal(context,current-1);refresh()}
            Text(stringResource(R.string.mushaf_plan_pages,Counts.format(context,current.toLong())),color=colors.ink,fontSize=15.sp,
                fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),textAlign=TextAlign.Center)
            PlanStep("+",stringResource(R.string.mushaf_plan_more),colors) {QuranStore.setGoal(context,current+1);refresh()}
        }
        val finish=remember(current,version) {QuranStore.finishDate(context)}
        val date=finish.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(context.resources.configuration.locales[0]))
        Text(stringResource(R.string.mushaf_plan_finish,Counts.digits(context,date)),
            color=colors.accent,fontSize=13.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
        // With a dated plan, the portion grows when you fall behind so the khatma still ends on time.
        if(planDays>0)Text(stringResource(R.string.plan_adaptive_note),color=colors.muted,fontSize=11.sp,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center)
    }
}

@Composable private fun PlanStep(text: String,label: String,colors: PlanColors,onClick: ()->Unit) {
    Surface(onClick=onClick,shape=CircleShape,color=colors.surface,border=BorderStroke(.8.dp,colors.border),
        modifier=Modifier.size(44.dp).semantics {contentDescription=label}) {
        Box(contentAlignment=Alignment.Center) {Text(text,color=colors.accent,fontSize=22.sp,fontWeight=FontWeight.Bold)}
    }
}

/**
 * The worship plan in Settings: the khatma, the reminder of the day's portion and the after-prayer card in one place.
 * The mushaf, the Quran page, the home card, the widget and the reminders all read from what is set here.
 */
@Composable fun WorshipPlanCard(p: Preferences,onSave: (Preferences)->Unit) {
    val context=LocalContext.current
    val c=LocalPalette.current
    var goal by remember {mutableIntStateOf(QuranStore.read(context).goal)}
    var auto by remember {mutableStateOf(QuranStore.autoRecord(context))}
    var qada by remember {mutableIntStateOf(org.najwa.app.platform.QadaPlan.perDay(context))}
    SettingsCard(stringResource(R.string.plan_section_title),stringResource(R.string.plan_section_help)) {
        // The Quran: the khatma plan, its reminder and how pages are recorded.
        Text(stringResource(R.string.mushaf_plan_title),color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold)
        KhatmaPlanPicker(goal,appPlanColors()) {goal=it;org.najwa.app.platform.DayWidget.update(context)}
        PreferenceSwitch(stringResource(R.string.wird_reminder_title),stringResource(R.string.wird_reminder_help),p.wirdReminder) {onSave(p.copy(wirdReminder=it))}
        if(p.wirdReminder)OptionGrid(listOf(Prayer.FAJR,Prayer.ASR,Prayer.ISHA).filter {it in WIRD_PRAYERS},p.wirdReminderAfter,
            {context.getString(R.string.wird_reminder_after,context.getString(prayerNameResource(it)))},3) {onSave(p.copy(wirdReminderAfter=it))}
        PreferenceSwitch(stringResource(R.string.mushaf_auto_title),stringResource(R.string.mushaf_auto_text),auto) {auto=it;QuranStore.setAutoRecord(context,it)}
        PreferenceSwitch(stringResource(R.string.kahf_reminder),stringResource(R.string.kahf_reminder_help),p.kahfReminder) {onSave(p.copy(kahfReminder=it))}
        // Adhkar: morning and evening, and sleep at a chosen time after Isha.
        PreferenceSwitch(stringResource(R.string.adhkar_reminders),stringResource(R.string.adhkar_reminders_help),p.adhkarReminders) {onSave(p.copy(adhkarReminders=it))}
        PreferenceSwitch(stringResource(R.string.sleep_reminder_title),stringResource(R.string.sleep_reminder_help),p.sleepReminder) {onSave(p.copy(sleepReminder=it))}
        if(p.sleepReminder)OptionGrid((listOf(60,90,120,180)+p.sleepReminderMinutes).distinct().sorted(),p.sleepReminderMinutes,
            {Counts.digits(context,context.getString(R.string.minutes_short,it))},4) {onSave(p.copy(sleepReminderMinutes=it))}
        // Prayers: the after-prayer card (needs the log), the daily make-up share and the Friday summary.
        PreferenceSwitch(stringResource(R.string.after_prayer_card_title),
            if(p.prayerLog)stringResource(R.string.after_prayer_card_help) else stringResource(R.string.after_prayer_card_needs_log),
            p.prayerLog && p.afterPrayerCard) {if(p.prayerLog)onSave(p.copy(afterPrayerCard=it))}
        Text(stringResource(R.string.qada_plan_title),color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold)
        Text(stringResource(R.string.qada_plan_help),color=c.secondary,fontSize=12.sp)
        OptionGrid(listOf(0,1,2,5),qada,{if(it==0)context.getString(R.string.qada_off) else Counts.format(context,it.toLong())},4) {
            qada=it;org.najwa.app.platform.QadaPlan.setPerDay(context,it);org.najwa.app.platform.DayWidget.update(context)}
        PreferenceSwitch(stringResource(R.string.weekly_setting_title),stringResource(R.string.weekly_setting_help),p.weeklySummary) {onSave(p.copy(weeklySummary=it))}
    }
}
