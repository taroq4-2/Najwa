package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import org.najwa.core.*
import org.najwa.app.BuildConfig
import org.najwa.app.MainActivity
import org.najwa.app.appLanguage
import org.najwa.app.changeAppLanguage
import org.najwa.app.platform.CityCatalog
import org.najwa.app.platform.CatalogCountry
import org.najwa.app.supportedLanguages
import org.najwa.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun CalculationScreen(p: Preferences,onSave: (Preferences)->Unit,onCity: ()->Unit,onBack: ()->Unit,onSecondCity: ()->Unit={}) {
    val c=LocalPalette.current
    var advanced by rememberSaveable {mutableStateOf(false)}
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading("مواقيت الصلاة والمدينة","اضبط الموقع والحساب في مكان واحد")
    SettingsCard("المدينة","تُحسب المواقيت حسب المدينة والمنطقة الزمنية المختارتين") {
        Action("${p.city.name}  ·  ${uiText("تغيير المدينة")}",Modifier.fillMaxWidth(),onClick=onCity)
        Text(p.city.zone,color=c.secondary,fontSize=12.sp)
    }
    SecondCityCard(p,onSave,onSecondCity)
    SettingsCard("مواقيت الصلاة") {
        Text(uiText("طريقة الحساب"),color=c.secondary,fontSize=13.sp)
        val methods=Method.entries.associateWith {localizedMethodName(it)}
        Choice(p.method,Method.entries.toList(),methods::getValue) {onSave(p.copy(method=it))}
        suggestedMethod(p.city.countryCode)?.takeIf {it!=p.method}?.let {suggested->
            Row(Modifier.fillMaxWidth().background(c.selected,androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(horizontal=12.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.method_suggested,methods.getValue(suggested)),Modifier.weight(1f),color=c.text,fontSize=13.sp)
                TextButton(onClick={onSave(p.copy(method=suggested))}) {Text(stringResource(R.string.method_use))}
            }
        }
        if(p.method==Method.UMM_AL_QURA)Text(uiText("العشاء بعد الغروب بـ90 دقيقة، و120 دقيقة في رمضان وفق التقويم الهجري المستخدم."),color=c.secondary,fontSize=12.sp)
        if(p.method==Method.CUSTOM) {
            var fajr by remember(p.customFajr) {mutableStateOf(p.customFajr.toString())}
            var isha by remember(p.customIsha) {mutableStateOf(p.customIsha.toString())}
            var invalid by remember {mutableStateOf(false)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fajr,{fajr=it;invalid=false},label={Text(uiText("زاوية الفجر"))},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=invalid)
                OutlinedTextField(isha,{isha=it;invalid=false},label={Text(uiText("زاوية العشاء"))},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=invalid)
            }
            if(invalid)Text(uiText("أدخل زاويتين بين 1 و30 درجة."),color=c.amber,fontSize=13.sp)
            Action("حفظ الزوايا",Modifier.fillMaxWidth()) {
                val f=normalizedNumber(fajr).toDoubleOrNull();val i=normalizedNumber(isha).toDoubleOrNull()
                invalid=f==null||i==null||f !in 1.0..30.0||i !in 1.0..30.0
                if(!invalid)onSave(p.copy(customFajr=f!!,customIsha=i!!))
            }
        }
        val methodNote=when(p.method) {
            Method.QATAR -> R.string.method_note_interval
            Method.DUBAI,Method.TURKEY,Method.SINGAPORE -> R.string.method_note_adjusted
            else -> null
        }
        methodNote?.let {Text(stringResource(it),color=c.secondary,fontSize=12.sp)}
        Text(uiText("حساب العصر"),color=c.secondary,fontSize=13.sp)
        Choice(p.hanafi,listOf(false,true),{if(it)"حنفي · مثلي الظل" else "قياسي · مثل الظل"}) {onSave(p.copy(hanafi=it))}
        TextButton(onClick={advanced=!advanced},contentPadding=PaddingValues(0.dp)) {Text(uiText(if(advanced)"إخفاء الخيارات المتقدمة" else "خيارات الحساب المتقدمة"))}
        if(advanced) {
            Text(uiText("تقدير الشفق في خطوط العرض العالية"),color=c.secondary,fontSize=13.sp)
            Choice(p.highLatitude,HighLatitude.entries.toList(),{it.label}) {onSave(p.copy(highLatitude=it))}
            Text(uiText("عند غياب الشروق أو الغروب، يعرض نجوى تعذّر الحساب ويوقف جدولة الصمت."),color=c.secondary,fontSize=12.sp)
            Text(stringResource(R.string.elevation_title),color=c.secondary,fontSize=13.sp)
            val ctx=LocalContext.current
            OptionGrid((listOf(0,500,1000,2000)+p.elevation).distinct().sorted(),p.elevation,{if(it==0)ctx.getString(R.string.elevation_sea) else ctx.getString(R.string.elevation_meters,it)},4) {onSave(p.copy(elevation=it))}
            Text(stringResource(R.string.elevation_help),color=c.secondary,fontSize=12.sp)
        }
    }
    MinuteOffsets(p,onSave)
    SettingsCard(stringResource(R.string.hijri_title),stringResource(R.string.hijri_help)) {
        val ctx=LocalContext.current
        OptionGrid((-2..2).toList(),p.hijriOffset,{if(it==0)ctx.getString(R.string.hijri_none) else ctx.getString(R.string.days_signed,if(it>0)"‎+$it" else "‎$it")},5) {
            onSave(p.copy(hijriOffset=it))
        }
        Text(localizedUiHijriDate(java.time.LocalDate.now(p.city.zoneId),p.hijriOffset),color=c.mint,fontSize=14.sp)
    }
}

