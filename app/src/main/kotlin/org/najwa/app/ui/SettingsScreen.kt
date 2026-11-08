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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import org.najwa.core.*
import org.najwa.app.BuildConfig
import org.najwa.app.platform.ArabCityCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable fun SettingsScreen(p: Preferences,onSave: (Preferences)->Unit,onCity: ()->Unit,onNotifications: ()->Unit = {},onWidget: ()->Unit = {},onInformation: ()->Unit = {},onCalculation: ()->Unit = {},onAppearance: ()->Unit = {},onSilence: ()->Unit = {}) {
    val c=LocalPalette.current
    PageHeading("الإعدادات","مساحة واحدة لكل ما يخص صلاتك")
    SectionLabel("الصلاة والهدوء")
    Panel(Modifier.fillMaxWidth()) {
        SettingsDestination("مواقيت الصلاة والمدينة","${p.city.name} · ${p.method.label}","pin",onCalculation)
        SettingsDivider()
        SettingsDestination("ضبط تنبيهات الصلاة","الأذان والإقامة والجمعة · الصوت والمدد","bell",onNotifications)
        SettingsDivider()
        SettingsDestination("الصمت أثناء الصلاة",if(p.autoSilent)"مفعّل · تخصيص المدة لكل صلاة" else "التفعيل والمدة واستعادة الرنين","sound",onSilence)
    }
    SectionLabel("تجربتك مع نجوى")
    Panel(Modifier.fillMaxWidth()) {
        SettingsDestination("المظهر والعرض","${p.theme.label} · ${if(p.hour24)"24 ساعة" else "12 ساعة"}","palette",onAppearance)
        SettingsDivider()
        SettingsDestination("ويدجت الشاشة الرئيسية","أحجام مرنة · مواقيتك بنظرة واحدة","widget",onWidget)
    }
    SectionLabel("المساعدة والمعلومات")
    Panel(Modifier.fillMaxWidth()) {SettingsDestination("معلومات التطبيق","من نحن · تواصل معنا · الخصوصية","shield",onInformation)}
    Text("نجوى · ${BuildConfig.VERSION_NAME}",color=c.secondary,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=24.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable fun CalculationScreen(p: Preferences,onSave: (Preferences)->Unit,onCity: ()->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    var advanced by rememberSaveable {mutableStateOf(false)}
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading("مواقيت الصلاة والمدينة","اضبط الموقع والحساب في مكان واحد")
    SettingsCard("المدينة","تُحسب المواقيت حسب المدينة والمنطقة الزمنية المختارتين") {
        Action("${p.city.name}  ·  تغيير المدينة",Modifier.fillMaxWidth(),onClick=onCity)
        Text(p.city.zone,color=c.secondary,fontSize=12.sp)
    }
    SettingsCard("مواقيت الصلاة") {
        Text("طريقة الحساب",color=c.secondary,fontSize=13.sp)
        Choice(p.method,Method.entries.toList(),{it.label}) {onSave(p.copy(method=it))}
        if(p.method==Method.UMM_AL_QURA)Text("العشاء بعد الغروب بـ90 دقيقة، و120 دقيقة في رمضان وفق التقويم الهجري المستخدم.",color=c.secondary,fontSize=12.sp)
        if(p.method==Method.CUSTOM) {
            var fajr by remember(p.customFajr) {mutableStateOf(p.customFajr.toString())}
            var isha by remember(p.customIsha) {mutableStateOf(p.customIsha.toString())}
            var invalid by remember {mutableStateOf(false)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fajr,{fajr=it;invalid=false},label={Text("زاوية الفجر")},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=invalid)
                OutlinedTextField(isha,{isha=it;invalid=false},label={Text("زاوية العشاء")},modifier=Modifier.weight(1f),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=invalid)
            }
            if(invalid)Text("أدخل زاويتين بين 1 و30 درجة.",color=c.amber,fontSize=13.sp)
            Action("حفظ الزوايا",Modifier.fillMaxWidth()) {
                val f=normalizedNumber(fajr).toDoubleOrNull();val i=normalizedNumber(isha).toDoubleOrNull()
                invalid=f==null||i==null||f !in 1.0..30.0||i !in 1.0..30.0
                if(!invalid)onSave(p.copy(customFajr=f!!,customIsha=i!!))
            }
        }
        Text("حساب العصر",color=c.secondary,fontSize=13.sp)
        Choice(p.hanafi,listOf(false,true),{if(it)"حنفي · مثلي الظل" else "قياسي · مثل الظل"}) {onSave(p.copy(hanafi=it))}
        TextButton(onClick={advanced=!advanced},contentPadding=PaddingValues(0.dp)) {Text(if(advanced)"إخفاء الخيارات المتقدمة" else "خيارات الحساب المتقدمة")}
        if(advanced) {
            Text("تقدير الشفق في خطوط العرض العالية",color=c.secondary,fontSize=13.sp)
            Choice(p.highLatitude,HighLatitude.entries.toList(),{it.label}) {onSave(p.copy(highLatitude=it))}
            Text("عند غياب الشروق أو الغروب، يعرض نجوى تعذّر الحساب ويوقف جدولة الصمت.",color=c.secondary,fontSize=12.sp)
        }
    }
}

@Composable fun AppearanceScreen(p: Preferences,onSave: (Preferences)->Unit,onBack: ()->Unit) {
    val c=LocalPalette.current
    BackLink("الرجوع إلى الإعدادات",onBack)
    PageHeading("المظهر والعرض","تفاصيل صغيرة لقراءة أكثر راحة")
    SettingsCard("مظهر التطبيق","اختر سمة ثابتة أو اتبع إعدادات هاتفك") {
        OptionGrid(ThemeChoice.entries.toList(),p.theme,{it.label}) {onSave(p.copy(theme=it))}
    }
    SettingsCard("صيغة الوقت") {OptionGrid(listOf(false,true),p.hour24,{if(it)"24 ساعة" else "12 ساعة"},2) {onSave(p.copy(hour24=it))}}
    SettingsCard("العد التنازلي") {
        PreferenceSwitch("عداد الإقامة","بعد الأذان، يعرض المتبقي على الإقامة في الرئيسية والويدجت",p.showIqamaCountdown) {onSave(p.copy(showIqamaCountdown=it))}
        Text("أخضر حتى الأذان، وذهبي أثناء انتظار الإقامة.",color=c.secondary,fontSize=13.sp)
    }
}

@Composable fun CityDialog(current: City,onDismiss: ()->Unit,onSelect: (City)->Unit) {
    val context=LocalContext.current
    var manual by rememberSaveable {mutableStateOf(false)}
    var query by rememberSaveable {mutableStateOf("")}
    var countryCode by rememberSaveable {mutableStateOf(ArabCityCatalog.forZone(current.zone).code)}
    var countryMenu by remember {mutableStateOf(false)}
    val country=ArabCityCatalog.countries.first {it.code==countryCode}
    val catalog by key(countryCode) {
        produceState<Result<List<ArabCityCatalog.Place>>?>(null,context) {
            value=withContext(Dispatchers.IO) {runCatching {ArabCityCatalog.load(context,country)}}
        }
    }
    val cities by key(countryCode) {
        produceState<List<ArabCityCatalog.Place>?>(null,catalog,query) {
            val places=catalog?.getOrNull() ?: return@produceState
            val term=citySearchKey(query)
            if(term.isEmpty())value=places else {
                delay(160)
                value=withContext(Dispatchers.Default) {places.filter {term in it.terms}}
            }
        }
    }
    var name by rememberSaveable {mutableStateOf(current.name)}
    var lat by rememberSaveable {mutableStateOf(current.latitude.toString())}
    var lon by rememberSaveable {mutableStateOf(current.longitude.toString())}
    var zone by rememberSaveable {mutableStateOf(current.zone)}
    var error by rememberSaveable {mutableStateOf(false)}
    val c=LocalPalette.current
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(manual)"موقع مخصص" else "اختر مدينتك")},text={
        Column(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(!manual) {
                Box {
                    OutlinedButton(onClick={countryMenu=true},modifier=Modifier.fillMaxWidth()) {Text("الدولة · ${country.name}")}
                    DropdownMenu(expanded=countryMenu,onDismissRequest={countryMenu=false},modifier=Modifier.heightIn(max=320.dp)) {
                        ArabCityCatalog.countries.forEach { choice ->
                            DropdownMenuItem(text={Text(choice.name)},onClick={countryCode=choice.code;query="";countryMenu=false})
                        }
                    }
                }
                OutlinedTextField(query,{query=it},placeholder={Text("ابحث بالعربية أو الإنجليزية")},singleLine=true,modifier=Modifier.fillMaxWidth())
                val matches=cities
                val listState=rememberLazyListState()
                LaunchedEffect(matches) {if(!matches.isNullOrEmpty())listState.scrollToItem(0)}
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state=listState,modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        items(matches.orEmpty(),key={it.id}) { place ->
                            val selected=place.name==current.name && place.lat==current.latitude && place.lon==current.longitude
                            val label=if(place.region.isBlank() || place.name==place.region)place.name else "${place.name} · ${place.region}"
                            OptionButton(label,selected,Modifier.fillMaxWidth()) {onSelect(place.city())}
                        }
                    }
                    when {
                        catalog?.isFailure==true -> Text("تعذّر تحميل قائمة المدن. يمكنك إدخال موقع مخصص.",color=c.amber)
                        matches==null -> Text("جارٍ تحميل المدن…",color=c.secondary)
                        matches.isEmpty() -> Text("لم نجد المدينة. جرّب اسمًا آخر أو أدخل موقعًا مخصصًا.",color=c.secondary)
                    }
                }
                TextButton(onClick={manual=true}) {Text("مدينتي غير موجودة · إدخال يدوي")}
            } else Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it;error=false},label={Text("اسم الموقع")},singleLine=true)
                OutlinedTextField(lat,{lat=it;error=false},label={Text("خط العرض · −90 إلى 90")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(lon,{lon=it;error=false},label={Text("خط الطول · −180 إلى 180")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(zone,{zone=it;error=false},label={Text("المنطقة الزمنية")},supportingText={Text("مثال: Asia/Riyadh")},singleLine=true)
                if(error)Text("تحقق من الاسم والإحداثيات والمنطقة الزمنية.",color=c.amber)
                TextButton(onClick={manual=false}) {Text("العودة إلى قائمة المدن")}
            }
        }
    },confirmButton={if(manual)TextButton(onClick={
        val city=runCatching {require(name.isNotBlank());City(name.trim(),normalizedNumber(lat).toDouble(),normalizedNumber(lon).toDouble(),zone.trim())}.getOrNull()
        error=city==null;if(city!=null)onSelect(city)
    }) {Text("حفظ الموقع")}},dismissButton={TextButton(onClick=onDismiss) {Text("إغلاق")}})
}
