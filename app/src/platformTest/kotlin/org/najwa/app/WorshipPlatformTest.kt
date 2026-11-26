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

    @Test fun pageLetterTableIsCompleteAndMatchesKnownPages() {
        val t=QuranPageLetters.table(app)!!
        assertEquals(QuranStore.PAGES,t.size)
        // Page 1 is Al-Fatiha with its basmalah: 139 letters. Page 604 is Al-Ikhlas 47 + Al-Falaq 71 + An-Nas 80.
        // The total follows the King Fahd Complex's Hafs text (its own orthography).
        assertEquals(139,t[0])
        assertEquals(47+71+80,t[603])
        assertEquals(325_883L,t.sumOf {it.toLong()})
        // Ranges wrap after the last page.
        assertEquals((t[603]+t[0]).toLong(),QuranPageLetters.letters(app,604,2))
    }

    @Test fun confirmedReadingBecomesLettersInTheSummary() {
        app.getSharedPreferences("quran",0).edit().clear().commit()
        QuranStore.addPages(app,1,today)
        val s=WorshipSummary.build(app,WorshipSettings(),today)
        assertEquals(139L,s.letters)
        assertNull("no approved rule, so no hasanat",s.virtues.hasanat)
        val preview=WorshipEngine.evaluate(listOf(WorshipRecord("q",WorshipActions.QURAN_LETTER,139,TimeWindow.ANY_TIME,today)),WorshipCatalog.rules,RulePolicy.REVIEW)
        assertEquals(1390L,preview.hasanat)
    }

    @Test fun mushafPagesSurahsAndJuzLineUp() {
        val d=Mushaf.load(app)
        assertEquals(6236,d.verses.size)
        assertEquals(114,d.surahs.size)
        assertEquals((1..7).map {1 to it},d.page(1).map {it.sura to it.aya})
        assertEquals(setOf(112,113,114),d.page(604).map {it.sura}.toSet())
        assertEquals(293,d.surahs[17].page)
        assertEquals(22,d.pageOfJuz(2))
        assertEquals(2,d.juzOf(22))
        // Every verse is on exactly one page.
        assertEquals(6236,(1..Mushaf.PAGES).sumOf {d.page(it).size})
        // The basmalah heads a surah instead of opening its first verse; Al-Fatiha keeps it as verse 1.
        val kahf=d.verses[d.surahs[17].start]
        assertFalse(d.displayText(kahf).contains("بِسْمِ"))
        assertEquals(d.verses[0].text,d.displayText(d.verses[0]))
        assertEquals("١١٠",Mushaf.arabicDigits(110))
    }

    @Test fun confirmingAPageRecordsItAndUndoRestoresThePosition() {
        app.getSharedPreferences("quran",0).edit().clear().commit()
        QuranStore.readPage(app,10,today)
        assertEquals(10,QuranStore.read(app).page)
        // Re-reading an earlier page counts for today without moving you back.
        QuranStore.readPage(app,3,today)
        assertEquals(10,QuranStore.read(app).page)
        assertEquals(2,QuranStore.today(app,today))
        assertEquals(setOf(10,3),QuranStore.readToday(app,today))
        QuranStore.undoLast(app,today)
        assertEquals(10,QuranStore.read(app).page)
        assertEquals(1,QuranStore.today(app,today))
        // Finishing the last page completes a khatma.
        QuranStore.readPage(app,604,today)
        assertEquals(1,QuranStore.read(app).khatmas)
        assertEquals(0,QuranStore.read(app).page)
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
