package moe.chenxy.oppopods.pods

import moe.chenxy.oppopods.config.ConfigManager

private val ADAPTIVE_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco Free4",
)

private val SPATIAL_AUDIO_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco X3",
)

private val SPATIAL_SOUND_SWITCH_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco Free4",
    "OPPO Enco Air5",
)

private val EQ_SUPPORTED_DEVICES = arrayOf(
    "OPPO Enco X3",
)

data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val eqSupported: Boolean,
)

fun detectDeviceCapabilities(
    deviceName: String,
    adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    eqOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
): DeviceCapabilities {
    return DeviceCapabilities(
        adaptiveSupported = resolveCapability(
            override = adaptiveOverride,
            autoDetected = isAdaptiveSupportedByName(deviceName),
        ),
        spatialAudioSupported = resolveCapability(
            override = spatialAudioOverride,
            autoDetected = isSpatialAudioSupportedByName(deviceName),
        ),
        spatialSoundSwitchSupported = resolveCapability(
            override = spatialSoundSwitchOverride,
            autoDetected = isSpatialSoundSwitchSupportedByName(deviceName),
        ),
        eqSupported = resolveCapability(
            override = eqOverride,
            autoDetected = isEqSupportedByName(deviceName),
        ),
    )
}

fun isAdaptiveSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, ADAPTIVE_SUPPORTED_DEVICES)
}

fun isSpatialAudioSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, SPATIAL_AUDIO_SUPPORTED_DEVICES)
}

fun isSpatialSoundSwitchSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, SPATIAL_SOUND_SWITCH_SUPPORTED_DEVICES)
}

fun isEqSupportedByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, EQ_SUPPORTED_DEVICES)
}

private fun resolveCapability(override: Int, autoDetected: Boolean): Boolean {
    return when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> true
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> false
        else -> autoDetected
    }
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}

private fun isDeviceInCapabilityList(deviceName: String, supportedDevices: Array<String>): Boolean {
    val normalizedName = normalizeDeviceName(deviceName)
    return supportedDevices.any { supportedDevice ->
        val normalizedSupportedDevice = normalizeDeviceName(supportedDevice)
        normalizedSupportedDevice in normalizedName || normalizedName in normalizedSupportedDevice
    }
}
