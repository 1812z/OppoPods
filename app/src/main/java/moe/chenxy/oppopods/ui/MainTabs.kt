package moe.chenxy.oppopods.ui

import android.bluetooth.BluetoothDevice
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.libxposed.service.XposedService
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.config.EarphonePref
import moe.chenxy.oppopods.config.PodImageResource
import moe.chenxy.oppopods.pods.GameModeImplementation
import moe.chenxy.oppopods.pods.NoiseControlMode
import moe.chenxy.oppopods.pods.WearStatus
import moe.chenxy.oppopods.ui.dialogs.RestartScope
import moe.chenxy.oppopods.ui.dialogs.RestartScopeDialog
import moe.chenxy.oppopods.ui.dialogs.MelodyImageImportDialog
import moe.chenxy.oppopods.ui.dialogs.PodImageConfigDialog
import moe.chenxy.oppopods.ui.pages.EarphonesTabPage
import moe.chenxy.oppopods.ui.pages.HomePage
import moe.chenxy.oppopods.ui.pages.SettingsPage
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun MainTabsScaffold(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    floatingBottomBar: Boolean,
    blurBottomBar: Boolean,
    backdrop: LayerBackdrop?,
    backgroundColor: Color,
    overlayBottomBar: Boolean,
    pageBottomContentPadding: Dp,
    xposedService: XposedService?,
    bluetoothServiceResponsive: Boolean,
    bluetoothEnabled: Boolean,
    bondedDeviceCount: Int,
    onBluetoothStatusClick: () -> Unit,
    onPairedBluetoothClick: () -> Unit,
    showEarphoneDetail: Boolean,
    mainTitle: String,
    displayTitle: String,
    displayBattery: BatteryParams,
    displayWearStatus: WearStatus,
    displayAnc: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    displayTransparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    displayGameMode: Boolean,
    onGameModeChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
    eqPreset: Int,
    onEqPresetChange: (Int) -> Unit,
    eqSupported: Boolean,
    displayDualDeviceConnection: Boolean,
    onDualDeviceConnectionChange: (Boolean) -> Unit,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    adaptiveModeEnabled: Boolean,
    earphonePrefs: List<EarphonePref>,
    connectedDeviceAddress: String,
    connectingDeviceAddress: String?,
    showConnectErrorDialog: Boolean,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onConnectedDeviceClick: () -> Unit,
    onDeviceDisconnect: (BluetoothDevice) -> Unit,
    onDismissConnectError: () -> Unit,
    desktopIconHidden: MutableState<Boolean>,
    onDesktopIconHiddenChange: (Boolean) -> Unit,
    logLevel: MutableState<Int>,
    onLogLevelChange: (Int) -> Unit,
    islandMode: MutableState<Int>,
    onIslandModeChange: (Int) -> Unit,
    islandShowTimings: MutableState<Set<Int>>,
    onIslandShowTimingsChange: (Set<Int>) -> Unit,
    appLanguage: MutableState<Int>,
    onAppLanguageChange: (Int) -> Unit,
    autoGameMode: MutableState<Boolean>,
    onAutoGameModeChange: (Boolean) -> Unit,
    gameModeImplementation: MutableState<GameModeImplementation>,
    onGameModeImplementationChange: (GameModeImplementation) -> Unit,
    notificationClickAction: MutableState<Int>,
    onNotificationClickActionChange: (Int) -> Unit,
    moreClickAction: MutableState<Int>,
    onMoreClickActionChange: (Int) -> Unit,
    adaptiveCapabilityOverride: MutableState<Int>,
    spatialAudioCapabilityOverride: MutableState<Int>,
    spatialSoundSwitchCapabilityOverride: MutableState<Int>,
    onOpenDeviceCapabilities: () -> Unit,
    onOpenRfcommDebug: () -> Unit,
    fakeDeviceId: MutableState<String>,
    onFakeDeviceIdChange: (String) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    showRestartScopeDialog: Boolean,
    restartingScopes: Boolean,
    onShowRestartScopeDialog: () -> Unit,
    onDismissRestartScopeDialog: () -> Unit,
    onRestartScopes: (List<String>) -> Unit,
    onBackToDevicePicker: () -> Unit,
    onOpenSystemHeadsetSettings: () -> Unit,
    onSavePodImages: (String, String, Map<PodImageResource, Uri?>, Set<PodImageResource>) -> Unit,
    onSavePodImageBytes: (String, String, Map<PodImageResource, ByteArray>) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { tabs.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val mainPagerState = remember(pagerState, coroutineScope) {
        MainTabsPagerState(pagerState, coroutineScope)
    }
    val isLandscapeDetail = selectedTab == MainTab.Earphones &&
            showEarphoneDetail &&
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val currentEarphonePref = earphonePrefs.firstOrNull {
        it.address.equals(connectedDeviceAddress, ignoreCase = true)
    }
    var showPodImageDialog by remember { mutableStateOf(false) }
    var showMelodyImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        val targetPage = selectedTab.ordinal
        if (mainPagerState.selectedPage != targetPage) {
            mainPagerState.animateToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    LaunchedEffect(mainPagerState.selectedPage, tabs) {
        val page = mainPagerState.selectedPage
        if (page in tabs.indices && selectedTab != tabs[page]) {
            onTabSelected(tabs[page])
        }
    }

    Scaffold(
        bottomBar = {
            MainBottomNavigation(
                tabs = tabs,
                selectedTab = tabs.getOrElse(mainPagerState.selectedPage) { selectedTab },
                floating = floatingBottomBar,
                blur = blurBottomBar,
                backdrop = backdrop,
                onTabClick = {
                    mainPagerState.animateToPage(it.ordinal)
                    onTabSelected(it)
                },
            )
        }
    ) { padding ->
        val contentPadding = if (overlayBottomBar) PaddingValues(0.dp) else PaddingValues(bottom = padding.calculateBottomPadding())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .padding(contentPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { page -> tabs[page] },
            ) { page ->
                when (tabs[page]) {
                    MainTab.Module -> ModuleTabPage(
                        xposedService = xposedService,
                        bluetoothServiceResponsive = bluetoothServiceResponsive,
                        bluetoothEnabled = bluetoothEnabled,
                        bondedDeviceCount = bondedDeviceCount,
                        onBluetoothStatusClick = onBluetoothStatusClick,
                        onPairedBluetoothClick = onPairedBluetoothClick,
                        onOpenRfcommDebug = onOpenRfcommDebug,
                        pageBottomContentPadding = pageBottomContentPadding,
                        restartingScopes = restartingScopes,
                        onShowRestartScopeDialog = onShowRestartScopeDialog,
                    )

                    MainTab.Earphones -> EarphonesTabShell(
                        isLandscapeDetail = isLandscapeDetail,
                        showEarphoneDetail = showEarphoneDetail,
                        mainTitle = mainTitle,
                        displayTitle = displayTitle,
                        displayBattery = displayBattery,
                        displayWearStatus = displayWearStatus,
                        displayAnc = displayAnc,
                        onAncModeChange = onAncModeChange,
                        displayTransparencyVocalEnhancement = displayTransparencyVocalEnhancement,
                        onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                        displayGameMode = displayGameMode,
                        onGameModeChange = onGameModeChange,
                        spatialAudioMode = spatialAudioMode,
                        onSpatialAudioModeChange = onSpatialAudioModeChange,
                        eqPreset = eqPreset,
                        onEqPresetChange = onEqPresetChange,
                        eqSupported = eqSupported,
                        displayDualDeviceConnection = displayDualDeviceConnection,
                        onDualDeviceConnectionChange = onDualDeviceConnectionChange,
                        spatialAudioSupported = spatialAudioSupported,
                        spatialSoundSupported = spatialSoundSupported,
                        adaptiveModeEnabled = adaptiveModeEnabled,
                        boxImagePath = currentEarphonePref?.boxImagePath,
                        connectedDeviceAddress = connectedDeviceAddress,
                        connectingDeviceAddress = connectingDeviceAddress,
                        showConnectErrorDialog = showConnectErrorDialog,
                        pageBottomContentPadding = pageBottomContentPadding,
                        onDeviceSelected = onDeviceSelected,
                        onConnectedDeviceClick = onConnectedDeviceClick,
                        onDeviceDisconnect = onDeviceDisconnect,
                        onDismissConnectError = onDismissConnectError,
                        onBackToDevicePicker = onBackToDevicePicker,
                        onOpenMelodyImport = { showMelodyImportDialog = true },
                        onOpenPodImageConfig = { showPodImageDialog = true },
                        onOpenSystemHeadsetSettings = onOpenSystemHeadsetSettings,
                    )

                    MainTab.Settings -> SettingsTabPage(
                        pageBottomContentPadding = pageBottomContentPadding,
                        desktopIconHidden = desktopIconHidden,
                        onDesktopIconHiddenChange = onDesktopIconHiddenChange,
                        logLevel = logLevel,
                        onLogLevelChange = onLogLevelChange,
                        islandMode = islandMode,
                        onIslandModeChange = onIslandModeChange,
                        islandShowTimings = islandShowTimings,
                        onIslandShowTimingsChange = onIslandShowTimingsChange,
                        appLanguage = appLanguage,
                        onAppLanguageChange = onAppLanguageChange,
                        autoGameMode = autoGameMode,
                        onAutoGameModeChange = onAutoGameModeChange,
                        gameModeImplementation = gameModeImplementation,
                        onGameModeImplementationChange = onGameModeImplementationChange,
                        notificationClickAction = notificationClickAction,
                        onNotificationClickActionChange = onNotificationClickActionChange,
                        moreClickAction = moreClickAction,
                        onMoreClickActionChange = onMoreClickActionChange,
                        adaptiveCapabilityOverride = adaptiveCapabilityOverride,
                        spatialAudioCapabilityOverride = spatialAudioCapabilityOverride,
                        spatialSoundSwitchCapabilityOverride = spatialSoundSwitchCapabilityOverride,
                        onOpenDeviceCapabilities = onOpenDeviceCapabilities,
                        fakeDeviceId = fakeDeviceId,
                        onFakeDeviceIdChange = onFakeDeviceIdChange,
                        onOpenTheme = onOpenTheme,
                        onOpenAbout = onOpenAbout,
                    )
                }
            }

            if (isLandscapeDetail) {
                LandscapeDetailActions(
                    onBackToDevicePicker = onBackToDevicePicker,
                    onOpenMelodyImport = { showMelodyImportDialog = true },
                    onOpenPodImageConfig = { showPodImageDialog = true },
                    onOpenSystemHeadsetSettings = onOpenSystemHeadsetSettings,
                )
            }
        }

        RestartScopeDialog(
            show = showRestartScopeDialog,
            scopes = restartScopeOptions,
            onDismissRequest = { if (!restartingScopes) onDismissRestartScopeDialog() },
            onConfirm = onRestartScopes,
        )

        PodImageConfigDialog(
            show = showPodImageDialog,
            earphones = earphonePrefs,
            currentAddress = connectedDeviceAddress,
            currentName = displayTitle,
            onDismissRequest = { showPodImageDialog = false },
            onSave = { address, name, images, clearedImages ->
                onSavePodImages(address, name, images, clearedImages)
                showPodImageDialog = false
            },
        )

        MelodyImageImportDialog(
            show = showMelodyImportDialog,
            currentAddress = connectedDeviceAddress,
            currentName = displayTitle,
            onDismissRequest = { showMelodyImportDialog = false },
            onImport = { address, name, images ->
                onSavePodImageBytes(address, name, images)
                showMelodyImportDialog = false
            },
        )
    }
}

@Composable
private fun ModuleTabPage(
    xposedService: XposedService?,
    bluetoothServiceResponsive: Boolean,
    bluetoothEnabled: Boolean,
    bondedDeviceCount: Int,
    onBluetoothStatusClick: () -> Unit,
    onPairedBluetoothClick: () -> Unit,
    onOpenRfcommDebug: () -> Unit,
    pageBottomContentPadding: Dp,
    restartingScopes: Boolean,
    onShowRestartScopeDialog: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onOpenRfcommDebug) {
                        Icon(imageVector = MiuixIcons.Months, contentDescription = "RFCOMM debug")
                    }
                    IconButton(
                        onClick = {
                            if (!restartingScopes) onShowRestartScopeDialog()
                        }
                    ) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "Restart scope")
                    }
                },
            )
        },
    ) { pagePadding ->
        HomePage(
            modifier = Modifier
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            xposedService = xposedService,
            bluetoothServiceResponsive = bluetoothServiceResponsive,
            bluetoothEnabled = bluetoothEnabled,
            bondedDeviceCount = bondedDeviceCount,
            onBluetoothStatusClick = onBluetoothStatusClick,
            onPairedBluetoothClick = onPairedBluetoothClick,
            contentPadding = pagePadding,
            bottomContentPadding = pageBottomContentPadding,
        )
    }
}

