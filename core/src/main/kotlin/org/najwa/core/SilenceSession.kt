package org.najwa.core

import java.util.UUID

enum class Ringer { SILENT, VIBRATE, NORMAL }
enum class SessionState { IDLE, PREPARED, ACTIVE, RESTORED, USER_OVERRIDE, RESTORE_FAILED, INTERRUPTED, PERMISSION_REVOKED }
data class Session(val id: String, val eventId: String, val original: Ringer, val applied: Ringer, val startedAt: Long, val deadline: Long, val state: SessionState, val detail: String = "")
data class SessionJournal(val session: Session? = null, val handled: List<String> = emptyList())
interface JournalStore { fun read(): SessionJournal; fun write(value: SessionJournal) }
interface RingerPort { fun capable(): Boolean; fun current(): Ringer; fun set(mode: Ringer) }
interface RestoreAlarm { fun schedule(id: String, deadline: Long); fun cancel() }

/** All ringer writes are owned here. Callers serialize access to one instance. */
class SilenceSessionManager(private val store: JournalStore, private val audio: RingerPort, private val alarm: RestoreAlarm, private val now: ()->Long) {
    private fun update(journal: SessionJournal, session: Session) { store.write(journal.copy(session=session)) }
    @Synchronized fun start(eventId: String, scheduledAt: Long, durationMinutes: Int, mode: Ringer): Session? {
        require(durationMinutes in 1..120 && mode != Ringer.NORMAL)
        var journal = store.read()
        val time = now()
        val deadline=scheduledAt+durationMinutes*60_000L
        if(eventId in journal.handled || time < scheduledAt || time >= deadline) return journal.session
        if(!audio.capable()) return journal.session
        var old = journal.session
        if(old?.state in listOf(SessionState.PREPARED,SessionState.RESTORE_FAILED,SessionState.PERMISSION_REVOKED,SessionState.INTERRUPTED)) return old
        if(old?.state == SessionState.ACTIVE && old.deadline <= time) { restore(old.id); journal=store.read(); old=journal.session }
        if(old?.state in listOf(SessionState.RESTORE_FAILED,SessionState.PERMISSION_REVOKED,SessionState.INTERRUPTED)) return old
        val current=audio.current()
        if(old?.state == SessionState.ACTIVE && current != old.applied) {
            val changed=old.copy(state=SessionState.USER_OVERRIDE,detail="external_mode_change")
            update(journal,changed); alarm.cancel(); return changed
        }
        // Changing preference during an active lease applies to the next session only.
        if(old?.state == SessionState.ACTIVE) {
            val extended=old.copy(deadline=maxOf(old.deadline,deadline))
            alarm.schedule(extended.id,extended.deadline)
            try { store.write(journal.copy(session=extended,handled=(journal.handled+eventId).takeLast(64))) }
            catch(e: Exception) {
                // A failed extension commit must not leave the old record with a later alarm.
                try { alarm.schedule(old.id,old.deadline) } catch(_: Exception) { restore(old.id,true) }
                throw e
            }
            return extended
        }
        if(current == mode) { store.write(journal.copy(handled=(journal.handled+eventId).takeLast(64))); return old }
        val prepared=Session(UUID.randomUUID().toString(),eventId,current,mode,time,deadline,SessionState.PREPARED)
        journal=journal.copy(session=prepared,handled=(journal.handled+eventId).takeLast(64))
        store.write(journal) // Must succeed before any audio write.
        try {
            alarm.schedule(prepared.id,prepared.deadline) // Restoration must be scheduled before muting.
            if(!audio.capable()) { update(journal,prepared.copy(state=SessionState.PERMISSION_REVOKED)); return store.read().session }
            audio.set(mode)
            val result=prepared.copy(state=if(audio.current()==mode) SessionState.ACTIVE else SessionState.INTERRUPTED,detail=if(audio.current()==mode) "" else "mode_not_applied")
            update(journal,result)
            return result
        } catch(e: Exception) {
            // PREPARED remains recoverable if writing this outcome also fails.
            val result=prepared.copy(state=SessionState.INTERRUPTED,detail=e.javaClass.simpleName)
            update(journal,result)
            return result
        }
    }
    @Synchronized fun restore(id: String, manual: Boolean = false): Session? {
        val journal=store.read(); val s=journal.session ?: return null
        if(s.id != id || s.state in listOf(SessionState.RESTORED,SessionState.USER_OVERRIDE,SessionState.IDLE)) return s
        if(!manual && s.state != SessionState.ACTIVE) return s
        if(!manual && now() < s.deadline) { alarm.schedule(s.id,s.deadline); return s }
        val result=try {
            when {
                audio.current()!=s.applied -> s.copy(state=SessionState.USER_OVERRIDE,detail="external_mode_change")
                !audio.capable() -> s.copy(state=SessionState.PERMISSION_REVOKED)
                else -> { audio.set(s.original); s.copy(state=if(audio.current()==s.original) SessionState.RESTORED else SessionState.RESTORE_FAILED) }
            }
        } catch(e: Exception) { s.copy(state=SessionState.RESTORE_FAILED,detail=e.javaClass.simpleName) }
        update(journal,result)
        if(result.state in listOf(SessionState.RESTORED,SessionState.USER_OVERRIDE)) alarm.cancel()
        return result
    }
    @Synchronized fun reconcile(): Session? {
        val j=store.read(); val s=j.session ?: return null
        if(s.state == SessionState.PREPARED || s.state == SessionState.INTERRUPTED) {
            // A crash around the ringer write leaves ambiguous ownership only if
            // the requested mode is still observed. Never write audio here.
            if(audio.current()!=s.applied) {
                val r=s.copy(state=SessionState.USER_OVERRIDE,detail="interrupted_mode_released")
                update(j,r)
                alarm.cancel()
                return r
            }
            if(s.state==SessionState.PREPARED) {
                val r=s.copy(state=SessionState.INTERRUPTED,detail="prepared_after_restart")
                update(j,r)
                return r
            }
            return s
        }
        if(s.state == SessionState.PERMISSION_REVOKED || s.state == SessionState.RESTORE_FAILED) {
            // Once permission returns, end the suspended lease rather than keeping
            // the phone muted indefinitely. restore checks ownership before writing
            // and also retires the lease without a write if the user changed mode.
            if(audio.current()!=s.applied || audio.capable()) return restore(s.id,manual=true)
            return s
        }
        if(s.state != SessionState.ACTIVE) return s
        // A wall-clock rollback makes the recorded deadline unreliable. Safely end
        // the owned lease now; INTERRUPTED would make its restore alarm a no-op.
        if(now() < s.startedAt) return restore(s.id,manual=true)
        if(audio.current()!=s.applied) { val r=s.copy(state=SessionState.USER_OVERRIDE); update(j,r); alarm.cancel(); return r }
        if(!audio.capable()) { val r=s.copy(state=SessionState.PERMISSION_REVOKED); update(j,r); return r }
        if(now() >= s.deadline) return restore(s.id)
        try { alarm.schedule(s.id,s.deadline) } catch(_: Exception) {
            // If we still own the sound but cannot ensure a future restoration, end the lease now.
            return restore(s.id,true)
        }
        return s
    }
    @Synchronized fun disable(): Session? { val s=store.read().session ?: return null; return restore(s.id,manual=true) }
}
