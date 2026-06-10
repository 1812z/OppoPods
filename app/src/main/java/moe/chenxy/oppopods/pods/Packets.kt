package moe.chenxy.oppopods.pods

/**
 * OPPO earphone RFCOMM protocol packet definitions.
 *
 * Packet format (Little Endian for multi-byte fields):
 * Header(AA) + TotalLen(1B) + Res(0000) + Cmd(2B) + Seq(1B) + PayLen(2B) + Payload
 */

object OppoPackets {

    /** Build a complete OPPO protocol packet. */
    fun buildPacket(cmd: Int, seq: Int = 0xF0, payload: ByteArray = byteArrayOf()): ByteArray {
        val payLen = payload.size
        // TotalLen = 7 (header fields after TotalLen: Res(2) + Cmd(2) + Seq(1) + PayLen(2)) + payLen
        val totalLen = 7 + payLen
        val packet = ByteArray(2 + totalLen) // Header(1) + TotalLen(1) + rest
        packet[0] = 0xAA.toByte()           // Header
        packet[1] = totalLen.toByte()        // TotalLen
        packet[2] = 0x00                     // Res byte 1
        packet[3] = 0x00                     // Res byte 2
        packet[4] = (cmd and 0xFF).toByte()          // Cmd low byte
        packet[5] = ((cmd shr 8) and 0xFF).toByte()  // Cmd high byte
        packet[6] = seq.toByte()             // Seq
        packet[7] = (payLen and 0xFF).toByte()        // PayLen low byte
        packet[8] = ((payLen shr 8) and 0xFF).toByte() // PayLen high byte
        payload.copyInto(packet, 9)
        return packet
    }
}

/**
 * ANC mode values for OPPO earphones (used in SET commands).
 * OPPO payload is 01 01 [value], except Adaptive which uses 01 01 00 08.
 */
object AncMode {
    const val OFF = 0x01
    const val NOISE_CANCELLATION = 0x02
    // ANC intensity payloads from OPPO captures: Smart/Light/Medium/Deep.
    const val NOISE_CANCELLATION_SMART = 0x80
    const val NOISE_CANCELLATION_LIGHT = 0x40
    const val NOISE_CANCELLATION_MEDIUM = 0x20
    const val NOISE_CANCELLATION_DEEP = 0x10
    const val TRANSPARENCY = 0x04
    const val ADAPTIVE_HIGH = 0x00
    const val ADAPTIVE_LOW = 0x08
}

/** Noise control mode enum for UI. */
enum class NoiseControlMode {
    OFF,
    NOISE_CANCELLATION,
    NOISE_CANCELLATION_SMART,
    NOISE_CANCELLATION_LIGHT,
    NOISE_CANCELLATION_MEDIUM,
    NOISE_CANCELLATION_DEEP,
    ADAPTIVE,
    TRANSPARENCY
}

fun NoiseControlMode.isNoiseCancellation(): Boolean {
    return when (this) {
        NoiseControlMode.NOISE_CANCELLATION,
        NoiseControlMode.NOISE_CANCELLATION_SMART,
        NoiseControlMode.NOISE_CANCELLATION_LIGHT,
        NoiseControlMode.NOISE_CANCELLATION_MEDIUM,
        NoiseControlMode.NOISE_CANCELLATION_DEEP -> true
        else -> false
    }
}

/** Battery component index in response payload. */
object BatteryComponent {
    const val LEFT = 1
    const val RIGHT = 2
    const val CASE = 3
}

/** Wearing-detection component/status values in active reports. */
object WearComponent {
    const val LEFT = 1
    const val RIGHT = 2
    const val CASE = 3
}

enum class WearState(val value: Int) {
    DISCONNECTED(0x00),
    IN_CASE(0x04),
    REMOVED(0x05),
    WEARING(0x07);

    companion object {
        fun fromValue(value: Int): WearState? = entries.firstOrNull { it.value == value }
    }
}

data class WearStatus(
    val left: WearState? = null,
    val right: WearState? = null,
    val case: WearState? = null
)

