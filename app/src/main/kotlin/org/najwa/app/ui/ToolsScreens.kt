package org.najwa.app.ui

import android.Manifest
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.app.platform.CityCatalog
import org.najwa.app.platform.openSystemSettings
import org.najwa.app.prayerDispatcher
import org.najwa.core.*
import java.time.LocalDate
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt

// ───────────────────────────── Qibla ─────────────────────────────

/** Compass heading (true north, degrees) from the rotation-vector sensor; null without one. */
@Composable private fun rememberHeading(city: City): Pair<Float?,Boolean> {
    val context=LocalContext.current
    var heading by remember {mutableStateOf<Float?>(null)}
    var calibrate by remember {mutableStateOf(false)}
    DisposableEffect(city) {
        val manager=context.getSystemService(SensorManager::class.java)
        val sensor=manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val declination=GeomagneticField(city.latitude.toFloat(),city.longitude.toFloat(),0f,System.currentTimeMillis()).declination
        val rotation=FloatArray(9); val remapped=FloatArray(9); val orientation=FloatArray(3)
        val listener=object: SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation,event.values)
                val display=if(Build.VERSION.SDK_INT>=30)context.display?.rotation ?: Surface.ROTATION_0
                    else @Suppress("DEPRECATION") (context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.rotation ?: Surface.ROTATION_0)
                val (x,y)=when(display) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation,x,y,remapped)
                SensorManager.getOrientation(remapped,orientation)
                val raw=((Math.toDegrees(orientation[0].toDouble()).toFloat()+declination)+360f)%360f
                // Low-pass across the 0/360 seam so the needle does not jitter.
                heading=heading?.let {old->
                    val delta=((raw-old+540f)%360f)-180f
                    (old+delta*.15f+360f)%360f
                } ?: raw
            }
            override fun onAccuracyChanged(sensor: Sensor,accuracy: Int) {
                calibrate=accuracy<=SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        if(sensor!=null)manager.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_UI)
        onDispose {manager?.unregisterListener(listener)}
    }
    return heading to calibrate
}

@Composable fun QiblaScreen(p: Preferences,onBack: ()->Unit,onCity: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val hasCompass=remember {context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!=null}
    val bearing=remember(p.city) {qiblaBearing(p.city).toFloat()}
    val distance=remember(p.city) {qiblaDistanceKm(p.city)}
    val (heading,calibrate)=rememberHeading(p.city)
    BackLink(stringResource(R.string.qibla_back),onBack)
    PageHeading(stringResource(R.string.qibla_title),stringResource(R.string.qibla_intro))
    val offset=heading?.let {((bearing-it+540f)%360f)-180f}
    val aligned=offset!=null && abs(offset)<=4f
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
            val ring=if(aligned)c.mint else c.border
            val accent=if(aligned)c.mint else c.amber
            val description=when {
                aligned -> stringResource(R.string.qibla_aligned)
                offset!=null -> stringResource(if(offset>0)R.string.qibla_turn_right else R.string.qibla_turn_left,abs(offset).roundToInt())
                else -> stringResource(R.string.qibla_degrees,bearing.roundToInt())
            }
            Box(Modifier.fillMaxWidth(.82f).aspectRatio(1f).semantics {contentDescription=description},contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r=size.minDimension/2
                    val center=Offset(size.width/2,size.height/2)
                    drawCircle(c.surface,r,center)
                    drawCircle(ring,r-2.dp.toPx(),center,style=Stroke(if(aligned)4.dp.toPx() else 2.dp.toPx()))
                    // The dial turns with the phone: north stays north.
                    rotate(-(heading ?: 0f),center) {
                        for(i in 0 until 72) {
                            val long=i%18==0
                            val a=Math.toRadians(i*5.0-90)
                            val inner=r-(if(long)18.dp.toPx() else 10.dp.toPx())
                            drawLine(if(long)c.text else c.secondary.copy(alpha=.5f),
                                Offset(center.x+inner*Math.cos(a).toFloat(),center.y+inner*Math.sin(a).toFloat()),
                                Offset(center.x+(r-6.dp.toPx())*Math.cos(a).toFloat(),center.y+(r-6.dp.toPx())*Math.sin(a).toFloat()),
                                if(long)2.5f.dp.toPx() else 1.dp.toPx(),StrokeCap.Round)
                        }
                        val n=Path();n.moveTo(center.x,center.y-r+22.dp.toPx());n.lineTo(center.x-7.dp.toPx(),center.y-r+36.dp.toPx());n.lineTo(center.x+7.dp.toPx(),center.y-r+36.dp.toPx());n.close()
                        drawPath(n,c.amber)
                        // Qibla needle with the Kaaba at its tip.
                        rotate(bearing,center) {
                            drawLine(accent,center,Offset(center.x,center.y-r+58.dp.toPx()),5.dp.toPx(),StrokeCap.Round)
                            val k=16.dp.toPx()
                            drawRect(accent,Offset(center.x-k/2,center.y-r+40.dp.toPx()-k),Size(k,k))
                            drawLine(c.surface,Offset(center.x-k/2,center.y-r+40.dp.toPx()-k*.62f),Offset(center.x+k/2,center.y-r+40.dp.toPx()-k*.62f),2.dp.toPx())
                        }
                    }
                    drawCircle(accent,7.dp.toPx(),center)
                    // Fixed marker: the top of the phone.
                    val top=Path();top.moveTo(center.x,center.y-r-2.dp.toPx());top.lineTo(center.x-8.dp.toPx(),center.y-r-16.dp.toPx());top.lineTo(center.x+8.dp.toPx(),center.y-r-16.dp.toPx());top.close()
                    drawPath(top,c.text)
                }
            }
            Text(description,color=if(aligned)c.mint else c.text,fontSize=18.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Text(stringResource(R.string.qibla_details,bearing.roundToInt(),String.format(Locale.US,"%,d",distance.roundToInt()),p.city.name),
                color=c.secondary,fontSize=13.sp,textAlign=TextAlign.Center)
        }
    }
    Spacer(Modifier.height(14.dp))
    if(!hasCompass)Text(stringResource(R.string.qibla_no_compass,bearing.roundToInt()),color=c.amber,fontSize=13.sp)
    else Text(stringResource(if(calibrate)R.string.qibla_calibrate_now else R.string.qibla_calibrate),color=if(calibrate)c.amber else c.secondary,fontSize=12.sp)
    TextButton(onClick=onCity) {Text(stringResource(R.string.qibla_change_city))}
}

