package org.najwa.app.platform

import android.content.Context

/** Verse bookmarks and highlights, by verse index into the whole text (private, included in the backup). */
object AyahMarks {
    enum class Kind {BOOKMARK,HIGHLIGHT}
    private fun prefs(context: Context)=context.getSharedPreferences("reading",Context.MODE_PRIVATE)
    private fun read(context: Context,kind: Kind)=prefs(context).getStringSet("marks_${kind.name}",emptySet()).orEmpty().mapNotNull {it.toIntOrNull()}.toSet()
    fun bookmarks(context: Context)=read(context,Kind.BOOKMARK)
    fun highlights(context: Context)=read(context,Kind.HIGHLIGHT)
    fun toggle(context: Context,kind: Kind,verse: Int) {
        val now=read(context,kind)
        val next=if(verse in now) now-verse else now+verse
        prefs(context).edit().putStringSet("marks_${kind.name}",next.map {it.toString()}.toSet()).apply()
    }
}

/**
 * Search in the mushaf text without vowel marks: pause marks, tatweel and diacritics are ignored and the forms of
 * alif (and hamza seats) are treated as one, so "الرحمن" finds "ٱلرَّحْمَٰنِ".
 */
object MushafSearch {
    data class Hit(val verse: Int,val sura: Int,val aya: Int,val page: Int)

    fun normalize(text: String): String=buildString(text.length) {
        text.forEach {ch->
            when {
                ch in 'ً'..'ٟ' || ch=='ٰ' || ch in 'ۖ'..'ۭ' || ch in 'ࣰ'..'ࣲ' || ch=='ـ' -> {}
                ch=='ٱ' || ch=='أ' || ch=='إ' || ch=='آ' -> append('ا')
                ch=='ؤ' -> append('و')
                ch=='ئ' || ch=='ى' -> append('ي')
                ch=='ة' -> append('ه')
                else -> append(ch)
            }
        }
    }.replace(Regex("\\s+")," ").trim()

    @Volatile private var index: List<String>?=null

    fun search(d: Mushaf.Data,query: String,limit: Int=200): List<Hit> {
        val q=normalize(query)
        if(q.length<2)return emptyList()
        val texts=index ?: d.verses.map {normalize(d.displayText(it))}.also {index=it}
        val hits=ArrayList<Hit>()
        for(i in texts.indices) {
            if(texts[i].contains(q)) {
                val v=d.verses[i]
                hits+=Hit(i,v.sura,v.aya,d.pageOf(i))
                if(hits.size>=limit)break
            }
        }
        return hits
    }
}