/** Feature IDs used by the switch-feature command/query. */
object GameModeFeature {
    const val LOW_LATENCY = 0x06
    const val DUAL_DEVICE_CONNECTION = 0x11
    const val FREE4_SPATIAL_SOUND = 0x1B
    const val MAIN = 0x28
}

/** Spatial audio mode values. */
object SpatialAudioMode {
    const val OFF = 0x00
    const val FIXED = 0x01
    const val HEAD_TRACKING = 0x02
}

/**
 * Master EQ preset IDs for OPPO Enco X3 ("大师调音" / Master Tuning).
 * Values are non-contiguous because other products in the same protocol family
 * use the missing slots (4..6).
 */
object EqPreset {
    const val AUTHENTIC = 0  // 至臻原音 (Authentic)
    const val DETAIL = 1     // 高清解析 (Detail)
    const val VOCAL = 2      // 纯享人声 (Vocal)
    const val BASS = 3       // 澎湃低音 (Bass)
    const val DYNAUDIO = 7   // 丹拿特调 (Dynaudio tuned)
    /** All supported presets, in UI display order. */
    val ALL: List<Int> = listOf(AUTHENTIC, DETAIL, VOCAL, BASS, DYNAUDIO)
}

/** Protocol command codes. */
object Cmd {
    /** Set ANC mode */
    const val SET_ANC = 0x0404
    /** Set game mode */
    const val SET_GAME_MODE = 0x0403
    /** Set spatial audio mode */
    const val SET_SPATIAL_AUDIO = 0x0422
    /** Query battery */
    const val QUERY_BATTERY = 0x0106
    /** Battery response from earphone */
    const val BATTERY_RESPONSE = 0x8106
    /** Query ANC mode */
    const val QUERY_ANC_MODE = 0x010C
    /** ANC mode response */
    const val ANC_MODE_RESPONSE = 0x810C
    /** ANC mode change notification */
    const val ANC_MODE_NOTIFY = 0x0204
    /** Batch parameter query */
    const val QUERY_STATUS = 0x010D
    /** Batch parameter query response */
    const val QUERY_STATUS_RESPONSE = 0x810D
    /** Switch-feature response */
    const val SET_GAME_MODE_RESPONSE = 0x8403
    /** Spatial audio mode response */
    const val SET_SPATIAL_AUDIO_RESPONSE = 0x8422
    /** Set spatial sound switch response */
    const val SET_SPATIAL_SOUND_SWITCH_RESPONSE = 0x8403
    /** Spatial audio mode notification */
    const val SPATIAL_AUDIO_NOTIFY = 0x0510

    /**
     * Master switch for the bud's spatial DSP. Distinct from [SET_SPATIAL_AUDIO]:
     * the mode cmd only stores the *value* (off/fixed/head-tracking), this switch
     * is what actually engages the DSP pipeline. On Enco X3 firmware 1.3.2, sending
     * only 0x0422 makes the bud report the mode back correctly but produces no
     * audible effect — heytap/melody also reports "off" because it reads this
     * switch's state, not the mode. Payload `[on]`. (Firmware analysis cross-ref:
     * see `firmware-analysis/14_spatial.md` in the protocol research notes.)
     */
    const val SET_SPATIAL_AUDIO_SWITCH = 0x041E

    /** Set master EQ preset ("大师调音"). Payload `[presetId]`. */
    const val SET_EQ = 0x0406
    /** Query current EQ preset (no payload). */
    const val QUERY_EQ_PRESET = 0x010F
    /** Response to [QUERY_EQ_PRESET]. Payload `[status, preset]`. */
    const val EQ_PRESET_RESPONSE = 0x810F
    /** Unsolicited push notification when EQ preset changes. Payload `[preset]`. */
    const val EQ_PRESET_NOTIFY = 0x0504
}

