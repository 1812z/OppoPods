package moe.chenxy.oppopods.hook

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Bundle
import android.os.Parcel
import java.lang.reflect.Method
import moe.chenxy.oppopods.BuildConfig
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.pods.RfcommController
import moe.chenxy.oppopods.utils.miuiStrongToast.data.BatteryParams
import moe.chenxy.oppopods.utils.miuiStrongToast.data.OppoPodsAction
import moe.chenxy.oppopods.utils.miuiStrongToast.data.PodParams
import org.json.JSONObject

@SuppressLint("MissingPermission")
class BluetoothUpstreamHeadsetHook : HookContext() {
    private val TAG = "OppoPods-Upstream"
    private val DESCRIPTOR = "com.android.bluetooth.ble.app.IMiuiHeadsetService"
    private companion object {
        const val HFP_STATE_CONNECTED = 2
        const val TOAST_RETRY_DELAY_MS = 300L
        const val TOAST_MAX_RETRIES = 10
    }
    private val knownOppoAddresses = linkedSetOf<String>()
    private val callbacks = linkedMapOf<IBinder, Any>()
    private val handler = Handler(Looper.getMainLooper())
    private val hookedBinderClasses = linkedSetOf<String>()
    private var lastOppoDevice: BluetoothDevice? = null
    private var context: Context? = null
    private var receiverRegistered = false
    private var currentBattery: BatteryParams? = null
    private var currentAnc = 1
    private var currentTransparencyVocalEnhancement = false
    private var hasTransparencyVocalEnhancementState = false
    private var currentAddress: String? = null
    private var currentName: String? = null

    override fun onHook() {
        hookHeadsetServiceBinder()
        hookNotificationBatteryUpstream()
    }

    private fun hookNotificationBatteryUpstream() {
        val notificationApiClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotificationApi")
        if (notificationApiClass != null) {
            runCatching {
                hookBefore(
                    notificationApiClass.method(
                        "showNewConnectedToast",
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        Int::class.java,
                        BluetoothDevice::class.java,
                        String::class.java
                    )
                ) {
                    val device = args[4] as? BluetoothDevice
                    if (!isOppoPod(device)) return@hookBefore
                    val battery = effectiveBattery() ?: return@hookBefore
                    val leftBattery = displayBattery(battery.left) ?: (args[1] as? Int ?: 0)
                    val rightBattery = displayBattery(battery.right) ?: (args[2] as? Int ?: 0)
                    val wearState = displayWearState(battery, args[3] as? Int ?: 1)
                    val notification = currentMiuiBluetoothNotification() ?: return@hookBefore
                    result = null
                    callMethod(
                        notification,
                        "showConnectedToast",
                        args[0] as? Int ?: 2,
                        leftBattery,
                        rightBattery,
                        wearState,
                        device,
                        args[5] as? String
                    )
                    Log.d(TAG, "showNewConnectedToast patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState oldLeft=${args[1]} oldRight=${args[2]} oldWear=${args[3]}")
                }
                Log.d(TAG, "MiuiBluetoothNotificationApi.showNewConnectedToast hook installed")
            }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotificationApi.showNewConnectedToast skipped", it) }
        }

        val notificationClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiBluetoothNotification")
        val requestClass = findClassOrNull("com.android.bluetooth.ble.app.S2")
            ?: findClassOrNull("com.android.bluetooth.ble.app.C4705R2")

        hookCloudParserNotifyFlags()

        if (notificationClass != null) {
            hookHfpChangedForLea(notificationClass)
            hookInvokeStatusBar(notificationClass)
        }
        if (notificationClass != null && requestClass != null) {
            hookUpdateParameters(notificationClass, requestClass)
        }
    }

    private fun hookHeadsetServiceBinder() {
        val serviceClassName = "com.android.bluetooth.ble.app.headset.BluetoothHeadsetService"
        val serviceClass = findClassOrNull(serviceClassName)
        if (serviceClass != null) {
            runCatching {
                hookAfter(serviceClass.method("onBind", Intent::class.java)) {
                    registerStatusReceiver(instance as? Context)
                    val binder = result ?: return@hookAfter
                    installHeadsetBinderHooks(binder.javaClass)
                }
                Log.d(TAG, "BluetoothHeadsetService.onBind hook installed package=$packageName")
            }.onFailure { Log.w(TAG, "hook BluetoothHeadsetService.onBind failed package=$packageName", it) }
            runCatching {
                hookAfter(serviceClass.method("onCreate")) {
                    registerStatusReceiver(instance as? Context)
                }
                Log.d(TAG, "BluetoothHeadsetService.onCreate hook installed package=$packageName")
            }.onFailure { Log.d(TAG, "hook BluetoothHeadsetService.onCreate skipped package=$packageName: ${it.message}") }
        } else {
            Log.d(TAG, "BluetoothHeadsetService class not present package=$packageName")
        }

        listOf(
            "com.android.bluetooth.ble.app.headset.BinderC6776v",
            "com.android.bluetooth.ble.app.headset.v"
        ).forEach { className ->
            findClassOrNull(className)?.let { installHeadsetBinderHooks(it) }
        }
    }

