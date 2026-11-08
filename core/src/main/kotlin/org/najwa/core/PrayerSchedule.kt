package org.najwa.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

const val DEFAULT_FRIDAY_LEAD_MINUTES = 30
enum class PrayerAction { SILENCE, ADHAN, IQAMA, FRIDAY }
data class ScheduledPrayer(val event: PrayerEvent, val action: PrayerAction, val at: Instant) {
    val id get() = "${action.name}:${event.id}:${at.toEpochMilli()}"
}

/** Notifications are independent of the prayers selected for automatic silence. */
fun prayerSchedule(events: List<PrayerEvent>, p: Preferences): List<ScheduledPrayer> =
    (events.filter { it.prayer.isPrayer }.flatMap { event -> buildList {
        if(p.autoSilent && event.prayer in p.enabled) add(ScheduledPrayer(event,PrayerAction.SILENCE,event.time.plusSeconds(20)))
        if(p.adhanNotifications) add(ScheduledPrayer(event,PrayerAction.ADHAN,event.time))
        if(p.iqamaNotifications) add(ScheduledPrayer(event,PrayerAction.IQAMA,event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L)))
    } } + fridayReminders(events,p)).sortedBy { it.at }

private fun fridayReminders(events: List<PrayerEvent>, p: Preferences): List<ScheduledPrayer> {
    if(!p.fridayReminder)return emptyList()
    val zone=p.city.zoneId
    return events.mapNotNull {event->
        if(event.prayer!=Prayer.DHUHR || event.time.atZone(zone).dayOfWeek!=DayOfWeek.FRIDAY)return@mapNotNull null
        val at=event.time.minusSeconds(p.fridayLeadMinutes*60L)
        if(at.atZone(zone).dayOfWeek==DayOfWeek.FRIDAY)ScheduledPrayer(event,PrayerAction.FRIDAY,at) else null
    }
}

/** A Friday-only setup still needs an alarm beyond the regular two-day lookahead. */
fun upcomingPrayerSchedule(calculator: PrayerCalculator, now: Instant, p: Preferences): List<ScheduledPrayer> {
    val days=calculator.around(now,p)
    val regular=prayerSchedule(days.filter {it.complete}.flatMap {it.events},p)
    if(!p.fridayReminder)return regular
    val nextFriday=now.atZone(p.city.zoneId).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
    if(days.any {it.date==nextFriday})return regular
    val friday=calculator.calculate(nextFriday,p)
    return (regular+fridayReminders(friday.events,p)).sortedBy {it.at}
}

/** Only recover work that was already scheduled; enabling a feature never rings retrospectively. */
fun duePrayers(saved: List<ScheduledPrayer>, current: List<ScheduledPrayer>, now: Instant, durationMinutes: Int): List<ScheduledPrayer> {
    return duePrayers(saved,current,now) {durationMinutes}
}
fun duePrayers(saved: List<ScheduledPrayer>, current: List<ScheduledPrayer>, now: Instant, duration: (Prayer)->Int): List<ScheduledPrayer> {
    val valid=current.map { it.id }.toSet()
    return saved.filter { task ->
        val late=now.toEpochMilli()-task.at.toEpochMilli()
        task.id in valid && late>=0 && when(task.action) {
            PrayerAction.SILENCE -> now.toEpochMilli()-task.event.time.toEpochMilli() < duration(task.event.prayer)*60_000L
            else -> late <= 120_000L
        }
    }
}

fun nextPrayers(schedule: List<ScheduledPrayer>, now: Instant): List<ScheduledPrayer> {
    val next=schedule.filter { it.at > now }.minOfOrNull { it.at } ?: return emptyList()
    return schedule.filter { it.at == next }
}
