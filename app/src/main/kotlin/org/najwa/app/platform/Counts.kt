package org.najwa.app.platform

import android.content.Context
import org.najwa.app.NajwaApplication
import org.najwa.app.R
import org.najwa.app.appLanguage
import org.najwa.core.Digits
import java.text.NumberFormat
import java.util.Locale

/**
 * One way of writing counts across the worship pages, the day widget and the reminders, chosen in Appearance:
 * Arabic-Indic digits (١٢٣) or Western digits (123), by default following the app language.
 */
object Counts {
    private fun preferences(context: Context)=
        (context.applicationContext as? NajwaApplication)?.runtime?.preferences ?: PreferencesStore(context).read()

    fun language(context: Context): String=appLanguage(context)

    /** The last answer of [arabicDigits], for formatting where no context is at hand. */
    @Volatile var lastArabic=true
    fun arabicDigits(context: Context)=when(preferences(context).digits) {
        Digits.ARABIC -> true
        Digits.LATIN -> false
        Digits.AUTO -> language(context)=="ar"
    }.also {lastArabic=it}

    fun format(context: Context,n: Long): String=format(arabicDigits(context),n)
    /** Rewrites the digits of [text] (a date, a time, a count) in the chosen style. */
    fun digits(context: Context,text: String): String=digits(arabicDigits(context),text)
    fun digits(arabic: Boolean,text: String): String=buildString(text.length) {
        text.forEach {ch->append(when {
            arabic && ch in '0'..'9' -> '٠'+(ch-'0')
            !arabic && ch in '٠'..'٩' -> '0'+(ch-'٠')
            !arabic && ch in '۰'..'۹' -> '0'+(ch-'۰')
            else -> ch
        })}
    }
    fun format(arabic: Boolean,n: Long): String=
        NumberFormat.getIntegerInstance(if(arabic) Locale.forLanguageTag("ar-EG") else Locale.US).format(n)

    /**
     * "Pages" with the right number agreement: in Arabic صفحة واحدة، صفحتان، ٣ صفحات، ١١ صفحة;
     * other languages use their own string.
     */
    fun pages(context: Context,n: Int): String {
        val strings=context.prayerLanguage()
        if(language(context)!="ar")return strings.getString(if(n==1)R.string.count_page_one else R.string.day_unit_pages,format(context,n.toLong()))
        return arabicNoun(n,format(context,n.toLong()),"صفحة واحدة","صفحتان","صفحات","صفحة")
    }

    /** A number of adhkar: ذكر واحد، ذكران، ٣ أذكار، ١٣ ذكرًا. */
    fun adhkar(context: Context,n: Int): String {
        val strings=context.prayerLanguage()
        if(language(context)!="ar")return strings.getString(if(n==1)R.string.count_adhkar_one else R.string.count_adhkar,format(context,n.toLong()))
        return arabicNoun(n,format(context,n.toLong()),"ذكر واحد","ذكران","أذكار","ذكرًا")
    }

    /** Arabic number agreement: 0 takes the plural, 1 and 2 are written in words, 3–10 take the plural, 11 and above the singular. */
    fun arabicNoun(n: Int,digits: String,one: String,two: String,plural: String,singular: String)=when {
        n==0 -> "$digits $plural"
        n==1 -> one
        n==2 -> two
        n%100 in 3..10 -> "$digits $plural"
        else -> "$digits $singular"
    }
}
