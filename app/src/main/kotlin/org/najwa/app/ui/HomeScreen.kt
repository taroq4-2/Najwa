package org.najwa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.najwa.core.*
import org.najwa.app.platform.hijriLabel
import java.time.Instant
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun HomeScreen(p: Preferences,now: Instant,day: PrayerDay,t: Timeline?,capable: Boolean,onCity: ()->Unit,onSilence: ()->Unit,activeUntil: Instant?=null) {
    val c=LocalPalette.current
    val focus=t?.let {prayerCountdown(listOf(it.previous,it.next),now,p)}
    val iqama=focus?.phase==CountdownPhase.IQAMA
    val accent=if(iqama)c.amber else c.mint
    val start=focus?.start
    val progress=if(focus!=null && start!=null) ((now.toEpochMilli()-start.toEpochMilli()).toDouble()/(focus.time.toEpochMilli()-start.toEpochMilli())).toFloat().coerceIn(0f,1f) else 0f
    var calendar by remember {mutableStateOf(false)}
    if(calendar)PrayerCalendarDialog(p,now) {calendar=false}
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Mark()
        Column(Modifier.weight(1f)) {
            Text("نجوى",fontSize=27.sp,fontWeight=FontWeight.Bold,color=c.text)
            Text("مواقيت الصلاة والهدوء",fontSize=11.sp,color=c.secondary)
        }
        Surface(onClick=onCity,color=c.surface,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,c.border)) {
            Row(Modifier.widthIn(max=160.dp).heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Glyph("pin",c.mint,Modifier.size(16.dp))
                Text(p.city.name,Modifier.weight(1f,false),maxLines=1,overflow=TextOverflow.Ellipsis,color=c.text,fontSize=13.sp)
                Glyph("down",modifier=Modifier.size(12.dp))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(hijriLabel(day.date),color=c.mint,fontSize=13.sp,fontWeight=FontWeight.Bold)
        Text(now.atZone(p.city.zoneId).format(DateTimeFormatter.ofPattern("EEEE، d MMMM",Locale.forLanguageTag("ar"))),color=c.secondary,fontSize=12.sp)
    }
    val heroShape=RoundedCornerShape(32.dp)
    val heroAccent=if(iqama)Color(0xFFF0C477) else Color(0xFF60DFBA)
    Box(Modifier.fillMaxWidth().clip(heroShape).background(Brush.linearGradient(listOf(Color(0xFF123936),Color(0xFF101D30),Color(0xFF0B1525))))) {
        Canvas(Modifier.matchParentSize()) {
            repeat(4) {index->drawCircle(Color(0xFF60DFBA).copy(alpha=.12f),radius=size.width*(.15f+index*.12f),
                center=androidx.compose.ui.geometry.Offset(size.width*.02f,size.height*.08f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))}
        }
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(heroAccent,RoundedCornerShape(50)))
            Text(if(iqama)"المتبقي على الإقامة" else "الصلاة القادمة",color=heroAccent,fontSize=13.sp,fontWeight=FontWeight.Bold)
        }
        if(focus!=null) {
            Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(focus.prayer.label,Modifier.weight(1f),color=Color.White,fontSize=34.sp,fontWeight=FontWeight.Bold)
                Column(horizontalAlignment=Alignment.End) {
                    Text(if(iqama)"موعد الإقامة" else "موعد الأذان",color=Color(0xFFB5C9D6),fontSize=11.sp)
                    Text(displayTime(focus.time,p),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                }
            }
            CountdownDisplay(Duration.between(now,focus.time).seconds.coerceAtLeast(0),heroAccent,true)
            Spacer(Modifier.height(18.dp))
            LinearProgressIndicator(progress={if(start==null)0f else 1f-progress},modifier=Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(10.dp)),color=heroAccent,trackColor=Color.White.copy(alpha=.14f),drawStopIndicator={})
            Text(if(iqama)"الإقامة حسب المدة التي اخترتها" else focus.iqamaLabel?.let {"الإقامة $it · حسب إعدادك"} ?: "مواقيتك حسب ${p.city.name}",color=Color(0xFFB5C9D6),fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
        } else Text(day.warning ?: "تعذّر تحديد الصلاة القادمة",color=c.amber,modifier=Modifier.padding(vertical=20.dp))
    }
    }
    Spacer(Modifier.height(12.dp))
    val active=activeUntil?.isAfter(now)==true
    Surface(onClick=onSilence,color=c.surface,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,c.border)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Glyph("sound",if(active || p.autoSilent && capable)c.mint else c.secondary)
            Column(Modifier.weight(1f)) {
                Text(when {active->"هدوء الصلاة مفعّل الآن";p.autoSilent && capable->"الصمت التلقائي جاهز";p.autoSilent->"الصمت يحتاج إلى إعداد";else->"الصمت التلقائي متوقف"},color=c.text,fontWeight=FontWeight.Bold,fontSize=13.sp)
                Text(if(active)"عودة الرنين ${displayTime(activeUntil!!,p)}" else "الصلوات والمدد والأذونات",color=c.secondary,fontSize=11.sp)
            }
            Glyph("chevron",modifier=Modifier.size(16.dp))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("مواقيت اليوم",Modifier.weight(1f),color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold)
        TextButton(onClick={calendar=true}) {Text("الأيام القادمة",fontSize=12.sp)}
    }
    Panel(Modifier.fillMaxWidth()) {
        day.events.forEachIndexed {index,event->
            val highlighted=if(iqama)event.id==t?.previous?.id else event.id==t?.next?.id
            val passed=event.time<=now && !highlighted
            val color=if(highlighted)accent else if(passed)c.secondary else c.text
            Column(Modifier.fillMaxWidth().background(if(highlighted)accent.copy(alpha=.08f) else Color.Transparent).padding(horizontal=16.dp,vertical=12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Glyph(when {event.prayer==Prayer.SUNRISE->"sun";highlighted->"dot";passed->"check";else->"clock"},color,Modifier.size(18.dp))
                    Text(event.prayer.label,Modifier.weight(1f),color=color,fontSize=15.sp,fontWeight=if(highlighted)FontWeight.Bold else FontWeight.Normal)
                    if(highlighted)Text(if(iqama)"الإقامة قريبًا" else "القادمة",color=accent,fontSize=10.sp)
                    Text(displayTime(event.time,p),color=color,fontSize=15.sp,fontWeight=FontWeight.Bold)
                }
                if(p.showIqamaCountdown && event.prayer.isPrayer)Text("الإقامة ${displayTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p)}",color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(start=28.dp,top=3.dp))
            }
            if(index<day.events.lastIndex)SettingsDivider()
        }
    }
    Text("${p.city.name} · ${p.method.label}",color=c.secondary,fontSize=11.sp,modifier=Modifier.padding(top=12.dp,bottom=8.dp))
}

@Composable fun CountdownDisplay(seconds: Long,color: Color,hero: Boolean=false) {
    val c=LocalPalette.current
    val fields=listOf(seconds/3600,(seconds%3600)/60,seconds%60)
    val labels=listOf("ساعة","دقيقة","ثانية")
    val big=LocalDensity.current.fontScale>1.25f
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {contentDescription=countdown(seconds)},horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            fields.forEachIndexed {i,value->
                Column(Modifier.weight(1f).background(if(hero)Color.White.copy(alpha=.07f) else c.bg.copy(alpha=.5f),RoundedCornerShape(16.dp)).padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(value.toString().padStart(2,'0'),color=color,fontWeight=FontWeight.Bold,fontSize=if(big)25.sp else 32.sp,lineHeight=40.sp)
                    Text(labels[i],color=if(hero)Color(0xFFB5C9D6) else c.secondary,fontSize=10.sp)
                }
            }
        }
    }
}
