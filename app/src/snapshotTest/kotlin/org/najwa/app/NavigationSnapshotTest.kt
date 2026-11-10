package org.najwa.app

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.DeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.najwa.app.ui.*
import org.najwa.app.platform.*
import org.najwa.core.*
import java.time.Instant

class NavigationSnapshotTest {
    @get:Rule val paparazzi=Paparazzi(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar"),theme="android:Theme.Material.Light.NoActionBar",supportsRtl=true)
    private val now=Instant.parse("2026-11-07T09:00:00Z")
    @Test fun clickingPermissionsAndSettingsThenReturningKeepsNavigationWorking() {
        var screen by mutableStateOf(Screen.SILENCE)
        var trail=listOf(Screen.HOME.name,Screen.SILENCE.name)
        fun navigate(destination: Screen,tab: Boolean=false) {trail=navigateScreens(trail,destination,tab);screen=Screen.fromSaved(trail.last())}
        var prefs by mutableStateOf(Preferences())
        var opened=-1;var email=0;var copies=0;var pins=0
        var policyGranted by mutableStateOf(false)
        fun back() {trail=backScreens(trail);screen=Screen.fromSaved(trail.last())}
        val view=ComposeView(paparazzi.context)
        val owner=object: androidx.lifecycle.LifecycleOwner {
            override val lifecycle=androidx.lifecycle.LifecycleRegistry(this).apply {currentState=androidx.lifecycle.Lifecycle.State.RESUMED}
        }
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
        var completed=false
        view.setContent {
            LaunchedEffect(Unit) {
                suspend fun step(text: String) {
                    repeat(3) {withFrameNanos {}}
                    clickText(view,text)
                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                }
        repeat(3) {withFrameNanos {}}
        val silenceToggles=allNodes(view).filter {it.config.getOrNull(SemanticsProperties.ToggleableState)!=null}
        assertEquals(6,silenceToggles.size)
        assertTrue("Every silent-mode toggle has a contextual label",silenceToggles.all {it.config.getOrNull(SemanticsProperties.ContentDescription)?.any(String::isNotBlank)==true})
        Prayer.entries.filter {it.isPrayer}.forEach {prayer->
            assertTrue("Edit action names ${prayer.label}",allNodes(view).any {n->n.config.getOrNull(SemanticsProperties.ContentDescription)?.any {it.startsWith("تعديل") && it.contains(prayer.label)}==true})
        }
        repeat(3) {
            policyGranted=false
            assertTrue(silenceToggles.first().config[SemanticsActions.OnClick].action!!.invoke());repeat(3) {withFrameNanos {}};assertEquals(Screen.PERMISSIONS,screen)
            step("فتح إعدادات الوصول");assertEquals(0,opened)
            step("فتح إعدادات المنبّهات");assertEquals(1,opened)
            step("الرجوع إلى الصمت");assertEquals(Screen.SILENCE,screen);assertFalse(prefs.autoSilent)
        }
        step("الإعدادات")
        step("المظهر والعرض")
        step("داكن");assertEquals(ThemeChoice.DARK,prefs.theme)
        step("24 ساعة");assertTrue(prefs.hour24)
        step("الرجوع إلى الإعدادات");assertEquals(Screen.SETTINGS,screen)
        step("مواقيت الصلاة والمدينة");assertEquals(Screen.CALCULATION,screen)
        step("خيارات الحساب المتقدمة")
        repeat(4) {withFrameNanos {}}
        val scroll=allNodes(view).first {it.config.getOrNull(SemanticsActions.ScrollBy)!=null}
        scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f,300f)
        repeat(18) {withFrameNanos {}}
        val scrolled=allNodes(view).first {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null}.config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("Calculation page scrolls",scrolled>0)
        step("الرجوع إلى الإعدادات")
        step("مواقيت الصلاة والمدينة")
        repeat(4) {withFrameNanos {}}
        val restored=allNodes(view).first {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!=null}.config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Scroll restored on return",scrolled,restored,1f)
        step("الرجوع إلى الإعدادات")
        step("ضبط تنبيهات الصلاة");assertEquals(Screen.NOTIFICATIONS,screen)
        repeat(3) {withFrameNanos {}}
        repeat(2) {index->
            val toggles=allNodes(view).filter {it.config.getOrNull(SemanticsProperties.ToggleableState)!=null}
            assertEquals(3,toggles.size)
            assertTrue("Notification switch has a label",toggles[index].config.getOrNull(SemanticsProperties.ContentDescription)?.any(String::isNotBlank)==true)
            assertTrue(toggles[index].config[SemanticsActions.OnClick].action!!.invoke())
            repeat(3) {withFrameNanos {}}
        }
        assertTrue(prefs.adhanNotifications);assertTrue(prefs.iqamaNotifications)
        step("الإعدادات")
        step("معلومات التطبيق")
        listOf("من نحن","تواصل معنا","سياسة الخصوصية").forEach {label->
            step(label)
            if(screen==Screen.CONTACT) {
                step("إرسال بالبريد");step("نسخ عنوان البريد")
                val fields=allNodes(view).mapNotNull {it.config.getOrNull(SemanticsActions.SetText)?.action}
                assertEquals(2,fields.size)
                assertTrue(fields.last()(androidx.compose.ui.text.AnnotatedString("مسودة قديمة")))
                repeat(3) {withFrameNanos {}}
                step("الرجوع إلى معلومات التطبيق")
                step("تواصل معنا")
                repeat(3) {withFrameNanos {}}
                assertTrue(allNodes(view).none {it.config.getOrNull(SemanticsProperties.EditableText)?.text=="مسودة قديمة"})
            }
            step("الرجوع إلى معلومات التطبيق");assertEquals(Screen.INFORMATION,screen)
        }
        assertEquals(0,email);assertEquals(1,copies)
        step("الرجوع إلى الإعدادات")
        step("ويدجت الشاشة الرئيسية");step("إضافة إلى الشاشة الرئيسية");assertEquals(1,pins)
        step("الرجوع إلى الإعدادات");assertEquals(Screen.SETTINGS,screen)
        step("الرئيسية");assertEquals(Screen.HOME,screen)

                completed=true
            }
            NajwaTheme(true) {Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
            PageScroll(screen) {when(screen) {
                Screen.SILENCE->SilenceContent(prefs,null,now,false,false,{prefs=it},{navigate(Screen.PERMISSIONS)},{})
                Screen.PERMISSIONS->PermissionsContent(policyGranted,false,false,null,{opened=it;if(it==0)policyGranted=true},::back,{prefs=prefs.copy(autoSilent=false);back()},::back)
                Screen.SETTINGS->SettingsScreen(prefs,{prefs=it},{},{navigate(Screen.NOTIFICATIONS)},{navigate(Screen.WIDGET)},{navigate(Screen.INFORMATION)},{navigate(Screen.CALCULATION)},{navigate(Screen.APPEARANCE)},{navigate(Screen.SILENCE)})
                Screen.CALCULATION->CalculationScreen(prefs,{prefs=it},{},::back)
                Screen.APPEARANCE->AppearanceScreen(prefs,{prefs=it},::back)
                Screen.WIDGET->WidgetContent(prefs,now,::back) {pins++}
                Screen.NOTIFICATIONS->NotificationPreferences(prefs) {prefs=it}
                Screen.HOME->HomeScreen(prefs,now,PrayerCalculator().around(now,prefs)[1],null,false,{},{navigate(Screen.SILENCE)})
                else->InformationContent(screen,{navigate(it)},::back,onEmail={_,_,_->email++},onCopy={copies++})
            }}
            BottomNavigation(screen.tab) {navigate(Screen.forTab(it),true)}
        }}}
        paparazzi.gif(view,"navigation_clicks",end=9000L,fps=30)
        assertTrue("All navigation actions completed",completed)
        view.disposeComposition()
    }
    private fun allNodes(view: View): List<SemanticsNode> {
        fun owner(v: View): SemanticsOwner? {
            v.javaClass.methods.firstOrNull {it.name=="getSemanticsOwner" && it.parameterCount==0}?.let {return it.invoke(v) as SemanticsOwner}
            if(v is ViewGroup)for(i in 0 until v.childCount)owner(v.getChildAt(i))?.let {return it}
            return null
        }
        fun nodes(n: SemanticsNode): List<SemanticsNode> = listOf(n)+n.children.flatMap(::nodes)
        val semantics=owner(view) ?: error("No Compose semantics")
        return nodes(semantics.rootSemanticsNode)
    }
    private fun clickText(view: View,text: String) {
        val match=allNodes(view).firstOrNull {n->n.config.getOrNull(SemanticsProperties.Text)?.any {it.text==text}==true} ?: error("Missing text: $text")
        var target: SemanticsNode?=match
        while(target!=null) {
            target.config.getOrNull(SemanticsActions.OnClick)?.action?.let {assertTrue("Click: $text",it());return}
            target=target.parent
        }
        error("No click action: $text")
    }
    @Test fun onboardingDoesNotOfferActivationUntilCityIsConfirmed() {
        var confirmed by mutableStateOf(false)
        var completed=false
        val view=ComposeView(paparazzi.context)
        view.setContent {
            LaunchedEffect(Unit) {
                repeat(3) {withFrameNanos {}}
                val activation="تفعيل التنبيهات والمتابعة"
                assertFalse(allNodes(view).any {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==activation}==true})
                clickText(view,"تأكيد جدة")
                repeat(3) {withFrameNanos {}}
                assertTrue(allNodes(view).any {it.config.getOrNull(SemanticsProperties.Text)?.any {it.text==activation}==true})
                completed=true
            }
            NajwaTheme(true) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.bg).padding(horizontal=20.dp)) {
                    PermissionsContent(true,true,false,null,{},{},{},onBack={},firstRun=true,
                        city=Preferences().city,cityConfirmed=confirmed,onConfirmCity={confirmed=true})
                }
            }
        }
        paparazzi.gif(view,"onboarding_city_confirmation",end=1000L,fps=30)
        assertTrue(completed)
        view.disposeComposition()
    }
    @Test fun setupShowsOnePermissionAndKeepsSilentModeOptional() {
        var notified by mutableStateOf(false)
        var exact by mutableStateOf(false)
        var completed=false
        val view=ComposeView(paparazzi.context)
        view.setContent {
            LaunchedEffect(Unit) {
                fun visible(label: String)=allNodes(view).any {node->
                    node.config.getOrNull(SemanticsProperties.Text)?.any {it.text==label}==true
                }
                repeat(3) {withFrameNanos {}}
                assertFalse(visible("فتح إعدادات المنبّهات"))
                clickText(view,"السماح بالإشعارات")
                repeat(3) {withFrameNanos {}}
                assertFalse(visible("السماح بالإشعارات"))
                clickText(view,"فتح إعدادات المنبّهات")
                repeat(3) {withFrameNanos {}}
                assertFalse(visible("فتح إعدادات الوصول"))
                assertTrue(visible("تفعيل التنبيهات والمتابعة"))
                completed=true
            }
            NajwaTheme(true) {
                Column(Modifier.fillMaxSize().background(LocalPalette.current.bg).padding(horizontal=20.dp)) {
                    PermissionsContent(false,exact,false,null,{if(it==1)exact=true},{},{},{},
                        firstRun=true,notificationsGranted=notified,onNotifications={notified=true})
                }
            }
        }
        paparazzi.gif(view,"onboarding_single_permission",end=1000L,fps=30)
        assertTrue(completed)
        view.disposeComposition()
    }
    @Test fun allNewPagesInBothThemesAndLargeFont() {
        for(dark in listOf(true,false))for(large in listOf(false,true))for(screen in listOf(Screen.PERMISSIONS,Screen.INFORMATION,Screen.ABOUT,Screen.CONTACT,Screen.PRIVACY,Screen.WIDGET,Screen.APPEARANCE,Screen.CALCULATION)) {
            paparazzi.snapshot("${screen.name.lowercase()}_${if(dark)"dark" else "light"}${if(large)"_large" else ""}") {
                CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(LocalDensity.current.density,if(large)1.4f else 1f)) {
                    NajwaTheme(dark) {Column(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                        PageScroll(screen) {when(screen) {
                            Screen.PERMISSIONS->PermissionsContent(false,false,false,null,{},{},{},{})
                            Screen.APPEARANCE->AppearanceScreen(Preferences(),{},{})
                            Screen.CALCULATION->CalculationScreen(Preferences(),{},{},{})
                            Screen.WIDGET->WidgetContent(Preferences(),now,{}) {}
                            else->InformationContent(screen,{},{})
                        }}
                        BottomNavigation(screen.tab) {}
                    }}
                }
            }
        }
    }
    @Test fun actualNativeWidgetInflatesWithItsRemoteActions() {
        paparazzi.unsafeUpdateConfig(deviceConfig=DeviceConfig.PIXEL_5.copy(locale="ar",screenWidth=780,screenHeight=480,density=com.android.resources.Density.XHIGH,orientation=com.android.resources.ScreenOrientation.LANDSCAPE))
        val parent=android.widget.FrameLayout(paparazzi.context)
        val widget=PrayerWidget.views(paparazzi.context,Preferences(),now).apply(paparazzi.context,parent)
        widget.layoutParams=ViewGroup.LayoutParams(780,480)
        // Stop the renderer's clock for a deterministic screenshot; the launcher runs it.
        widget.findViewById<android.widget.Chronometer>(R.id.widget_countdown).apply {stop();text="01:24:30"}
        paparazzi.snapshot(widget,"native_widget")
        assertEquals("نجوى",(widget as ViewGroup).let {findText(it,"نجوى")})
    }
    private fun findText(view: View,text: String): String? {
        if(view is android.widget.TextView && view.text.toString()==text)return text
        if(view is ViewGroup)for(i in 0 until view.childCount)findText(view.getChildAt(i),text)?.let {return it}
        return null
    }
}
