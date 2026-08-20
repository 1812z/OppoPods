package moe.chenxy.oppopods.ui.pages

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.pods.NoiseControlMode
import moe.chenxy.oppopods.pods.WearStatus
import moe.chenxy.oppopods.ui.components.AncSwitch
import moe.chenxy.oppopods.ui.components.PodStatus
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ArrowRight
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun PodDetailPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomContentPadding: Dp = 16.dp,
    podName: String,
    batteryParams: BatteryParams,
    wearStatus: WearStatus = WearStatus(),
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode? = null,
    transparencyVocalEnhancement: Boolean = false,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit = {},
    gameMode: Boolean = false,
    gameModeSupported: Boolean = false,
    onGameModeChange: (Boolean) -> Unit = {},
    spatialAudioMode: Int = ConfigManager.SPATIAL_AUDIO_OFF,
    onSpatialAudioModeChange: (Int) -> Unit = {},
    dualDeviceConnection: Boolean = false,
    onDualDeviceConnectionChange: (Boolean) -> Unit = {},
    spatialAudioSupported: Boolean = false,
    spatialSoundSupported: Boolean = false,
    adaptiveModeEnabled: Boolean = true,
    equalizerVisible: Boolean = false,
    dualDeviceSupported: Boolean = false,
    onOpenEqualizer: () -> Unit = {},
    boxImagePath: String? = null,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = rememberPodImagePainter(boxImagePath),
                    contentDescription = "Earphones",
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 360.dp),
                    contentScale = ContentScale.FillWidth
                )
                Text(
                    text = podName,
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                podControlItems(
                    batteryParams = batteryParams,
                    wearStatus = wearStatus,
                    ancMode = ancMode,
                    onAncModeChange = onAncModeChange,
                    smartAncLevel = smartAncLevel,
                    transparencyVocalEnhancement = transparencyVocalEnhancement,
                    onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
                    gameMode = gameMode,
                    gameModeSupported = gameModeSupported,
                    onGameModeChange = onGameModeChange,
                    spatialAudioMode = spatialAudioMode,
                    onSpatialAudioModeChange = onSpatialAudioModeChange,
                    dualDeviceConnection = dualDeviceConnection,
                    onDualDeviceConnectionChange = onDualDeviceConnectionChange,
                    spatialAudioSupported = spatialAudioSupported,
                    spatialSoundSupported = spatialSoundSupported,
                    adaptiveModeEnabled = adaptiveModeEnabled,
                    equalizerVisible = equalizerVisible,
                    dualDeviceSupported = dualDeviceSupported,
                    onOpenEqualizer = onOpenEqualizer,
                    bottomContentPadding = bottomContentPadding
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Image(
                painter = rememberPodImagePainter(boxImagePath),
                contentDescription = "Earphones",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .padding(vertical = 16.dp),
                contentScale = ContentScale.FillWidth
            )
        }

        podControlItems(
            batteryParams = batteryParams,
            wearStatus = wearStatus,
            ancMode = ancMode,
            onAncModeChange = onAncModeChange,
            smartAncLevel = smartAncLevel,
            transparencyVocalEnhancement = transparencyVocalEnhancement,
            onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange,
            gameMode = gameMode,
            gameModeSupported = gameModeSupported,
            onGameModeChange = onGameModeChange,
            spatialAudioMode = spatialAudioMode,
            onSpatialAudioModeChange = onSpatialAudioModeChange,
            dualDeviceConnection = dualDeviceConnection,
            onDualDeviceConnectionChange = onDualDeviceConnectionChange,
            spatialAudioSupported = spatialAudioSupported,
            spatialSoundSupported = spatialSoundSupported,
            adaptiveModeEnabled = adaptiveModeEnabled,
            equalizerVisible = equalizerVisible,
            dualDeviceSupported = dualDeviceSupported,
            onOpenEqualizer = onOpenEqualizer,
            bottomContentPadding = bottomContentPadding
        )
    }
}

