package org.najwa.app

import android.content.ContextWrapper
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.najwa.app.platform.*
import org.najwa.core.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=android.app.Application::class,shadows=[WindowsAtomicFile::class])
class PreferencesMigrationTest {
    @get:Rule val folder=TemporaryFolder()
    private fun context()=object: ContextWrapper(RuntimeEnvironment.getApplication()) {override fun getFilesDir()=folder.root}
    @Test fun legacyDefaultsUpgradeWithoutLosingCityOrEnabledFlags() {
        val context=context();val store=PreferencesStore(context)
        val old=Preferences(city=Cities.all.last(),autoSilent=true,adhanNotifications=true,iqamaMinutes=defaultIqamaMinutes().mapValues {15})
        store.write(old)
        val file=JsonFile(context,"preferences.json");val json=file.read()!!
        json.remove("schema");json.remove("silenceMinutes");json.remove("showIqamaCountdown")
        json.remove("fridayReminder");json.remove("fridayLeadMinutes");file.write(json)
        val migrated=PreferencesStore(context).read()
        assertEquals(defaultIqamaMinutes(),migrated.iqamaMinutes)
        assertEquals(defaultSilenceMinutes(),migrated.silenceMinutes)
        assertEquals(old.city,migrated.city);assertTrue(migrated.autoSilent);assertTrue(migrated.adhanNotifications)
        assertFalse(migrated.fridayReminder)
        assertEquals(DEFAULT_FRIDAY_LEAD_MINUTES,migrated.fridayLeadMinutes)
    }
    @Test fun customPerPrayerDurationsSurviveColdStartAndRepeatedSaves() {
        val context=context()
        val expected=Preferences(iqamaMinutes=defaultIqamaMinutes()+(Prayer.MAGHRIB to 9),silenceMinutes=defaultSilenceMinutes()+(Prayer.FAJR to 42),showIqamaCountdown=false)
        PreferencesStore(context).write(expected)
        assertEquals(expected,PreferencesStore(context).read())
        PreferencesStore(context).write(PreferencesStore(context).read())
        assertEquals(expected,PreferencesStore(context).read())
    }
    @Test fun oldCustomizedIqamaIsPreserved() {
        val context=context();val expected=Preferences(iqamaMinutes=defaultIqamaMinutes()+(Prayer.FAJR to 25))
        PreferencesStore(context).write(expected)
        val file=JsonFile(context,"preferences.json");val json=file.read()!!;json.remove("schema");file.write(json)
        assertEquals(expected.iqamaMinutes,PreferencesStore(context).read().iqamaMinutes)
    }
}
