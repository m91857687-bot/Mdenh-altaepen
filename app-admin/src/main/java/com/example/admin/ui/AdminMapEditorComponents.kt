package com.example.admin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.AdminEditorUiState
import com.example.admin.EditorSubTool
import com.example.admin.EditorToolCategory
import com.example.admin.PendingPlacementDraft
import com.example.admin.R
import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.LightSourceType
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.ui.ArabFlagBadge
import com.example.core.data.history.CityEditorHistoryState

@Composable
fun EditorTopActionBar(
    editorState: AdminEditorUiState,
    historyState: CityEditorHistoryState,
    showDegradedServerBadge: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOpenLighting: () -> Unit,
    onOpenExpandGrid: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 3.dp,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                val hintRes = when {
                    editorState.subTool == EditorSubTool.PLOT_MULTI_SELECT -> R.string.editor_hint_multi
                    editorState.subTool.isTileBrush -> R.string.editor_hint_paint
                    editorState.subTool != EditorSubTool.SELECT_INSPECT -> R.string.editor_hint_place
                    else -> R.string.editor_hint_select
                }
                Text(
                    text = stringResource(hintRes),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onUndo,
                    enabled = historyState.canUndo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_undo_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo_cd))
                }
                FilledTonalIconButton(
                    onClick = onRedo,
                    enabled = historyState.canRedo,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_redo_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo_cd))
                }
                FilledTonalIconButton(
                    onClick = onOpenLighting,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_lighting_button")
                ) {
                    Icon(Icons.Rounded.Brightness6, contentDescription = stringResource(R.string.editor_lighting_cd))
                }
                FilledTonalIconButton(
                    onClick = onOpenExpandGrid,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_expand_grid_button")
                ) {
                    Icon(Icons.Rounded.AspectRatio, contentDescription = stringResource(R.string.editor_expand_grid_cd))
                }
                FilledTonalIconButton(
                    onClick = onRecenter,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("admin_recenter_map_button")
                ) {
                    Icon(Icons.Rounded.CenterFocusStrong, contentDescription = stringResource(R.string.editor_recenter_cd))
                }
            }
        }
        if (showDegradedServerBadge) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth().testTag("editor_degraded_server_notice")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.admin_builder_degraded_banner), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun EditorToolDock(
    editorState: AdminEditorUiState,
    onSelectCategory: (EditorToolCategory) -> Unit,
    onSelectSubTool: (EditorSubTool) -> Unit,
    onSelectBuildingType: (PublicBuildingType) -> Unit,
    onSelectPropType: (EnvironmentPropType) -> Unit,
    onSelectFlagCountry: (String) -> Unit,
    onSelectLightType: (LightSourceType) -> Unit,
    onToggleDragPaint: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth().testTag("editor_tool_dock")
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorToolCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = editorState.category == cat,
                        onClick = { onSelectCategory(cat) },
                        label = { Text(stringResource(categoryLabelRes(cat))) },
                        modifier = Modifier.testTag("editor_cat_${cat.name.lowercase()}")
                    )
                }
            }
            if (editorState.category != EditorToolCategory.SELECT) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (editorState.category) {
                        EditorToolCategory.PLOTS -> {
                            SubToolChip(editorState.subTool == EditorSubTool.PLOT_CREATE, stringResource(R.string.editor_tool_create_plot), "subtool_plot_create") { onSelectSubTool(EditorSubTool.PLOT_CREATE) }
                            SubToolChip(editorState.subTool == EditorSubTool.PLOT_MULTI_SELECT, stringResource(R.string.editor_tool_multi_select), "subtool_plot_multi") { onSelectSubTool(EditorSubTool.PLOT_MULTI_SELECT) }
                        }
                        EditorToolCategory.ROADS -> {
                            FilterChip(
                                selected = editorState.dragPaintEnabled,
                                onClick = onToggleDragPaint,
                                leadingIcon = { Icon(Icons.Rounded.Gesture, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.editor_drag_paint_label)) },
                                modifier = Modifier.testTag("editor_drag_paint_toggle")
                            )
                            SubToolChip(editorState.subTool == EditorSubTool.ROAD_PAINT, stringResource(R.string.editor_tool_road), "subtool_road") { onSelectSubTool(EditorSubTool.ROAD_PAINT) }
                            SubToolChip(editorState.subTool == EditorSubTool.SIDEWALK_PAINT, stringResource(R.string.editor_tool_sidewalk), "subtool_sidewalk") { onSelectSubTool(EditorSubTool.SIDEWALK_PAINT) }
                            SubToolChip(editorState.subTool == EditorSubTool.CROSSWALK_PAINT, stringResource(R.string.editor_tool_crosswalk), "subtool_crosswalk") { onSelectSubTool(EditorSubTool.CROSSWALK_PAINT) }
                            SubToolChip(editorState.subTool == EditorSubTool.PARK_PAINT, stringResource(R.string.editor_tool_park), "subtool_park") { onSelectSubTool(EditorSubTool.PARK_PAINT) }
                            SubToolChip(editorState.subTool == EditorSubTool.PLAZA_PAINT, stringResource(R.string.editor_tool_plaza), "subtool_plaza") { onSelectSubTool(EditorSubTool.PLAZA_PAINT) }
                            SubToolChip(editorState.subTool == EditorSubTool.ROAD_SEGMENT_PLACE, stringResource(R.string.editor_tool_road_segment), "subtool_road_seg") { onSelectSubTool(EditorSubTool.ROAD_SEGMENT_PLACE) }
                            SubToolChip(editorState.subTool == EditorSubTool.ERASE_TO_GRASS, stringResource(R.string.editor_tool_erase), "subtool_erase") { onSelectSubTool(EditorSubTool.ERASE_TO_GRASS) }
                        }
                        EditorToolCategory.DISTRICTS -> {
                            SubToolChip(editorState.subTool == EditorSubTool.DISTRICT_PLACE, stringResource(R.string.editor_tool_district_create), "subtool_district_create") { onSelectSubTool(EditorSubTool.DISTRICT_PLACE) }
                        }
                        EditorToolCategory.BUILDINGS -> {
                            PublicBuildingType.entries.forEach { bType ->
                                SubToolChip(
                                    selected = editorState.subTool == EditorSubTool.PUBLIC_BUILDING_PLACE && editorState.selectedBuildingType == bType,
                                    label = bType.labelAr,
                                    tag = "subtool_bld_${bType.name.lowercase()}"
                                ) { onSelectBuildingType(bType) }
                            }
                        }
                        EditorToolCategory.PROPS_LIGHTS -> {
                            SubToolChip(editorState.subTool == EditorSubTool.LIGHT_PLACE, stringResource(R.string.editor_tool_light_source), "subtool_light") {
                                onSelectLightType(editorState.selectedLightType)
                            }
                            EnvironmentPropType.entries.forEach { pType ->
                                SubToolChip(
                                    selected = editorState.subTool == EditorSubTool.PROP_PLACE && editorState.selectedPropType == pType,
                                    label = pType.labelAr,
                                    tag = "subtool_prop_${pType.name.lowercase()}"
                                ) { onSelectPropType(pType) }
                            }
                        }
                        EditorToolCategory.SELECT -> Unit
                    }
                }
                if (editorState.category == EditorToolCategory.PROPS_LIGHTS &&
                    editorState.subTool == EditorSubTool.PROP_PLACE &&
                    editorState.selectedPropType == EnvironmentPropType.CITY_FLAG
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArabCountriesRegistry.allCountries.forEach { spec ->
                            FilterChip(
                                selected = editorState.selectedFlagIso.equals(spec.isoCode, ignoreCase = true),
                                onClick = { onSelectFlagCountry(spec.isoCode) },
                                leadingIcon = {
                                    ArabFlagBadge(
                                        flag = spec.flagStyle,
                                        width = 20.dp,
                                        height = 14.dp,
                                        cornerRadius = 3.dp
                                    )
                                },
                                label = { Text(spec.nameAr) },
                                modifier = Modifier.testTag("dock_flag_${spec.isoCode}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubToolChip(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.testTag(tag))
}

@Composable
fun EditorPendingPreviewBanner(
    pending: PendingPlacementDraft,
    onRotate: () -> Unit,
    onSetDimensions: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isValid = pending.overlay.isValid
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isValid) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier.widthIn(max = 540.dp).fillMaxWidth().testTag("editor_preview_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isValid) {
                        "${pending.overlay.label ?: ""} (${pending.origin.x},${pending.origin.y}) • ${pending.widthCells}×${pending.heightCells} • ${pending.rotation.degrees}°"
                    } else {
                        pending.validationError ?: "الموقع غير صالح"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f).testTag("editor_preview_status_text")
                )
                IconButton(onClick = onCancel, modifier = Modifier.minimumInteractiveComponentSize().testTag("editor_cancel_preview_button")) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.editor_cancel))
                }
            }
            if (pending.tool == EditorSubTool.PLOT_CREATE || pending.tool == EditorSubTool.PUBLIC_BUILDING_PLACE || pending.tool == EditorSubTool.ROAD_SEGMENT_PLACE || pending.tool == EditorSubTool.DISTRICT_PLACE) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to 1, 2 to 1, 2 to 2, 3 to 2, 4 to 4).forEach { (w, h) ->
                        FilterChip(
                            selected = pending.widthCells == w && pending.heightCells == h,
                            onClick = { onSetDimensions(w, h) },
                            label = { Text("${w}×${h}") },
                            modifier = Modifier.testTag("preview_size_${w}x${h}")
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pending.tool != EditorSubTool.DISTRICT_PLACE && !pending.tool.isTileBrush) {
                    OutlinedButton(onClick = onRotate, modifier = Modifier.testTag("editor_rotate_draft_button")) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.editor_rotate))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onConfirm,
                    enabled = isValid,
                    modifier = Modifier.testTag("editor_confirm_preview_button")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.editor_confirm))
                }
            }
        }
    }
}

