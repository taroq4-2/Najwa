package org.najwa.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.prayerDispatcher
import org.najwa.core.*
import java.io.File
import java.util.UUID

/** A QR code drawn on the device (no network). */
internal fun qrBitmap(text: String,size: Int=640): Bitmap {
    val matrix=QRCodeWriter().encode(text,BarcodeFormat.QR_CODE,size,size,mapOf(EncodeHintType.MARGIN to 2,EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    val pixels=IntArray(size*size) {i->if(matrix[i%size,i/size])0xFF071715.toInt() else 0xFFFFFFFF.toInt()}
    return Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
}

internal fun saveQr(context: Context,bitmap: Bitmap,name: String): File {
    val dir=File(context.cacheDir,"shared").apply {mkdirs()}
    return File(dir,"$name.png").also {file->file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
}

@Composable fun QrDialog(title: String,link: String,shareText: String,onDismiss: ()->Unit) {
    val context=LocalContext.current
    val bitmap=remember(link) {runCatching {qrBitmap(link)}.getOrNull()}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
            bitmap?.let {Image(it.asImageBitmap(),null,Modifier.size(240.dp).background(androidx.compose.ui.graphics.Color.White,RoundedCornerShape(12.dp)).padding(6.dp))}
            Text(stringResource(R.string.qr_help),color=LocalPalette.current.secondary,fontSize=12.sp)
        }
    },confirmButton={
        TextButton(onClick={
            runCatching {context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"$shareText\n$link"),null))}
        }) {Text(stringResource(R.string.share_link))}
    },dismissButton={
        TextButton(onClick={bitmap?.let {shareImage(context,saveQr(context,it,"najwa-qr"),"$shareText\n$link")}}) {Text(stringResource(R.string.share_qr))}
    })
}

@Composable fun MosqueScreen(runtime: NajwaRuntime,p: Preferences,importCode: String?,onImportHandled: ()->Unit,onBack: ()->Unit,onChanged: ()->Unit) {
    val c=LocalPalette.current
    val scope=rememberCoroutineScope()
    var mosques by remember {mutableStateOf(runtime.mosqueList())}
    var editing by remember {mutableStateOf<Mosque?>(null)}
    var adding by remember {mutableStateOf(false)}
    var sharing by remember {mutableStateOf<Mosque?>(null)}
    var importing by rememberSaveable {mutableStateOf(false)}
    var incoming by remember {mutableStateOf<Mosque?>(null)}
    LaunchedEffect(importCode) {
        if(importCode!=null) {incoming=MosqueCode.decode(importCode);if(incoming==null)importing=true;onImportHandled()}
    }
    fun refresh() {mosques=runtime.mosqueList();onChanged()}
    fun run(block: ()->Unit) {scope.launch {withContext(prayerDispatcher) {block()};refresh()}}
    BackLink(stringResource(R.string.settings_back),onBack)
    PageHeading(stringResource(R.string.mosque_title),stringResource(R.string.mosque_intro))
    if(mosques.isEmpty())SettingsCard(stringResource(R.string.mosque_empty_title),stringResource(R.string.mosque_empty_help)) {}
    mosques.forEach {m->
        val active=p.activeMosque==m.id
        Surface(shape=RoundedCornerShape(22.dp),color=if(active)c.selected else c.surface,border=BorderStroke(1.dp,if(active)c.green else c.border),
            modifier=Modifier.fillMaxWidth().padding(bottom=12.dp)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(m.name,Modifier.weight(1f),color=c.text,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    if(active)Text(stringResource(R.string.mosque_active),color=c.mint,fontSize=12.sp,fontWeight=FontWeight.Bold)
                }
                val order=dailyPrayers.sortedBy {it.ordinal}
                val names=order.map {localizedPrayerName(it)}
                Text(order.indices.joinToString(" · ") {"${names[it]} ${m.iqamaMinutes.getValue(order[it])}"},color=c.secondary,fontSize=13.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    if(!active)TextButton(onClick={run {runtime.applyMosque(m)}}) {Text(stringResource(R.string.mosque_use))}
                    TextButton(onClick={editing=m}) {Text(stringResource(R.string.mosque_edit))}
                    TextButton(onClick={sharing=m}) {Text(stringResource(R.string.mosque_share))}
                    TextButton(onClick={run {runtime.deleteMosque(m.id)}}) {Text(stringResource(R.string.mosque_delete),color=c.amber)}
                }
            }
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Action(stringResource(R.string.mosque_add),Modifier.weight(1f),true) {
            adding=true
        }
        Action(stringResource(R.string.mosque_import),Modifier.weight(1f)) {importing=true}
    }
    Text(stringResource(R.string.mosque_note),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(top=10.dp))

    editing?.let {m->MosqueEditor(m.id,m.name,m.iqamaMinutes,m.jumuahSilence,onDismiss={editing=null}) {saved,apply->editing=null;run {runtime.saveMosque(saved,apply)}}}
    if(adding)MosqueEditor(UUID.randomUUID().toString(),"",p.iqamaMinutes,null,onDismiss={adding=false}) {saved,apply->adding=false;run {runtime.saveMosque(saved,apply)}}
    sharing?.let {m->QrDialog(m.name,MosqueCode.link(m),stringResource(R.string.mosque_share_text,m.name)) {sharing=null}}
    if(importing) {
        var text by rememberSaveable {mutableStateOf("")}
        var invalid by remember {mutableStateOf(false)}
        AlertDialog(onDismissRequest={importing=false},title={Text(stringResource(R.string.mosque_import))},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mosque_import_help),color=c.secondary,fontSize=13.sp)
                OutlinedTextField(text,{text=it;invalid=false},singleLine=false,minLines=2,modifier=Modifier.fillMaxWidth(),isError=invalid)
                if(invalid)Text(stringResource(R.string.mosque_import_invalid),color=c.amber,fontSize=12.sp)
            }
        },confirmButton={TextButton(onClick={
            val m=MosqueCode.decode(text)
            if(m==null)invalid=true else {importing=false;incoming=m}
        }) {Text(stringResource(R.string.mosque_continue))}},dismissButton={TextButton(onClick={importing=false}) {Text(stringResource(R.string.close))}})
    }
    incoming?.let {m->
        AlertDialog(onDismissRequest={incoming=null},title={Text(m.name)},text={
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.mosque_incoming_help),color=c.secondary,fontSize=13.sp)
                dailyPrayers.sortedBy {it.ordinal}.forEach {prayer->
                    Row {Text(localizedPrayerName(prayer),Modifier.weight(1f),color=c.text);Text(stringResource(R.string.minutes_short,m.iqamaMinutes.getValue(prayer)),color=c.mint)}
                }
            }
        },confirmButton={TextButton(onClick={incoming=null;run {runtime.saveMosque(m,true)}}) {Text(stringResource(R.string.mosque_save_use))}},
            dismissButton={TextButton(onClick={incoming=null;run {runtime.saveMosque(m,false)}}) {Text(stringResource(R.string.mosque_save_only))}})
    }
}

