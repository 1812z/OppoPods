package moe.chenxy.oppopods.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.ui.AppLocale
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    desktopIconHidden: MutableState<Boolean> = mutableStateOf(false),
    onDesktopIconHiddenChange: (Boolean) -> Unit = {},
    logLevel: MutableState<Int> = mutableStateOf(ConfigManager.LOG_LEVEL_BASIC),
    onLogLevelChange: (Int) -> Unit = {},
    islandMode: MutableState<Int> = mutableStateOf(ConfigManager.ISLAND_MODE_OFFICIAL),
    onIslandModeChange: (Int) -> Unit = {},
    islandShowTimings: MutableState<Set<Int>> = mutableStateOf(emptySet()),
    onIslandShowTimingsChange: (Set<Int>) -> Unit = {},
    appLanguage: MutableState<Int> = mutableStateOf(AppLocale.SYSTEM),
    onAppLanguageChange: (Int) -> Unit = {},
    autoGameMode: MutableState<Boolean> = mutableStateOf(false),
    onAutoGameModeChange: (Boolean) -> Unit = {},
    milinkCardFeatures: MutableState<Set<Int>> = mutableStateOf(ConfigManager.DEFAULT_MILINK_CARD_FEATURES),
    onMilinkCardFeaturesChange: (Set<Int>) -> Unit = {},
    notificationClickAction: MutableState<Int> = mutableStateOf(ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP),
    onNotificationClickActionChange: (Int) -> Unit = {},
    moreClickAction: MutableState<Int> = mutableStateOf(ConfigManager.MORE_CLICK_MODULE),
    onMoreClickActionChange: (Int) -> Unit = {},
    fakeDeviceId: MutableState<String> = mutableStateOf(ConfigManager.DEFAULT_FAKE_DEVICE_ID),
    onFakeDeviceIdChange: (String) -> Unit = {},
    onOpenTheme: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val languageOptions = listOf(
        stringResource(R.string.language_system),
        stringResource(R.string.language_chinese),
        stringResource(R.string.language_english),
    )
    val logLevelValues = listOf(ConfigManager.LOG_LEVEL_OFF, ConfigManager.LOG_LEVEL_BASIC, ConfigManager.LOG_LEVEL_DEBUG)
    val logLevelOptions = listOf(
        stringResource(R.string.log_level_off),
        stringResource(R.string.log_level_basic),
        stringResource(R.string.log_level_debug),
    )
    val islandModeValues = listOf(ConfigManager.ISLAND_MODE_NONE, ConfigManager.ISLAND_MODE_OFFICIAL, ConfigManager.ISLAND_MODE_MODULE)
    val islandModeOptions = listOf(
        stringResource(R.string.island_mode_none),
        stringResource(R.string.island_mode_official),
        stringResource(R.string.island_mode_module),
    )
    val islandShowTimingOptions = listOf(
        ConfigManager.ISLAND_SHOW_TIMING_CONNECTED to stringResource(R.string.island_show_timing_connected),
        ConfigManager.ISLAND_SHOW_TIMING_WEARING to stringResource(R.string.island_show_timing_wearing),
        ConfigManager.ISLAND_SHOW_TIMING_REMOVED to stringResource(R.string.island_show_timing_removed),
        ConfigManager.ISLAND_SHOW_TIMING_IN_CASE to stringResource(R.string.island_show_timing_in_case),
    )
    val islandShowTimingEntries = remember(islandShowTimings.value, islandShowTimingOptions) {
        listOf(
            DropdownEntry(
                items = islandShowTimingOptions.map { (value, text) ->
                    DropdownItem(
                        text = text,
                        selected = value in islandShowTimings.value,
                        onClick = {
                            val selected = islandShowTimings.value
                            onIslandShowTimingsChange(
                                if (value in selected) selected - value else selected + value
                            )
                        },
                    )
                }
            )
        )
    }
    val notificationClickActionValues = listOf(
        ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP,
        ConfigManager.NOTIFICATION_CLICK_SYSTEM_SETTINGS,
        ConfigManager.NOTIFICATION_CLICK_HEYTAP,
    )
    val notificationClickActionOptions = listOf(
        stringResource(R.string.notification_click_module_popup),
        stringResource(R.string.click_action_system_settings),
        stringResource(R.string.click_action_heytap),
    )
    val moreClickActionValues = listOf(
        ConfigManager.MORE_CLICK_HEYTAP,
        ConfigManager.MORE_CLICK_SYSTEM_SETTINGS,
        ConfigManager.MORE_CLICK_MODULE,
    )
    val moreClickActionOptions = listOf(
        stringResource(R.string.click_action_heytap),
        stringResource(R.string.click_action_system_settings),
        stringResource(R.string.click_action_module),
    )
    val milinkCardFeatureOptions = listOf(
        ConfigManager.MILINK_CARD_GAME_MODE to stringResource(R.string.game_mode),
        ConfigManager.MILINK_CARD_SPATIAL_AUDIO to stringResource(R.string.spatial_audio),
    )
    val milinkCardFeatureEntries = remember(milinkCardFeatures.value, milinkCardFeatureOptions) {
        listOf(
            DropdownEntry(
                items = milinkCardFeatureOptions.map { (value, text) ->
                    DropdownItem(
                        text = text,
                        selected = value in milinkCardFeatures.value,
                        onClick = {
                            val selected = milinkCardFeatures.value
                            onMilinkCardFeaturesChange(if (value in selected) selected - value else selected + value)
                        },
                    )
                }
            )
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp
        ),
    ) {
        item {
            Card {
                BasicComponent(
                    title = stringResource(R.string.theme_title),
                    summary = stringResource(R.string.theme_color_summary),
                    onClick = onOpenTheme,
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.language),
                    summary = stringResource(R.string.language_summary),
                    items = languageOptions,
                    selectedIndex = appLanguage.value.coerceIn(languageOptions.indices),
                    onSelectedIndexChange = { onAppLanguageChange(it) }
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.log_level),
                    summary = stringResource(R.string.log_level_summary),
                    items = logLevelOptions,
                    selectedIndex = logLevelValues.indexOf(logLevel.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onLogLevelChange(logLevelValues[it]) }
                )
                SwitchPreference(
                    title = stringResource(R.string.hide_desktop_icon),
                    summary = stringResource(R.string.hide_desktop_icon_summary),
                    checked = desktopIconHidden.value,
                    onCheckedChange = { onDesktopIconHiddenChange(it) }
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.island_mode),
                    summary = stringResource(R.string.island_mode_summary),
                    items = islandModeOptions,
                    selectedIndex = islandModeValues.indexOf(islandMode.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onIslandModeChange(islandModeValues[it]) }
                )
                if (islandMode.value == ConfigManager.ISLAND_MODE_MODULE) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.island_show_timing),
                        summary = stringResource(R.string.island_show_timing_summary),
                        entries = islandShowTimingEntries,
                        collapseOnSelection = false,
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.auto_game_mode),
                    checked = autoGameMode.value,
                    onCheckedChange = { onAutoGameModeChange(it) }
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.milink_card_features),
                    summary = stringResource(R.string.milink_card_features_summary),
                    entries = milinkCardFeatureEntries,
                    collapseOnSelection = false,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.notification_click_action),
                    summary = stringResource(R.string.notification_click_action_summary),
                    items = notificationClickActionOptions,
                    selectedIndex = notificationClickActionValues.indexOf(notificationClickAction.value).coerceAtLeast(0),
                    onSelectedIndexChange = { onNotificationClickActionChange(notificationClickActionValues[it]) }
                )
                if (notificationClickAction.value == ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP) {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.more_click_action),
                        summary = stringResource(R.string.more_click_action_summary),
                        items = moreClickActionOptions,
                        selectedIndex = moreClickActionValues.indexOf(moreClickAction.value).coerceAtLeast(0),
                        onSelectedIndexChange = { onMoreClickActionChange(moreClickActionValues[it]) }
                    )
                }
                BasicComponent(
                    title = stringResource(R.string.fake_device_id),
                    summary = stringResource(R.string.fake_device_id_summary)
                )
                TextField(
                    value = fakeDeviceId.value,
                    onValueChange = { onFakeDeviceIdChange(it.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(top = 12.dp)) {
                BasicComponent(
                    title = stringResource(R.string.about),
                    summary = "OppoPods-Enhanced",
                    onClick = onOpenAbout,
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}
