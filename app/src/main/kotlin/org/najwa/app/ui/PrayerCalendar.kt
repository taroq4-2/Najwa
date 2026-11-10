package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import org.najwa.app.R
import org.najwa.core.*
import java.time.*

@Composable fun PrayerCalendarDialog(p: Preferences,now: Instant,onDismiss: ()->Unit) {
    var offset by remember {mutableIntStateOf(0)}
    val today=now.atZone(p.city.zoneId).toLocalDate()
    val date=today.plusDays(offset.toLong())
    val day=remember(date,p) {PrayerCalculator().calculate(date,p)}
    val c=LocalPalette.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.en_calendar_upcoming))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(p.city.name,color=c.mint)
            Text(localizedUiDate(date),color=c.text)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={offset--},enabled=offset>0) {Text(stringResource(R.string.en_calendar_previous))}
                TextButton(onClick={offset=0},enabled=offset!=0) {Text(stringResource(R.string.en_calendar_today))}
                TextButton(onClick={offset++},enabled=offset<6) {Text(stringResource(R.string.en_calendar_next))}
            }
            day.warning?.let {Text(it,color=c.amber)}
            day.events.forEach { event ->
                Row(Modifier.fillMaxWidth().padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(localizedPrayerName(event.prayer),color=c.text)
                    Text(localizedUiTime(event.time,p),color=c.mint)
                }
                HorizontalDivider(color=c.border)
            }
            Text(stringResource(R.string.en_calendar_tz_summary,localizedMethodName(p.method)),color=c.secondary,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.en_calendar_close))}})
}