@Composable
private fun EarphonesTabShell(
    isLandscapeDetail: Boolean,
    showEarphoneDetail: Boolean,
    mainTitle: String,
    displayTitle: String,
    displayBattery: BatteryParams,
    displayWearStatus: WearStatus,
    displayAnc: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    displayTransparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    displayGameMode: Boolean,
    onGameModeChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
    eqPreset: Int,
    onEqPresetChange: (Int) -> Unit,
    eqSupported: Boolean,
    displayDualDeviceConnection: Boolean,
    onDualDeviceConnectionChange: (Boolean) -> Unit,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    adaptiveModeEnabled: Boolean,
    boxImagePath: String?,
    connectedDeviceAddress: String,
    connectingDeviceAddress: String?,
    showConnectErrorDialog: Boolean,
    pageBottomContentPadding: Dp,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onConnectedDeviceClick: () -> Unit,
    onDeviceDisconnect: (BluetoothDevice) -> Unit,
    onDismissConnectError: () -> Unit,
    onBackToDevicePicker: () -> Unit,
    onOpenMelodyImport: () -> Unit,
    onOpenPodImageConfig: () -> Unit,
    onOpenSystemHeadsetSettings: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            if (!isLandscapeDetail) {
                TopAppBar(
                    title = mainTitle.ifEmpty { stringResource(R.string.pod_info) },
                    largeTitle = mainTitle.ifEmpty { stringResource(R.string.pod_info) },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        if (showEarphoneDetail) {
                            IconButton(onClick = onBackToDevicePicker) {
                                Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (showEarphoneDetail) {
                            EarphoneDetailActions(
                                onOpenMelodyImport = onOpenMelodyImport,
                                onOpenPodImageConfig = onOpenPodImageConfig,
                                onOpenSystemHeadsetSettings = onOpenSystemHeadsetSettings,
                            )
                        }
                    },
                )
            }
        },
    ) { pagePadding ->
        EarphonesTabPage(
            showEarphoneDetail = showEarphoneDetail,
            displayTitle = displayTitle,
            displayBattery = displayBattery,
            displayWearStatus = displayWearStatus,
            displayAnc = displayAnc,
            onAncModeChange = onAncModeChange,
            displayTransparencyVocalEnhancement = displayTransparencyVocalEnhancement,
            onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
            displayGameMode = displayGameMode,
            onGameModeChange = onGameModeChange,
            spatialAudioMode = spatialAudioMode,
            onSpatialAudioModeChange = onSpatialAudioModeChange,
            eqPreset = eqPreset,
            onEqPresetChange = onEqPresetChange,
            eqSupported = eqSupported,
            displayDualDeviceConnection = displayDualDeviceConnection,
            onDualDeviceConnectionChange = onDualDeviceConnectionChange,
            spatialAudioSupported = spatialAudioSupported,
            spatialSoundSupported = spatialSoundSupported,
            adaptiveModeEnabled = adaptiveModeEnabled,
            boxImagePath = boxImagePath,
            connectedDeviceAddress = connectedDeviceAddress,
            connectingDeviceAddress = connectingDeviceAddress,
            showConnectErrorDialog = showConnectErrorDialog,
            contentPadding = pagePadding,
            pageBottomContentPadding = pageBottomContentPadding,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            onDeviceSelected = onDeviceSelected,
            onConnectedDeviceClick = onConnectedDeviceClick,
            onDeviceDisconnect = onDeviceDisconnect,
            onDismissConnectError = onDismissConnectError,
        )
    }
}

