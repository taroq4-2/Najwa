package org.najwa.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.*
import org.najwa.app.BuildConfig
import org.najwa.app.platform.*
import org.najwa.core.Screen

@Composable fun InformationScreen(screen: Screen,onNavigate: (Screen)->Unit,onBack: ()->Unit) {
    val context=LocalContext.current
    var message by remember {mutableStateOf<String?>(null)}
    InformationContent(screen,onNavigate,onBack,message,onEmail={category,device,details->
        if(!launchExternal(context,contactEmailIntent(category,device,details)))message="لم نجد تطبيق بريد. انسخ العنوان واستخدمه في خدمة البريد التي تفضلها."
    },onCopy={
        message=if(runCatching {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("بريد نجوى",CONTACT_EMAIL))}.isSuccess)"تم نسخ البريد الإلكتروني." else "تعذّر النسخ. البريد: $CONTACT_EMAIL"
    })
}

@Composable fun InformationContent(screen: Screen,onNavigate: (Screen)->Unit,onBack: ()->Unit,message: String?=null,onEmail: (String,String,String)->Unit={_,_,_->},onCopy: ()->Unit={}) {
    val c=LocalPalette.current
    BackLink(if(screen==Screen.INFORMATION)"الرجوع إلى الإعدادات" else "الرجوع إلى معلومات التطبيق",onBack)
    when(screen) {
        Screen.INFORMATION -> {
            PageHeading("معلومات التطبيق","تعرف على نجوى، وتواصل معنا، واطّلع على خصوصيتك")
            Panel(Modifier.fillMaxWidth()) {
                SettingsDestination("من نحن","عن نجوى وهوية التطبيق","home") {onNavigate(Screen.ABOUT)}
                SettingsDivider()
                SettingsDestination("تواصل معنا",CONTACT_EMAIL,"bell") {onNavigate(Screen.CONTACT)}
                SettingsDivider()
                SettingsDestination("سياسة الخصوصية","بياناتك محفوظة على جهازك","shield") {onNavigate(Screen.PRIVACY)}
            }
            Spacer(Modifier.height(16.dp))
            ExpandableSettings("دليل الاستخدام","خطوات البداية وضبط التنبيهات") {
                Text("اختر المدينة وطريقة الحساب من إعدادات المواقيت، وراجع الأوقات مع تقويم مدينتك. خصّص الإقامة من صفحة تنبيهات الصلاة، والصمت من تبويب الصمت.",color=c.secondary)
                Text("الخط: Noto Sans Arabic بترخيص SIL OFL. دليل المدن: GeoNames بترخيص CC BY 4.0.",color=c.secondary,fontSize=12.sp)
            }
            Text("نجوى · ${BuildConfig.VERSION_NAME}",color=c.secondary,fontSize=12.sp)
        }
        Screen.ABOUT -> {
            PageHeading("من نحن","نجوى · مواقيتك وهدوؤك في مكان واحد")
            SettingsCard("عن نجوى") {
                Text("نجوى — صلاتك أولاً، وضجيجك لاحقاً",color=c.mint)
                Text("نجوى تطبيق يساعدك على متابعة مواقيت الصلاة، وتلقي تنبيهات الأذان والإقامة، وضبط صمت الهاتف وقت الصلاة مع استعادة وضع الرنين السابق.",color=c.text)
                Text("صُمّم بواجهة عربية واضحة، ويحسب المواقيت محليًا على جهازك حسب المدينة وطريقة الحساب التي تختارها.",color=c.secondary)
                Text("تصميم وتطوير os73.com",color=c.mint)
            }
            SettingsCard("استخدام يناسبك") {
                Text("راجع المواقيت مع تقويم مدينتك، وحدّد مدة الإقامة حسب مسجدك. التذكيرات التي تختارها للإقامة ليست مواقيت معتمدة للمساجد.",color=c.secondary)
                Text("الإصدار ${BuildConfig.VERSION_NAME}",color=c.mint)
                Text("الخط: Noto Sans Arabic بترخيص SIL OFL. مكونات AndroidX وKotlin بترخيص Apache-2.0.",color=c.secondary,fontSize=12.sp)
            }
        }
        Screen.CONTACT -> {
            val focus=LocalFocusManager.current
            PageHeading("تواصل معنا","نسعد بملاحظاتك واقتراحاتك لتحسين نجوى")
            val categories=listOf("مشكلة تقنية","اقتراح تحسين","استفسار")
            var category by remember {mutableStateOf(categories.first())}
            var device by remember {
                mutableStateOf("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · نجوى ${BuildConfig.VERSION_NAME}")
            }
            var details by remember {mutableStateOf("")}
            var invalid by remember {mutableStateOf(false)}
            SettingsCard("رسالة إلى فريق نجوى",CONTACT_EMAIL) {
                SectionLabel("نوع الطلب")
                Choice(category,categories,{it}) {category=it}
                OutlinedTextField(device,{device=it.take(200)},label={Text("نوع الهاتف وإصدار النظام")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(details,{details=it.take(2000);invalid=false},label={Text("تفاصيل الرسالة")},minLines=3,maxLines=6,
                    isError=invalid,supportingText={Text(if(invalid)"أدخل تفاصيل رسالتك." else "${details.length} من 2000 حرف")},modifier=Modifier.fillMaxWidth())
                Action("إرسال بالبريد",Modifier.fillMaxWidth(),true) {
                    if(details.isBlank())invalid=true else {
                        focus.clearFocus()
                        onEmail(category,device.trim(),details.trim())
                    }
                }
                Text("تُفتح الرسالة جاهزة في تطبيق البريد لتراجعها وترسلها.",color=c.secondary,fontSize=12.sp)
                TextButton(onClick=onCopy) {Text("نسخ عنوان البريد")}
                message?.let {Text(it,color=c.secondary,fontSize=13.sp)}
            }
        }
        Screen.PRIVACY -> {
            PageHeading("سياسة الخصوصية","بياناتك تبقى على جهازك أثناء استخدام نجوى")
            SettingsCard("ما نحفظه محليًا") {
                Text("نحفظ المدينة أو الإحداثيات التي تدخلها، وطريقة حساب المواقيت، والمظهر، وخيارات الصمت والإشعارات والإقامة. يُحفظ أيضًا سجل محدود لاستعادة الرنين ومنع تكرار التنبيهات وآخر تنفيذ للجدولة.",color=c.text)
                Text("لا يطلب التطبيق موقع GPS، ولا يقرأ جهات الاتصال أو الرسائل أو إشعارات التطبيقات الأخرى.",color=c.secondary)
                Text("إذا اخترت ملف أذان، نحفظ نسخة خاصة منه داخل نجوى. نشارك هذا الصوت فقط مع مشغّل إشعارات Android لتشغيل التنبيه. لا نطلب الوصول العام إلى ملفات الهاتف.",color=c.secondary)
            }
            SettingsCard("الاتصال والمشاركة") {
                Text("لا يستخدم نجوى إذن الإنترنت، ولا يحتوي على حسابات أو إعلانات أو أدوات تحليل وتتبع. لا يرسل بيانات المواقيت أو إعداداتك إلى خادم.",color=c.text)
                Text("عند الضغط على التواصل، يفتح تطبيق البريد الذي تختاره. لا تُرسل رسالة تلقائيًا؛ أنت تختار ما ترسله، ويخضع البريد لخدمة البريد التي تستخدمها.",color=c.secondary)
            }
            SettingsCard("لماذا نطلب الأذونات؟") {
                Text("سياسة الإشعارات وتعديل الصوت: لتغيير وضع الرنين واستعادته. المنبّهات الدقيقة: لتوقيت الصمت وتنبيهات الصلاة. عرض الإشعارات: لإظهار الأذان والإقامة. بدء التشغيل: لإعادة الجدولة بعد إعادة تشغيل الهاتف.",color=c.text)
                Text("الويدجت يعرض المدينة والمواقيت على شاشة الهاتف الرئيسية. لا يضيف إذنًا للوصول إلى بياناتك.",color=c.secondary)
            }
            SettingsCard("التحكم والحذف") {
                Text("يمكنك تعطيل الصمت والتنبيهات أو سحب الأذونات من إعدادات الهاتف. تُحذف البيانات المحلية عند مسح بيانات التطبيق أو إزالته. النسخ الاحتياطي للنظام معطّل.",color=c.text)
                Text("استفسارات الخصوصية: $CONTACT_EMAIL",color=c.mint)
            }
        }
        else -> Unit
    }
}