/** Pre-built packets. */
object Enums {
    /** Switch to Noise Cancellation: AA 0A 00 00 04 04 00 03 00 01 01 02 */
    val ANC_NOISE_CANCEL: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.NOISE_CANCELLATION.toByte())
    )

    /** Switch to Smart Noise Cancellation: AA 0A 00 00 04 04 00 03 00 01 01 80 */
    val ANC_NOISE_CANCEL_SMART: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.NOISE_CANCELLATION_SMART.toByte())
    )

    /** Switch to Light Noise Cancellation: AA 0A 00 00 04 04 00 03 00 01 01 40 */
    val ANC_NOISE_CANCEL_LIGHT: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.NOISE_CANCELLATION_LIGHT.toByte())
    )

    /** Switch to Medium Noise Cancellation: AA 0A 00 00 04 04 00 03 00 01 01 20 */
    val ANC_NOISE_CANCEL_MEDIUM: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.NOISE_CANCELLATION_MEDIUM.toByte())
    )

    /** Switch to Deep Noise Cancellation: AA 0A 00 00 04 04 00 03 00 01 01 10 */
    val ANC_NOISE_CANCEL_DEEP: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.NOISE_CANCELLATION_DEEP.toByte())
    )

    /** Switch to Transparency: AA 0A 00 00 04 04 00 03 00 01 01 04 */
    val ANC_TRANSPARENCY: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.TRANSPARENCY.toByte())
    )

    /** Enable transparency vocal enhancement: AA 0B 00 00 04 04 57 04 00 01 01 00 02 */
    val TRANSPARENCY_VOCAL_ENHANCEMENT_ON: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC,
        seq = 0x57,
        payload = byteArrayOf(0x01, 0x01, 0x00, 0x02)
    )

    /** Disable transparency vocal enhancement: AA 0B 00 00 04 04 57 04 00 01 01 00 01 */
    val TRANSPARENCY_VOCAL_ENHANCEMENT_OFF: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC,
        seq = 0x57,
        payload = byteArrayOf(0x01, 0x01, 0x00, 0x01)
    )

    /** Switch to Off: AA 0A 00 00 04 04 00 03 00 01 01 01 */
    val ANC_OFF: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.OFF.toByte())
    )

    /** Switch to Adaptive: AA 0B 00 00 04 04 00 04 00 01 01 00 08 */
    val ANC_ADAPTIVE: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_ANC, payload = byteArrayOf(0x01, 0x01, AncMode.ADAPTIVE_HIGH.toByte(), AncMode.ADAPTIVE_LOW.toByte())
    )

    /** Query battery: AA 07 00 00 06 01 F0 00 00 */
    val QUERY_BATTERY: ByteArray = byteArrayOf(
        0xAA.toByte(), 0x07, 0x00, 0x00, 0x06, 0x01, 0xF0.toByte(), 0x00, 0x00
    )

    /** Enable active earphone status reports: AA 09 00 00 05 02 3A 02 00 01 02 */
    val ENABLE_STATUS_REPORT: ByteArray = byteArrayOf(
        0xAA.toByte(), 0x09, 0x00, 0x00, 0x05, 0x02, 0x3A, 0x02, 0x00, 0x01, 0x02
    )

    /** Query ANC mode: AA 09 00 00 0C 01 00 02 00 01 01 */
    val QUERY_ANC: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.QUERY_ANC_MODE, payload = byteArrayOf(0x01, 0x01)
    )

    /** Enable game mode main switch: AA 09 00 00 03 04 00 02 00 28 01 */
    val GAME_MODE_ON: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE, payload = byteArrayOf(GameModeFeature.MAIN.toByte(), 0x01)
    )

    /** Disable game mode main switch: AA 09 00 00 03 04 00 02 00 28 00 */
    val GAME_MODE_OFF: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE, payload = byteArrayOf(GameModeFeature.MAIN.toByte(), 0x00)
    )

    /** Enable low-latency game mode: AA 09 00 00 03 04 00 02 00 06 01 */
    val GAME_LOW_LATENCY_ON: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE, payload = byteArrayOf(GameModeFeature.LOW_LATENCY.toByte(), 0x01)
    )

    /** Disable low-latency game mode: AA 09 00 00 03 04 00 02 00 06 00 */
    val GAME_LOW_LATENCY_OFF: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE, payload = byteArrayOf(GameModeFeature.LOW_LATENCY.toByte(), 0x00)
    )

    fun gameModePackets(enabled: Boolean, implementation: GameModeImplementation): List<ByteArray> {
        return when (implementation) {
            GameModeImplementation.STANDARD -> listOf(if (enabled) GAME_MODE_ON else GAME_MODE_OFF)
            GameModeImplementation.COMPATIBLE -> if (enabled) {
                listOf(GAME_MODE_ON, GAME_LOW_LATENCY_ON)
            } else {
                listOf(GAME_LOW_LATENCY_OFF, GAME_MODE_OFF)
            }
        }
    }

    /** Set spatial audio: AA 08 00 00 22 04 F0 01 00 [mode]. */
    fun spatialAudioPacket(mode: Int): ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_SPATIAL_AUDIO,
        payload = byteArrayOf(mode.coerceIn(SpatialAudioMode.OFF, SpatialAudioMode.HEAD_TRACKING).toByte())
    )

    /**
     * Toggle the bud's spatial DSP master switch. Payload `[on]`.
     * See [Cmd.SET_SPATIAL_AUDIO_SWITCH] for why this needs to be paired with
     * [spatialAudioPacket] on Enco X3.
     */
    fun spatialAudioSwitchPacket(on: Boolean): ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_SPATIAL_AUDIO_SWITCH,
        payload = byteArrayOf(if (on) 0x01 else 0x00)
    )

    /** Set master EQ preset. Payload `[presetId]`. */
    fun eqPresetPacket(presetId: Int): ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_EQ,
        payload = byteArrayOf(presetId.toByte())
    )

    /** Query current EQ preset: AA 07 00 00 0F 01 F0 00 00 */
    val QUERY_EQ: ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.QUERY_EQ_PRESET, payload = byteArrayOf()
    )

    /** Set spatial sound switch: AA 09 00 00 03 04 F0 02 00 1B [00/01]. */
    fun spatialSoundSwitchPacket(enabled: Boolean): ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE,
        payload = byteArrayOf(GameModeFeature.FREE4_SPATIAL_SOUND.toByte(), if (enabled) 0x01 else 0x00)
    )

    /** Set dual-device connection: AA 09 00 00 03 04 F0 02 00 11 [00/01]. */
    fun dualDeviceConnectionPacket(enabled: Boolean): ByteArray = OppoPackets.buildPacket(
        cmd = Cmd.SET_GAME_MODE,
        payload = byteArrayOf(GameModeFeature.DUAL_DEVICE_CONNECTION.toByte(), if (enabled) 0x01 else 0x00)
    )

    /**
     * Batch parameter query (fixed hex blob).
     * Cmd=0x010D, contains multiple param IDs including 0x28 (game mode).
     * Has built-in wake weight, no need for preceding 0x0106.
     */
    val QUERY_STATUS: ByteArray = byteArrayOf(
        0xAA.toByte(), 0x13, 0x00, 0x00, 0x0D, 0x01, 0x00, 0x0C, 0x00,
        0x0B, 0x05, 0x04, 0x0B, 0x11, 0x13, 0x18, 0x06, 0x1B, 0x1C, 0x27, 0x28
    )
}

