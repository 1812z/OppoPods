package moe.chenxy.oppopods.config

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppConfig(
    val fakeDeviceId: String = ConfigManager.DEFAULT_FAKE_DEVICE_ID,
    val logLevel: Int = ConfigManager.LOG_LEVEL_BASIC,
    val islandMode: Int = ConfigManager.ISLAND_MODE_OFFICIAL,
    val islandShowTimings: Set<Int> = emptySet(),
    val notificationClickAction: Int = ConfigManager.NOTIFICATION_CLICK_MODULE_POPUP,
    val moreClickAction: Int = ConfigManager.MORE_CLICK_MODULE,
    val milinkCardFeatures: Set<Int> = ConfigManager.DEFAULT_MILINK_CARD_FEATURES,
    val autoGameMode: Boolean = false,
)

object ConfigManager {
    private const val TAG = "OppoPods-Config"
    const val PREFS_NAME = "oppopods_settings"
    const val PREF_KEY_CONFIG_JSON = "config_json"
    const val PREF_KEY_FAKE_DEVICE_ID = "fake_device_id"
    const val PREF_KEY_LOG_LEVEL = "log_level"
    const val PREF_KEY_ISLAND_MODE = "island_mode"
    const val PREF_KEY_ISLAND_SHOW_TIMINGS = "island_show_timings"
    const val PREF_KEY_NOTIFICATION_CLICK_ACTION = "notification_click_action"
    const val PREF_KEY_MORE_CLICK_ACTION = "more_click_action"
    const val PREF_KEY_MILINK_CARD_FEATURES = "milink_card_features"
    const val PREF_KEY_AUTO_GAME_MODE = "auto_game_mode"
    const val DEFAULT_FAKE_DEVICE_ID = "01010607"
    const val LOG_LEVEL_OFF = 0
    const val LOG_LEVEL_BASIC = 1
    const val LOG_LEVEL_DEBUG = 2
    const val ISLAND_MODE_NONE = 0
    const val ISLAND_MODE_OFFICIAL = 1
    const val ISLAND_MODE_MODULE = 2
    const val ISLAND_SHOW_TIMING_CONNECTED = 0
    const val ISLAND_SHOW_TIMING_WEARING = 1
    const val ISLAND_SHOW_TIMING_REMOVED = 2
    const val ISLAND_SHOW_TIMING_IN_CASE = 3
    const val NOTIFICATION_CLICK_MODULE_POPUP = 0
    const val NOTIFICATION_CLICK_SYSTEM_SETTINGS = 1
    const val NOTIFICATION_CLICK_HEYTAP = 2
    const val MORE_CLICK_HEYTAP = 0
    const val MORE_CLICK_SYSTEM_SETTINGS = 1
    const val MORE_CLICK_MODULE = 2
    const val SPATIAL_AUDIO_OFF = 0
    const val SPATIAL_AUDIO_FIXED = 1
    const val SPATIAL_AUDIO_HEAD_TRACKING = 2
    const val MILINK_CARD_GAME_MODE = 0
    const val MILINK_CARD_SPATIAL_AUDIO = 1
    val DEFAULT_MILINK_CARD_FEATURES = setOf(MILINK_CARD_GAME_MODE, MILINK_CARD_SPATIAL_AUDIO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedConfig: AppConfig = AppConfig()

    fun init(prefs: SharedPreferences) {
        val oldConfig = cachedConfig
        cachedConfig = readConfig(prefs, "init")
        logConfigChange("init", oldConfig, cachedConfig)
    }

    fun refreshFromPrefs(prefs: SharedPreferences): AppConfig {
        val oldConfig = cachedConfig
        return readConfig(prefs, "refreshFromPrefs").also {
            cachedConfig = it
            logConfigChange("refreshFromPrefs", oldConfig, it)
        }
    }

    fun current(): AppConfig = cachedConfig

    fun fakeDeviceId(): String = current().fakeDeviceId.normalizedFakeDeviceId()

    fun logLevel(): Int = current().logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG)

    fun islandMode(): Int = current().islandMode.coerceIn(ISLAND_MODE_NONE, ISLAND_MODE_MODULE)

    fun islandShowTimings(): Set<Int> = current().islandShowTimings.normalizedIslandShowTimings()

