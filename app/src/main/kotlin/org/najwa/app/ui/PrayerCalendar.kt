package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import org.najwa.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun PrayerCalendarDialog(p: Preferences,now: Instant,onDismiss: ()->Unit) {
    var offset by remember {mutableIntStateOf(0)}
    val today=now.atZone(p.city.zoneId).toLocalDate()
    val date=today.plusDays(offset.toLong())
    val day=remember(date,p) {PrayerCalculator().calculate(date,p)}
    val c=LocalPalette.current
    AlertDialog(onDismissRequest=onDismiss,title={Text("مواقيت الأيام القادمة")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(p.city.name,color=c.mint)
            Text(date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM",Locale.forLanguageTag("ar"))),color=c.text)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={offset--},enabled=offset>0) {Text("السابق")}
                TextButton(onClick={offset=0},enabled=offset!=0) {Text("اليوم")}
                TextButton(onClick={offset++},enabled=offset<6) {Text("التالي")}
            }
            day.warning?.let {Text(it,color=c.amber)}
            day.events.forEach { event ->
                Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(event.prayer.label,color=c.text)
                    Text(displayTime(event.time,p),color=c.mint)
                }
                HorizontalDivider(color=c.border)
            }
            Text("${p.method.label} · حسب المنطقة الزمنية للمدينة",color=c.secondary,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("إغلاق")}})
}
