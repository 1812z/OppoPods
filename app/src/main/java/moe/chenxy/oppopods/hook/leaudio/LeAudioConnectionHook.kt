package moe.chenxy.oppopods.hook.leaudio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.os.Binder
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.hook.HookContext
import moe.chenxy.oppopods.hook.HookParam
import moe.chenxy.oppopods.hook.Log
import moe.chenxy.oppopods.hook.getObjectField
import moe.chenxy.oppopods.hook.setObjectField

/**
 * Port of OPPOLeaConnect's event-driven connection/stability path.
 * HFP must remain usable for the vendor handshake; never globally force dual mode.
 * A profile CONNECTED callback does not prove both LE ACLs or both CIS streams are healthy.
 */
@SuppressLint("MissingPermission")
internal class LeAudioConnectionHook : HookContext() {
    private val tag = "OppoPods-LEAudio"
    private val main = Handler(Looper.getMainLooper())
    private val cfg get() = ConfigManager.current().leAudio
    private var adapter: Any? = null
    private val buds = ConcurrentHashMap.newKeySet<String>()
    private var classicAddress: String? = null
    private val userOff = ConcurrentHashMap.newKeySet<String>()
    private val paused = ConcurrentHashMap.newKeySet<String>()
    private val poked = ConcurrentHashMap.newKeySet<String>()
    private val starved = ConcurrentHashMap.newKeySet<String>()
    private val startup = ConcurrentHashMap.newKeySet<String>()
    private val completed = ConcurrentHashMap.newKeySet<String>()
    private val established = ConcurrentHashMap.newKeySet<String>()
    private val discoveryRetried = ConcurrentHashMap.newKeySet<String>()
    private val acl = ConcurrentHashMap.newKeySet<String>()
    private val held = ConcurrentHashMap<String, BluetoothGatt>()
    private val contexts = ConcurrentHashMap<Int, Int>()
    private val knownGroups = ConcurrentHashMap<String, Set<String>>()
    private val moduleCall = ThreadLocal<Boolean>()
    private var gameForced = false

    private val lea get() = service("getLeAudioService")
    private fun service(name: String): Any? = invoke(adapter, name).let {
        if (it is Optional<*>) it.orElse(null) else it
    }

