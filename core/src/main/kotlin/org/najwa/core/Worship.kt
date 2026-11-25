package org.najwa.core

import java.time.LocalDate

// Worship companion: what the person recorded, which approved evidence applies to it, and a day's summary.
// Nothing here touches prayer times, alerts or silence. Totals are always rebuilt from the records,
// so a correction anywhere changes every summary consistently.

/** Review state of a religious rule. Release builds count only APPROVED rules (see [RulePolicy]). */
enum class EvidenceStatus { UNREVIEWED, VERIFIED, APPROVED, DISPUTED, DISABLED }

/** What the evidence mentions. Only HASANAT adds to a number; the others are listed, never converted. */
enum class VirtueKind { HASANAT, EXPIATION, PROTECTION, VIRTUE }

/** One outcome named by the evidence. [quantity] only when the text states a number; for EXPIATION it is shown, never summed. */
data class Outcome(val kind: VirtueKind, val quantity: Long? = null)

/** ALL: the outcomes come together. ANY: the text says "or", so no single number is added for it. */
enum class OutcomeRelation { ALL, ANY }

/** When the recorded action must happen for the rule to apply, and how often it may count in one day. */
enum class TimeWindow(val perDay: Int) {
    ANY_TIME(Int.MAX_VALUE), DAY(1), MORNING(1), EVENING(1), AFTER_PRAYER(5), BEFORE_SLEEP(1)
}

data class Evidence(val collection: String, val reference: String)

/**
 * One rule linking a recorded action to what the evidence mentions.
 * [required] is the repetitions needed in one sitting; 99 of a required 100 never qualifies.
 * [perUnit] rules (Quran letters) multiply by the number of units recorded instead.
 */
data class VirtueRule(
    val id: String,
    val version: Int,
    val action: String,
    val required: Int,
    val window: TimeWindow,
    val outcomes: List<Outcome>,
    val evidence: List<Evidence>,
    val relation: OutcomeRelation = OutcomeRelation.ALL,
    val status: EvidenceStatus = EvidenceStatus.UNREVIEWED,
    val perUnit: Boolean = false,
) {
    init {
        require(required >= 1) {"required must be positive"}
        require(outcomes.isNotEmpty() && evidence.isNotEmpty()) {"a rule needs outcomes and evidence"}
        require(outcomes.all {it.quantity == null || it.quantity > 0}) {"quantities must be positive"}
    }
    val numeric get() = relation == OutcomeRelation.ALL && outcomes.any {it.kind == VirtueKind.HASANAT && it.quantity != null}
    fun has(kind: VirtueKind) = outcomes.any {it.kind == kind}
}

/** Which review states count. Release: approved only. Review preview: everything not disputed or disabled. */
data class RulePolicy(val active: Set<EvidenceStatus>) {
    fun allows(rule: VirtueRule) = rule.status in active
    companion object {
        val RELEASE = RulePolicy(setOf(EvidenceStatus.APPROVED))
        val REVIEW = RulePolicy(setOf(EvidenceStatus.APPROVED, EvidenceStatus.VERIFIED, EvidenceStatus.UNREVIEWED))
    }
}

/** Action keys shared by the recorders and the rules. */
object WorshipActions {
    const val PRAYER = "prayer"
    const val PRAYER_JAMAAH = "prayer.jamaah"
    const val QURAN_LETTER = "quran.letter"
    const val TASBIH = "dhikr.tasbih"                       // سبحان الله
    const val TASBIH_BIHAMDIHI = "dhikr.tasbih_bihamdihi"   // سبحان الله وبحمده
    const val TAHLIL = "dhikr.tahlil"                       // لا إله إلا الله وحده لا شريك له ... قدير
    const val SALAWAT = "dhikr.salawat"
    const val KURSI = "dhikr.kursi"
    const val MUAWWIDHAT = "dhikr.muawwidhat"               // الإخلاص والفلق والناس
    const val BISMILLAH_SHIELD = "dhikr.bismillah_la_yadurr"
    const val SAYYID_ISTIGHFAR = "dhikr.sayyid_istighfar"
    const val KALIMAT = "dhikr.kalimat_tammat"
    const val AFTER_PRAYER_HUNDRED = "routine.after_prayer_tasbih" // 33 + 33 + 33 completed by the tahlil
}

