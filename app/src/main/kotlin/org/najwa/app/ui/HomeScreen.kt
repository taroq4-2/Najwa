package org.najwa.app.ui

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.core.*
import java.time.Instant
import java.time.Duration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role

@Composable fun HomeScreen(p: Preferences,now: Instant,day: PrayerDay,t: Timeline?,capable: Boolean,onCity: ()->Unit,onSilence: ()->Unit,activeUntil: Instant?=null,onQibla: ()->Unit={},
    tomorrow: PrayerDay?=null,logged: Set<Prayer> = emptySet(),onTogglePrayed: ((Prayer)->Unit)?=null,onTool: (Screen)->Unit={},mosqueName: String?=null) {
    val c=LocalPalette.current
    val largeText=LocalDensity.current.fontScale>1.4f
    val focus=t?.let {prayerCountdown(listOf(it.previous,it.next),now,p)}
    val iqama=focus?.phase==CountdownPhase.IQAMA
    val accent=if(iqama)c.amber else c.mint
    val start=focus?.start
    val remainingProgress=if(focus!=null && start!=null && start<focus.time) {
        (Duration.between(now,focus.time).toMillis().toDouble()/Duration.between(start,focus.time).toMillis())
            .toFloat().coerceIn(0f,1f)
    } else null
    var calendar by remember {mutableStateOf(false)}
    if(calendar)PrayerCalendarDialog(p,now) {calendar=false}
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        val markShape=RoundedCornerShape(15.dp)
        Box(Modifier.size(44.dp).background(c.selected,markShape).border(1.dp,c.border,markShape),
            contentAlignment=Alignment.Center) {Mark(Modifier.size(32.dp))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.en_app_name),fontSize=26.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold,color=c.text,
                modifier=Modifier.semantics {heading()})
            if(!largeText)Text(stringResource(R.string.en_home_tagline),fontSize=11.sp,lineHeight=17.sp,color=c.secondary)
        }
        Surface(onClick=onCity,color=c.surface,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,c.border)) {
            Row(Modifier.widthIn(max=if(largeText)110.dp else 160.dp).heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Glyph("pin",c.mint,Modifier.size(16.dp))
                Text(p.city.name,Modifier.weight(1f,false),maxLines=1,overflow=TextOverflow.Ellipsis,color=c.text,fontSize=13.sp)
                Glyph("down",modifier=Modifier.size(12.dp))
            }
        }
    }
    if(largeText)Column(Modifier.fillMaxWidth().padding(top=20.dp,bottom=13.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(localizedUiHijriDate(day.date,p.hijriOffset),color=c.mint,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
        Text(localizedUiDate(day.date),color=c.secondary,fontSize=11.sp,lineHeight=18.sp)
    } else Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=13.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(localizedUiHijriDate(day.date,p.hijriOffset),Modifier.weight(1f),color=c.mint,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
        Text(localizedUiDate(day.date),color=c.secondary,fontSize=11.sp,lineHeight=18.sp)
    }
    val heroShape=RoundedCornerShape(28.dp)
    // The hero card follows the theme: deep green at night, a soft daylight card in light mode.
    val light=!c.dark
    val heroAccent=when {
        light && iqama -> Color(0xFF9A6418)
        light -> Color(0xFF0F7A55)
        iqama -> Color(0xFFF0C787)
        else -> Color(0xFFA5E8C4)
    }
    val heroStart=when {
        light && iqama -> Color(0xFFF8EBD3)
        light -> Color(0xFFDDF2E6)
        iqama -> Color(0xFF3A2E22)
        else -> Color(0xFF184B3C)
    }
    val heroText=if(light)Color(0xFF10302A) else Color(0xFFF6F4EC)
    val heroDetail=if(light)Color(0xFF3F6157) else Color(0xFFC4D9CE)
    val heroTrack=if(light)Color(0xFFC9E0D4) else Color(0xFF416157)
    val heroStops=if(light)listOf(heroStart,Color(0xFFEFF8F3),Color(0xFFFBFDFC)) else listOf(heroStart,Color(0xFF0E2D26),Color(0xFF081C19))
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Box(Modifier.fillMaxWidth().shadow(if(light)8.dp else 0.dp,heroShape).clip(heroShape)
        .background(Brush.linearGradient(heroStops))
        .border(1.dp,heroAccent.copy(alpha=.28f),heroShape)) {
        Canvas(Modifier.matchParentSize()) {
            val edge=if(rtl)size.width*.35f else size.width*.65f
            val center=Offset(edge,0f)
            val radius=maxOf(size.width*.65f,size.height*.72f)
            drawCircle(heroAccent.copy(alpha=.035f),radius+84.dp.toPx(),center)
            drawCircle(heroAccent.copy(alpha=.035f),radius+42.dp.toPx(),center)
            drawCircle(heroStart.copy(alpha=.24f),radius,center)
            drawCircle(heroAccent.copy(alpha=.11f),radius,center,
                style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val glow=Offset(if(rtl)24.dp.toPx() else size.width-24.dp.toPx(),0f)
            drawCircle(Brush.radialGradient(listOf(heroAccent.copy(alpha=.18f),Color.Transparent),glow,64.dp.toPx()),
                64.dp.toPx(),glow)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=23.dp,vertical=20.dp)) {
            if(focus!=null) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(heroAccent,RoundedCornerShape(50)))
                    Text(stringResource(if(iqama)R.string.en_home_until_iqama else R.string.en_home_next_prayer),
                        color=heroAccent,fontSize=12.sp,lineHeight=18.sp)
                }
                Text(localizedPrayerName(focus.prayer,if(iqama)focus.start ?: focus.time else focus.time,p),Modifier.padding(top=7.dp).semantics {heading()},
                    color=heroText,fontSize=34.sp,fontWeight=FontWeight.Bold,lineHeight=42.sp)
                Text("${localizedUiTime(focus.time,p)} · ${p.city.name}",color=heroDetail,
                    fontSize=12.sp,lineHeight=18.sp,maxLines=2,overflow=TextOverflow.Ellipsis,
                    style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                Spacer(Modifier.height(14.dp))
                CountdownDisplay(Duration.between(now,focus.time).seconds.coerceAtLeast(0),heroAccent,true)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(if(iqama)R.string.system_to_iqama else R.string.system_to_adhan),
                    color=heroDetail,fontSize=11.sp,lineHeight=17.sp)
                if(remainingProgress!=null) {
                    Canvas(Modifier.padding(top=10.dp).fillMaxWidth().height(3.dp)) {
                        val corner=CornerRadius(size.height/2f)
                        drawRoundRect(heroTrack,cornerRadius=corner)
                        val filled=size.width*remainingProgress
                        if(filled>0f) {
                            val left=if(rtl)size.width-filled else 0f
                            val colors=if(rtl)listOf(heroAccent.copy(alpha=.68f),heroAccent)
                                else listOf(heroAccent,heroAccent.copy(alpha=.68f))
                            drawRoundRect(Brush.horizontalGradient(colors,left,left+filled),
                                Offset(left,0f),Size(filled,size.height),corner)
                        }
                    }
                }
            } else {
                Text(stringResource(org.najwa.app.platform.dayIssueResource(day.issue)),color=heroAccent)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val active=activeUntil?.isAfter(now)==true
    Surface(onClick=onSilence,color=c.surface,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,c.border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).background(if(active || p.autoSilent && capable)c.selected else c.bg,RoundedCornerShape(16.dp)),
                contentAlignment=Alignment.Center) {Glyph("sound",if(active || p.autoSilent && capable)c.mint else c.secondary)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(when {active->stringResource(R.string.en_home_silent_active);p.autoSilent && capable->stringResource(R.string.en_home_silent_ready);p.autoSilent->stringResource(R.string.en_home_silent_needs_setup);else->stringResource(R.string.en_home_silent_off)},color=c.text,fontWeight=FontWeight.Bold,fontSize=13.sp)
                Text(if(active)stringResource(R.string.en_home_ringer_return,localizedUiTime(activeUntil!!,p)) else stringResource(R.string.en_home_silence_summary),color=c.secondary,fontSize=11.sp)
            }
            Glyph("chevron",modifier=Modifier.size(16.dp))
        }
    }
    RamadanCard(p,now,day,tomorrow)
    SecondCityCard(p,now)
    Spacer(Modifier.height(10.dp))
    ToolsGrid(p) {if(it==Screen.QIBLA)onQibla() else onTool(it)}
    if(p.dailyDua)DuaOfDay(now.atZone(p.city.zoneId).toLocalDate())
    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(stringResource(R.string.en_home_today_times),Modifier.weight(1f).semantics {heading()},color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold)
        TextButton(onClick={calendar=true}) {Text(stringResource(R.string.en_home_upcoming_days),fontSize=12.sp)}
    }
    Panel(Modifier.fillMaxWidth(),radius=21) {
        day.events.forEachIndexed {index,event->
            val highlighted=if(iqama)event.id==t?.previous?.id else event.id==t?.next?.id
            val passed=event.time<=now && !highlighted
            val color=if(highlighted)accent else if(passed)c.secondary else c.text
            Row(Modifier.fillMaxWidth().background(if(highlighted)accent.copy(alpha=.09f) else Color.Transparent)
                .padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).background(if(highlighted)accent.copy(alpha=.14f) else c.bg,RoundedCornerShape(14.dp)),
                    contentAlignment=Alignment.Center) {
                    Glyph(when {event.prayer==Prayer.SUNRISE->"sun";highlighted->"dot";passed->"check";else->"clock"},color,Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(localizedPrayerName(event.prayer,event.time,p),color=color,fontSize=if(p.largeDisplay)19.sp else 15.sp,fontWeight=if(highlighted)FontWeight.Bold else FontWeight.Normal)
                    if(p.showIqamaCountdown && event.prayer.isPrayer)Text(stringResource(R.string.en_home_iqama_row,
                        localizedUiTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p)),color=c.secondary,fontSize=12.sp)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(localizedUiTime(event.time,p),color=color,fontSize=if(p.largeDisplay)20.sp else 16.sp,fontWeight=FontWeight.Bold,
                        style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                    if(highlighted)Text(if(iqama)stringResource(R.string.en_home_iqama_soon) else stringResource(R.string.en_home_next),
                        color=accent,fontSize=11.sp,fontWeight=FontWeight.Medium)
                }
                // Private prayer log: a tap marks a prayer whose time has come as prayed.
                if(onTogglePrayed!=null && event.prayer.isPrayer && event.time<=now) {
                    val done=event.prayer in logged
                    val label=stringResource(R.string.log_mark,localizedPrayerName(event.prayer))
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(if(done)c.green else c.bg)
                        .border(1.dp,if(done)c.green else c.border,RoundedCornerShape(50))
                        .clickable(role=Role.Checkbox) {onTogglePrayed(event.prayer)}.semantics {contentDescription=label;selected=done},
                        contentAlignment=Alignment.Center) {if(done)Glyph("check",c.bg,Modifier.size(16.dp))}
                }
            }
            if(index<day.events.lastIndex)SettingsDivider()
        }
    }
    if(p.showExtraTimes)extraTimes(day,tomorrow)?.let {extra->
        Panel(Modifier.fillMaxWidth().padding(top=10.dp),radius=21) {
            listOf(R.string.extra_duha to extra.duha,R.string.extra_midnight to extra.midnight,R.string.extra_last_third to extra.lastThird).forEachIndexed {i,(label,time)->
                if(i>0)SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp)) {
                    Text(stringResource(label),Modifier.weight(1f),color=c.secondary,fontSize=14.sp)
                    Text(localizedUiTime(time,p),color=c.text,fontSize=14.sp,style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                }
            }
        }
    }
    Text(listOfNotNull("${p.city.name} · ${localizedMethodName(p.method)}",mosqueName?.let {stringResource(R.string.home_mosque,it)}).joinToString(" · "),
        color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
}