/**
 * Parser for OPPO earphone battery response packets.
 *
 * Response packet format: AA + TotalLen + 0000 + Cmd(0x8106 = 06 81) + Seq + PayLen + Payload
 * Payload consists of pairs: [Index(1B), RawValue(1B)]
 *   Index: 1=Left, 2=Right, 3=Case
 *   RawValue: battery = value & 0x7F, charging = (value & 0x80) != 0
 */
object BatteryParser {

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean
    )

    data class BatteryResult(
        val left: BatteryInfo?,
        val right: BatteryInfo?,
        val case: BatteryInfo?
    )

    /**
     * Parse a raw packet buffer for battery response (query response, Cmd=0x8106).
     * Returns null if the packet is not a valid battery response.
     */
    fun parse(data: ByteArray): BatteryResult? {
        // Minimum packet: AA + TotalLen + 00 00 + Cmd(2) + Seq(1) + PayLen(2) = 9 bytes header
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        // Check command = 0x8106 (stored as 06 81 in little endian at offsets 4,5)
        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.BATTERY_RESPONSE) return null

        // PayLen at offsets 7,8 (little endian)
        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9

        if (data.size < payloadStart + payLen) return null

        var left: BatteryInfo? = null
        var right: BatteryInfo? = null
        var case: BatteryInfo? = null

        var i = payloadStart
        while (i + 1 < payloadStart + payLen) {
            val index = data[i].toInt() and 0xFF
            val rawValue = data[i + 1].toInt() and 0xFF
            val level = rawValue and 0x7F
            val charging = (rawValue and 0x80) != 0
            val info = BatteryInfo(level, charging)

            when (index) {
                BatteryComponent.LEFT -> left = info
                BatteryComponent.RIGHT -> right = info
                BatteryComponent.CASE -> case = info
            }
            i += 2
        }

        return BatteryResult(left, right, case)
    }

    /**
     * Parse an active/unsolicited battery report (Cmd=0x0204, payload type=0x01).
     *
     * Active report format:
     * Payload[0] = 0x01 (report type: battery)
     * Payload[1] = count (number of index-value pairs)
     * Payload[2..] = [Index(1B), StatusValue(1B)] * count
     *
     * Returns null if the packet is not a valid active battery report.
     */
    fun parseActiveReport(data: ByteArray): BatteryResult? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.ANC_MODE_NOTIFY) return null // 0x0204 = active status report

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9
        if (data.size < payloadStart + payLen) return null
        if (payLen < 2) return null

        // Check report type = 0x01 (battery)
        val reportType = data[payloadStart].toInt() and 0xFF
        if (reportType != 0x01) return null

        val count = data[payloadStart + 1].toInt() and 0xFF
        if (payLen < 2 + count * 2) return null

        var left: BatteryInfo? = null
        var right: BatteryInfo? = null
        var case: BatteryInfo? = null

        for (j in 0 until count) {
            val idx = payloadStart + 2 + j * 2
            if (idx + 1 >= data.size) break
            val index = data[idx].toInt() and 0xFF
            val rawValue = data[idx + 1].toInt() and 0xFF
            val level = rawValue and 0x7F
            val charging = (rawValue and 0x80) != 0
            val info = BatteryInfo(level, charging)

            when (index) {
                BatteryComponent.LEFT -> left = info
                BatteryComponent.RIGHT -> right = info
                BatteryComponent.CASE -> case = info
            }
        }

        return BatteryResult(left, right, case)
    }
}