// ───────────────────────────── Settings backup ─────────────────────────────

@Composable fun BackupScreen(runtime: NajwaRuntime,onBack: ()->Unit,onRestored: ()->Unit) {
    val c=LocalPalette.current
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var message by remember {mutableStateOf<Pair<Int,Boolean>?>(null)}
    // Optional password: typed by the person here, used once and never stored.
    var protect by rememberSaveable {mutableStateOf(false)}
    var password by remember {mutableStateOf("")}
    var again by remember {mutableStateOf("")}
    val passwordOk=!protect || (password.length>=6 && password==again)
    // A file read for restore: asked for its password when protected, then previewed before it replaces anything.
    var pending by remember {mutableStateOf<String?>(null)}
    var locked by remember {mutableStateOf<String?>(null)}
    var unlock by remember {mutableStateOf("")}
    var unlockWrong by remember {mutableStateOf(false)}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null)scope.launch {
            val chars=password.toCharArray()
            val ok=withContext(Dispatchers.IO) {runCatching {
                val plain=runtime.exportSettings()
                val text=if(protect) org.najwa.app.platform.BackupCrypto.encrypt(plain,chars) else plain
                context.contentResolver.openOutputStream(uri,"wt")!!.use {it.write(text.toByteArray(Charsets.UTF_8))}
            }.isSuccess}
            chars.fill(' ')
            message=(if(ok)R.string.backup_saved else R.string.backup_save_failed) to ok
        }
    }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri->
        if(uri!=null)scope.launch {
            val text=withContext(Dispatchers.IO) {runCatching {
                context.contentResolver.openInputStream(uri)!!.use {input->String(input.readNBytesCompat(8*1024*1024),Charsets.UTF_8)}
            }.getOrNull()}
            when {
                text==null -> message=R.string.backup_invalid to false
                org.najwa.app.platform.BackupCrypto.isEncrypted(text) -> {locked=text;unlock="";unlockWrong=false}
                else -> pending=text
            }
        }
    }
    fun restore(text: String) {
        scope.launch {
            val ok=withContext(prayerDispatcher) {runtime.importSettings(text)}
            if(ok)onRestored()
            message=(if(ok)R.string.backup_restored else R.string.backup_invalid) to ok
        }
    }
    BackLink(stringResource(R.string.backup_back),onBack)
    PageHeading(stringResource(R.string.backup_title),stringResource(R.string.backup_intro))
    Text(stringResource(R.string.backup_worship_note),color=c.secondary,fontSize=13.sp,modifier=Modifier.padding(bottom=12.dp))
    SettingsCard(stringResource(R.string.backup_save_title),stringResource(R.string.backup_save_help)) {
        PreferenceSwitch(stringResource(R.string.backup_protect),stringResource(R.string.backup_protect_help),protect) {protect=it}
        if(protect) {
            OutlinedTextField(password,{password=it},label={Text(stringResource(R.string.backup_password))},singleLine=true,
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions=KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(again,{again=it},label={Text(stringResource(R.string.backup_password_again))},singleLine=true,
                visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),isError=again.isNotEmpty() && !passwordOk,
                keyboardOptions=KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),modifier=Modifier.fillMaxWidth())
            if(again.isNotEmpty() && !passwordOk)Text(stringResource(R.string.backup_password_mismatch),color=c.amber,fontSize=12.sp)
        }
        Action(stringResource(R.string.backup_save),Modifier.fillMaxWidth(),passwordOk) {
            if(passwordOk)runCatching {save.launch("najwa-backup-${LocalDate.now()}.json")}.onFailure {message=R.string.backup_save_failed to false}
        }
    }
    SettingsCard(stringResource(R.string.backup_restore_title),stringResource(R.string.backup_restore_help)) {
        Action(stringResource(R.string.backup_restore),Modifier.fillMaxWidth()) {
            runCatching {open.launch(arrayOf("application/json","text/plain","application/octet-stream"))}.onFailure {message=R.string.backup_invalid to false}
        }
    }
    message?.let {(text,ok)->Text(stringResource(text),color=if(ok)c.mint else c.amber,fontSize=14.sp)}
    Text(stringResource(R.string.backup_note),color=c.secondary,fontSize=12.sp)

    locked?.let {text->
        AlertDialog(onDismissRequest={locked=null},title={Text(stringResource(R.string.backup_password_needed))},text={
            Column {
                OutlinedTextField(unlock,{unlock=it;unlockWrong=false},label={Text(stringResource(R.string.backup_password))},singleLine=true,
                    visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),isError=unlockWrong,modifier=Modifier.fillMaxWidth())
                if(unlockWrong)Text(stringResource(R.string.backup_password_wrong),color=c.amber,fontSize=12.sp)
            }
        },confirmButton={TextButton(onClick={
            val chars=unlock.toCharArray()
            val plain=org.najwa.app.platform.BackupCrypto.decrypt(text,chars)
            chars.fill(' ')
            if(plain==null)unlockWrong=true else {locked=null;unlock="";pending=plain}
        }) {Text(stringResource(R.string.mushaf_done))}},
            dismissButton={TextButton(onClick={locked=null}) {Text(stringResource(R.string.action_cancel))}})
    }
    pending?.let {text->
        val preview=remember(text) {org.najwa.app.platform.BackupPreview.of(text)}
        AlertDialog(onDismissRequest={pending=null},title={Text(stringResource(R.string.backup_preview_title))},text={
            Text(if(preview==null) stringResource(R.string.backup_invalid) else stringResource(R.string.backup_preview_text,preview.city,
                org.najwa.app.platform.Counts.format(context,preview.page.toLong()),org.najwa.app.platform.Counts.format(context,preview.khatmas.toLong()),
                org.najwa.app.platform.Counts.format(context,preview.logDays.toLong()),org.najwa.app.platform.Counts.format(context,preview.mosques.toLong())))
        },confirmButton={if(preview!=null)TextButton(onClick={pending=null;restore(text)}) {Text(stringResource(R.string.backup_replace))}},
            dismissButton={TextButton(onClick={pending=null}) {Text(stringResource(R.string.action_cancel))}})
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true) {
        val n=read(buffer); if(n<0)break
        out.write(buffer,0,n)
        require(out.size()<=limit) {"too_large"}
    }
    return out.toByteArray()
}