    override fun onHook() {
        Log.i(tag, "Installing connection/stability hooks: $cfg")
        after(AS, "onCreate") { captureAdapter(instance) }
        before(AS, "connectAllEnabledProfiles", BluetoothDevice::class.java) {
            captureAdapter(instance)
            val device = args[0] as? BluetoothDevice ?: return@before
            if (!cfg.enabled || !isTarget(device)) return@before
            if (explicitRequest()) resume(device)
            else if (isPaused(device)) { result = 0; return@before }
            repairClassic(device, "full connection")
        }
        before(AS, "disconnectAllEnabledProfiles", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!) {
            val device = args[0] as? BluetoothDevice ?: return@before
            if (cfg.enabled && isTarget(device) && explicitRequest()) {
                val members = group(device)
                paused.addAll(members.map { it.address })
                main.post { if (isPaused(device)) stopGroup(members) }
                Log.i(tag, "User disconnect: pause group ${members.map { it.address }}")
            }
        }
        // Preserve user policy writes. Internal FORBIDDEN writes are not a user request,
        // even when a Settings Binder identity (uid=1000) survives on that call stack.
        listOf(A2DP to 2, HFP to 1, LEA to 22).forEach { (className, profile) ->
            before(className, "setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType!!) {
                val device = args[0] as? BluetoothDevice ?: return@before
                if (!cfg.enabled || !isTarget(device)) return@before
                val policy = args[1] as Int
                val key = "${device.address}/$profile"
                val fromLePolicy = profile != 22 && Throwable().stackTrace.any {
                    it.className == LEA && it.methodName == "setConnectionPolicy"
                }
                if (explicitRequest() && !fromLePolicy) {
                    if (policy == 0) userOff.add(key) else if (policy == 100) userOff.remove(key)
                    if (policy == 100) resume(device)
                    if (profile == 22 && policy == 0) group(device).forEach { release(it.address) }
                } else if (cfg.fixPolicy && policy == 0 && key !in userOff && !isPaused(device)) {
                    result = true
                    Log.i(tag, "Blocked internal FORBIDDEN: profile=$profile ${device.address}")
                }
            }
        }
        before(A2DP, "okToConnect", BluetoothDevice::class.java, Boolean::class.javaPrimitiveType!!) { gate(this, false) }
        before(HFP, "okToAcceptConnection", BluetoothDevice::class.java, Boolean::class.javaPrimitiveType!!) { gate(this, true) }
        before(LEA, "connect", BluetoothDevice::class.java) {
            val device = args[0] as? BluetoothDevice ?: return@before
            if (!cfg.enabled || !isTarget(device)) return@before
            if (explicitRequest()) resume(device)
            if (isPaused(device)) result = false
        }
        // HyperOS inherits this method from ConnectableProfile; limit the shared hook to LE Audio.
        before(LEA, "okToConnect", BluetoothDevice::class.java) {
            if (instance?.javaClass?.name != LEA) return@before
            val device = args[0] as? BluetoothDevice ?: return@before
            if (cfg.enabled && isPaused(device)) result = false
        }
        before(NI, "connectLeAudio", BluetoothDevice::class.java) {
            val device = args[0] as? BluetoothDevice ?: return@before
            if (cfg.enabled && isPaused(device)) result = false
        }
        before(NI, "setEnableState", BluetoothDevice::class.java, Boolean::class.javaPrimitiveType!!) {
            val device = args[0] as? BluetoothDevice ?: return@before
            if (cfg.enabled && args[1] == true && isPaused(device)) result = false
        }
        after(LEA, "deviceConnected", BluetoothDevice::class.java) {
            val device = args[0] as? BluetoothDevice ?: return@after
            if (!cfg.enabled || !isTarget(device)) return@after
            established.add(device.address)
            group(device)
            endRound(device)
            Log.i(tag, "LE Audio connected: ${device.address}, group=${groupId(device)}")
            if (isPaused(device)) { main.post { stopGroup(group(device)) }; return@after }
            hold(device)
            complete(device)
            applyGame()
            adopt(groupId(device))
            main.postDelayed({ if (cfg.enabled && !isPaused(device)) activate(groupId(device)) }, 1500L)
        }
        after(LEA, "deviceDisconnected", BluetoothDevice::class.java, Boolean::class.javaPrimitiveType!!) {
            val device = args[0] as? BluetoothDevice ?: return@after
            if (isTarget(device)) endRound(device)
        }
        runCatching {
            after(LEA, "messageFromNative", findClass("com.android.bluetooth.le_audio.LeAudioStackEvent")) {
                if (!cfg.enabled) return@after
                val event = args[0] ?: return@after
                val type = field(event, "type") as? Int ?: return@after
                val device = field(event, "device") as? BluetoothDevice
                val id = field(event, if (type == 4) "valueInt2" else "valueInt1") as? Int ?: -1
                if (type == 4 && targetGroup(id)) {
                    val mask = field(event, "valueInt5") as? Int ?: 0
                    if (mask != 0) contexts[id] = mask
                    Log.i(tag, "LE audio configuration: group=$id available=0x${mask.toString(16)}")
                    adopt(id)
                    if (cfg.adoptContexts && mask != 0) activate(id)
                } else if (type == 2 && targetGroup(id) && field(event, "valueInt2") == 1) {
                    activate(id)
                    applyGame()
                } else if (type == 1 && device != null && isTarget(device)) {
                    val state = field(event, "valueInt1") as? Int ?: -1
                    Log.i(tag, "LE native state=$state ${device.address}")
                    if (state == 2) established.add(device.address)
                    if (state == 0) {
                        endRound(device)
                        if (device.address !in acl) release(device.address) else retryDiscovery(device)
                        main.post { complete(device) }
                    }
                }
            }
        }.onFailure { Log.w(tag, "LE stack event hook unavailable", it) }
        before("com.android.bluetooth.btservice.RemoteDevices", "aclStateChangeCallback",
            Int::class.javaPrimitiveType!!, ByteArray::class.java, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!) {
            if (!cfg.enabled || args[0] != 0 || args[3] != 2) return@before
            val device = invoke(instance, "getDevice", args[1]) as? BluetoothDevice ?: return@before
            if (!isTarget(device)) return@before
            if (args[4] == 0) {
                acl.add(device.address)
                Log.i(tag, "LE ACL established: ${device.address}")
                main.post { hold(device) }
            } else {
                acl.remove(device.address)
                release(device.address)
            }
        }
        after("com.android.bluetooth.btservice.PhonePolicy", "autoConnect") { main.post { startupConnect() } }
        before("com.android.bluetooth.btservice.PhonePolicy", "autoConnectLeAudio", BluetoothDevice::class.java) {
            val device = args[0] as? BluetoothDevice ?: return@before
            if (cfg.enabled && isTarget(device)) {
                if (isPaused(device)) result = null else startupConnect()
            }
        }
        runCatching {
            hookAfter(findMethodByParamCount(AS, "onProfileServiceStateChanged", 2)) {
                if (args[1] == 12) main.post { startupConnect() }
            }
            Log.i(tag, "Installed profile startup hook")
        }.onFailure { Log.w(tag, "Profile startup hook unavailable", it) }
    }