/** Six quick tools under the countdown. */
@Composable private fun ToolsGrid(p: Preferences,onTool: (Screen)->Unit) {
    val c=LocalPalette.current
    val bearing=remember(p.city) {kotlin.math.round(qiblaBearing(p.city)).toInt()}
    val context=androidx.compose.ui.platform.LocalContext.current
    val quran=remember {org.najwa.app.platform.QuranStore.read(context)}
    val tools=listOf(
        Triple(Screen.QIBLA,stringResource(R.string.qibla_title),"$bearing°"),
        Triple(Screen.ADHKAR,stringResource(R.string.adhkar_title),stringResource(R.string.adhkar_short)),
        Triple(Screen.TASBEEH,stringResource(R.string.tasbeeh_title),stringResource(R.string.tasbeeh_short)),
        Triple(Screen.TIMETABLE,stringResource(R.string.timetable_title),stringResource(R.string.timetable_short)),
        Triple(Screen.PRAYER_LOG,stringResource(R.string.log_title),stringResource(R.string.log_short)),
        Triple(Screen.QURAN,stringResource(R.string.quran_tool),quran.let {if(it.page==0)stringResource(R.string.quran_start_short) else stringResource(R.string.quran_page_short,it.page)}))
    Text(stringResource(R.string.tools_title),color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp,bottom=10.dp).semantics {heading()})
    // Every tool in view at once (no hidden sideways scrolling): three per row, two at large text.
    val perRow=if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.25f)2 else 3
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {tools.chunked(perRow).forEach {row-> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        row.forEach {(screen,title,detail)->
            Surface(onClick={onTool(screen)},color=c.surface,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,c.border),modifier=Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(40.dp).background(c.selected,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center) {
                        when(screen) {
                            Screen.QIBLA -> QiblaGlyph(c.mint,Modifier.size(20.dp))
                            Screen.ADHKAR -> Glyph("book",c.mint,Modifier.size(20.dp))
                            Screen.TASBEEH -> Glyph("beads",c.mint,Modifier.size(20.dp))
                            Screen.TIMETABLE -> Glyph("calendar",c.mint,Modifier.size(20.dp))
                            Screen.QURAN -> Glyph("mushaf",c.mint,Modifier.size(20.dp))
                            else -> Glyph("check",c.mint,Modifier.size(20.dp))
                        }
                    }
                    // Two lines so longer names ("Qibla direction", "Morning and evening") are never cut off.
                    Text(title,color=c.text,fontSize=13.sp,lineHeight=17.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Text(detail,color=c.secondary,fontSize=11.sp,lineHeight=15.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
        repeat(perRow-row.size) {Spacer(Modifier.weight(1f))}
    }}}
}

/** During Ramadan: time left to iftar (by day) or to the end of suhoor (by night). */
@Composable private fun RamadanCard(p: Preferences,now: Instant,day: PrayerDay,tomorrow: PrayerDay?) {
    if(!p.ramadanMode || !day.complete)return
    val c=LocalPalette.current
    fun at(d: PrayerDay?,prayer: Prayer)=d?.events?.firstOrNull {it.prayer==prayer}?.time
    val fajr=at(day,Prayer.FAJR) ?: return
    val maghrib=at(day,Prayer.MAGHRIB) ?: return
    val (label,target)=when {
        isRamadan(day.date,p) && now>=fajr && now<maghrib -> R.string.ramadan_iftar to maghrib
        now<fajr && isRamadan(day.date,p) -> R.string.ramadan_suhoor to fajr
        now>=maghrib && tomorrow!=null && isRamadan(tomorrow.date,p) -> R.string.ramadan_suhoor to (at(tomorrow,Prayer.FAJR) ?: return)
        else -> return
    }
    val seconds=Duration.between(now,target).seconds.coerceAtLeast(0)
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth(),radius=22,selected=true) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ramadan_kareem),color=c.mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text(stringResource(label,localizedUiTime(target,p)),color=c.text,fontSize=15.sp,fontWeight=FontWeight.Bold)
            }
            Text(countdown(seconds),color=c.mint,fontSize=24.sp,fontWeight=FontWeight.Bold,style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
        }
    }
}