object SpatialAudioParser {
    fun parseModeNotify(packet: ByteArray): Int? {
        if (packet.size < 10 || packet[0] != 0xAA.toByte()) return null
        val cmd = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        if (cmd != Cmd.SPATIAL_AUDIO_NOTIFY) return null
        val payLen = (packet[7].toInt() and 0xFF) or ((packet[8].toInt() and 0xFF) shl 8)
        if (payLen < 1 || packet.size < 9 + payLen) return null
        val mode = packet[9].toInt() and 0xFF
        return mode.takeIf { it in SpatialAudioMode.OFF..SpatialAudioMode.HEAD_TRACKING }
    }

    fun parseSetResponseStatus(packet: ByteArray): Int? {
        if (packet.size < 10 || packet[0] != 0xAA.toByte()) return null
        val cmd = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        if (cmd != Cmd.SET_SPATIAL_AUDIO_RESPONSE) return null
        val payLen = (packet[7].toInt() and 0xFF) or ((packet[8].toInt() and 0xFF) shl 8)
        if (payLen < 1 || packet.size < 9 + payLen) return null
        return packet[9].toInt() and 0xFF
    }

    fun parseSpatialSoundSwitchSetResponse(packet: ByteArray): Boolean? {
        if (packet.size < 11 || packet[0] != 0xAA.toByte()) return null
        val cmd = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)
        if (cmd != Cmd.SET_SPATIAL_SOUND_SWITCH_RESPONSE) return null
        val payLen = (packet[7].toInt() and 0xFF) or ((packet[8].toInt() and 0xFF) shl 8)
        if (payLen < 2 || packet.size < 9 + payLen) return null
        val feature = packet[9].toInt() and 0xFF
        if (feature != GameModeFeature.FREE4_SPATIAL_SOUND) return null
        return when (packet[10].toInt() and 0xFF) {
            0x00 -> false
            0x01 -> true
            else -> null
        }
    }
}