    private fun captureAdapter(value: Any?) {
        if (value == null || adapter === value) return
        adapter = value
        val context = value as? Context ?: return
        val saved = context.getSharedPreferences("oppopods_le_audio_buds", Context.MODE_PRIVATE)
        buds.addAll(saved.getStringSet("all", emptySet()).orEmpty())
        classicAddress = saved.getString("classic", null)
        paused.clear()
        startup.clear()
        completed.clear()
        established.clear()
        discoveryRetried.clear()
        contexts.clear()
        knownGroups.clear()
        acl.clear()
        poked.clear()
        starved.clear()
        held.keys.toList().forEach { release(it) }
        Log.i(tag, "Adapter ready, remembered buds=${buds.size}, classic=$classicAddress")
    }

    fun isTarget(device: BluetoothDevice): Boolean {
        val selected = cfg.deviceAddresses
        if (selected.isEmpty()) return false
        if (device.address in selected) return true
        // Only system group/address mappings can expand the whitelist, never old name caches.
        val members = group(device).map { it.address }.toSet()
        if (members.any { it in selected }) return true
        // A selected classic address can map to LE members before CSIP group discovery completes.
        return selected.any { address ->
            val linked = (invoke(adapter, "findLeAudioDevices", address) as? Collection<*>)
                ?.filterIsInstance<BluetoothDevice>().orEmpty()
            linked.any { it.address == device.address }
        }
    }

    fun rememberClassic(device: BluetoothDevice) {
        if (classicAddress == device.address) return
        classicAddress = device.address
        buds.add(device.address)
        saveBuds()
        Log.i(tag, "HFP classic address: ${device.address}")
    }

    private fun saveBuds() {
        (adapter as? Context)?.getSharedPreferences("oppopods_le_audio_buds", Context.MODE_PRIVATE)?.edit()
            ?.putStringSet("all", buds.toSet())?.putString("classic", classicAddress)?.apply()
    }

    fun isPaused(device: BluetoothDevice) = device.address in paused && isTarget(device)
    private fun groupId(device: BluetoothDevice) = invoke(lea, "getGroupId", device) as? Int ?: -1
    private fun group(device: BluetoothDevice): List<BluetoothDevice> {
        val members = (invoke(lea, "getGroupDevices", groupId(device)) as? Collection<*>)
            ?.filterIsInstance<BluetoothDevice>().orEmpty()
        val classic = invoke(adapter, "findBrDevice", device.address) as? BluetoothDevice
        val mappedLe = (invoke(adapter, "findLeAudioDevices", (classic ?: device).address) as? Collection<*>)
            ?.filterIsInstance<BluetoothDevice>().orEmpty()
        val previous = knownGroups[device.address].orEmpty().mapNotNull {
            invoke(adapter, "getRemoteDevice", it) as? BluetoothDevice
        }
        val all = (members + mappedLe + previous + listOfNotNull(device, classic)).distinctBy { it.address }
        val addresses = all.map { it.address }.toSet()
        all.forEach { knownGroups[it.address] = addresses }
        buds.addAll(all.map { it.address })
        if (all.any { it.address in paused }) paused.addAll(all.map { it.address })
        saveBuds()
        return all
    }
    private fun targetGroup(id: Int): Boolean = id >= 0 &&
        (invoke(lea, "getGroupDevices", id) as? Collection<*>)?.filterIsInstance<BluetoothDevice>()?.any { isTarget(it) } == true
    private fun state(device: BluetoothDevice) = invoke(lea, "getConnectionState", device) as? Int ?: 0
    private fun wantsLe(device: BluetoothDevice) = "${device.address}/22" !in userOff &&
        lea != null && invoke(lea, "getConnectionPolicy", device) != 0 && device.bondState == BluetoothDevice.BOND_BONDED