@Composable private fun localizedPrayerNameShort(prayer: Prayer)=localizedPrayerName(prayer)

/** A blank name is allowed while typing; the Mosque is only built (and validated) on save. */
@Composable private fun MosqueEditor(id: String,initialName: String,initialMinutes: Map<Prayer,Int>,initialJumuah: Int?,onDismiss: ()->Unit,onSave: (Mosque,Boolean)->Unit) {
    val c=LocalPalette.current
    var name by rememberSaveable {mutableStateOf(initialName)}
    val minutes=remember {mutableStateMapOf<Prayer,String>().apply {initialMinutes.forEach {(k,v)->put(k,v.toString())}}}
    var jumuah by rememberSaveable {mutableStateOf(initialJumuah?.toString() ?: "")}
    var invalid by remember {mutableStateOf(false)}
    fun build(): Mosque? = runCatching {
        Mosque(id,name.trim(),dailyPrayers.associateWith {normalizedNumber(minutes[it] ?: "").toInt()},
            jumuah.takeIf {it.isNotBlank()}?.let {normalizedNumber(it).toInt().also {v->require(v in 5..120)}})
    }.getOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.mosque_editor_title))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it;invalid=false},label={Text(stringResource(R.string.mosque_name))},singleLine=true,modifier=Modifier.fillMaxWidth())
            Text(stringResource(R.string.mosque_minutes_help),color=c.secondary,fontSize=12.sp)
            dailyPrayers.sortedBy {it.ordinal}.forEach {prayer->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(localizedPrayerName(prayer),Modifier.weight(1f),color=c.text)
                    OutlinedTextField(minutes[prayer] ?: "",{minutes[prayer]=it;invalid=false},singleLine=true,modifier=Modifier.width(96.dp),
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                }
            }
            OutlinedTextField(jumuah,{jumuah=it;invalid=false},label={Text(stringResource(R.string.mosque_jumuah))},singleLine=true,
                modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(invalid)Text(stringResource(R.string.mosque_invalid),color=c.amber,fontSize=12.sp)
        }
    },confirmButton={TextButton(onClick={build()?.let {onSave(it,true)} ?: run {invalid=true}}) {Text(stringResource(R.string.mosque_save_use))}},
        dismissButton={TextButton(onClick={build()?.let {onSave(it,false)} ?: run {invalid=true}}) {Text(stringResource(R.string.mosque_save_only))}})
}
