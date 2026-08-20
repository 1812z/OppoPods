package moe.chenxy.oppopods.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import moe.chenxy.oppopods.R
import moe.chenxy.oppopods.pods.EqDefaults
import moe.chenxy.oppopods.pods.EqDevicePreset
import moe.chenxy.oppopods.pods.EqPresetInfo
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalSlider
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun EqualizerPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    builtInPresets: List<EqPresetInfo>,
    devicePresets: List<EqDevicePreset>,
    selectedId: Int,
    customEqSupported: Boolean,
    customEqFrequencies: List<Int>,
    customEqMaxPresets: Int,
    onSelectPreset: (Int) -> Unit,
    onSavePreset: (Int, String, List<Int>, List<Int>, Int, Int) -> Unit,
    onDeletePreset: (EqDevicePreset) -> Unit,
) {
    val builtInIds = remember(builtInPresets) { builtInPresets.map { it.id }.toSet() }
    val customPresets = devicePresets.filter { it.id !in builtInIds && it.name.isNotBlank() }
    val canCreate = customEqSupported &&
        (customEqMaxPresets <= 0 || customPresets.size < customEqMaxPresets)
    val customPresetName = stringResource(R.string.eq_custom)
    var editor by remember { mutableStateOf<EqDevicePreset?>(null) }
    var deleteTarget by remember { mutableStateOf<EqDevicePreset?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
    ) {
        item { SmallTitle(text = stringResource(R.string.eq_recommended)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                builtInPresets.forEach { preset ->
                    EqCheckboxRow(
                        title = preset.name,
                        summary = presetDescription(preset),
                        checked = selectedId == preset.id,
                        onClick = { onSelectPreset(preset.id) },
                    )
                }
            }
        }

        item { SmallTitle(text = stringResource(R.string.eq_custom)) }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                customPresets.forEach { preset ->
                    CustomEqRow(
                        title = preset.name,
                        summary = stringResource(R.string.eq_custom_summary),
                        checked = selectedId == preset.id,
                        onSelect = { onSelectPreset(preset.id) },
                        onEdit = { editor = preset },
                        onDelete = { deleteTarget = preset },
                    )
                }
                if (customEqSupported) {
                    AddEqualizerRow(
                        title = stringResource(R.string.eq_add),
                        summary = stringResource(R.string.eq_add_summary),
                        enabled = canCreate,
                        onClick = {
                            val frequencies = customEqFrequencies.ifEmpty { EqDefaults.FREQUENCIES }
                            editor = EqDevicePreset(
                                id = 0,
                                name = "$customPresetName ${customPresets.size + 1}",
                                frequencies = frequencies,
                                gains = frequencies.map { 0 },
                            )
                        },
                    )
                }
            }
        }
    }

    EqualizerWindowDialog(
        preset = editor,
        fallbackFrequencies = customEqFrequencies,
        onDismiss = { editor = null },
        onSave = { updated ->
            onSavePreset(
                updated.id,
                updated.name,
                updated.frequencies,
                updated.gains,
                updated.minValue,
                updated.maxValue,
            )
            editor = null
        },
    )

    DeleteEqualizerDialog(
        preset = deleteTarget,
        onDismiss = { deleteTarget = null },
        onConfirm = { preset ->
            onDeletePreset(preset)
            deleteTarget = null
        },
    )
}

@Composable
private fun EqCheckboxRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 3.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = ToggleableState(checked),
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
private fun CustomEqRow(
    title: String,
    summary: String,
    checked: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onEdit)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 3.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = stringResource(R.string.eq_edit),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.eq_delete),
            )
        }
        Checkbox(
            state = ToggleableState(checked),
            onClick = onSelect,
        )
    }
}

