package moe.chenxy.oppopods.pods

import android.content.Context
import moe.chenxy.oppopods.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/** Loads the bundled official model table and resolves models without substring matching. */
object DeviceModelRegistry {
    private const val ASSET_NAME = "device_models.json"
    private const val EQ_MODE_NAMES_ASSET_NAME = "eq_mode_names.json"
    private const val EQ_MODE_NAMES_EN_ASSET_NAME = "eq_mode_names.en.json"

    @Volatile
    private var entries: List<JSONObject> = emptyList()

    @Volatile
    private var byId: Map<String, JSONObject> = emptyMap()

    @Volatile
    private var eqModeNames: Map<Int, String> = emptyMap()

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (entries.isNotEmpty()) return
        val ownContext = if (context.packageName == BuildConfig.APPLICATION_ID) {
            context
        } else {
            runCatching {
                context.createPackageContext(
                    BuildConfig.APPLICATION_ID,
                    Context.CONTEXT_IGNORE_SECURITY,
                )
            }.getOrDefault(context)
        }
        eqModeNames = loadEqModeNames(ownContext)
        val list = runCatching {
            val text = ownContext.assets.open(ASSET_NAME).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val array = JSONObject(text).optJSONArray("whiteList") ?: JSONArray()
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        }.getOrDefault(emptyList())
        entries = list
        byId = buildMap {
            for (entry in list) {
                val id = entry.optString("id").uppercase().takeIf { it.isNotBlank() } ?: continue
                val existing = get(id)
                if (existing != null && existing.has("function") && !entry.has("function")) continue
                put(id, entry)
            }
        }
    }

    fun byProductId(context: Context, productId: String?): ModelCapabilities? {
        ensureLoaded(context)
        val id = productId?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        return byId[id]?.let(::parse)
    }

    fun byDeviceName(context: Context, deviceName: String?): ModelCapabilities? {
        ensureLoaded(context)
        val target = normalize(deviceName.orEmpty())
        if (target.isEmpty()) return null
        var fallback: JSONObject? = null
        for (entry in entries) {
            if (normalize(entry.optString("name")) != target) continue
            if (entry.has("function")) return parse(entry)
            if (fallback == null) fallback = entry
        }
        return fallback?.let(::parse)
    }

    private fun parse(entry: JSONObject): ModelCapabilities {
        val function = entry.optJSONObject("function")
        val spatialTypes = function?.optJSONArray("spatialTypes")?.let { array ->
            (0 until array.length()).map { array.optInt(it) }.distinct()
        }.orEmpty()
        val ancModes = function?.optJSONArray("noiseReductionMode")
        val gameSoundList = function?.optJSONArray("gameSoundList")
        val hasGameSound = gameSoundList != null && (0 until gameSoundList.length()).any {
            (gameSoundList.optJSONObject(it)?.optInt("type") ?: 0) != 0
        }
        return ModelCapabilities(
            modelId = entry.optString("id"),
            modelName = entry.optString("name"),
            adaptiveSupported = hasTopLevelAncMode(ancModes, 6, 10),
            spatialAudioSupported = spatialTypes.contains(SpatialAudioMode.HEAD_TRACKING),
            spatialSoundSwitchSupported = spatialTypes.isNotEmpty() &&
                !spatialTypes.contains(SpatialAudioMode.HEAD_TRACKING),
            legacyAnc = isLegacyAnc(ancModes),
            dualDeviceSupported = flagOn(function, "multiDevicesConnect") ||
                (function?.optJSONArray("multiConnectFunctions")?.length() ?: 0) > 0,
            customEqSupported = flagOn(function, "customEqualizer"),
            eqPresets = parseEqPresets(function),
            customEqFrequencies = function?.optJSONArray("customEqFrequency")?.let { array ->
                (0 until array.length()).mapNotNull { array.optInt(it, 0).takeIf { value -> value > 0 } }
            }.orEmpty(),
            customEqMaxPresets = function?.optInt("customEqMax", 0)?.coerceAtLeast(0) ?: 0,
            gameModeFeatureId = if (hasGameSound) GameModeFeature.MAIN else GameModeFeature.LOW_LATENCY,
            gameModeSupported = gameModeSupported(function) || hasGameSound,
        )
    }

    private fun parseEqPresets(function: JSONObject?): List<EqPresetInfo> {
        if (function == null) return emptyList()
        val result = LinkedHashMap<Int, EqPresetInfo>()
        listOf("equalizerMode", "equalizerModeCompat", "equalizerModeByVersion").forEach { key ->
            val modes = function.optJSONArray(key) ?: return@forEach
            for (index in 0 until modes.length()) {
                val mode = modes.optJSONObject(index) ?: continue
                val id = mode.optInt("protocolIndex", -1)
                if (id < 0 || result.containsKey(id)) continue
                val modeType = mode.optInt("modeType", -1)
                result[id] = EqPresetInfo(id, eqModeNames[modeType] ?: "M$id", modeType)
            }
        }
        return result.values.sortedBy { it.id }
    }

    private fun loadEqModeNames(context: Context): Map<Int, String> {
        val language = context.resources.configuration.locales[0].language.lowercase()
        val preferred = if (language == "zh") EQ_MODE_NAMES_ASSET_NAME else EQ_MODE_NAMES_EN_ASSET_NAME
        for (assetName in listOf(preferred, EQ_MODE_NAMES_ASSET_NAME).distinct()) {
            val mapping = runCatching {
                val text = context.assets.open(assetName).use { it.readBytes().toString(Charsets.UTF_8) }
                JSONObject(text).optJSONObject("mapping")
            }.getOrNull() ?: continue
            return buildMap {
                for (key in mapping.keys()) {
                    key.toIntOrNull()?.let { id ->
                        mapping.optString(key).takeIf { it.isNotBlank() }?.let { put(id, it) }
                    }
                }
            }
        }
        return emptyMap()
    }

    private fun flagOn(function: JSONObject?, key: String): Boolean {
        val value = function?.opt(key) ?: return false
        return when (value) {
            is Number -> value.toInt() >= 1
            is Boolean -> value
            else -> false
        }
    }

    private fun gameModeSupported(function: JSONObject?): Boolean {
        if (function == null) return false
        val list = function.optJSONArray("gameModeList")
        if (list != null && list.length() > 0) {
            return (0 until list.length()).any {
                (list.optJSONObject(it)?.optInt("gameMode") ?: 0) == 1
            }
        }
        return if (function.has("gameMode")) flagOn(function, "gameMode") else true
    }

    private fun hasTopLevelAncMode(modes: JSONArray?, vararg modeTypes: Int): Boolean {
        if (modes == null) return false
        return (0 until modes.length()).any { index ->
            val modeType = modes.optJSONObject(index)?.optInt("modeType", -1) ?: -1
            modeType in modeTypes
        }
    }

    private fun isLegacyAnc(modes: JSONArray?): Boolean {
        if (modes == null || modes.length() == 0) return false
        val hasChildren = (0 until modes.length()).any {
            modes.optJSONObject(it)?.optJSONArray("childrenMode") != null
        }
        return !hasChildren && (0 until modes.length()).any { index ->
            val mode = modes.optJSONObject(index) ?: return@any false
            mode.optInt("modeType", -1) == 5 && mode.optInt("protocolIndex", -1) == 0
        }
    }

    private fun normalize(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }
}

data class ModelCapabilities(
    val modelId: String,
    val modelName: String,
    val adaptiveSupported: Boolean,
    val spatialAudioSupported: Boolean,
    val spatialSoundSwitchSupported: Boolean,
    val legacyAnc: Boolean,
    val dualDeviceSupported: Boolean,
    val customEqSupported: Boolean,
    val eqPresets: List<EqPresetInfo>,
    val customEqFrequencies: List<Int>,
    val customEqMaxPresets: Int,
    val gameModeFeatureId: Int,
    val gameModeSupported: Boolean,
)
