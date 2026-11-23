package com.example.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.admin.AdminBuilderDialogState
import com.example.admin.R
import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.CityLightingState
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.PublicBuildingType
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.model.TimeOfDayPhase
import com.example.cityengine.ui.ArabFlagBadge
import com.example.core.data.model.DistrictDraft
import com.example.core.data.model.EnvironmentPropDraft
import com.example.core.data.model.PublicBuildingDraft
import com.example.core.data.model.RoadSegmentDraft
import kotlin.math.abs

private val THEME_SWATCHES = listOf(0xFF2E8B7D, 0xFF3F6EA8, 0xFFB87833, 0xFF8B476B, 0xFF4F7A44)
private val WALL_SWATCHES = listOf(0xFFEFE7D8, 0xFFE6D5C3, 0xFFDCE6EE, 0xFFE9E2F3, 0xFFF2DFD3)
private val ACCENT_SWATCHES = listOf(0xFF2D6A8E, 0xFF8B4513, 0xFF2E7D32, 0xFF7B3F61, 0xFFB8860B)

@Composable
fun AdminBuilderDialogHost(
    dialogState: AdminBuilderDialogState?,
    currentWidth: Int,
    currentHeight: Int,
    currentLighting: CityLightingState,
    allPlots: List<Plot>,
    districts: List<DistrictZone>,
    onDismiss: () -> Unit,
    onExpandGrid: (Int, Int) -> Unit,
    onSaveLighting: (CityLightingState) -> Unit,
    onSaveDistrict: (DistrictDraft) -> Unit,
    onSaveBuilding: (PublicBuildingDraft) -> Unit,
    onSaveRoad: (RoadSegmentDraft) -> Unit,
    onResizePlot: (Plot, Int, Int) -> Unit,
    onMergePlots: (String, String) -> Unit,
    onAssignDistrict: (List<String>, String?) -> Unit,
    onSaveProp: (EnvironmentPropDraft) -> Unit
) {
    when (dialogState) {
        null -> Unit
        is AdminBuilderDialogState.ExpandGrid -> ExpandGridDialog(currentWidth, currentHeight, onDismiss, onExpandGrid)
        is AdminBuilderDialogState.LightingSettings -> LightingControlDialog(currentLighting, onDismiss, onSaveLighting)
        is AdminBuilderDialogState.DistrictEditor -> DistrictEditorDialog(dialogState.existing, dialogState.defaultOrigin, onDismiss, onSaveDistrict)
        is AdminBuilderDialogState.PublicBuildingEditor -> PublicBuildingDialog(dialogState.existing, districts, onDismiss, onSaveBuilding)
        is AdminBuilderDialogState.RoadSegmentEditor -> RoadSegmentDialog(dialogState.existing, dialogState.coord, onDismiss, onSaveRoad)
        is AdminBuilderDialogState.PlotResize -> PlotResizeDialog(dialogState.plot, onDismiss) { w, h -> onResizePlot(dialogState.plot, w, h) }
        is AdminBuilderDialogState.PlotMerge -> PlotMergePickerDialog(dialogState.plot, allPlots, onDismiss) { otherId -> onMergePlots(dialogState.plot.id, otherId) }
        is AdminBuilderDialogState.AssignDistrict -> AssignDistrictPickerDialog(districts, onDismiss) { distId -> onAssignDistrict(dialogState.plotIds, distId) }
        is AdminBuilderDialogState.PropDetailsEditor -> PropDetailsDialog(dialogState.prop, onDismiss, onSaveProp)
        is AdminBuilderDialogState.LightDetailsEditor -> Unit
    }
}