@Composable
fun EditorMultiSelectBar(
    selectedCount: Int,
    onSetAvailable: () -> Unit,
    onSetUnavailable: () -> Unit,
    onAssignDistrict: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("editor_multi_select_bar")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.editor_multi_count_format, selectedCount), fontWeight = FontWeight.Bold)
                IconButton(onClick = onClear, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.editor_close))
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = onSetAvailable, modifier = Modifier.testTag("batch_plots_available")) { Text(stringResource(R.string.editor_enable_plot)) }
                FilledTonalButton(onClick = onSetUnavailable, modifier = Modifier.testTag("batch_plots_unavailable")) { Text(stringResource(R.string.editor_disable_plot)) }
                FilledTonalButton(onClick = onAssignDistrict, modifier = Modifier.testTag("batch_plots_district")) { Text(stringResource(R.string.editor_assign_district)) }
                Button(onClick = onDeleteSelected, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("batch_plots_delete")) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_delete_house))
                }
            }
        }
    }
}

private fun categoryLabelRes(cat: EditorToolCategory): Int = when (cat) {
    EditorToolCategory.SELECT -> R.string.editor_cat_select
    EditorToolCategory.PLOTS -> R.string.editor_cat_plots
    EditorToolCategory.ROADS -> R.string.editor_cat_roads
    EditorToolCategory.DISTRICTS -> R.string.editor_cat_districts
    EditorToolCategory.BUILDINGS -> R.string.editor_cat_buildings
    EditorToolCategory.PROPS_LIGHTS -> R.string.editor_cat_props
}
