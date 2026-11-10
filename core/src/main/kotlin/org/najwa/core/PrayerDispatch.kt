package org.najwa.core

import java.time.Instant

/** Shared by real scheduled broadcasts and the end-to-end scheduled test. */
class PrayerDispatch(private val sessions: SilenceSessionManager,private val notify: (ScheduledPrayer,Preferences)->Unit) {
    fun deliver(tasks: List<ScheduledPrayer>,p: Preferences,onFailure: (ScheduledPrayer,Exception)->Unit = {_,e->throw e}) {
        tasks.sortedBy {if(it.action==PrayerAction.SILENCE)1 else 0}.forEach {task->
            try {
                if(task.action==PrayerAction.SILENCE)sessions.start(task.event.id,task.event.time.toEpochMilli(),p.silenceMinutes.getValue(task.event.prayer),p.mode)
                else notify(task,p)
            } catch(e: Exception) {onFailure(task,e)}
        }
    }
}

/** Recover an interrupted plan and validate the incoming timestamp independently
 * of which future alarm was last registered. Stale broadcasts cannot run new settings. */
fun dispatchablePrayers(saved: List<ScheduledPrayer>,current: List<ScheduledPrayer>,now: Instant,p: Preferences,incomingAt: Long?=null): List<ScheduledPrayer> {
    val candidates=if(saved.isEmpty() && incomingAt!=null)current.filter {it.at.toEpochMilli()==incomingAt} else saved
    return duePrayers(candidates,current,now) {p.silenceMinutes.getValue(it)}
}
