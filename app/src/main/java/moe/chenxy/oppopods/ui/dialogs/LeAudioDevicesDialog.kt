package moe.chenxy.oppopods.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import moe.chenxy.oppopods.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@SuppressLint("MissingPermission")
@Composable
fun LeAudioDevicesDialog(
    show: Boolean,
    addresses: Set<String>,
    onDismissRequest: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember(show) {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var selected by remember(show, addresses) { mutableStateOf(addresses) }
    val devices = remember(show, granted) {
        if (!show || !granted) emptyList() else runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                ?.bondedDevices.orEmpty().map { it.address to (it.name ?: it.alias ?: it.address) }
                .sortedWith(compareBy({ it.second }, { it.first }))
        }.getOrDefault(emptyList())
    }
    WindowDialog(
        title = stringResource(R.string.le_audio_devices),
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.le_audio_devices_hint))
            if (!granted) {
                TextButton(
                    text = stringResource(R.string.le_audio_devices_permission),
                    onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    if (devices.isEmpty()) item { Text(stringResource(R.string.no_paired_devices)) }
                    items(devices, key = { it.first }) { (address, name) ->
                        val checked = address in selected
                        val toggle = { selected = if (checked) selected - address else selected + address }
                        BasicComponent(
                            title = name,
                            summary = address,
                            onClick = toggle,
                            endActions = { Checkbox(state = ToggleableState(checked), onClick = toggle) },
                        )
                    }
                    // Keep unavailable saved addresses removable rather than silently discarding them.
                    items((selected - devices.map { it.first }.toSet()).sorted(), key = { it }) { address ->
                        BasicComponent(
                            title = stringResource(R.string.le_audio_saved_device),
                            summary = address,
                            onClick = { selected = selected - address },
                            endActions = {
                                Checkbox(state = ToggleableState.On, onClick = { selected = selected - address })
                            },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.le_audio_devices_save),
                    onClick = { onSave(selected) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
