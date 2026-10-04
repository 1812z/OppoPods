package moe.chenxy.oppopods.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.config.LeAudioConfig
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun LeAudioSettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    config: LeAudioConfig,
    onConfigChange: (LeAudioConfig) -> Unit,
) {
    var vendorId by remember(config.vendorId) { mutableStateOf(config.vendorId) }
    var oesfMask by remember(config.oesfMask) { mutableStateOf(config.oesfMask) }
    val validVendor = vendorId.isNotEmpty() && vendorId.all { it in '0'..'9' } &&
        vendorId.toIntOrNull()?.let { it in 0..0xFFFF } == true
    val validMask = config.copy(oesfMask = oesfMask).parsedMask() != null

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
            start = 12.dp,
            end = 12.dp,
        ),
    ) {
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.le_audio_enable),
                    summary = stringResource(R.string.le_audio_enable_summary),
                    checked = config.enabled,
                    onCheckedChange = { onConfigChange(config.copy(enabled = it)) },
                )
            }
        }
        item { SmallTitle(text = stringResource(R.string.le_audio_latency_group)) }
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.le_audio_game_context),
                    summary = stringResource(R.string.le_audio_game_context_summary),
                    checked = config.gameContext,
                    onCheckedChange = { onConfigChange(config.copy(gameContext = it)) },
                )
            }
        }
        item { SmallTitle(text = stringResource(R.string.le_audio_connection_group)) }
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.le_audio_le_first),
                    summary = stringResource(R.string.le_audio_le_first_summary),
                    checked = config.leFirst,
                    onCheckedChange = { onConfigChange(config.copy(leFirst = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_poke),
                    summary = stringResource(R.string.le_audio_poke_summary),
                    checked = config.poke,
                    onCheckedChange = { onConfigChange(config.copy(poke = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_wake_classic),
                    summary = stringResource(R.string.le_audio_wake_classic_summary),
                    checked = config.wakeClassic,
                    onCheckedChange = { onConfigChange(config.copy(wakeClassic = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_fix_policy),
                    summary = stringResource(R.string.le_audio_fix_policy_summary),
                    checked = config.fixPolicy,
                    onCheckedChange = { onConfigChange(config.copy(fixPolicy = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_gate_hfp),
                    summary = stringResource(R.string.le_audio_gate_hfp_summary),
                    checked = config.gateHfp,
                    onCheckedChange = { onConfigChange(config.copy(gateHfp = it)) },
                )
            }
        }
        item { SmallTitle(text = stringResource(R.string.le_audio_stability_group)) }
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.le_audio_hold_gatt),
                    summary = stringResource(R.string.le_audio_hold_gatt_summary),
                    checked = config.holdGatt,
                    onCheckedChange = { onConfigChange(config.copy(holdGatt = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_complete_group),
                    summary = stringResource(R.string.le_audio_complete_group_summary),
                    checked = config.completeGroup,
                    onCheckedChange = { onConfigChange(config.copy(completeGroup = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_adopt_contexts),
                    summary = stringResource(R.string.le_audio_adopt_contexts_summary),
                    checked = config.adoptContexts,
                    onCheckedChange = { onConfigChange(config.copy(adoptContexts = it)) },
                )
            }
        }
        item { SmallTitle(text = stringResource(R.string.le_audio_protocol)) }
        item {
            Card {
                SwitchPreference(
                    title = stringResource(R.string.le_audio_at),
                    summary = stringResource(R.string.le_audio_at_summary),
                    checked = config.respondAt,
                    onCheckedChange = { onConfigChange(config.copy(respondAt = it)) },
                )
                SwitchPreference(
                    title = stringResource(R.string.le_audio_vdsp),
                    summary = stringResource(R.string.le_audio_vdsp_summary),
                    checked = config.sendVdsp,
                    onCheckedChange = { onConfigChange(config.copy(sendVdsp = it)) },
                )
                BasicComponent(
                    title = stringResource(R.string.le_audio_vendor_id),
                    summary = stringResource(R.string.le_audio_vendor_id_summary),
                )
                TextField(
                    value = vendorId,
                    onValueChange = { vendorId = it.trim() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                BasicComponent(
                    title = stringResource(R.string.le_audio_oesf_mask),
                    summary = stringResource(R.string.le_audio_oesf_mask_summary),
                )
                TextField(
                    value = oesfMask,
                    onValueChange = { oesfMask = it.trim() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Button(
                    enabled = validVendor && validMask,
                    onClick = { onConfigChange(config.copy(vendorId = vendorId, oesfMask = oesfMask)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(stringResource(R.string.le_audio_save_protocol)) }
            }
        }
        item {
            Button(
                onClick = {
                    vendorId = "1946"
                    oesfMask = "0x3f"
                    onConfigChange(LeAudioConfig(enabled = config.enabled))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.le_audio_reset)) }
        }
    }
}
