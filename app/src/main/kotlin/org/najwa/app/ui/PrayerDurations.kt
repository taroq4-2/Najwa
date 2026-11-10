package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import org.najwa.core.*
import org.najwa.app.R

@Composable fun PrayerDurations(title: String,subtitle: String,values: Map<Prayer,Int>,defaults: Map<Prayer,Int>,minimum: Int,enabledPrayers: Set<Prayer>?=null,onEnabled: ((Prayer,Boolean)->Unit)?=null,onChange: (Map<Prayer,Int>)->Unit) {
    val c=LocalPalette.current
    var editing by rememberSaveable {mutableStateOf<Prayer?>(null)}
    SettingsCard(title,subtitle) {
        Prayer.entries.filter {it.isPrayer}.forEach {prayer->
            val prayerName=localizedPrayerName(prayer)
            val silenceLabel=stringResource(R.string.en_duration_switch,prayerName)
            val minutes=values.getValue(prayer)
            val editLabel=stringResource(R.string.en_duration_edit_desc,title,prayerName,minutes)
            Row(verticalAlignment=Alignment.CenterVertically) {
                if(enabledPrayers!=null)Switch(prayer in enabledPrayers,{onEnabled?.invoke(prayer,it)},modifier=Modifier.padding(end=8.dp).semantics {contentDescription=silenceLabel})
                Text(prayerName,Modifier.weight(1f),color=c.text)
                OutlinedButton(onClick={editing=prayer},modifier=Modifier.semantics {contentDescription=editLabel}) {Text(stringResource(R.string.en_duration_edit,minutes))}
            }
        }
        TextButton(onClick={onChange(defaults)}) {Text(stringResource(R.string.en_duration_restore))}
    }
    editing?.let {prayer->
        var input by rememberSaveable(prayer) {mutableStateOf(values.getValue(prayer).toString())}
        val minutes=normalizedNumber(input).toIntOrNull()
        val valid=minutes!=null && minutes in minimum..120
        AlertDialog(onDismissRequest={editing=null},title={Text("$title · ${localizedPrayerName(prayer)}")},text={
            OutlinedTextField(input,{input=it},label={Text(stringResource(R.string.en_duration_since_adhan))},supportingText={Text(stringResource(R.string.en_duration_range,minimum))},isError=!valid,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        },confirmButton={TextButton(enabled=valid,onClick={onChange(values+(prayer to minutes!!));editing=null}) {Text(stringResource(R.string.en_duration_save))}},dismissButton={TextButton(onClick={editing=null}) {Text(stringResource(R.string.en_duration_cancel))}})
    }
}
