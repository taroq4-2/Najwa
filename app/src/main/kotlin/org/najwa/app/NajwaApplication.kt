package org.najwa.app

import android.app.Application
import android.content.Context
import org.json.JSONObject
import org.najwa.app.platform.*
import org.najwa.core.*
import java.time.Instant
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

internal val prayerExecutor = Executors.newSingleThreadExecutor()
internal val prayerDispatcher = prayerExecutor.asCoroutineDispatcher()

class NajwaApplication: Application() {
    lateinit var runtime: NajwaRuntime; private set
    override fun onCreate() { super.onCreate(); runtime=NajwaRuntime(this) }
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
    @Volatile var error: String?=null; private set
    private var preferencesAvailable=true
    @Volatile var preferences=runCatching { preferencesStore.read() }.getOrElse { preferencesAvailable=false;error="تعذّر قراءة الإعدادات المحفوظة. ${it.javaClass.simpleName}";Preferences() }; private set
    @Volatile var nextScheduled: ScheduledPrayer?=null;private set
    @Volatile var nextNotification: ScheduledPrayer?=null;private set
    @Volatile var notice: String?=null;private set
    @Volatile private var dispatchText=runCatching {diagnostic.read()?.optString("text")?.takeIf {it.isNotBlank()}}.getOrNull()
    fun lastDispatch(): String? = dispatchText
    fun session(): Session? = try {journal.read().session} catch(e: Exception) {error="تعذّر قراءة حالة الرنين؛ لن نغيّر الصوت. ${e.javaClass.simpleName}";null}
    fun displayedSession(): Session? = journal.cachedSnapshot()?.session
    private fun attempt(label: String,block: ()->Unit): Boolean = try {block();true} catch(e: Exception) {
        error=listOfNotNull(error,"$label: ${e.javaClass.simpleName}").joinToString("\n");false
    }
    private fun begin(): Boolean {
        error=null
        if(!preferencesAvailable) return attempt("تعذّر قراءة الإعدادات المحفوظة") {preferences=preferencesStore.read();preferencesAvailable=true}
        return true
    }
    @Synchronized fun update(p: Preferences) {
        if(!begin())return
        val hideDetails=preferences.showLockScreenDetails && !p.showLockScreenDetails
        val disabled=preferences.autoSilent && !p.autoSilent
        if(!attempt("تعذّر حفظ الإعدادات") {preferencesStore.write(p)})return
        preferences=p
        if(hideDetails)attempt("تعذّر إخفاء التنبيهات السابقة") {notifications.hidePreviouslyPublicAlerts()}
        PrayerWidget.updateAll(context)
        if(disabled)attempt("تعذّرت استعادة الرنين") {sessions.disable()}
        attempt("تعذّرت جدولة المواقيت") {reschedule()}
    }
    @Synchronized fun reconcile(refreshAlarms: Boolean=false) {
        val available=begin()
        attempt("تعذّرت مراجعة الرنين") {sessions.reconcile()}
        if(available)attempt("تعذّرت جدولة المواقيت") {reschedule(refreshAlarms=refreshAlarms)}
    }
    @Synchronized fun restore() {
        begin()
        attempt("تعذّرت استعادة الرنين") {session()?.let {sessions.restore(it.id,true)}}
        if(preferencesAvailable)attempt("تعذّرت جدولة المواقيت") {reschedule()}
    }
    @Synchronized fun trial() {
        if(!begin())return
        attempt("تعذّر بدء التجربة") {
            check(audio.capable() && alarms.exactAccess)
            val now=clock().toEpochMilli()
            sessions.start("trial:$now",now,1,preferences.mode)
            notice="بدأ اختبار الصمت لمدة دقيقة."
        }
    }
    @Synchronized fun scheduledTrial() {
        if(!begin())return
        val strings=context.prayerLanguage()
        val now=clock()
        attempt("تعذّرت جدولة التجربة") {
            if(!audio.capable() || !alarms.exactAccess ||
                !notifications.enabled(PrayerNotifications.ADHAN) || !notifications.enabled(PrayerNotifications.IQAMA)) {
                error=strings.getString(org.najwa.app.R.string.trial_permissions_required)
                return@attempt
            }
            val active=session()
            if(error!=null)return@attempt
            val ongoingSession=active!=null && active.state !in setOf(SessionState.IDLE,SessionState.RESTORED,SessionState.USER_OVERRIDE)
            val ongoingTrial=alarms.trialEvent()?.time?.plusSeconds(300)?.isAfter(now)==true
            val prayerDueSoon=nextScheduled?.at?.let {it<=now.plusSeconds(330)}==true
            if(ongoingSession || ongoingTrial || prayerDueSoon) {
                error=strings.getString(org.najwa.app.R.string.trial_existing_schedule)
                return@attempt
            }
            alarms.scheduleTrial(now.plusSeconds(30).toEpochMilli())
            notice=strings.getString(org.najwa.app.R.string.trial_scheduled)
        }
    }
    /** Posts a sample adhan/iqama notification. The caller shows a localized result. */
    @Synchronized fun testNotification(adhan: Boolean): Boolean {
        if(!begin())return false
        val channel=if(adhan)PrayerNotifications.ADHAN else PrayerNotifications.IQAMA
        if(!notifications.enabled(channel))return false
        var delivered=false
        attempt("تعذّر عرض إشعار التجربة") {
            val now=clock()
            delivered=notifications.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),if(adhan)PrayerAction.ADHAN else PrayerAction.IQAMA,now),preferences,true)==NotificationDelivery.POSTED
        }
        return delivered
    }
    private fun record(tasks: List<ScheduledPrayer>,now: Instant,delivery: Map<String,String>) {
        if(tasks.isEmpty())return
        val text=tasks.joinToString("، ") { task ->
            val label=when(task.action) {
                PrayerAction.ADHAN->"أذان ${task.event.prayer.label}"
                PrayerAction.IQAMA->"إقامة ${task.event.prayer.label}"
                PrayerAction.SILENCE->"الصمت"
                PrayerAction.FRIDAY->"تذكير الجمعة"
            }
            val result=delivery[task.id] ?: when(session()?.state) {
                SessionState.ACTIVE->"صمت نشط"
                SessionState.RESTORED->"استُعيد الرنين"
                SessionState.USER_OVERRIDE->"احتُفظ بوضع الرنين الحالي"
                SessionState.PERMISSION_REVOKED->"إذن تغيير الرنين غير متاح"
                SessionState.RESTORE_FAILED,SessionState.INTERRUPTED,SessionState.PREPARED,SessionState.APPLYING->"تحتاج استعادة الرنين إلى مراجعة"
                else->"لم تبدأ جلسة جديدة؛ تحقق من التفعيل ووضع الهاتف"
            }
            "$label · $result"
        }
        val line="$text · الموعد ${displayTime(tasks.first().at,preferences)} · الاستلام ${displayTime(now,preferences)}"
        dispatchText=line
        diagnostic.write(JSONObject().put("text",line))
    }
    private fun dispatch(tasks: List<ScheduledPrayer>,p: Preferences,test: Boolean=false) {
        val delivery=mutableMapOf<String,String>()
        PrayerDispatch(sessions) {task,prefs->
            delivery[task.id]=when(notifications.show(task,prefs,test)) {
                NotificationDelivery.POSTED->"أُرسل إلى النظام"
                NotificationDelivery.POSTED_HISTORY_UNAVAILABLE->"أُرسل إلى النظام وتعذّر حفظ سجله"
                NotificationDelivery.BLOCKED->"الإذن أو قناة الإشعار غير متاحين"
                NotificationDelivery.ALREADY_POSTED->"أُرسل سابقًا"
            }
        }.deliver(tasks,p) {task,e->
            error="تعذّر تنفيذ ${task.event.prayer.label}: ${e.javaClass.simpleName}"
            delivery[task.id]="تعذّر التنفيذ"
        }
        record(tasks,clock(),delivery)
        if(tasks.isNotEmpty())PrayerWidget.updateAll(context)
    }
    private fun reschedule(incomingAt: Long?=null,allowRinger: Boolean=true,refreshAlarms: Boolean=false) {
        nextScheduled=null
        nextNotification=null
        if(!alarms.exactAccess) {alarms.prayers(emptyList());return}
        val now=clock()
        // Keep notification jobs even when their permission is currently off. They
        // must not decide whether the same prayer's silent job is scheduled.
        val tasks=upcomingPrayerSchedule(calculator,now,preferences)
        val due=dispatchablePrayers(alarms.saved(),tasks,now,preferences,incomingAt)
            .filter {allowRinger || it.action!=PrayerAction.SILENCE}
        // Deliver before replacing the persisted plan. Session/notification journals
        // make recovery idempotent if the process stops between these two operations.
        attempt("تعذّر تنفيذ موعد الصلاة") {dispatch(due,preferences)}
        val future=tasks.filter {it.at>now}
        val window=future.filter {it.at<=now.plusSeconds(86_400)}
        val planned=window.ifEmpty { nextPrayers(future,now) }
        if(attempt("تعذّر إعداد مواعيد الصلاة") {alarms.prayers(planned,refreshAlarms)}) {
            nextScheduled=future.firstOrNull()
            val available=mutableMapOf<PrayerAction,Boolean>()
            nextNotification=future.firstOrNull {task->
                available.getOrPut(task.action) {
                    when(task.action) {
                        PrayerAction.ADHAN -> notifications.enabled(PrayerNotifications.ADHAN)
                        PrayerAction.IQAMA -> notifications.enabled(PrayerNotifications.IQAMA)
                        PrayerAction.FRIDAY -> notifications.enabled(PrayerNotifications.FRIDAY)
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
            if(action=="org.najwa.restore")attempt("تعذّرت استعادة الرنين") {sessions.restore(id)}
            else attempt("تعذّرت مراجعة الرنين") {sessions.reconcile()}
        } else if(action=="org.najwa.restore") {
            attempt("تعذّر تأجيل استعادة الرنين") {
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
            notice=context.prayerLanguage().getString(org.najwa.app.R.string.system_background_unavailable)
        }
        if(action=="org.najwa.trial" && available && allowRinger)attempt("تعذّر الاختبار المؤجل") {
            val event=alarms.trialEvent()
            if(event!=null && alarms.exactAccess && audio.capable()) {
                val trial=preferences.copy(autoSilent=true,adhanNotifications=true,iqamaNotifications=true,fridayReminder=false,
                    adhanPrayers=dailyPrayers,iqamaPrayers=dailyPrayers,
                    enabled=setOf(event.prayer),silenceMinutes=preferences.silenceMinutes.mapValues {5},iqamaMinutes=preferences.iqamaMinutes.mapValues {1})
                val tasks=prayerSchedule(listOf(event),trial)
                val due=dispatchablePrayers(emptyList(),tasks,clock(),trial,at)
                dispatch(due,trial,true)
            }
        }
        if(available)attempt("تعذّرت جدولة المواقيت") {reschedule(if(action=="org.najwa.prayer")at else null,allowRinger)}
    }
}
