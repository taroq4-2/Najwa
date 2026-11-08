package org.najwa.app

import android.Manifest
import android.app.NotificationManager
import android.media.RingtoneManager
import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.najwa.core.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class,WindowsFileProviderPaths::class],instrumentedPackages=["androidx.core.content"])
class AdhanSoundTest {
    private lateinit var app: NajwaApplication
    private lateinit var manager: NotificationManager
    private lateinit var notices: PrayerNotifications
    private val bytes="RIFF-test-audio".toByteArray()
    private fun sounds()=AdhanSounds(app) {file->require(file.readBytes().contentEquals(bytes)) {"invalid audio"}}
    private fun source(name: String="picked.wav",data: ByteArray=bytes): Pair<File,android.net.Uri> {
        val file=File(app.filesDir,"adhan/$name").apply {parentFile!!.mkdirs();writeBytes(data)}
        return file to FileProvider.getUriForFile(app,"${app.packageName}.adhan.files",file)
    }
    @Before fun prepare() {
        // Robolectric reuses AndroidX statics but allocates a new filesDir per test.
        FileProvider::class.java.getDeclaredField("sCache").apply {isAccessible=true}.let {(it.get(null) as MutableMap<*,*>).clear()}
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        manager=app.getSystemService(NotificationManager::class.java)
        notices=app.runtime.notifications
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
    }
    @Test fun importsCopiesPersistsAndPostsOnSelectedChannelAfterSourceIsDeleted() {
        val (original,uri)=source()
        val selected=sounds().importAudio(uri)
        assertEquals("picked.wav",selected.name)
        assertNotEquals(PrayerNotifications.ADHAN,selected.channelId)
        assertEquals(selected.uri,manager.getNotificationChannel(selected.channelId).sound.toString())
        assertTrue(original.delete())
        assertEquals(selected,sounds().selected())
        app.contentResolver.openInputStream(android.net.Uri.parse(selected.uri))!!.use {assertArrayEquals(bytes,it.readBytes())}
        val now=Instant.now()
        notices.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now),Preferences(),true)
        assertEquals(selected.channelId,shadowOf(manager).getNotification(499).channelId)
        notices.cancelTest();assertNull(shadowOf(manager).getNotification(499))
        assertNull(manager.getNotificationChannel(PrayerNotifications.IQAMA).sound)
    }
    @Test fun invalidEmptyOrNonAudioFilesLeavePreviousChoiceAndCleanStagingFiles() {
        val selected=sounds().importAudio(source().second)
        val invalid=source("bad.wav","not audio".toByteArray())
        val empty=source("empty.wav",byteArrayOf())
        val text=source("text.txt")
        val before=File(app.filesDir,"adhan").list()!!.toSet()
        for(uri in listOf(invalid.second,empty.second,text.second)) {
            assertThrows(Exception::class.java) {sounds().importAudio(uri)}
            assertEquals(selected,sounds().selected())
        }
        assertEquals(before,File(app.filesDir,"adhan").list()!!.toSet())
    }
    @Test fun oversizedFileIsRejectedWithoutReplacingCurrentSound() {
        val selected=sounds().importAudio(source().second)
        val file=File(app.filesDir,"adhan/large.wav")
        file.outputStream().use {output->val block=ByteArray(1024*1024);repeat(33){output.write(block)}}
        val uri=FileProvider.getUriForFile(app,"${app.packageName}.adhan.files",file)
        assertThrows(IllegalArgumentException::class.java) {sounds().importAudio(uri)}
        assertEquals(selected,sounds().selected())
    }
    @Test fun blockedChannelCannotBeBypassedByChoosingAnotherFile() {
        val old=manager.getNotificationChannel(PrayerNotifications.ADHAN)
        old.importance=NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(old)
        assertThrows(IllegalStateException::class.java) {sounds().importAudio(source().second)}
        assertEquals(PrayerNotifications.ADHAN,sounds().selected().channelId)
        assertFalse(notices.enabled(PrayerNotifications.ADHAN))
    }
    @Test fun resetRestoresDefaultAndRemovesOnlyPreviousImportedFileAndChannel() {
        val (original,uri)=source()
        val custom=sounds().importAudio(uri)
        val reset=sounds().reset()
        assertNull(reset.fileName)
        assertTrue(original.exists())
        assertFalse(File(app.filesDir,"adhan/${custom.fileName}").exists())
        assertNull(manager.getNotificationChannel(custom.channelId))
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),manager.getNotificationChannel(reset.channelId).sound)
        assertNull(manager.getNotificationChannel(PrayerNotifications.IQAMA).sound)
    }
    @Test fun corruptedSoundSelectionDoesNotCrashOrSilentlyEnableDefaultSound() {
        val file=File(app.filesDir,"adhan-sound.json")
        for(invalid in listOf("{broken","{}","{\"channel\":\"foreign_channel\",\"name\":\"test\"}")) {
            file.writeText(invalid)
            val selection=sounds().selected()
            assertNotNull(selection.error)
            assertEquals(AdhanSounds.UNAVAILABLE_CHANNEL,selection.channelId)
            assertFalse(notices.enabled(PrayerNotifications.ADHAN))
            val now=Instant.now()
            notices.show(ScheduledPrayer(PrayerEvent(Prayer.DHUHR,now),PrayerAction.ADHAN,now),Preferences(),true)
            assertNull(shadowOf(manager).getNotification(499))
        }
        // The file remains unchanged until an explicit selection/reset.
        assertTrue(file.readText().contains("foreign_channel"))
    }
    @Test fun explicitResetRecoversCorruptedSelectionWithoutChangingIqama() {
        File(app.filesDir,"adhan-sound.json").writeText("{broken")
        val recovered=sounds().reset()
        assertNull(recovered.error)
        assertEquals(recovered,sounds().selected())
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),manager.getNotificationChannel(recovered.channelId).sound)
        assertNull(manager.getNotificationChannel(PrayerNotifications.IQAMA).sound)
    }
}
