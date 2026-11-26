package org.najwa.core

/**
 * The bottom bar has four tabs: home (0), the mushaf (1), adhkar (2) and settings (3). Silence lives in settings.
 */
enum class Screen(val tab: Int) {
    HOME(0), SILENCE(3), SETTINGS(3), PERMISSIONS(3), NOTIFICATIONS(3),
    WIDGET(3), INFORMATION(3), ABOUT(3), CONTACT(3), PRIVACY(3), CALCULATION(3), APPEARANCE(3), QIBLA(0), BACKUP(3),
    MOSQUES(3), SOUND(3), SUNNAH(3), ADVANCED(3), TASBEEH(0), ADHKAR(2), TIMETABLE(0), PRAYER_LOG(0), QURAN(1), MUSHAF(1);
    val parent: Screen? get() = when(this) {
        HOME -> null
        SETTINGS, QIBLA, TASBEEH, ADHKAR, TIMETABLE, PRAYER_LOG, QURAN -> HOME
        SILENCE -> SETTINGS
        PERMISSIONS -> SILENCE
        NOTIFICATIONS, WIDGET, INFORMATION, CALCULATION, APPEARANCE, BACKUP, MOSQUES, SOUND, SUNNAH, ADVANCED -> SETTINGS
        ABOUT, CONTACT, PRIVACY -> INFORMATION
        MUSHAF -> QURAN
    }
    companion object {
        fun fromSaved(value: String) = entries.firstOrNull {it.name==value} ?: HOME
        const val TABS = 4
        fun forTab(index: Int) = when(index) {1->QURAN;2->ADHKAR;3->SETTINGS;else->HOME}
    }
}

/** ADHAN: waiting for the next adhan. IQAMA: adhan called, waiting for iqama.
 * PRAYER (widget only): iqama reached, prayer in progress until the prayer's silence window ends. */
enum class CountdownPhase { ADHAN, IQAMA, PRAYER }

/** A tab starts its own trail; a detail retains the actual screen that opened it. */
fun navigateScreens(trail: List<String>,destination: Screen,tab: Boolean=false): List<String> {
    if(tab)return if(destination==Screen.HOME)listOf(Screen.HOME.name) else listOf(Screen.HOME.name,destination.name)
    if(trail.lastOrNull()==destination.name)return trail
    val previous=trail.indexOf(destination.name)
    return if(previous>=0)trail.take(previous+1) else trail+destination.name
}
fun backScreens(trail: List<String>)=if(trail.size>1)trail.dropLast(1) else listOf(Screen.HOME.name)
data class WidgetPrayer(val city: String,val prayer: Prayer,val time: java.time.Instant,val timeLabel: String,val iqamaLabel: String?,val phase: CountdownPhase=CountdownPhase.ADHAN,val start: java.time.Instant?=null) {
    val heading get()=if(phase==CountdownPhase.IQAMA)"المتبقي على الإقامة" else "الصلاة القادمة"
}
fun widgetPrayer(now: java.time.Instant,p: Preferences): WidgetPrayer? {
    return prayerCountdown(PrayerCalculator().around(now,p).flatMap {it.events},now,p,includePrayer=true)
}
fun prayerCountdown(events: List<PrayerEvent>,now: java.time.Instant,p: Preferences,includePrayer: Boolean=false): WidgetPrayer? {
    val prayers=events.filter {it.prayer.isPrayer}.sortedBy {it.time}
    val previous=prayers.lastOrNull {it.time<=now}
    if(p.showIqamaCountdown && previous!=null) {
        val iqama=previous.time.plusSeconds(p.iqamaMinutes.getValue(previous.prayer)*60L)
        if(now<iqama)return WidgetPrayer(p.city.name,previous.prayer,iqama,displayTime(iqama,p),displayTime(iqama,p),CountdownPhase.IQAMA,previous.time)
        // The prayer lasts until the silence window chosen for it (counted from adhan) ends.
        val end=previous.time.plusSeconds(p.silenceFor(previous)*60L)
        if(includePrayer && now<end)return WidgetPrayer(p.city.name,previous.prayer,end,displayTime(end,p),displayTime(iqama,p),CountdownPhase.PRAYER,iqama)
    }
    val event=prayers.firstOrNull {it.time>now} ?: return null
    return WidgetPrayer(p.city.name,event.prayer,event.time,displayTime(event.time,p),
        if(p.showIqamaCountdown)displayTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p) else null,
        CountdownPhase.ADHAN,previous?.time)
}
