package org.najwa.app.platform

import org.najwa.core.Prayer
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

private val hijriMonths=listOf("محرم","صفر","ربيع الأول","ربيع الآخر","جمادى الأولى","جمادى الآخرة",
    "رجب","شعبان","رمضان","شوال","ذو القعدة","ذو الحجة")

fun hijriLabel(date: LocalDate): String = try {
    val hijri=HijrahDate.from(date)
    val day=hijri.get(ChronoField.DAY_OF_MONTH)
    val month=hijri.get(ChronoField.MONTH_OF_YEAR)
    val year=hijri.get(ChronoField.YEAR)
    val text="$day ${hijriMonths[month-1]} $year هـ"
    text.map {if(it in '0'..'9') '٠'+(it-'0') else it}.joinToString("")
} catch(_: java.time.DateTimeException) { "التاريخ الهجري غير متاح" }

internal fun prayerMessage(prayer: Prayer, adhan: Boolean): String = if(adhan) when(prayer) {
    Prayer.FAJR -> "ابدأ يومك بصلاة الفجر."
    Prayer.DHUHR -> "توقّف قليلًا في منتصف يومك، فقد دخل وقت الظهر."
    Prayer.ASR -> "حان وقت العصر؛ اجعل للصلاة موعدًا في بقية يومك."
    Prayer.MAGHRIB -> "غربت الشمس ودخل وقت المغرب."
    Prayer.ISHA -> "حان وقت العشاء؛ اختم يومك بالصلاة."
    else -> ""
} else when(prayer) {
    Prayer.FAJR -> "«من صلى البردين دخل الجنة» · البخاري ٥٧٤. البردان: الفجر والعصر."
    Prayer.DHUHR -> "من معاني الحديث: فضل التبكير إلى صلاة الظهر · البخاري ٦١٥."
    Prayer.ASR -> "«من صلى البردين دخل الجنة» · البخاري ٥٧٤. البردان: الفجر والعصر."
    Prayer.MAGHRIB -> "من هدي النبي ﷺ صلاة ركعتين بعد المغرب في بيته · البخاري ١١٨٠."
    Prayer.ISHA -> "من صلى العشاء في جماعة فكأنما قام نصف الليل · مسلم ٦٥٦."
    else -> ""
}
