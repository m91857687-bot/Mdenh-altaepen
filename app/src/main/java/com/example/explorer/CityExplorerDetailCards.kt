package com.example.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Signpost
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cityengine.model.ArabCountriesRegistry
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.EnvironmentProp
import com.example.cityengine.model.EnvironmentPropType
import com.example.cityengine.model.GridCoord
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.model.PublicBuilding
import com.example.cityengine.model.RoadSegment
import com.example.cityengine.ui.ArabFlagBadge

@Composable
fun SelectedPublicBuildingCard(
    building: PublicBuilding,
    district: DistrictZone?,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val extraDetails = buildList {
        building.descriptionAr?.takeIf { it.isNotBlank() }?.let { add(it) }
        building.publicInfoAr?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString("\n")
    ReadOnlyElementCard(
        icon = Icons.Rounded.AccountBalance,
        title = building.nameAr,
        badge = building.type.labelAr,
        subtitle = stringResource(R.string.explorer_building_level_format, building.upgradeLevel, building.widthCells, building.heightCells),
        extraInfo = extraDetails.ifBlank { null },
        district = district,
        cardTag = "selected_building_card",
        isWideScreen = isWideScreen,
        onDismiss = onDismiss
    )
}

@Composable
fun SelectedPropCard(
    prop: EnvironmentProp,
    district: DistrictZone?,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val flagSpec = if (prop.type == EnvironmentPropType.CITY_FLAG) {
        val iso = ArabCountriesRegistry.parseCountryCodeFromAssetKey(prop.assetKey)
        ArabCountriesRegistry.findByNameOrCode(iso, prop.labelAr)
    } else null

    val maxCardWidth = if (isWideScreen) 420.dp else 560.dp
    if (flagSpec != null) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth().testTag("selected_prop_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        ArabFlagBadge(flag = flagSpec.flagStyle, width = 44.dp, height = 30.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(flagSpec.nameAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(
                                    stringResource(R.string.explorer_flag_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize().testTag("close_element_card_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_card_cd))
                    }
                }
                if (district != null) DistrictInlineBadge(district = district)
            }
        }
    } else {
        ReadOnlyElementCard(
            icon = if (prop.type == EnvironmentPropType.CITY_FLAG) Icons.Rounded.Flag else Icons.Rounded.Park,
            title = prop.labelAr ?: prop.type.labelAr,
            badge = stringResource(R.string.explorer_prop_badge),
            subtitle = "(${prop.coord.x},${prop.coord.y}) • ${prop.type.labelAr}",
            extraInfo = null,
            district = district,
            cardTag = "selected_prop_card",
            isWideScreen = isWideScreen,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun SelectedRoadCard(
    road: RoadSegment?,
    coord: GridCoord,
    district: DistrictZone?,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val streetTitle = road?.streetNameAr ?: stringResource(R.string.explorer_road_default_name)
    val details = buildList {
        add(stringResource(R.string.explorer_road_lanes_format, road?.lanes ?: 2))
        if (road?.hasCrosswalk == true) add(stringResource(R.string.explorer_road_crosswalk))
        if (road?.hasSidewalkBorder != false) add(stringResource(R.string.explorer_road_sidewalk))
    }.joinToString(" • ")
    ReadOnlyElementCard(
        icon = Icons.Rounded.Signpost,
        title = streetTitle,
        badge = "(${coord.x},${coord.y})",
        subtitle = details,
        extraInfo = null,
        district = district,
        cardTag = "selected_road_card",
        isWideScreen = isWideScreen,
        onDismiss = onDismiss
    )
}

@Composable
fun SelectedPlotCard(
    plot: Plot,
    district: DistrictZone?,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val statusLabel = when (plot.status) {
        PlotStatus.AVAILABLE -> stringResource(R.string.explorer_plot_available)
        PlotStatus.UNAVAILABLE -> stringResource(R.string.explorer_plot_unavailable)
        PlotStatus.OCCUPIED -> stringResource(R.string.explorer_plot_occupied)
    }
    ReadOnlyElementCard(
        icon = Icons.Rounded.Landscape,
        title = plot.plotCode,
        badge = statusLabel,
        subtitle = stringResource(R.string.explorer_plot_dimensions_format, plot.widthCells, plot.heightCells, plot.coord.x, plot.coord.y),
        extraInfo = null,
        district = district,
        cardTag = "selected_plot_card",
        isWideScreen = isWideScreen,
        onDismiss = onDismiss
    )
}

@Composable
fun SelectedDistrictCard(
    district: DistrictZone,
    scene: CitySceneState,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val plotsCount = scene.plots.count { it.districtId == district.id || district.contains(it.coord) }
    val buildings = scene.publicBuildings.filter { it.districtId == district.id || district.contains(it.coord) }
    val facilitiesSummary = if (buildings.isEmpty()) null else buildings.joinToString(" • ") { it.nameAr }
    ReadOnlyElementCard(
        icon = Icons.Rounded.LocationCity,
        title = district.nameAr,
        badge = district.code,
        subtitle = stringResource(R.string.explorer_district_stats_format, plotsCount, buildings.size),
        extraInfo = facilitiesSummary,
        district = null,
        cardTag = "selected_district_card",
        isWideScreen = isWideScreen,
        onDismiss = onDismiss
    )
}

@Composable
private fun ReadOnlyElementCard(
    icon: ImageVector,
    title: String,
    badge: String,
    subtitle: String,
    extraInfo: String?,
    district: DistrictZone?,
    cardTag: String,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val maxCardWidth = if (isWideScreen) 420.dp else 560.dp
    ElevatedCard(
        modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth().testTag(cardTag),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(badge, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize().testTag("close_element_card_button")) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_card_cd))
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!extraInfo.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().testTag("element_extra_public_info")
                ) {
                    Text(
                        text = extraInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (district != null) DistrictInlineBadge(district = district)
        }
    }
}

@Composable
internal fun DistrictInlineBadge(district: DistrictZone, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(district.themeColorHex).copy(alpha = 0.16f),
        modifier = modifier.testTag("element_district_badge")
    ) {
        Text(
            text = "${stringResource(R.string.explorer_district_label)}: ${district.nameAr}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