    private fun findClassOrNull(className: String): Class<*>? {
        return runCatching { findClass(className) }.getOrNull()
    }

    // 绕过云端配置检查，实测 OPPO 耳机连接通知会被拦截
    private fun hookCloudParserNotifyFlags() {
        val cloudParserClass = findClassOrNull("com.android.bluetooth.ble.app.MiuiCloudParser")
            ?: findClassOrNull("com.android.bluetooth.ble.app.Y2")
        if (cloudParserClass == null) return
        runCatching {
            val method = cloudParserClass.declaredMethods.firstOrNull {
                it.name == "d" && it.parameterTypes.size == 2 && it.returnType == Boolean::class.javaPrimitiveType
            }
            if (method != null) {
                hookBefore(method) { result = true }
            }
        }.onFailure { Log.d(TAG, "hook MiuiCloudParser.getNotifyFlags skipped", it) }
    }

    // OPPO 耳机连接时主动触发超级岛弹窗
    private fun hookHfpChangedForLea(notificationClass: Class<*>) {
        runCatching {
            val method = notificationClass.declaredMethods.firstOrNull {
                it.name == "handleHfpChangedForLea" && it.parameterTypes.size == 2
            }
            if (method != null) {
                hookAfter(method) {
                    val device = args[0] as? BluetoothDevice ?: return@hookAfter
                    val state = args[1] as? Int ?: return@hookAfter
                    if (!isOppoPod(device) || state != HFP_STATE_CONNECTED) return@hookAfter
                    showConnectedToastWithRetry(instance, device)
                }
            }
        }.onFailure { Log.d(TAG, "hook handleHfpChangedForLea skipped", it) }
    }

    private fun hookInvokeStatusBar(notificationClass: Class<*>) {
        runCatching {
            hookBefore(notificationClass.method("invokeStatusBar", Context::class.java, String::class.java, Bundle::class.java)) {
                val bundle = args[2] as? Bundle
                if (shouldInterceptHeadsetWearIsland(bundle)) {
                    when (ConfigManager.islandMode()) {
                        ConfigManager.ISLAND_MODE_NONE, ConfigManager.ISLAND_MODE_MODULE -> {
                            result = null
                            Log.d(TAG, "invokeStatusBar swallowed headset_wear_notification island mode=${ConfigManager.islandMode()}")
                            return@hookBefore
                        }
                    }
                }
                patchHeadsetWearIslandBundle(bundle)
                Log.d(TAG, "invokeStatusBar upstream action=${args[1]} bundle=$bundle focus=${bundle?.getString("miui.focus.param")}")
            }
            Log.d(TAG, "MiuiBluetoothNotification.invokeStatusBar debug hook installed")
        }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.invokeStatusBar skipped", it) }
    }

    private fun hookUpdateParameters(notificationClass: Class<*>, requestClass: Class<*>) {
        runCatching {
            hookAfter(notificationClass.method("updateParameters", requestClass)) {
                val request = args[0] ?: return@hookAfter
                // 通过字段类型获取 BluetoothDevice 和 int 字段
                val requestFields = requestClass.declaredFields
                val deviceField = requestFields.firstOrNull { it.type == BluetoothDevice::class.java }
                val intFields = requestFields.filter { it.type == Int::class.javaPrimitiveType }
                val device = deviceField?.let { getObjectField(request, it.name) } as? BluetoothDevice
                if (!isOppoPod(device)) return@hookAfter
                val battery = effectiveBattery() ?: return@hookAfter
                val leftBattery = displayBattery(battery.left)
                val rightBattery = displayBattery(battery.right)
                // int 字段顺序：requestFlag, leftBattery, rightBattery, wearState
                val wearStateField = intFields.getOrNull(3)
                val leftBatteryField = intFields.getOrNull(1)
                val rightBatteryField = intFields.getOrNull(2)
                val wearState = displayWearState(battery, wearStateField?.let { getObjectField(request, it.name) } as? Int ?: 1)
                leftBatteryField?.let { setObjectField(request, it.name, leftBattery) }
                rightBatteryField?.let { setObjectField(request, it.name, rightBattery) }
                wearStateField?.let { setObjectField(request, it.name, wearState) }
                Log.d(TAG, "updateParameters patched device=${device.describe()} left=$leftBattery right=$rightBattery wear=$wearState")
            }
            Log.d(TAG, "MiuiBluetoothNotification.updateParameters hook installed")
        }.onFailure { Log.w(TAG, "hook MiuiBluetoothNotification.updateParameters skipped", it) }
    }

