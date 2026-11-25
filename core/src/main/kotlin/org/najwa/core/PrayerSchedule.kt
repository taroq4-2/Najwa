package org.najwa.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

const val DEFAULT_FRIDAY_LEAD_MINUTES = 30
enum class PrayerAction { SILENCE, ADHAN, IQAMA, FRIDAY, PRE_ADHAN, LIVE, REMINDER }
data class ScheduledPrayer(val event: PrayerEvent, val action: PrayerAction, val at: Instant, val reminder: Reminder? = null) {
    val id get() = "${action.name}${reminder?.let {":"+it.name} ?: ""}:${event.id}:${at.toEpochMilli()}"
}

/** Notifications are independent of the prayers selected for automatic silence. */
fun prayerSchedule(events: List<PrayerEvent>, p: Preferences): List<ScheduledPrayer> =
    (events.filter { it.prayer.isPrayer }.flatMap { event -> buildList {
        if(p.autoSilent && event.prayer in p.enabled) add(ScheduledPrayer(event,PrayerAction.SILENCE,event.time.plusSeconds(20)))
        if(p.preAdhanReminder && event.prayer in p.adhanPrayers) add(ScheduledPrayer(event,PrayerAction.PRE_ADHAN,event.time.minusSeconds(p.preAdhanMinutes*60L)))
        if(p.adhanNotifications && event.prayer in p.adhanPrayers) add(ScheduledPrayer(event,PrayerAction.ADHAN,event.time))
        // The adhan alert brings its own live countdown; without one, a quiet LIVE task posts it
        // (only for people who use alerts at all).
        if(p.liveIqama && p.showIqamaCountdown && (p.adhanNotifications || p.iqamaNotifications) &&
            !(p.adhanNotifications && event.prayer in p.adhanPrayers)) add(ScheduledPrayer(event,PrayerAction.LIVE,event.time))
        if(p.iqamaNotifications && event.prayer in p.iqamaPrayers) add(ScheduledPrayer(event,PrayerAction.IQAMA,event.time.plusSeconds(p.iqamaMinutes.getValue(event.prayer)*60L)))
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
    val regular=(prayerSchedule(days.filter {it.complete}.flatMap {it.events},p)+reminderSchedule(days,p)).sortedBy {it.at}
    if(!p.fridayReminder)return regular
    val nextFriday=now.atZone(p.city.zoneId).toLocalDate().with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
    if(days.any {it.date==nextFriday})return regular
    val friday=calculator.calculate(nextFriday,p)
    return (regular+fridayReminders(friday.events,p)).sortedBy {it.at}
}

/** Only recover work that was already scheduled; enabling a feature never rings retrospectively. */
fun duePrayers(saved: List<ScheduledPrayer>, current: List<ScheduledPrayer>, now: Instant, durationMinutes: Int): List<ScheduledPrayer> {
    return duePrayers(saved,current,now,NOTIFICATION_GRACE_MS) {_: PrayerEvent->durationMinutes}
}
/** A notification more than this late is dropped; inexact alarms (no exact-alarm access) get [INEXACT_GRACE_MS]. */
const val NOTIFICATION_GRACE_MS = 120_000L
const val INEXACT_GRACE_MS = 15*60_000L
fun duePrayers(saved: List<ScheduledPrayer>, current: List<ScheduledPrayer>, now: Instant, grace: Long=NOTIFICATION_GRACE_MS, duration: (PrayerEvent)->Int): List<ScheduledPrayer> {
    val valid=current.map { it.id }.toSet()
    return saved.filter { task ->
        val late=now.toEpochMilli()-task.at.toEpochMilli()
        task.id in valid && late>=0 && when(task.action) {
            PrayerAction.SILENCE -> now.toEpochMilli()-task.event.time.toEpochMilli() < duration(task.event)*60_000L
            // A reminder before adhan is useless once adhan has passed.
            PrayerAction.PRE_ADHAN -> late <= grace && now < task.event.time
            else -> late <= grace
        }
    }
}

fun nextPrayers(schedule: List<ScheduledPrayer>, now: Instant): List<ScheduledPrayer> {
    val next=schedule.filter { it.at > now }.minOfOrNull { it.at } ?: return emptyList()
    return schedule.filter { it.at == next }
}