/**
 * Parser for the EQ preset, handling both:
 *  - cmd 0x0504 (unsolicited change notification, payload `[preset]`)
 *  - cmd 0x810F (response to [Cmd.QUERY_EQ_PRESET], payload `[status, preset]`)
 *
 * Buds push 0x0504 whenever EQ changes but don't push initial state on connect,
 * so we query 0x010F at connect time and parse the 0x810F response here.
 */
object EqPresetParser {
    fun parse(data: ByteArray): Int? {
        if (data.size < 10 || data[0] != 0xAA.toByte()) return null
        val cmd = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        return when (cmd) {
            Cmd.EQ_PRESET_NOTIFY -> {
                if (payLen < 1) return null
                (data[9].toInt() and 0xFF).takeIf { it in EqPreset.ALL }
            }
            Cmd.EQ_PRESET_RESPONSE -> {
                if (payLen < 2 || data.size < 11) return null
                // payload[0] = status (0 on success), payload[1] = preset
                (data[10].toInt() and 0xFF).takeIf { it in EqPreset.ALL }
            }
            else -> null
        }
    }
}

/** Parser for active wearing-detection reports (Cmd=0x0204, payload type=0x02). */
object WearStatusParser {
    fun parse(data: ByteArray): WearStatus? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.ANC_MODE_NOTIFY) return null

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9
        if (data.size < payloadStart + payLen || payLen < 2) return null

        val reportType = data[payloadStart].toInt() and 0xFF
        if (reportType != 0x02) return null

        val count = data[payloadStart + 1].toInt() and 0xFF
        if (payLen < 2 + count * 2) return null

        var left: WearState? = null
        var right: WearState? = null
        var case: WearState? = null

        for (j in 0 until count) {
            val idx = payloadStart + 2 + j * 2
            if (idx + 1 >= data.size) break
            val component = data[idx].toInt() and 0xFF
            val state = WearState.fromValue(data[idx + 1].toInt() and 0xFF) ?: continue
            when (component) {
                WearComponent.LEFT -> left = state
                WearComponent.RIGHT -> right = state
                WearComponent.CASE -> case = state
            }
        }

        return WearStatus(left, right, case).takeIf {
            it.left != null || it.right != null || it.case != null
        }
    }
}

/**
 * Parser for OPPO earphone ANC mode response/notification packets.
 *
 * Cmd: 0x810C (mode query response) or 0x0204 (mode change notification)
 * Scan payload for consecutive bytes 01 01 [Val1] with optional [Val2].
 * Val mapping: 0x08 0x00=Off, 0x02/0x80/0x40/0x20/0x10 0x00=NC,
 * 0x00 0x01/0x02=Transparency, 0x00 0x08=Adaptive.
 */
object AncModeParser {

