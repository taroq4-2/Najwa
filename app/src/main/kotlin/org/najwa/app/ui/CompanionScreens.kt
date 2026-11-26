package org.najwa.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.VibrationEffect
import android.os.Vibrator
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.platform.hijriLabel
import org.najwa.app.platform.prayerLanguage
import org.najwa.app.platform.prayerName
import org.najwa.app.platform.prayerTime
import org.najwa.app.prayerDispatcher
import org.najwa.core.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ───────────────────────────── Monthly timetable ─────────────────────────────

@Composable fun TimetableScreen(p: Preferences,onBack: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var month by rememberSaveable {mutableStateOf(YearMonth.now(p.city.zoneId).toString())}
    val ym=YearMonth.parse(month)
    val calculator=remember {PrayerCalculator()}
    val days=remember(ym,p) {(1..ym.lengthOfMonth()).map {calculator.calculate(ym.atDay(it),p)}}
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val title="${ym.month.getDisplayName(TextStyle.FULL_STANDALONE,locale)} ${ym.year}"
    val today=LocalDate.now(p.city.zoneId)
    BackLink(stringResource(R.string.tools_back),onBack)
    PageHeading(stringResource(R.string.timetable_title),"${p.city.name} · ${localizedMethodName(p.method)}")
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        // The arrows are read as the month they lead to.
        val previous=ym.minusMonths(1).month.getDisplayName(TextStyle.FULL_STANDALONE,locale)
        val next=ym.plusMonths(1).month.getDisplayName(TextStyle.FULL_STANDALONE,locale)
        TextButton(onClick={month=ym.minusMonths(1).toString()},Modifier.size(56.dp).semantics {contentDescription=previous}) {Glyph("back",c.mint,Modifier.size(22.dp))}
        Text(title,Modifier.weight(1f),color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
        TextButton(onClick={month=ym.plusMonths(1).toString()},Modifier.size(56.dp).semantics {contentDescription=next}) {Glyph("chevron",c.mint,Modifier.size(22.dp))}
    }
    val prayers=listOf(Prayer.FAJR,Prayer.SUNRISE,Prayer.DHUHR,Prayer.ASR,Prayer.MAGHRIB,Prayer.ISHA)
    Panel(Modifier.fillMaxWidth(),radius=18) {
        Row(Modifier.fillMaxWidth().background(c.selected).padding(vertical=8.dp,horizontal=6.dp)) {
            Text(stringResource(R.string.timetable_day),Modifier.weight(1.3f),color=c.mint,fontSize=11.sp,fontWeight=FontWeight.Bold)
            prayers.forEach {Text(localizedPrayerName(it),Modifier.weight(1f),color=c.mint,fontSize=10.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,maxLines=1)}
        }
        // Najwa's display font has tall line metrics; the system UI font keeps a 31-row table compact.
        val tight=LocalTextStyle.current.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Default,platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false),
            lineHeightStyle=androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,androidx.compose.ui.text.style.LineHeightStyle.Trim.Both))
        days.forEach {day->
            val isToday=day.date==today
            Row(Modifier.fillMaxWidth().background(if(isToday)c.selected.copy(alpha=.6f) else androidx.compose.ui.graphics.Color.Transparent).padding(vertical=7.dp,horizontal=6.dp),
                verticalAlignment=Alignment.CenterVertically) {
                // Weekday and day, then the Hijri day in a lighter tone, on one line.
                val dayLabel=androidx.compose.ui.text.buildAnnotatedString {
                    append(day.date.format(DateTimeFormatter.ofPattern("EEE d",locale)))
                    withStyle(androidx.compose.ui.text.SpanStyle(color=c.secondary,fontSize=9.sp,fontWeight=FontWeight.Normal)) {
                        append(" · ");append(context.hijriLabel(day.date,p.hijriOffset).substringBefore(' '))
                    }
                }
                Text(dayLabel,Modifier.weight(1.3f),color=if(isToday)c.mint else c.text,fontSize=11.sp,lineHeight=14.sp,
                    fontWeight=if(isToday)FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=tight)
                prayers.forEach {prayer->
                    val time=day.events.firstOrNull {it.prayer==prayer}?.time
                    Text(time?.let {shortTime(it,p,locale)} ?: "—",Modifier.weight(1f),color=c.text,fontSize=11.sp,lineHeight=14.sp,textAlign=TextAlign.Center,maxLines=1,
                        fontWeight=if(isToday)FontWeight.Bold else FontWeight.Normal,style=tight.copy(fontFeatureSettings="tnum"))
                }
            }
            SettingsDivider()
        }
    }
    Spacer(Modifier.height(14.dp))
    var busy by remember {mutableStateOf(false)}
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Action(stringResource(R.string.timetable_share),Modifier.weight(1f),true) {
            if(busy)return@Action
            busy=true
            scope.launch {
                val file=withContext(Dispatchers.IO) {runCatching {timetableImage(context,p,ym,days,title)}.getOrNull()}
                busy=false
                if(file!=null)shareImage(context,file,context.getString(R.string.timetable_share_text,title,p.city.name))
            }
        }
        Action(stringResource(R.string.timetable_print),Modifier.weight(1f)) {printTimetable(context,p,days,title)}
    }
    Text(stringResource(R.string.timetable_note),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))
}

