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

private val LEGACY_ANC_DEVICES = arrayOf(
    "OPPO Enco Air2 Pro",
    "OPPO Enco Air 4",
    "OPPO Enco Air4",
)

data class DeviceCapabilities(
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val ancImplementation: AncImplementation,
)

fun detectDeviceCapabilities(
    deviceName: String,
    adaptiveOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialAudioOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    spatialSoundSwitchOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
    ancImplementationOverride: Int = ConfigManager.CAPABILITY_OVERRIDE_AUTO,
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
        ancImplementation = resolveAncImplementation(
            override = ancImplementationOverride,
            autoDetected = isLegacyAncDeviceByName(deviceName)
        )
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

fun isLegacyAncDeviceByName(deviceName: String): Boolean {
    return isDeviceInCapabilityList(deviceName, LEGACY_ANC_DEVICES)
}

private fun resolveCapability(override: Int, autoDetected: Boolean): Boolean {
    return when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> true
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> false
        else -> autoDetected
    }
}

private fun resolveAncImplementation(override: Int, autoDetected: Boolean): AncImplementation {
    return when (override) {
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_ENABLED -> AncImplementation.COMPATIBLE
        ConfigManager.CAPABILITY_OVERRIDE_FORCE_DISABLED -> AncImplementation.STANDARD
        else -> if (autoDetected) AncImplementation.COMPATIBLE else AncImplementation.STANDARD
    }
}

private fun normalizeDeviceName(deviceName: String): String {
    return deviceName.lowercase().filter { it.isLetterOrDigit() }
}

private fun isDeviceInCapabilityList(deviceName: String, supportedDevices: Array<String>): Boolean {
    val normalizedName = normalizeDeviceName(deviceName)
    if (normalizedName.isEmpty()) return false

    return supportedDevices.any { supportedDevice ->
        val normalizedSupportedDevice = normalizeDeviceName(supportedDevice)
        normalizedSupportedDevice.isNotEmpty() && normalizedSupportedDevice in normalizedName
    }
}
