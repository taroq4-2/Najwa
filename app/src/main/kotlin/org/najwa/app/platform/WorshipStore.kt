package org.najwa.app.platform

import android.content.Context
import org.najwa.app.BuildConfig
import org.najwa.app.ui.Adhkar
import org.najwa.app.ui.Dhikr
import org.najwa.core.GoalKind
import org.najwa.core.TimeWindow
import org.najwa.core.WorshipActions
import org.najwa.core.WorshipRecord
import java.time.LocalDate

/** Settings of the worship companion, kept apart from the prayer preferences that drive alerts and silence. */
data class WorshipSettings(
    /** Master switch: off restores the 1.6 screens exactly; recorded data is kept. */
    val enabled: Boolean = true,
    val summary: Boolean = true,
    val focus: Boolean = false,
    val goalKind: GoalKind = GoalKind.QURAN_PAGES,
    /** 0 = no personal goal. */
    val goal: Long = 0,
    /** Trial builds only: count rules still pending scholarly review, clearly labelled as such. */
    val preview: Boolean = false,
    val widgetReward: Boolean = true,
    val widgetExpiation: Boolean = true,
    val widgetAdhkar: Boolean = true,
) {
    val previewActive get() = preview && WorshipSettingsStore.previewAvailable
}

object WorshipSettingsStore {
    /** Rule preview exists only in debug and trial builds; release builds never count unreviewed rules. */
    val previewAvailable get() = BuildConfig.DEBUG || BuildConfig.TRIAL_BUILD
    private fun prefs(context: Context)=context.getSharedPreferences("worship_settings",Context.MODE_PRIVATE)
    fun read(context: Context)=prefs(context).let {s->
        WorshipSettings(s.getBoolean("enabled",true),s.getBoolean("summary",true),s.getBoolean("focus",false),
            runCatching {GoalKind.valueOf(s.getString("goalKind",null) ?: "")}.getOrDefault(GoalKind.QURAN_PAGES),
            // Trial builds open with the preview on, so testers see every figure (marked as not approved).
            s.getLong("goal",0).coerceAtLeast(0),s.getBoolean("preview",previewAvailable && BuildConfig.TRIAL_BUILD),
            s.getBoolean("widgetReward",true),s.getBoolean("widgetExpiation",true),s.getBoolean("widgetAdhkar",true))
    }
    fun write(context: Context,w: WorshipSettings) {
        prefs(context).edit().putBoolean("enabled",w.enabled).putBoolean("summary",w.summary).putBoolean("focus",w.focus)
            .putString("goalKind",w.goalKind.name).putLong("goal",w.goal.coerceAtLeast(0)).putBoolean("preview",w.preview)
            .putBoolean("widgetReward",w.widgetReward).putBoolean("widgetExpiation",w.widgetExpiation).putBoolean("widgetAdhkar",w.widgetAdhkar).apply()
        DayWidget.update(context)
    }
}

/**
 * Confirmed worship actions per day. The single source for dhikr completions, tasbeeh rounds and Quran reading ranges
 * (prayers are read from the prayer log itself, never copied). Records are keyed by id, so writing the same id twice
 * changes nothing and an intentional repeat carries a new id. Kept for 400 days (like the Quran and adhkar days), private to the app, never backed up.
 */
object WorshipStore {
    data class Entry(val id: String,val action: String,val count: Long,val window: TimeWindow,val from: Int=0) {
        fun record(date: LocalDate)=WorshipRecord(id,action,count,window,date)
    }
    const val QURAN_RANGE="quran.range"
    private const val KEEP_DAYS=400L

    private fun prefs(context: Context)=context.getSharedPreferences("worship",Context.MODE_PRIVATE)

    fun entries(context: Context,date: LocalDate): List<Entry> =
        prefs(context).getString("d_$date",null).orEmpty().split(';').mapNotNull(::parse)

    /** Replaces the entries whose id starts with [prefix] by [entries], in one write. */
    @Synchronized fun replace(context: Context,date: LocalDate,prefix: String,entries: List<Entry>) {
        val kept=entries(context,date).filterNot {it.id.startsWith(prefix)}
        save(context,date,kept+entries)
    }
    @Synchronized fun put(context: Context,date: LocalDate,entry: Entry) {
        save(context,date,entries(context,date).filterNot {it.id==entry.id}+entry)
    }
    @Synchronized fun remove(context: Context,date: LocalDate,id: String) {
        save(context,date,entries(context,date).filterNot {it.id==id})
    }
    @Synchronized fun newId(context: Context,prefix: String): String {
        val n=prefs(context).getLong("seq",0)+1
        prefs(context).edit().putLong("seq",n).apply()
        return "$prefix$n"
    }
    /** Every restart of an adhkar category opens a new session, so a second full reading is a new record. */
    fun session(context: Context,category: String,date: LocalDate)=prefs(context).getInt("s_${category}_$date",0)
    @Synchronized fun nextSession(context: Context,category: String,date: LocalDate) {
        prefs(context).edit().putInt("s_${category}_$date",session(context,category,date)+1).apply()
    }
    /** Deletes only the worship companion's own records; prayer log, adhkar progress and settings stay. */
    fun clear(context: Context) {prefs(context).edit().clear().apply()}