@Composable
private fun SettingsTabPage(
    pageBottomContentPadding: Dp,
    desktopIconHidden: MutableState<Boolean>,
    onDesktopIconHiddenChange: (Boolean) -> Unit,
    logLevel: MutableState<Int>,
    onLogLevelChange: (Int) -> Unit,
    islandMode: MutableState<Int>,
    onIslandModeChange: (Int) -> Unit,
    islandShowTimings: MutableState<Set<Int>>,
    onIslandShowTimingsChange: (Set<Int>) -> Unit,
    appLanguage: MutableState<Int>,
    onAppLanguageChange: (Int) -> Unit,
    autoGameMode: MutableState<Boolean>,
    onAutoGameModeChange: (Boolean) -> Unit,
    gameModeImplementation: MutableState<GameModeImplementation>,
    onGameModeImplementationChange: (GameModeImplementation) -> Unit,
    notificationClickAction: MutableState<Int>,
    onNotificationClickActionChange: (Int) -> Unit,
    moreClickAction: MutableState<Int>,
    onMoreClickActionChange: (Int) -> Unit,
    adaptiveCapabilityOverride: MutableState<Int>,
    spatialAudioCapabilityOverride: MutableState<Int>,
    spatialSoundSwitchCapabilityOverride: MutableState<Int>,
    onOpenDeviceCapabilities: () -> Unit,
    fakeDeviceId: MutableState<String>,
    onFakeDeviceIdChange: (String) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings),
                largeTitle = stringResource(R.string.settings),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { pagePadding ->
        SettingsPage(
            modifier = Modifier
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = pagePadding.calculateTopPadding(),
                bottom = pageBottomContentPadding,
            ),
            desktopIconHidden = desktopIconHidden,
            onDesktopIconHiddenChange = onDesktopIconHiddenChange,
            logLevel = logLevel,
            onLogLevelChange = onLogLevelChange,
            islandMode = islandMode,
            onIslandModeChange = onIslandModeChange,
            islandShowTimings = islandShowTimings,
            onIslandShowTimingsChange = onIslandShowTimingsChange,
            appLanguage = appLanguage,
            onAppLanguageChange = onAppLanguageChange,
            autoGameMode = autoGameMode,
            onAutoGameModeChange = onAutoGameModeChange,
            gameModeImplementation = gameModeImplementation,
            onGameModeImplementationChange = onGameModeImplementationChange,
            notificationClickAction = notificationClickAction,
            onNotificationClickActionChange = onNotificationClickActionChange,
            moreClickAction = moreClickAction,
            onMoreClickActionChange = onMoreClickActionChange,
            adaptiveCapabilityOverride = adaptiveCapabilityOverride,
            spatialAudioCapabilityOverride = spatialAudioCapabilityOverride,
            spatialSoundSwitchCapabilityOverride = spatialSoundSwitchCapabilityOverride,
            onOpenDeviceCapabilities = onOpenDeviceCapabilities,
            fakeDeviceId = fakeDeviceId,
            onFakeDeviceIdChange = onFakeDeviceIdChange,
            onOpenTheme = onOpenTheme,
            onOpenAbout = onOpenAbout,
        )
    }
}

