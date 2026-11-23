package com.example.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Domain
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.AdminTab
import com.example.admin.R
import com.example.cityengine.CityEngine
import com.example.cityengine.ui.CityCanvas
import com.example.core.data.model.AdminCitySnapshot

@Composable
fun AdminDashboardScreen(
    snapshot: AdminCitySnapshot,
    engine: CityEngine,
    isOffline: Boolean = false,
    offlineMessage: String? = null,
    adminDisplayLabel: String? = null,
    pendingRequestsCount: Int = 0,
    onNavigateTab: (AdminTab) -> Unit,
    onOpenAddHouse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val bannerContainerColor = when {
            isOffline -> MaterialTheme.colorScheme.errorContainer
            snapshot.isLocalTestData -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        }
        val bannerIcon = when {
            isOffline -> Icons.Rounded.CloudOff
            snapshot.isLocalTestData -> Icons.Rounded.WarningAmber
            else -> Icons.Rounded.CloudDone
        }
        val baseLiveBanner = stringResource(R.string.admin_live_mode_banner)
        val bannerText = when {
            isOffline -> offlineMessage ?: stringResource(R.string.admin_offline_banner)
            snapshot.isLocalTestData -> stringResource(R.string.admin_local_mode_banner)
            !adminDisplayLabel.isNullOrBlank() -> "$baseLiveBanner ($adminDisplayLabel)"
            else -> baseLiveBanner
        }
        val bannerTag = if (snapshot.isLocalTestData) "admin_local_mode_banner" else "admin_live_mode_banner"

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bannerContainerColor,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(bannerTag)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = if (isOffline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOffline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (!snapshot.isLocalTestData && !snapshot.builderCapabilities.builderTablesAvailable) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth().testTag("dashboard_degraded_server_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.admin_builder_degraded_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2x2 Summary KPI cards (explicitly local test values)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatCard(
                icon = Icons.Rounded.Home,
                label = stringResource(R.string.stat_houses_label),
                value = snapshot.totalHouses.toString(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_total_houses")
            )
            DashboardStatCard(
                icon = Icons.Rounded.CheckCircle,
                label = stringResource(R.string.stat_available_plots_label),
                value = snapshot.availablePlotsCount.toString(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_available_plots")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardStatCard(
                icon = Icons.Rounded.Domain,
                label = stringResource(R.string.stat_occupied_plots_label),
                value = snapshot.occupiedPlotsCount.toString(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_occupied_plots")
            )
            DashboardStatCard(
                icon = Icons.Rounded.Groups,
                label = stringResource(R.string.stat_residents_label),
                value = snapshot.totalKnownResidents.toString(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stat_total_residents")
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth().testTag("dashboard_builder_summary")
        ) {
            Text(
                text = stringResource(
                    R.string.stat_districts_buildings_format,
                    snapshot.scene.districts.size,
                    snapshot.scene.publicBuildings.size,
                    snapshot.scene.gridWidth,
                    snapshot.scene.gridHeight
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }

        // Shortcuts for Houses, Plots & City Builder
        Text(
            text = stringResource(R.string.dashboard_shortcuts_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenAddHouse,
                enabled = snapshot.availablePlotsCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_add_house_button")
            ) {
                Icon(Icons.Rounded.AddHome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dashboard_build_house))
            }
            FilledTonalButton(
                onClick = { onNavigateTab(AdminTab.HOUSES) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_manage_houses_button")
            ) {
                Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dashboard_manage_houses))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { onNavigateTab(AdminTab.PLOTS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_manage_plots_button")
            ) {
                Icon(Icons.Rounded.Landscape, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dashboard_manage_plots))
            }
            FilledTonalButton(
                onClick = { onNavigateTab(AdminTab.MAP) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_open_map_button")
            ) {
                Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dashboard_open_editor))
            }
        }
        FilledTonalButton(
            onClick = { onNavigateTab(AdminTab.REQUESTS) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_requests_button")
        ) {
            Icon(Icons.Rounded.Inbox, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.dashboard_manage_requests, pendingRequestsCount))
        }

        // Interactive live city preview powered by shared :core-city
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_city_preview_card")
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_city_preview_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(onClick = { onNavigateTab(AdminTab.MAP) }) {
                        Text(stringResource(R.string.dashboard_zoom_in))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                ) {
                    CityCanvas(
                        engine = engine,
                        scene = snapshot.scene,
                        onHouseTapped = { house ->
                            if (house != null) onNavigateTab(AdminTab.MAP)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
