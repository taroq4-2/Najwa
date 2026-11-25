package org.najwa.app.platform

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.najwa.core.*
import java.io.File

class JsonFile(context: Context,name: String) {
    private val file=AtomicFile(File(context.filesDir,name))
    @Synchronized fun read(): JSONObject? = try {
        // openRead must run even when only the API 26–28 backup remains after a crash.
        JSONObject(file.openRead().use {it.readBytes().toString(Charsets.UTF_8)})
    } catch(e: java.io.FileNotFoundException) {
        if(file.baseFile.exists() || File(file.baseFile.path+".bak").exists())throw e
        null
    }
    @Synchronized fun write(value: JSONObject) {
        val stream=file.startWrite()
        try { stream.write(value.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch(e: Exception) { file.failWrite(stream); throw e }
    }
}
class PreferencesStore(context: Context) {
    private val file=JsonFile(context,"preferences.json")
    fun read(): Preferences = file.read()?.let {decode(it)} ?: Preferences()
    /** Settings backup file: the same JSON the app stores, tagged so other files are rejected. */
    fun export(p: Preferences): String = encode(p).put("najwaBackup",1).toString(2)
    fun import(text: String): Preferences {
        val j=JSONObject(text)
        require(j.optInt("najwaBackup",0)==1) {"not_a_backup"}
        return decode(j)
    }
    private fun decode(j: JSONObject): Preferences {
        val storedIqama=Prayer.entries.filter {it.isPrayer}.associateWith {j.optJSONObject("iqamaMinutes")?.optInt(it.name,15) ?: 15}
        val iqama=if(j.optInt("schema",0)<5 && storedIqama.values.all {it==15})defaultIqamaMinutes() else storedIqama
        return Preferences(City(j.getString("city"),j.getDouble("lat"),j.getDouble("lon"),j.getString("zone"),
            j.optString("cityCountry").takeIf {it.isNotBlank()},j.optString("cityCatalogId").takeIf {it.isNotBlank()}),Method.valueOf(j.getString("method")),j.getBoolean("hanafi"),HighLatitude.valueOf(j.getString("high")),j.getDouble("fajr"),j.getDouble("isha"),ThemeChoice.valueOf(j.getString("theme")),j.getBoolean("hour24"),j.getBoolean("auto"),j.getJSONArray("enabled").let { a -> (0 until a.length()).map { Prayer.valueOf(a.getString(it)) }.toSet() },j.getInt("duration"),Ringer.valueOf(j.getString("mode")),
            adhanNotifications=j.optBoolean("adhanNotifications",false),iqamaNotifications=j.optBoolean("iqamaNotifications",false),
            iqamaMinutes=iqama,
            silenceMinutes=Prayer.entries.filter {it.isPrayer}.associateWith {j.optJSONObject("silenceMinutes")?.optInt(it.name,defaultSilenceMinutes().getValue(it)) ?: defaultSilenceMinutes().getValue(it)},
            showIqamaCountdown=j.optBoolean("showIqamaCountdown",true),
            widgetTheme=runCatching {WidgetTheme.valueOf(j.optString("widgetTheme","AUTO"))}.getOrDefault(WidgetTheme.AUTO),
            widgetOpacity=j.optInt("widgetOpacity",85).coerceIn(80,100),
            fridayReminder=j.optBoolean("fridayReminder",false),
            fridayLeadMinutes=j.optInt("fridayLeadMinutes",DEFAULT_FRIDAY_LEAD_MINUTES).coerceIn(5,60),
            showLockScreenDetails=j.optBoolean("showLockScreenDetails",false),
            adhanPrayers=readPrayerSelection(j,"adhanPrayers"),
            iqamaPrayers=readPrayerSelection(j,"iqamaPrayers"),
            offsets=Prayer.entries.associateWith {(j.optJSONObject("offsets")?.optInt(it.name,0) ?: 0).coerceIn(-MAX_OFFSET_MINUTES,MAX_OFFSET_MINUTES)}
                .filterValues {it!=0},
            hijriOffset=j.optInt("hijriOffset",0).coerceIn(-2,2),
            preAdhanReminder=j.optBoolean("preAdhanReminder",false),
            preAdhanMinutes=j.optInt("preAdhanMinutes",DEFAULT_PRE_ADHAN_MINUTES).coerceIn(1,60),
            jumuah=j.optBoolean("jumuah",true),
            fridaySilenceMinutes=j.optInt("fridaySilenceMinutes",DEFAULT_FRIDAY_SILENCE_MINUTES).coerceIn(5,120),
            dynamicColor=j.optBoolean("dynamicColor",false),
            elevation=j.optInt("elevation",0).coerceIn(0,6000),
            showExtraTimes=j.optBoolean("showExtraTimes",false),
            ramadanMode=j.optBoolean("ramadanMode",true),
            suhoorReminder=j.optBoolean("suhoorReminder",false),suhoorMinutes=j.optInt("suhoorMinutes",45).coerceIn(10,180),
            taraweehReminder=j.optBoolean("taraweehReminder",false),taraweehMinutes=j.optInt("taraweehMinutes",20).coerceIn(0,120),
            sunriseReminder=j.optBoolean("sunriseReminder",false),sunriseLeadMinutes=j.optInt("sunriseLeadMinutes",15).coerceIn(5,60),
            duhaReminder=j.optBoolean("duhaReminder",false),qiyamReminder=j.optBoolean("qiyamReminder",false),
            adhkarReminders=j.optBoolean("adhkarReminders",false),kahfReminder=j.optBoolean("kahfReminder",false),
            fastingReminders=j.optBoolean("fastingReminders",false),
            liveIqama=j.optBoolean("liveIqama",true),
            enhancedAdhan=j.optBoolean("enhancedAdhan",false),adhanFadeIn=j.optBoolean("adhanFadeIn",true),
            flipToStop=j.optBoolean("flipToStop",true),headphonesOnly=j.optBoolean("headphonesOnly",false),
            quietDuringCalls=j.optBoolean("quietDuringCalls",true),announceAdhan=j.optBoolean("announceAdhan",false),
            flashAlert=j.optBoolean("flashAlert",false),prayerLog=j.optBoolean("prayerLog",true),
            largeDisplay=j.optBoolean("largeDisplay",false),dailyDua=j.optBoolean("dailyDua",true),logCheckIn=j.optBoolean("logCheckIn",false),
            secondCity=j.optJSONObject("secondCity")?.let {c->runCatching {City(c.getString("name"),c.getDouble("lat"),c.getDouble("lon"),c.getString("zone"),
                c.optString("country").takeIf {it.isNotBlank()},c.optString("catalogId").takeIf {it.isNotBlank()})}.getOrNull()},
            broadcastEvents=j.optBoolean("broadcastEvents",false),
            activeMosque=j.optString("activeMosque").takeIf {it.isNotBlank()}).normalized()
    }
    private fun readPrayerSelection(j: JSONObject,key: String): Set<Prayer> {
        val values=j.optJSONArray(key) ?: return dailyPrayers
        val names=(0 until values.length()).map {values.optString(it)}.toSet()
        return dailyPrayers.filterTo(mutableSetOf()) {it.name in names}
    }
    fun write(p: Preferences) { file.write(encode(p)) }
    private fun encode(p: Preferences): JSONObject = JSONObject().put("city",p.city.name).put("lat",p.city.latitude).put("lon",p.city.longitude).put("zone",p.city.zone).put("method",p.method.name).put("hanafi",p.hanafi).put("high",p.highLatitude.name).put("fajr",p.customFajr).put("isha",p.customIsha).put("theme",p.theme.name).put("hour24",p.hour24).put("auto",p.autoSilent).put("enabled",JSONArray(p.enabled.map { it.name })).put("duration",p.durationMinutes).put("mode",p.mode.name)
        .put("adhanNotifications",p.adhanNotifications).put("iqamaNotifications",p.iqamaNotifications)
        .put("cityCountry",p.city.countryCode).put("cityCatalogId",p.city.catalogId)
        .put("fridayReminder",p.fridayReminder).put("fridayLeadMinutes",p.fridayLeadMinutes)
        .put("showLockScreenDetails",p.showLockScreenDetails)
        .put("adhanPrayers",JSONArray(p.adhanPrayers.map {it.name}))
        .put("iqamaPrayers",JSONArray(p.iqamaPrayers.map {it.name}))
        .put("iqamaMinutes",JSONObject().apply {p.iqamaMinutes.forEach {(prayer,minutes)->put(prayer.name,minutes)}})
        .put("schema",8).put("showIqamaCountdown",p.showIqamaCountdown)
        .put("widgetTheme",p.widgetTheme.name).put("widgetOpacity",p.widgetOpacity)
        .put("silenceMinutes",JSONObject().apply {p.silenceMinutes.forEach {(prayer,minutes)->put(prayer.name,minutes)}})
        .put("offsets",JSONObject().apply {p.offsets.forEach {(prayer,minutes)->put(prayer.name,minutes)}})
        .put("hijriOffset",p.hijriOffset).put("preAdhanReminder",p.preAdhanReminder).put("preAdhanMinutes",p.preAdhanMinutes)
        .put("jumuah",p.jumuah).put("fridaySilenceMinutes",p.fridaySilenceMinutes).put("dynamicColor",p.dynamicColor)
        .put("elevation",p.elevation).put("showExtraTimes",p.showExtraTimes).put("ramadanMode",p.ramadanMode)
        .put("suhoorReminder",p.suhoorReminder).put("suhoorMinutes",p.suhoorMinutes)
        .put("taraweehReminder",p.taraweehReminder).put("taraweehMinutes",p.taraweehMinutes)
        .put("sunriseReminder",p.sunriseReminder).put("sunriseLeadMinutes",p.sunriseLeadMinutes)
        .put("duhaReminder",p.duhaReminder).put("qiyamReminder",p.qiyamReminder).put("adhkarReminders",p.adhkarReminders)
        .put("kahfReminder",p.kahfReminder).put("fastingReminders",p.fastingReminders).put("liveIqama",p.liveIqama)
        .put("enhancedAdhan",p.enhancedAdhan).put("adhanFadeIn",p.adhanFadeIn).put("flipToStop",p.flipToStop)
        .put("headphonesOnly",p.headphonesOnly).put("quietDuringCalls",p.quietDuringCalls).put("announceAdhan",p.announceAdhan)
        .put("flashAlert",p.flashAlert).put("prayerLog",p.prayerLog).put("largeDisplay",p.largeDisplay).put("dailyDua",p.dailyDua).put("logCheckIn",p.logCheckIn)
        .put("broadcastEvents",p.broadcastEvents).put("activeMosque",p.activeMosque ?: "")
        .apply {p.secondCity?.let {c->put("secondCity",JSONObject().put("name",c.name).put("lat",c.latitude).put("lon",c.longitude)
            .put("zone",c.zone).put("country",c.countryCode ?: "").put("catalogId",c.catalogId ?: ""))}}
}
class AndroidJournal(context: Context): JournalStore {
    private val file=JsonFile(context,"session.json")
    @Volatile private var displaySnapshot: SessionJournal? = null
    fun snapshot(): SessionJournal = displaySnapshot ?: read()
    fun cachedSnapshot(): SessionJournal? = displaySnapshot
    @Synchronized override fun read(): SessionJournal {
        val j=file.read() ?: return SessionJournal().also {displaySnapshot=it}
        val a=j.getJSONArray("handled")
        val s=j.optJSONObject("session")
        return SessionJournal(s?.let { Session(it.getString("id"),it.getString("event"),Ringer.valueOf(it.getString("original")),Ringer.valueOf(it.getString("applied")),it.getLong("start"),it.getLong("deadline"),SessionState.valueOf(it.getString("state")),it.optString("detail")) },(0 until a.length()).map { a.getString(it) }).also {displaySnapshot=it}
    }
    @Synchronized override fun write(value: SessionJournal) {
        val j=JSONObject().put("handled",JSONArray(value.handled))
        value.session?.let { j.put("session",JSONObject().put("id",it.id).put("event",it.eventId).put("original",it.original.name).put("applied",it.applied.name).put("start",it.startedAt).put("deadline",it.deadline).put("state",it.state.name).put("detail",it.detail)) }
        file.write(j)
        displaySnapshot=value
    }
}
