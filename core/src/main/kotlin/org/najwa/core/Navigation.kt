package org.najwa.core

enum class Screen(val tab: Int) {
    HOME(0), SILENCE(1), SETTINGS(2), PERMISSIONS(1), NOTIFICATIONS(2),
    WIDGET(2), INFORMATION(2), ABOUT(2), CONTACT(2), PRIVACY(2), CALCULATION(2), APPEARANCE(2);
    val parent: Screen? get() = when(this) {
        HOME -> null
        SILENCE, SETTINGS -> HOME
        PERMISSIONS -> SILENCE
        NOTIFICATIONS, WIDGET, INFORMATION, CALCULATION, APPEARANCE -> SETTINGS
        ABOUT, CONTACT, PRIVACY -> INFORMATION
    }
    companion object {
        fun fromSaved(value: String) = entries.firstOrNull {it.name==value} ?: HOME
        fun forTab(index: Int) = when(index) {1->SILENCE;2->SETTINGS;else->HOME}
    }
}

enum class CountdownPhase { ADHAN, IQAMA }

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
    return prayerCountdown(PrayerCalculator().around(now,p).flatMap {it.events},now,p)
}
fun prayerCountdown(events: List<PrayerEvent>,now: java.time.Instant,p: Preferences): WidgetPrayer? {
    val prayers=events.filter {it.prayer.isPrayer}.sortedBy {it.time}
    val previous=prayers.lastOrNull {it.time<=now}
    if(p.showIqamaCountdown && previous!=null) {
        val iqama=previous.time.plusSeconds(p.iqamaMinutes.getValue(previous.prayer)*60L)
        if(now<iqama)return WidgetPrayer(p.city.name,previous.prayer,iqama,displayTime(iqama,p),displayTime(iqama,p),CountdownPhase.IQAMA,previous.time)
    }
    val event=prayers.firstOrNull {it.time>now} ?: return null
    return WidgetPrayer(p.city.name,event.prayer,event.time,displayTime(event.time,p),
        if(p.showIqamaCountdown)displayTime(event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L),p) else null,
        CountdownPhase.ADHAN,previous?.time)
}
