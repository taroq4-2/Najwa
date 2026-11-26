package org.najwa.app.platform

import kotlin.math.ceil

/**
 * Lays out a mushaf page the way the printed Madinah mushaf does: fifteen lines from top to bottom, a surah heading
 * and the basmalah each taking one line, and every text line justified to the full width. The page starts and ends
 * at the same verses as the print; how words fall on the lines is computed here, as evenly as the text allows.
 *
 * Widths come from [Measure], so the same code runs with the real font on the phone and with fixed widths in tests.
 */
object MushafLayout {
    const val LINES=15
    /** Width of the centred block on the two opening pages. */
    const val OPENING_WIDTH=.82f

    /** A word as it is drawn. Pause marks and the hizb sign travel with their word; [aya] is set on a verse's last word. */
    data class Token(val text: String,val aya: Int?=null,val verse: Int=-1)

    sealed interface Row
    data class Heading(val surah: Int): Row
    data object Basmalah: Row
    data class Line(val tokens: List<Token>): Row

    /**
     * [rows] fill the fifteen-line grid ([compact] pages, the first two, hold fewer lines in a narrower centred block).
     * [scale] multiplies the measuring size to give the font size that fits the page.
     */
    data class Page(val number: Int,val rows: List<Row>,val scale: Float,val compact: Boolean)

    interface Measure {
        /** Width of [token] at the reference size, verse marker included. */
        fun width(token: Token): Float
        /** Width of a word space at the reference size. */
        val space: Float
    }

    /** Pause marks (U+06D6–06ED) written as separate words belong to the word before them; the hizb sign to the word after. */
    fun tokens(text: String,aya: Int): List<Token> {
        val out=ArrayList<String>()
        var carry: String?=null
        text.split(' ').filter {it.isNotEmpty()}.forEach {w->
            when {
                w=="۞" -> carry=w
                w.all {it in 'ۖ'..'ۭ'} && out.isNotEmpty() -> out[out.lastIndex]=out.last()+" "+w
                else -> {out+=if(carry!=null)"$carry $w" else w;carry=null}
            }
        }
        carry?.let {out+=it}
        return out.mapIndexed {i,w->Token(w,if(i==out.lastIndex) aya else null)}
    }

    /**
     * Lays out [page] for a text block [width] wide; [maxScale] caps the font so lines keep their height.
     * The font is reduced in small steps until every segment fits its lines.
     */
    fun layout(d: Mushaf.Data,page: Int,width: Float,maxScale: Float,m: Measure): Page {
        val verses=d.page(page)
        val compact=page<=2
        // Segments: the verses of one surah on this page, each opened by its heading (and basmalah) when it starts here.
        data class Segment(val surah: Int,val opens: Boolean,val tokens: List<Token>)
        val segments=ArrayList<Segment>()
        val base=d.firstIndex(page)
        verses.forEachIndexed {n,v->
            // Every word knows its verse (index into the whole text), for marks, search results and memorization.
            val t=tokens(d.displayText(v),v.aya).map {it.copy(verse=base+n)}
            if(segments.isEmpty() || v.aya==1) segments+=Segment(v.sura,v.aya==1,t)
            else segments[segments.lastIndex]=segments.last().let {it.copy(tokens=it.tokens+t)}
        }
        fun furniture(s: Segment)=if(!s.opens) 0 else if(s.surah==1 || s.surah==9) 1 else 2
        val natural=segments.map {s->s.tokens.sumOf {m.width(it).toDouble()}.toFloat()+m.space*(s.tokens.size-1)}

        val blockWidth=if(compact) width*OPENING_WIDTH else width
        var scale=if(compact) maxScale else minOf(maxScale,fitScale(natural,blockWidth,LINES-segments.sumOf(::furniture),m.space))
        repeat(40) {
            val cap=blockWidth/scale
            val lines=if(compact) segments.mapIndexed {i,s->ceil(natural[i]/(cap*.94f)).toInt().coerceIn(1,s.tokens.size)}
                else allocate(natural,segments.map {it.tokens.size},LINES-segments.sumOf(::furniture))
            val broken=segments.mapIndexed {i,s->breakLines(s.tokens.map(m::width),m.space,lines[i],cap)}
            if(broken.all {it!=null}) {
                val rows=ArrayList<Row>()
                segments.forEachIndexed {i,s->
                    if(s.opens) {rows+=Heading(s.surah);if(furniture(s)==2)rows+=Basmalah}
                    broken[i]!!.forEach {(a,b)->rows+=Line(s.tokens.subList(a,b))}
                }
                return Page(page,rows,scale,compact)
            }
            scale*=.97f
        }
        // Unreachable for real pages; a safe fallback keeps one verse per line.
        return Page(page,segments.flatMap {s->listOfNotNull<Row>(if(s.opens)Heading(s.surah) else null)+s.tokens.chunked(6).map {Line(it)}},scale,compact)
    }