private fun shortTime(time: java.time.Instant,p: Preferences,locale: Locale): String =
    time.atZone(p.city.zoneId).format(DateTimeFormatter.ofPattern(if(p.hour24)"HH:mm" else "h:mm",Locale.US))

/** Renders the month as a tall PNG (Arabic shaping is handled by the platform text renderer). */
private fun timetableImage(context: Context,p: Preferences,ym: YearMonth,days: List<PrayerDay>,title: String): File {
    val strings=context.prayerLanguage()
    val rtl=strings.resources.configuration.layoutDirection==android.view.View.LAYOUT_DIRECTION_RTL
    val width=1240; val rowH=46; val top=230
    val height=top+rowH*(days.size+1)+90
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap)
    canvas.drawColor(0xFF071715.toInt())
    val font=runCatching {androidx.core.content.res.ResourcesCompat.getFont(context,R.font.noto_sans_arabic_regular)}.getOrNull() ?: Typeface.DEFAULT
    val bold=runCatching {androidx.core.content.res.ResourcesCompat.getFont(context,R.font.noto_sans_arabic_bold)}.getOrNull() ?: Typeface.DEFAULT_BOLD
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=font;textAlign=Paint.Align.CENTER}
    paint.color=0xFFA5E8C4.toInt();paint.textSize=54f;paint.typeface=bold
    canvas.drawText(title,width/2f,92f,paint)
    paint.color=0xFFC4D9CE.toInt();paint.textSize=30f;paint.typeface=font
    canvas.drawText("${p.city.name} · ${strings.getString(R.string.system_brand)}",width/2f,146f,paint)
    val prayers=listOf(Prayer.FAJR,Prayer.SUNRISE,Prayer.DHUHR,Prayer.ASR,Prayer.MAGHRIB,Prayer.ISHA)
    val columns=listOf(strings.getString(R.string.timetable_day))+prayers.map {strings.prayerName(it)}
    val colW=(width-80f)/columns.size
    fun x(i: Int)=if(rtl) width-40f-colW*(i+.5f) else 40f+colW*(i+.5f)
    paint.color=0xFF1B3B32.toInt()
    canvas.drawRoundRect(30f,top-36f,width-30f,top+14f,16f,16f,paint)
    paint.color=0xFFA5E8C4.toInt();paint.textSize=26f;paint.typeface=bold
    columns.forEachIndexed {i,name->canvas.drawText(name,x(i),top+2f,paint)}
    paint.typeface=font
    val today=LocalDate.now(p.city.zoneId)
    days.forEachIndexed {row,day->
        val y=top+rowH*(row+1)+4f
        if(day.date==today) {paint.color=0x553EB28C;canvas.drawRect(30f,y-32f,width-30f,y+12f,paint)}
        paint.color=if(row%2==0)0xFFF6F4EC.toInt() else 0xFFD8E4DD.toInt();paint.textSize=25f
        canvas.drawText("${day.date.dayOfMonth} · ${strings.hijriLabel(day.date,p.hijriOffset).substringBefore(' ')}",x(0),y,paint)
        prayers.forEachIndexed {i,prayer->
            val t=day.events.firstOrNull {it.prayer==prayer}?.time
            canvas.drawText(t?.let {shortTime(it,p,Locale.US)} ?: "—",x(i+1),y,paint)
        }
    }
    paint.color=0xFFA9C0B6.toInt();paint.textSize=22f
    canvas.drawText(strings.getString(R.string.timetable_footer),width/2f,height-36f,paint)
    val dir=File(context.cacheDir,"shared").apply {mkdirs()}
    val file=File(dir,"najwa-${ym}.png")
    file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    bitmap.recycle()
    return file
}

