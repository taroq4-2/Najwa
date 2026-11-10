package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.najwa.app.platform.PrayerWidget
import org.najwa.core.*
import java.time.Instant

@Composable fun WidgetScreen(p: Preferences,now: Instant,onBack: ()->Unit,onSave: (Preferences)->Unit) {
    val context=LocalContext.current
    var message by remember {mutableStateOf<String?>(null)}
    WidgetContent(p,now,onBack,message,onSave=onSave) {
        message=if(PrayerWidget.requestPin(context))"أكمل إضافة الويدجت من نافذة الشاشة الرئيسية."
        else "اضغط مطولًا على مكان فارغ في الشاشة الرئيسية ← الأدوات / الويدجت ← نجوى، ثم اسحب الويدجت إلى الشاشة."
    }
}
@Composable fun WidgetContent(p: Preferences,now: Instant,onBack: ()->Unit,message: String?=null,onSave: (Preferences)->Unit={},onAdd: ()->Unit) {
    val c=LocalPalette.current
    TextButton(onClick=onBack) {Text(uiText("الرجوع إلى الإعدادات"))}
    PageHeading("ويدجت نجوى","مواقيتك أمامك على الشاشة الرئيسية")
    var previewSize by rememberSaveable {mutableIntStateOf(1)}
    var background by rememberSaveable {mutableIntStateOf(0)}
    var opacity by remember(p.widgetOpacity) {mutableFloatStateOf(p.widgetOpacity.toFloat())}
    SettingsCard("المظهر والشفافية","طبقة خفيفة فوق خلفيتك، مع بقاء العداد واضحًا") {
        OptionGrid(WidgetTheme.entries.toList(),p.widgetTheme,{it.label}) {onSave(p.copy(widgetTheme=it))}
        Text(uiText("التلقائي يختار لونًا مناسبًا لإضاءة خلفية الهاتف عند توفرها، وإلا يتبع سمة النظام."),color=c.secondary,fontSize=12.sp)
        Text(uiText("شفافية الخلفية: ${100-opacity.toInt()}٪"),color=c.text,fontSize=14.sp)
        Slider(value=opacity,onValueChange={opacity=it},onValueChangeFinished={onSave(p.copy(widgetOpacity=opacity.toInt()))},valueRange=80f..100f,steps=3,modifier=Modifier.semantics {contentDescription="شفافية خلفية الويدجت";stateDescription="شفافية ${100-opacity.toInt()} بالمئة"})
        Text(uiText("شفافية خفيفة للخلفية فقط، ضمن نطاق يحافظ على تباين النص فوق الخلفيات الفاتحة والداكنة."),color=c.secondary,fontSize=12.sp)
    }
    SectionLabel("معاينة مظهر الويدجت")
    OptionGrid(listOf(0,1,2),background,{listOf("ليلية","فاتحة","ملونة")[it]}) {background=it}
    Spacer(Modifier.height(12.dp))
    val sizes=listOf(110 to 100,180 to 210,280 to 110,180 to 300,360 to 240,110 to 280)
    OptionGrid(sizes.indices.toList(),previewSize.coerceIn(sizes.indices),{listOf("مصغّر","صغير","عريض","طولي","كبير","طولي نحيف")[it]},3) {previewSize=it}
    Spacer(Modifier.height(16.dp))
    val (width,height)=sizes[previewSize.coerceIn(sizes.indices)]
    val wallpapers=listOf(listOf(Color(0xFF081629),Color(0xFF38546C)),listOf(Color(0xFFEAD5AA),Color(0xFFF9F4E9)),listOf(Color(0xFF735DA5),Color(0xFFD3978F),Color(0xFF537E87)))
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(wallpapers[background])).padding(12.dp),contentAlignment=Alignment.Center) {
        val actualWidth=minOf(width,maxWidth.value.toInt())
        val context=LocalContext.current
        val previewTheme=if(p.widgetTheme!=WidgetTheme.AUTO)p.widgetTheme
            else if(background==1)WidgetTheme.LIGHT else WidgetTheme.DARK
        val preview=p.copy(widgetOpacity=opacity.toInt(),widgetTheme=previewTheme)
        val remote=remember(preview,now.epochSecond/60,actualWidth,height) {
            PrayerWidget.views(context,preview,now,actualWidth,height)
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory={android.widget.FrameLayout(it)},
            modifier=Modifier.width(actualWidth.dp).height(height.dp),
            update={container->container.removeAllViews();container.addView(remote.apply(context,container))})
    }
    Text(uiText("معاينة الويدجت الفعلي على خلفية تجريبية · يتكيف عند سحب أطرافه"),color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(top=12.dp))
    Spacer(Modifier.height(18.dp))
    Action("إضافة إلى الشاشة الرئيسية",Modifier.fillMaxWidth(),true,onAdd)
    message?.let {Text(it,color=c.mint,modifier=Modifier.padding(vertical=12.dp))}
    SettingsCard("طريقة الإضافة") {
        Text(uiText("اضغط زر الإضافة ووافق في نافذة الهاتف. إذا لم يدعم مشغّل الشاشة هذه الطريقة، اضغط مطولًا على الشاشة الرئيسية ثم اختر الأدوات وابحث عن نجوى."),color=c.secondary)
        Text(uiText("الويدجت يتبع المدينة وطريقة الحساب في التطبيق. اضغط عليه لفتح نجوى، أو على ↻ للتحديث، واسحب أطرافه لتغيير الحجم."),color=c.secondary)
        Text(uiText("قد يؤخر الهاتف تحديث الصلاة التالية أثناء السكون. فتح الشاشة أو تحديث الويدجت يعيد عرض المواقيت؛ لا يغيّر ذلك جدولة الصمت وتنبيهات الصلاة."),color=c.secondary,fontSize=12.sp)
    }
}
