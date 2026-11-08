package org.najwa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import org.najwa.app.NajwaRuntime
import org.najwa.core.*
import java.time.Instant

@Composable fun SilenceScreen(runtime: NajwaRuntime,p: Preferences,s: Session?,now: Instant,onSave: (Preferences)->Unit,onAccess: ()->Unit,onRestore: ()->Unit) {
    SilenceContent(p,s,now,runtime.audio.capable() && runtime.alarms.exactAccess,runtime.audio.fixed,onSave,onAccess,onRestore)
}

@Composable fun SilenceContent(p: Preferences,s: Session?,now: Instant,ready: Boolean,fixed: Boolean,onSave: (Preferences)->Unit,onAccess: ()->Unit,onRestore: ()->Unit) {
    val c=LocalPalette.current
    PageHeading("الصمت التلقائي","هدوء وقت الصلاة، ثم عودة إلى وضع الرنين السابق")
    SettingsCard("التفعيل") {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(p.autoSilent)"الصمت التلقائي مفعّل" else "تفعيل الصمت التلقائي",color=c.text,fontSize=16.sp)
                Text(when {fixed->"هذا الجهاز لا يدعم تغيير الرنين";!ready->"أكمل الأذونات لبدء الاستخدام";p.enabled.isEmpty()->"اختر صلاة واحدة على الأقل";else->"يبدأ بعد الأذان بـ20 ثانية؛ نهاية المدة محسوبة من الأذان"},color=c.secondary,fontSize=12.sp)
            }
            Switch(p.autoSilent,{if(it&&!ready)onAccess() else onSave(p.copy(autoSilent=it))},modifier=Modifier.semantics {contentDescription="تفعيل الصمت التلقائي"},enabled=!fixed,colors=silenceSwitchColors())
        }
        Text("عند التفعيل قد يظهر رمز المنبّه في الهاتف وموعد الصلاة المجدولة التالية.",color=c.secondary,fontSize=12.sp)
    }
    if(s!=null && s.state !in listOf(SessionState.RESTORED,SessionState.USER_OVERRIDE,SessionState.IDLE)) {
        val active=s.state==SessionState.ACTIVE
        SettingsCard(if(active)"جلسة صمت نشطة" else "راجع استعادة الرنين") {
            if(active)Text(countdown(((s.deadline-now.toEpochMilli())/1000).coerceAtLeast(0)),color=c.mint,fontSize=36.sp,fontWeight=FontWeight.Bold)
            Text("الاستعادة: ${displayTime(Instant.ofEpochMilli(s.deadline),p)} · الوضع السابق: ${modeName(s.original)}",color=c.secondary,fontSize=13.sp)
            if(!active)Text("تحقق من صوت الهاتف. يمكنك طلب استعادة الوضع السابق إذا لم تغيّره بنفسك.",color=c.amber,fontSize=13.sp)
            Action("استعادة الآن",Modifier.fillMaxWidth(),true,onRestore)
        }
    }
    PrayerDurations("الصلوات ومدد الصمت","فعّل كل صلاة وحدد مدة الصمت من الأذان حتى نهايتها.",p.silenceMinutes,defaultSilenceMinutes(),5,
        enabledPrayers=p.enabled,onEnabled={prayer,enabled->onSave(p.copy(enabled=if(enabled)p.enabled+prayer else p.enabled-prayer))}) {onSave(p.copy(silenceMinutes=it))}
    SettingsCard("وضع الهاتف أثناء الصلاة") {
        OptionGrid(listOf(Ringer.SILENT,Ringer.VIBRATE),p.mode,::modeName,2) {onSave(p.copy(mode=it))}
        Text("يبقى صوت المنبّه والوسائط دون تعديل.",color=c.secondary,fontSize=12.sp)
    }
}
private fun modeName(mode: Ringer)=when(mode) {Ringer.SILENT->"صامت";Ringer.VIBRATE->"اهتزاز";Ringer.NORMAL->"عادي"}
@Composable private fun silenceSwitchColors() = LocalPalette.current.let { c -> SwitchDefaults.colors(checkedThumbColor=c.bg,checkedTrackColor=c.green,uncheckedThumbColor=c.secondary,uncheckedTrackColor=c.track,uncheckedBorderColor=c.border) }
