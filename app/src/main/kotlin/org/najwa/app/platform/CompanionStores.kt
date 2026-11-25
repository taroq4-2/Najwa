package org.najwa.app.platform

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import org.najwa.app.MainActivity
import org.najwa.app.R
import org.najwa.core.*
import java.time.LocalDate

/** Mosque profiles: each keeps its own iqama times. */
class MosqueStore(context: Context) {
    private val file=JsonFile(context,"mosques.json")
    fun all(): List<Mosque> = runCatching {
        val a=file.read()?.optJSONArray("mosques") ?: return emptyList()
        (0 until a.length()).mapNotNull {i->runCatching {
            val o=a.getJSONObject(i)
            val minutes=o.getJSONObject("iqama")
            Mosque(o.getString("id"),o.getString("name"),dailyPrayers.associateWith {minutes.getInt(it.name)},
                o.optInt("jumuahSilence",0).takeIf {it in 5..120})
        }.getOrNull()}
    }.getOrDefault(emptyList())
    fun save(list: List<Mosque>) {
        file.write(JSONObject().put("mosques",JSONArray(list.map {m->
            JSONObject().put("id",m.id).put("name",m.name).put("jumuahSilence",m.jumuahSilence ?: 0)
                .put("iqama",JSONObject().apply {m.iqamaMinutes.forEach {(prayer,minutes)->put(prayer.name,minutes)}})
        })))
    }
    fun upsert(m: Mosque) = save(all().filterNot {it.id==m.id}+m)
    fun delete(id: String) = save(all().filterNot {it.id==id})
}

/** The private prayer log, kept only on this phone. */
class PrayerLogStore(context: Context) {
    private val file=JsonFile(context,"prayer-log.json")
    fun read(): PrayerLog = runCatching {
        val j=file.read() ?: return PrayerLog()
        val days=j.optJSONObject("days") ?: JSONObject()
        val detail=j.optJSONObject("detail") ?: JSONObject()
        val sunnah=j.optJSONObject("sunnah") ?: JSONObject()
        PrayerLog(days.keys().asSequence().mapNotNull {key->
            runCatching {
                val a=days.getJSONArray(key)
                LocalDate.parse(key) to (0 until a.length()).map {Prayer.valueOf(a.getString(it))}.filter {it.isPrayer}.toSet()
            }.getOrNull()
        }.toMap(),j.optInt("qada",0),
        detail.keys().asSequence().mapNotNull {key->
            runCatching {
                val o=detail.getJSONObject(key)
                LocalDate.parse(key) to o.keys().asSequence().mapNotNull {k->runCatching {Prayer.valueOf(k) to PrayerStatus.valueOf(o.getString(k))}.getOrNull()}
                    .filter {it.first.isPrayer}.toMap()
            }.getOrNull()
        }.toMap(),
        sunnah.keys().asSequence().mapNotNull {key->
            runCatching {
                val a=sunnah.getJSONArray(key)
                LocalDate.parse(key) to (0 until a.length()).mapNotNull {runCatching {SunnahPrayer.valueOf(a.getString(it))}.getOrNull()}.toSet()
            }.getOrNull()
        }.toMap())
    }.getOrDefault(PrayerLog())
    fun write(log: PrayerLog) {
        file.write(JSONObject().put("qada",log.qada).put("days",JSONObject().apply {
            log.days.forEach {(date,prayers)->put(date.toString(),JSONArray(prayers.map {it.name}))}
        }).put("detail",JSONObject().apply {
            log.detail.forEach {(date,marks)->put(date.toString(),JSONObject().apply {marks.forEach {(prayer,status)->put(prayer.name,status.name)}})}
        }).put("sunnah",JSONObject().apply {
            log.sunnah.forEach {(date,done)->put(date.toString(),JSONArray(done.map {it.name}))}
        }))
    }
}