internal fun shareImage(context: Context,file: File,text: String) {
    val uri=FileProvider.getUriForFile(context,"${context.packageName}.adhan.files",file)
    val send=Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TEXT,text)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {context.startActivity(Intent.createChooser(send,null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
}

/** Prints (or saves as PDF) through Android's print service; no network is involved. */
private var printView: WebView?=null
private fun printTimetable(context: Context,p: Preferences,days: List<PrayerDay>,title: String) {
    val strings=context.prayerLanguage()
    val rtl=strings.resources.configuration.layoutDirection==android.view.View.LAYOUT_DIRECTION_RTL
    val prayers=listOf(Prayer.FAJR,Prayer.SUNRISE,Prayer.DHUHR,Prayer.ASR,Prayer.MAGHRIB,Prayer.ISHA)
    fun esc(s: String)=s.replace("&","&amp;").replace("<","&lt;")
    val html=buildString {
        append("<html dir='${if(rtl)"rtl" else "ltr"}'><head><meta charset='utf-8'><style>")
        append("body{font-family:sans-serif;margin:24px;color:#10302a}h1{margin:0;font-size:22px}p{margin:4px 0 14px;color:#3f6157}")
        append("table{width:100%;border-collapse:collapse;font-size:12px}th{background:#dff1e7;padding:6px}td{padding:5px;text-align:center;border-bottom:1px solid #e3ebe6}")
        append("</style></head><body><h1>").append(esc(title)).append("</h1><p>").append(esc("${p.city.name} · ${strings.getString(R.string.system_brand)}")).append("</p><table><tr>")
        append("<th>").append(esc(strings.getString(R.string.timetable_day))).append("</th>")
        prayers.forEach {append("<th>").append(esc(strings.prayerName(it))).append("</th>")}
        append("</tr>")
        days.forEach {day->
            append("<tr><td>").append("${day.date.dayOfMonth} · ").append(esc(strings.hijriLabel(day.date,p.hijriOffset).substringBefore(' '))).append("</td>")
            prayers.forEach {prayer->append("<td>").append(day.events.firstOrNull {it.prayer==prayer}?.time?.let {esc(strings.prayerTime(it,p))} ?: "—").append("</td>")}
            append("</tr>")
        }
        append("</table></body></html>")
    }
    val view=WebView(context)
    printView=view
    view.webViewClient=object: WebViewClient() {
        override fun onPageFinished(webView: WebView,url: String?) {
            runCatching {
                context.getSystemService(PrintManager::class.java).print("Najwa $title",webView.createPrintDocumentAdapter("Najwa $title"),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
            printView=null
        }
    }
    view.loadDataWithBaseURL(null,html,"text/html","utf-8",null)
}

// ───────────────────────────── Prayer log ─────────────────────────────

/** The private prayer log: today at a glance, how each prayer was prayed, sunnah prayers, a five-week map,
 * per-prayer consistency, gentle insights and milestones. Nothing leaves the phone. */
@Composable fun PrayerLogScreen(runtime: NajwaRuntime,p: Preferences,onSave: (Preferences)->Unit,onAdhkar: ()->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    val scope=rememberCoroutineScope()
    var log by remember {mutableStateOf(runtime.log())}
    val today=LocalDate.now(p.city.zoneId)
    var selected by rememberSaveable {mutableStateOf(today.toString())}
    val day=LocalDate.parse(selected)
    var picking by remember {mutableStateOf<Prayer?>(null)}
    // Worship companion: after marking a prayer today, offer its adhkar (nothing is marked automatically).
    val appContext=androidx.compose.ui.platform.LocalContext.current
    val companion=remember {org.najwa.app.platform.WorshipSettingsStore.read(appContext).enabled}
    var justPrayed by remember {mutableStateOf<Prayer?>(null)}
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    fun refresh() {log=runtime.log()}
    val order=dailyPrayers.sortedBy {it.ordinal}
    BackLink(stringResource(R.string.tools_back),onBack)
    PageHeading(stringResource(R.string.log_title),stringResource(R.string.log_intro))

    // 1) The selected day (today by default): five prayers, one tap each.
    Panel(Modifier.fillMaxWidth(),radius=24) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(day==today)stringResource(R.string.log_today) else day.format(DateTimeFormatter.ofPattern("EEEE d MMMM",locale)),
                    Modifier.weight(1f),color=c.text,fontSize=17.sp,fontWeight=FontWeight.Bold)
                Text(String.format(Locale.ROOT,"%d/5",log.days[day]?.size ?: 0),color=c.mint,fontSize=17.sp,fontWeight=FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                order.forEach {prayer->
                    val status=log.status(day,prayer)
                    val name=localizedPrayerName(prayer)
                    val statusLabel=status?.let {stringResource(statusText(it))} ?: stringResource(R.string.log_status_none)
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp),
                        modifier=Modifier.clip(RoundedCornerShape(14.dp)).clickable(role=Role.Button) {picking=prayer}
                            .semantics {contentDescription="$name · $statusLabel"}.padding(4.dp)) {
                        StatusDot(status,46)
                        Text(name,color=c.text,fontSize=12.sp,maxLines=1)
                    }
                }
            }
            // Sunnah prayers of the day.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SunnahPrayer.entries.forEach {s->
                    val done=s in log.sunnah[day].orEmpty()
                    Pill(stringResource(sunnahText(s)),done) {
                        scope.launch {withContext(prayerDispatcher) {runtime.setSunnah(day,s,!done)};refresh()}
                    }
                }
            }
            StatusLegend()
        }
    }
    justPrayed?.takeIf {companion && log.prayed(today,it)}?.let {prayer->
        Surface(onClick=onAdhkar,shape=RoundedCornerShape(18.dp),color=accent("teal").tint,modifier=Modifier.fillMaxWidth().padding(top=10.dp)) {
            Row(Modifier.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Glyph("beads",accent("teal").ink,Modifier.size(20.dp))
                Text(stringResource(R.string.after_prayer_link,localizedPrayerName(prayer)),Modifier.weight(1f),color=c.text,fontSize=14.sp)
                Glyph("chevron",c.secondary,Modifier.size(16.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    // 2) Three numbers that matter.
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        StatTile(stringResource(R.string.log_week),"${log.total(today,7)}/35",Modifier.weight(1f))
        StatTile(stringResource(R.string.log_streak),"${log.streak(today)}",Modifier.weight(1f))
        StatTile(stringResource(R.string.log_jamaah_month),"${log.jamaah(today,30)}",Modifier.weight(1f))
    }
    Spacer(Modifier.height(14.dp))

    // 3) A gentle insight: which prayer needs care, and the Fajr streak.
    val weakest=log.weakest(today)
    val fajrStreak=log.prayerStreak(Prayer.FAJR,today)
    if(weakest!=null || fajrStreak>1) Panel(Modifier.fillMaxWidth().padding(bottom=14.dp),radius=22,selected=true) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            IconBadge("sparkle",accent("amber"))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                if(weakest!=null)Text(stringResource(R.string.log_insight_weakest,localizedPrayerName(weakest),log.recentDays(weakest,today,14)),color=c.text,fontSize=14.sp)
                if(fajrStreak>1)Text(stringResource(R.string.log_insight_fajr,fajrStreak),color=c.mint,fontSize=13.sp,fontWeight=FontWeight.Bold)
            }
        }
    }

    // 4) Five weeks at a glance; tap a day to edit it above.
    SettingsCard(stringResource(R.string.log_map_title),stringResource(R.string.log_map_help)) {
        val start=today.minusDays(34)
        (0 until 5).forEach {week->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach {d->
                    val date=start.plusDays((week*7+d).toLong())
                    val count=log.days[date]?.size ?: 0
                    val isSelected=date==day
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        .background(if(count==0)c.track else c.green.copy(alpha=.25f+.15f*count))
                        .border(if(isSelected)2.dp else 0.dp,if(isSelected)c.mint else Color.Transparent,RoundedCornerShape(8.dp))
                        .clickable(role=Role.Button) {selected=date.toString()}
                        .semantics {contentDescription="${date.format(DateTimeFormatter.ofPattern("d MMMM",locale))} · $count/5"},
                        contentAlignment=Alignment.Center) {
                        Text("${date.dayOfMonth}",color=if(count>=3)c.bg else c.secondary,fontSize=11.sp,fontWeight=if(date==today)FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }

    // 5) Consistency of each prayer over 30 days.
    SettingsCard(stringResource(R.string.log_by_prayer)) {
        order.forEach {prayer->
            val n=log.recentDays(prayer,today,30)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(localizedPrayerName(prayer),Modifier.width(64.dp),color=c.text,fontSize=13.sp)
                Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(c.track)) {
                    Box(Modifier.fillMaxWidth(n/30f).fillMaxHeight().background(if(n>=27)c.green else if(n>=15)c.mint.copy(alpha=.7f) else c.amber,CircleShape))
                }
                Text(String.format(Locale.ROOT,"%d/30",n),color=c.secondary,fontSize=12.sp)
            }
        }
    }

    // 6) Milestones of consecutive complete days.
    val best=log.bestStreak()
    val gold=accent("amber")
    SettingsCard(stringResource(R.string.log_milestones),stringResource(R.string.log_best,best)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(7,30,40,100).forEach {goal->
                val reached=best>=goal
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if(reached)gold.tint else c.bg)
                    .border(1.dp,if(reached)gold.ink else c.border,RoundedCornerShape(16.dp)).padding(vertical=10.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Glyph(if(reached)"star" else "dot",if(reached)gold.ink else c.secondary,Modifier.size(18.dp))
                    Text("$goal",color=if(reached)gold.ink else c.text,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.log_days),color=c.secondary,fontSize=11.sp)
                }
            }
        }
    }

    // 7) Make-up prayers.
    SettingsCard(stringResource(R.string.log_qada_title),stringResource(R.string.log_qada_help)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick={scope.launch {withContext(prayerDispatcher) {runtime.setQada(log.qada-1)};refresh()}},enabled=log.qada>0) {Text("−",fontSize=18.sp)}
            Text(org.najwa.app.platform.Counts.format(androidx.compose.ui.platform.LocalContext.current,log.qada.toLong()),Modifier.weight(1f),color=c.mint,fontSize=30.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            OutlinedButton(onClick={scope.launch {withContext(prayerDispatcher) {runtime.setQada(log.qada+1)};refresh()}}) {Text("+",fontSize=18.sp)}
        }
    }
    SettingsCard(stringResource(R.string.log_options)) {
        PreferenceSwitch(stringResource(R.string.log_check_setting),stringResource(R.string.log_check_setting_help),p.logCheckIn) {onSave(p.copy(logCheckIn=it))}
        Text(stringResource(R.string.log_widget_hint),color=c.secondary,fontSize=12.sp)
    }
    Text(stringResource(R.string.log_privacy),color=c.secondary,fontSize=12.sp)

    picking?.let {prayer->
        val current=log.status(day,prayer)
        AlertDialog(onDismissRequest={picking=null},title={Text(stringResource(R.string.log_how,localizedPrayerName(prayer)))},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                PrayerStatus.entries.forEach {status->
                    Surface(onClick={picking=null;justPrayed=if(status.prayed && day==today)prayer else null
                        scope.launch {withContext(prayerDispatcher) {runtime.setStatus(day,prayer,status)};refresh()}},
                        shape=RoundedCornerShape(14.dp),color=if(status==current)c.selected else c.surface,
                        border=BorderStroke(1.dp,if(status==current)c.green else c.border),modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            StatusDot(status,30)
                            Text(stringResource(statusText(status)),color=c.text,fontSize=15.sp)
                        }
                    }
                }
            }
        },confirmButton={TextButton(onClick={picking=null}) {Text(stringResource(R.string.close))}},
            dismissButton={if(current!=null)TextButton(onClick={picking=null;scope.launch {withContext(prayerDispatcher) {runtime.setStatus(day,prayer,null)};refresh()}}) {
                Text(stringResource(R.string.log_clear),color=c.amber)}})
    }
}