/** Per-prayer minute corrections, to match the local mosque or official calendar. */
@Composable fun MinuteOffsets(p: Preferences,onSave: (Preferences)->Unit) {
    val c=LocalPalette.current
    val changed=p.offsets.count {it.value!=0}
    ExpandableSettings(stringResource(R.string.offsets_title),
        if(changed==0)stringResource(R.string.offsets_none) else pluralStringResource(R.plurals.offsets_changed,changed,changed)) {
        Text(stringResource(R.string.offsets_help),color=c.secondary,fontSize=13.sp)
        Prayer.entries.forEach {prayer->
            val minutes=p.offset(prayer)
            fun set(value: Int) {
                val clamped=value.coerceIn(-MAX_OFFSET_MINUTES,MAX_OFFSET_MINUTES)
                onSave(p.copy(offsets=(p.offsets+(prayer to clamped)).filterValues {it!=0}))
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(localizedPrayerName(prayer),Modifier.weight(1f),color=c.text,fontSize=15.sp)
                val name=localizedPrayerName(prayer)
                val buttonBorder=androidx.compose.foundation.BorderStroke(1.dp,c.border)
                val buttonColors=IconButtonDefaults.outlinedIconButtonColors(contentColor=c.mint)
                OutlinedIconButton(onClick={set(minutes-1)},enabled=minutes>-MAX_OFFSET_MINUTES,border=buttonBorder,colors=buttonColors,
                    modifier=Modifier.semantics {contentDescription="$name −1"}) {Text("−",fontSize=18.sp)}
                Text(if(minutes==0)"0" else stringResource(R.string.minutes_signed,if(minutes>0)"‎+$minutes" else "‎$minutes"),
                    Modifier.widthIn(min=64.dp),color=if(minutes==0)c.secondary else c.mint,fontSize=14.sp,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
                OutlinedIconButton(onClick={set(minutes+1)},enabled=minutes<MAX_OFFSET_MINUTES,border=buttonBorder,colors=buttonColors,
                    modifier=Modifier.semantics {contentDescription="$name +1"}) {Text("+",fontSize=18.sp)}
            }
        }
        if(changed>0)TextButton(onClick={onSave(p.copy(offsets=emptyMap()))}) {Text(stringResource(R.string.offsets_reset))}
    }
}

@Composable fun AppearanceScreen(p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit,onWidget: ()->Unit={}) {
    val c=LocalPalette.current
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading(stringResource(R.string.section_display),stringResource(R.string.display_intro))
    var languageDialog by rememberSaveable {mutableStateOf(false)}
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    SettingsCard("مظهر التطبيق","اختر سمة ثابتة أو اتبع إعدادات هاتفك") {
        OptionGrid(ThemeChoice.entries.toList(),p.theme,{it.label}) {onSave(p.copy(theme=it))}
    }
    SettingsCard("صيغة الوقت") {OptionGrid(listOf(false,true),p.hour24,{if(it)"24 ساعة" else "12 ساعة"},2) {onSave(p.copy(hour24=it))}}
    if(android.os.Build.VERSION.SDK_INT>=31)SettingsCard(stringResource(R.string.dynamic_color_title)) {
        PreferenceSwitch(stringResource(R.string.dynamic_color_switch),stringResource(R.string.dynamic_color_help),p.dynamicColor) {onSave(p.copy(dynamicColor=it))}
    }
    SettingsCard(stringResource(R.string.large_display_title)) {
        PreferenceSwitch(stringResource(R.string.large_display),stringResource(R.string.large_display_help),p.largeDisplay) {onSave(p.copy(largeDisplay=it))}
    }
    Panel(Modifier.fillMaxWidth()) {
        if(activity!=null) {
            SettingsDestination(stringResource(R.string.app_language_label),supportedLanguages.getValue(appLanguage(activity)),"globe") {languageDialog=true}
            SettingsDivider()
        }
        SettingsDestination(uiText("ويدجت الشاشة الرئيسية"),stringResource(R.string.widget_summary),"widget",onWidget)
    }
    if(languageDialog && activity!=null)LanguageDialog(activity) {languageDialog=false}
}

@Composable fun CityDialog(current: City,onDismiss: ()->Unit,onSelect: (City)->Unit) {
    val context=LocalContext.current
    var manual by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    val countries=remember(context) {CityCatalog.countries(context)}
    var countryCode by rememberSaveable {mutableStateOf(CityCatalog.forCity(context,current,countries).code)}
    var countryMenu by rememberSaveable {mutableStateOf(false)}
    var countryQuery by rememberSaveable {mutableStateOf("")}
    val country=countries.first {it.code==countryCode}
    val term=citySearchKey(query)
    val cities by key(countryCode) {
        produceState<Pair<String,Result<List<CityCatalog.Place>>>?>(null,context,term,manual,countryMenu) {
            if(manual || countryMenu)return@produceState
            if(term.isNotEmpty())delay(250)
            val result=withContext(Dispatchers.IO) {
                try { Result.success(CityCatalog.search(context,country,term)) }
                catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { Result.failure(e) }
            }
            value=term to result
        }
    }
    var name by rememberSaveable {mutableStateOf(current.name)}
    var lat by rememberSaveable {mutableStateOf(current.latitude.toString())}
    var lon by rememberSaveable {mutableStateOf(current.longitude.toString())}
    var zone by rememberSaveable {mutableStateOf(current.zone)}
    var error by rememberSaveable {mutableStateOf(false)}
    var locateError by rememberSaveable {mutableStateOf<Int?>(null)}
    val (locating,locate)=rememberCityLocator(onCity={locateError=null;onSelect(it)},onError={locateError=it})
    val c=LocalPalette.current
    val locale=LocalConfiguration.current.locales[0]
    fun countryName(choice: CatalogCountry): String =
        choice.arabicName?.takeIf {locale.language=="ar"}
            ?: Locale.Builder().setRegion(choice.code).build().getDisplayCountry(locale)
    AlertDialog(onDismissRequest={if(countryMenu)countryMenu=false else onDismiss()},title={
        Text(if(countryMenu)stringResource(R.string.city_choose_country) else uiText(if(manual)"موقع مخصص" else "اختر مدينتك"))
    },text={
        Column(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(countryMenu) {
                val names=remember(countries,locale) {
                    val collator=Collator.getInstance(locale)
                    countries.map {it to countryName(it)}.sortedWith {a,b->collator.compare(a.second,b.second)}
                }
                val countryTerm=citySearchKey(countryQuery)
                val filtered=remember(names,countryTerm) {
                    names.filter {(choice,name)->
                        countryTerm in citySearchKey(name) || countryTerm in citySearchKey(choice.code) ||
                            countryTerm in citySearchKey(Locale.Builder().setRegion(choice.code).build().getDisplayCountry(Locale.ENGLISH))
                    }
                }
                OutlinedTextField(countryQuery,{countryQuery=it},placeholder={Text(stringResource(R.string.city_search_country))},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered,key={it.first.code}) { (choice,name) ->
                        OptionButton(name,choice.code==countryCode,Modifier.fillMaxWidth()) {
                            countryCode=choice.code
                            query=""
                            error=false
                            countryMenu=false
                        }
                    }
                }
            } else if(!manual) {
                Button(onClick={locateError=null;locate()},enabled=!locating,modifier=Modifier.fillMaxWidth()) {
                    Text(stringResource(if(locating)R.string.locate_busy else R.string.locate_button))
                }
                locateError?.let {Text(stringResource(it),color=c.amber,fontSize=13.sp)}
                OutlinedButton(onClick={countryQuery="";countryMenu=true},modifier=Modifier.fillMaxWidth()) {
                    Text("${uiText("الدولة")} · ${countryName(country)}")
                }
                OutlinedTextField(query,{query=it;error=false},placeholder={Text(stringResource(R.string.city_search_place))},singleLine=true,modifier=Modifier.fillMaxWidth())
                val results=cities?.takeIf {it.first==term}?.second
                val matches=results?.getOrNull()
                val listState=rememberLazyListState()
                LaunchedEffect(matches) {if(!matches.isNullOrEmpty())listState.scrollToItem(0)}
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        items(matches.orEmpty(),key={it.id}) { place ->
                            val selected=if(current.catalogId!=null) current.catalogId==place.id && current.countryCode==country.code
                                else place.name==current.name && place.lat==current.latitude && place.lon==current.longitude
                            val label=if(place.region.isBlank() || place.name==place.region)place.name else "${place.name} · ${place.region}"
                            OptionButton(label,selected,Modifier.fillMaxWidth()) {
                                if(cities?.first==citySearchKey(query) && countryCode==country.code) {
                                    val city=runCatching {place.city(country)}.getOrNull()
                                    error=city==null
                                    if(city!=null)onSelect(city)
                                }
                            }
                        }
                    }
                    when {
                        results?.isFailure==true -> Text(uiText("تعذّر تحميل قائمة المدن. يمكنك إدخال موقع مخصص."),color=c.amber)
                        matches==null -> Text(uiText("جارٍ تحميل المدن…"),color=c.secondary)
                        matches.isEmpty() -> Text(uiText("لم نجد المدينة. جرّب اسمًا آخر أو أدخل موقعًا مخصصًا."),color=c.secondary)
                    }
                }
                if(error)Text(stringResource(R.string.city_unavailable_zone),color=c.amber)
                if(matches?.size==100)Text(stringResource(R.string.city_search_limit),color=c.secondary,fontSize=12.sp)
                TextButton(onClick={manual=true;error=false}) {Text(uiText("مدينتي غير موجودة · إدخال يدوي"))}
            } else Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it;error=false},label={Text(uiText("اسم الموقع"))},singleLine=true)
                OutlinedTextField(lat,{lat=it;error=false},label={Text(uiText("خط العرض · −90 إلى 90"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(lon,{lon=it;error=false},label={Text(uiText("خط الطول · −180 إلى 180"))},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(zone,{zone=it;error=false},label={Text(uiText("المنطقة الزمنية"))},supportingText={Text(uiText("مثال: Asia/Riyadh"))},singleLine=true)
                if(error)Text(uiText("تحقق من الاسم والإحداثيات والمنطقة الزمنية."),color=c.amber)
                TextButton(onClick={manual=false}) {Text(uiText("العودة إلى قائمة المدن"))}
            }
        }
    },confirmButton={if(manual)TextButton(onClick={
        val city=runCatching {require(name.isNotBlank());City(name.trim(),normalizedNumber(lat).toDouble(),normalizedNumber(lon).toDouble(),zone.trim())}.getOrNull()
        error=city==null;if(city!=null)onSelect(city)
    }) {Text(uiText("حفظ الموقع"))}},dismissButton={TextButton(onClick={if(countryMenu)countryMenu=false else onDismiss()}) {Text(uiText(if(countryMenu)"رجوع" else "إغلاق"))}})
}