/**
 * One confirmed action: [count] repetitions done in one sitting (or units, for Quran letters).
 * [id] makes recording idempotent: the same id delivered twice is one record; an intentional repeat gets a new id.
 */
data class WorshipRecord(val id: String, val action: String, val count: Long, val window: TimeWindow, val date: LocalDate) {
    init {require(count >= 0) {"count must not be negative"}}
}

/** A rule that applied today, [times] times (units for per-unit rules). */
data class Qualified(val rule: VirtueRule, val times: Long)

data class DayVirtues(
    /** Hoped-for hasanat from active numeric rules; null when no active numeric rule exists (show no number at all). */
    val hasanat: Long?,
    val qualified: List<Qualified>,
) {
    /** Recorded actions linked to an expiation virtue: a count of actions, never of erased sins. */
    val expiationActions get() = qualified.filter {it.rule.has(VirtueKind.EXPIATION) && !it.rule.perUnit}.sumOf {it.times}.toInt()
    val protection get() = qualified.filter {it.rule.has(VirtueKind.PROTECTION)}
}

/** Deterministic evaluation of one day's records against the catalog. */
object WorshipEngine {
    fun evaluate(records: Collection<WorshipRecord>, rules: List<VirtueRule>, policy: RulePolicy): DayVirtues {
        // Idempotency: the same record id counts once however often it was delivered.
        val unique = records.associateBy {it.id}.values
        val active = rules.filter(policy::allows)
        val qualified = active.mapNotNull {rule ->
            val matching = unique.filter {it.action == rule.action && fits(rule.window, it.window)}
            val times = if(rule.perUnit) matching.fold(0L) {sum, r -> saturatingAdd(sum, r.count)}
                else matching.count {it.count >= rule.required}.toLong().coerceAtMost(rule.window.perDay.toLong())
            if(times > 0) Qualified(rule, times) else null
        }
        val hasanat = if(active.none {it.numeric}) null else qualified.filter {it.rule.numeric}.fold(0L) {sum, q ->
            val each = q.rule.outcomes.filter {it.kind == VirtueKind.HASANAT}.sumOf {it.quantity ?: 0L}
            saturatingAdd(sum, saturatingMultiply(each, q.times))
        }
        return DayVirtues(hasanat, qualified)
    }

    private fun fits(rule: TimeWindow, recorded: TimeWindow) = rule == TimeWindow.ANY_TIME || rule == TimeWindow.DAY || rule == recorded

    fun saturatingAdd(a: Long, b: Long) = try {Math.addExact(a, b)} catch(e: ArithmeticException) {Long.MAX_VALUE}
    fun saturatingMultiply(a: Long, b: Long) = try {Math.multiplyExact(a, b)} catch(e: ArithmeticException) {Long.MAX_VALUE}
}

/** Personal, optional goal. Activity goals work without any approved numeric rule. */
enum class GoalKind { HASANAT, QURAN_PAGES, ADHKAR_ITEMS }

data class GoalProgress(val value: Long, val target: Long) {
    val reached get() = value >= target
    val remaining get() = (target - value).coerceAtLeast(0)
    /** Progress in thousandths, capped at 1000 for the bar; [value] keeps the real total beyond the goal. */
    val permille get() = if(value >= target) 1000 else ((value * 1000) / target).toInt()
}

object DailyGoal {
    val PRESETS = listOf(5_000L, 10_000L, 15_000L, 25_000L)
    const val MAX_TARGET = 10_000_000_000L
    fun valid(target: Long) = target in 1..MAX_TARGET
    /** null when the goal is off, invalid, or its value cannot be shown honestly (no approved numeric rule). */
    fun progress(target: Long, value: Long?): GoalProgress? = if(!valid(target) || value == null) null else GoalProgress(value.coerceAtLeast(0), target)
}

/** Progress through one adhkar routine (morning, evening ...), from the confirmed counts only. */
data class RoutineState(val done: Int, val total: Int) {
    val complete get() = total > 0 && done >= total
    val left get() = (total - done).coerceAtLeast(0)
}
