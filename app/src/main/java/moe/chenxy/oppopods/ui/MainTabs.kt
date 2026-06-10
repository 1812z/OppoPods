package moe.chenxy.oppopods.ui

import android.bluetooth.BluetoothDevice
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.config.EarphonePref
import moe.chenxy.oppopods.config.PodImageResource
import moe.chenxy.oppopods.pods.GameModeImplementation
import moe.chenxy.oppopods.pods.NoiseControlMode
import moe.chenxy.oppopods.pods.WearStatus
import moe.chenxy.oppopods.ui.components.AppIcons
import moe.chenxy.oppopods.ui.components.RestartScope
import moe.chenxy.oppopods.ui.components.RestartScopeDialog
import moe.chenxy.oppopods.ui.pages.DevicePickerPage
import moe.chenxy.oppopods.ui.pages.HomePage
import moe.chenxy.oppopods.ui.pages.PodDetailPage
import moe.chenxy.oppopods.ui.pages.SettingsPage
import moe.chenxy.oppopods.utils.MelodyImageCandidate
import moe.chenxy.oppopods.utils.RootManager
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.abs

internal enum class MainTab(val icon: ImageVector) {
    Module(AppIcons.Home),
    Earphones(AppIcons.Headphones),
    Settings(MiuixIcons.Settings),
}