// ───────────────────────────── Background running ─────────────────────────────

internal fun Context.ignoresBatteryOptimizations(): Boolean =
    runCatching {getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)}.getOrDefault(true)

/** Shown while Android may delay or stop Najwa in the background (Samsung "sleeping apps" and similar). */
@Composable fun BackgroundReadiness(resumed: Int) {
    val context=LocalContext.current
    val c=LocalPalette.current
    val unrestricted=remember(resumed) {context.ignoresBatteryOptimizations()}
    if(unrestricted)return
    val vendor=Build.MANUFACTURER.lowercase(Locale.ROOT)
    SettingsCard(stringResource(R.string.bg_title),stringResource(R.string.bg_text)) {
        val tip=when {
            "samsung" in vendor -> R.string.bg_samsung
            "xiaomi" in vendor || "redmi" in vendor || "poco" in vendor -> R.string.bg_xiaomi
            "huawei" in vendor || "honor" in vendor -> R.string.bg_huawei
            "oppo" in vendor || "realme" in vendor || "oneplus" in vendor || "vivo" in vendor -> R.string.bg_oppo
            else -> null
        }
        tip?.let {Text(stringResource(it),color=c.text,fontSize=13.sp)}
        // The maker's own autostart page, where one is known (tried in order; nothing happens if none exists).
        val vendorPages=when {
            "xiaomi" in vendor || "redmi" in vendor || "poco" in vendor -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
            "huawei" in vendor || "honor" in vendor -> listOf("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity")
            "oppo" in vendor || "realme" in vendor || "oneplus" in vendor -> listOf("com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity")
            "vivo" in vendor -> listOf("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            "samsung" in vendor -> listOf("com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
                "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity")
            else -> emptyList()
        }
        if(vendorPages.isNotEmpty())Action(stringResource(R.string.oem_open),Modifier.fillMaxWidth()) {
            vendorPages.firstOrNull {(pkg,cls)->runCatching {
                context.startActivity(Intent().setComponent(android.content.ComponentName(pkg,cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));true
            }.getOrDefault(false)}
        }
        Action(stringResource(R.string.bg_open),Modifier.fillMaxWidth(),true) {
            val list=Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            if(!openSystemSettings(context,list))
                openSystemSettings(context,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
        }
        Text(stringResource(R.string.bg_why),color=c.secondary,fontSize=12.sp)
    }
}

// ───────────────────────────── One-time location ─────────────────────────────

/** Finds the nearest catalogue city to the phone's current (coarse) location. The location is
 * used once on the device and never stored: only the chosen city is saved. */
@Composable fun rememberCityLocator(onCity: (City)->Unit,onError: (Int)->Unit): Pair<Boolean,()->Unit> {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember {mutableStateOf(false)}
    fun locate() {
        busy=true
        scope.launch {
            try {
                val location=currentLocation(context)
                if(location==null) {onError(R.string.locate_unavailable);return@launch}
                val city=withContext(Dispatchers.IO) {nearestCity(context,location)}
                onCity(city)
            } catch(e: kotlinx.coroutines.CancellationException) {throw e}
            catch(_: Exception) {onError(R.string.locate_failed)}
            finally {busy=false}
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->
        if(granted)locate() else onError(R.string.locate_denied)
    }
    return busy to {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate()
        else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

@Suppress("MissingPermission")
private suspend fun currentLocation(context: Context): Location? {
    val manager=context.getSystemService(LocationManager::class.java) ?: return null
    val providers=listOfNotNull(
        if(Build.VERSION.SDK_INT>=31)LocationManager.FUSED_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER
    ).filter {runCatching {manager.isProviderEnabled(it)}.getOrDefault(false)}
    if(providers.isEmpty())return null
    // A fix from the last quarter hour is accurate enough for choosing a city, and instant.
    providers.mapNotNull {runCatching {manager.getLastKnownLocation(it)}.getOrNull()}
        .filter {System.currentTimeMillis()-it.time<15*60_000L}.maxByOrNull {it.time}?.let {return it}
    if(Build.VERSION.SDK_INT>=30) {
        for(provider in providers) {
            val found=withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> {cont->
                    val signal=android.os.CancellationSignal()
                    cont.invokeOnCancellation {signal.cancel()}
                    manager.getCurrentLocation(provider,signal,context.mainExecutor) {cont.resume(it)}
                }
            }
            if(found!=null)return found
        }
    }
    return providers.mapNotNull {runCatching {manager.getLastKnownLocation(it)}.getOrNull()}.maxByOrNull {it.time}
}

private fun nearestCity(context: Context,location: Location): City {
    val zone=TimeZone.getDefault().id
    val countries=CityCatalog.countries(context)
    val country=CityCatalog.countryForZone(context,zone,countries)
    fun distance(place: CityCatalog.Place)=distanceKm(location.latitude,location.longitude,place.lat,place.lon)
    fun near(place: CityCatalog.Place)=distance(place)<=60.0
    val arabic=Regex("[ء-ي]")
    val wantsArabic=context.resources.configuration.locales[0].language in setOf("ar","ur")
    // Fast path: the country the phone's time zone names. Otherwise (travel, border areas,
    // a generic GMT/UTC zone) look across the whole catalogue.
    val nearest=country?.let {choice->
        val ranked=CityCatalog.load(context,choice).sortedBy(::distance).take(25)
        val closest=ranked.firstOrNull()?.takeIf(::near) ?: return@let null
        // Some small places only carry a Latin name; prefer a close one written in the app's script.
        val readable=if(wantsArabic)ranked.firstOrNull {arabic.containsMatchIn(it.name) && distance(it)<=distance(closest)+10.0} else null
        (readable ?: closest).city(choice)
    } ?: CityCatalog.nearestAnywhere(context,location.latitude,location.longitude,countries)
        ?.takeIf {near(it.second)}?.let {(choice,place)->place.city(choice)}
    return nearest ?: City(context.getString(R.string.locate_my_location),
        (location.latitude*1e4).roundToInt()/1e4,(location.longitude*1e4).roundToInt()/1e4,zone)
}
