package moe.chenxy.oppopods.hook.lc3

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.reflect.Method
import moe.chenxy.oppopods.hook.HookContext
import moe.chenxy.oppopods.hook.Log
import moe.chenxy.oppopods.hook.getObjectField

/** Answers the earbuds' HFP host-identification handshake; the system owns LE Audio connection. */
@SuppressLint("MissingPermission")
class Lc3HandshakeHook : HookContext() {
    private val tag = "OppoPods-LC3"
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = mutableMapOf<String, Session>()
    private val lock = Any()

    private class Session(val nativeInterface: Any) {
        var activationPending = false
        var lastActivationAt = 0L
    }

    override fun onHook() {
        runCatching {
            hookBefore(findMethod(
                "com.android.bluetooth.hfp.HeadsetStateMachine",
                "processVendorSpecificAt",
                String::class.java,
                BluetoothDevice::class.java
            )) {
                val command = args[0] as? String ?: return@hookBefore
                val device = args[1] as? BluetoothDevice ?: return@hookBefore
                if (!isTarget(device)) return@hookBefore

                val reply = when {
                    command.startsWith("+VDID", ignoreCase = true) -> "+VDID: 1946"
                    command.startsWith("+VDSF", ignoreCase = true) -> "+VDSF: 7"
                    command.startsWith("+OESF", ignoreCase = true) -> {
                        val original = command.substringAfter('=', command.substringAfter(':', "0"))
                            .substringBefore(',').trim()
                        "+OESF=${original.ifEmpty { "0" }},1"
                    }
                    else -> return@hookBefore
                }

                val nativeInterface = runCatching { getObjectField(instance, "mNativeInterface") }
                    .onFailure { Log.w(tag, "HFP native interface unavailable", it) }
                    .getOrNull() ?: return@hookBefore
                if (!sendAt(nativeInterface, device, reply)) return@hookBefore

                val session = synchronized(lock) {
                    sessions.getOrPut(device.address) { Session(nativeInterface) }.let { old ->
                        if (old.nativeInterface === nativeInterface) old
                        else Session(nativeInterface).also { sessions[device.address] = it }
                    }
                }
                // Suppress the unknown-AT error only after our response was sent successfully.
                result = null
                if (reply == "+VDID: 1946") scheduleActivation(device, session)
            }
            Log.i(tag, "HFP vendor AT handshake installed")
        }.onFailure { Log.w(tag, "HFP vendor AT handshake unavailable", it) }

        // Only clear pending handshake work on HFP disconnect; do not alter any profile policy.
        runCatching {
            hookAfter(findMethod(
                "com.android.bluetooth.hfp.HeadsetStateBase",
                "broadcastConnectionState",
                BluetoothDevice::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )) {
                if (args[2] != BluetoothProfile.STATE_DISCONNECTED) return@hookAfter
                val device = args[0] as? BluetoothDevice ?: return@hookAfter
                synchronized(lock) { sessions.remove(device.address) }
            }
        }.onFailure { Log.w(tag, "HFP disconnect cleanup unavailable", it) }
    }

    private fun scheduleActivation(device: BluetoothDevice, session: Session) {
        synchronized(lock) {
            if (session.activationPending ||
                (session.lastActivationAt != 0L && SystemClock.elapsedRealtime() - session.lastActivationAt < 5_000L)
            ) return
            session.activationPending = true
        }
        handler.postDelayed({
            val active = synchronized(lock) {
                session.activationPending = false
                sessions[device.address] === session
            }
            if (!active || device.bondState != BluetoothDevice.BOND_BONDED) return@postDelayed
            if (sendAt(session.nativeInterface, device, "+VDSP=1,1")) {
                synchronized(lock) { session.lastActivationAt = SystemClock.elapsedRealtime() }
            }
        }, 500L)
    }

    private fun sendAt(nativeInterface: Any, device: BluetoothDevice, response: String): Boolean =
        runCatching {
            var type: Class<*>? = nativeInterface.javaClass
            var method: Method? = null
            while (type != null && method == null) {
                method = type.declaredMethods.firstOrNull {
                    it.name == "atResponseString" && it.parameterTypes.contentEquals(
                        arrayOf(BluetoothDevice::class.java, String::class.java)
                    )
                }
                type = type.superclass
            }
            val atResponse = method ?: error("atResponseString(BluetoothDevice, String) missing")
            atResponse.isAccessible = true
            val sent = atResponse.invoke(nativeInterface, device, response)
            if (sent == false) error("atResponseString returned false")
            Log.d(tag, "sent $response to ${device.address}")
            true
        }.onFailure { Log.w(tag, "failed to send $response to ${device.address}", it) }.getOrDefault(false)

    private fun isTarget(device: BluetoothDevice): Boolean {
        val name = runCatching { device.name ?: device.alias }.getOrNull() ?: return false
        return name.contains("oppo", ignoreCase = true) ||
            name.contains("oneplus", ignoreCase = true) ||
            name.contains("enco", ignoreCase = true)
    }
}