    private fun save(context: Context,date: LocalDate,list: List<Entry>) {
        val p=prefs(context)
        val edit=p.edit().putString("d_$date",list.joinToString(";") {listOf(it.id,it.action,it.count,it.window.name,it.from).joinToString(",")})
        // Drop days older than the window, so storage stays bounded.
        p.all.keys.filter {it.startsWith("d_") || it.startsWith("s_")}.forEach {key->
            val day=runCatching {LocalDate.parse(key.substringAfterLast('_'))}.getOrNull()
            if(day!=null && day.isBefore(date.minusDays(KEEP_DAYS)))edit.remove(key)
        }
        edit.commit()
    }
    private fun parse(raw: String): Entry? {
        val f=raw.split(',')
        if(f.size<5)return null
        return runCatching {Entry(f[0],f[1],f[2].toLong(),TimeWindow.valueOf(f[3]),f[4].toInt())}.getOrNull()
    }
}

/** Translates confirmed progress in the existing screens into worship records. */
object WorshipRecorder {
    private fun windowOf(category: String)=when(category) {
        "morning"->TimeWindow.MORNING
        "evening"->TimeWindow.EVENING
        "after"->TimeWindow.AFTER_PRAYER
        "sleep"->TimeWindow.BEFORE_SLEEP
        else->TimeWindow.DAY
    }
    private val tahlil="لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ"
    private fun actionOf(d: Dhikr)=when {
        d.text==Adhkar.KURSI_TEXT->WorshipActions.KURSI
        d.text=="سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"->WorshipActions.TASBIH_BIHAMDIHI
        d.text==tahlil->WorshipActions.TAHLIL
        d.text.startsWith("بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ")->WorshipActions.BISMILLAH_SHIELD
        d.text.startsWith("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ")->WorshipActions.KALIMAT
        d.source.startsWith("سيد الاستغفار")->WorshipActions.SAYYID_ISTIGHFAR
        else->null
    }

    /**
     * The records a category's current progress implies (completed items only). Recomputed on every change,
     * so an undo that reopens an item removes its record, and nothing is ever counted twice.
     */
    fun adhkarEntries(category: String,items: List<Dhikr>,remaining: List<Int>,session: Int): List<WorshipStore.Entry> {
        val window=windowOf(category)
        fun done(i: Int)=remaining.getOrElse(i) {1}==0
        val out=mutableListOf<WorshipStore.Entry>()
        items.forEachIndexed {i,d-> if(done(i))actionOf(d)?.let {out+=WorshipStore.Entry("a:$category:$session:$i",it,d.count.toLong(),window)}}
        // The three protective surahs count together, once all three are complete.
        val trio=items.indices.filter {items[it].text in Adhkar.MUAWWIDHAT_TEXTS}
        if(trio.size==3 && trio.all(::done))out+=WorshipStore.Entry("a:$category:$session:trio",WorshipActions.MUAWWIDHAT,trio.minOf {items[it].count}.toLong(),window)
        // After prayer: 33 tasbih, 33 tahmid, 33 takbir and the tahlil that completes the hundred.
        if(category=="after") {
            val hundred=items.indices.filter {Adhkar.isAfterPrayerHundred(items[it])}
            if(hundred.size==4 && hundred.all(::done))out+=WorshipStore.Entry("a:$category:$session:hundred",WorshipActions.AFTER_PRAYER_HUNDRED,1,window)
        }
        return out
    }
    fun onAdhkar(context: Context,category: String,items: List<Dhikr>,remaining: List<Int>,date: LocalDate=WorshipDay.today(context)) {
        val session=WorshipStore.session(context,category,date)
        WorshipStore.replace(context,date,"a:$category:$session:",adhkarEntries(category,items,remaining,session))
        DayWidget.update(context)
    }
    fun onAdhkarRestart(context: Context,category: String,date: LocalDate=WorshipDay.today(context)) = WorshipStore.nextSession(context,category,date)

    /** A finished tasbeeh round: only phrases with linked evidence are recorded. */
    fun onTasbeehRound(context: Context,phrase: Int,target: Int,date: LocalDate=WorshipDay.today(context)) {
        val action=when(phrase) {
            0->WorshipActions.TASBIH
            5->WorshipActions.TASBIH_BIHAMDIHI
            6->WorshipActions.SALAWAT
            else->return
        }
        if(target<=0)return
        WorshipStore.put(context,date,WorshipStore.Entry(WorshipStore.newId(context,"t:"),action,target.toLong(),TimeWindow.DAY))
        DayWidget.update(context)
    }

    /** The last reading range recorded today, which an undo takes back. */
    fun lastQuranRange(context: Context,date: LocalDate=WorshipDay.today(context))=
        WorshipStore.entries(context,date).lastOrNull {it.action==WorshipStore.QURAN_RANGE}

    /** Pages [from]..from+pages-1 confirmed as read; returns the record id so it can be undone. */
    fun onQuranRead(context: Context,from: Int,pages: Int,date: LocalDate=WorshipDay.today(context)): String {
        val id=WorshipStore.newId(context,"q:")
        WorshipStore.put(context,date,WorshipStore.Entry(id,WorshipStore.QURAN_RANGE,pages.toLong(),TimeWindow.ANY_TIME,from))
        DayWidget.update(context)
        return id
    }
}
