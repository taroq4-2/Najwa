package org.najwa.core

import org.najwa.core.VirtueKind.EXPIATION
import org.najwa.core.VirtueKind.HASANAT
import org.najwa.core.VirtueKind.PROTECTION
import org.najwa.core.VirtueKind.VIRTUE

/**
 * Evidence catalog. Every rule is UNREVIEWED: it was prepared from the cited collections and has not been
 * approved by a qualified scholar, so release builds count none of them. A reviewer approves a rule by setting
 * its status to APPROVED (and bumping [VirtueRule.version] whenever the rule itself changes); see docs/worship/EVIDENCE.md.
 * Wording shown to people lives in string resources keyed by rule id, also pending review.
 */
object WorshipCatalog {
    val rules = listOf(
        // «من قرأ حرفًا من كتاب الله فله به حسنة، والحسنة بعشر أمثالها». Counted per confirmed letter (see QuranLetters).
        VirtueRule("quran.letters", 1, WorshipActions.QURAN_LETTER, 1, TimeWindow.ANY_TIME,
            listOf(Outcome(HASANAT, 10)), listOf(Evidence("الترمذي", "2910")), perUnit = true),
        // Each obligatory prayer: the five prayers expiate what is between them, while major sins are avoided.
        VirtueRule("prayer.five", 1, WorshipActions.PRAYER, 1, TimeWindow.ANY_TIME,
            listOf(Outcome(EXPIATION)), listOf(Evidence("مسلم", "233"))),
        // Prayer in congregation: twenty-seven degrees (degrees, not hasanat).
        VirtueRule("prayer.jamaah", 1, WorshipActions.PRAYER_JAMAAH, 1, TimeWindow.ANY_TIME,
            listOf(Outcome(VIRTUE, 27)), listOf(Evidence("البخاري", "645"), Evidence("مسلم", "650"))),
        // سبحان الله وبحمده مائة مرة في يوم: his sins are removed even if like the foam of the sea (no number).
        VirtueRule("dhikr.tasbih_bihamdihi.100", 1, WorshipActions.TASBIH_BIHAMDIHI, 100, TimeWindow.DAY,
            listOf(Outcome(EXPIATION)), listOf(Evidence("البخاري", "6405"), Evidence("مسلم", "2691"))),
        // The full tahlil a hundred times in a day: ten freed slaves, a hundred hasanat written, a hundred sins erased,
        // and a protection from Satan that day until evening. All together (AND).
        VirtueRule("dhikr.tahlil.100", 1, WorshipActions.TAHLIL, 100, TimeWindow.DAY,
            listOf(Outcome(HASANAT, 100), Outcome(EXPIATION, 100), Outcome(PROTECTION), Outcome(VIRTUE)),
            listOf(Evidence("البخاري", "3293"), Evidence("مسلم", "2691"))),
        // The full tahlil ten times: like freeing four souls (no hasanat number).
        VirtueRule("dhikr.tahlil.10", 1, WorshipActions.TAHLIL, 10, TimeWindow.DAY,
            listOf(Outcome(VIRTUE)), listOf(Evidence("البخاري", "6404"), Evidence("مسلم", "2693"))),
        // A hundred tasbihat: a thousand hasanat are written OR a thousand sins removed. "Or": never summed.
        VirtueRule("dhikr.tasbih.100", 1, WorshipActions.TASBIH, 100, TimeWindow.DAY,
            listOf(Outcome(HASANAT, 1000), Outcome(EXPIATION, 1000)), listOf(Evidence("مسلم", "2698")),
            relation = OutcomeRelation.ANY),
        // After each prayer: 33 tasbih, 33 tahmid, 33 takbir, completed to a hundred by the tahlil.
        VirtueRule("routine.after_prayer.100", 1, WorshipActions.AFTER_PRAYER_HUNDRED, 1, TimeWindow.AFTER_PRAYER,
            listOf(Outcome(EXPIATION)), listOf(Evidence("مسلم", "597"))),
        // One blessing upon the Prophet ﷺ: Allah blesses him ten times (blessings, not hasanat).
        VirtueRule("dhikr.salawat", 1, WorshipActions.SALAWAT, 1, TimeWindow.ANY_TIME,
            listOf(Outcome(VIRTUE, 10)), listOf(Evidence("مسلم", "408"))),
        // Ayat al-Kursi on going to bed: a guardian from Allah stays with him until morning.
        VirtueRule("dhikr.kursi.sleep", 1, WorshipActions.KURSI, 1, TimeWindow.BEFORE_SLEEP,
            listOf(Outcome(PROTECTION)), listOf(Evidence("البخاري", "2311"))),
        // Al-Ikhlas and the two Mu'awwidhat three times, morning and evening: they suffice you against everything.
        VirtueRule("dhikr.muawwidhat.morning", 1, WorshipActions.MUAWWIDHAT, 3, TimeWindow.MORNING,
            listOf(Outcome(PROTECTION)), listOf(Evidence("أبو داود", "5082"), Evidence("الترمذي", "3575"))),
        VirtueRule("dhikr.muawwidhat.evening", 1, WorshipActions.MUAWWIDHAT, 3, TimeWindow.EVENING,
            listOf(Outcome(PROTECTION)), listOf(Evidence("أبو داود", "5082"), Evidence("الترمذي", "3575"))),
        // «بسم الله الذي لا يضر مع اسمه شيء ...» three times morning and evening.
        VirtueRule("dhikr.bismillah.morning", 1, WorshipActions.BISMILLAH_SHIELD, 3, TimeWindow.MORNING,
            listOf(Outcome(PROTECTION)), listOf(Evidence("أبو داود", "5088"), Evidence("الترمذي", "3388"))),
        VirtueRule("dhikr.bismillah.evening", 1, WorshipActions.BISMILLAH_SHIELD, 3, TimeWindow.EVENING,
            listOf(Outcome(PROTECTION)), listOf(Evidence("أبو داود", "5088"), Evidence("الترمذي", "3388"))),
        // «أعوذ بكلمات الله التامات من شر ما خلق» in the evening: nothing would have harmed him (no count in this narration).
        VirtueRule("dhikr.kalimat.evening", 1, WorshipActions.KALIMAT, 1, TimeWindow.EVENING,
            listOf(Outcome(PROTECTION)), listOf(Evidence("مسلم", "2709"))),
        // Sayyid al-istighfar with conviction, morning or evening.
        VirtueRule("dhikr.sayyid_istighfar.morning", 1, WorshipActions.SAYYID_ISTIGHFAR, 1, TimeWindow.MORNING,
            listOf(Outcome(VIRTUE)), listOf(Evidence("البخاري", "6306"))),
        VirtueRule("dhikr.sayyid_istighfar.evening", 1, WorshipActions.SAYYID_ISTIGHFAR, 1, TimeWindow.EVENING,
            listOf(Outcome(VIRTUE)), listOf(Evidence("البخاري", "6306"))),
    )

    fun rule(id: String) = rules.firstOrNull {it.id == id}
}

/**
 * Quran letter counting, method version [METHOD_VERSION] (docs/worship/QURAN_LETTERS.md). Pending qualified review.
 * Counts the letters written in the Uthmani text: every Arabic letter code point, each hamza form as one letter
 * (including a hamza written above a tatweel, U+0654), alif wasla as alif. Not counted: harakat, shadda (the doubled letter is written once), sukun, tanwin,
 * the dagger alif and other small superscript letters, tatweel, Quranic pause and annotation marks,
 * verse-end ornaments and numbers, spaces. Disjoined letters (الم) count as written (three letters).
 * Whether a surah's opening basmalah is part of a page total is decided by the page table, not here.
 */
object QuranLetters {
    const val METHOD_VERSION = 1

    fun isLetter(c: Char) = c in 'ء'..'غ' || c in 'ف'..'ي' || c == 'ٱ' || c == 'ٔ'

    fun count(text: String): Int = text.count(::isLetter)
}
