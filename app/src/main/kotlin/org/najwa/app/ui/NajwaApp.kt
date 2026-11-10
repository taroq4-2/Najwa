package org.najwa.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.BackHandler
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.core.*
import java.time.Instant

@Composable fun NajwaApp(runtime: NajwaRuntime,resumed: Int,homeRequest: Int=0) {
    var prefs by remember { mutableStateOf(runtime.preferences) }
    var now by remember { mutableStateOf(Instant.now()) }
    var session by remember { mutableStateOf(runtime.session()) }
    var trail by rememberSaveable {mutableStateOf(listOf(Screen.HOME.name))}
    val context=LocalContext.current
    val setup=remember(context) {context.getSharedPreferences("first-run",0)}
    val setupKey="permissions-setup-v2"
    var onboarding by rememberSaveable {mutableStateOf(!setup.getBoolean(setupKey,false) &&
        (!setup.getBoolean("permissions-complete",false) || !runtime.notifications.granted || !runtime.alarms.exactAccess || !runtime.audio.policyAccess))}
    var cityConfirmed by rememberSaveable {mutableStateOf(setup.getBoolean("city-confirmed",false) ||
        setup.getBoolean("permissions-complete",false) || prefs.city!=Preferences().city ||
        prefs.autoSilent || prefs.adhanNotifications || prefs.iqamaNotifications || prefs.fridayReminder)}
    var setupSilence by rememberSaveable {mutableStateOf(prefs.autoSilent)}
    fun confirmCity() {setup.edit().putBoolean("city-confirmed",true).apply();cityConfirmed=true}
    fun finishSetup() {setup.edit().putBoolean(setupKey,true).apply();onboarding=false}
    val screen=if(onboarding)Screen.PERMISSIONS else Screen.fromSaved(trail.last())
    fun navigate(destination: Screen) {trail=navigateScreens(trail,destination)}
    LaunchedEffect(homeRequest) {if(homeRequest>0)trail=navigateScreens(trail,Screen.HOME,true)}
    fun back() {trail=backScreens(trail)}
    var cityDialog by rememberSaveable {mutableStateOf(false)}
    BackHandler(!cityDialog && (onboarding || trail.size>1)) {if(onboarding)finishSetup() else back()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,_->visible=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    LaunchedEffect(visible,resumed) { if(visible) { prefs=runtime.preferences; while(true) { now=Instant.now(); session=runtime.displayedSession(); delay(1000) } } }
    val dark=when(prefs.theme) { ThemeChoice.DARK->true;ThemeChoice.LIGHT->false;else->isSystemInDarkTheme() }
    val activity=LocalContext.current as? android.app.Activity
    SideEffect {
        activity?.let {androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView).apply {
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }}
    }
    val colors=if(dark)Dark else Light
    val days=remember(prefs,now.atZone(prefs.city.zoneId).toLocalDate()) { runtime.calculator.around(now,prefs) }
    val current=days.first { it.date==now.atZone(prefs.city.zoneId).toLocalDate() }
    val times=timeline(days.flatMap { it.events },now)
    val editingContact=screen==Screen.CONTACT && WindowInsets.ime.getBottom(LocalDensity.current)>0
    fun save(p: Preferences) {
        if(!cityConfirmed && (p.autoSilent || p.adhanNotifications || p.iqamaNotifications || p.fridayReminder)) {
            cityDialog=true
            return
        }
        runtime.update(p)
        prefs=runtime.preferences
        session=runtime.session()
    }
    CompositionLocalProvider(LocalPalette provides colors) {
        NajwaTheme(dark) {
            Column(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding(),horizontalAlignment=Alignment.CenterHorizontally) {
                PageScroll(screen) {
                    runtime.error?.let { Text(it,color=colors.amber,modifier=Modifier.padding(vertical=10.dp)) }
                    runtime.notice?.let {Text(it,color=colors.mint,modifier=Modifier.padding(vertical=10.dp))}
                    when(screen) {
                        Screen.PERMISSIONS -> PermissionsScreen(runtime,
                            onDone={
                                if(onboarding && cityConfirmed) {
                                    val alerts=runtime.notifications.granted && runtime.alarms.exactAccess
                                    save(prefs.copy(
                                        adhanNotifications=prefs.adhanNotifications || alerts,
                                        iqamaNotifications=prefs.iqamaNotifications || alerts,
                                        fridayReminder=prefs.fridayReminder || alerts,
                                        autoSilent=prefs.autoSilent || (setupSilence && runtime.audio.capable() && runtime.alarms.exactAccess)
                                    ))
                                    finishSetup()
                                } else if(!onboarding) {if(runtime.audio.capable() && runtime.alarms.exactAccess)save(prefs.copy(autoSilent=true));back()}
                            },
                            onSkip={if(onboarding)finishSetup() else back()},
                            onBack={if(onboarding)finishSetup() else back()},firstRun=onboarding,
                            city=prefs.city,cityConfirmed=cityConfirmed,onChooseCity={cityDialog=true},onConfirmCity=::confirmCity,
                            wantsSilence=setupSilence,onWantsSilence={setupSilence=it})
                        Screen.NOTIFICATIONS -> NotificationScreen(runtime,prefs,::save,::back,resumed)
                        Screen.HOME -> HomeScreen(prefs,now,current,times,runtime.error==null && days.all {it.complete} && prefs.enabled.isNotEmpty() && runtime.audio.capable() && runtime.alarms.exactAccess,onCity={cityDialog=true},onSilence={navigate(Screen.SILENCE)},activeUntil=session?.takeIf {it.state==SessionState.ACTIVE}?.let {Instant.ofEpochMilli(it.deadline)})
                        Screen.SILENCE -> SilenceScreen(runtime,prefs,session,now,onSave=::save,onAccess={navigate(Screen.PERMISSIONS)},onRestore={runtime.restore();session=runtime.session()})
                        Screen.SETTINGS -> SettingsScreen(prefs,onSave=::save,onCity={cityDialog=true},onNotifications={navigate(Screen.NOTIFICATIONS)},onWidget={navigate(Screen.WIDGET)},onInformation={navigate(Screen.INFORMATION)},onCalculation={navigate(Screen.CALCULATION)},onAppearance={navigate(Screen.APPEARANCE)},onSilence={navigate(Screen.SILENCE)})
                        Screen.CALCULATION -> CalculationScreen(prefs,::save,{cityDialog=true},::back)
                        Screen.APPEARANCE -> AppearanceScreen(prefs,::save,::back)
                        Screen.WIDGET -> WidgetScreen(prefs,now,::back,::save)
                        else -> InformationScreen(screen,::navigate,::back)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if(!onboarding && !editingContact)BottomNavigation(screen.tab) {trail=navigateScreens(trail,Screen.forTab(it),true)}
            }
            if(cityDialog) CityDialog(prefs.city,onDismiss={cityDialog=false},onSelect={
                save(prefs.copy(city=it))
                if(prefs.city==it)confirmCity()
                cityDialog=false
            })
        }
    }
}
/** Keep each scroll node with its own state and content. Never replace only the
 * scroll state of a node that is still attached during a navigation transition. */
@Composable fun ColumnScope.PageScroll(screen: Screen,content: @Composable ColumnScope.()->Unit) {
    val stateHolder=rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(screen.name) {
        val scroll=rememberScrollState()
        val entrance=remember {Animatable(1f)}
        LaunchedEffect(screen) {entrance.snapTo(.92f);entrance.animateTo(1f,tween(180))}
        Column(Modifier.weight(1f).widthIn(max=600.dp).fillMaxWidth()
            .graphicsLayer {alpha=entrance.value;translationY=(1f-entrance.value)*80f}
            .verticalScroll(scroll).padding(horizontal=20.dp),content=content)
    }
}
@Composable fun BottomNavigation(tab: Int,onTab: (Int)->Unit) {
    val c=LocalPalette.current
    NavigationBar(containerColor=c.surface,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
        listOf(stringResource(R.string.en_tab_home) to "home",stringResource(R.string.en_tab_silence) to "sound",stringResource(R.string.en_tab_settings) to "settings").forEachIndexed { i,item ->
            NavigationBarItem(selected=i==tab,onClick={onTab(i)},icon={Glyph(item.second,if(i==tab)c.mint else c.secondary)},
                label={Text(item.first,fontSize=12.sp,fontWeight=if(i==tab)FontWeight.Bold else FontWeight.Normal)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=c.selected,selectedTextColor=c.mint,unselectedTextColor=c.secondary))
        }
    }
}
@Composable fun najwaTypography(): Typography {
    val font=FontFamily(Font(R.font.noto_sans_arabic_regular),Font(R.font.noto_sans_arabic_bold,FontWeight.Bold))
    val t=Typography()
    return t.copy(bodyLarge=t.bodyLarge.copy(fontFamily=font),bodyMedium=t.bodyMedium.copy(fontFamily=font),bodySmall=t.bodySmall.copy(fontFamily=font),titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),titleSmall=t.titleSmall.copy(fontFamily=font),labelLarge=t.labelLarge.copy(fontFamily=font),labelMedium=t.labelMedium.copy(fontFamily=font),labelSmall=t.labelSmall.copy(fontFamily=font),headlineLarge=t.headlineLarge.copy(fontFamily=font),headlineMedium=t.headlineMedium.copy(fontFamily=font),headlineSmall=t.headlineSmall.copy(fontFamily=font),displayLarge=t.displayLarge.copy(fontFamily=font),displayMedium=t.displayMedium.copy(fontFamily=font),displaySmall=t.displaySmall.copy(fontFamily=font))
}