    fun parse(data: ByteArray): NoiseControlMode? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)

        if (cmd != Cmd.ANC_MODE_RESPONSE && cmd != Cmd.ANC_MODE_NOTIFY) return null

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9

        if (data.size < payloadStart + payLen) return null

        // For 0x0204, skip if this is a battery report (type=0x01) or button report (type=0x02)
        if (cmd == Cmd.ANC_MODE_NOTIFY && payLen > 0) {
            val reportType = data[payloadStart].toInt() and 0xFF
            if (reportType == 0x01 || reportType == 0x02) return null
        }

        // Scan for pattern: 01 01 [Val1] with optional [Val2]
        val payloadEnd = minOf(payloadStart + payLen, data.size)
        for (i in payloadStart until payloadEnd - 2) {
            if (data[i] == 0x01.toByte() && data[i + 1] == 0x01.toByte()) {
                val val1 = data[i + 2].toInt() and 0xFF
                val val2 = if (i + 3 < payloadEnd) data[i + 3].toInt() and 0xFF else 0x00

                return when {
                    val1 == 0x08 && val2 == 0x00 -> NoiseControlMode.OFF
                    val1 == 0x02 && val2 == 0x00 -> NoiseControlMode.NOISE_CANCELLATION
                    val1 == 0x80 && val2 == 0x00 -> NoiseControlMode.NOISE_CANCELLATION_SMART
                    val1 == 0x40 && val2 == 0x00 -> NoiseControlMode.NOISE_CANCELLATION_LIGHT
                    val1 == 0x20 && val2 == 0x00 -> NoiseControlMode.NOISE_CANCELLATION_MEDIUM
                    val1 == 0x10 && val2 == 0x00 -> NoiseControlMode.NOISE_CANCELLATION_DEEP
                    val1 == 0x00 && val2 == 0x01 -> NoiseControlMode.TRANSPARENCY
                    val1 == 0x00 && val2 == 0x02 -> NoiseControlMode.TRANSPARENCY
                    val1 == 0x01 && val2 == 0x00 -> NoiseControlMode.OFF
                    val1 == 0x04 && val2 == 0x00 -> NoiseControlMode.TRANSPARENCY
                    val1 == 0x00 && val2 == 0x08 -> NoiseControlMode.ADAPTIVE
                    else -> null
                }
            }
        }
        return null
    }
}

/**
 * Parser for Transparency vocal enhancement status.
 *
 * Status can appear in 0x0404 echoes or 0x0204 notifications. The payload may be
 * plain 01 01 00 [01/02] or wrapped like 00 03 01 01 00 [01/02].
 */
object TransparencyVocalEnhancementParser {

    fun parse(data: ByteArray): Boolean? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.SET_ANC && cmd != Cmd.ANC_MODE_NOTIFY) return null

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9
        if (data.size < payloadStart + payLen) return null

        val payloadEnd = minOf(payloadStart + payLen, data.size)
        for (i in payloadStart until payloadEnd - 3) {
            if (data[i] == 0x01.toByte() &&
                data[i + 1] == 0x01.toByte() &&
                data[i + 2] == 0x00.toByte()
            ) {
                return when (data[i + 3].toInt() and 0xFF) {
                    0x01 -> false
                    0x02 -> true
                    else -> null
                }
            }
        }
        return null
    }
}

/**
 * Parser for game mode status from batch parameter query response (Cmd=0x810D).
 */
object GameModeParser {

    data class Status(
        val mainEnabled: Boolean?,
        val lowLatencyEnabled: Boolean?,
        val dualDeviceConnectionEnabled: Boolean? = null
    ) {
        fun enabledFor(implementation: GameModeImplementation): Boolean? {
            return when (implementation) {
                GameModeImplementation.STANDARD -> mainEnabled
                GameModeImplementation.COMPATIBLE -> lowLatencyEnabled ?: mainEnabled
            }
        }
    }

    fun parse(data: ByteArray, implementation: GameModeImplementation = GameModeImplementation.STANDARD): Boolean? {
        return parseStatus(data)?.enabledFor(implementation)
    }

