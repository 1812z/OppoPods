package moe.chenxy.oppopods.hook.milink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import moe.chenxy.oppopods.BuildConfig
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.hook.HookContext
import moe.chenxy.oppopods.hook.Log
import moe.chenxy.oppopods.hook.callMethod
import moe.chenxy.oppopods.hook.getObjectField
import moe.chenxy.oppopods.hook.setObjectField
import moe.chenxy.oppopods.pods.RfcommController
import moe.chenxy.oppopods.pods.detectDeviceCapabilities
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.oppopods.utils.miuiStrongToast.data.OppoPodsAction
import moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams

@SuppressLint("MissingPermission")
object MiLinkServiceHook : HookContext() {
    internal const val TAG = "OppoPods-MiLink"
    private const val PREFS_NAME = "oppopods_milink_state"
    private val knownOppoAddresses = linkedSetOf<String>()
    internal var context: Context? = null
    private var currentProductId: String? = null
    private var receiverRegistered = false
    internal var currentAddress: String? = null
    private var currentName: String? = null
    private var currentBattery: BatteryParams = BatteryParams()
    private var currentAnc = 1
    internal var currentSpatialAudioMode = ConfigManager.SPATIAL_AUDIO_OFF
    internal var lastAncBatteryController: Any? = null
    internal var lastProfileContext: Any? = null
    private val spatialAudioHook = MiLinkSpatialAudioHook(this)

    override fun onHook() {
        hookContextEntry()
        hookMxBluetoothRuntime()
        hookHeadsetRuntimeDisplay()
        spatialAudioHook.hookCirculateHeadsetServiceInfo()
    }

    private fun hookContextEntry() {
        listOf(
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService",
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager"
        ).forEach { className ->
            runCatching {
                hookBefore(findMethod(className, "getInstanceForIsMiTWS", Context::class.java)) {
                    registerStatusReceiver(args[0] as? Context)
                }
            }.onFailure { Log.w(TAG, "hook $className.getInstanceForIsMiTWS skipped", it) }
        }
    }

    private fun hookMxBluetoothRuntime() {
        val classes = listOf(
            "com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager",
            "com.xiaomi.mxbluetoothsdk.service.MxBluetoothService"
        )
        classes.forEach { className ->
            hookBluetoothDeviceResult(className, "checkIsMiTWS") { 1 }
            hookBluetoothDeviceResult(className, "getDeviceId") { fakeDeviceId() }
            hookBluetoothDeviceResult(className, "getBatteryLevel") { 1 }
            hookBluetoothDeviceResult(className, "getAncState") { miLinkAncState() }
            hookBluetoothDeviceResult(className, "getDeviceRunInfo") { 0 }
            hookBluetoothDeviceResult(className, "getWearStatus") { "0,0" }
            hookBluetoothDeviceResult(className, "isLeAudio") { false }
            hookAncCommand(className, "openAnc", 2, 1)
            hookAncCommand(className, "closeAnc", 1, 0)
            hookAncCommand(className, "openTransparent", 3, 2)
        }
        classes.forEach { className ->
            hookStringAddressResult(className, "isMiTWS") { true }
            hookStringAddressResult(className, "isSupportAudioSwitch") { miLinkSwitchState() }
            hookStringAddressResult(className, "getRingFindState") { false }
        }
        spatialAudioHook.hookMxBluetoothRuntime(classes)
    }

