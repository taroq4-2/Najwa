package org.najwa.app.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.najwa.core.*
import org.najwa.app.MainActivity
import java.time.Instant

class AndroidAlarms(private val context: Context): RestoreAlarm {
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val plan=JsonFile(context,"alarm-plan.json")
    private val trialPlan=JsonFile(context,"trial-plan.json")
    val exactAccess get() = Build.VERSION.SDK_INT < 31 || runCatching {manager?.canScheduleExactAlarms()==true}.getOrDefault(false)
    private fun pending(kind: String,id: String="",at: Long=0,independent: Boolean=false): PendingIntent {
        val intent=Intent(context,AlarmReceiver::class.java).setAction("org.najwa.$kind").putExtra("id",id).putExtra("at",at)
        if(independent)intent.data=Uri.parse("najwa://scheduled/$kind/$at")
        return PendingIntent.getBroadcast(context,when(kind){"restore"->102;"trial"->103;else->101},intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun clock(at: Long,intent: PendingIntent) {
        check(exactAccess) {"exact_access_unavailable"}
        val show=PendingIntent.getActivity(context,202,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(at,show),intent)
    }
    override fun schedule(id: String,deadline: Long) {
        check(exactAccess)
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,deadline,pending("restore",id,deadline))
    }
    override fun cancel() { manager.cancel(pending("restore")) }
    fun saved(): List<ScheduledPrayer> = try {
        readPlan()
    } catch (_: JSONException) {
        // The plan is derived from preferences. Rebuild it without touching the ringer journal.
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
    private fun readPlan(): List<ScheduledPrayer> {
        val p=plan.read() ?: return emptyList()
        val a=p.optJSONArray("tasks")
        if(a==null) {
            val id=p.optString("id");val at=p.optLong("at")
            val prayer=runCatching {Prayer.valueOf(id.substringBefore(':'))}.getOrNull() ?: return emptyList()
            return listOf(ScheduledPrayer(PrayerEvent(prayer,Instant.ofEpochMilli(at)),PrayerAction.SILENCE,Instant.ofEpochMilli(at)))
        }
        return (0 until a.length()).map {index->a.getJSONObject(index).let {
            ScheduledPrayer(PrayerEvent(Prayer.valueOf(it.getString("prayer")),Instant.ofEpochMilli(it.getLong("eventAt"))),PrayerAction.valueOf(it.getString("action")),Instant.ofEpochMilli(it.getLong("at")))
        }}
    }
    fun prayers(tasks: List<ScheduledPrayer>) {
        val old=saved().map {it.at.toEpochMilli()}.toSet()
        val times=tasks.map {it.at.toEpochMilli()}.toSet()
        val added=mutableListOf<Long>()
        try {
            times.sorted().forEach {at->
                clock(at,pending("prayer","batch:$at",at,true))
                if(at !in old)added.add(at)
            }
            plan.write(JSONObject().put("tasks",JSONArray(tasks.map {t->JSONObject().put("prayer",t.event.prayer.name).put("eventAt",t.event.time.toEpochMilli()).put("action",t.action.name).put("at",t.at.toEpochMilli())})))
        } catch(e: Exception) {
            added.forEach {at->runCatching {manager.cancel(pending("prayer",at=at,independent=true))}}
            throw e
        }
        // Retire the old single PendingIntent, whose extras/plan could be overwritten
        // while a delivered broadcast was still waiting for the process to start.
        manager.cancel(pending("prayer"))
        (old-times).forEach {manager.cancel(pending("prayer",at=it,independent=true))}
    }
    fun scheduleTrial(at: Long) {
        val old=trialPlan.read()?.optLong("at") ?: 0L
        if(old>0)listOf(old,old+20_000L,old+60_000L).forEach {manager.cancel(pending("trial",at=it,independent=true))}
        trialPlan.write(JSONObject().put("at",at))
        listOf(at,at+20_000L,at+60_000L).forEach {clock(it,pending("trial","trial:$at",it,true))}
    }
    fun trialEvent(): PrayerEvent? = trialPlan.read()?.optLong("at")?.takeIf {it>0}?.let {PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(it))}
}
