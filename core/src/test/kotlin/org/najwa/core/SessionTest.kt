package org.najwa.core

import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    @Test fun rejectedMuteDoesNotBlockNextPrayerAfterReconcile() {
        val f=Fixture(); val s=f.start()
        f.mode=Ringer.NORMAL
        f.state=f.state.copy(session=s.copy(state=SessionState.INTERRUPTED,detail="mode_not_applied"))
        val writes=f.writes
        f.manager.reconcile()
        assertEquals(writes,f.writes)
        assertEquals(Ringer.NORMAL,f.mode)
        assertEquals(SessionState.USER_OVERRIDE,f.state.session!!.state)
        assertEquals(SessionState.ACTIVE,f.start("next-prayer").state)
        assertEquals(Ringer.SILENT,f.mode)
    }
    @Test fun rejectedMuteWithLaterSilentModeRemainsManualRecovery() {
        val f=Fixture(); val s=f.start()
        f.state=f.state.copy(session=s.copy(state=SessionState.INTERRUPTED,detail="mode_not_applied"))
        f.manager.reconcile()
        assertEquals(SessionState.INTERRUPTED,f.state.session!!.state)
        assertEquals(Ringer.SILENT,f.mode)
        assertEquals(1,f.writes)
    }
    class Fixture {
        var now=1_000_000L;var state=SessionJournal();var mode=Ringer.NORMAL;var capable=true;var writes=0;var scheduleFails=false;var diskFails=false;var audioFails=false;var scheduled=0L
        val store=object: JournalStore { override fun read()=state;override fun write(value: SessionJournal) {check(!diskFails);state=value} }
        val audio=object: RingerPort {override fun capable()=capable;override fun current()=mode;override fun set(value: Ringer){check(state.session!=null);check(!audioFails);mode=value;writes++} }
        val alarm=object: RestoreAlarm {override fun schedule(id: String,deadline: Long) {check(!scheduleFails);scheduled=deadline};override fun cancel(){scheduled=0} }
        val manager=SilenceSessionManager(store,audio,alarm,{now})
        fun start(id: String="a")=manager.start(id,now,15,Ringer.SILENT)!!
    }
    @Test fun persistsBeforeWriteAndRestoresOriginal() {val f=Fixture();val s=f.start();assertEquals(SessionState.ACTIVE,s.state);assertEquals(Ringer.NORMAL,s.original);assertEquals(Ringer.SILENT,f.mode);f.now=s.deadline;f.manager.restore(s.id);assertEquals(Ringer.NORMAL,f.mode);assertEquals(SessionState.RESTORED,f.state.session!!.state)}
    @Test fun preservesVibrateOriginal() {val f=Fixture();f.mode=Ringer.VIBRATE;val s=f.start();f.manager.restore(s.id,true);assertEquals(Ringer.VIBRATE,f.mode)}
    @Test fun respectsExternalOverride() {val f=Fixture();val s=f.start();f.mode=Ringer.VIBRATE;f.now=s.deadline;f.manager.restore(s.id);assertEquals(Ringer.VIBRATE,f.mode);assertEquals(SessionState.USER_OVERRIDE,f.state.session!!.state)}
    @Test fun duplicateCannotExtendLease() {val f=Fixture();val s=f.start();f.now+=1000;assertEquals(s,f.start());assertEquals(1,f.writes)}
    @Test fun extensionPreservesOriginal() {val f=Fixture();val s=f.start();f.now+=60_000;val second=f.start("b");assertEquals(s.id,second.id);assertEquals(s.original,second.original);assertEquals(s.deadline+60_000,second.deadline);assertEquals(1,f.writes)}
    @Test fun staleRestoreCannotTouchNewSession() {val f=Fixture();val s=f.start();f.manager.restore(s.id,true);val next=f.start("b");f.manager.restore(s.id,true);assertEquals(next,f.state.session);assertEquals(Ringer.SILENT,f.mode)}
    @Test fun missingPermissionDoesNotWrite() {val f=Fixture();f.capable=false;assertNull(f.manager.start("a",f.now,15,Ringer.SILENT));assertEquals(0,f.writes)}
    @Test fun expiredAndEarlyAlarmsIgnored() {val f=Fixture();assertNull(f.manager.start("a",f.now-900000,15,Ringer.SILENT));assertNull(f.manager.start("b",f.now+1,15,Ringer.SILENT));assertEquals(0,f.writes)}
    @Test fun delayedPrayerStillMutesButKeepsOriginalDeadline() {val f=Fixture();val at=f.now;f.now+=180000;val s=f.manager.start("a",at,15,Ringer.SILENT)!!;assertEquals(SessionState.ACTIVE,s.state);assertEquals(at+900000,s.deadline);f.now=s.deadline;f.manager.restore(s.id);assertEquals(Ringer.NORMAL,f.mode)}
    @Test fun lateDuplicateAfterManualRestoreNeverRemutes() {val f=Fixture();val at=f.now;val s=f.start();f.manager.restore(s.id,true);f.now+=180000;f.manager.start("a",at,15,Ringer.SILENT);assertEquals(Ringer.NORMAL,f.mode);assertEquals(SessionState.RESTORED,f.state.session!!.state)}
    @Test fun delayedExtensionDoesNotExtendPastPrayerDeadline() {val f=Fixture();val s=f.start();val secondAt=f.now+60000;f.now+=180000;val next=f.manager.start("b",secondAt,15,Ringer.SILENT)!!;assertEquals(s.id,next.id);assertEquals(secondAt+900000,next.deadline)}
    @Test fun alreadySilentNotClaimed() {val f=Fixture();f.mode=Ringer.SILENT;assertNull(f.manager.start("a",f.now,15,Ringer.SILENT));assertEquals(0,f.writes);assertTrue("a" in f.state.handled)}
    @Test fun failedAlarmNeverMutes() {val f=Fixture();f.scheduleFails=true;assertEquals(SessionState.INTERRUPTED,f.start().state);assertEquals(0,f.writes)}
    @Test fun failedPersistenceNeverMutes() {val f=Fixture();f.diskFails=true;assertThrows(IllegalStateException::class.java) {f.start()};assertEquals(0,f.writes)}
    @Test fun preparedRecoveryNeverGuesses() {val f=Fixture();val s=f.start();f.state=f.state.copy(session=s.copy(state=SessionState.PREPARED));f.now=s.deadline;f.manager.reconcile();assertEquals(SessionState.INTERRUPTED,f.state.session!!.state);assertEquals(1,f.writes)}
    @Test fun activeRebootAfterDeadlineRestoresIfStillOwned() {val f=Fixture();val s=f.start();f.now=s.deadline+100;val recreated=SilenceSessionManager(f.store,f.audio,f.alarm,{f.now});recreated.reconcile();assertEquals(Ringer.NORMAL,f.mode)}
    @Test fun revokedPolicyKeepsRecoveryRecord() {val f=Fixture();val s=f.start();f.capable=false;f.now=s.deadline;f.manager.restore(s.id);assertEquals(SessionState.PERMISSION_REVOKED,f.state.session!!.state);assertEquals(1,f.writes)}
    @Test fun manualDisableSafelyRestores() {val f=Fixture();f.start();f.manager.disable();assertEquals(Ringer.NORMAL,f.mode);assertEquals(0,f.scheduled)}
    @Test fun earlyRestoreReschedulesInsteadOfChangingSound() {val f=Fixture();val s=f.start();f.manager.restore(s.id);assertEquals(Ringer.SILENT,f.mode);assertEquals(s.deadline,f.scheduled)}
    @Test fun clockRollbackEndsOwnedLeaseInsteadOfLeavingPhoneMuted() {val f=Fixture();val s=f.start();f.now=s.startedAt-1;f.manager.reconcile();assertEquals(SessionState.RESTORED,f.state.session!!.state);assertEquals(Ringer.NORMAL,f.mode);assertEquals(0,f.scheduled);assertEquals(2,f.writes)}
    @Test fun clockRollbackPreservesExternalModeChange() {val f=Fixture();val s=f.start();f.mode=Ringer.VIBRATE;f.now=s.startedAt-1;f.manager.reconcile();assertEquals(SessionState.USER_OVERRIDE,f.state.session!!.state);assertEquals(Ringer.VIBRATE,f.mode);assertEquals(1,f.writes);assertEquals(0,f.scheduled)}
    @Test fun clockRollbackWithoutPermissionKeepsRecoverableLease() {val f=Fixture();val s=f.start();f.capable=false;f.now=s.startedAt-1;f.manager.reconcile();assertEquals(SessionState.PERMISSION_REVOKED,f.state.session!!.state);assertEquals(1,f.writes);f.capable=true;f.manager.reconcile();assertEquals(SessionState.RESTORED,f.state.session!!.state);assertEquals(Ringer.NORMAL,f.mode)}
    @Test fun restoredPermissionEndsSuspendedLeaseAndDuplicatePrayerCannotRemute() {val f=Fixture();val s=f.start();f.capable=false;f.manager.reconcile();f.now+=1000;f.capable=true;f.manager.reconcile();assertEquals(SessionState.RESTORED,f.state.session!!.state);assertEquals(Ringer.NORMAL,f.mode);assertEquals(0,f.scheduled);f.manager.start("a",s.startedAt,15,Ringer.SILENT);assertEquals(Ringer.NORMAL,f.mode);assertEquals(2,f.writes)}
    @Test fun restoredPermissionAfterDeadlineRestoresOriginalVibration() {val f=Fixture();f.mode=Ringer.VIBRATE;val s=f.start();f.capable=false;f.manager.reconcile();f.now=s.deadline+100;f.capable=true;f.manager.reconcile();assertEquals(SessionState.RESTORED,f.state.session!!.state);assertEquals(Ringer.VIBRATE,f.mode);assertEquals(0,f.scheduled)}
    @Test fun revokedPermissionStillHonorsManualModeChange() {val f=Fixture();f.start();f.capable=false;f.manager.reconcile();f.mode=Ringer.VIBRATE;f.manager.reconcile();assertEquals(SessionState.USER_OVERRIDE,f.state.session!!.state);assertEquals(Ringer.VIBRATE,f.mode);assertEquals(1,f.writes);assertEquals(0,f.scheduled)}
    @Test fun restoreFailureRemainsVisibleAndRetryable() {val f=Fixture();val s=f.start();f.audioFails=true;f.manager.restore(s.id,true);assertEquals(SessionState.RESTORE_FAILED,f.state.session!!.state);f.audioFails=false;f.manager.restore(s.id,true);assertEquals(SessionState.RESTORED,f.state.session!!.state)}
    @Test fun modePreferenceDoesNotOverwriteOriginalDuringExtension() {val f=Fixture();val s=f.start();f.manager.start("b",f.now,20,Ringer.VIBRATE);assertEquals(s.original,f.state.session!!.original);assertEquals(Ringer.SILENT,f.mode)}
    @Test fun failedExtensionCommitRestoresOriginalAlarmDeadline() {val f=Fixture();val s=f.start();f.now+=1000;f.diskFails=true;assertThrows(IllegalStateException::class.java) {f.start("b")};assertEquals(s.deadline,f.scheduled);assertEquals(s,f.state.session)}
    @Test fun lostExactAlarmAccessEndsOwnedLeaseImmediately() {val f=Fixture();f.start();f.scheduleFails=true;f.manager.reconcile();assertEquals(Ringer.NORMAL,f.mode);assertEquals(SessionState.RESTORED,f.state.session!!.state)}
    @Test fun policyRevocationDuringActiveSessionIsVisibleBeforeDeadline() {val f=Fixture();f.start();f.capable=false;f.manager.reconcile();assertEquals(SessionState.PERMISSION_REVOKED,f.state.session!!.state);assertEquals(1,f.writes)}
}
