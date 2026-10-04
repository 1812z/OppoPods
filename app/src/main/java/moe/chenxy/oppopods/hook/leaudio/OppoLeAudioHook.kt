package moe.chenxy.oppopods.hook.leaudio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Method
import java.util.Locale
import moe.chenxy.oppopods.config.ConfigManager
import moe.chenxy.oppopods.hook.HookContext
import moe.chenxy.oppopods.hook.Log
import moe.chenxy.oppopods.hook.getObjectField

/** OPPO vendor handshake plus the HyperOS connection prerequisites from OPPOLeaConnect. */
@SuppressLint("MissingPermission")
class OppoLeAudioHook : HookContext() {
    private val tag = "OppoPods-LEAudio"
    private val handler = Handler(Looper.getMainLooper())
    private var configListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var atString: Method
    private lateinit var atCode: Method
    private var lastConfig = ConfigManager.current().leAudio

    override fun onHook() {
        val connection = LeAudioConnectionHook().also {
            it.module = module
            it.appClassLoader = appClassLoader
            it.prefs = prefs
            it.packageName = packageName
            it.onHook()
        }
        // Always install callbacks so the master switch can also enable the feature at runtime.
        configListener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == ConfigManager.PREF_KEY_CONFIG_JSON) {
                ConfigManager.refreshFromPrefs(preferences)
                val current = ConfigManager.current().leAudio
                if (lastConfig != current) handler.removeCallbacksAndMessages(null)
                lastConfig = current
                connection.configChanged()
            }
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

        runCatching {
            val nativeClass = "com.android.bluetooth.hfp.HeadsetNativeInterface"
            atString = findMethod(nativeClass, "atResponseString", BluetoothDevice::class.java, String::class.java)
            atCode = findMethod(nativeClass, "atResponseCode", BluetoothDevice::class.java,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
        }.onFailure { Log.w(tag, "HFP AT interface unavailable", it) }.getOrElse { return }

        runCatching {
            hookBefore(findMethod("com.android.bluetooth.hfp.HeadsetStateMachine", "processUnknownAt",
                String::class.java, BluetoothDevice::class.java)) {
                val config = ConfigManager.current().leAudio
                if (!config.enabled || !config.respondAt) return@hookBefore
                val device = args[1] as? BluetoothDevice ?: return@hookBefore
                if (!connection.isTarget(device)) return@hookBefore
                connection.rememberClassic(device)
                val command = (args[0] as? String)?.trim()?.uppercase(Locale.ROOT)
                    ?.replace(" ", "")?.removePrefix("AT") ?: return@hookBefore
                val name = command.takeWhile { it != '=' && it != ':' && it != '?' }
                Log.i(tag, "AT received: $command from ${device.address}")
                val reply = when (name) {
                    "+VDID" -> "+VDID: ${config.vendorId}"
                    "+VDSF" -> "+VDSF: 7"
                    "+VDSP" -> "+VDSP=1,1"
                    "+OESF" -> {
                        val values = command.substringAfter('=', "").split(',')
                        val key = values.getOrNull(0)?.toIntOrNull()
                        val requested = values.getOrNull(1)?.toIntOrNull()
                        if (key == null || requested == null) "+OESF=0,0"
                        else "+OESF=$key,${requested and (config.parsedMask() ?: 0x3F)}"
                    }
                    else -> return@hookBefore
                }
                val native = getObjectField(instance, "mNativeInterface") ?: return@hookBefore
                if (send(native, device, reply, acknowledge = true)) result = null
            }
            Log.i(tag, "Vendor AT handshake installed")
        }.onFailure { Log.w(tag, "Vendor AT handshake unavailable", it) }

        runCatching {
            hookBefore(findMethod("com.android.bluetooth.hfp.HeadsetStateMachine\$HeadsetStateBase", "exit")) {
                val sm = getObjectField(instance, "this\$0") ?: return@hookBefore
                handler.removeCallbacksAndMessages(sm)
            }
            hookAfter(findMethod("com.android.bluetooth.hfp.HeadsetStateMachine\$Connected", "enter")) {
                val sm = getObjectField(instance, "this\$0") ?: return@hookAfter
                val config = ConfigManager.current().leAudio
                if (!config.enabled || !config.sendVdsp) return@hookAfter
                val device = getObjectField(sm, "mDevice") as? BluetoothDevice ?: return@hookAfter
                if (!connection.isTarget(device)) return@hookAfter
                connection.rememberClassic(device)
                val native = getObjectField(sm, "mNativeInterface") ?: return@hookAfter
                val stateMethod = sm.javaClass.getDeclaredMethod("getConnectionState").apply { isAccessible = true }
                handler.postAtTime({
                    runCatching {
                        val current = ConfigManager.current().leAudio
                        if (current.enabled && current.sendVdsp && !connection.isPaused(device) && device.bondState == BluetoothDevice.BOND_BONDED &&
                            stateMethod.invoke(sm) == BluetoothProfile.STATE_CONNECTED) {
                            send(native, device, "+VDSP=1,1", acknowledge = false)
                        }
                    }.onFailure { Log.w(tag, "SLC initialization failed", it) }
                }, sm, android.os.SystemClock.uptimeMillis() + 500L)
            }
            Log.i(tag, "SLC initialization installed")
        }.onFailure { Log.w(tag, "SLC initialization unavailable", it) }
    }

    private fun send(native: Any, device: BluetoothDevice, reply: String, acknowledge: Boolean): Boolean =
        runCatching {
            check(atString.invoke(native, device, reply) != false) { "atResponseString returned false" }
            if (acknowledge) check(atCode.invoke(native, device, 1, 0) != false) { "atResponseCode returned false" }
            Log.i(tag, "sent $reply${if (acknowledge) " (+OK)" else ""} to ${device.address}")
            true
        }.onFailure { Log.w(tag, "Failed to send $reply", it) }.getOrDefault(false)

}
