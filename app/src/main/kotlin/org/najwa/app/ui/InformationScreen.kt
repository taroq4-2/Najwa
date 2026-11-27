package org.najwa.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.*
import org.najwa.app.BuildConfig
import org.najwa.app.R
import org.najwa.app.platform.*
import org.najwa.core.Screen

@Composable fun InformationScreen(screen: Screen,onNavigate: (Screen)->Unit,onBack: ()->Unit) {
    val context=LocalContext.current
    var message by remember {mutableStateOf<String?>(null)}
    InformationContent(screen,onNavigate,onBack,message,onEmail={category,device,details->
        if(!launchExternal(context,contactEmailIntent(category,device,details)))message=context.getString(R.string.contact_no_email_app)
    },onCopy={
        message=if(runCatching {(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Najwa contact",CONTACT_EMAIL))}.isSuccess)context.getString(R.string.contact_email_copied) else context.getString(R.string.contact_copy_failed,CONTACT_EMAIL)
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
                Text(stringResource(R.string.info_guide_body),color=c.secondary,lineHeight=22.sp)
            }
            Text(stringResource(R.string.brand_version,BuildConfig.VERSION_NAME),color=c.secondary,fontSize=12.sp)
        }
        Screen.ABOUT -> {
            PageHeading(stringResource(R.string.about_title_page),stringResource(R.string.about_subtitle))
            InfoSection(R.string.about_what_title,R.string.about_what_body)
            InfoSection(R.string.about_features_title,R.string.about_features_body)
            InfoSection(R.string.about_sources_title,R.string.about_sources_body)
            InfoSection(R.string.about_note_title,R.string.about_note_body)
            Text(stringResource(R.string.about_made_by),color=c.mint,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold,modifier=Modifier.padding(top=4.dp))
            Text(stringResource(R.string.app_version_label,BuildConfig.VERSION_NAME),color=c.secondary,fontSize=12.sp)
        }
        Screen.CONTACT -> {
            val focus=LocalFocusManager.current
            PageHeading("تواصل معنا","نسعد بملاحظاتك واقتراحاتك لتحسين نجوى")
            val categories=listOf("مشكلة تقنية","اقتراح تحسين","استفسار")
            var category by rememberSaveable {mutableStateOf(categories.first())}
            var device by rememberSaveable {
                mutableStateOf("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · نجوى ${BuildConfig.VERSION_NAME}")
            }
            var details by rememberSaveable {mutableStateOf("")}
            var invalid by rememberSaveable {mutableStateOf(false)}
            SettingsCard("رسالة إلى فريق نجوى",CONTACT_EMAIL) {
                SectionLabel("نوع الطلب")
                Choice(category,categories,{it}) {category=it}
                OutlinedTextField(device,{device=it.take(200)},label={Text(uiText("نوع الهاتف وإصدار النظام"))},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(details,{details=it.take(2000);invalid=false},label={Text(uiText("تفاصيل الرسالة"))},minLines=3,maxLines=6,
                    isError=invalid,supportingText={Text(if(invalid)uiText("أدخل تفاصيل رسالتك.") else stringResource(R.string.contact_character_count,details.length,2000))},modifier=Modifier.fillMaxWidth())
                Action("إرسال بالبريد",Modifier.fillMaxWidth(),true) {
                    if(details.isBlank())invalid=true else {
                        focus.clearFocus()
                        onEmail(category,device.trim(),details.trim())
                    }
                }
                Text(uiText("تُفتح الرسالة جاهزة في تطبيق البريد لتراجعها وترسلها."),color=c.secondary,fontSize=12.sp)
                TextButton(onClick=onCopy) {Text(uiText("نسخ عنوان البريد"))}
                message?.let {Text(it,color=c.secondary,fontSize=13.sp)}
            }
        }
        Screen.PRIVACY -> {
            PageHeading(stringResource(R.string.privacy_title_page),stringResource(R.string.privacy_subtitle))
            Text(stringResource(R.string.privacy_updated,java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(java.time.LocalDate.of(2026,11,26)),BuildConfig.VERSION_NAME),
                color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(bottom=12.dp))
            InfoSection(R.string.privacy_short_title,R.string.privacy_short_body,highlight=true)
            InfoSection(R.string.privacy_stored_title,R.string.privacy_stored_body)
            InfoSection(R.string.privacy_location_title,R.string.privacy_location,extra=R.string.qibla_location_note)
            InfoSection(R.string.privacy_permissions_title,R.string.privacy_permissions_body)
            InfoSection(R.string.privacy_leaves_title,R.string.privacy_leaves_body)
            InfoSection(R.string.privacy_control_title,R.string.privacy_control_body)
            Text(stringResource(R.string.contact_privacy_email,CONTACT_EMAIL),color=c.mint,modifier=Modifier.padding(top=4.dp))
        }
        else -> Unit
    }
}

/** One titled section of the About and Privacy pages; bullet lines keep comfortable spacing. */
@Composable private fun InfoSection(title: Int,body: Int,highlight: Boolean=false,extra: Int?=null) {
    val c=LocalPalette.current
    Panel(Modifier.fillMaxWidth().padding(bottom=12.dp),radius=22,selected=highlight) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title),color=c.text,fontSize=16.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
            Text(stringResource(body),color=if(highlight)c.text else c.secondary,fontSize=14.sp,lineHeight=24.sp)
            if(extra!=null)Text(stringResource(R.string.qibla_title)+": "+stringResource(extra),color=c.secondary,fontSize=14.sp,lineHeight=24.sp)
        }
    }
}
