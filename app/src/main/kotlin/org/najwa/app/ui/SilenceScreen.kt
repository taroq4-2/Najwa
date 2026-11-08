package org.najwa.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.najwa.app.NajwaRuntime
import org.najwa.app.platform.openSystemSettings

@Composable fun SectionTitle(text: String) { Text(text,color=LocalPalette.current.secondary,fontSize=15.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=18.dp,bottom=10.dp)) }
@Composable fun PermissionsScreen(runtime: NajwaRuntime,onDone: ()->Unit,onSkip: ()->Unit,onBack: ()->Unit=onDone,firstRun: Boolean=false) {
    val context=LocalContext.current
    var error by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    var askedOnFirstRun by androidx.compose.runtime.saveable.rememberSaveable {mutableStateOf(false)}
    val request=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {runtime.reconcile();revision++}
    fun notifications() {
        val permission=android.Manifest.permission.POST_NOTIFICATIONS
        val rationale=(context as? android.app.Activity)?.shouldShowRequestPermissionRationale(permission)==true
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(permission)!=android.content.pm.PackageManager.PERMISSION_GRANTED && (!askedOnFirstRun || rationale)) {
            askedOnFirstRun=true
            request.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if(!runtime.notifications.granted)
            openSystemSettings(context,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
    }
    val notificationGranted=remember(revision,runtime.notifications.granted) {runtime.notifications.granted}
    LaunchedEffect(firstRun) {
        if(firstRun && !askedOnFirstRun && Build.VERSION.SDK_INT>=33 && !notificationGranted &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifications()
        }
    }
    PermissionsContent(runtime.audio.policyAccess,runtime.alarms.exactAccess,runtime.audio.fixed,error,
        onOpen={index->
            val intent=if(index==0)Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                else if(Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}"))
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))
            if(!openSystemSettings(context,intent))error="تعذّر فتح صفحة النظام. افتح إعدادات الهاتف وامنح الوصول من هناك."
        },onDone,onSkip,onBack,firstRun,notificationGranted,::notifications)
}
@Composable fun PermissionsContent(policy: Boolean,exact: Boolean,fixed: Boolean,error: String?,onOpen: (Int)->Unit,onDone: ()->Unit,onSkip: ()->Unit,onBack: ()->Unit,firstRun: Boolean=false,notificationsGranted: Boolean=true,onNotifications: ()->Unit={}) {
    val c=LocalPalette.current
    if(!firstRun)TextButton(onClick=onBack) {Text("الرجوع إلى الصمت")}
    Spacer(Modifier.height(30.dp))
    Text(if(firstRun)"أهلًا بك في نجوى" else "الوصول اللازم للصمت التلقائي",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,color=c.text)
    Text("المواقيت والعدّ التنازلي متاحان للجميع. فعّل التنبيهات والمنبّهات الدقيقة والصمت التلقائي من خطوات الإعداد.",color=c.secondary,modifier=Modifier.padding(vertical=16.dp))
    if(firstRun && (!notificationsGranted || !exact || !policy && !fixed)) {
        Action("تفعيل الأذونات الآن",Modifier.fillMaxWidth(),true) {
            when {
                !notificationsGranted -> onNotifications()
                !exact -> onOpen(1)
                else -> onOpen(0)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if(firstRun)SettingsCard("إشعارات الصلاة") {
        Text(if(notificationsGranted)"✓ الإشعارات مسموح بها" else "اسمح بعرض إشعارات الأذان والإقامة.",color=c.secondary)
        if(!notificationsGranted)Action("السماح بالإشعارات",Modifier.fillMaxWidth(),onClick=onNotifications)
    }
    listOf(Triple("الوصول إلى سياسة الإشعارات",policy,"يسمح باستخدام واجهة النظام لتغيير الرنين ثم إعادته. لا نقرأ إشعاراتك، ولا نضبط مرشّح عدم الإزعاج مباشرةً."),Triple("المنبّهات الدقيقة",exact,"لبدء الصمت واستعادة الحالة في الموعد. بدونه يبقى الصمت التلقائي متوقفًا، ولا نستبدله بتنبيه غير دقيق.")).forEachIndexed { i,item ->
        Panel(Modifier.fillMaxWidth().padding(bottom=12.dp),24,item.second) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(item.first,color=c.text,fontWeight=FontWeight.Bold,fontSize=18.sp)
                Text(if(item.second)"✓ ممنوح" else "مطلوب",color=if(item.second)c.mint else c.amber)
                Text(item.third,color=c.secondary,fontSize=14.sp)
                Action("فتح إعدادات ${if(i==0)"الوصول" else "المنبّهات"}",Modifier.fillMaxWidth(),true) {
                    onOpen(i)
                }
            }
        }
    }
    error?.let {Text(it,color=c.amber)}
    Text("لن ننتقل إلى إعدادات النظام إلا بعد أن تضغط الزر.",color=c.secondary,fontSize=12.sp,modifier=Modifier.padding(vertical=20.dp))
    if(fixed)Text("هذا الجهاز لا يسمح لتطبيق نجوى بتغيير وضع الرنين.",color=c.amber)
    val setupLabel=when {
        firstRun && notificationsGranted && exact && policy && !fixed -> "تفعيل التنبيهات والصمت والمتابعة"
        firstRun && notificationsGranted && exact -> "تفعيل التنبيهات والمتابعة"
        firstRun && exact && policy && !fixed -> "تفعيل الصمت والمتابعة"
        firstRun -> "المتابعة إلى نجوى"
        policy && exact && !fixed -> "تفعيل الصمت والمتابعة"
        else -> "الرجوع إلى الصمت"
    }
    Action(setupLabel,Modifier.fillMaxWidth(),true,onDone)
    Spacer(Modifier.height(10.dp));Action("التخطي الآن",Modifier.fillMaxWidth(),onClick=onSkip)
}
