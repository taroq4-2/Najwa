package org.najwa.app.platform

import android.content.Context

/**
 * The mushaf text for the in-app reader: Tanzil Quran Text (Uthmani), bundled unchanged in assets/quran/quran-uthmani.txt,
 * with the page, surah and juz index of the 604-page Madinah mushaf (assets/quran/index.txt). Loaded once, off the main thread.
 */
object Mushaf {
    const val PAGES=604
    data class Surah(val number: Int,val start: Int,val verses: Int,val name: String,val latin: String,val meccan: Boolean,val page: Int)
    data class Verse(val sura: Int,val aya: Int,val text: String)

    class Data(val verses: List<Verse>,val surahs: List<Surah>,private val pageStarts: IntArray,private val juzStarts: IntArray) {
        /** Verses printed on [page] (1..604). */
        fun page(page: Int): List<Verse> {
            val p=page.coerceIn(1,PAGES)
            return verses.subList(pageStarts[p-1],if(p<PAGES)pageStarts[p] else verses.size)
        }
        /** Index of the first verse printed on [page]. */
        fun firstIndex(page: Int)=pageStarts[page.coerceIn(1,PAGES)-1]
        fun surahsOn(page: Int)=page(page).map {it.sura}.distinct().map {surahs[it-1]}
        fun juzOf(page: Int): Int {val first=pageStarts[page.coerceIn(1,PAGES)-1];return juzStarts.indexOfLast {it<=first}+1}
        fun pageOfJuz(juz: Int)=pageOf(juzStarts[juz.coerceIn(1,30)-1])
        fun pageOf(verseIndex: Int)=pageStarts.indexOfLast {it<=verseIndex}+1
        /** Verse 1 of every surah but Al-Fatiha and At-Tawbah starts with the basmalah in the text; the reader shows it as a heading. */
        fun displayText(v: Verse): String =
            if(v.aya==1 && v.sura!=1 && v.sura!=9) v.text.split(' ').drop(4).joinToString(" ") else v.text
    }

    @Volatile private var cache: Data?=null

    @Synchronized fun load(context: Context): Data {
        cache?.let {return it}
        val verses=ArrayList<Verse>(6236)
        context.assets.open("quran/quran-uthmani.txt").bufferedReader().useLines {lines->
            lines.forEach {line->
                if(line.isEmpty() || line.startsWith("#"))return@forEach
                val a=line.indexOf('|');val b=line.indexOf('|',a+1)
                verses+=Verse(line.substring(0,a).toInt(),line.substring(a+1,b).toInt(),line.substring(b+1))
            }
        }
        val rows=context.assets.open("quran/index.txt").bufferedReader().useLines {lines->
            lines.filterNot {it.startsWith("#") || it.isBlank()}.map {it.split('|')}.toList()
        }
        val surahRows=rows.filter {it[0]=="S"}
        val starts=surahRows.map {it[2].toInt()}
        val pages=IntArray(PAGES);val juz=IntArray(30)
        // Page and juz starts as indexes into the verse list.
        rows.filter {it[0]=="P" || it[0]=="J"}.forEach {f->
            val index=starts[f[2].toInt()-1]+f[3].toInt()-1
            if(f[0]=="P")pages[f[1].toInt()-1]=index else juz[f[1].toInt()-1]=index
        }
        val surahs=surahRows.map {f->
            val start=f[2].toInt()
            Surah(f[1].toInt(),start,f[3].toInt(),f[4],f[5],f[6]=="Meccan",pages.indexOfLast {it<=start}+1)
        }
        require(verses.size==6236 && surahs.size==114) {"Incomplete mushaf data"}
        return Data(verses,surahs,pages,juz).also {cache=it}
    }

    /** Arabic-Indic digits for verse numbers. */
    fun arabicDigits(n: Int)=n.toString().map {if(it.isDigit()) '٠'+(it-'0') else it}.joinToString("")
}
