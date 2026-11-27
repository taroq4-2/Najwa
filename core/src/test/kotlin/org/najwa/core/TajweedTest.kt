package org.najwa.core

import org.junit.Assert.*
import org.junit.Test
import org.najwa.core.Tajweed.Rule

/** The tajweed colouring read from the King Fahd Complex text's own marks (verses quoted from its Hafs text). */
class TajweedTest {
    private fun rules(text: String,following: String?=null,verseEnd: Boolean=false)=
        Tajweed.spans(text,following,verseEnd).map {text.substring(it.start,it.end) to it.rule}

    @Test fun silentLettersAreGrey() {
        // The small circle (stored as U+0652): the alif after the plural waw, the waw of «أولئك».
        assertTrue(rules("ءَامَنُواْ").any {(s,r)->r==Rule.SILENT && s.startsWith("ا")})
        assertTrue(rules("أُوْلَٰٓئِكَ").any {(s,r)->r==Rule.SILENT && s.startsWith("و")})
        // Alif wasla and the article's lam merged into «الله».
        assertEquals(Rule.SILENT,rules("ٱللَّهَ").first().second)
        assertEquals("ٱل",rules("ٱللَّهَ").first().first)
        // «أنا»: the alif with the rectangle is not read in continuation.
        assertTrue(rules("أَنَا۠").any {(s,r)->r==Rule.SILENT && s.startsWith("ا")})
    }

    @Test fun noonAndTanweenRules() {
        // Ikhfa: bare noon before fa; open tanween before fa.
        assertTrue(rules("أَنفُسَهُمۡ").any {(s,r)->r==Rule.NASAL && s.startsWith("ن")})
        assertTrue(rules("مَّرَضࣱ","فَزَادَهُمُ").any {(s,r)->r==Rule.NASAL && s.startsWith("ض")})
        // Idgham with ghunnah across the carrier alif: «هُدࣰى مِّن».
        assertTrue(rules("هُدࣰى","مِّن").any {(s,r)->r==Rule.NASAL && s.startsWith("د")})
        // Idgham without ghunnah: the noon of «مِّن رَّبِّهِمۡ» is merged (grey), the meem's shadda is ghunnah.
        val min=rules("مِّن","رَّبِّهِمۡ")
        assertTrue(min.any {(s,r)->r==Rule.NASAL && s.startsWith("م")})
        assertTrue(min.any {(s,r)->r==Rule.SILENT && s.startsWith("ن")})
        // Iqlab: the small meem in «أَلِيمُۢ بِمَا».
        assertTrue(rules("أَلِيمُۢ","بِمَا").any {(s,r)->r==Rule.NASAL && s.startsWith("م")})
        // Izhar: a regular tanween before hamza stays plain.
        assertTrue(rules("عَذَابٌ","أَلِيمُۢ").none {(s,_)->s.startsWith("ب")})
    }

    @Test fun maddAndQalqalah() {
        // The madd alif is coloured with its lam (one ligature).
        assertTrue(rules("إِلَّآ").any {(s,r)->r==Rule.MADD && s.contains("ا")})
        assertTrue(rules("أُوْلَٰٓئِكَ").any {(s,r)->r==Rule.MADD && s.startsWith("ل")})
        // Qalqalah: a letter of قطب جد with sukun, and the last letter when the verse ends.
        assertTrue(rules("يَدۡخُلُونَ").any {(s,r)->r==Rule.QALQALAH && s.startsWith("د")})
        assertTrue(rules("ٱلۡفَلَقِ",verseEnd=true).any {(s,r)->r==Rule.QALQALAH && s.startsWith("ق")})
        assertTrue(rules("ٱلۡفَلَقِ").none {(_,r)->r==Rule.QALQALAH})
        // A pronounced lam (with sukun) is not silent.
        assertTrue(rules("ٱلۡفَلَقِ").none {(s,_)->s.startsWith("ل")})
    }

    @Test fun lamAlifLigatureIsNeverSplit() {
        // «إِلَّآ»: the madd is on the alif; the colour takes in the lam so «لا» stays one ligature.
        val text="إِلَّآ"
        Tajweed.spans(text).forEach {s->
            if(s.start>0)assertFalse("split before alif",text[s.start] in "اأإآٱ" && text.substring(0,s.start).trimEnd {it in 'ً'..'ٟ'}.lastOrNull()=='ل')
        }
        assertEquals(Rule.MADD,Tajweed.spans(text).single().rule)
        assertEquals(2,Tajweed.spans(text).single().start)
    }

    @Test fun spansStayInsideTheTextAndCoverWholeLetters() {
        val text="فِي قُلُوبِهِم مَّرَضࣱ فَزَادَهُمُ ٱللَّهُ مَرَضࣰاۖ وَلَهُمۡ عَذَابٌ أَلِيمُۢ بِمَا كَانُواْ يَكۡذِبُونَ"
        val spans=Tajweed.spans(text,verseEnd=true)
        assertTrue(spans.isNotEmpty())
        spans.forEach {s->
            assertTrue(s.start>=0 && s.end<=text.length && s.start<s.end)
            // A span never starts on a combining mark (the letter and its marks are coloured together).
            assertFalse(text[s.start] in 'ً'..'ٟ')
        }
        // Kashida inserted by justification does not change the reading.
        assertEquals(rules("أَنفُسَهُمۡ").map {it.second},rules("أَنفُـسَـهُمۡ").map {it.second})
    }
}
