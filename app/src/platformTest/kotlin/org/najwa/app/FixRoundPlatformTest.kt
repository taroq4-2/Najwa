package org.najwa.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.app.ui.Adhkar
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** The fix round and the new features, on the platform: reminders, backup, khatma, marks, search, travel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class FixRoundPlatformTest {
    private lateinit var app: NajwaApplication
    private lateinit var nm: NotificationManager
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nm=app.getSystemService(NotificationManager::class.java)
        nm.cancelAll()
        WorshipStore.clear(app)
        WorshipSettingsStore.write(app,WorshipSettings())
        WorshipBackup.PREFERENCES.forEach {app.getSharedPreferences(it,0).edit().clear().commit()}
        app.runtime.notifications.updateChannels(false)
    }
    private val today get()=WorshipDay.today(app)
    private fun task(prayer: Prayer,reminder: Reminder,at: Instant=Instant.now())=ScheduledPrayer(PrayerEvent(prayer,at),PrayerAction.REMINDER,at,reminder)
    private fun active(id: Int)=nm.activeNotifications.any {it.id==id}

    @Test fun adhkarReminderSkipsAFinishedRoutineAndCountsWhatIsLeft() {
        val n=app.runtime.notifications
        val p=app.runtime.preferences
        assertEquals(NotificationDelivery.POSTED,n.showReminder(task(Prayer.FAJR,Reminder.MORNING_ADHKAR),p))
        nm.cancelAll()
        AdhkarProgressStore.saveRemaining(app,"morning",Adhkar.morning.map {0},today)
        assertEquals(NotificationDelivery.ALREADY_POSTED,n.showReminder(task(Prayer.FAJR,Reminder.MORNING_ADHKAR),p))
        assertFalse(active(540+Reminder.MORNING_ADHKAR.ordinal))
    }

    @Test fun wirdReminderStopsOnceThePortionIsRead() {
        val n=app.runtime.notifications
        QuranStore.setGoal(app,2)
        assertEquals(NotificationDelivery.POSTED,n.showWird(app.runtime.preferences))
        assertTrue(active(WIRD_ID))
        // Two actions: open at the bookmark, and snooze.
        assertEquals(2,nm.activeNotifications.first {it.id==WIRD_ID}.notification.actions.size)
        QuranStore.readPage(app,1,today);QuranStore.readPage(app,2,today)
        assertEquals(NotificationDelivery.ALREADY_POSTED,n.showWird(app.runtime.preferences))
    }

    @Test fun kahfReminderSkipsWhenTheSurahWasRead() {
        val n=app.runtime.notifications
        (KAHF_PAGE..KAHF_LAST_PAGE).forEach {QuranStore.readPage(app,it,today)}
        assertEquals(NotificationDelivery.ALREADY_POSTED,n.showReminder(task(Prayer.SUNRISE,Reminder.KAHF),app.runtime.preferences))
        assertEquals(Mushaf.load(app).surahs[18].page-1,KAHF_LAST_PAGE)
    }

    @Test fun afterPrayerCardHasThreeActionsAndTheLogCheckReplacesIt() {
        val n=app.runtime.notifications
        val p=app.runtime.preferences.copy(prayerLog=true,afterPrayerCard=true)
        val at=Instant.now().minusSeconds(600)
        assertEquals(NotificationDelivery.POSTED,n.showReminder(task(Prayer.ASR,Reminder.AFTER_PRAYER,at),p))
        val card=nm.activeNotifications.first {it.id==AFTER_PRAYER_ID+Prayer.ASR.ordinal}
        assertEquals(3,card.notification.actions.size)
        n.showReminder(task(Prayer.ASR,Reminder.LOG_CHECK,at),p)
        assertFalse(active(AFTER_PRAYER_ID+Prayer.ASR.ordinal))
        assertTrue(active(LOG_CHECK_ID+Prayer.ASR.ordinal))
    }

    @Test fun remindersAtTheSameTimeArriveAsOneGroup() {
        val n=app.runtime.notifications
        val p=app.runtime.preferences
        n.showReminder(task(Prayer.FAJR,Reminder.MORNING_ADHKAR),p)
        n.showWird(p)
        assertTrue(active(ReminderChannels.SUMMARY_ID))
        assertTrue(nm.activeNotifications.filter {it.id!=ReminderChannels.SUMMARY_ID}.all {it.notification.group==ReminderChannels.GROUP})
        // Each kind has its own channel.
        listOf(ReminderChannels.ADHKAR,ReminderChannels.QURAN,ReminderChannels.SEASON,ReminderChannels.SUNNAH,ReminderChannels.PRAYER)
            .forEach {assertNotNull(nm.getNotificationChannel(it))}
    }

    @Test fun prayedInCongregationFromTheCard() {
        val at=Instant.now()
        val date=at.atZone(app.runtime.preferences.city.zoneId).toLocalDate()
        NotificationActionReceiver().onReceive(app,Intent(PrayerNotifications.MARK_PRAYED).putExtra("prayer","ASR")
            .putExtra("eventAt",at.toEpochMilli()).putExtra("status","JAMAAH"))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(300)
        assertEquals(PrayerStatus.JAMAAH,app.runtime.log().status(date,Prayer.ASR))
    }

    @Test fun protectedBackupNeedsItsPassword() {
        val plain=app.runtime.exportSettings()
        val pw="test-pass-1".toCharArray()
        val locked=BackupCrypto.encrypt(plain,pw)
        assertTrue(BackupCrypto.isEncrypted(locked))
        assertFalse(locked.contains("najwaBackup"))
        assertEquals(plain,BackupCrypto.decrypt(locked,"test-pass-1".toCharArray()))
        assertNull(BackupCrypto.decrypt(locked,"wrong-pass".toCharArray()))
    }

    @Test fun backupPreviewShowsWhatWillBeReplaced() {
        QuranStore.readPage(app,10,today)
        val preview=BackupPreview.of(app.runtime.exportSettings())!!
        assertEquals(10,preview.page)
        assertEquals(app.runtime.preferences.city.name,preview.city)
        assertNull(BackupPreview.of("{\"x\":1}"))
    }

    @Test fun datedPlanSetsThePortionAndTheEnd() {
        QuranStore.setPlan(app,10,today)
        assertEquals(61,QuranStore.read(app).goal)
        assertEquals(today.plusDays(9),QuranStore.finishDate(app,today))
        assertEquals(10,QuranStore.planDays(app))
        // Fixed pages a day ends the dated plan.
        QuranStore.setGoal(app,5)
        assertEquals(0,QuranStore.planDays(app))
        assertEquals(5,QuranStore.read(app).goal)
    }

    @Test fun finishingTheMushafLogsTheKhatma() {
        QuranStore.readPage(app,1,today)
        QuranStore.readPage(app,604,today)
        assertEquals(1,QuranStore.read(app).khatmas)
        assertEquals(today,QuranStore.khatmaLog(app).first().first)
        assertTrue(QuranStore.takeKhatmaMoment(app))
        assertFalse(QuranStore.takeKhatmaMoment(app))
    }

    @Test fun recentMushafReadingIsNoticed() {
        assertEquals(0,QuranStore.recentMushafPages(app))
        QuranStore.readPage(app,5,today)
        assertEquals(1,QuranStore.recentMushafPages(app))
    }

    @Test fun marksAndSearch() {
        val d=Mushaf.load(app)
        AyahMarks.toggle(app,AyahMarks.Kind.BOOKMARK,7)
        assertEquals(setOf(7),AyahMarks.bookmarks(app))
        AyahMarks.toggle(app,AyahMarks.Kind.BOOKMARK,7)
        assertTrue(AyahMarks.bookmarks(app).isEmpty())
        // "الرحمن الرحيم" without marks finds Al-Fatiha 1 and 3.
        val hits=MushafSearch.search(d,"الرحمن الرحيم")
        assertTrue(hits.any {it.sura==1 && it.aya==3})
        assertEquals("الرحمن",MushafSearch.normalize("ٱلرَّحْمَٰنِ"))
        // Every word on a page knows its verse.
        val page=MushafLayout.layout(d,50,600f,1.4f,object: MushafLayout.Measure {
            override val space=3f
            override fun width(token: MushafLayout.Token)=token.text.length*8f
        })
        val first=page.rows.filterIsInstance<MushafLayout.Line>().first().tokens.first()
        assertEquals(d.firstIndex(50),first.verse)
    }

    @Test fun qadaShareCountsWhatWasMadeUpToday() {
        QadaPlan.setPerDay(app,2)
        assertEquals(2,QadaPlan.leftToday(app,10))
        QadaPlan.onTotalChanged(app,10,9)
        assertEquals(1,QadaPlan.leftToday(app,9))
        // Never more than owed, and off at zero.
        assertEquals(0,QadaPlan.leftToday(app,0))
        QadaPlan.setPerDay(app,0)
        assertEquals(0,QadaPlan.leftToday(app,9))
    }

    @Test fun afterPrayerMarksArePerPrayer() {
        AfterPrayerMarks.mark(app,today,Prayer.DHUHR)
        assertTrue(AfterPrayerMarks.done(app,today,Prayer.DHUHR))
        assertFalse(AfterPrayerMarks.done(app,today,Prayer.ASR))
    }

    @Test fun travelHomeIsRemembered() {
        val home=app.runtime.preferences.city
        TravelHome.save(app,home)
        assertEquals(home,TravelHome.read(app))
        TravelHome.clear(app)
        assertNull(TravelHome.read(app))
    }

    @Test fun automationBroadcastCarriesNoWorshipData() {
        val at=Instant.now()
        broadcastPrayerEvent(app,ScheduledPrayer(PrayerEvent(Prayer.ASR,at),PrayerAction.ADHAN,at))
        val sent=shadowOf(app).broadcastIntents.last {it.action==ACTION_PRAYER_EVENT}
        assertEquals(setOf("prayer","stage","time"),sent.extras!!.keySet())
    }

    @Test fun countsWriteDigitsAndNumbersRight() {
        assertEquals("٢٤ ديسمبر",Counts.digits(true,"24 ديسمبر"))
        assertEquals("24",Counts.digits(false,"٢٤"))
        assertEquals("أذكار",Counts.arabicNoun(5,"٥","ذكر واحد","ذكران","أذكار","ذكرًا").substringAfter(' '))
    }
}
