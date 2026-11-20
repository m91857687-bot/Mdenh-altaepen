package com.example.admin.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.PlotFilter
import com.example.cityengine.model.House
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.ValidationResult

@Composable
fun AdminPlotsScreen(
    plots: List<Plot>,
    housesById: Map<String, House>,
    activeFilter: PlotFilter,
    validatePlot: (String) -> ValidationResult,
    onFilterChange: (PlotFilter) -> Unit,
    onSelectOnMap: (Plot) -> Unit,
    onBuildOnPlot: (Plot) -> Unit,
    onInspectHouse: (House) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeFilter == PlotFilter.ALL,
                onClick = { onFilterChange(PlotFilter.ALL) },
                label = { Text("الكل") },
                modifier = Modifier.testTag("filter_plots_all")
            )
            FilterChip(
                selected = activeFilter == PlotFilter.AVAILABLE,
                onClick = { onFilterChange(PlotFilter.AVAILABLE) },
                label = { Text("متاحة") },
                modifier = Modifier.testTag("filter_plots_available")
            )
            FilterChip(
                selected = activeFilter == PlotFilter.OCCUPIED,
                onClick = { onFilterChange(PlotFilter.OCCUPIED) },
                label = { Text("مشغولة") },
                modifier = Modifier.testTag("filter_plots_occupied")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("admin_plots_list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(plots, key = { it.id }) { plot ->
                val isAvailable = plot.status == PlotStatus.AVAILABLE
                val occupyingHouse = plot.houseId?.let { housesById[it] }
                val buildValid = validatePlot(plot.id) is ValidationResult.Valid

                ElevatedCard(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plot_card_${plot.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "قطعة ${plot.id}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الإحداثيات الشبكية: (${plot.coord.x}, ${plot.coord.y})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isAvailable) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = if (isAvailable) "AVAILABLE" else "OCCUPIED",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isAvailable) {
                            Text(
                                text = if (buildValid) "صالحة للبناء وفق SpatialValidator" else "غير صالحة للبناء",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (buildValid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                            )
                        } else if (occupyingHouse != null) {
                            Text(
                                text = "يشغلها البيت: ${occupyingHouse.ownerName} (${occupyingHouse.id})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onSelectOnMap(plot) },
                                modifier = Modifier.testTag("show_plot_map_${plot.id}")
                            ) {
                                Text("تحديد على الخريطة")
                            }

                            if (isAvailable && buildValid) {
                                Button(
                                    onClick = { onBuildOnPlot(plot) },
                                    modifier = Modifier.testTag("build_on_${plot.id}")
                                ) {
                                    Text("بناء بيت هنا")
                                }
                            } else if (occupyingHouse != null) {
                                FilledTonalButton(
                                    onClick = { onInspectHouse(occupyingHouse) },
                                    modifier = Modifier.testTag("inspect_plot_house_${plot.id}")
                                ) {
                                    Text("إدارة البيت")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
