package org.najwa.core

/**
 * An approximate tajweed colouring for the King Fahd Complex Hafs text, read from the marks the Complex already prints.
 *
 * The Complex's text encodes much of the recitation: the small circle over a letter that is never pronounced is
 * stored as U+0652 (the real sukun is U+06E1), the rectangle over the alif of «أنا» as U+06E0, the open (staggered)
 * tanween as U+08F0–08F2, iqlab as a small meem (U+06E2/U+06ED), the obligatory madd as U+0653/U+06E4, and a letter
 * merged into the next is written bare before a shadda. Four groups are coloured, close to common tajweed mushafs:
 *
 *  - [Rule.SILENT]: written but not pronounced (the small circle, alif wasla in continuation, a letter merged into the next).
 *  - [Rule.NASAL]: ghunnah, ikhfa, idgham with ghunnah and iqlab.
 *  - [Rule.MADD]: the obligatory and necessary madd.
 *  - [Rule.QALQALAH]: ق ط ب ج د with sukun, or at the end of a verse.
 *
 * It is a reading aid, not a ruling: the colouring is automatic and does not replace learning from a qualified teacher.
 */
object Tajweed {
    enum class Rule {SILENT,NASAL,MADD,QALQALAH}
    data class Span(val start: Int,val end: Int,val rule: Rule)

    private const val TATWEEL='ـ'
    private const val SHADDA='ّ'
    private const val SILENT_MARK='ْ'
    private const val SUKUN='ۡ'
    private const val IKHFA="تثجدذزسشصضطظفقك"
    private const val IDGHAM_GHUNNAH="ينمو"
    private const val QALQALAH="قطبجد"
    /** Letters that only carry a vowel's length and are skipped when looking for the next consonant. */
    private const val CARRIERS="اىوي"

    private fun isMark(c: Char)=c in 'ً'..'ٟ' || c=='ٰ' || c in 'ۖ'..'ۭ' || c in 'ࣰ'..'ࣳ'
    private fun isLetter(c: Char)=c in 'ء'..'ي' || c=='ٱ'

    /** A letter with its marks (and any kashida after it), as [start, end) in the text. */
    private class Cluster(val start: Int,var end: Int,val base: Char,val marks: StringBuilder=StringBuilder())

    private fun clusters(text: String): List<Cluster> {
        val out=ArrayList<Cluster>()
        for(i in text.indices) {
            val c=text[i]
            when {
                isLetter(c) -> out+=Cluster(i,i+1,c)
                (isMark(c) || c==TATWEEL) && out.isNotEmpty() && out.last().end==i -> out.last().apply {end=i+1;if(c!=TATWEEL)marks.append(c)}
            }
        }
        return out
    }

    private val Cluster.bare get()=marks.isEmpty()
    private val Cluster.shadda get()=SHADDA in marks
    private fun Cluster.has(vararg c: Char)=c.any {it in marks}

    /**
     * Coloured spans of [text] (a word, a line or a verse). [following] is the text that comes next (the next word),
     * read only to decide noon and tanween rules at the end of [text]; [verseEnd] marks [text] as the end of a verse.
     */
    fun spans(text: String,following: String?=null,verseEnd: Boolean=false): List<Span> {
        val all=if(following.isNullOrEmpty()) text else "$text $following"
        val cs=clusters(all)
        val own=cs.count {it.start<text.length}
        val lastOwn=cs.indexOfLast {it.start<text.length}
        val out=ArrayList<Span>()
        fun next(i: Int,skipCarriers: Boolean): Cluster? {
            var j=i+1
            while(j<cs.size) {
                val c=cs[j]
                if(!(skipCarriers && c.base in CARRIERS && c.bare))return c
                j++
            }
            return null
        }
        for(i in 0 until own) {
            val c=cs[i]
            val n=next(i,false)
            val consonant=next(i,true)
            val rule=when {
                c.has(SILENT_MARK,'۠') -> Rule.SILENT
                c.base=='ٱ' -> Rule.SILENT
                c.has('ٓ','ۤ') -> Rule.MADD
                (c.base=='ن' || c.base=='م') && c.shadda -> Rule.NASAL
                c.has('ۢ','ۭ') -> Rule.NASAL
                c.has('ࣰ','ࣱ','ࣲ') && consonant!=null && (consonant.base in IKHFA || consonant.base in IDGHAM_GHUNNAH) -> Rule.NASAL
                c.base=='ن' && c.bare && n!=null && n.shadda && n.base in IDGHAM_GHUNNAH -> Rule.NASAL
                c.base=='ن' && c.bare && n!=null && n.base in IKHFA -> Rule.NASAL
                c.base=='م' && c.bare && n?.base=='ب' -> Rule.NASAL
                // A consonant written bare before a shadda is merged into it: «ٱللَّه», «مِن رَّبِّهِم», the sun letters.
                c.bare && c.base !in CARRIERS && c.base!='ء' && n!=null && n.shadda -> Rule.SILENT
                c.base in QALQALAH && c.has(SUKUN) -> Rule.QALQALAH
                verseEnd && i==lastOwn && c.base in QALQALAH -> Rule.QALQALAH
                else -> null
            }
            if(rule!=null) {
                val end=minOf(c.end,text.length)
                // Neighbouring letters of the same rule form one span.
                val prev=out.lastOrNull()
                if(prev!=null && prev.rule==rule && prev.end==c.start) out[out.lastIndex]=prev.copy(end=end) else out+=Span(c.start,end,rule)
            }
        }
        return keepLigatures(out,cs,text.length)
    }

    private const val ALIFS="اأإآٱ"

    /**
     * Lam followed by an alif is drawn as one ligature (لا); a colour boundary between them would break it apart.
     * A span that starts on such an alif takes in its lam, and one that ends on such a lam takes in its alif.
     */
    private fun keepLigatures(spans: List<Span>,cs: List<Cluster>,limit: Int): List<Span> {
        if(spans.isEmpty())return spans
        val byStart=cs.associateBy {it.start}
        val byEnd=cs.associateBy {it.end}
        val adjusted=spans.map {s->
            var start=s.start;var end=s.end
            val first=byStart[start]
            val before=byEnd[start]
            if(first!=null && first.base in ALIFS && before!=null && before.base=='ل')start=before.start
            val last=byEnd[end]
            val after=byStart[end]
            if(last!=null && last.base=='ل' && after!=null && after.base in ALIFS)end=minOf(after.end,limit)
            Span(start,end,s.rule)
        }.sortedBy {it.start}
        // Widening can make neighbours overlap: the earlier span keeps its colour up to where the next one begins.
        val out=ArrayList<Span>()
        adjusted.forEach {s->
            val prev=out.lastOrNull()
            when {
                prev==null -> out+=s
                s.start>=prev.end -> out+=s
                s.end>prev.end -> out+=Span(prev.end,s.end,s.rule)
            }
        }
        return out.filter {it.start<it.end}
    }
}