    private fun explicitRequest(): Boolean {
        if (moduleCall.get() == true || Binder.getCallingUid() == 1002) return false
        return Throwable().stackTrace.none {
            it.className.endsWith("PhonePolicy") ||
                (it.className.startsWith("com.android.bluetooth.") && it.className.endsWith("Service") && it.methodName == "connect")
        }
    }

    private fun resume(device: BluetoothDevice) {
        val members = group(device)
        val changed = paused.removeAll(members.map { it.address }.toSet())
        members.forEach { completed.remove(it.address); discoveryRetried.remove(it.address) }
        if (changed) members.filter { wantsLe(it) }.forEach { invoke(field(lea, "mNativeInterface"), "setEnableState", it, true) }
    }

    private fun repairClassic(device: BluetoothDevice, reason: String) {
        if (!cfg.fixPolicy || isPaused(device) || device.bondState != BluetoothDevice.BOND_BONDED) return
        listOf(2, 1).forEach { profile ->
            if ("${device.address}/$profile" !in userOff && invoke(adapter, "getProfileConnectionPolicy", device, profile) == 0) {
                invoke(adapter, "setProfileConnectionPolicy", device, profile, 100)
                Log.i(tag, "Repair classic policy profile=$profile ${device.address} ($reason)")
            }
        }
    }

    private fun gate(param: HookParam, hfp: Boolean) {
        val device = param.args[0] as? BluetoothDevice ?: return
        if (!cfg.enabled || !isTarget(device)) return
        if (isPaused(device)) { param.result = false; return }
        repairClassic(device, if (hfp) "HFP incoming" else "A2DP incoming")
        if (param.args[1] == true || !cfg.leFirst || !wantsLe(device) || (hfp && !cfg.gateHfp)) return
        val status = state(device)
        if (status == 2 || status == 3 || device.address in starved) return
        if (status == 1) {
            if (invoke(service("getA2dpService"), "getConnectionState", device) == 2 ||
                invoke(service("getHeadsetService"), "getConnectionState", device) == 2) starved.add(device.address)
            else param.result = false
        } else if (device.address in poked) {
            starved.add(device.address)
        } else if (cfg.poke) {
            poked.add(device.address)
            param.result = false
            invoke(lea, "connect", device)
            Log.i(tag, "LE priority: one direct attempt ${device.address}; HFP gate=${cfg.gateHfp}")
        }
    }
    private fun endRound(device: BluetoothDevice) { poked.remove(device.address); starved.remove(device.address) }

    private fun startupConnect() {
        if (!cfg.enabled || !cfg.wakeClassic || invoke(adapter, "profileServicesRunning") != true ||
            invoke(adapter, "isQuietModeEnabled") == true) return
        val bonded = (invoke(adapter, "getBondedDevices") as? Collection<*>)?.filterIsInstance<BluetoothDevice>().orEmpty()
        val device = bonded.firstOrNull { it.address == classicAddress && isTarget(it) } ?: bonded.firstOrNull {
            it.type != BluetoothDevice.DEVICE_TYPE_LE && isTarget(it)
        } ?: return
        if (isPaused(device) || state(device) == 2 ||
            invoke(service("getHeadsetService"), "getConnectionState", device) == 2 ||
            invoke(service("getA2dpService"), "getConnectionState", device) == 2 || !startup.add(device.address)) return
        repairClassic(device, "Bluetooth startup")
        internalCall { invoke(adapter, "connectAllEnabledProfiles", device) }
        Log.i(tag, "Startup classic connection: ${device.address}")
    }

