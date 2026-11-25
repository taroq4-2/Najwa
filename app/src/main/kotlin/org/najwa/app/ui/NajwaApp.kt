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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.najwa.app.prayerDispatcher
import org.najwa.app.NajwaRuntime
import org.najwa.app.R
import org.najwa.core.*
import java.time.Instant

@Composable fun NajwaApp(runtime: NajwaRuntime,resumed: Int,homeRequest: Int=0,action: String?=null,actionRequest: Int=0,actionData: String?=null) {
    var prefs by remember { mutableStateOf(runtime.preferences) }
    var now by remember { mutableStateOf(Instant.now()) }
    var session by remember { mutableStateOf(runtime.displayedSession()) }
    val scope=rememberCoroutineScope()
    var saving by remember {mutableStateOf(false)}
    var trail by rememberSaveable {mutableStateOf(listOf(Screen.HOME.name))}
    var cityDialog by rememberSaveable {mutableStateOf(false)}
    var secondCityDialog by rememberSaveable {mutableStateOf(false)}
    var adhkarTab by rememberSaveable {mutableIntStateOf(0)}
    var mosqueImport by rememberSaveable {mutableStateOf<String?>(null)}
    var mosques by remember {mutableStateOf(runtime.mosqueList())}
    var log by remember {mutableStateOf(runtime.log())}
    val whatsNewStore=LocalContext.current.let {ctx->remember(ctx) {ctx.getSharedPreferences("whats-new",0)}}
    var whatsNew by remember {mutableStateOf(false)}
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
    fun silenceNow() {
        scope.launch {
            withContext(prayerDispatcher) {runtime.manualSilence()}
            session=runtime.displayedSession()
        }
    }
    // Shortcuts, the quick-settings tile and the travel notification open the app with an action.
    LaunchedEffect(actionRequest) {
        when(action) {
            org.najwa.app.platform.ACTION_SILENCE_NOW -> {trail=navigateScreens(trail,Screen.SILENCE,true);silenceNow()}
            org.najwa.app.platform.ACTION_QIBLA -> trail=navigateScreens(navigateScreens(trail,Screen.HOME,true),Screen.QIBLA)
            org.najwa.app.platform.ACTION_CHOOSE_CITY -> cityDialog=true
            org.najwa.app.platform.ACTION_ADHKAR -> {adhkarTab=actionData?.toIntOrNull() ?: 0;trail=navigateScreens(navigateScreens(trail,Screen.HOME,true),Screen.ADHKAR)}
            org.najwa.app.platform.ACTION_HEALTH -> trail=navigateScreens(trail,Screen.SETTINGS,true)
            org.najwa.app.platform.ACTION_PRAYER_LOG -> {log=runtime.log();trail=navigateScreens(navigateScreens(trail,Screen.HOME,true),Screen.PRAYER_LOG)}
            org.najwa.app.platform.ACTION_TASBEEH -> trail=navigateScreens(navigateScreens(trail,Screen.HOME,true),Screen.TASBEEH)
            org.najwa.app.platform.ACTION_QURAN -> trail=navigateScreens(navigateScreens(trail,Screen.HOME,true),Screen.QURAN)
            ACTION_MOSQUE -> {mosqueImport=actionData;trail=navigateScreens(navigateScreens(trail,Screen.SETTINGS,true),Screen.MOSQUES)}
        }
    }
    fun back() {trail=backScreens(trail)}
    BackHandler(!cityDialog && (onboarding || trail.size>1)) {if(onboarding)finishSetup() else back()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) { val observer=LifecycleEventObserver { _,_->visible=lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }; lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) } }
    LaunchedEffect(visible,resumed) { if(visible) { prefs=runtime.preferences; while(true) { now=Instant.now(); session=runtime.displayedSession(); delay(1000) } } }
    val dark=when(prefs.theme) { ThemeChoice.DARK->true;ThemeChoice.LIGHT->false;else->isSystemInDarkTheme() }
    val activity=androidx.activity.compose.LocalActivity.current
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
    fun save(p: Preferences, onSaved: ()->Unit={}) {
        if(!cityConfirmed && (p.autoSilent || p.adhanNotifications || p.iqamaNotifications || p.fridayReminder)) {
            cityDialog=true
            return
        }
        if(saving)return
        saving=true
        scope.launch {
            try {
                withContext(prayerDispatcher) { runtime.update(p) }
                prefs=runtime.preferences
                session=runtime.displayedSession()
                if(prefs==p)onSaved()
            } finally { saving=false }
        }
    }
    CompositionLocalProvider(LocalPalette provides colors) {
        NajwaTheme(dark,prefs.dynamicColor) {
            Column(Modifier.fillMaxSize().background(colors.bg).safeDrawingPadding(),horizontalAlignment=Alignment.CenterHorizontally) {
                PageScroll(screen) {
                    runtime.error?.let { Text(it,color=colors.amber,modifier=Modifier.padding(vertical=10.dp)) }
                    runtime.notice?.let {Text(it,color=colors.mint,modifier=Modifier.padding(vertical=10.dp))}
                    when(screen) {
                        Screen.PERMISSIONS -> if(onboarding) SetupWizard(runtime,prefs,cityConfirmed,
                            onChooseCity={cityDialog=true},onConfirmCity=::confirmCity,onSave={save(it)},
                            onFinish={choice,advanced->
                                // Alerts follow the person's choice (Settings flags missing permissions);
                                // silence is only switched on when the phone can really change the ringer.
                                save(prefs.copy(
                                    adhanNotifications=prefs.adhanNotifications || choice.adhan,
                                    iqamaNotifications=prefs.iqamaNotifications || choice.iqama,
                                    fridayReminder=prefs.fridayReminder || choice.friday,
                                    autoSilent=prefs.autoSilent || (choice.silence && runtime.audio.capable() && runtime.alarms.exactAccess)
                                ))
                                finishSetup()
                                if(advanced)trail=navigateScreens(trail,Screen.SETTINGS,true)
                            },
                            onSkip={finishSetup()})
                        else PermissionsScreen(runtime,
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
                        Screen.NOTIFICATIONS -> NotificationScreen(runtime,prefs,::save,::back,resumed) {navigate(Screen.SOUND)}
                        Screen.MOSQUES -> {
                            MosqueScreen(runtime,prefs,mosqueImport,{mosqueImport=null},::back) {prefs=runtime.preferences;mosques=runtime.mosqueList()}
                            IqamaSettings(prefs) {save(it)}
                        }
                        Screen.SOUND -> SoundScreen(runtime,prefs,{save(it)},{openSystemIntent(context,it)},::back)
                        Screen.SUNNAH -> SunnahScreen(prefs,{save(it)},::back)
                        Screen.ADVANCED -> AdvancedScreen(prefs,{save(it)},{secondCityDialog=true},{navigate(Screen.BACKUP)},::back)
                        Screen.TASBEEH -> TasbeehScreen(::back)
                        Screen.ADHKAR -> AdhkarScreen(adhkarTab,::back)
                        Screen.TIMETABLE -> TimetableScreen(prefs,::back)
                        Screen.PRAYER_LOG -> PrayerLogScreen(runtime,prefs,{save(it)},{adhkarTab=2;navigate(Screen.ADHKAR)}) {log=runtime.log();back()}
                        Screen.QURAN -> QuranScreen(::back)
                        Screen.HOME -> {
                            if(!cityConfirmed) {
                                Text(stringResource(R.string.city_unconfirmed),color=colors.amber,modifier=Modifier.padding(vertical=8.dp))
                                TextButton(onClick={cityDialog=true}) {Text(stringResource(R.string.en_setup_choose_city))}
                            }
                            HomeScreen(prefs,now,current,times,runtime.error==null && days.all {it.complete} && prefs.enabled.isNotEmpty() && runtime.audio.capable() && runtime.alarms.exactAccess,onCity={cityDialog=true},
                                onSilence={navigate(Screen.SILENCE)},onQibla={navigate(Screen.QIBLA)},activeUntil=session?.takeIf {it.state==SessionState.ACTIVE}?.let {Instant.ofEpochMilli(it.deadline)},
                                tomorrow=days.firstOrNull {it.date==current.date.plusDays(1)},logged=if(prefs.prayerLog)log.days[current.date].orEmpty() else emptySet(),
                                onTogglePrayed=if(prefs.prayerLog) {prayer->scope.launch {withContext(prayerDispatcher) {runtime.setPrayed(current.date,prayer,!log.prayed(current.date,prayer))};log=runtime.log()}} else null,
                                onTool={navigate(it)},mosqueName=prefs.activeMosque?.let {id->mosques.firstOrNull {it.id==id}?.name},onAdhkar={adhkarTab=it;navigate(Screen.ADHKAR)})
                        }
                        Screen.SILENCE -> SilenceScreen(runtime,prefs,session,now,onSave={save(it)},onAccess={navigate(Screen.PERMISSIONS)},onRestore={
                            scope.launch { withContext(prayerDispatcher) {runtime.restore()};session=runtime.displayedSession() }
                        },onSilenceNow=::silenceNow,resumed=resumed)
                        Screen.QIBLA -> QiblaScreen(prefs,::back) {cityDialog=true}
                        Screen.BACKUP -> BackupScreen(runtime,::back) {prefs=runtime.preferences}
                        Screen.SETTINGS -> {
                            val status=remember(resumed,prefs) {SettingsStatus(
                                runtime.notifications.granted && runtime.alarms.exactAccess,
                                runtime.audio.capable() && runtime.alarms.exactAccess,
                                runtime.audio.fixed)}
                            SettingsScreen(prefs,onSave=::save,onCity={cityDialog=true},onNotifications={navigate(Screen.NOTIFICATIONS)},onWidget={navigate(Screen.WIDGET)},onInformation={navigate(Screen.INFORMATION)},onCalculation={navigate(Screen.CALCULATION)},onAppearance={navigate(Screen.APPEARANCE)},onSilence={navigate(Screen.SILENCE)},
                                status=status,onSilenceSetup={navigate(Screen.PERMISSIONS)},onBackup={navigate(Screen.BACKUP)},onNavigate={navigate(it)},
                                health=remember(resumed,prefs,session) {org.najwa.app.platform.healthIssues(context,runtime)},
                                onFix={issue->openSystemIntent(context,fixIntent(context,issue,runtime.notifications.adhanChannel)) ?: navigate(Screen.SOUND)})
                        }
                        Screen.CALCULATION -> CalculationScreen(prefs,::save,{cityDialog=true},::back) {secondCityDialog=true}
                        Screen.APPEARANCE -> AppearanceScreen(prefs,::save,::back) {navigate(Screen.WIDGET)}
                        Screen.WIDGET -> WidgetScreen(prefs,now,::back,::save)
                        else -> InformationScreen(screen,::navigate,::back)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if(!onboarding && !editingContact)BottomNavigation(screen.tab) {trail=navigateScreens(trail,Screen.forTab(it),true)}
            }
            if(cityDialog) CityDialog(prefs.city,onDismiss={cityDialog=false},onSelect={
                // First choice of city: also use the usual calculation method of its country.
                val method=if(!cityConfirmed) suggestedMethod(it.countryCode) ?: prefs.method else prefs.method
                save(prefs.copy(city=it,method=method)) { confirmCity();cityDialog=false }
            })
            if(secondCityDialog) CityDialog(prefs.secondCity ?: prefs.city,onDismiss={secondCityDialog=false},onSelect={
                save(prefs.copy(secondCity=it)) {secondCityDialog=false}
            })
            LaunchedEffect(onboarding) {
                // Show what's new once after an update (not on a fresh install).
                val seen=whatsNewStore.getInt("seen",0)
                val upgraded=runCatching {context.packageManager.getPackageInfo(context.packageName,0).let {it.firstInstallTime!=it.lastUpdateTime}}.getOrDefault(false)
                if(!onboarding && (seen in 1 until org.najwa.app.BuildConfig.VERSION_CODE || seen==0 && upgraded))whatsNew=true
                if(!onboarding)whatsNewStore.edit().putInt("seen",org.najwa.app.BuildConfig.VERSION_CODE).apply()
            }
            if(whatsNew)WhatsNewDialog {whatsNew=false}
        }
    }
}
/** Keep each scroll node with its own state and content. Never replace only the
 * scroll state of a node that is still attached during a navigation transition. */
@Composable fun ColumnScope.PageScroll(screen: Screen,content: @Composable ColumnScope.()->Unit) {
    val stateHolder=rememberSaveableStateHolder()
    LaunchedEffect(screen) {
        // Preserve a draft during configuration recreation, but clear it on leaving.
        if(screen!=Screen.CONTACT)stateHolder.removeState(Screen.CONTACT.name)
    }
    stateHolder.SaveableStateProvider(screen.name) {
        val scroll=rememberScrollState()
        val entrance=remember {Animatable(1f)}
        LaunchedEffect(screen) {entrance.snapTo(.92f);entrance.animateTo(1f,tween(180))}
        Column(Modifier.weight(1f).widthIn(max=600.dp).fillMaxWidth()
            .graphicsLayer {alpha=entrance.value;translationY=(1f-entrance.value)*80f}
            .verticalScroll(scroll).padding(horizontal=if(screen==Screen.HOME)17.dp else 20.dp),content=content)
    }
}
@Composable fun BottomNavigation(tab: Int,onTab: (Int)->Unit) {
    val c=LocalPalette.current
    Column {
        HorizontalDivider(color=c.border)
        NavigationBar(containerColor=c.surface,tonalElevation=0.dp,windowInsets=WindowInsets(0,0,0,0)) {
        listOf(stringResource(R.string.en_tab_home) to "home",stringResource(R.string.en_tab_silence) to "sound",stringResource(R.string.en_tab_settings) to "settings").forEachIndexed { i,item ->
            NavigationBarItem(selected=i==tab,onClick={onTab(i)},icon={Glyph(item.second,if(i==tab)c.mint else c.secondary)},
                label={Text(item.first,fontSize=12.sp,fontWeight=if(i==tab)FontWeight.Bold else FontWeight.Normal)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=c.selected,selectedTextColor=c.mint,unselectedTextColor=c.secondary))
        }
        }
    }
}
@Composable fun najwaTypography(): Typography {
    val font=FontFamily(Font(R.font.noto_sans_arabic_regular),Font(R.font.noto_sans_arabic_bold,FontWeight.Bold))
    val t=Typography()
    return t.copy(bodyLarge=t.bodyLarge.copy(fontFamily=font),bodyMedium=t.bodyMedium.copy(fontFamily=font),bodySmall=t.bodySmall.copy(fontFamily=font),titleLarge=t.titleLarge.copy(fontFamily=font),titleMedium=t.titleMedium.copy(fontFamily=font),titleSmall=t.titleSmall.copy(fontFamily=font),labelLarge=t.labelLarge.copy(fontFamily=font),labelMedium=t.labelMedium.copy(fontFamily=font),labelSmall=t.labelSmall.copy(fontFamily=font),headlineLarge=t.headlineLarge.copy(fontFamily=font),headlineMedium=t.headlineMedium.copy(fontFamily=font),headlineSmall=t.headlineSmall.copy(fontFamily=font),displayLarge=t.displayLarge.copy(fontFamily=font),displayMedium=t.displayMedium.copy(fontFamily=font),displaySmall=t.displaySmall.copy(fontFamily=font))
}

const val ACTION_MOSQUE="org.najwa.action.MOSQUE"

/** Opens a system settings page; returns null when it could not be opened. */
internal fun openSystemIntent(context: android.content.Context,intent: android.content.Intent?): Unit? =
    intent?.let {if(org.najwa.app.platform.openSystemSettings(context,it)) Unit else null}

/** The settings page that fixes a health issue (null: handled inside Najwa). */
internal fun fixIntent(context: android.content.Context,issue: org.najwa.app.platform.HealthIssue,adhanChannel: String): android.content.Intent? {
    val pkg=context.packageName
    return when(issue) {
        org.najwa.app.platform.HealthIssue.NOTIFICATIONS,org.najwa.app.platform.HealthIssue.CHANNELS ->
            android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,pkg)
        org.najwa.app.platform.HealthIssue.EXACT_ALARMS -> if(android.os.Build.VERSION.SDK_INT>=31)
            android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:$pkg")) else null
        org.najwa.app.platform.HealthIssue.RINGER_ACCESS -> android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        org.najwa.app.platform.HealthIssue.BATTERY -> android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        org.najwa.app.platform.HealthIssue.SOUND -> null
    }
}
