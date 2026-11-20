package com.example.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityengine.CityEngine
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.ui.CityCanvas

@Composable
fun AdminMapScreen(
    engine: CityEngine,
    scene: CitySceneState,
    selectedHouse: House?,
    selectedPlot: Plot?,
    onHouseTapped: (House?) -> Unit,
    onPlotTapped: (Plot?) -> Unit,
    onClearSelection: () -> Unit,
    onBuildOnPlot: (Plot) -> Unit,
    onEditHouse: (House) -> Unit,
    onChangeColor: (House) -> Unit,
    onMoveHouse: (House) -> Unit,
    onDeleteHouse: (House) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        CityCanvas(
            engine = engine,
            scene = scene,
            onHouseTapped = onHouseTapped,
            onPlotTapped = onPlotTapped,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 3.dp
            ) {
                Text(
                    text = "اضغط على بيت لتعديله أو على أرض متاحة للبناء",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            FilledTonalIconButton(
                onClick = {
                    val center = engine.initialFocusWorld(scene)
                    engine.cameraController.centerOnWorld(center.x, center.y, 1.0f)
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_recenter_map_button")
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = "توسيط الخريطة")
            }
        }

        if (selectedHouse != null || selectedPlot != null) {
            ElevatedCard(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .padding(14.dp)
                    .testTag("admin_map_selection_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (selectedHouse != null) {
                                Text(
                                    text = selectedHouse.ownerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedHouse.flag.countryName} • ${selectedHouse.plotId} (${selectedHouse.coord.x},${selectedHouse.coord.y})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else if (selectedPlot != null) {
                                Text(
                                    text = "قطعة ${selectedPlot.id} (${selectedPlot.coord.x}, ${selectedPlot.coord.y})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedPlot.status == PlotStatus.AVAILABLE) "متاحة للبناء (AVAILABLE)" else "مشغولة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "إغلاق")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedHouse != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onEditHouse(selectedHouse) },
                                modifier = Modifier.weight(1f)
                            ) { Text("تعديل") }
                            FilledTonalButton(
                                onClick = { onChangeColor(selectedHouse) },
                                modifier = Modifier.weight(1f)
                            ) { Text("لون") }
                            FilledTonalButton(
                                onClick = { onMoveHouse(selectedHouse) },
                                modifier = Modifier.weight(1f)
                            ) { Text("نقل") }
                            Button(
                                onClick = { onDeleteHouse(selectedHouse) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f)
                            ) { Text("حذف") }
                        }
                    } else if (selectedPlot != null && selectedPlot.status == PlotStatus.AVAILABLE) {
                        Button(
                            onClick = { onBuildOnPlot(selectedPlot) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_build_on_selected_plot_button")
                        ) {
                            Text("بناء بيت على ${selectedPlot.id}")
                        }
                    }
                }
            }
        }
    }
}
