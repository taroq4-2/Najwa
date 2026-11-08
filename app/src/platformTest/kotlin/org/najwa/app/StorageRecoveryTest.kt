package org.najwa.app

import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.najwa.app.platform.*
import org.najwa.core.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=android.app.Application::class,shadows=[WindowsAtomicFile::class])
class StorageRecoveryTest {
    @get:Rule val folder=TemporaryFolder()
    private fun context()=object: ContextWrapper(RuntimeEnvironment.getApplication()) {override fun getFilesDir()=folder.root}
    @Test fun displaySnapshotTracksWritesAndDurableReads() {
        val c=context(); val journal=AndroidJournal(c)
        val first=SessionJournal(handled=listOf("first"))
        journal.write(first); assertEquals(first,journal.snapshot())
        val second=SessionJournal(handled=listOf("second"))
        AndroidJournal(c).write(second)
        assertEquals(first,journal.snapshot()) // Display does not reopen the file each tick.
        assertEquals(second,journal.read()) // Durable recovery still reads storage.
        assertEquals(second,journal.snapshot())
        assertEquals(second,AndroidJournal(c).snapshot()) // Cold launch restores storage.
    }
    @Test fun backupOnlyRecoversSettingsAfterInterruptedWrite() {
        val c=context();val expected=Preferences(autoSilent=true,widgetTheme=WidgetTheme.LIGHT,widgetOpacity=80)
        PreferencesStore(c).write(expected)
        assertTrue(File(folder.root,"preferences.json").renameTo(File(folder.root,"preferences.json.bak")))
        assertEquals(expected,PreferencesStore(c).read())
    }
    @Test fun widgetPreferencesMigrateAndRoundTrip() {
        val c=context();val store=PreferencesStore(c);store.write(Preferences())
        val json=JsonFile(c,"preferences.json").read()!!;json.remove("widgetTheme");json.remove("widgetOpacity");JsonFile(c,"preferences.json").write(json)
        assertEquals(WidgetTheme.AUTO,store.read().widgetTheme);assertEquals(85,store.read().widgetOpacity)
        val updated=store.read().copy(widgetTheme=WidgetTheme.DARK,widgetOpacity=80)
        store.write(updated);assertEquals(updated,PreferencesStore(c).read())
    }
}
