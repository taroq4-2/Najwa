package org.najwa.app

import android.app.Application
import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.najwa.app.platform.*
import org.najwa.core.*
import java.time.Instant
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

internal val prayerExecutor = Executors.newSingleThreadExecutor()
internal val prayerDispatcher = prayerExecutor.asCoroutineDispatcher()

/** Minutes used by the quick-settings tile and the "Silence now" shortcut. */
const val MANUAL_SILENCE_MINUTES = 30

class NajwaApplication: Application() {
    lateinit var runtime: NajwaRuntime; private set
    override fun onCreate() { super.onCreate(); runtime=NajwaRuntime(this); runCatching {org.najwa.app.platform.Counts.arabicDigits(this)} }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        prayerExecutor.execute { PrayerWidget.updateAll(this) }
    }
}
class NajwaRuntime(private val context: Context,private val clock: ()->Instant = Instant::now) {
    private val preferencesStore=PreferencesStore(context)
    private val diagnostic=JsonFile(context,"last-dispatch.json")
    val journal=AndroidJournal(context)
    val audio=AndroidAudio(context)
    val alarms=AndroidAlarms(context)
    val notifications=PrayerNotifications(context,clock)
    val calculator=PrayerCalculator()
    val sessions=SilenceSessionManager(journal,audio,alarms,{clock().toEpochMilli()})
    val mosques=MosqueStore(context)
    val prayerLog=PrayerLogStore(context)
    /** Messages are resolved in the app language when they are raised. */
    private fun text(id: Int,vararg args: Any): String = context.prayerLanguage().getString(id,*args)
    @Volatile var error: String?=null; private set
    private var preferencesAvailable=true
    @Volatile var preferences=runCatching { preferencesStore.read() }.getOrElse {
        Log.w(TAG,"preferences",it);preferencesAvailable=false;error=text(R.string.rt_read_settings);Preferences()
    }; private set
    @Volatile var nextScheduled: ScheduledPrayer?=null;private set
    @Volatile var nextNotification: ScheduledPrayer?=null;private set
    @Volatile var notice: String?=null;private set
    @Volatile private var dispatchText=runCatching {diagnostic.read()?.optString("text")?.takeIf {it.isNotBlank()}}.getOrNull()
    fun lastDispatch(): String? = dispatchText
    fun session(): Session? = try {journal.read().session} catch(e: Exception) {Log.w(TAG,"journal",e);error=text(R.string.rt_ringer_state);null}
    fun displayedSession(): Session? = journal.cachedSnapshot()?.session
    /** Technical details go to the log; people see one plain sentence per failure. */
    private fun attempt(label: Int,block: ()->Unit): Boolean = try {block();true} catch(e: Exception) {
        Log.w(TAG,context.resources.getResourceEntryName(label),e)
        error=listOfNotNull(error,text(label)).distinct().joinToString("\n");false
    }
    private fun begin(): Boolean {
        error=null
        // A notice describes the last action only (e.g. "silenced until 8:43"); it must not outlive it.
        notice=null
        if(!preferencesAvailable) return attempt(R.string.rt_read_settings) {preferences=preferencesStore.read();preferencesAvailable=true}
        return true
    }
    @Synchronized fun update(requested: Preferences) {
        if(!begin())return
        val p=requested.normalized()
        val hideDetails=preferences.showLockScreenDetails && !p.showLockScreenDetails
        val disabled=preferences.autoSilent && !p.autoSilent
        if(!attempt(R.string.rt_save_settings) {preferencesStore.write(p)})return
        preferences=p
        if(hideDetails)attempt(R.string.rt_hide_alerts) {notifications.hidePreviouslyPublicAlerts()}
        PrayerWidget.updateAll(context)
        if(disabled)attempt(R.string.rt_restore_ringer) {sessions.disable()}
        attempt(R.string.rt_schedule) {reschedule()}
    }
    /** Backup: the settings, plus the khatma, records, logs and mosques ([WorshipBackup]), as the JSON text to save. */
    fun exportSettings(): String =
        org.json.JSONObject(preferencesStore.export(preferences)).put("data",WorshipBackup.export(context)).toString(2)
    /** Restores a backup made by [exportSettings]; false when the file is not a Najwa backup. Older files hold settings only. */
    fun importSettings(text: String): Boolean {
        val restored=runCatching {preferencesStore.import(text)}.getOrElse {Log.w(TAG,"import",it);return false}
        val data=runCatching {org.json.JSONObject(text).optJSONObject("data")}.getOrNull()
        // A copy of what is here now, put back if writing the file's data fails part-way.
        val before=runCatching {WorshipBackup.export(context)}.getOrNull()
        if(data!=null && !WorshipBackup.restore(context,data)) {
            before?.let {WorshipBackup.restore(context,it)}
            return false
        }
        update(restored)
        return preferences==restored.normalized()
    }
    @Synchronized fun reconcile(refreshAlarms: Boolean=false) {
        val available=begin()
        attempt(R.string.rt_review_ringer) {sessions.reconcile()}
        if(available)attempt(R.string.rt_schedule) {reschedule(refreshAlarms=refreshAlarms)}
    }
    @Synchronized fun restore() {
        begin()
        attempt(R.string.rt_restore_ringer) {session()?.let {sessions.restore(it.id,true)}}
        if(preferencesAvailable)attempt(R.string.rt_schedule) {reschedule()}
    }
    fun canSilenceNow(): Boolean = audio.capable() && alarms.exactAccess
    /** An active ringer session the person can end now (prayer or manual). */
    fun activeSilence(): Session? = runCatching {journal.read().session}.getOrNull()
        ?.takeIf {it.state==SessionState.ACTIVE && it.deadline>clock().toEpochMilli()}
    /** "Silence now" from the tile, shortcut or app: mutes for [minutes] and schedules the ringer's return. */
    @Synchronized fun manualSilence(minutes: Int=MANUAL_SILENCE_MINUTES): Session? {
        if(!begin())return null
        if(!canSilenceNow()) {error=text(R.string.rt_manual_unavailable);return null}
        var result: Session?=null
        attempt(R.string.rt_manual_failed) {
            val now=clock().toEpochMilli()
            result=sessions.start("manual:$now",now,minutes,preferences.mode)
            result?.takeIf {it.state==SessionState.ACTIVE}?.let {
                notice=text(R.string.rt_manual_started,displayTimeFor(Instant.ofEpochMilli(it.deadline)))
            }
        }
        PrayerWidget.updateAll(context)
        return result?.takeIf {it.state==SessionState.ACTIVE}
    }
    private fun displayTimeFor(at: Instant)=context.prayerLanguage().prayerTime(at,preferences)
    @Synchronized fun trial() {
        if(!begin())return
        attempt(R.string.rt_trial_start) {
            check(audio.capable() && alarms.exactAccess)
            val now=clock().toEpochMilli()
            sessions.start("trial:$now",now,1,preferences.mode)
            notice=text(R.string.rt_trial_started)
        }
    }
    @Synchronized fun scheduledTrial() {
        if(!begin())return
        val now=clock()
        attempt(R.string.rt_trial_schedule) {
            if(!audio.capable() || !alarms.exactAccess ||
                !notifications.enabled(PrayerNotifications.ADHAN) || !notifications.enabled(PrayerNotifications.IQAMA)) {
                error=text(R.string.trial_permissions_required)
                return@attempt
            }
            val active=session()
            if(error!=null)return@attempt
            val ongoingSession=active!=null && active.state !in setOf(SessionState.IDLE,SessionState.RESTORED,SessionState.USER_OVERRIDE)
            val ongoingTrial=alarms.trialEvent()?.time?.plusSeconds(300)?.isAfter(now)==true
            val prayerDueSoon=nextScheduled?.at?.let {it<=now.plusSeconds(330)}==true
            if(ongoingSession || ongoingTrial || prayerDueSoon) {
                error=text(R.string.trial_existing_schedule)
                return@attempt
            }
            alarms.scheduleTrial(now.plusSeconds(30).toEpochMilli())
            notice=text(R.string.trial_scheduled)
        }
    }
    /** Posts a sample adhan/iqama notification. The caller shows a localized result. */
    @Synchronized fun testNotification(adhan: Boolean): Boolean {
        if(!begin())return false
        val channel=if(adhan)PrayerNotifications.ADHAN else PrayerNotifications.IQAMA
        if(!notifications.enabled(channel))return false
        var delivered=false
        attempt(R.string.rt_test_notification) {
            val now=clock()
            delivered=notifications.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),if(adhan)PrayerAction.ADHAN else PrayerAction.IQAMA,now),preferences,true)==NotificationDelivery.POSTED
        }
        return delivered
    }
    private fun record(tasks: List<ScheduledPrayer>,now: Instant,delivery: Map<String,String>) {
        if(tasks.isEmpty())return
        val strings=context.prayerLanguage()
        val text=tasks.joinToString(" · ") { task ->
            val prayer=strings.prayerName(task.event.prayer)
            val label=when(task.action) {
                PrayerAction.ADHAN->strings.getString(R.string.diag_adhan,prayer)
                PrayerAction.IQAMA->strings.getString(R.string.diag_iqama,prayer)
                PrayerAction.PRE_ADHAN->strings.getString(R.string.diag_pre_adhan,prayer)
                PrayerAction.SILENCE->strings.getString(R.string.diag_silence)
                PrayerAction.FRIDAY->strings.getString(R.string.diag_friday)
                PrayerAction.LIVE->strings.getString(R.string.diag_live,prayer)
                PrayerAction.REMINDER->strings.getString(R.string.diag_reminder)
            }
            val result=delivery[task.id] ?: strings.getString(when(session()?.state) {
                SessionState.ACTIVE->R.string.diag_active
                SessionState.RESTORED->R.string.diag_restored
                SessionState.USER_OVERRIDE->R.string.diag_override
                SessionState.PERMISSION_REVOKED->R.string.diag_revoked
                SessionState.RESTORE_FAILED,SessionState.INTERRUPTED,SessionState.PREPARED,SessionState.APPLYING->R.string.diag_review
                else->R.string.diag_no_session
            })
            "$label: $result"
        }
        val line=strings.getString(R.string.diag_line,text,strings.prayerTime(tasks.first().at,preferences),strings.prayerTime(now,preferences))
        dispatchText=line
        diagnostic.write(JSONObject().put("text",line))
    }
    private fun dispatch(tasks: List<ScheduledPrayer>,p: Preferences,test: Boolean=false) {
        val delivery=mutableMapOf<String,String>()
        PrayerDispatch(sessions) {task,prefs->
            delivery[task.id]=text(when(notifications.show(task,prefs,test)) {
                NotificationDelivery.POSTED->R.string.diag_posted
                NotificationDelivery.POSTED_HISTORY_UNAVAILABLE->R.string.diag_posted_no_history
                NotificationDelivery.BLOCKED->R.string.diag_blocked
                NotificationDelivery.ALREADY_POSTED->R.string.diag_already
            })
        }.deliver(tasks,p) {task,e->
            Log.w(TAG,"dispatch ${task.id}",e)
            error=text(R.string.rt_dispatch_prayer,context.prayerLanguage().prayerName(task.event.prayer))
            delivery[task.id]=text(R.string.diag_failed)
        }
        record(tasks,clock(),delivery)
        if(p.broadcastEvents && !test)tasks.forEach {runCatching {broadcastPrayerEvent(context,it)}}
        if(tasks.isNotEmpty())PrayerWidget.updateAll(context)
    }
    private fun reschedule(incomingAt: Long?=null,allowRinger: Boolean=true,refreshAlarms: Boolean=false) {
        nextScheduled=null
        nextNotification=null
        val now=clock()
        // Without exact-alarm access, notifications still go out on approximate alarms;
        // only silence (which must also restore the ringer on time) needs exact access.
        val exact=alarms.exactAccess
        // Keep notification jobs even when their permission is currently off. They
        // must not decide whether the same prayer's silent job is scheduled.
        val tasks=upcomingPrayerSchedule(calculator,now,preferences).filter {exact || it.action!=PrayerAction.SILENCE}
        val due=dispatchablePrayers(alarms.saved(),tasks,now,preferences,incomingAt,exact)
            .filter {allowRinger || it.action!=PrayerAction.SILENCE}
        // Deliver before replacing the persisted plan. Session/notification journals
        // make recovery idempotent if the process stops between these two operations.
        attempt(R.string.rt_dispatch) {dispatch(due,preferences)}
        val future=tasks.filter {it.at>now}
        val window=future.filter {it.at<=now.plusSeconds(86_400)}
        val planned=window.ifEmpty { nextPrayers(future,now) }
        if(attempt(R.string.rt_plan) {alarms.prayers(planned,refreshAlarms)}) {
            nextScheduled=future.firstOrNull()
            val available=mutableMapOf<PrayerAction,Boolean>()
            nextNotification=future.firstOrNull {task->
                available.getOrPut(task.action) {
                    when(task.action) {
                        PrayerAction.ADHAN -> notifications.enabled(PrayerNotifications.ADHAN)
                        PrayerAction.IQAMA -> notifications.enabled(PrayerNotifications.IQAMA)
                        PrayerAction.FRIDAY -> notifications.enabled(PrayerNotifications.FRIDAY)
                        PrayerAction.PRE_ADHAN -> notifications.enabled(PrayerNotifications.PRE_ADHAN)
                        PrayerAction.LIVE -> notifications.enabled(PrayerNotifications.LIVE)
                        PrayerAction.REMINDER -> notifications.enabled(PrayerNotifications.REMINDERS)
                        PrayerAction.SILENCE -> false
                    }
                }
            }
        }
    }
    @Synchronized fun receive(action: String?,id: String,at: Long,allowRinger: Boolean=true) {
        if(action !in listOf("org.najwa.restore","org.najwa.prayer","org.najwa.trial"))return
        val available=begin()
        if(allowRinger) {
            if(action=="org.najwa.restore")attempt(R.string.rt_restore_ringer) {sessions.restore(id)}
            else attempt(R.string.rt_review_ringer) {sessions.reconcile()}
        } else if(action=="org.najwa.restore") {
            attempt(R.string.rt_defer_restore) {
                val active=session()?.takeIf {it.id==id && it.state in listOf(SessionState.ACTIVE,SessionState.APPLYING)}
                if(active!=null && at==active.deadline && alarms.exactAccess) {
                    val now=clock()
                    // One quick retry, then space attempts to avoid frequent wakeups.
                    val delay=if(now.toEpochMilli()<=active.deadline)30L else 300L
                    alarms.deferRestore(id,active.deadline,now.plusSeconds(delay).toEpochMilli())
                }
            }
        }
        if(!allowRinger && action=="org.najwa.prayer") {
            notice=text(R.string.system_background_unavailable)
        }
        if(action=="org.najwa.trial" && available && allowRinger)attempt(R.string.rt_trial_delayed) {
            val event=alarms.trialEvent()
            if(event!=null && alarms.exactAccess && audio.capable()) {
                val trial=preferences.copy(autoSilent=true,adhanNotifications=true,iqamaNotifications=true,fridayReminder=false,preAdhanReminder=false,
                    adhanPrayers=dailyPrayers,iqamaPrayers=dailyPrayers,jumuah=false,
                    enabled=setOf(event.prayer),silenceMinutes=preferences.silenceMinutes.mapValues {5},iqamaMinutes=preferences.iqamaMinutes.mapValues {1})
                val tasks=prayerSchedule(listOf(event),trial)
                val due=dispatchablePrayers(emptyList(),tasks,clock(),trial,at)
                dispatch(due,trial,true)
            }
        }
        if(available)attempt(R.string.rt_schedule) {reschedule(if(action=="org.najwa.prayer")at else null,allowRinger)}
        PrayerWidget.updateAll(context)
        checkHealthInBackground()
    }
    /** Tells the person (at most daily) when a permission change would silence their alerts. */
    fun checkHealthInBackground() = runCatching {notifyHealthProblem(context,healthIssues(context,this))}

    // ── Mosque profiles ──
    fun mosqueList(): List<Mosque> = mosques.all()
    @Synchronized fun saveMosque(m: Mosque,apply: Boolean) {
        mosques.upsert(m)
        if(apply || preferences.activeMosque==m.id)applyMosque(m)
    }
    @Synchronized fun applyMosque(m: Mosque) = update(preferences.copy(iqamaMinutes=m.iqamaMinutes,activeMosque=m.id,
        fridaySilenceMinutes=m.jumuahSilence ?: preferences.fridaySilenceMinutes))
    @Synchronized fun deleteMosque(id: String) {
        mosques.delete(id)
        if(preferences.activeMosque==id)update(preferences.copy(activeMosque=null))
    }

    // ── Prayer log ──
    fun log(): PrayerLog = prayerLog.read()
    @Synchronized fun setPrayed(date: java.time.LocalDate,prayer: Prayer,prayed: Boolean) {
        val log=prayerLog.read()
        if(log.prayed(date,prayer)!=prayed)prayerLog.write(log.toggled(date,prayer))
        org.najwa.app.platform.PrayerLogWidget.update(context)
        org.najwa.app.platform.DayWidget.update(context)
    }
    /** Records how a prayer was prayed (null clears the mark). */
    @Synchronized fun setStatus(date: java.time.LocalDate,prayer: Prayer,status: PrayerStatus?) {
        prayerLog.write(prayerLog.read().withStatus(date,prayer,status))
        org.najwa.app.platform.PrayerLogWidget.update(context)
        org.najwa.app.platform.DayWidget.update(context)
    }
    @Synchronized fun setSunnah(date: java.time.LocalDate,prayer: SunnahPrayer,done: Boolean) =
        prayerLog.write(prayerLog.read().withSunnah(date,prayer,done))
    /** The make-up prayer total; a decrease counts toward today's qada share. */
    @Synchronized fun setQada(value: Int) {
        val before=prayerLog.read()
        val after=before.withQada(value)
        prayerLog.write(after)
        org.najwa.app.platform.QadaPlan.onTotalChanged(context,before.qada,after.qada)
        org.najwa.app.platform.DayWidget.update(context)
    }
    private companion object { const val TAG="Najwa" }
}