    private fun registerStatusReceiver(ctx: Context?) {
        if (ctx == null || receiverRegistered) return
        context = ctx.applicationContext ?: ctx
        val filter = IntentFilter().apply {
            addAction(OppoPodsAction.ACTION_PODS_CONNECTED)
            addAction(OppoPodsAction.ACTION_PODS_DISCONNECTED)
            addAction(OppoPodsAction.ACTION_PODS_BATTERY_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_ANC_CHANGED)
            addAction(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED)
            addAction(OppoPodsAction.ACTION_CONFIG_CHANGED)
        }
        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    OppoPodsAction.ACTION_CONFIG_CHANGED -> {
                        refreshConfig()
                        notifyRealStatus("config-changed")
                    }
                    OppoPodsAction.ACTION_PODS_CONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentName = intent.getStringExtra("device_name") ?: currentName
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                    }
                    OppoPodsAction.ACTION_PODS_DISCONNECTED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                    }
                    OppoPodsAction.ACTION_PODS_BATTERY_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentBattery = intent.batteryStatusFromExtras() ?: intent.parcelableStatus() ?: currentBattery
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                    }
                    OppoPodsAction.ACTION_PODS_ANC_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentAnc = intent.getIntExtra("status", currentAnc)
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                    }
                    OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED -> {
                        currentAddress = intent.getStringExtra("address") ?: currentAddress
                        currentTransparencyVocalEnhancement = intent.getBooleanExtra("enabled", currentTransparencyVocalEnhancement)
                        hasTransparencyVocalEnhancementState = true
                        currentAddress?.let { knownOppoAddresses.add(it.uppercase()) }
                    }
                }
                Log.d(TAG, "state action=${intent?.action} address=$currentAddress name=$currentName anc=$currentAnc battery=${currentBattery.debugString()}")
                notifyRealStatus("broadcast:${intent?.action}")
            }
        }, filter, Context.RECEIVER_EXPORTED)
        receiverRegistered = true
        context?.sendBroadcast(Intent(OppoPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "registered status receiver context=$context")
    }

    private fun installHeadsetBinderHooks(binderClass: Class<*>) {
        val className = binderClass.name
        if (!hookedBinderClasses.add(className)) return
        Log.d(TAG, "BluetoothHeadsetService binder class=$className")

        runCatching {
            hookBefore(binderClass.method("checkSupport", BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = fakeSupport()
                Log.d(TAG, "BinderC6776v.checkSupport forced device=${device.describe()} support=$result")
            }
            Log.d(TAG, "BinderC6776v.checkSupport hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.checkSupport skipped", it) }

        hookAddressStringResult(binderClass, listOf("getDeviceInfo"), "getDeviceInfo") { fakeSupport() }
        hookAddressStringResult(binderClass, listOf("isSupportAudioSwitch", "mo19775z1", "z1"), "isSupportAudioSwitch") { "1" }
        hookAddressBooleanResult(binderClass, listOf("isMiTWS", "mo19771O0", "O0"), "isMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("checkIsMiTWS", "mo19766B", "B"), "checkIsMiTWS", true)
        hookAddressBooleanResult(binderClass, listOf("getRingFindState", "mo19772m0", "m0"), "getRingFindState", false)

        runCatching {
            hookBefore(binderClass.method("setCommonCommand", Int::class.java, String::class.java, BluetoothDevice::class.java)) {
                val command = args[0] as? Int
                val value = args[1] as? String
                val device = args[2] as? BluetoothDevice
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = when (command) {
                    102 -> "1"
                    123 -> "4"
                    else -> "1"
                }
                Log.d(TAG, "BinderC6776v.setCommonCommand forced command=$command value=$value device=${device.describe()} result=$result")
                sendRealStatus(device, "setCommonCommand:$command")
            }
            Log.d(TAG, "BinderC6776v.setCommonCommand hook installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.setCommonCommand skipped", it) }

        hookBinderVoidDevice(binderClass, "connect") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDevice(binderClass, "getDeviceConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderVoidDeviceString(binderClass, "getCommonConfig") { device, method -> sendRealStatus(device, method) }
        hookBinderAncMode(binderClass)
        hookBinderAncLevel(binderClass)

        runCatching {
            val callbackClass = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback")
            hookBefore(binderClass.method("register", callbackClass)) {
                val callback = args[0]
                if (callback != null && lastOppoDevice != null) {
                    rememberCallback(callback)
                    result = null
                    Log.d(TAG, "BinderC6776v.register swallowed callback=$callback device=${lastOppoDevice.describe()}")
                    requestBluetoothStatus("register")
                    sendRealStatus(lastOppoDevice, "register")
                    sendRealStatusDelayed(lastOppoDevice, "register-refresh", 350L)
                }
            }
            hookBefore(binderClass.method("registerCallbackDevice", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isOppoPod(device) || callback == null) return@hookBefore
                lastOppoDevice = device
                rememberCallback(callback)
                result = null
                Log.d(TAG, "BinderC6776v.registerCallbackDevice swallowed callback=$callback device=${device.describe()}")
                requestBluetoothStatus("registerCallbackDevice")
                sendRealStatus(device, "registerCallbackDevice")
                sendRealStatusDelayed(device, "registerCallbackDevice-refresh", 350L)
            }
            hookBefore(binderClass.method("unregister", callbackClass, BluetoothDevice::class.java)) {
                val callback = args[0]
                val device = args[1] as? BluetoothDevice
                if (!isOppoPod(device) || callback == null) return@hookBefore
                forgetCallback(callback)
                result = null
                Log.d(TAG, "BinderC6776v.unregister swallowed callback=$callback device=${device.describe()}")
            }
            Log.d(TAG, "BinderC6776v callback hooks installed")
        }.onFailure { Log.w(TAG, "hook BinderC6776v callback methods skipped", it) }
    }

    private fun hookBinderVoidDevice(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java)) {
                val device = args[0] as? BluetoothDevice
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed device=${device.describe()}")
                requestBluetoothStatus(methodName)
                after(device, methodName)
                sendRealStatusDelayed(device, "$methodName-refresh", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookAddressStringResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: () -> String) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isOppoAddress(address)) return@hookBefore
                result = forced()
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$result method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookAddressBooleanResult(binderClass: Class<*>, methodNames: List<String>, label: String, forced: Boolean) {
        val methodName = methodNames.firstOrNull { name ->
            runCatching { binderClass.method(name, String::class.java) }.isSuccess
        } ?: run {
            Log.w(TAG, "hook BinderC6776v.$label skipped: no method in $methodNames")
            return
        }
        runCatching {
            hookBefore(binderClass.method(methodName, String::class.java)) {
                val address = args[0] as? String
                if (address == null || !isOppoAddress(address)) return@hookBefore
                result = forced
                Log.d(TAG, "BinderC6776v.$label forced address=$address result=$forced method=$methodName")
            }
            Log.d(TAG, "BinderC6776v.$label hook installed method=$methodName")
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$label skipped", it) }
    }

    private fun hookBinderVoidDeviceString(binderClass: Class<*>, methodName: String, after: (BluetoothDevice?, String) -> Unit) {
        runCatching {
            hookBefore(binderClass.method(methodName, BluetoothDevice::class.java, String::class.java)) {
                val device = args[0] as? BluetoothDevice
                val value = args[1] as? String
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.$methodName swallowed value=$value device=${device.describe()}")
                requestBluetoothStatus("$methodName:$value")
                after(device, "$methodName:$value")
                sendRealStatusDelayed(device, "$methodName-refresh:$value", 350L)
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.$methodName skipped", it) }
    }

    private fun hookBinderAncMode(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncMode", Int::class.java, BluetoothDevice::class.java)) {
                val mode = args[0] as? Int
                val device = args[1] as? BluetoothDevice
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.changeAncMode swallowed mode=$mode device=${device.describe()}")
                mode?.let { sendOppoAnc(oppoAncFromMiuiMode(it)) }
                sendRealStatus(device, "changeAncMode:$mode")
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncMode skipped", it) }
    }

    private fun hookBinderAncLevel(binderClass: Class<*>) {
        runCatching {
            hookBefore(binderClass.method("changeAncLevel", String::class.java, BluetoothDevice::class.java)) {
                val level = args[0] as? String
                val device = args[1] as? BluetoothDevice
                if (!isOppoPod(device)) return@hookBefore
                lastOppoDevice = device
                result = null
                Log.d(TAG, "BinderC6776v.changeAncLevel swallowed level=$level device=${device.describe()}")
                level?.let { sendOppoAncLevel(it) }
                sendRealStatus(device, "changeAncLevel:$level")
            }
        }.onFailure { Log.w(TAG, "hook BinderC6776v.changeAncLevel skipped", it) }
    }

    private fun rememberCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks[it] = callback }
    }

    private fun forgetCallback(callback: Any) {
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks.remove(it) }
    }

    private fun firstExistingClass(vararg classNames: String): String? {
        return classNames.firstOrNull { className ->
            runCatching { findClass(className) }.isSuccess
        }
    }

    private fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method {
        return getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }

    private fun hookMiuiHeadsetBinder() {
        val stubClass = firstExistingClass("com.android.bluetooth.ble.app.IMiuiHeadsetService\$Stub") ?: run {
            Log.d(TAG, "IMiuiHeadsetService.Stub fallback not found")
            return
        }
        runCatching {
            hookBefore(findMethod(stubClass, "onTransact", Int::class.java, Parcel::class.java, Parcel::class.java, Int::class.java)) {
                val code = args[0] as? Int ?: return@hookBefore
                val data = args[1] as? Parcel ?: return@hookBefore
                val reply = args[2] as? Parcel ?: return@hookBefore
                handleTransaction(code, data, reply)?.let { handled ->
                    result = handled
                }
            }
            Log.d(TAG, "IMiuiHeadsetService.Stub.onTransact hooked class=$stubClass")
        }.onFailure { Log.w(TAG, "hook IMiuiHeadsetService.Stub.onTransact skipped", it) }
    }

    private fun handleTransaction(code: Int, data: Parcel, reply: Parcel): Boolean? {
        val originalPosition = data.dataPosition()
        return runCatching {
            data.enforceInterface(DESCRIPTOR)
            when (code) {
                1 -> handleCheckSupport(data, reply)
                2 -> handleRegister(data, reply)
                3 -> handleUnregister(data)
                4 -> handleDeviceVoid("connect", data, reply)
                9 -> handleAncMode(data, reply)
                10 -> handleAncLevel(data, reply)
                11 -> handleAddressString("getDeviceInfo", data, reply, fakeSupport())
                12 -> handleDeviceVoid("getDeviceConfig", data, reply)
                14 -> handleSetCommonCommand(data, reply)
                15 -> handleCommonConfig(data, reply)
                16 -> handleRegisterCallbackDevice(data, reply)
                18 -> handleAddressBoolean("isMiTWS", data, reply, true)
                19 -> handleAddressBoolean("checkIsMiTWS", data, reply, true)
                20 -> handleAddressString("isSupportAudioSwitch", data, reply, "1")
                24 -> handleAddressBoolean("getRingFindState", data, reply, false)
                else -> null
            }
        }.onFailure {
            Log.w(TAG, "onTransact inspect failed code=$code", it)
        }.also {
            data.setDataPosition(originalPosition)
        }.getOrNull()
    }

    private fun handleCheckSupport(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "checkSupport upstream device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        reply.writeNoException()
        val support = fakeSupport()
        reply.writeString(support)
        Log.d(TAG, "checkSupport upstream forced $support")
        return true
    }

    private fun handleRegister(data: Parcel, reply: Parcel): Boolean? {
        val callback = data.readCallbackBinder()
        Log.d(TAG, "register upstream callback=$callback lastDevice=${lastOppoDevice.describe()}")
        if (callback == null || lastOppoDevice == null) return null
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks[it] = callback }
        reply.writeNoException()
        sendRealStatus(lastOppoDevice, "register")
        return true
    }

    private fun handleUnregister(data: Parcel): Boolean? {
        val binder = data.readStrongBinder() ?: return null
        callbacks.remove(binder)
        Log.d(TAG, "unregister upstream callback removed=$binder")
        return null
    }

    private fun handleDeviceVoid(method: String, data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "$method upstream device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        reply.writeNoException()
        Log.d(TAG, "$method upstream no-op for Oppo")
        sendRealStatus(device, method)
        return true
    }

    private fun handleAncMode(data: Parcel, reply: Parcel): Boolean? {
        val mode = data.readInt()
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "changeAncMode upstream mode=$mode device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        sendOppoAnc(oppoAncFromMiuiMode(mode))
        reply.writeNoException()
        sendRealStatus(device, "changeAncMode:$mode")
        return true
    }

    private fun handleAncLevel(data: Parcel, reply: Parcel): Boolean? {
        val level = data.readString()
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "changeAncLevel upstream level=$level device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        level?.let { sendOppoAncLevel(it) }
        reply.writeNoException()
        sendRealStatus(device, "changeAncLevel:$level")
        return true
    }

    private fun handleAddressString(method: String, data: Parcel, reply: Parcel, forced: String): Boolean? {
        val address = data.readString()
        val isOppo = address != null && isOppoAddress(address)
        Log.d(TAG, "$method upstream address=$address isOppo=$isOppo")
        if (!isOppo) return null
        reply.writeNoException()
        reply.writeString(forced)
        Log.d(TAG, "$method upstream forced $forced")
        return true
    }

    private fun handleAddressBoolean(method: String, data: Parcel, reply: Parcel, forced: Boolean): Boolean? {
        val address = data.readString()
        val isOppo = address != null && isOppoAddress(address)
        Log.d(TAG, "$method upstream address=$address isOppo=$isOppo")
        if (!isOppo) return null
        reply.writeNoException()
        reply.writeInt(if (forced) 1 else 0)
        Log.d(TAG, "$method upstream forced $forced")
        return true
    }

    private fun handleSetCommonCommand(data: Parcel, reply: Parcel): Boolean? {
        val command = data.readInt()
        val value = data.readString()
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "setCommonCommand upstream command=$command value=$value device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        reply.writeNoException()
        reply.writeString(
            when (command) {
                102 -> "1"
                123 -> "4"
                else -> "1"
            }
        )
        sendRealStatus(device, "setCommonCommand:$command")
        return true
    }

    private fun handleCommonConfig(data: Parcel, reply: Parcel): Boolean? {
        val device = data.readDevice()
        val type = data.readString()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "getCommonConfig upstream type=$type device=${device.describe()} isOppo=$isOppo")
        if (!isOppo) return null
        lastOppoDevice = device
        reply.writeNoException()
        sendRealStatus(device, "getCommonConfig:$type")
        return true
    }

    private fun handleRegisterCallbackDevice(data: Parcel, reply: Parcel): Boolean? {
        val callback = data.readCallbackBinder()
        val device = data.readDevice()
        val isOppo = isOppoPod(device)
        Log.d(TAG, "registerCallbackDevice upstream callback=$callback device=${device.describe()} isOppo=$isOppo")
        if (!isOppo || callback == null) return null
        lastOppoDevice = device
        (callMethod(callback, "asBinder") as? IBinder)?.let { callbacks[it] = callback }
        reply.writeNoException()
        sendRealStatus(device, "registerCallbackDevice")
        return true
    }

    private fun Parcel.readCallbackBinder(): Any? {
        val binder = readStrongBinder() ?: return null
        return runCatching {
            val stub = findClass("com.android.bluetooth.ble.app.IMiuiHeadsetCallback\$Stub")
            stub.getDeclaredMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.onFailure {
            Log.w(TAG, "read callback binder failed", it)
        }.getOrNull()
    }

    private fun Parcel.readDevice(): BluetoothDevice? {
        return if (readInt() != 0) BluetoothDevice.CREATOR.createFromParcel(this) else null
    }

    // 等电池数据就绪后弹超级岛，带重试
    private fun showConnectedToastWithRetry(notification: Any?, device: BluetoothDevice, retriesLeft: Int = TOAST_MAX_RETRIES) {
        val battery = effectiveBattery()
        if (battery == null && retriesLeft > 0) {
            handler.postDelayed({ showConnectedToastWithRetry(notification, device, retriesLeft - 1) }, TOAST_RETRY_DELAY_MS)
            return
        }
        val leftBattery = battery?.let { displayBattery(it.left) } ?: 0
        val rightBattery = battery?.let { displayBattery(it.right) } ?: 0
        val wearState = battery?.let { w -> displayWearState(w, 1) } ?: 1
        runCatching {
            callMethod(notification, "showConnectedToast", 2, leftBattery, rightBattery, wearState, device, device.name)
        }
    }

    private fun isOppoPod(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name ?: device.alias }.getOrNull().orEmpty()
        val result = name.contains("oppo", ignoreCase = true) || (address != null && isOppoAddress(address))
        if (result && address != null) knownOppoAddresses.add(address.uppercase())
        return result
    }

    private fun notifyRealStatus(reason: String) {
        val device = lastOppoDevice
        if (device != null) {
            sendRealStatus(device, reason)
            return
        }
        val address = currentAddress ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatus(device: BluetoothDevice?, reason: String) {
        val address = device?.address ?: return
        sendRealStatus(address, reason)
    }

    private fun sendRealStatusDelayed(device: BluetoothDevice?, reason: String, delayMs: Long) {
        val address = device?.address ?: return
        handler.postDelayed({ sendRealStatus(address, reason) }, delayMs)
    }

    private fun sendRealStatus(address: String, reason: String) {
        if (callbacks.isEmpty()) {
            Log.d(TAG, "send real status skipped: no callback reason=$reason address=$address")
            return
        }
        val payload = realRefreshPayload()
        handler.post {
            callbacks.values.toList().forEach { callback ->
                runCatching {
                    callMethod(callback, "refreshStatus", address, payload)
                    Log.d(TAG, "sent real refreshStatus reason=$reason address=$address payload=$payload callback=$callback")
                }.onFailure {
                    forgetCallback(callback)
                    Log.w(TAG, "send real refreshStatus failed reason=$reason callback=$callback", it)
                }
            }
        }
    }

    private fun realRefreshPayload(): String {
        val localSnapshot = runCatching { RfcommController.currentStatusSnapshot() }
            .getOrNull()
            ?.takeIf { it.address != null || it.battery != null }
        val battery = localSnapshot?.battery ?: currentBattery
        val anc = currentAnc
        if (!hasTransparencyVocalEnhancementState && localSnapshot != null) {
            currentTransparencyVocalEnhancement = localSnapshot.transparencyVocalEnhancement
            hasTransparencyVocalEnhancementState = true
        }
        val transparencyVocalEnhancement = if (hasTransparencyVocalEnhancementState) {
            currentTransparencyVocalEnhancement
        } else {
            localSnapshot?.transparencyVocalEnhancement ?: currentTransparencyVocalEnhancement
        }
        localSnapshot?.address?.let {
            currentAddress = it
            knownOppoAddresses.add(it.uppercase())
        }
        localSnapshot?.deviceName?.let { currentName = it }
        return RfcommController.miuiRefreshPayload(battery, anc, transparencyVocalEnhancement)
    }

    private fun effectiveBattery(): BatteryParams? {
        return runCatching { RfcommController.currentStatusSnapshot().battery }.getOrNull() ?: currentBattery
    }

    private fun displayBattery(params: PodParams?): Int? {
        if (params?.isConnected != true) return null
        return params.battery.coerceIn(0, 100)
    }

    private fun displayWearState(battery: BatteryParams, fallback: Int): Int {
        val leftConnected = battery.left?.isConnected == true
        val rightConnected = battery.right?.isConnected == true
        return when {
            leftConnected && rightConnected -> 1
            leftConnected -> 3
            rightConnected -> 2
            fallback != 0 -> fallback
            else -> 1
        }
    }

    private fun currentMiuiBluetoothNotification(): Any? {
        return runCatching {
            findClass("com.android.bluetooth.ble.app.headset.BluetoothHeadsetService")
                .getField("mMiuiBluetoothNotification")
                .apply { isAccessible = true }
                .get(null)
        }.getOrNull()
    }

    private fun patchHeadsetWearIslandBundle(bundle: Bundle?) {
        if (bundle == null) return
        if (!shouldInterceptHeadsetWearIsland(bundle)) return
        if (ConfigManager.islandMode() != ConfigManager.ISLAND_MODE_OFFICIAL) return
        val battery = effectiveBattery() ?: return
        val leftText = displayBattery(battery.left)?.let { "$it%" }
        val rightText = displayBattery(battery.right)?.let { "$it%" }
        if (leftText == null && rightText == null) return
        patchIslandJson(bundle, "param", leftText, rightText)
        patchIslandJson(bundle, "island_param", leftText, rightText)
        Log.d(TAG, "patched headset_wear_notification island text left=$leftText right=$rightText")
    }

    private fun shouldInterceptHeadsetWearIsland(bundle: Bundle?): Boolean {
        return bundle?.getString("notifyId") == "headset_wear_notification"
    }

    private fun patchIslandJson(bundle: Bundle, key: String, leftText: String?, rightText: String?) {
        val raw = bundle.getString(key) ?: return
        runCatching {
            val json = JSONObject(raw)
            leftText?.let { putTextParams(json.optJSONObject("left"), it) }
            rightText?.let { putTextParams(json.optJSONObject("right"), it) }
            bundle.putString(key, json.toString())
        }.onFailure {
            Log.w(TAG, "patch island json failed key=$key raw=$raw", it)
        }
    }

    private fun putTextParams(area: JSONObject?, text: String) {
        if (area == null) return
        area.put(
            "textParams",
            JSONObject().apply {
                put("text", text)
                put("textColor", -1)
                put("turnAnim", true)
            }
        )
    }

    private fun requestBluetoothStatus(reason: String) {
        runCatching {
            if (packageName == "com.android.bluetooth") {
                RfcommController.queryStatus()
            } else {
                context?.sendBroadcast(Intent(OppoPodsAction.ACTION_REFRESH_STATUS).apply {
                    setPackage("com.android.bluetooth")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                })
            }
            Log.d(TAG, "requested bluetooth status reason=$reason package=$packageName")
        }.onFailure {
            Log.w(TAG, "request bluetooth status failed reason=$reason package=$packageName", it)
        }
    }

    private fun oppoAncFromMiuiMode(mode: Int): Int {
        return when (mode) {
            1 -> 2
            2 -> 3
            else -> 1
        }
    }

    private fun oppoAncFromMiuiLevel(level: String): Int {
        // MIUI binder level codes: 0103=Smart, 0101=Light, 0100=Medium, 0102=Deep.
        return when {
            level.startsWith("0103") -> 5
            level.startsWith("0101") -> 6
            level.startsWith("0100") -> 7
            level.startsWith("0102") -> 8
            level.startsWith("01") -> 7
            level.startsWith("02") -> 3
            else -> 1
        }
    }

    private fun sendOppoAncLevel(level: String) {
        when {
            level.startsWith("0201") -> {
                currentAnc = 3
                sendOppoTransparencyVocalEnhancement(true)
            }
            level.startsWith("0200") -> {
                currentAnc = 3
                sendOppoTransparencyVocalEnhancement(false)
            }
            else -> sendOppoAnc(oppoAncFromMiuiLevel(level))
        }
    }

    private fun sendOppoAnc(mode: Int) {
        currentAnc = mode
        val ctx = context ?: run {
            Log.w(TAG, "sendOppoAnc skipped: context is null mode=$mode")
            return
        }
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_ANC_SELECT).apply {
            putExtra("status", mode)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_ANC_CHANGED).apply {
            putExtra("status", mode)
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendOppoAnc broadcast sent mode=$mode")
    }

    private fun sendOppoTransparencyVocalEnhancement(enabled: Boolean) {
        currentTransparencyVocalEnhancement = enabled
        hasTransparencyVocalEnhancementState = true
        val ctx = context ?: run {
            Log.w(TAG, "sendOppoTransparencyVocalEnhancement skipped: context is null enabled=$enabled")
            return
        }
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_TRANSPARENCY_VOCAL_ENHANCEMENT_SET).apply {
            putExtra("enabled", enabled)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_PODS_TRANSPARENCY_VOCAL_ENHANCEMENT_CHANGED).apply {
            putExtra("enabled", enabled)
            setPackage(BuildConfig.APPLICATION_ID)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        ctx.sendBroadcast(Intent(OppoPodsAction.ACTION_REFRESH_STATUS).apply {
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        Log.d(TAG, "sendOppoTransparencyVocalEnhancement broadcast sent enabled=$enabled")
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

    private fun BatteryParams?.debugString(): String {
        if (this == null) return "null"
        return "left=${left?.battery}/${left?.isCharging}/${left?.isConnected} right=${right?.battery}/${right?.isCharging}/${right?.isConnected} case=${case?.battery}/${case?.isCharging}/${case?.isConnected}"
    }

    private fun isOppoAddress(address: String): Boolean {
        return address.uppercase() in knownOppoAddresses
    }

    private fun BluetoothDevice?.describe(): String {
        if (this == null) return "null"
        val address = runCatching { this.address }.getOrNull()
        val name = runCatching { this.name }.getOrNull()
        val alias = runCatching { this.alias }.getOrNull()
        return "BluetoothDevice(address=$address,name=$name,alias=$alias)"
    }
}
