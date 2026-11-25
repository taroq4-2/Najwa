package org.najwa.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.app.ui.Adhkar
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class WorshipPlatformTest {
    private lateinit var app: NajwaApplication
    private val today=LocalDate.now()
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        WorshipStore.clear(app)
        WorshipSettingsStore.write(app,WorshipSettings())
    }

    private fun morningWith(done: Set<Int>)=Adhkar.morning.mapIndexed {i,d->if(i in done)0 else d.count}

    @Test fun completedAdhkarBecomeRecordsAndUndoRemovesThem() {
        val items=Adhkar.morning
        val hundred=items.indexOfFirst {it.text=="سُبْحَانَ اللَّهِ وَبِحَمْدِهِ"}
        WorshipRecorder.onAdhkar(app,"morning",items,morningWith(setOf(hundred)),today)
        val first=WorshipStore.entries(app,today)
        assertEquals(listOf(WorshipActions.TASBIH_BIHAMDIHI),first.map {it.action})
        assertEquals(100L,first.single().count)
        assertEquals(TimeWindow.MORNING,first.single().window)
        // The same state delivered again changes nothing.
        WorshipRecorder.onAdhkar(app,"morning",items,morningWith(setOf(hundred)),today)
        assertEquals(first,WorshipStore.entries(app,today))
        // Undo reopens the item: its record goes away.
        WorshipRecorder.onAdhkar(app,"morning",items,morningWith(emptySet()),today)
        assertTrue(WorshipStore.entries(app,today).isEmpty())
    }

    @Test fun protectiveSurahsCountOnlyWhenAllThreeAreDone() {
        val items=Adhkar.morning
        val trio=items.indices.filter {items[it].text in Adhkar.MUAWWIDHAT_TEXTS}
        assertEquals(3,trio.size)
        WorshipRecorder.onAdhkar(app,"morning",items,morningWith(trio.take(2).toSet()),today)
        assertTrue(WorshipStore.entries(app,today).none {it.action==WorshipActions.MUAWWIDHAT})
        WorshipRecorder.onAdhkar(app,"morning",items,morningWith(trio.toSet()),today)
        assertEquals(3L,WorshipStore.entries(app,today).single {it.action==WorshipActions.MUAWWIDHAT}.count)
    }

    @Test fun afterPrayerHundredNeedsTheClosingTahlil() {
        val items=Adhkar.afterPrayer
        val four=items.indices.filter {Adhkar.isAfterPrayerHundred(items[it])}
        assertEquals(4,four.size)
        fun state(done: Collection<Int>)=items.mapIndexed {i,d->if(i in done)0 else d.count}
        WorshipRecorder.onAdhkar(app,"after",items,state(four.take(3)),today)
        assertTrue(WorshipStore.entries(app,today).none {it.action==WorshipActions.AFTER_PRAYER_HUNDRED})
        WorshipRecorder.onAdhkar(app,"after",items,state(four),today)
        assertEquals(1,WorshipStore.entries(app,today).count {it.action==WorshipActions.AFTER_PRAYER_HUNDRED})
    }

    @Test fun aRestartKeepsEarlierCompletionsAsSeparateRecords() {
        val items=Adhkar.afterPrayer
        val all=items.map {0}
        WorshipRecorder.onAdhkar(app,"after",items,all,today)
        WorshipRecorder.onAdhkarRestart(app,"after",today)
        WorshipRecorder.onAdhkar(app,"after",items,items.map {it.count},today)
        WorshipRecorder.onAdhkar(app,"after",items,all,today)
        assertEquals(2,WorshipStore.entries(app,today).count {it.action==WorshipActions.AFTER_PRAYER_HUNDRED})
    }

    @Test fun tasbeehRoundsOfLinkedPhrasesAreRecorded() {
        TasbeehStore.write(app,TasbeehStore.State(phrase=0,target=3))
        repeat(3) {TasbeehStore.tap(app,today)}
        val r=WorshipStore.entries(app,today).single()
        assertEquals(WorshipActions.TASBIH,r.action)
        assertEquals(3L,r.count)
        // "لا إله إلا الله" alone is not the full tahlil: nothing is recorded for it.
        TasbeehStore.write(app,TasbeehStore.State(phrase=3,target=1))
        TasbeehStore.tap(app,today)
        assertEquals(1,WorshipStore.entries(app,today).size)
    }

    @Test fun quranReadingCanBeUndone() {
        app.getSharedPreferences("quran",0).edit().clear().commit()
        QuranStore.addPages(app,5,today)
        QuranStore.addPages(app,2,today)
        assertEquals(7,QuranStore.today(app,today))
        val last=WorshipRecorder.lastQuranRange(app,today)!!
        assertEquals(6,last.from)
        assertEquals(2L,last.count)
        val back=QuranStore.undoLast(app,today)!!
        assertEquals(5,back.page)
        assertEquals(5,QuranStore.today(app,today))
        assertEquals(1,WorshipStore.entries(app,today).count {it.action==WorshipStore.QURAN_RANGE})
    }

    @Test fun releaseSummaryShowsNoNumbersWhileRulesArePending() {
        val items=Adhkar.morning
        WorshipRecorder.onAdhkar(app,"morning",items,items.map {0},today)
        app.getSharedPreferences("adhkar",0).edit().putString("p_morning","$today|${items.joinToString(",") {"0"}}").commit()
        val release=WorshipSummary.build(app,WorshipSettings(goalKind=GoalKind.HASANAT,goal=10_000),today)
        assertNull(release.virtues.hasanat)
        assertTrue(release.virtues.qualified.isEmpty())
        assertNull("no honest number, so no hasanat goal",release.goal)
        assertTrue(release.morning.complete)
        val pages=WorshipSummary.build(app,WorshipSettings(goalKind=GoalKind.ADHKAR_ITEMS,goal=10),today)
        assertEquals(items.size.toLong(),pages.goal!!.value)
    }

    @Test fun prayersAreReadFromTheLogNotCopied() {
        val log=PrayerLog().withStatus(today,Prayer.FAJR,PrayerStatus.JAMAAH).withStatus(today,Prayer.DHUHR,PrayerStatus.MISSED)
        val r=WorshipSummary.prayerRecords(log,today)
        assertEquals(setOf("p:FAJR","j:FAJR"),r.map {it.id}.toSet())
    }

    @Test fun turningTheCompanionOffStopsRecording() {
        WorshipSettingsStore.write(app,WorshipSettings(enabled=false))
        TasbeehStore.write(app,TasbeehStore.State(phrase=0,target=1))
        TasbeehStore.tap(app,today)
        app.getSharedPreferences("quran",0).edit().clear().commit()
        QuranStore.addPages(app,3,today)
        assertTrue(WorshipStore.entries(app,today).isEmpty())
        assertNull(QuranStore.undoLast(app,today))
    }

    @Test fun deletingCompanionRecordsLeavesOtherDataAlone() {
        QuranStore.addPages(app,1,today)
        app.getSharedPreferences("adhkar",0).edit().putString("keep","yes").commit()
        WorshipStore.clear(app)
        assertTrue(WorshipStore.entries(app,today).isEmpty())
        assertEquals("yes",app.getSharedPreferences("adhkar",0).getString("keep",null))
        assertTrue(QuranStore.today(app,today)>0)
    }

    @Test fun catalogTextsExistForEveryRule() {
        val ids=WorshipCatalog.rules.map {"virtue_"+it.id.replace('.','_')}
        ids.forEach {assertNotEquals(it,0,app.resources.getIdentifier(it,"string",app.packageName))}
    }

    @Test fun dayWidgetRendersWithThePrayerBlockIntact() {
        val p=PreferencesStore(app).read()
        val views=PrayerWidget.views(app,p,java.time.Instant.now(),360,240)
        assertEquals(R.layout.prayer_widget_large,views.layoutId)
    }
}
