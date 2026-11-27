package org.najwa.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WorshipTest {
    private val day = LocalDate.of(2026, 11, 25)
    private val rules = WorshipCatalog.rules
    private var next = 0
    private fun rec(action: String, count: Long, window: TimeWindow = TimeWindow.DAY, id: String = "r${next++}") =
        WorshipRecord(id, action, count, window, day)
    private fun review(vararg r: WorshipRecord) = WorshipEngine.evaluate(r.toList(), rules, RulePolicy.REVIEW)
    /** Only [id] approved, every other rule back to unreviewed. */
    private fun approved(id: String) = rules.map {it.copy(status = if(it.id == id) EvidenceStatus.APPROVED else EvidenceStatus.UNREVIEWED)}

    @Test fun approvedCatalogCountsInRelease() {
        // All 17 rules were approved as written (docs/worship/EVIDENCE.md).
        assertEquals(17, rules.size)
        assertTrue(rules.all {it.status == EvidenceStatus.APPROVED})
        val r = WorshipEngine.evaluate(listOf(rec(WorshipActions.TAHLIL, 100), rec(WorshipActions.QURAN_LETTER, 500, TimeWindow.ANY_TIME)), rules, RulePolicy.RELEASE)
        // 500 letters × 10, plus the hundred hasanat of the full tahlil said a hundred times.
        assertEquals(5100L, r.hasanat)
        assertTrue(r.expiationActions >= 1)
        assertEquals(rules.size, rules.map {it.id}.toSet().size)
    }

    @Test fun unreviewedRulesNeverCountInRelease() {
        val pending = rules.map {it.copy(status = EvidenceStatus.UNREVIEWED)}
        val r = WorshipEngine.evaluate(listOf(rec(WorshipActions.TAHLIL, 100), rec(WorshipActions.QURAN_LETTER, 500, TimeWindow.ANY_TIME)), pending, RulePolicy.RELEASE)
        assertNull("no number without an approved numeric rule", r.hasanat)
        assertTrue(r.qualified.isEmpty())
        assertEquals(0, r.expiationActions)
    }

    @Test fun onlyApprovedRulesCountInRelease() {
        val r = WorshipEngine.evaluate(listOf(rec(WorshipActions.QURAN_LETTER, 47, TimeWindow.ANY_TIME), rec(WorshipActions.TAHLIL, 100)),
            approved("quran.letters"), RulePolicy.RELEASE)
        assertEquals(470L, r.hasanat)
        assertEquals(listOf("quran.letters"), r.qualified.map {it.rule.id})
    }

    @Test fun disputedAndDisabledRulesNeverCount() {
        val set = rules.map {if(it.id == "dhikr.tahlil.100") it.copy(status = EvidenceStatus.DISPUTED) else if(it.id == "dhikr.tahlil.10") it.copy(status = EvidenceStatus.DISABLED) else it}
        val r = WorshipEngine.evaluate(listOf(rec(WorshipActions.TAHLIL, 100)), set, RulePolicy.REVIEW)
        assertTrue(r.qualified.isEmpty())
    }

    @Test fun repetitionThresholdIsExact() {
        assertTrue(review(rec(WorshipActions.TASBIH_BIHAMDIHI, 99)).qualified.isEmpty())
        val r = review(rec(WorshipActions.TASBIH_BIHAMDIHI, 100))
        assertEquals(listOf("dhikr.tasbih_bihamdihi.100"), r.qualified.map {it.rule.id})
        assertEquals(1, r.expiationActions)
    }

    @Test fun tahlilHundredBringsItsOutcomesTogether() {
        val r = review(rec(WorshipActions.TAHLIL, 100))
        assertEquals(setOf("dhikr.tahlil.100", "dhikr.tahlil.10"), r.qualified.map {it.rule.id}.toSet())
        assertEquals(100L, r.hasanat)
        assertEquals(1, r.expiationActions)
        assertEquals(listOf("dhikr.tahlil.100"), r.protection.map {it.rule.id})
    }

    @Test fun alternativeOutcomesAreNeverSummed() {
        val r = review(rec(WorshipActions.TASBIH, 100))
        assertEquals(listOf("dhikr.tasbih.100"), r.qualified.map {it.rule.id})
        assertEquals("a thousand hasanat OR a thousand sins: no number is added", 0L, r.hasanat)
        assertEquals(1, r.expiationActions)
    }

    @Test fun duplicateDeliveryCountsOnceButIntentionalRepeatsCount() {
        val once = rec(WorshipActions.QURAN_LETTER, 19, TimeWindow.ANY_TIME, id = "same")
        assertEquals(190L, review(once, once.copy()).hasanat)
        assertEquals(380L, review(once, once.copy(id = "again")).hasanat)
    }

    @Test fun dailyRulesCountOncePerDayAndAfterPrayerUpToFive() {
        val day = review(rec(WorshipActions.TASBIH_BIHAMDIHI, 100), rec(WorshipActions.TASBIH_BIHAMDIHI, 100))
        assertEquals(1L, day.qualified.single().times)
        val after = review(*Array(7) {rec(WorshipActions.AFTER_PRAYER_HUNDRED, 1, TimeWindow.AFTER_PRAYER)})
        assertEquals(5L, after.qualified.single().times)
        assertEquals(5, after.expiationActions)
    }

    @Test fun morningAndEveningStaySeparate() {
        val morning = review(rec(WorshipActions.MUAWWIDHAT, 3, TimeWindow.MORNING))
        assertEquals(listOf("dhikr.muawwidhat.morning"), morning.protection.map {it.rule.id})
        val both = review(rec(WorshipActions.MUAWWIDHAT, 3, TimeWindow.MORNING), rec(WorshipActions.MUAWWIDHAT, 3, TimeWindow.EVENING))
        assertEquals(setOf("dhikr.muawwidhat.morning", "dhikr.muawwidhat.evening"), both.protection.map {it.rule.id}.toSet())
        assertTrue(review(rec(WorshipActions.KURSI, 1, TimeWindow.MORNING)).qualified.isEmpty())
    }

    @Test fun prayersAreExpiationActionsWithoutANumber() {
        val r = review(rec(WorshipActions.PRAYER, 1, TimeWindow.ANY_TIME), rec(WorshipActions.PRAYER, 1, TimeWindow.ANY_TIME),
            rec(WorshipActions.PRAYER_JAMAAH, 1, TimeWindow.ANY_TIME))
        assertEquals(2, r.expiationActions)
        assertEquals("congregation degrees are not hasanat", 0L, r.hasanat)
    }

    @Test fun overflowSaturates() {
        val r = review(rec(WorshipActions.QURAN_LETTER, Long.MAX_VALUE / 2, TimeWindow.ANY_TIME), rec(WorshipActions.QURAN_LETTER, Long.MAX_VALUE / 2, TimeWindow.ANY_TIME))
        assertEquals(Long.MAX_VALUE, r.hasanat)
        assertEquals(Long.MAX_VALUE, WorshipEngine.saturatingAdd(Long.MAX_VALUE, 1))
    }

    @Test fun rulesRejectInvalidDefinitions() {
        assertThrows(IllegalArgumentException::class.java) {
            VirtueRule("x", 1, "a", 0, TimeWindow.DAY, listOf(Outcome(VirtueKind.VIRTUE)), listOf(Evidence("c", "1")))
        }
        assertThrows(IllegalArgumentException::class.java) {WorshipRecord("x", "a", -1, TimeWindow.DAY, day)}
    }

    @Test fun goalProgress() {
        val g = DailyGoal.progress(15_000, 12_450)!!
        assertEquals(830, g.permille)
        assertEquals(2_550L, g.remaining)
        assertFalse(g.reached)
        val over = DailyGoal.progress(5_000, 7_000)!!
        assertEquals(1000, over.permille)
        assertEquals(7_000L, over.value)
        assertEquals(0L, over.remaining)
        assertTrue(over.reached)
        assertNull("off", DailyGoal.progress(0, 10))
        assertNull("invalid", DailyGoal.progress(-5, 10))
        assertNull("too large", DailyGoal.progress(DailyGoal.MAX_TARGET + 1, 10))
        assertNull("no honest number to show", DailyGoal.progress(10_000, null))
        assertEquals(1000, DailyGoal.progress(DailyGoal.MAX_TARGET, Long.MAX_VALUE)!!.permille)
    }

    @Test fun routineState() {
        assertTrue(RoutineState(13, 13).complete)
        assertEquals(3, RoutineState(10, 13).left)
        assertFalse(RoutineState(0, 0).complete)
    }

    @Test fun quranLetterMethod() {
        // Basmalah: 19 written letters; the dagger alif in الرَّحْمَٰنِ is not a written letter.
        assertEquals(19, QuranLetters.count("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"))
        // Al-Ikhlas without the basmalah: 47 letters. Verse ornaments, numbers and spaces are ignored.
        assertEquals(47, QuranLetters.count("قُلْ هُوَ اللَّهُ أَحَدٌ ﴿١﴾ اللَّهُ الصَّمَدُ ﴿٢﴾ لَمْ يَلِدْ وَلَمْ يُولَدْ ﴿٣﴾ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ ﴿٤﴾"))
        // Alif wasla counts as alif; shadda does not double the letter; pause marks are not letters.
        assertEquals(4, QuranLetters.count("ٱللَّهِ"))
        assertEquals(QuranLetters.count("ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ"), QuranLetters.count("ذلك الكتب لا ريب فيه"))
        // Disjoined letters count as written.
        assertEquals(3, QuranLetters.count("الٓمٓ"))
        // A hamza written above a tatweel is a written letter: ي س ء ل و ن ك.
        assertEquals(7, QuranLetters.count("يَسْـَٔلُونَكَ"))
        assertEquals(0, QuranLetters.count("﴿١٢٣﴾ ۚ ۖ ـ 123"))
    }
}