    /** The scale at which the text just fills [lines] full lines (a little slack is left for the breaks). */
    private fun fitScale(natural: List<Float>,width: Float,lines: Int,space: Float): Float {
        val total=natural.sum()+space*natural.size
        return if(total<=0f || lines<=0) 1f else width*lines*.965f/total
    }

    /** Text lines per segment in proportion to its text, at least one each and no more than it has words. */
    internal fun allocate(natural: List<Float>,words: List<Int>,lines: Int): List<Int> {
        val n=natural.size
        val total=natural.sum().coerceAtLeast(1f)
        val available=lines.coerceAtLeast(n)
        val exact=natural.map {it/total*available}
        val out=exact.mapIndexed {i,e->e.toInt().coerceIn(1,words[i])}.toMutableList()
        // Hand out what is left by the largest remainder, then take back any excess from the largest share.
        var left=available-out.sum()
        val order=exact.indices.sortedByDescending {exact[it]-exact[it].toInt()}
        while(left>0 && order.any {out[it]<words[it]}) {
            for(i in order) if(left>0 && out[i]<words[i]) {out[i]++;left--}
        }
        while(out.sum()>available) {val i=out.indices.maxBy {out[it]};if(out[i]<=1)break;out[i]--}
        return out
    }

    /**
     * Splits words of [widths] into exactly [lines] lines no wider than [cap], keeping line widths as equal as possible
     * (least squares of the slack). Returns [start, end) ranges, or null when the words cannot fit.
     */
    internal fun breakLines(widths: List<Float>,space: Float,lines: Int,cap: Float): List<Pair<Int,Int>>? {
        val n=widths.size
        val l=lines.coerceIn(1,n)
        val prefix=FloatArray(n+1).also {p->widths.forEachIndexed {i,w->p[i+1]=p[i]+w}}
        fun width(a: Int,b: Int)=prefix[b]-prefix[a]+space*(b-a-1)
        val inf=Double.MAX_VALUE
        // cost[k][j]: best cost of the first j words in k lines.
        val cost=Array(l+1) {DoubleArray(n+1) {inf}}
        val from=Array(l+1) {IntArray(n+1)}
        cost[0][0]=0.0
        for(k in 1..l) for(j in k..n-(l-k)) {
            var i=j-1
            while(i>=k-1) {
                val w=width(i,j)
                if(w>cap)break
                val prev=cost[k-1][i]
                if(prev<inf) {
                    val slack=(cap-w).toDouble()
                    val c=prev+slack*slack
                    if(c<cost[k][j]) {cost[k][j]=c;from[k][j]=i}
                }
                i--
            }
        }
        if(cost[l][n]==inf)return null
        val ranges=ArrayList<Pair<Int,Int>>(l)
        var j=n
        for(k in l downTo 1) {val i=from[k][j];ranges+=i to j;j=i}
        return ranges.reversed()
    }

    /** Arabic ordinal of a juz for the page margin: "الجزء الخامس عشر". */
    fun juzName(juz: Int): String = "الجزء "+juzOrdinals[(juz-1).coerceIn(0,29)]
    private val juzOrdinals=listOf("الأول","الثاني","الثالث","الرابع","الخامس","السادس","السابع","الثامن","التاسع","العاشر",
        "الحادي عشر","الثاني عشر","الثالث عشر","الرابع عشر","الخامس عشر","السادس عشر","السابع عشر","الثامن عشر","التاسع عشر","العشرون",
        "الحادي والعشرون","الثاني والعشرون","الثالث والعشرون","الرابع والعشرون","الخامس والعشرون","السادس والعشرون","السابع والعشرون",
        "الثامن والعشرون","التاسع والعشرون","الثلاثون")

