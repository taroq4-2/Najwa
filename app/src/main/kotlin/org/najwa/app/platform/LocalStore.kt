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
    fun read(): Preferences {
        val j=file.read() ?: return Preferences()
        val storedIqama=Prayer.entries.filter {it.isPrayer}.associateWith {j.optJSONObject("iqamaMinutes")?.optInt(it.name,15) ?: 15}
        val iqama=if(j.optInt("schema",0)<5 && storedIqama.values.all {it==15})defaultIqamaMinutes() else storedIqama
        return Preferences(City(j.getString("city"),j.getDouble("lat"),j.getDouble("lon"),j.getString("zone")),Method.valueOf(j.getString("method")),j.getBoolean("hanafi"),HighLatitude.valueOf(j.getString("high")),j.getDouble("fajr"),j.getDouble("isha"),ThemeChoice.valueOf(j.getString("theme")),j.getBoolean("hour24"),j.getBoolean("auto"),j.getJSONArray("enabled").let { a -> (0 until a.length()).map { Prayer.valueOf(a.getString(it)) }.toSet() },j.getInt("duration"),Ringer.valueOf(j.getString("mode")),
            adhanNotifications=j.optBoolean("adhanNotifications",false),iqamaNotifications=j.optBoolean("iqamaNotifications",false),
            iqamaMinutes=iqama,
            silenceMinutes=Prayer.entries.filter {it.isPrayer}.associateWith {j.optJSONObject("silenceMinutes")?.optInt(it.name,defaultSilenceMinutes().getValue(it)) ?: defaultSilenceMinutes().getValue(it)},
            showIqamaCountdown=j.optBoolean("showIqamaCountdown",true),
            widgetTheme=runCatching {WidgetTheme.valueOf(j.optString("widgetTheme","AUTO"))}.getOrDefault(WidgetTheme.AUTO),
            widgetOpacity=j.optInt("widgetOpacity",85).coerceIn(80,100),
            fridayReminder=j.optBoolean("fridayReminder",false),
            fridayLeadMinutes=j.optInt("fridayLeadMinutes",DEFAULT_FRIDAY_LEAD_MINUTES).coerceIn(5,60))
    }
    fun write(p: Preferences) { file.write(JSONObject().put("city",p.city.name).put("lat",p.city.latitude).put("lon",p.city.longitude).put("zone",p.city.zone).put("method",p.method.name).put("hanafi",p.hanafi).put("high",p.highLatitude.name).put("fajr",p.customFajr).put("isha",p.customIsha).put("theme",p.theme.name).put("hour24",p.hour24).put("auto",p.autoSilent).put("enabled",JSONArray(p.enabled.map { it.name })).put("duration",p.durationMinutes).put("mode",p.mode.name)
        .put("adhanNotifications",p.adhanNotifications).put("iqamaNotifications",p.iqamaNotifications)
        .put("fridayReminder",p.fridayReminder).put("fridayLeadMinutes",p.fridayLeadMinutes)
        .put("iqamaMinutes",JSONObject().apply {p.iqamaMinutes.forEach {(prayer,minutes)->put(prayer.name,minutes)}})
        .put("schema",6).put("showIqamaCountdown",p.showIqamaCountdown)
        .put("widgetTheme",p.widgetTheme.name).put("widgetOpacity",p.widgetOpacity)
        .put("silenceMinutes",JSONObject().apply {p.silenceMinutes.forEach {(prayer,minutes)->put(prayer.name,minutes)}})) }
}
class AndroidJournal(context: Context): JournalStore {
    private val file=JsonFile(context,"session.json")
    @Volatile private var displaySnapshot: SessionJournal? = null
    fun snapshot(): SessionJournal = displaySnapshot ?: read()
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
