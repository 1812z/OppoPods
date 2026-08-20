package moe.chenxy.oppopods.pods

import android.content.Context

data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
    val dualDeviceSupported: Boolean = false,
    val customEqSupported: Boolean = false,
    val eqPresets: List<EqPresetInfo> = emptyList(),
    val customEqFrequencies: List<Int> = emptyList(),
    val customEqMaxPresets: Int = 0,
    val gameModeFeatureId: Int = GameModeFeature.LOW_LATENCY,
    val gameModeSupported: Boolean = false,
)

@kotlinx.serialization.Serializable
data class EqPresetInfo(
    val id: Int,
    val name: String,
    val modeType: Int = -1,
)

@kotlinx.serialization.Serializable
data class EqDevicePreset(
    val id: Int,
    val name: String,
    val selected: Boolean = false,
    val minValue: Int = -6,
    val maxValue: Int = 6,
    val frequencies: List<Int> = emptyList(),
    val gains: List<Int> = emptyList(),
)

object EqDefaults {
    val FREQUENCIES = listOf(62, 250, 1000, 4000, 8000, 16000)
}

fun detectDeviceCapabilities(
    context: Context? = null,
    deviceName: String,
    productId: String? = null,
): DeviceCapabilities {
    val detected = context?.let {
        DeviceModelRegistry.byProductId(it, productId)
            ?: DeviceModelRegistry.byDeviceName(it, deviceName)
    }
    return DeviceCapabilities(
        adaptiveSupported = detected?.adaptiveSupported == true,
        spatialAudioSupported = detected?.spatialAudioSupported == true,
        spatialSoundSwitchSupported = detected?.spatialSoundSwitchSupported == true,
        ancImplementation = if (detected?.legacyAnc == true) {
            AncImplementation.COMPATIBLE
        } else {
            AncImplementation.STANDARD
        },
        dualDeviceSupported = detected?.dualDeviceSupported == true,
        customEqSupported = detected?.customEqSupported == true,
        eqPresets = detected?.eqPresets.orEmpty(),
        customEqFrequencies = detected?.customEqFrequencies.orEmpty(),
        customEqMaxPresets = detected?.customEqMaxPresets ?: 0,
        gameModeFeatureId = detected?.gameModeFeatureId ?: GameModeFeature.LOW_LATENCY,
        gameModeSupported = detected?.gameModeSupported == true,
    )
}