private fun statusText(s: PrayerStatus)=when(s) {
    PrayerStatus.JAMAAH->R.string.log_status_jamaah
    PrayerStatus.ON_TIME->R.string.log_status_on_time
    PrayerStatus.LATE->R.string.log_status_late
    PrayerStatus.MISSED->R.string.log_status_missed
}
private fun sunnahText(s: SunnahPrayer)=when(s) {
    SunnahPrayer.RAWATIB->R.string.log_sunnah_rawatib
    SunnahPrayer.DUHA->R.string.log_sunnah_duha
    SunnahPrayer.WITR->R.string.log_sunnah_witr
    SunnahPrayer.QIYAM->R.string.log_sunnah_qiyam
}
@Composable private fun statusColor(s: PrayerStatus?)=when(s) {
    PrayerStatus.JAMAAH->LocalPalette.current.amber
    PrayerStatus.ON_TIME->LocalPalette.current.green
    PrayerStatus.LATE->accent("blue").ink
    PrayerStatus.MISSED->accent("coral").ink
    null->Color.Transparent
}
@Composable private fun StatusDot(status: PrayerStatus?,size: Int) {
    val c=LocalPalette.current
    val color=statusColor(status)
    Box(Modifier.size(size.dp).clip(CircleShape).background(if(status==null||status==PrayerStatus.MISSED)c.bg else color)
        .border(if(status==null)1.5.dp else 2.dp,if(status==null)c.border else color,CircleShape),contentAlignment=Alignment.Center) {
        when(status) {
            null->{}
            PrayerStatus.MISSED->Glyph("minus",color,Modifier.size((size*.45f).dp))
            PrayerStatus.JAMAAH->Glyph("mosque",c.bg,Modifier.size((size*.5f).dp))
            else->Glyph("check",c.bg,Modifier.size((size*.5f).dp))
        }
    }
}
@Composable private fun StatusLegend() {
    val c=LocalPalette.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        PrayerStatus.entries.forEach {s->
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(s)))
                Text(stringResource(statusText(s)),color=c.secondary,fontSize=11.sp)
            }
        }
    }
}

@Composable fun StatTile(label: String,value: String,modifier: Modifier=Modifier) {
    val c=LocalPalette.current
    Panel(modifier,radius=20) {
        Column(Modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(value,color=c.mint,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text(label,color=c.secondary,fontSize=12.sp,textAlign=TextAlign.Center)
        }
    }
}
