package org.najwa.app

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.najwa.app.platform.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The printed-page layout, kashida justification, the khatma plan and today's hasanat target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=NajwaApplication::class,shadows=[WindowsAtomicFile::class])
class MushafReaderPlatformTest {
    private lateinit var app: NajwaApplication
    private val today=LocalDate.now()
    @Before fun prepare() {
        app=RuntimeEnvironment.getApplication() as NajwaApplication
        WorshipStore.clear(app)
        WorshipSettingsStore.write(app,WorshipSettings())
        app.getSharedPreferences("quran",0).edit().clear().commit()
    }

    /** Widths proportional to the letters, enough to exercise the layout without a font. */
    private val measure=object: MushafLayout.Measure {
        override val space=3f
        override fun width(token: MushafLayout.Token)=token.text.count {it !in 'ً'..'ٟ' && it!='ٰ'}*10f+if(token.aya!=null) 12f else 0f
    }

    @Test fun pauseMarksAndTheHizbSignStayWithTheirWords() {
        val t=MushafLayout.tokens("۞ وَإِذْ قَالَ رَبُّكَ ۛ لِلْمَلَٰٓئِكَةِ",30)
        assertEquals(listOf("۞ وَإِذْ","قَالَ","رَبُّكَ ۛ","لِلْمَلَٰٓئِكَةِ"),t.map {it.text})
        assertEquals(listOf(null,null,null,30),t.map {it.aya})
    }

    @Test fun everyPageFillsFifteenLinesAndKeepsAllItsWords() {
        val d=Mushaf.load(app)
        for(page in 1..Mushaf.PAGES) {
            val p=MushafLayout.layout(d,page,600f,1.4f,measure)
            val words=d.page(page).sumOf {MushafLayout.tokens(d.displayText(it),it.aya).size}
            assertEquals("words on page $page",words,p.rows.filterIsInstance<MushafLayout.Line>().sumOf {it.tokens.size})
            if(page>2) assertEquals("rows on page $page",MushafLayout.LINES,p.rows.size)
            // No line is wider than the block at the chosen size.
            p.rows.filterIsInstance<MushafLayout.Line>().forEach {line->
                val w=line.tokens.sumOf {measure.width(it).toDouble()}+measure.space*(line.tokens.size-1)
                assertTrue("line too wide on page $page",w*p.scale<=600.5)
            }
        }
    }

    @Test fun headingsAndBasmalahFollowThePrint() {
        val d=Mushaf.load(app)
        val last=MushafLayout.layout(d,604,600f,1.4f,measure).rows
        assertEquals(listOf(112,113,114),last.filterIsInstance<MushafLayout.Heading>().map {it.surah})
        assertEquals(3,last.count {it==MushafLayout.Basmalah})
        // At-Tawbah has no basmalah; Al-Fatiha's basmalah is its first verse.
        val tawbah=MushafLayout.layout(d,d.surahs[8].page,600f,1.4f,measure).rows
        val i=tawbah.indexOfFirst {it is MushafLayout.Heading && it.surah==9}
        assertTrue(tawbah[i+1] is MushafLayout.Line)
        assertFalse(MushafLayout.layout(d,1,600f,1.4f,measure).rows.contains(MushafLayout.Basmalah))
    }

    @Test fun linesAreBalancedAndRespectTheWidth() {
        val ranges=MushafLayout.breakLines(List(20) {10f},2f,4,70f)!!
        assertEquals(4,ranges.size)
        assertEquals(listOf(5,5,5,5),ranges.map {it.second-it.first})
        assertNull(MushafLayout.breakLines(List(20) {10f},2f,2,70f))
        assertEquals(listOf(1,1,1),MushafLayout.allocate(listOf(1f,1f,1f),listOf(5,5,5),3))
        assertEquals(9,MushafLayout.allocate(listOf(40f,120f,130f),listOf(10,30,30),9).sum())
    }

    @Test fun kashidaAvoidsTheNameOfAllahLamAlefAndShortWords() {
        assertTrue(MushafLayout.kashidaPoints("ٱللَّهِ").isEmpty())
        assertTrue(MushafLayout.kashidaPoints("لِلَّهِ").isEmpty())
        assertTrue(MushafLayout.kashidaPoints("مِن").isEmpty())
        // Lam followed by alef (here hamza on alef) is a ligature and is never split.
        val ard="ٱلْأَرْضِ"
        assertTrue(MushafLayout.kashidaPoints(ard).none {ard[it]=='أ'})
        assertTrue(MushafLayout.kashidaPoints("ٱلرَّحِيمِ").isNotEmpty())
    }

    @Test fun justifiedLinesSpanTheWholeWidth() {
        val tokens=MushafLayout.tokens("قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ",1)
        val j=MushafLayout.justify(tokens,400f,6f,8f,{if(it.aya!=null)20f else 0f}) {s->s.length*9f}
        val total=j.widths.sum()+j.gap*(tokens.size-1)
        assertEquals(400f,total,.5f)
        assertTrue("tatweel added",j.texts.any {'ـ' in it})
        assertTrue(j.texts.none {it.contains("لـا")})
    }

    @Test fun khatmaPlanGivesPagesPerDayAndAFinishDate() {
        assertEquals(21,QuranStore.pagesFor(30))
        assertEquals(87,QuranStore.pagesFor(7))
        QuranStore.setGoal(app,87)
        assertEquals(87,QuranStore.read(app).goal)
        assertEquals(today.plusDays(6),QuranStore.finishDate(app,today))
        QuranStore.readPage(app,1,today)
        assertEquals(1,QuranStore.wird(app,today).read)
        assertTrue(QuranStore.autoRecord(app))
    }

    @Test fun targetHasanatIncludeTheRestOfTodaysPortion() {
        WorshipSettingsStore.write(app,WorshipSettings(preview=true))
        QuranStore.setGoal(app,4)
        QuranStore.readPage(app,1,today)
        val s=WorshipSummary.build(app,WorshipSettingsStore.read(app),today)
        if(!s.preview)return  // Release-like builds never count unreviewed rules.
        val letters=QuranPageLetters.letters(app,1,4)!!
        assertEquals(letters,s.wirdLetters)
        assertEquals(s.virtues.hasanat!!-(s.letters!!*10)+letters*10,s.hasanatTarget)
        assertTrue(s.hasanatTarget!!>=s.virtues.hasanat!!)
    }

    @Test fun releaseSummaryHasNoTarget() {
        WorshipSettingsStore.write(app,WorshipSettings(preview=false))
        QuranStore.readPage(app,1,today)
        val s=WorshipSummary.build(app,WorshipSettingsStore.read(app),today)
        assertNull(s.virtues.hasanat)
        assertNull(s.hasanatTarget)
        assertNotNull(s.wirdLetters)
    }
}