@Composable
private fun ExpandGridDialog(curW: Int, curH: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var wText by remember { mutableStateOf((curW + 4).toString()) }
    var hText by remember { mutableStateOf((curH + 4).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_expand_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${curW}×${curH} ->")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24 to 24, 32 to 32, 40 to 40).filter { it.first >= curW && it.second >= curH }.forEach { (w, h) ->
                        FilterChip(selected = wText == "$w" && hText == "$h", onClick = { wText = "$w"; hText = "$h" }, label = { Text("${w}×${h}") })
                    }
                }
                OutlinedTextField(value = wText, onValueChange = { wText = it.filter(Char::isDigit) }, label = { Text("Width") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("expand_width_input"))
                OutlinedTextField(value = hText, onValueChange = { hText = it.filter(Char::isDigit) }, label = { Text("Height") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("expand_height_input"))
            }
        },
        confirmButton = {
            val w = wText.toIntOrNull() ?: curW
            val h = hText.toIntOrNull() ?: curH
            Button(onClick = { onConfirm(w, h) }, enabled = w >= curW && h >= curH, modifier = Modifier.testTag("confirm_expand_grid_button")) {
                Text(stringResource(R.string.editor_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun LightingControlDialog(initial: CityLightingState, onDismiss: () -> Unit, onConfirm: (CityLightingState) -> Unit) {
    var phase by remember { mutableStateOf(initial.phase) }
    var azimuth by remember { mutableFloatStateOf(initial.sunAzimuthDeg) }
    var dimming by remember { mutableFloatStateOf(initial.ambientDimming) }
    var autoLamps by remember { mutableStateOf(initial.nightLampAutoOn) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_lighting_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeOfDayPhase.entries.forEach { p ->
                        FilterChip(selected = phase == p, onClick = { phase = p }, label = { Text(p.labelAr) }, modifier = Modifier.testTag("lighting_phase_${p.name.lowercase()}"))
                    }
                }
                Text("Azimuth: ${azimuth.toInt()}°")
                Slider(value = azimuth, onValueChange = { azimuth = it }, valueRange = 0f..360f)
                Text("Dimming: ${(dimming * 100).toInt()}%")
                Slider(value = dimming, onValueChange = { dimming = it }, valueRange = 0f..0.75f)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_auto_lamps_label), modifier = Modifier.weight(1f))
                    Switch(checked = autoLamps, onCheckedChange = { autoLamps = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(CityLightingState(phase, azimuth, dimming, autoLamps)) }, modifier = Modifier.testTag("confirm_lighting_button")) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun DistrictEditorDialog(existing: DistrictZone?, defaultOrigin: GridCoord, onDismiss: () -> Unit, onConfirm: (DistrictDraft) -> Unit) {
    var nameAr by remember { mutableStateOf(existing?.nameAr ?: "") }
    var code by remember { mutableStateOf(existing?.code ?: "") }
    var colorHex by remember { mutableStateOf(existing?.themeColorHex ?: THEME_SWATCHES.first()) }
    var style by remember { mutableStateOf(existing?.ambientStyle ?: "RESIDENTIAL") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_district_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text("اسم الحي") }, modifier = Modifier.fillMaxWidth().testTag("district_name_input"))
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("رمز الحي (Code)") }, modifier = Modifier.fillMaxWidth().testTag("district_code_input"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    THEME_SWATCHES.forEach { hex ->
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(hex)).border(if (colorHex == hex) 3.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { colorHex = hex })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RESIDENTIAL", "COMMERCIAL", "CIVIC", "PARKLAND").forEach { s ->
                        FilterChip(selected = style == s, onClick = { style = s }, label = { Text(s) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(DistrictDraft(existing?.id, nameAr.trim(), code.trim().ifEmpty { "D1" }, colorHex, existing?.origin ?: defaultOrigin, existing?.widthCells ?: 4, existing?.heightCells ?: 4, style))
                },
                enabled = nameAr.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_district_button")
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun PublicBuildingDialog(existing: PublicBuilding, districts: List<DistrictZone>, onDismiss: () -> Unit, onConfirm: (PublicBuildingDraft) -> Unit) {
    var nameAr by remember { mutableStateOf(existing.nameAr) }
    var descriptionAr by remember { mutableStateOf(existing.descriptionAr.orEmpty()) }
    var publicInfoAr by remember { mutableStateOf(existing.publicInfoAr.orEmpty()) }
    var type by remember { mutableStateOf(existing.type) }
    var level by remember { mutableIntStateOf(existing.upgradeLevel) }
    var primaryHex by remember { mutableStateOf(existing.primaryColorHex) }
    var accentHex by remember { mutableStateOf(existing.accentColorHex) }
    var litAtNight by remember { mutableStateOf(existing.isLitAtNight) }
    var districtId by remember { mutableStateOf(existing.districtId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_building_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text("اسم المبنى") }, modifier = Modifier.fillMaxWidth().testTag("building_name_input"))
                OutlinedTextField(
                    value = descriptionAr,
                    onValueChange = { descriptionAr = it },
                    label = { Text(stringResource(R.string.building_description_label)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("building_description_input")
                )
                OutlinedTextField(
                    value = publicInfoAr,
                    onValueChange = { publicInfoAr = it },
                    label = { Text(stringResource(R.string.building_public_info_label)) },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("building_public_info_input")
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PublicBuildingType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.labelAr) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { lv -> FilterChip(selected = level == lv, onClick = { level = lv }, label = { Text("Lv $lv") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WALL_SWATCHES.forEach { hex -> Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(hex)).border(if (primaryHex == hex) 2.5.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { primaryHex = hex }) }
                    ACCENT_SWATCHES.forEach { hex -> Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(hex)).border(if (accentHex == hex) 2.5.dp else 1.dp, MaterialTheme.colorScheme.primary, CircleShape).clickable { accentHex = hex }) }
                }
                if (districts.isNotEmpty()) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = districtId == null, onClick = { districtId = null }, label = { Text(stringResource(R.string.editor_no_district)) })
                        districts.forEach { d -> FilterChip(selected = districtId == d.id, onClick = { districtId = d.id }, label = { Text(d.nameAr) }) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("إضاءة ليلية", modifier = Modifier.weight(1f))
                    Switch(checked = litAtNight, onCheckedChange = { litAtNight = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PublicBuildingDraft(
                            id = existing.id,
                            districtId = districtId,
                            nameAr = nameAr.trim().ifEmpty { type.labelAr },
                            type = type,
                            coord = existing.coord,
                            widthCells = existing.widthCells,
                            heightCells = existing.heightCells,
                            rotation = existing.rotation,
                            upgradeLevel = level,
                            primaryColorHex = primaryHex,
                            accentColorHex = accentHex,
                            assetKey = "building_${type.name.lowercase()}",
                            isLitAtNight = litAtNight,
                            descriptionAr = descriptionAr.trim().ifEmpty { null },
                            publicInfoAr = publicInfoAr.trim().ifEmpty { null }
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_building_button")
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun RoadSegmentDialog(existing: RoadSegment?, coord: GridCoord, onDismiss: () -> Unit, onConfirm: (RoadSegmentDraft) -> Unit) {
    var streetName by remember { mutableStateOf(existing?.streetNameAr.orEmpty()) }
    var lanes by remember { mutableIntStateOf(existing?.lanes ?: 2) }
    var sidewalk by remember { mutableStateOf(existing?.hasSidewalkBorder ?: true) }
    var crosswalk by remember { mutableStateOf(existing?.hasCrosswalk ?: false) }
    var parking by remember { mutableStateOf(existing?.hasParkingBay ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_road_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = streetName, onValueChange = { streetName = it }, label = { Text("اسم الشارع") }, modifier = Modifier.fillMaxWidth().testTag("road_name_input"))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 4).forEach { l -> FilterChip(selected = lanes == l, onClick = { lanes = l }, label = { Text("$l Lanes") }) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("حواف أرصفة", modifier = Modifier.weight(1f)); Switch(checked = sidewalk, onCheckedChange = { sidewalk = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("معبر مشاة", modifier = Modifier.weight(1f)); Switch(checked = crosswalk, onCheckedChange = { crosswalk = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("مواقف جانبية", modifier = Modifier.weight(1f)); Switch(checked = parking, onCheckedChange = { parking = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(RoadSegmentDraft(existing?.id, existing?.coord ?: coord, existing?.widthCells ?: 1, existing?.heightCells ?: 1, lanes, sidewalk, crosswalk, parking, streetName.trim().ifEmpty { null })) },
                modifier = Modifier.testTag("confirm_road_button")
            ) { Text(stringResource(R.string.editor_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun PlotResizeDialog(plot: Plot, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var w by remember { mutableIntStateOf(plot.widthCells) }
    var h by remember { mutableIntStateOf(plot.heightCells) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.editor_resize)} • ${plot.plotCode}", fontWeight = FontWeight.Bold) },
        text = {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to 1, 2 to 1, 1 to 2, 2 to 2, 3 to 2).forEach { (pw, ph) ->
                    FilterChip(selected = w == pw && h == ph, onClick = { w = pw; h = ph }, label = { Text("${pw}×${ph}") })
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(w, h) }, modifier = Modifier.testTag("confirm_plot_resize_button")) { Text(stringResource(R.string.editor_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}

@Composable
private fun PlotMergePickerDialog(target: Plot, allPlots: List<Plot>, onDismiss: () -> Unit, onPickOther: (String) -> Unit) {
    val candidates = remember(target, allPlots) {
        allPlots.filter { other ->
            other.id != target.id && other.status != PlotStatus.OCCUPIED &&
                ((other.coord.y == target.coord.y && other.heightCells == target.heightCells && (other.maxXExclusive == target.coord.x || target.maxXExclusive == other.coord.x)) ||
                    (other.coord.x == target.coord.x && other.widthCells == target.widthCells && (other.maxYExclusive == target.coord.y || target.maxYExclusive == other.coord.y)) ||
                    (abs(other.coord.x - target.coord.x) + abs(other.coord.y - target.coord.y) == 1 && other.widthCells == target.widthCells && other.heightCells == target.heightCells))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_merge_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isEmpty()) Text(stringResource(R.string.editor_no_adjacent_merge))
                else candidates.forEach { p ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { onPickOther(p.id) }.testTag("merge_pick_${p.id}")) {
                        Text("${p.plotCode} (${p.coord.x},${p.coord.y}) • ${p.widthCells}×${p.heightCells}", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_close)) } }
    )
}

@Composable
private fun AssignDistrictPickerDialog(districts: List<DistrictZone>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_assign_district_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { onPick(null) }) {
                    Text(stringResource(R.string.editor_no_district), modifier = Modifier.padding(12.dp))
                }
                districts.forEach { d ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().clickable { onPick(d.id) }.testTag("assign_district_${d.id}")) {
                        Text("${d.nameAr} (${d.code})", modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_close)) } }
    )
}

@Composable
private fun PropDetailsDialog(prop: EnvironmentProp, onDismiss: () -> Unit, onConfirm: (EnvironmentPropDraft) -> Unit) {
    var labelAr by remember { mutableStateOf(prop.labelAr.orEmpty()) }
    var scale by remember { mutableFloatStateOf(prop.scale) }
    var rot by remember { mutableStateOf(prop.rotation) }
    val initialIso = remember(prop) {
        ArabCountriesRegistry.parseCountryCodeFromAssetKey(prop.assetKey)
            ?: ArabCountriesRegistry.allCountries.getOrNull(prop.variant)?.isoCode
            ?: "SA"
    }
    var selectedFlagIso by remember(prop) { mutableStateOf(initialIso) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_prop_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (prop.type == EnvironmentPropType.CITY_FLAG) {
                    Text(
                        text = stringResource(R.string.editor_pick_country_flag),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ArabCountriesRegistry.allCountries.forEach { spec ->
                            FilterChip(
                                selected = selectedFlagIso.equals(spec.isoCode, ignoreCase = true),
                                onClick = {
                                    selectedFlagIso = spec.isoCode
                                    labelAr = spec.nameAr
                                },
                                leadingIcon = {
                                    ArabFlagBadge(
                                        flag = spec.flagStyle,
                                        width = 22.dp,
                                        height = 15.dp,
                                        cornerRadius = 3.dp
                                    )
                                },
                                label = { Text(spec.nameAr) },
                                modifier = Modifier.testTag("prop_flag_${spec.isoCode}")
                            )
                        }
                    }
                }
                OutlinedTextField(value = labelAr, onValueChange = { labelAr = it }, label = { Text("تسمية مخصصة") }, modifier = Modifier.fillMaxWidth())
                Text("الحجم: ${"%.1f".format(scale)}x")
                Slider(value = scale, onValueChange = { scale = it }, valueRange = 0.6f..1.6f)
                FilterChip(selected = false, onClick = { rot = rot.rotatedClockwise() }, label = { Text("${stringResource(R.string.editor_rotate)}: ${rot.degrees}°") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedVariant = if (prop.type == EnvironmentPropType.CITY_FLAG) {
                        ArabCountriesRegistry.allCountries.indexOfFirst { it.isoCode.equals(selectedFlagIso, ignoreCase = true) }.coerceAtLeast(0)
                    } else {
                        prop.variant
                    }
                    val resolvedAssetKey = if (prop.type == EnvironmentPropType.CITY_FLAG) {
                        ArabCountriesRegistry.assetKeyFor(selectedFlagIso)
                    } else {
                        prop.assetKey
                    }
                    onConfirm(
                        EnvironmentPropDraft(
                            id = prop.id,
                            type = prop.type,
                            coord = prop.coord,
                            widthCells = prop.widthCells,
                            heightCells = prop.heightCells,
                            rotation = rot,
                            scale = scale,
                            variant = resolvedVariant,
                            labelAr = labelAr.trim().ifEmpty { null },
                            layer = prop.layer,
                            primaryColorHex = prop.primaryColorHex,
                            assetKey = resolvedAssetKey
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_prop_details_button")
            ) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.editor_cancel)) } }
    )
}
