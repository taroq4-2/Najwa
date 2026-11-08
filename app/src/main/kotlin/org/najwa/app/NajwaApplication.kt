package org.najwa.app

import android.app.Application
import android.content.Context
import org.json.JSONObject
import org.najwa.app.platform.*
import org.najwa.core.*
import java.time.Instant

class NajwaApplication: Application() {
    lateinit var runtime: NajwaRuntime; private set
    override fun onCreate() { super.onCreate(); runtime=NajwaRuntime(this) }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        PrayerWidget.updateAll(this)
    }
}
class NajwaRuntime(private val context: Context,private val clock: ()->Instant = Instant::now) {
    private val preferencesStore=PreferencesStore(context)
    private val diagnostic=JsonFile(context,"last-dispatch.json")
    val journal=AndroidJournal(context)
    val audio=AndroidAudio(context)
    val alarms=AndroidAlarms(context)
    val notifications=PrayerNotifications(context)
    val calculator=PrayerCalculator()
    val sessions=SilenceSessionManager(journal,audio,alarms,{clock().toEpochMilli()})
    var error: String?=null; private set
    private var preferencesAvailable=true
    var preferences=runCatching { preferencesStore.read() }.getOrElse { preferencesAvailable=false;error="تعذّر قراءة الإعدادات المحفوظة. ${it.javaClass.simpleName}";Preferences() }; private set
    var nextScheduled: ScheduledPrayer?=null;private set
    var notice: String?=null;private set
    fun lastDispatch(): String? = runCatching {diagnostic.read()?.optString("text")?.takeIf {it.isNotBlank()}}.getOrNull()
    fun session(): Session? = try {journal.read().session} catch(e: Exception) {error="تعذّر قراءة حالة الرنين؛ لن نغيّر الصوت. ${e.javaClass.simpleName}";null}
    fun displayedSession(): Session? = runCatching {journal.snapshot().session}.getOrNull()
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
        val disabled=preferences.autoSilent && !p.autoSilent
        if(!attempt("تعذّر حفظ الإعدادات") {preferencesStore.write(p)})return
        preferences=p
        PrayerWidget.updateAll(context)
        if(disabled)attempt("تعذّرت استعادة الرنين") {sessions.disable()}
        attempt("تعذّرت جدولة المواقيت") {reschedule()}
    }
    @Synchronized fun reconcile() {
        val available=begin()
        attempt("تعذّرت مراجعة الرنين") {sessions.reconcile()}
        if(available)attempt("تعذّرت جدولة المواقيت") {reschedule()}
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
        attempt("تعذّرت جدولة التجربة") {
            check(audio.capable() && alarms.exactAccess)
            alarms.scheduleTrial(clock().toEpochMilli()+30_000L)
            notice="اختبار كامل: أذان بعد 30 ثانية وصمت بعده بـ20 ثانية، إقامة بعد دقيقة، وعودة الرنين بعد 5 دقائق من الأذان. أغلق الشاشة الآن."
        }
    }
    @Synchronized fun testNotification(adhan: Boolean) {
        if(!begin())return
        attempt("تعذّر عرض إشعار التجربة") {
            val channel=if(adhan)PrayerNotifications.ADHAN else PrayerNotifications.IQAMA
            check(notifications.enabled(channel))
            val now=clock()
            notifications.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),if(adhan)PrayerAction.ADHAN else PrayerAction.IQAMA,now),preferences,true)
            notice="أُرسل إشعار التجربة. راجع لوحة إشعارات الهاتف."
        }
    }
    private fun record(tasks: List<ScheduledPrayer>,now: Instant) {
        if(tasks.isEmpty())return
        val s=session()
        val text=tasks.joinToString("، ") {when(it.action) {
            PrayerAction.ADHAN->"أذان ${it.event.prayer.label}"
            PrayerAction.IQAMA->"إقامة ${it.event.prayer.label}"
            PrayerAction.SILENCE->"الصمت"
            PrayerAction.FRIDAY->"تذكير الجمعة"
        }}
        val state=when(s?.state) {
            SessionState.ACTIVE->"صمت نشط"
            SessionState.RESTORED->"استُعيد الرنين"
            SessionState.USER_OVERRIDE->"احتُفظ بتغييرك اليدوي"
            SessionState.PERMISSION_REVOKED->"إذن تغيير الرنين غير متاح"
            SessionState.RESTORE_FAILED,SessionState.INTERRUPTED,SessionState.PREPARED->"تحتاج استعادة الرنين إلى مراجعة"
            else->"لم تبدأ جلسة جديدة؛ تحقق من التفعيل ووضع الهاتف"
        }
        diagnostic.write(JSONObject().put("text","$text · الموعد ${displayTime(tasks.first().at,preferences)} · الاستلام ${displayTime(now,preferences)} · $state"))
    }
    private fun dispatch(tasks: List<ScheduledPrayer>,p: Preferences,test: Boolean=false) {
        PrayerDispatch(sessions) {task,prefs->notifications.show(task,prefs,test)}.deliver(tasks,p) {task,e->
            error="تعذّر تنفيذ ${task.event.prayer.label}: ${e.javaClass.simpleName}"
        }
        record(tasks,clock())
        if(tasks.isNotEmpty())PrayerWidget.updateAll(context)
    }
    private fun reschedule(incomingAt: Long?=null) {
        nextScheduled=null
        if(!alarms.exactAccess) {alarms.prayers(emptyList());return}
        val now=clock()
        // Keep notification jobs even when their permission is currently off. They
        // must not decide whether the same prayer's silent job is scheduled.
        val tasks=upcomingPrayerSchedule(calculator,now,preferences)
        val due=dispatchablePrayers(alarms.saved(),tasks,now,preferences,incomingAt)
        // Deliver before replacing the persisted plan. Session/notification journals
        // make recovery idempotent if the process stops between these two operations.
        attempt("تعذّر تنفيذ موعد الصلاة") {dispatch(due,preferences)}
        val future=tasks.filter {it.at>now}
        if(attempt("تعذّر إعداد مواعيد الصلاة") {alarms.prayers(future)})nextScheduled=future.firstOrNull()
    }
    @Synchronized fun receive(action: String?,id: String,at: Long) {
        if(action !in listOf("org.najwa.restore","org.najwa.prayer","org.najwa.trial"))return
        val available=begin()
        if(action=="org.najwa.restore")attempt("تعذّرت استعادة الرنين") {sessions.restore(id)}
        else attempt("تعذّرت مراجعة الرنين") {sessions.reconcile()}
        if(action=="org.najwa.trial" && available)attempt("تعذّر الاختبار المؤجل") {
            val event=alarms.trialEvent()
            if(event!=null && alarms.exactAccess && audio.capable()) {
                val trial=preferences.copy(autoSilent=true,adhanNotifications=true,iqamaNotifications=true,fridayReminder=false,
                    enabled=setOf(event.prayer),silenceMinutes=preferences.silenceMinutes.mapValues {5},iqamaMinutes=preferences.iqamaMinutes.mapValues {1})
                val tasks=prayerSchedule(listOf(event),trial)
                val due=dispatchablePrayers(emptyList(),tasks,clock(),trial,at)
                dispatch(due,trial,true)
            }
        }
        if(available)attempt("تعذّرت جدولة المواقيت") {reschedule(if(action=="org.najwa.prayer")at else null)}
    }
}