    fun parseStatus(data: ByteArray): Status? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.QUERY_STATUS_RESPONSE) return null

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9

        if (data.size < payloadStart + payLen) return null

        val structuredStatus = parseStructuredFeaturePairs(data, payloadStart, payLen)
        if (structuredStatus != null) return structuredStatus

        var mainEnabled: Boolean? = null
        var lowLatencyEnabled: Boolean? = null
        var dualDeviceConnectionEnabled: Boolean? = null
        for (i in payloadStart until minOf(payloadStart + payLen - 1, data.size - 1)) {
            val value = data[i + 1].toInt() and 0xFF
            if (value != 0x00 && value != 0x01) continue
            when (data[i].toInt() and 0xFF) {
                GameModeFeature.MAIN -> mainEnabled = value == 0x01
                GameModeFeature.LOW_LATENCY -> lowLatencyEnabled = value == 0x01
                GameModeFeature.DUAL_DEVICE_CONNECTION -> dualDeviceConnectionEnabled = value == 0x01
            }
        }
        return if (mainEnabled != null || lowLatencyEnabled != null || dualDeviceConnectionEnabled != null) {
            Status(mainEnabled, lowLatencyEnabled, dualDeviceConnectionEnabled)
        } else {
            null
        }
    }

    private fun parseStructuredFeaturePairs(data: ByteArray, payloadStart: Int, payLen: Int): Status? {
        if (payLen < 2) return null

        val statusByte = data[payloadStart].toInt() and 0xFF
        val count = data[payloadStart + 1].toInt() and 0xFF
        if (statusByte != 0x00 || count <= 0 || payLen < 2 + count * 2) return null

        var mainEnabled: Boolean? = null
        var lowLatencyEnabled: Boolean? = null
        var dualDeviceConnectionEnabled: Boolean? = null
        for (j in 0 until count) {
            val index = payloadStart + 2 + j * 2
            val featureId = data[index].toInt() and 0xFF
            val enabled = (data[index + 1].toInt() and 0xFF) == 0x01
            when (featureId) {
                GameModeFeature.MAIN -> mainEnabled = enabled
                GameModeFeature.LOW_LATENCY -> lowLatencyEnabled = enabled
                GameModeFeature.DUAL_DEVICE_CONNECTION -> dualDeviceConnectionEnabled = enabled
            }
        }
        return if (mainEnabled != null || lowLatencyEnabled != null || dualDeviceConnectionEnabled != null) {
            Status(mainEnabled, lowLatencyEnabled, dualDeviceConnectionEnabled)
        } else {
            null
        }
    }
}

object SwitchFeatureSetParser {
    data class Result(
        val status: Int,
        val value: Int?,
        val featureId: Int? = null
    )

    private data class FeatureValue(
        val featureId: Int,
        val value: Int
    )

    fun parse(data: ByteArray): Result? {
        if (data.size < 9) return null
        if (data[0] != 0xAA.toByte()) return null

        val cmdLow = data[4].toInt() and 0xFF
        val cmdHigh = data[5].toInt() and 0xFF
        val cmd = cmdLow or (cmdHigh shl 8)
        if (cmd != Cmd.SET_GAME_MODE_RESPONSE) return null

        val payLen = (data[7].toInt() and 0xFF) or ((data[8].toInt() and 0xFF) shl 8)
        val payloadStart = 9
        if (payLen <= 0 || data.size < payloadStart + payLen) return null

        val status = data[payloadStart].toInt() and 0xFF
        val featureValue = findSwitchFeatureValue(data, payloadStart, payLen)
        val value = featureValue?.value ?: if (payLen > 1) data[payloadStart + 1].toInt() and 0xFF else null
        return Result(status, value, featureValue?.featureId)
    }

    private fun findSwitchFeatureValue(data: ByteArray, payloadStart: Int, payLen: Int): FeatureValue? {
        val payloadEnd = minOf(payloadStart + payLen, data.size)
        for (i in payloadStart until payloadEnd - 1) {
            val featureId = data[i].toInt() and 0xFF
            val value = data[i + 1].toInt() and 0xFF
            if (featureId == GameModeFeature.DUAL_DEVICE_CONNECTION && (value == 0x00 || value == 0x01)) {
                return FeatureValue(featureId, value)
            }
        }
        return null
    }
}
