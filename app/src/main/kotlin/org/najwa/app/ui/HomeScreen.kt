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

@Composable fun HomeScreen(p: Preferences,now: Instant,day: PrayerDay,t: Timeline?,capable: Boolean,onCity: ()->Unit,onSilence: ()->Unit,activeUntil: Instant?=null) {
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
        Text(localizedUiHijriDate(day.date),color=c.mint,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
        Text(localizedUiDate(day.date),color=c.secondary,fontSize=11.sp,lineHeight=18.sp)
    } else Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=13.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(localizedUiHijriDate(day.date),Modifier.weight(1f),color=c.mint,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold)
        Text(localizedUiDate(day.date),color=c.secondary,fontSize=11.sp,lineHeight=18.sp)
    }
    val heroShape=RoundedCornerShape(28.dp)
    val heroAccent=if(iqama)Color(0xFFF0C787) else Color(0xFFA5E8C4)
    val heroStart=if(iqama)Color(0xFF3A2E22) else Color(0xFF184B3C)
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Box(Modifier.fillMaxWidth().shadow(if(c==Light)8.dp else 0.dp,heroShape).clip(heroShape)
        .background(Brush.linearGradient(listOf(heroStart,Color(0xFF0E2D26),Color(0xFF081C19))))
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
                Text(localizedPrayerName(focus.prayer),Modifier.padding(top=7.dp).semantics {heading()},
                    color=Color(0xFFF6F4EC),fontSize=34.sp,fontWeight=FontWeight.Bold,lineHeight=42.sp)
                Text("${localizedUiTime(focus.time,p)} · ${p.city.name}",color=Color(0xFFC4D9CE),
                    fontSize=12.sp,lineHeight=18.sp,maxLines=2,overflow=TextOverflow.Ellipsis,
                    style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                Spacer(Modifier.height(14.dp))
                CountdownDisplay(Duration.between(now,focus.time).seconds.coerceAtLeast(0),heroAccent,true)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(if(iqama)R.string.system_to_iqama else R.string.system_to_adhan),
                    color=Color(0xFFD3E4D9),fontSize=11.sp,lineHeight=17.sp)
                if(remainingProgress!=null) {
                    Canvas(Modifier.padding(top=10.dp).fillMaxWidth().height(3.dp)) {
                        val corner=CornerRadius(size.height/2f)
                        drawRoundRect(Color(0xFF416157),cornerRadius=corner)
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
                Text(day.warning ?: stringResource(R.string.en_home_next_unavailable),color=heroAccent)
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
                    Text(localizedPrayerName(event.prayer),color=color,fontSize=15.sp,fontWeight=if(highlighted)FontWeight.Bold else FontWeight.Normal)
                    if(p.showIqamaCountdown && event.prayer.isPrayer)Text(stringResource(R.string.en_home_iqama_row,
                        localizedUiTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p)),color=c.secondary,fontSize=12.sp)
                }
                Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(localizedUiTime(event.time,p),color=color,fontSize=16.sp,fontWeight=FontWeight.Bold,
                        style=LocalTextStyle.current.copy(fontFeatureSettings="tnum"))
                    if(highlighted)Text(if(iqama)stringResource(R.string.en_home_iqama_soon) else stringResource(R.string.en_home_next),
                        color=accent,fontSize=11.sp,fontWeight=FontWeight.Medium)
                }
            }
            if(index<day.events.lastIndex)SettingsDivider()
        }
    }
    Text("${p.city.name} · ${localizedMethodName(p.method)}",color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
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