    fun notificationClickAction(): Int = current().notificationClickAction.coerceIn(NOTIFICATION_CLICK_MODULE_POPUP, NOTIFICATION_CLICK_HEYTAP)

    fun moreClickAction(): Int = current().moreClickAction.coerceIn(MORE_CLICK_HEYTAP, MORE_CLICK_MODULE)

    fun milinkCardFeatures(): Set<Int> = current().milinkCardFeatures.normalizedMilinkCardFeatures()

    fun autoGameMode(): Boolean = current().autoGameMode

    fun fakeSupport(): String = "${fakeDeviceId()},000000000000000010000000"

    fun updateFakeDeviceId(prefs: SharedPreferences, fakeDeviceId: String) {
        val config = current().copy(fakeDeviceId = fakeDeviceId.normalizedFakeDeviceId())
        save(prefs, config)
    }

    fun updateFakeDeviceId(prefs: SharedPreferences, service: XposedService?, fakeDeviceId: String) {
        val config = current().copy(fakeDeviceId = fakeDeviceId.normalizedFakeDeviceId())
        save(prefs, service, config)
    }

    fun updateLogLevel(prefs: SharedPreferences, service: XposedService?, logLevel: Int) {
        val config = current().copy(logLevel = logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG))
        save(prefs, service, config)
    }

    fun updateIslandMode(prefs: SharedPreferences, service: XposedService?, islandMode: Int) {
        val config = current().copy(islandMode = islandMode.coerceIn(ISLAND_MODE_NONE, ISLAND_MODE_MODULE))
        save(prefs, service, config)
    }

    fun updateIslandShowTimings(prefs: SharedPreferences, service: XposedService?, timings: Set<Int>) {
        val config = current().copy(islandShowTimings = timings.normalizedIslandShowTimings())
        save(prefs, service, config)
    }

    fun updateNotificationClickAction(prefs: SharedPreferences, service: XposedService?, action: Int) {
        val config = current().copy(notificationClickAction = action.coerceIn(NOTIFICATION_CLICK_MODULE_POPUP, NOTIFICATION_CLICK_HEYTAP))
        save(prefs, service, config)
    }

    fun updateMoreClickAction(prefs: SharedPreferences, service: XposedService?, action: Int) {
        val config = current().copy(moreClickAction = action.coerceIn(MORE_CLICK_HEYTAP, MORE_CLICK_MODULE))
        save(prefs, service, config)
    }

    fun updateMilinkCardFeatures(prefs: SharedPreferences, service: XposedService?, features: Set<Int>) {
        save(prefs, service, current().copy(milinkCardFeatures = features.normalizedMilinkCardFeatures()))
    }

    fun updateAutoGameMode(prefs: SharedPreferences, service: XposedService?, enabled: Boolean) {
        save(prefs, service, current().copy(autoGameMode = enabled))
    }

    fun save(prefs: SharedPreferences, config: AppConfig) {
        val oldConfig = cachedConfig
        val normalized = config.copy(fakeDeviceId = config.fakeDeviceId.normalizedFakeDeviceId())
        cachedConfig = normalized
        writePrefs(prefs, normalized)
        logConfigChange("save", oldConfig, normalized)
    }

    fun save(prefs: SharedPreferences, service: XposedService?, config: AppConfig) {
        val oldConfig = cachedConfig
        val normalized = config.copy(fakeDeviceId = config.fakeDeviceId.normalizedFakeDeviceId())
        cachedConfig = normalized
        writePrefs(prefs, normalized)
        service?.getRemotePreferences(PREFS_NAME)?.let { remotePrefs ->
            writePrefs(remotePrefs, normalized)
            Log.d(TAG, "save remote prefs class=${remotePrefs.javaClass.name} fakeDeviceId=${normalized.fakeDeviceId}")
        } ?: Log.w(TAG, "save remote prefs skipped: LSPosed service is null")
        logConfigChange("save", oldConfig, normalized)
    }

    private fun writePrefs(prefs: SharedPreferences, config: AppConfig) {
        prefs.edit()
            .putString(PREF_KEY_CONFIG_JSON, json.encodeToString(AppConfig.serializer(), config))
            .putString(PREF_KEY_FAKE_DEVICE_ID, config.fakeDeviceId)
            .putInt(PREF_KEY_LOG_LEVEL, config.logLevel)
            .putInt(PREF_KEY_ISLAND_MODE, config.islandMode)
            .putStringSet(PREF_KEY_ISLAND_SHOW_TIMINGS, config.islandShowTimings.map { it.toString() }.toSet())
            .putInt(PREF_KEY_NOTIFICATION_CLICK_ACTION, config.notificationClickAction)
            .putInt(PREF_KEY_MORE_CLICK_ACTION, config.moreClickAction)
            .putStringSet(PREF_KEY_MILINK_CARD_FEATURES, config.milinkCardFeatures.map(Int::toString).toSet())
            .putBoolean(PREF_KEY_AUTO_GAME_MODE, config.autoGameMode)
            .commit()
    }

    private fun readConfig(prefs: SharedPreferences, source: String): AppConfig {
        val directFakeDeviceId = prefs.getString(PREF_KEY_FAKE_DEVICE_ID, null)
        val directLogLevel = prefs.getInt(PREF_KEY_LOG_LEVEL, Int.MIN_VALUE)
        val directIslandMode = prefs.getInt(PREF_KEY_ISLAND_MODE, Int.MIN_VALUE)
        val directIslandShowTimings = prefs.getStringSet(PREF_KEY_ISLAND_SHOW_TIMINGS, null)?.mapNotNull { it.toIntOrNull() }?.toSet()
        val directNotificationClickAction = prefs.getInt(PREF_KEY_NOTIFICATION_CLICK_ACTION, Int.MIN_VALUE)
        val directMoreClickAction = prefs.getInt(PREF_KEY_MORE_CLICK_ACTION, Int.MIN_VALUE)
        val directMilinkCardFeatures = prefs.getStringSet(PREF_KEY_MILINK_CARD_FEATURES, null)
            ?.mapNotNull(String::toIntOrNull)
            ?.toSet()
        val directAutoGameMode = prefs.takeIf { it.contains(PREF_KEY_AUTO_GAME_MODE) }
            ?.getBoolean(PREF_KEY_AUTO_GAME_MODE, false)
        val raw = prefs.getString(PREF_KEY_CONFIG_JSON, null)
        logPrefsSnapshot(source, prefs, directFakeDeviceId, raw)
        val config = raw?.let {
            runCatching { json.decodeFromString(AppConfig.serializer(), it) }.getOrNull()
        } ?: AppConfig()
        val migratedMoreClickAction = if (prefs.getBoolean("open_heytap", false)) MORE_CLICK_HEYTAP else config.moreClickAction
        if (!directFakeDeviceId.isNullOrBlank()) {
            return config.copy(
                fakeDeviceId = directFakeDeviceId.normalizedFakeDeviceId(),
                logLevel = directLogLevel.takeIf { it != Int.MIN_VALUE } ?: config.logLevel,
                islandMode = directIslandMode.takeIf { it != Int.MIN_VALUE } ?: config.islandMode,
                islandShowTimings = directIslandShowTimings ?: config.islandShowTimings,
                notificationClickAction = directNotificationClickAction.takeIf { it != Int.MIN_VALUE } ?: config.notificationClickAction,
                moreClickAction = directMoreClickAction.takeIf { it != Int.MIN_VALUE } ?: migratedMoreClickAction,
                milinkCardFeatures = directMilinkCardFeatures ?: config.milinkCardFeatures,
                autoGameMode = directAutoGameMode ?: config.autoGameMode,
            ).normalized()
        }
        return config.copy(
            fakeDeviceId = config.fakeDeviceId.normalizedFakeDeviceId(),
            logLevel = directLogLevel.takeIf { it != Int.MIN_VALUE } ?: config.logLevel,
            islandMode = directIslandMode.takeIf { it != Int.MIN_VALUE } ?: config.islandMode,
            islandShowTimings = directIslandShowTimings ?: config.islandShowTimings,
            notificationClickAction = directNotificationClickAction.takeIf { it != Int.MIN_VALUE } ?: config.notificationClickAction,
            moreClickAction = directMoreClickAction.takeIf { it != Int.MIN_VALUE } ?: migratedMoreClickAction,
            milinkCardFeatures = directMilinkCardFeatures ?: config.milinkCardFeatures,
            autoGameMode = directAutoGameMode ?: config.autoGameMode,
        ).normalized()
    }

    private fun AppConfig.normalized(): AppConfig = copy(
        fakeDeviceId = fakeDeviceId.normalizedFakeDeviceId(),
        logLevel = logLevel.coerceIn(LOG_LEVEL_OFF, LOG_LEVEL_DEBUG),
        islandMode = islandMode.coerceIn(ISLAND_MODE_NONE, ISLAND_MODE_MODULE),
        islandShowTimings = islandShowTimings.normalizedIslandShowTimings(),
        notificationClickAction = notificationClickAction.coerceIn(NOTIFICATION_CLICK_MODULE_POPUP, NOTIFICATION_CLICK_HEYTAP),
        moreClickAction = moreClickAction.coerceIn(MORE_CLICK_HEYTAP, MORE_CLICK_MODULE),
        milinkCardFeatures = milinkCardFeatures.normalizedMilinkCardFeatures(),
    )

    private fun String.normalizedFakeDeviceId(): String = trim().takeIf { it.isNotEmpty() } ?: DEFAULT_FAKE_DEVICE_ID

    private fun Set<Int>.normalizedIslandShowTimings(): Set<Int> = filterTo(mutableSetOf()) {
        it in ISLAND_SHOW_TIMING_CONNECTED..ISLAND_SHOW_TIMING_IN_CASE
    }

    private fun Set<Int>.normalizedMilinkCardFeatures(): Set<Int> = filterTo(mutableSetOf()) {
        it == MILINK_CARD_GAME_MODE || it == MILINK_CARD_SPATIAL_AUDIO
    }

    private fun logConfigChange(source: String, oldConfig: AppConfig, newConfig: AppConfig) {
        val changes = changedFields(oldConfig, newConfig)
        if (changes.isEmpty()) {
            Log.d(TAG, "$source config unchanged: $newConfig")
        } else {
            Log.d(TAG, "$source config changed: ${changes.joinToString()}")
        }
    }

    private fun logPrefsSnapshot(source: String, prefs: SharedPreferences, directFakeDeviceId: String?, rawConfig: String?) {
        val all = runCatching { prefs.all }.getOrElse { error -> mapOf("<getAllError>" to error.message) }
        Log.d(
            TAG,
            "$source prefs snapshot class=${prefs.javaClass.name} keys=${all.keys.sorted()} " +
                "$PREF_KEY_FAKE_DEVICE_ID=$directFakeDeviceId $PREF_KEY_CONFIG_JSON=$rawConfig all=$all"
        )
    }

    private fun changedFields(oldConfig: AppConfig, newConfig: AppConfig): List<String> {
        return buildList {
            if (oldConfig.fakeDeviceId != newConfig.fakeDeviceId) {
                add("fakeDeviceId=${oldConfig.fakeDeviceId}->${newConfig.fakeDeviceId}")
            }
            if (oldConfig.logLevel != newConfig.logLevel) {
                add("logLevel=${oldConfig.logLevel}->${newConfig.logLevel}")
            }
            if (oldConfig.islandMode != newConfig.islandMode) {
                add("islandMode=${oldConfig.islandMode}->${newConfig.islandMode}")
            }
            if (oldConfig.islandShowTimings != newConfig.islandShowTimings) {
                add("islandShowTimings=${oldConfig.islandShowTimings}->${newConfig.islandShowTimings}")
            }
            if (oldConfig.notificationClickAction != newConfig.notificationClickAction) {
                add("notificationClickAction=${oldConfig.notificationClickAction}->${newConfig.notificationClickAction}")
            }
            if (oldConfig.moreClickAction != newConfig.moreClickAction) {
                add("moreClickAction=${oldConfig.moreClickAction}->${newConfig.moreClickAction}")
            }
            if (oldConfig.milinkCardFeatures != newConfig.milinkCardFeatures) {
                add("milinkCardFeatures=${oldConfig.milinkCardFeatures}->${newConfig.milinkCardFeatures}")
            }
            if (oldConfig.autoGameMode != newConfig.autoGameMode) {
                add("autoGameMode=${oldConfig.autoGameMode}->${newConfig.autoGameMode}")
            }
        }
    }
}