@Composable
private fun LandscapeDetailActions(
    onBackToDevicePicker: () -> Unit,
    onOpenMelodyImport: () -> Unit,
    onOpenPodImageConfig: () -> Unit,
    onOpenSystemHeadsetSettings: () -> Unit,
) {
    IconButton(
        modifier = Modifier
            .padding(top = 8.dp, start = 8.dp)
            .zIndex(1f),
        onClick = onBackToDevicePicker,
    ) {
        Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
    }
    Box(Modifier.fillMaxSize()) {
        IconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 104.dp)
                .zIndex(1f),
            onClick = onOpenMelodyImport,
        ) {
            Icon(
                imageVector = MiuixIcons.Import,
                contentDescription = stringResource(R.string.import_melody_images),
            )
        }
        IconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 56.dp)
                .zIndex(1f),
            onClick = onOpenPodImageConfig,
        ) {
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = stringResource(R.string.custom_pod_images),
            )
        }
        IconButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .zIndex(1f),
            onClick = onOpenSystemHeadsetSettings,
        ) {
            Icon(
                imageVector = MiuixIcons.Settings,
                contentDescription = stringResource(R.string.click_action_system_settings),
            )
        }
    }
}

@Composable
private fun EarphoneDetailActions(
    onOpenMelodyImport: () -> Unit,
    onOpenPodImageConfig: () -> Unit,
    onOpenSystemHeadsetSettings: () -> Unit,
) {
    IconButton(onClick = onOpenMelodyImport) {
        Icon(
            imageVector = MiuixIcons.Import,
            contentDescription = stringResource(R.string.import_melody_images),
        )
    }
    IconButton(onClick = onOpenPodImageConfig) {
        Icon(
            imageVector = MiuixIcons.Edit,
            modifier = Modifier.size(23.dp),
            contentDescription = stringResource(R.string.custom_pod_images),
        )
    }
    IconButton(onClick = onOpenSystemHeadsetSettings) {
        Icon(
            imageVector = MiuixIcons.Settings,
            contentDescription = stringResource(R.string.click_action_system_settings),
        )
    }
}

private val restartScopeOptions = listOf(
    RestartScope("com.android.bluetooth", "Bluetooth"),
    RestartScope("com.milink.service", "MiLink Service"),
    RestartScope("com.xiaomi.bluetooth", "Mi Bluetooth"),
)
