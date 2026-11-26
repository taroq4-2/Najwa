package org.najwa.app

import org.json.JSONObject
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

/** Phase one: one backup for everything, counts written one way, and the day engine in the summary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class ConnectedDayPlatformTest {
    private lateinit var app: NajwaApplication
    private val today=LocalDate.now()
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        WorshipStore.clear(app)
        WorshipSettingsStore.write(app,WorshipSettings())
        WorshipBackup.PREFERENCES.forEach {app.getSharedPreferences(it,0).edit().clear().commit()}
    }

    @Test fun backupCarriesTheKhatmaRecordsAndSettings() {
        QuranStore.setGoal(app,21)
        QuranStore.readPage(app,1,today)
        QuranStore.readPage(app,2,today)
        AdhkarProgressStore.saveRemaining(app,"morning",Adhkar.morning.map {0},today)
        app.getSharedPreferences("reading",0).edit().putString("mode","NIGHT").commit()
        app.runtime.update(app.runtime.preferences.copy(wirdReminder=true,digits=Digits.LATIN))
        val text=app.runtime.exportSettings()
        assertTrue(JSONObject(text).has("data"))

        // A new phone: everything gone, then restored from the file.
        WorshipBackup.PREFERENCES.forEach {app.getSharedPreferences(it,0).edit().clear().commit()}
        WorshipStore.clear(app)
        app.runtime.update(app.runtime.preferences.copy(wirdReminder=false,digits=Digits.AUTO))
        assertEquals(0,QuranStore.read(app).page)

        assertTrue(app.runtime.importSettings(text))
        assertEquals(2,QuranStore.read(app).page)
        assertEquals(21,QuranStore.read(app).goal)
        assertEquals(setOf(1,2),QuranStore.readToday(app,today))
        assertTrue(AdhkarProgressStore.remaining(app,"morning",Adhkar.morning.size,today)!!.all {it==0})
        assertEquals(org.najwa.app.ui.ReadingMode.NIGHT.name,app.getSharedPreferences("reading",0).getString("mode",null))
        assertTrue(app.runtime.preferences.wirdReminder)
        assertEquals(Digits.LATIN,app.runtime.preferences.digits)
    }

    @Test fun aSettingsOnlyBackupStillRestores() {
        val old=JSONObject(app.runtime.exportSettings()).apply {remove("data")}.toString()
        QuranStore.setGoal(app,30)
        assertTrue(app.runtime.importSettings(old))
        // Worship data is left as it is when the file has none.
        assertEquals(30,QuranStore.read(app).goal)
    }

    @Test fun aDamagedDataPartChangesNothing() {
        QuranStore.setGoal(app,15)
        val bad=JSONObject(app.runtime.exportSettings()).apply {
            put("data",JSONObject().put("version",1).put("preferences",JSONObject().put("quran",JSONObject().put("goal",JSONObject().put("t","x")))))
        }.toString()
        assertFalse(app.runtime.importSettings(bad))
        assertEquals(15,QuranStore.read(app).goal)
    }

    @Test fun arabicCountsAgreeWithTheNoun() {
        assertEquals("صفحة واحدة",Counts.arabicNoun(1,"١","صفحة واحدة","صفحتان","صفحات","صفحة"))
        assertEquals("صفحتان",Counts.arabicNoun(2,"٢","صفحة واحدة","صفحتان","صفحات","صفحة"))
        assertEquals("٣ صفحات",Counts.arabicNoun(3,"٣","صفحة واحدة","صفحتان","صفحات","صفحة"))
        assertEquals("٢١ صفحة",Counts.arabicNoun(21,"٢١","صفحة واحدة","صفحتان","صفحات","صفحة"))
        assertEquals("١٠٣ صفحات",Counts.arabicNoun(103,"١٠٣","صفحة واحدة","صفحتان","صفحات","صفحة"))
        assertEquals("1,234",Counts.format(false,1234))
        assertEquals("١٬٢٣٤",Counts.format(true,1234))
    }

    @Test fun summaryCarriesTheNextStep() {
        QuranStore.setGoal(app,4)
        val s=WorshipSummary.build(app,WorshipSettingsStore.read(app),today)
        assertEquals(4,s.wirdLeft)
        // Whatever the hour, the step is one of the open items and never "done" while the portion remains.
        assertNotEquals(StepKind.DONE,s.next.kind)
        assertTrue(s.adhkarNow==StepKind.MORNING_ADHKAR || s.adhkarNow==StepKind.EVENING_ADHKAR)
    }

    @Test fun kahfPageMatchesTheMushafIndex() {
        assertEquals(Mushaf.load(app).surahs[17].page,KAHF_PAGE)
    }
}