/** What can stop Najwa's alerts from arriving, in order of importance. */
enum class HealthIssue { NOTIFICATIONS, CHANNELS, EXACT_ALARMS, RINGER_ACCESS, BATTERY, SOUND }

fun healthIssues(context: Context,runtime: org.najwa.app.NajwaRuntime): List<HealthIssue> {
    val p=runtime.preferences
    val alerts=p.adhanNotifications || p.iqamaNotifications || p.fridayReminder || p.preAdhanReminder
    return buildList {
        if(alerts && !runtime.notifications.granted) add(HealthIssue.NOTIFICATIONS)
        if(alerts && runtime.notifications.granted && listOfNotNull(
                PrayerNotifications.ADHAN.takeIf {p.adhanNotifications},PrayerNotifications.IQAMA.takeIf {p.iqamaNotifications},
                PrayerNotifications.FRIDAY.takeIf {p.fridayReminder},PrayerNotifications.PRE_ADHAN.takeIf {p.preAdhanReminder}
            ).any {!runtime.notifications.enabled(it)}) add(HealthIssue.CHANNELS)
        if((alerts || p.autoSilent) && !runtime.alarms.exactAccess) add(HealthIssue.EXACT_ALARMS)
        if(p.autoSilent && !runtime.audio.capable()) add(HealthIssue.RINGER_ACCESS)
        if((alerts || p.autoSilent) && !runCatching {context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}.getOrDefault(true))
            add(HealthIssue.BATTERY)
        if(p.adhanNotifications && runtime.notifications.sounds.selected().error!=null) add(HealthIssue.SOUND)
    }
}
/** 100 when nothing stands in the way; battery optimisation weighs least. */
fun healthScore(issues: List<HealthIssue>) = issues.fold(100) {acc,it->acc-(if(it==HealthIssue.BATTERY) 10 else if(it==HealthIssue.SOUND) 15 else 25)}.coerceAtLeast(0)

/** Once a day at most: tells the person when a newly broken permission would silence their alerts. */
fun notifyHealthProblem(context: Context,issues: List<HealthIssue>) {
    val serious=issues.filter {it!=HealthIssue.BATTERY && it!=HealthIssue.NOTIFICATIONS}
    val state=context.getSharedPreferences("health",Context.MODE_PRIVATE)
    val key=serious.joinToString(",") {it.name}
    if(serious.isEmpty()) {state.edit().remove("last").apply();return}
    val now=System.currentTimeMillis()
    if(state.getString("last","")==key && now-state.getLong("at",0)<24*3600_000L)return
    val strings=context.prayerLanguage()
    val open=PendingIntent.getActivity(context,209,Intent(context,MainActivity::class.java).setAction(ACTION_HEALTH)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(context,PrayerNotifications.STATUS).setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(strings.getString(R.string.health_alert_title)).setContentText(strings.getString(R.string.health_alert_text))
        .setContentIntent(open).setAutoCancel(true).build()
    runCatching {context.getSystemService(NotificationManager::class.java).notify(PrayerNotifications.HEALTH_ID,notification)}
    state.edit().putString("last",key).putLong("at",now).apply()
}

/** Automation (Tasker and similar): "org.najwa.action.PRAYER_EVENT" with prayer and stage extras. */
const val ACTION_PRAYER_EVENT="org.najwa.action.PRAYER_EVENT"
fun broadcastPrayerEvent(context: Context,task: ScheduledPrayer) {
    val stage=when(task.action) {
        PrayerAction.ADHAN,PrayerAction.LIVE -> "adhan"
        PrayerAction.IQAMA -> "iqama"
        PrayerAction.SILENCE -> "silence"
        PrayerAction.PRE_ADHAN -> "before_adhan"
        else -> return
    }
    context.sendBroadcast(Intent(ACTION_PRAYER_EVENT).putExtra("prayer",task.event.prayer.name.lowercase())
        .putExtra("stage",stage).putExtra("time",task.event.time.toEpochMilli()))
}
