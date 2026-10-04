package moe.chenxy.oppopods.config

import kotlinx.serialization.Serializable

/** OPPOLeaConnect compatibility switches, including the HyperOS connection prerequisites. */
@Serializable
data class LeAudioConfig(
    val enabled: Boolean = true,
    val respondAt: Boolean = true,
    val sendVdsp: Boolean = true,
    val vendorId: String = "1946",
    val oesfMask: String = "0x3f",
    val leFirst: Boolean = true,
    val poke: Boolean = true,
    val wakeClassic: Boolean = true,
    val fixPolicy: Boolean = true,
    val gateHfp: Boolean = false,
    val holdGatt: Boolean = true,
    val adoptContexts: Boolean = true,
    val gameContext: Boolean = false,
    val completeGroup: Boolean = true,
) {
    fun parsedMask(): Int? = oesfMask.trim().let {
        if (it.startsWith("0x", ignoreCase = true)) it.substring(2).toIntOrNull(16)
        else it.toIntOrNull()
    }?.takeIf { it in 0..0xFFFF }

    fun normalized(): LeAudioConfig = copy(
        vendorId = vendorId.trim().takeIf { value ->
            value.isNotEmpty() && value.all { it in '0'..'9' } &&
                value.toIntOrNull()?.let { it in 0..0xFFFF } == true
        } ?: "1946",
        oesfMask = if (parsedMask() != null) oesfMask.trim() else "0x3f",
    )
}