    private fun hookHeadsetRuntimeDisplay() {
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.ProfileContext", "getBatteryLevel") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getDeviceId") { fakeDeviceId() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getAncState") { miLinkAncState() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getBatteryLevelCache") { miLinkBatteryLevels() }
        hookBluetoothDeviceResult("com.miui.headset.runtime.AncBatteryController", "getHeadsetPropertyBlock") { batteryPercentForMiLink() }
        hookStringAddressResult("com.miui.headset.runtime.AncBatteryController", "getSwitchState") { miLinkSwitchState() }
        hookAncStateBlock()
        spatialAudioHook.hookHeadsetRuntimeDisplay()
        hookHeadsetInfoNoArg("getDeviceId") { fakeDeviceId() }
        hookHeadsetInfoNoArg("component3") { fakeDeviceId() }
        hookHeadsetInfoNoArg("getPowers") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("component4") { miLinkBatteryLevels() }
        hookHeadsetInfoNoArg("getMode") { miLinkAncState() }
        hookHeadsetInfoNoArg("component5") { miLinkAncState() }
        hookHeadsetInfoNoArg("getSwitchState") { miLinkSwitchState() }
        hookHeadsetInfoNoArg("component8") { miLinkSwitchState() }
    }

    internal fun hookBluetoothDeviceResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                if (!isOppoPod(device)) return@hookAfter
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                this.result = result()
                if (className == "com.miui.headset.runtime.AncBatteryController" && methodName == "getHeadsetPropertyBlock") {
                    notifyHeadsetPropertyChanged(instance, device, 4)
                }
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(BluetoothDevice) skipped", it) }
    }

    internal fun hookStringAddressResult(className: String, methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethod(className, methodName, String::class.java)) {
                val address = args[0] as? String ?: return@hookAfter
                if (!isOppoAddress(address)) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName(String) skipped", it) }
    }

    private fun hookAncCommand(className: String, methodName: String, oppoAnc: Int, result: Int) {
        runCatching {
            hookBefore(findMethod(className, methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                if (!isOppoPod(device)) return@hookBefore
                cacheRuntimeOwner(className, instance)
                captureRuntimeContext(instance)
                currentAnc = oppoAnc
                sendOppoAnc(oppoAnc)
                sendAncChanged(oppoAnc)
                this.result = result
            }
        }.onFailure { Log.w(TAG, "hook $className.$methodName command skipped", it) }
    }

    private fun hookAncStateBlock() {
        runCatching {
            hookBefore(findMethod("com.miui.headset.runtime.AncBatteryController", "setAncStateBlock", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) {
                val device = args[0] as? BluetoothDevice ?: return@hookBefore
                if (!isOppoPod(device)) return@hookBefore
                lastAncBatteryController = instance
                captureRuntimeContext(instance)
                val miLinkMode = args[1] as? Int ?: return@hookBefore
                val oppoAnc = oppoAncFromMiLink(miLinkMode)
                val instanceContext = runCatching { getObjectField(instance, "context") as? Context }.getOrNull()
                if (instanceContext != null) {
                    context = instanceContext.applicationContext ?: instanceContext
                }
                currentAnc = oppoAnc
                sendOppoAnc(oppoAnc, instanceContext)
                sendAncChanged(oppoAnc, instanceContext)
                notifyHeadsetPropertyChanged(instance, device, 8)
                notifyHeadsetPropertyChanged(instance, device, 4)
                this.result = miLinkAncState()
            }
        }.onFailure { Log.w(TAG, "hook AncBatteryController.setAncStateBlock skipped", it) }
    }

    internal fun hookHeadsetInfoNoArg(methodName: String, result: () -> Any) {
        runCatching {
            hookAfter(findMethodByParamCount("com.miui.headset.api.HeadsetInfo", methodName, 0)) {
                if (!isTargetHeadsetInfo(instance)) return@hookAfter
                this.result = result()
            }
        }.onFailure { Log.w(TAG, "hook HeadsetInfo.$methodName skipped", it) }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addAction(OppoPodsAction.ACTION_PODS_CONNECTED)
            addAction(OppoPodsAction.ACTION_PODS_DISCONNECTED)
            addAction(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_ANC_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED)
            addAction(OppoPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    OppoPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                    }
                    OppoPodsAction.ACTION_PODS_CONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentName = intent.getStringExtra("device_name") ?: currentName
                        currentProductId = intent.getStringExtra("product_id") ?: currentProductId
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                    }
                    OppoPodsAction.ACTION_PODS_DISCONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentProductId = null
                    }
                    OppoPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentBattery = intent.batteryStatusFromExtras() ?: intent.parcelableStatus() ?: currentBattery
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                        saveState(context)
                    }
                    OppoPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAnc = intent.getIntExtra("status", currentAnc)
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                        saveState(context)
                    }
                    OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentSpatialAudioMode = intent.getIntExtra("mode", currentSpatialAudioMode)
                            .coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                        saveState(context)
                    }
                }
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_UI_INIT).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    internal fun isOppoPod(device: BluetoothDevice): Boolean {
        val address = runCatching { device.address }.getOrNull()
        if (address != null && isOppoAddress(address)) return true
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = name.contains("oppo", ignoreCase = true)
        if (result && address != null) {
            knownOppoAddresses.add(address.uppercase())
            currentAddress = address
            currentName = name
        }
        return result
    }

    internal fun isOppoAddress(address: String): Boolean {
        val normalized = address.uppercase()
        return normalized == currentAddress?.uppercase() || normalized in knownOppoAddresses
    }

    private fun isTargetHeadsetInfo(info: Any?): Boolean {
        if (info == null) return false
        listOf("getAddress", "component1").forEach { method ->
            val address = runCatching { callMethod(info, method) as? String }.getOrNull()
            if (address != null && isOppoAddress(address)) return true
        }
        return false
    }

    private fun miLinkAncState(): Int {
        loadState()
        return when (currentAnc) {
            2, 5, 6, 7, 8 -> 1
            3 -> 2
            else -> 0
        }
    }

    private fun oppoAncFromMiLink(mode: Int): Int {
        return when (mode) {
            1 -> 2
            2 -> 3
            else -> 1
        }
    }

    private fun miLinkBatteryLevels(): List<Int> {
        loadState()
        val left = batteryValue(currentBattery.left)
        val right = batteryValue(currentBattery.right)
        val box = batteryValue(currentBattery.case)
        return listOf(
            box,
            left,
            right,
            chargingValue(currentBattery.case),
            chargingValue(currentBattery.left),
            chargingValue(currentBattery.right)
        )
    }

    private fun batteryPercentForMiLink(): Int {
        loadState()
        val values = listOfNotNull(currentBattery.left, currentBattery.right)
            .filter { it.isConnected }
            .map { it.battery.coerceIn(0, 100) }
        return values.minOrNull() ?: 0
    }

    private fun batteryValue(params: moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams?): Int {
        if (params?.isConnected != true) return -1
        return params.battery.coerceIn(0, 100)
    }

    private fun chargingValue(params: moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams?): Int {
        return if (params?.isConnected == true && params.isCharging) 1 else 0
    }

    private fun sendOppoAnc(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "sendOppoAnc skipped: context is null mode=$mode")
            return
        }
        Intent(OppoPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", mode)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    private fun sendAncChanged(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: return
        listOf(BuildConfig.APPLICATION_ID, "com.milink.service", "com.android.settings").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_ANC_CHANGED).apply {
                putExtra("status", mode)
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    internal fun sendOppoSpatialAudio(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: run {
            Log.w(TAG, "sendOppoSpatialAudio skipped: context is null mode=$mode")
            return
        }
        Intent(OppoPodsAction.ACTION_SPATIAL_AUDIO_SET).apply {
            putExtra("mode", mode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING))
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            ctx.sendBroadcast(this)
        }
    }

    internal fun sendSpatialChanged(mode: Int, fallbackContext: Context? = null) {
        val ctx = fallbackContext ?: context ?: return
        val normalizedMode = mode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        listOf(BuildConfig.APPLICATION_ID, "com.milink.service", "com.android.settings").forEach { targetPackage ->
            ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_SPATIAL_AUDIO_CHANGED).apply {
                currentAddress?.let { putExtra("address", it) }
                putExtra("mode", normalizedMode)
                setPackage(targetPackage)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            })
        }
    }

    internal fun miLinkSpatialMode(): Int {
        loadState()
        if (!spatialAudioPanelEnabled()) return -1
        return when (currentAudioEffectState()) {
            ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING -> if (miLinkDeviceSpatialType() == 1) 11 else 9
            ConfigManager.SPATIAL_AUDIO_FIXED -> 1
            else -> 0
        }
    }

    internal fun oppoSpatialFromMiLink(mode: Int): Int {
        return when (mode) {
            9, 11 -> ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING
            1 -> ConfigManager.SPATIAL_AUDIO_FIXED
            else -> ConfigManager.SPATIAL_AUDIO_OFF
        }
    }

    internal fun miLinkSpatialModeFromMode(mode: Int): Int {
        return when (mode) {
            ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING -> if (miLinkDeviceSpatialType() == 1) 11 else 9
            ConfigManager.SPATIAL_AUDIO_FIXED -> 1
            ConfigManager.SPATIAL_AUDIO_OFF -> 0
            else -> -1
        }
    }

    internal fun miLinkAudioEffectState(): Int {
        loadState()
        return currentAudioEffectState()
    }

    internal fun miLinkDeviceSpatialType(): Int {
        return if (spatialAudioPanelEnabled()) 1 else 0
    }

    internal fun miLinkSwitchState(): Int {
        return if (spatialAudioPanelEnabled()) 1 else 0
    }

    internal fun updateSpatialAudioMode(mode: Int) {
        currentSpatialAudioMode = mode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        saveState(context)
    }

    private fun currentAudioEffectState(): Int {
        return if (spatialAudioPanelEnabled()) {
            currentSpatialAudioMode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        } else {
            -1
        }
    }

    internal fun spatialAudioPanelEnabled(): Boolean {
        runCatching { refreshConfig() }
        return panelCapabilities().spatialAudioSupported
    }

    private fun panelCapabilities() = detectDeviceCapabilities(
        context = context,
        deviceName = backendDeviceName() ?: currentName.orEmpty(),
        productId = runCatching { RfcommController.currentStatusSnapshot().productId }.getOrNull()
            ?: currentProductId,
    )

    private fun backendDeviceName(): String? {
        return runCatching { RfcommController.currentStatusSnapshot().deviceName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    internal fun isTargetAncBatteryModel(model: Any?): Boolean {
        val device = runCatching { callMethod(model, "getBluetoothDevice") as? BluetoothDevice }.getOrNull()
        return device?.let { isOppoPod(it) } == true
    }

    internal fun cacheRuntimeOwner(className: String, owner: Any?) {
        when (className) {
            "com.miui.headset.runtime.AncBatteryController" -> lastAncBatteryController = owner
            "com.miui.headset.runtime.ProfileContext" -> lastProfileContext = owner
        }
    }

    internal fun captureRuntimeContext(owner: Any?) {
        val ownerContext = runCatching { getObjectField(owner, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastProfileContext, "context") as? Context }.getOrNull()
            ?: runCatching { getObjectField(lastAncBatteryController, "context") as? Context }.getOrNull()
            ?: return
        context = ownerContext.applicationContext ?: ownerContext
    }

    internal fun notifySpatialUiChanged(owner: Any?, device: BluetoothDevice, mode: Int) {
        val spatialMode = miLinkSpatialModeFromMode(mode)
        val audioEffectState = mode.coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        syncSpatialModel(owner, device, spatialMode)
        syncSpatialModel(lastAncBatteryController, device, spatialMode)
        listOf(owner, lastAncBatteryController, lastProfileContext).distinctBy { it?.javaClass?.name }.forEach { target ->
            notifyHeadsetPropertyChanged(target, device, 9)
            notifyHeadsetPropertyChanged(target, device, 4)
            notifyProfileAudioEffectListeners(target, audioEffectState)
        }
    }

    private fun syncSpatialModel(owner: Any?, device: BluetoothDevice, spatialMode: Int) {
        val model = runCatching { getObjectField(owner, "ancBatteryModel") }.getOrNull() ?: return
        if (!isTargetAncBatteryModel(model)) return
        runCatching { setObjectField(model, "spatialState", spatialMode) }
            .onFailure { }
        runCatching { setObjectField(model, "deviceSpatialType", miLinkDeviceSpatialType()) }
            .onFailure { }
    }

    private fun notifyProfileAudioEffectListeners(owner: Any?, audioEffectState: Int) {
        runCatching {
            val listener = getObjectField(owner, "audioEffectListener")
            callMethod(listener, "invoke", audioEffectState)
        }.onFailure { }
    }

    private fun notifyHeadsetPropertyChanged(controller: Any?, device: BluetoothDevice, updateType: Int) {
        val listener = runCatching { getObjectField(controller, "headsetPropertyChangeListener") }.getOrNull() ?: return
        runCatching {
            callMethod(listener, "invoke", device, updateType)
        }.onFailure { }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStatus(): BatteryParams? {
        return runCatching { getParcelableExtra("status", BatteryParams::class.java) }.getOrNull()
            ?: runCatching { getParcelableExtra<BatteryParams>("status") }.getOrNull()
    }

    private fun Intent.batteryStatusFromExtras(): BatteryParams? {
        if (!hasExtra("left_connected") && !hasExtra("right_connected") && !hasExtra("case_connected")) return null
        return BatteryParams(
            left = PodParams(
                getIntExtra("left_battery", 0),
                getBooleanExtra("left_charging", false),
                getBooleanExtra("left_connected", false),
                0
            ),
            right = PodParams(
                getIntExtra("right_battery", 0),
                getBooleanExtra("right_charging", false),
                getBooleanExtra("right_connected", false),
                0
            ),
            case = PodParams(
                getIntExtra("case_battery", 0),
                getBooleanExtra("case_charging", false),
                getBooleanExtra("case_connected", false),
                0
            )
        )
    }

    private fun saveState(ctx: Context?) {
        val prefs = (ctx ?: context)?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs.edit()
            .putString("address", currentAddress)
            .putString("name", currentName)
            .putInt("anc", currentAnc)
            .putInt("spatial_audio_mode", currentSpatialAudioMode)
            .putInt("left_battery", currentBattery.left?.battery ?: 0)
            .putBoolean("left_charging", currentBattery.left?.isCharging == true)
            .putBoolean("left_connected", currentBattery.left?.isConnected == true)
            .putInt("right_battery", currentBattery.right?.battery ?: 0)
            .putBoolean("right_charging", currentBattery.right?.isCharging == true)
            .putBoolean("right_connected", currentBattery.right?.isConnected == true)
            .putInt("case_battery", currentBattery.case?.battery ?: 0)
            .putBoolean("case_charging", currentBattery.case?.isCharging == true)
            .putBoolean("case_connected", currentBattery.case?.isConnected == true)
            .apply()
    }

    private fun loadState() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        currentAddress = prefs.getString("address", currentAddress)
        currentName = prefs.getString("name", currentName)
        currentAnc = prefs.getInt("anc", currentAnc)
        currentSpatialAudioMode = prefs.getInt("spatial_audio_mode", currentSpatialAudioMode)
            .coerceIn(ConfigManager.SPATIAL_AUDIO_OFF, ConfigManager.SPATIAL_AUDIO_HEAD_TRACKING)
        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
        currentBattery = BatteryParams(
            left = PodParams(
                prefs.getInt("left_battery", currentBattery.left?.battery ?: 0),
                prefs.getBoolean("left_charging", currentBattery.left?.isCharging == true),
                prefs.getBoolean("left_connected", currentBattery.left?.isConnected == true),
                0
            ),
            right = PodParams(
                prefs.getInt("right_battery", currentBattery.right?.battery ?: 0),
                prefs.getBoolean("right_charging", currentBattery.right?.isCharging == true),
                prefs.getBoolean("right_connected", currentBattery.right?.isConnected == true),
                0
            ),
            case = PodParams(
                prefs.getInt("case_battery", currentBattery.case?.battery ?: 0),
                prefs.getBoolean("case_charging", currentBattery.case?.isCharging == true),
                prefs.getBoolean("case_connected", currentBattery.case?.isConnected == true),
                0
            )
        )
    }
}
