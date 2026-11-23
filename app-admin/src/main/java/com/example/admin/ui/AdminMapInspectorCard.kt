package com.example.admin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.R
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment

@Composable
fun AdminMapInspectorCard(
    selectedHouse: House?,
    selectedPlot: Plot?,
    selectedBuilding: PublicBuilding?,
    selectedProp: EnvironmentProp?,
    selectedRoad: RoadSegment?,
    selectedRoadCoord: GridCoord?,
    selectedDistrict: DistrictZone?,
    onClearSelection: () -> Unit,
    onBuildOnPlot: (Plot) -> Unit,
    onEditHouse: (House) -> Unit,
    onChangeColor: (House) -> Unit,
    onMoveHouse: (House) -> Unit,
    onDeleteHouse: (House) -> Unit,
    onMovePlot: (Plot) -> Unit,
    onResizePlot: (Plot) -> Unit,
    onRotatePlot: (Plot) -> Unit,
    onSplitPlot: (Plot, Boolean) -> Unit,
    onMergePlot: (Plot) -> Unit,
    onTogglePlotAvailability: (Plot) -> Unit,
    onAssignPlotDistrict: (Plot) -> Unit,
    onDeletePlot: (Plot) -> Unit,
    onEditBuilding: (PublicBuilding) -> Unit,
    onMoveBuilding: (PublicBuilding) -> Unit,
    onRotateBuilding: (PublicBuilding) -> Unit,
    onDeleteBuilding: (PublicBuilding) -> Unit,
    onEditProp: (EnvironmentProp) -> Unit,
    onDeleteProp: (EnvironmentProp) -> Unit,
    onEditRoad: (RoadSegment?, GridCoord) -> Unit,
    onEraseRoadOrTile: (RoadSegment?, GridCoord) -> Unit,
    onEditDistrict: (DistrictZone) -> Unit,
    onDeleteDistrict: (DistrictZone) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .testTag("admin_map_selection_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        selectedHouse != null -> {
                            Text(selectedHouse.ownerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedHouse.flag.countryName} • ${selectedHouse.plotId} (${selectedHouse.coord.x},${selectedHouse.coord.y})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        selectedPlot != null -> {
                            Text("${selectedPlot.plotCode} (${selectedPlot.coord.x},${selectedPlot.coord.y}) • ${selectedPlot.widthCells}×${selectedPlot.heightCells} • ${selectedPlot.rotation.degrees}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val statusRes = when (selectedPlot.status) {
                                PlotStatus.AVAILABLE -> R.string.status_available
                                PlotStatus.UNAVAILABLE -> R.string.status_unavailable
                                PlotStatus.OCCUPIED -> R.string.status_occupied
                            }
                            Text(stringResource(statusRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        selectedBuilding != null -> {
                            Text(selectedBuilding.nameAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedBuilding.type.labelAr} • Lv ${selectedBuilding.upgradeLevel} • (${selectedBuilding.coord.x},${selectedBuilding.coord.y}) • ${selectedBuilding.widthCells}×${selectedBuilding.heightCells}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        selectedProp != null -> {
                            Text(selectedProp.labelAr ?: selectedProp.type.labelAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedProp.type.labelAr} • (${selectedProp.coord.x},${selectedProp.coord.y})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        selectedDistrict != null -> {
                            Text(selectedDistrict.nameAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${selectedDistrict.code} • (${selectedDistrict.origin.x},${selectedDistrict.origin.y}) ${selectedDistrict.widthCells}×${selectedDistrict.heightCells}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        selectedRoad != null || selectedRoadCoord != null -> {
                            Text(selectedRoad?.streetNameAr ?: "شارع / أرضية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val c = selectedRoad?.coord ?: selectedRoadCoord!!
                            Text("(${c.x},${c.y})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconButton(onClick = onClearSelection, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.editor_close))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                selectedHouse != null -> {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEditHouse(selectedHouse) }) { Text(stringResource(R.string.action_edit_house)) }
                        FilledTonalButton(onClick = { onChangeColor(selectedHouse) }) { Text(stringResource(R.string.action_change_color)) }
                        FilledTonalButton(onClick = { onMoveHouse(selectedHouse) }) { Text(stringResource(R.string.action_move_house)) }
                        Button(onClick = { onDeleteHouse(selectedHouse) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text(stringResource(R.string.action_delete_house))
                        }
                    }
                }
                selectedPlot != null -> {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedPlot.status == PlotStatus.AVAILABLE) {
                            Button(onClick = { onBuildOnPlot(selectedPlot) }, modifier = Modifier.testTag("map_build_on_selected_plot_button")) {
                                Text(stringResource(R.string.dashboard_build_house))
                            }
                        }
                        if (selectedPlot.status != PlotStatus.OCCUPIED) {
                            FilledTonalButton(onClick = { onMovePlot(selectedPlot) }, modifier = Modifier.testTag("plot_move_button")) { Text(stringResource(R.string.action_move_house)) }
                            FilledTonalButton(onClick = { onResizePlot(selectedPlot) }, modifier = Modifier.testTag("plot_resize_button")) { Text(stringResource(R.string.editor_resize)) }
                            FilledTonalButton(onClick = { onRotatePlot(selectedPlot) }, modifier = Modifier.testTag("plot_rotate_button")) { Text(stringResource(R.string.editor_rotate)) }
                            if (selectedPlot.widthCells >= 2) {
                                FilledTonalButton(onClick = { onSplitPlot(selectedPlot, true) }, modifier = Modifier.testTag("plot_split_h_button")) { Text(stringResource(R.string.editor_split_h)) }
                            }
                            if (selectedPlot.heightCells >= 2) {
                                FilledTonalButton(onClick = { onSplitPlot(selectedPlot, false) }, modifier = Modifier.testTag("plot_split_v_button")) { Text(stringResource(R.string.editor_split_v)) }
                            }
                            FilledTonalButton(onClick = { onMergePlot(selectedPlot) }, modifier = Modifier.testTag("plot_merge_button")) { Text(stringResource(R.string.editor_merge)) }
                            FilledTonalButton(onClick = { onTogglePlotAvailability(selectedPlot) }, modifier = Modifier.testTag("plot_toggle_avail_button")) {
                                Text(stringResource(if (selectedPlot.status == PlotStatus.UNAVAILABLE) R.string.editor_enable_plot else R.string.editor_disable_plot))
                            }
                            FilledTonalButton(onClick = { onAssignPlotDistrict(selectedPlot) }, modifier = Modifier.testTag("plot_assign_district_button")) { Text(stringResource(R.string.editor_assign_district)) }
                            Button(onClick = { onDeletePlot(selectedPlot) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("plot_delete_button")) {
                                Text(stringResource(R.string.editor_delete_plot))
                            }
                        }
                    }
                }
                selectedBuilding != null -> {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEditBuilding(selectedBuilding) }, modifier = Modifier.testTag("building_edit_button")) { Text(stringResource(R.string.action_edit_house)) }
                        FilledTonalButton(onClick = { onMoveBuilding(selectedBuilding) }, modifier = Modifier.testTag("building_move_button")) { Text(stringResource(R.string.action_move_house)) }
                        FilledTonalButton(onClick = { onRotateBuilding(selectedBuilding) }, modifier = Modifier.testTag("building_rotate_button")) { Text(stringResource(R.string.editor_rotate)) }
                        Button(onClick = { onDeleteBuilding(selectedBuilding) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("building_delete_button")) {
                            Text(stringResource(R.string.action_delete_house))
                        }
                    }
                }
                selectedProp != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEditProp(selectedProp) }, modifier = Modifier.testTag("prop_edit_button")) { Text(stringResource(R.string.action_edit_house)) }
                        Button(onClick = { onDeleteProp(selectedProp) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("prop_delete_button")) {
                            Text(stringResource(R.string.action_delete_house))
                        }
                    }
                }
                selectedDistrict != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEditDistrict(selectedDistrict) }, modifier = Modifier.testTag("district_edit_button")) { Text(stringResource(R.string.action_edit_house)) }
                        Button(onClick = { onDeleteDistrict(selectedDistrict) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("district_delete_button")) {
                            Text(stringResource(R.string.action_delete_house))
                        }
                    }
                }
                selectedRoad != null || selectedRoadCoord != null -> {
                    val coord = selectedRoad?.coord ?: selectedRoadCoord!!
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onEditRoad(selectedRoad, coord) }, modifier = Modifier.testTag("road_edit_button")) { Text(stringResource(R.string.action_edit_house)) }
                        Button(onClick = { onEraseRoadOrTile(selectedRoad, coord) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("road_erase_button")) {
                            Text(stringResource(R.string.editor_erase_grass))
                        }
                    }
                }
            }
        }
    }
}