    private fun complete(device: BluetoothDevice) {
        if (!cfg.enabled || !cfg.completeGroup || !isTarget(device) || isPaused(device)) return
        val members = group(device)
        if (members.none { state(it) == 2 }) return
        members.filter { !isPaused(it) && wantsLe(it) && state(it) != 2 && state(it) != 3 }.forEach {
            if (completed.add(it.address)) {
                val result = internalCall {
                    if (state(it) == 1) invoke(field(lea, "mNativeInterface"), "connectLeAudio", it)
                    else invoke(lea, "connect", it)
                }
                Log.i(tag, "Complete missing LE group member: ${it.address} result=$result")
            }
        }
    }

    private fun hold(device: BluetoothDevice) {
        if (!cfg.enabled || !cfg.holdGatt || !isTarget(device) || isPaused(device) || !wantsLe(device) ||
            (device.address !in acl && state(device) != 2)) return
        val context = adapter as? Context ?: return
        synchronized(held) {
            if (held.containsKey(device.address)) return
            runCatching {
                val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == 0) {
                            held.remove(gatt.device.address, gatt)
                            gatt.close()
                            Log.i(tag, "GATT holder disconnected: ${gatt.device.address} status=$status")
                        }
                    }
                }, BluetoothDevice.TRANSPORT_LE) ?: return
                held[device.address] = gatt
                Log.i(tag, "Direct GATT holder attached: ${device.address}")
            }.onFailure { Log.w(tag, "GATT holder failed ${device.address}", it) }
        }
    }
    private fun release(address: String) {
        val gatt = held.remove(address) ?: return
        runCatching { gatt.disconnect() }.onFailure { Log.w(tag, "GATT disconnect failed", it) }
        runCatching { gatt.close() }.onFailure { Log.w(tag, "GATT close failed", it) }
        Log.i(tag, "GATT holder released: $address")
    }
    private fun retryDiscovery(device: BluetoothDevice) {
        if (!cfg.enabled || !cfg.completeGroup || !isTarget(device) || isPaused(device) || device.address in established || !held.containsKey(device.address) ||
            !discoveryRetried.add(device.address)) return
        main.post {
            if (cfg.enabled && cfg.completeGroup && isTarget(device) && !isPaused(device) && device.address in acl && state(device) != 2 &&
                invoke(held[device.address], "refresh") == true) {
                internalCall { invoke(lea, "connect", device) }
                Log.i(tag, "Retried service discovery once: ${device.address}")
            }
        }
    }

    private fun adopt(id: Int) {
        if (!cfg.adoptContexts || !targetGroup(id)) return
        val descriptor = invoke(lea, "getGroupDescriptor", id) ?: return
        if ((field(descriptor, "mAvailableContexts") as? Int ?: 0) != 0) return
        val mask = contexts[id] ?: 0x4F
        runCatching { setObjectField(descriptor, "mAvailableContexts", mask) }
            .onFailure { Log.w(tag, "Context repair failed group=$id", it); return }
        invoke(lea, "setGroupAllowedContextMask", id, mask, 0xFFF)
        Log.i(tag, "Repaired empty available contexts: group=$id mask=0x${mask.toString(16)}")
    }
    private fun activate(id: Int) {
        if (!cfg.enabled || !targetGroup(id) || invoke(lea, "allLeAudioDevicesConnected") != true) return
        val lead = invoke(lea, "getConnectedGroupLeadDevice", id) as? BluetoothDevice ?: return
        if (!isPaused(lead)) {
            val result = invoke(lea, "setActiveDevice", lead)
            Log.i(tag, "Activate complete LE group=$id lead=${lead.address} result=$result")
        }
    }
    private fun applyGame() {
        if (cfg.enabled && cfg.gameContext && cfg.deviceAddresses.isNotEmpty()) {
            invoke(field(lea, "mNativeInterface"), "setInGame", true)
            gameForced = true
        } else if (gameForced) {
            invoke(field(lea, "mNativeInterface"), "setInGame", false)
            gameForced = false
        }
    }
    fun configChanged() {
        main.post {
            if (!cfg.enabled || !cfg.holdGatt) held.keys.toList().forEach { release(it) }
            held.keys.toList().forEach { address ->
                val device = invoke(adapter, "getRemoteDevice", address) as? BluetoothDevice
                if (device == null || !isTarget(device)) release(address)
            }
            if (!cfg.enabled || !cfg.leFirst) { poked.clear(); starved.clear() }
            if (!cfg.enabled) {
                paused.toList().forEach { address ->
                    val device = invoke(adapter, "getRemoteDevice", address) as? BluetoothDevice
                    if (device != null && wantsLe(device)) invoke(field(lea, "mNativeInterface"), "setEnableState", device, true)
                }
                paused.clear()
            }
            applyGame()
            if (cfg.enabled && cfg.holdGatt) acl.forEach { address ->
                (invoke(adapter, "getRemoteDevice", address) as? BluetoothDevice)?.takeIf { isTarget(it) }?.let { hold(it) }
            }
        }
    }
    private fun stopGroup(members: List<BluetoothDevice>) = internalCall {
        members.filter { cfg.enabled && isTarget(it) }.forEach {
            val native = field(lea, "mNativeInterface")
            invoke(native, "setEnableState", it, false)
            invoke(native, "disconnectLeAudio", it)
            invoke(lea, "disconnect", it)
            release(it.address)
            endRound(it)
            invoke(adapter, "disconnectAllEnabledProfiles", it, 0)
        }
    }
    private fun <T> internalCall(block: () -> T): T {
        val previous = moduleCall.get()
        moduleCall.set(true)
        return try { block() } finally { moduleCall.set(previous) }
    }

    private fun field(value: Any?, name: String) = runCatching { getObjectField(value, name) }.getOrNull()
    private fun resolve(type: Class<*>, name: String, vararg parameters: Class<*>): Method {
        var current: Class<*>? = type
        while (current != null) {
            val candidate = current
            runCatching { candidate.getDeclaredMethod(name, *parameters) }.getOrNull()?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        throw NoSuchMethodException("${type.name}.$name")
    }
    private fun invoke(value: Any?, name: String, vararg args: Any?): Any? {
        if (value == null) return null
        return runCatching {
            var type: Class<*>? = value.javaClass
            var method: Method? = null
            while (type != null && method == null) {
                method = type.declaredMethods.firstOrNull {
                    it.name == name && it.parameterTypes.size == args.size && it.parameterTypes.indices.all { index ->
                        val expected = it.parameterTypes[index]
                        val arg = args[index]
                        if (arg == null) !expected.isPrimitive else expected.isInstance(arg) ||
                            (expected == Int::class.javaPrimitiveType && arg is Int) ||
                            (expected == Boolean::class.javaPrimitiveType && arg is Boolean)
                    }
                }
                type = type.superclass
            }
            val selected = method ?: throw NoSuchMethodException("${value.javaClass.name}.$name/${args.size}")
            selected.isAccessible = true
            selected.invoke(value, *args)
        }.onFailure { Log.w(tag, "Reflection failed: ${value.javaClass.simpleName}.$name", it) }.getOrNull()
    }
    private fun before(className: String, name: String, vararg parameters: Class<*>, block: HookParam.() -> Unit) {
        runCatching { hookBefore(resolve(findClass(className), name, *parameters)) {
            runCatching { block() }.onFailure { Log.w(tag, "$name callback failed", it) }
        } }.onSuccess { Log.i(tag, "Installed $className.$name") }
            .onFailure { Log.w(tag, "Hook unavailable: $className.$name", it) }
    }
    private fun after(className: String, name: String, vararg parameters: Class<*>, block: HookParam.() -> Unit) {
        runCatching { hookAfter(resolve(findClass(className), name, *parameters)) {
            runCatching { block() }.onFailure { Log.w(tag, "$name callback failed", it) }
        } }.onSuccess { Log.i(tag, "Installed $className.$name") }
            .onFailure { Log.w(tag, "Hook unavailable: $className.$name", it) }
    }
    companion object {
        private const val AS = "com.android.bluetooth.btservice.AdapterService"
        private const val A2DP = "com.android.bluetooth.a2dp.A2dpService"
        private const val HFP = "com.android.bluetooth.hfp.HeadsetService"
        private const val LEA = "com.android.bluetooth.le_audio.LeAudioService"
        private const val NI = "com.android.bluetooth.le_audio.LeAudioNativeInterface"
    }
}