private class MainTabsPagerState(
    val pagerState: PagerState,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetPage: Int) {
        if (targetPage == selectedPage) return

        navJob?.cancel()
        selectedPage = targetPage
        isNavigating = true

        val layoutInfo = pagerState.layoutInfo
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distanceInPages = targetPage - pagerState.currentPage - pagerState.currentPageOffsetFraction
        val scrollPixels = distanceInPages * pageSize
        val duration = 100 * abs(targetPage - pagerState.currentPage).coerceAtLeast(2) + 100

        navJob = coroutineScope.launch {
            val currentJob = coroutineContext.job
            try {
                pagerState.animateScrollBy(
                    value = scrollPixels,
                    animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                )
            } finally {
                if (navJob == currentJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetPage) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

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
            BottomNavigation(
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
                    MainTab.Module -> {
                        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = stringResource(R.string.app_name),
                                    largeTitle = stringResource(R.string.app_name),
                                    scrollBehavior = scrollBehavior,
                                    actions = {
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
                                contentPadding = pagePadding,
                                bottomContentPadding = pageBottomContentPadding,
                            )
                        }
                    }

                    MainTab.Earphones -> {
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
                                                IconButton(onClick = { showMelodyImportDialog = true }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Import,
                                                        contentDescription = stringResource(R.string.import_melody_images),
                                                    )
                                                }
                                                IconButton(onClick = { showPodImageDialog = true }) {
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
                                boxImagePath = currentEarphonePref?.boxImagePath,
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

                    MainTab.Settings -> {
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
                }
            }

            if (isLandscapeDetail) {
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                        .zIndex(1f),
                    onClick = onBackToDevicePicker,
                ) {
                    Icon(imageVector = MiuixIcons.Back, contentDescription = "Back")
                }
                IconButton(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 104.dp)
                        .zIndex(1f),
                    onClick = { showMelodyImportDialog = true },
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
                    onClick = { showPodImageDialog = true },
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
private fun EarphonesTabPage(
    showEarphoneDetail: Boolean,
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
    contentPadding: PaddingValues,
    pageBottomContentPadding: Dp,
    nestedScrollConnection: NestedScrollConnection,
    onDeviceSelected: (BluetoothDevice) -> Unit,
    onConnectedDeviceClick: () -> Unit,
    onDeviceDisconnect: (BluetoothDevice) -> Unit,
    onDismissConnectError: () -> Unit,
) {
    AnimatedContent(
        targetState = showEarphoneDetail,
        modifier = Modifier.fillMaxSize(),
        label = "EarphonesPageAnim",
    ) { detailVisible ->
        if (detailVisible) {
            PodDetailPage(
                modifier = Modifier
                    .overScrollVertical()
                    .nestedScroll(nestedScrollConnection),
                contentPadding = contentPadding,
                bottomContentPadding = pageBottomContentPadding,
                podName = displayTitle.ifEmpty { stringResource(R.string.pod_info) },
                batteryParams = displayBattery,
                wearStatus = displayWearStatus,
                ancMode = displayAnc,
                onAncModeChange = onAncModeChange,
                transparencyVocalEnhancement = displayTransparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                gameMode = displayGameMode,
                onGameModeChange = onGameModeChange,
                spatialAudioMode = spatialAudioMode,
                onSpatialAudioModeChange = onSpatialAudioModeChange,
                dualDeviceConnection = displayDualDeviceConnection,
                onDualDeviceConnectionChange = onDualDeviceConnectionChange,
                spatialAudioSupported = spatialAudioSupported,
                spatialSoundSupported = spatialSoundSupported,
                adaptiveModeEnabled = adaptiveModeEnabled,
                eqPreset = eqPreset,
                onEqPresetChange = onEqPresetChange,
                eqSupported = eqSupported,
                boxImagePath = boxImagePath,
            )
        } else {
            DevicePickerPage(
                connectedDeviceName = displayTitle,
                connectedDeviceAddress = connectedDeviceAddress,
                connectingDeviceAddress = connectingDeviceAddress,
                showConnectError = showConnectErrorDialog,
                contentPadding = contentPadding,
                bottomContentPadding = pageBottomContentPadding,
                onDeviceSelected = onDeviceSelected,
                onConnectedDeviceClick = onConnectedDeviceClick,
                onDeviceDisconnect = onDeviceDisconnect,
                onDismissConnectError = onDismissConnectError,
            )
        }
    }
}

@Composable
private fun MelodyImageImportDialog(
    show: Boolean,
    currentAddress: String,
    currentName: String,
    onDismissRequest: () -> Unit,
    onImport: (String, String, Map<PodImageResource, ByteArray>) -> Unit,
) {
    var candidates by remember(show) { mutableStateOf<List<MelodyImageCandidate>>(emptyList()) }
    var selectedCandidate by remember(show) { mutableStateOf<MelodyImageCandidate?>(null) }
    var loading by remember(show) { mutableStateOf(false) }
    var importing by remember(show) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        loading = true
        candidates = withContext(Dispatchers.IO) { RootManager.scanMelodyImageCandidates() }
        selectedCandidate = candidates.firstOrNull()
        loading = false
    }

    OverlayDialog(
        title = stringResource(R.string.import_melody_images),
        summary = stringResource(R.string.import_melody_images_summary),
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(R.string.import_melody_images_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfiniteProgressIndicator()
            }
        } else if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.import_melody_images_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(candidates, key = { it.imageDir }) { candidate ->
                    MelodyImageCandidateRow(
                        candidate = candidate,
                        selected = candidate.imageDir == selectedCandidate?.imageDir,
                        onClick = { selectedCandidate = candidate },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                text = stringResource(R.string.import_melody_images_action),
                onClick = {
                    val candidate = selectedCandidate ?: return@TextButton
                    if (importing) return@TextButton
                    importing = true
                    scope.launch {
                        val images: Map<PodImageResource, ByteArray> = withContext(Dispatchers.IO) {
                            val paths: Map<PodImageResource, String> = mapOf(
                                PodImageResource.BOX to candidate.boxPath,
                                PodImageResource.LEFT to candidate.rightPath,
                                PodImageResource.RIGHT to candidate.leftPath,
                            )
                            paths.mapNotNull { (resource, path) ->
                                RootManager.readMelodyImage(path)?.takeIf { it.isNotEmpty() }?.let { bytes ->
                                    resource to bytes
                                }
                            }.toMap()
                        }
                        importing = false
                        if (images.size == PodImageResource.entries.size) {
                            onImport(currentAddress, currentName, images)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun MelodyImageCandidateRow(
    candidate: MelodyImageCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val previewPainter = remember(candidate.imageDir) {
        BitmapFactory.decodeByteArray(candidate.boxBytes, 0, candidate.boxBytes.size)
    }?.let { bitmap -> BitmapPainter(bitmap.asImageBitmap()) }
        ?: painterResource(R.drawable.img_box)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = previewPainter,
            contentDescription = candidate.label,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.label,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = candidate.imageDir,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PodImageConfigDialog(
    show: Boolean,
    earphones: List<EarphonePref>,
    currentAddress: String,
    currentName: String,
    onDismissRequest: () -> Unit,
    onSave: (String, String, Map<PodImageResource, Uri?>, Set<PodImageResource>) -> Unit,
) {
    val target = earphones.firstOrNull { it.address.equals(currentAddress, ignoreCase = true) }
        ?: EarphonePref(address = currentAddress, name = currentName)
    var selectedResource by remember(show) { mutableStateOf(PodImageResource.BOX) }
    var selectedImages by remember(show, target.address) { mutableStateOf<Map<PodImageResource, Uri?>>(emptyMap()) }
    var clearedImages by remember(show, target.address) { mutableStateOf<Set<PodImageResource>>(emptySet()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImages = selectedImages + (selectedResource to uri)
            clearedImages = clearedImages - selectedResource
        }
    }

    OverlayDialog(
        title = stringResource(R.string.custom_pod_images),
        summary = target.name.ifBlank { target.address },
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PodImageResource.entries.forEach { resource ->
                PodImageResourceRow(
                    resource = resource,
                    selectedUri = selectedImages[resource],
                    savedPath = target.imagePath(resource).takeUnless { resource in clearedImages },
                    title = stringResource(resource.titleRes()),
                    summary = if (resource !in clearedImages && (selectedImages[resource] != null || target.imagePath(resource) != null)) {
                        stringResource(R.string.custom_image_selected)
                    } else {
                        stringResource(R.string.custom_image_default)
                    },
                    clearable = resource !in clearedImages && (selectedImages[resource] != null || target.imagePath(resource) != null),
                    onClick = {
                        selectedResource = resource
                        launcher.launch("image/*")
                    },
                    onClear = {
                        selectedImages = selectedImages - resource
                        clearedImages = clearedImages + resource
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismissRequest,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                text = stringResource(R.string.save),
                onClick = { onSave(target.address, target.name, selectedImages, clearedImages) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun PodImageResourceRow(
    resource: PodImageResource,
    selectedUri: Uri?,
    savedPath: String?,
    title: String,
    summary: String,
    clearable: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val previewPainter = rememberPodImagePreviewPainter(resource, selectedUri, savedPath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = previewPainter,
            contentDescription = title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (clearable) {
            Spacer(Modifier.width(12.dp))
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = onClear,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.custom_image_restore_default),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun rememberPodImagePreviewPainter(
    resource: PodImageResource,
    selectedUri: Uri?,
    savedPath: String?,
): Painter {
    val context = LocalContext.current
    return remember(context, selectedUri, savedPath, resource) {
        selectedUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri).use { input ->
                    input?.let { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        } ?: savedPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }?.let { bitmap -> BitmapPainter(bitmap.asImageBitmap()) }
        ?: painterResource(resource.defaultImageRes())
}

private fun PodImageResource.titleRes(): Int = when (this) {
    PodImageResource.BOX -> R.string.custom_image_box
    PodImageResource.LEFT -> R.string.custom_image_left
    PodImageResource.RIGHT -> R.string.custom_image_right
}

private fun PodImageResource.defaultImageRes(): Int = when (this) {
    PodImageResource.BOX -> R.drawable.img_box
    PodImageResource.LEFT -> R.drawable.img_left
    PodImageResource.RIGHT -> R.drawable.img_right
}

@Composable
private fun BottomNavigation(
    tabs: List<MainTab>,
    selectedTab: MainTab,
    floating: Boolean,
    blur: Boolean,
    backdrop: LayerBackdrop?,
    onTabClick: (MainTab) -> Unit,
) {
    val barModifier = if (blur && backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RoundedCornerShape(if (floating) 50.dp else 0.dp),
        )
    } else {
        Modifier
    }

    if (floating) {
        FloatingNavigationBar(
            modifier = barModifier.zIndex(2f),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
        ) {
            tabs.forEach { tab ->
                FloatingNavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    } else {
        NavigationBar(
            modifier = barModifier.zIndex(2f),
            color = if (blur) Color.Transparent else MiuixTheme.colorScheme.surface,
            showDivider = false,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    icon = tab.icon,
                    label = tab.title(),
                )
            }
        }
    }
}

@Composable
private fun MainTab.title(): String = when (this) {
    MainTab.Module -> stringResource(R.string.module)
    MainTab.Earphones -> stringResource(R.string.earphones)
    MainTab.Settings -> stringResource(R.string.settings)
}

private val restartScopeOptions = listOf(
    RestartScope("com.android.bluetooth", "Bluetooth"),
    RestartScope("com.milink.service", "MiLink Service"),
    RestartScope("com.xiaomi.bluetooth", "Mi Bluetooth"),
)

private fun PaddingValues.withoutBottom(): PaddingValues {
    return PaddingValues(
        start = calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = calculateTopPadding(),
        end = calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = 0.dp,
    )
}