/** A second city (family abroad, Makkah…): its local time and next prayer. */
@Composable private fun SecondCityCard(p: Preferences,now: Instant) {
    val city=p.secondCity ?: return
    val c=LocalPalette.current
    val other=remember(city,p.method,p.hanafi,p.highLatitude,now.epochSecond/60) {p.copy(city=city,offsets=emptyMap(),activeMosque=null)}
    val next=remember(other,now.epochSecond/60) {timeline(PrayerCalculator().around(now,other).flatMap {it.events},now)?.next}
    // Same localized clock as the rest of the app ("2:10 ص", not "AM 2:10"), in the other city's zone.
    val clock=localizedUiTime(now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES),other)
    Spacer(Modifier.height(12.dp))
    Panel(Modifier.fillMaxWidth(),radius=20) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(city.name,color=c.text,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                next?.let {Text(stringResource(R.string.second_city_next,localizedPrayerName(it.prayer,it.time,other),localizedUiTime(it.time,other)),color=c.secondary,fontSize=12.sp)}
            }
            Text(clock,color=c.mint,fontSize=18.sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable fun CountdownDisplay(seconds: Long,color: Color,hero: Boolean=false) {
    val c=LocalPalette.current
    val fields=listOf(seconds/3600,(seconds%3600)/60,seconds%60)
    val labels=listOf(stringResource(R.string.en_home_hours),stringResource(R.string.en_home_minutes),stringResource(R.string.en_home_seconds))
    val big=LocalDensity.current.fontScale>1.25f
    val locale=LocalConfiguration.current.locales[0]
    val format=remember(locale) {MeasureFormat.getInstance(locale,MeasureFormat.FormatWidth.WIDE)}
    val remaining=remember(seconds,format) {
        val units=listOf(MeasureUnit.HOUR,MeasureUnit.MINUTE,MeasureUnit.SECOND)
        val measures=fields.mapIndexedNotNull {index,value->
            if(value>0 || seconds==0L && index==2)Measure(value,units[index]) else null
        }
        format.formatMeasures(*measures.toTypedArray())
    }
    val spoken=stringResource(R.string.en_home_countdown_spoken,remaining)
    val timerDigits=LocalTextStyle.current.copy(fontFeatureSettings="tnum",letterSpacing=0.sp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        if(hero) {
            val digits=fields.joinToString(":") {it.toString().padStart(2,'0')}
            val fontScale=LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size=minOf(46f,maxWidth.value/(digits.length*.62f)/fontScale).sp
                Text(digits,Modifier.fillMaxWidth().clearAndSetSemantics {contentDescription=spoken},
                    color=color,fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.Bold,fontSize=size,
                    lineHeight=size*1.16f,textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                    style=timerDigits,maxLines=1,softWrap=false)
            }
        } else Row(Modifier.fillMaxWidth().clearAndSetSemantics {contentDescription=spoken},
            horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalAlignment=Alignment.CenterVertically) {
            fields.forEachIndexed {i,value->
                Column(Modifier.weight(1f).background(c.bg.copy(alpha=.5f),RoundedCornerShape(16.dp)).padding(vertical=10.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(value.toString().padStart(2,'0'),color=color,fontWeight=FontWeight.Bold,
                        fontSize=if(big)25.sp else 32.sp,lineHeight=38.sp,style=timerDigits,maxLines=1)
                    Text(labels[i],color=c.secondary,fontSize=11.sp)
                }
            }
        }
    }
}

/** One short dua a day (Quranic or prophetic); tap to share it. */
@Composable private fun DuaOfDay(date: java.time.LocalDate) {
    val c=LocalPalette.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val dua=Adhkar.duaOfDay(date)
    val title=stringResource(R.string.dua_title)
    val a=accent("amber")
    Spacer(Modifier.height(14.dp))
    Surface(onClick={
            val text="${dua.text}\n(${dua.source})"
            runCatching {context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT,text),title))}
        },shape=RoundedCornerShape(22.dp),color=c.surface,border=BorderStroke(1.dp,c.border),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Glyph("sparkle",a.ink,Modifier.size(18.dp))
                Text(title,Modifier.weight(1f),color=a.ink,fontSize=13.sp,fontWeight=FontWeight.Bold)
                Glyph("share",c.secondary,Modifier.size(16.dp))
            }
            Text(dua.text,color=c.text,fontSize=18.sp,lineHeight=32.sp,
                style=LocalTextStyle.current.copy(textDirection=androidx.compose.ui.text.style.TextDirection.Rtl))
            Text(dua.source,color=c.secondary,fontSize=12.sp)
        }
    }
}
