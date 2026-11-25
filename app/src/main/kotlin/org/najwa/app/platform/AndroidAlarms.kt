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

/** Delivery window for notifications when exact alarms are not allowed (stays within the dispatch grace). */
private const val INEXACT_WINDOW_MS=10*60_000L

class AndroidAlarms(private val context: Context): RestoreAlarm {
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val plan=JsonFile(context,"alarm-plan.json")
    private val trialPlan=JsonFile(context,"trial-plan.json")
    private val registered=mutableSetOf<Long>()
    val exactAccess get() = Build.VERSION.SDK_INT < 31 || runCatching {manager?.canScheduleExactAlarms()==true}.getOrDefault(false)
    private fun pending(kind: String,id: String="",at: Long=0,independent: Boolean=false,backup: Boolean=false): PendingIntent {
        val intent=Intent(context,AlarmReceiver::class.java).setAction("org.najwa.$kind").putExtra("id",id).putExtra("at",at)
        if(independent)intent.data=Uri.parse("najwa://scheduled/$kind/$at")
        if(backup)intent.data=Uri.parse("najwa://scheduled/restore/backup")
        return PendingIntent.getBroadcast(context,when(kind){"restore"->102;"trial"->103;else->101},intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    private fun clock(at: Long,intent: PendingIntent) {
        check(exactAccess) {"exact_access_unavailable"}
        val show=PendingIntent.getActivity(context,202,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(at,show),intent)
    }
    override fun schedule(id: String,deadline: Long) {
        scheduleRestore(id,deadline,deadline)
    }
    fun deferRestore(id: String,deadline: Long,retryAt: Long) {
        require(retryAt>=deadline)
        scheduleRestore(id,deadline,retryAt)
    }
    private fun scheduleRestore(id: String,deadline: Long,triggerAt: Long) {
        check(exactAccess)
        // Keep the original session deadline in the intent when delaying execution.
        // A second foreground-service denial must still match the active session.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt+120_000L,pending("restore",id,deadline,backup=true))
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAt,pending("restore",id,deadline))
    }
    override fun cancel() {
        manager.cancel(pending("restore"))
        manager.cancel(pending("restore",backup=true))
    }
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
            // Reminders keep their kind, or the saved task would never match the current schedule.
            ScheduledPrayer(PrayerEvent(Prayer.valueOf(it.getString("prayer")),Instant.ofEpochMilli(it.getLong("eventAt"))),PrayerAction.valueOf(it.getString("action")),Instant.ofEpochMilli(it.getLong("at")),
                it.optString("reminder").takeIf {r->r.isNotEmpty()}?.let {r->org.najwa.core.Reminder.valueOf(r)})
        }}
    }
    /** Silence (which changes the ringer) uses an alarm clock: the most reliable delivery,
     * at the cost of the alarm icon in the status bar. Notifications use exact idle alarms
     * on Android 12+ (no icon), and approximate alarms when exact access is not granted. */
    private fun arm(at: Long,intent: PendingIntent,critical: Boolean) {
        when {
            // Without exact access Android may defer a plain inexact alarm by up to an hour.
            // A 10-minute window is the narrowest allowed without the permission.
            !exactAccess -> manager.setWindow(AlarmManager.RTC_WAKEUP,at,INEXACT_WINDOW_MS,intent)
            critical || Build.VERSION.SDK_INT<31 -> clock(at,intent)
            else -> manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent)
        }
    }
    fun prayers(tasks: List<ScheduledPrayer>, refresh: Boolean=false) {
        val previous=saved()
        val old=previous.map {it.at.toEpochMilli()}.toSet()
        val batches=tasks.groupBy {it.at.toEpochMilli()}
        val times=batches.keys
        val added=mutableListOf<Long>()
        try {
            times.filter {refresh || it !in registered}.sorted().forEach {at->
                arm(at,pending("prayer","batch:$at",at,true),batches.getValue(at).any {it.action==PrayerAction.SILENCE})
                if(at !in old)added.add(at)
            }
            if(previous!=tasks)plan.write(JSONObject().put("tasks",JSONArray(tasks.map {t->JSONObject().put("prayer",t.event.prayer.name).put("eventAt",t.event.time.toEpochMilli()).put("action",t.action.name).put("at",t.at.toEpochMilli()).apply {t.reminder?.let {put("reminder",it.name)}}})))
        } catch(e: Exception) {
            added.forEach {at->runCatching {manager.cancel(pending("prayer",at=at,independent=true))}}
            throw e
        }
        // Retire the old single PendingIntent, whose extras/plan could be overwritten
        // while a delivered broadcast was still waiting for the process to start.
        manager.cancel(pending("prayer"))
        (old-times).forEach {manager.cancel(pending("prayer",at=it,independent=true))}
        registered.retainAll(times)
        registered.addAll(times)
    }
    fun scheduleTrial(at: Long) {
        val old=trialPlan.read()?.optLong("at") ?: 0L
        if(old>0)listOf(old,old+20_000L,old+60_000L).forEach {manager.cancel(pending("trial",at=it,independent=true))}
        trialPlan.write(JSONObject().put("at",at))
        listOf(at,at+20_000L,at+60_000L).forEach {clock(it,pending("trial","trial:$at",it,true))}
    }
    fun trialEvent(): PrayerEvent? = trialPlan.read()?.optLong("at")?.takeIf {it>0}?.let {PrayerEvent(Prayer.DHUHR,Instant.ofEpochMilli(it))}
}