    /** Fraction of a line's width that is text; lines much shorter than the block are centred instead of stretched. */
    fun fill(widths: List<Float>,space: Float,cap: Float)=(widths.sum()+space*(widths.size-1).coerceAtLeast(0))/cap

    /* ── Justification with kashida ── */

    /** A line ready to draw: each token's text (with any tatweel added), its width, and the space between tokens. */
    data class Justified(val texts: List<String>,val widths: List<Float>,val gap: Float)

    private fun isMark(c: Char)=c in 'ً'..'ٟ' || c=='ٰ' || c in 'ۖ'..'ۭ'
    private fun dualJoining(c: Char)=c=='ئ' || c=='ب' || c in 'ت'..'خ' || c in 'س'..'غ' ||
        c=='ـ' || c in 'ف'..'ه' || c=='ى' || c=='ي'
    private fun joinsBefore(c: Char)=c in 'آ'..'غ' || c in 'ـ'..'ي' || c=='ٱ'
    private fun alef(c: Char)=c=='ا' || c=='أ' || c=='إ' || c=='آ' || c=='ٱ'

    /**
     * Where a tatweel may stretch [word], nearest the end first: between two connected letters, after the first
     * letter's marks. Never inside lam-alef, and never in the name of Allah.
     */
    fun kashidaPoints(word: String): List<Int> {
        val bases=word.indices.filter {!isMark(word[it])}
        // Short words are not stretched.
        if(bases.count {word[it] in 'ء'..'ي' || word[it]=='ٱ'}<3)return emptyList()
        if(bases.map {word[it]}.filterNot {it=='ـ'}.joinToString("").contains("لله"))return emptyList()
        val points=ArrayList<Int>()
        for(j in 0 until bases.size-1) {
            val a=word[bases[j]];val b=word[bases[j+1]]
            if(dualJoining(a) && joinsBefore(b) && !(a=='ل' && alef(b)))points+=bases[j+1]
        }
        return points.reversed()
    }

    /**
     * Fills [width] exactly: tatweel goes into the longest words first (up to three per word), the rest of the
     * space is shared evenly between words. [measure] gives widths at the final size; [extra] is each token's marker width.
     */
    fun justify(tokens: List<Token>,width: Float,space: Float,kashida: Float,extra: (Token)->Float,measure: (String)->Float): Justified {
        val texts=tokens.map {it.text}.toMutableList()
        fun widths()=tokens.indices.map {measure(texts[it])+extra(tokens[it])}
        var w=widths()
        val gaps=(tokens.size-1).coerceAtLeast(0)
        val slack=width-w.sum()-space*gaps
        // Spaces take a modest share first (up to 70% wider), so tatweel stays light, as in a well-set page.
        val stretch=slack-minOf(slack,gaps*space*.7f)
        if(stretch>kashida && kashida>0f) {
            var count=(stretch/kashida).toInt()
            val points=tokens.map {kashidaPoints(it.text)}
            val order=tokens.indices.filter {points[it].isNotEmpty()}.sortedByDescending {w[it]}
            val added=IntArray(tokens.size)
            var round=0
            while(count>0 && round<3 && order.isNotEmpty()) {
                for(i in order) {
                    if(count==0)break
                    val pts=points[i]
                    // Spread over the word's joins before stacking on one.
                    val at=pts[added[i]%pts.size]
                    // Indices move as tatweel is inserted earlier in the word; count the ones placed before this point.
                    val before=(0 until added[i]).count {k->pts[k%pts.size]<at}
                    val pos=at+before
                    texts[i]=texts[i].substring(0,pos)+'ـ'+texts[i].substring(pos)
                    added[i]++;count--
                }
                round++
            }
            w=widths()
        }
        val rest=width-w.sum()
        val gap=if(gaps==0) 0f else (rest/gaps).coerceAtLeast(space*.5f)
        return Justified(texts,w,gap)
    }
}