@Composable
private fun AddEqualizerRow(
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.headline1,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 3.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun EqualizerWindowDialog(
    preset: EqDevicePreset?,
    fallbackFrequencies: List<Int>,
    onDismiss: () -> Unit,
    onSave: (EqDevicePreset) -> Unit,
) {
    var state by remember(preset) { mutableStateOf(preset?.normalized(fallbackFrequencies)) }
    WindowDialog(
        title = state?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.eq_new),
        show = preset != null,
        onDismissRequest = onDismiss,
    ) {
        state?.let { current ->
            Column(modifier = Modifier.fillMaxWidth()) {
                EqualizerSliders(current) { index, gain ->
                    val gains = current.gains.toMutableList()
                    gains[index] = gain
                    state = current.copy(gains = gains)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.eq_save),
                        onClick = { onSave(current) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteEqualizerDialog(
    preset: EqDevicePreset?,
    onDismiss: () -> Unit,
    onConfirm: (EqDevicePreset) -> Unit,
) {
    WindowDialog(
        title = stringResource(R.string.eq_delete_title),
        show = preset != null,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = preset?.let { stringResource(R.string.eq_delete_confirm, it.name) }.orEmpty(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = { preset?.let(onConfirm) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun EqualizerSliders(preset: EqDevicePreset, onGainChange: (Int, Int) -> Unit) {
    val lower = preset.minValue.coerceAtMost(preset.maxValue)
    val upper = preset.maxValue.coerceAtLeast(lower)
    val range = (upper - lower).coerceAtLeast(1).toFloat()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        preset.frequencies.forEachIndexed { index, frequency ->
            val gain = preset.gains.getOrNull(index)?.coerceIn(lower, upper) ?: 0
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (gain > 0) "+$gain" else gain.toString(), color = MiuixTheme.colorScheme.primary)
                VerticalSlider(
                    value = ((gain - lower) / range).coerceIn(0f, 1f),
                    onValueChange = {
                        onGainChange(index, (lower + it.coerceIn(0f, 1f) * range).roundToInt())
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.height(180.dp),
                )
                Text(formatFrequency(frequency), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun presetDescription(preset: EqPresetInfo): String = when (preset.modeType) {
    26 -> stringResource(R.string.eq_desc_authentic)
    27 -> stringResource(R.string.eq_desc_detail)
    28 -> stringResource(R.string.eq_desc_vocal)
    29 -> stringResource(R.string.eq_desc_bass)
    30 -> stringResource(R.string.eq_desc_dynaudio)
    34 -> stringResource(R.string.eq_desc_dynamic)
    else -> when {
        preset.name.contains("至臻原音", ignoreCase = true) ||
            preset.name.contains("Authentic", ignoreCase = true) ->
            stringResource(R.string.eq_desc_authentic)
        preset.name.contains("高清解析", ignoreCase = true) ||
            preset.name.contains("Detail", ignoreCase = true) ->
            stringResource(R.string.eq_desc_detail)
        preset.name.contains("纯享人声", ignoreCase = true) ||
            preset.name.contains("Vocal", ignoreCase = true) ->
            stringResource(R.string.eq_desc_vocal)
        preset.name.contains("澎湃低音", ignoreCase = true) ||
            preset.name.contains("Bass", ignoreCase = true) ->
            stringResource(R.string.eq_desc_bass)
        preset.name.contains("丹拿特调", ignoreCase = true) ||
            preset.name.contains("Dynaudio", ignoreCase = true) ->
            stringResource(R.string.eq_desc_dynaudio)
        preset.name.contains("活力动感", ignoreCase = true) ||
            preset.name.contains("Dynamic", ignoreCase = true) ->
            stringResource(R.string.eq_desc_dynamic)
        else -> stringResource(R.string.eq_recommended_summary)
    }
}

private fun EqDevicePreset.normalized(fallbackFrequencies: List<Int>): EqDevicePreset {
    val frequencies = frequencies.ifEmpty { fallbackFrequencies }.ifEmpty { EqDefaults.FREQUENCIES }
    val lower = minValue.coerceAtMost(maxValue)
    val upper = maxValue.coerceAtLeast(lower)
    return copy(
        frequencies = frequencies,
        gains = frequencies.mapIndexed { index, _ -> gains.getOrNull(index)?.coerceIn(lower, upper) ?: 0 },
        minValue = lower,
        maxValue = upper,
    )
}

private fun formatFrequency(value: Int): String =
    if (value >= 1000 && value % 1000 == 0) "${value / 1000}k" else value.toString()