@Composable
private fun rememberPodImagePainter(path: String?) = remember(path) {
    path?.let {
        runCatching { BitmapFactory.decodeFile(it) }
            .getOrNull()
            ?.let { bitmap -> BitmapPainter(bitmap.asImageBitmap()) }
    }
} ?: painterResource(R.drawable.img_box)

private fun LazyListScope.podControlItems(
    batteryParams: BatteryParams,
    wearStatus: WearStatus,
    ancMode: NoiseControlMode,
    onAncModeChange: (NoiseControlMode) -> Unit,
    smartAncLevel: NoiseControlMode?,
    transparencyVocalEnhancement: Boolean,
    onTransparencyVocalEnhancementChange: (Boolean) -> Unit,
    gameMode: Boolean,
    gameModeSupported: Boolean,
    onGameModeChange: (Boolean) -> Unit,
    spatialAudioMode: Int,
    onSpatialAudioModeChange: (Int) -> Unit,
    dualDeviceConnection: Boolean,
    onDualDeviceConnectionChange: (Boolean) -> Unit,
    spatialAudioSupported: Boolean,
    spatialSoundSupported: Boolean,
    adaptiveModeEnabled: Boolean,
    equalizerVisible: Boolean,
    dualDeviceSupported: Boolean,
    onOpenEqualizer: () -> Unit,
    bottomContentPadding: Dp
) {
    val spatialAudioValues = listOf(
        ConfigManager.SPATIAL_AUDIO_OFF,
        ConfigManager.SPATIAL_AUDIO_FIXED,
        ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING,
    )

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            PodStatus(
                batteryParams = batteryParams,
                wearStatus = wearStatus,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
            )
        }
    }

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            AncSwitch(
                ancStatus = ancMode,
                onAncModeChange = onAncModeChange,
                smartAncLevel = smartAncLevel,
                adaptiveModeEnabled = adaptiveModeEnabled,
                transparencyVocalEnhancement = transparencyVocalEnhancement,
                onTransparencyVocalEnhancementChange = onTransparencyVocalEnhancementChange
            )
        }
    }

    item {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            if (gameModeSupported) {
                SwitchPreference(
                    title = stringResource(R.string.game_mode),
                    summary = stringResource(R.string.game_mode_summary),
                    checked = gameMode,
                    onCheckedChange = onGameModeChange
                )
            }
            if (spatialAudioSupported) {
                val spatialAudioOptions = listOf(
                    stringResource(R.string.off),
                    stringResource(R.string.spatial_audio_fixed),
                    stringResource(R.string.spatial_audio_head_tracking),
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.spatial_audio),
                    summary = stringResource(R.string.spatial_audio_summary),
                    items = spatialAudioOptions,
                    selectedIndex = spatialAudioValues.indexOf(spatialAudioMode).coerceAtLeast(0),
                    onSelectedIndexChange = { onSpatialAudioModeChange(spatialAudioValues[it]) }
                )
            }
            if (spatialSoundSupported) {
                SwitchPreference(
                    title = stringResource(R.string.spatial_sound),
                    summary = stringResource(if (spatialAudioMode != ConfigManager.SPATIAL_AUDIO_OFF) R.string.enabled else R.string.off),
                    checked = spatialAudioMode != ConfigManager.SPATIAL_AUDIO_OFF,
                    onCheckedChange = {
                        onSpatialAudioModeChange(if (it) ConfigManager.SPATIAL_AUDIO_FIXED else ConfigManager.SPATIAL_AUDIO_OFF)
                    }
                )
            }
            if (equalizerVisible) {
                BasicComponent(
                    title = stringResource(R.string.eq_preset_title),
                    summary = stringResource(R.string.eq_preset_summary),
                    onClick = onOpenEqualizer,
                    endActions = {
                        Icon(
                            imageVector = MiuixIcons.ArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
            if (dualDeviceSupported) {
                SwitchPreference(
                    title = stringResource(R.string.dual_device_connection),
                    summary = stringResource(if (dualDeviceConnection) R.string.enabled else R.string.off),
                    checked = dualDeviceConnection,
                    onCheckedChange = onDualDeviceConnectionChange
                )
            }
        }
    }
    item {
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(bottomContentPadding))
    }
}
