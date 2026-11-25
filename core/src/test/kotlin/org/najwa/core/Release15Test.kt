package org.najwa.core

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class Release15Test {
    private val riyadh=City("الرياض",24.7136,46.6753,"Asia/Riyadh")
    private val london=City("London",51.5074,-0.1278,"Europe/London")
    private val tehran=City("Tehran",35.6892,51.389,"Asia/Tehran")
    private val calc=PrayerCalculator()
    private fun day(date: LocalDate,p: Preferences)=calc.calculate(date,p)
    private fun PrayerDay.at(prayer: Prayer)=events.first {it.prayer==prayer}.time

    @Test fun shiaMethodsDelayMaghribAfterSunset() {
        val date=LocalDate.of(2026,11,23)
        val sunni=day(date,Preferences(city=tehran,method=Method.MWL))
        val jafari=day(date,Preferences(city=tehran,method=Method.JAFARI))
        val gap=Duration.between(sunni.at(Prayer.MAGHRIB),jafari.at(Prayer.MAGHRIB)).toMinutes()
        assertTrue("maghrib delayed by $gap",gap in 12..25)
    }

    @Test fun moonsightingUsesSeasonalLimitsAndAdjustments() {
        for(month in 1..12) {
            val d=day(LocalDate.of(2027,month,10),Preferences(city=london,method=Method.MOONSIGHTING))
            assertTrue("month $month",d.complete)
            val fajrBeforeSunrise=Duration.between(d.at(Prayer.FAJR),d.at(Prayer.SUNRISE)).toMinutes()
            assertTrue("fajr gap $fajrBeforeSunrise in $month",fajrBeforeSunrise in 75..140)
        }
    }

    @Test fun elevationMakesTheDayLonger() {
        val date=LocalDate.of(2026,11,23)
        val sea=day(date,Preferences(city=riyadh))
        val high=day(date,Preferences(city=riyadh,elevation=2000))
        assertTrue(high.at(Prayer.SUNRISE)<sea.at(Prayer.SUNRISE))
        assertTrue(high.at(Prayer.MAGHRIB)>sea.at(Prayer.MAGHRIB))
    }

    @Test fun extraTimesSplitTheNight() {
        val p=Preferences(city=riyadh)
        val today=day(LocalDate.of(2026,11,23),p); val tomorrow=day(LocalDate.of(2026,11,24),p)
        val extra=extraTimes(today,tomorrow)!!
        assertEquals(15L,Duration.between(today.at(Prayer.SUNRISE),extra.duha).toMinutes())
        assertTrue(extra.midnight<extra.lastThird && extra.lastThird<tomorrow.at(Prayer.FAJR))
        assertNull(extraTimes(today,null))
    }

    @Test fun ramadanRemindersOnlyInRamadan() {
        val p=Preferences(city=riyadh,suhoorReminder=true,taraweehReminder=true)
        // 1 Ramadan 1448 ≈ 9 February 2027 (Umm al-Qura).
        val start=(0L..60L).map {LocalDate.of(2027,1,20).plusDays(it)}.first {isRamadan(it,p)}
        val days=(-2L..2L).map {day(start.plusDays(it),p)}
        val reminders=reminderSchedule(days,p)
        val suhoor=reminders.filter {it.reminder==Reminder.SUHOOR}.map {it.event.time.atZone(riyadh.zoneId).toLocalDate()}
        assertEquals(listOf(start,start.plusDays(1),start.plusDays(2)),suhoor)
        val taraweeh=reminders.filter {it.reminder==Reminder.TARAWEEH}.map {it.event.time.atZone(riyadh.zoneId).toLocalDate()}
        // The first taraweeh is the evening before the first fast.
        assertEquals(start.minusDays(1),taraweeh.first())
        assertTrue(reminderSchedule(days,p.copy(ramadanMode=false)).isEmpty())
    }

    @Test fun sunnahRemindersFollowTheirRules() {
        val p=Preferences(city=riyadh,kahfReminder=true,fastingReminders=true,adhkarReminders=true,qiyamReminder=true,duhaReminder=true,sunriseReminder=true)
        val friday=LocalDate.of(2026,11,23).with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
        val days=(-1L..2L).map {day(friday.plusDays(it),p)}
        val r=reminderSchedule(days,p)
        assertEquals(1,r.count {it.reminder==Reminder.KAHF})
        assertTrue(r.filter {it.reminder==Reminder.MORNING_ADHKAR}.all {it.at>it.event.time})
        // Sunday night reminds of Monday's fast.
        val fasting=r.filter {it.reminder==Reminder.FASTING}.map {it.event.time.atZone(riyadh.zoneId).toLocalDate().plusDays(1)}
        assertTrue(fasting.all {fastingReason(it,p)!=null})
        assertEquals(r.size,r.map {it.id}.distinct().size)
        assertTrue(r.zipWithNext().all {it.first.at<=it.second.at})
    }

    @Test fun fastingSkipsEidAndRamadan() {
        val p=Preferences()
        val eid=(0L..400L).map {LocalDate.of(2027,1,1).plusDays(it)}.first {hijriMonth(it,p)==10 && hijriDay(it,p)==1}
        assertNull(fastingReason(eid,p))
        val arafah=(0L..400L).map {LocalDate.of(2027,1,1).plusDays(it)}.first {hijriMonth(it,p)==12 && hijriDay(it,p)==9}
        assertEquals(FastReason.ARAFAH,fastingReason(arafah,p))
    }

    @Test fun mosqueCodesRoundTrip() {
        val m=Mosque("x","مسجد الحي | الشمالي",mapOf(Prayer.FAJR to 25,Prayer.DHUHR to 20,Prayer.ASR to 20,Prayer.MAGHRIB to 7,Prayer.ISHA to 20),50)
        val link=MosqueCode.link(m)
        assertTrue(link.startsWith(MosqueCode.WEB_PREFIX))
        val back=MosqueCode.decode(link)!!
        assertEquals(m.iqamaMinutes,back.iqamaMinutes)
        assertEquals(50,back.jumuahSilence)
        assertEquals("مسجد الحي   الشمالي",back.name)
        assertEquals(back,MosqueCode.decode(MosqueCode.APP_PREFIX+MosqueCode.encode(m)))
        assertNull(MosqueCode.decode("https://example.com/#nonsense"))
    }

    @Test fun prayerLogCountsAndStreaks() {
        val today=LocalDate.of(2026,11,23)
        var log=PrayerLog()
        for(d in 0L..2L) for(prayer in dailyPrayers) log=log.toggled(today.minusDays(d),prayer)
        assertEquals(3,log.streak(today))
        assertEquals(15,log.total(today,7))
        log=log.toggled(today,Prayer.ASR)
        assertFalse(log.prayed(today,Prayer.ASR))
        assertEquals(2,log.streak(today))
        assertEquals(0,log.withQada(-5).qada)
    }

    @Test fun suggestedMethodsMatchTheirCountries() {
        assertEquals(Method.UMM_AL_QURA,suggestedMethod("sa"))
        assertEquals(Method.TURKEY,suggestedMethod("TR"))
        assertNull(suggestedMethod(null))
    }
}
