package com.example.explorer

import android.text.TextUtils
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.cityengine.CityEngine
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.ui.CityCanvas
import java.util.Locale

@Composable
fun CityExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: CityExplorerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.hasSelection && !uiState.houseRequestState.isOpen && !uiState.isDistrictsBrowserOpen) {
        viewModel.clearSelection()
    }

    val layoutDirection = if (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp
            val scene = uiState.scene

            when {
                uiState.isLoading && scene == null -> {
                    ExplorerLoadingState(modifier = Modifier.align(Alignment.Center))
                }
                uiState.isEmpty && scene == null -> {
                    ExplorerEmptyState(onRetry = viewModel::retryLoad, modifier = Modifier.align(Alignment.Center))
                }
                uiState.isOffline && scene == null -> {
                    ExplorerOfflineState(
                        message = uiState.offlineMessage ?: stringResource(R.string.offline_stale_banner),
                        onRetry = viewModel::retryLoad,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.errorMessage != null && scene == null -> {
                    ExplorerErrorState(message = uiState.errorMessage.orEmpty(), onRetry = viewModel::retryLoad, modifier = Modifier.align(Alignment.Center))
                }
                scene != null -> {
                    val engine = remember(scene.gridWidth, scene.gridHeight) {
                        CityEngine(initialGridWidth = scene.gridWidth, initialGridHeight = scene.gridHeight)
                    }

                    CityCanvas(
                        engine = engine,
                        scene = scene,
                        onHouseTapped = viewModel::onHouseTapped,
                        onPlotTapped = viewModel::onPlotTapped,
                        onBuildingTapped = viewModel::onBuildingTapped,
                        onPropTapped = viewModel::onPropTapped,
                        onRoadTapped = viewModel::onRoadTapped,
                        modifier = Modifier.fillMaxSize()
                    )

                    val subtitle = if (uiState.isTestData) {
                        stringResource(R.string.city_subtitle)
                    } else {
                        stringResource(R.string.city_live_subtitle, scene.houses.size)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExplorerTopBar(
                            subtitle = subtitle,
                            onRecenterClick = {
                                val center = engine.initialFocusWorld(scene)
                                engine.cameraController.centerOnWorld(center.x, center.y, targetZoom = 1.0f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExplorerQuickActionsBar(
                            hasDistricts = scene.districts.isNotEmpty(),
                            onRequestHouseClick = viewModel::openHouseRequestDialog,
                            onOpenDistrictsBrowser = viewModel::openDistrictsBrowser
                        )

                        if (scene.districts.isNotEmpty()) {
                            ExplorerDistrictsRow(
                                districts = scene.districts,
                                selectedDistrictId = uiState.selectedDistrict?.id,
                                onSelectDistrict = { district ->
                                    viewModel.onDistrictSelected(district)
                                    val target = engine.projection.gridToWorld(
                                        district.origin.x + district.widthCells * 0.5f,
                                        district.origin.y + district.heightCells * 0.5f
                                    )
                                    engine.cameraController.centerOnWorld(target.x, target.y, targetZoom = 1.0f)
                                }
                            )
                        }

                        if (uiState.isOffline) {
                            ExplorerOfflineBanner(
                                message = uiState.offlineMessage ?: stringResource(R.string.offline_stale_banner),
                                onRetry = viewModel::retryLoad
                            )
                        }
                    }

                    val cardAlignment = if (isWideScreen) Alignment.BottomStart else Alignment.BottomCenter
                    Box(
                        modifier = Modifier
                            .align(cardAlignment)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = cardAlignment
                    ) {
                        AnimatedVisibility(visible = !uiState.hasSelection, enter = fadeIn(), exit = fadeOut()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                                tonalElevation = 2.dp,
                                modifier = Modifier.testTag("explorer_hint_pill")
                            ) {
                                Text(
                                    text = stringResource(R.string.tap_house_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = uiState.hasSelection,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                        ) {
                            when {
                                uiState.selectedHouse != null -> {
                                    val details = uiState.selectedHouse!!
                                    val houseModel = uiState.selectedHouseSceneModel()
                                    val dist = houseModel?.coord?.let { uiState.districtForCoord(it) }
                                    SelectedHouseCard(
                                        house = details,
                                        designStyle = houseModel?.designStyle ?: HouseDesignStyle.CLASSIC_HOME,
                                        district = dist,
                                        isWideScreen = isWideScreen,
                                        onDismiss = viewModel::clearSelection
                                    )
                                }
                                uiState.selectedBuilding != null -> {
                                    val bld = uiState.selectedBuilding!!
                                    val dist = uiState.districtById(bld.districtId) ?: uiState.districtForCoord(bld.coord)
                                    SelectedPublicBuildingCard(bld, dist, isWideScreen, viewModel::clearSelection)
                                }
                                uiState.selectedProp != null -> {
                                    val prop = uiState.selectedProp!!
                                    SelectedPropCard(prop, uiState.districtForCoord(prop.coord), isWideScreen, viewModel::clearSelection)
                                }
                                uiState.selectedPlot != null -> {
                                    val plot = uiState.selectedPlot!!
                                    val dist = uiState.districtById(plot.districtId) ?: uiState.districtForCoord(plot.coord)
                                    SelectedPlotCard(plot, dist, isWideScreen, viewModel::clearSelection)
                                }
                                uiState.selectedRoad != null || uiState.selectedRoadCoord != null -> {
                                    val c = uiState.selectedRoad?.coord ?: uiState.selectedRoadCoord!!
                                    SelectedRoadCard(uiState.selectedRoad, c, uiState.districtForCoord(c), isWideScreen, viewModel::clearSelection)
                                }
                                uiState.selectedDistrict != null -> {
                                    SelectedDistrictCard(uiState.selectedDistrict!!, scene, isWideScreen, viewModel::clearSelection)
                                }
                            }
                        }
                    }

                    HouseRequestDialog(
                        state = uiState.houseRequestState,
                        onDismiss = viewModel::closeHouseRequestDialog,
                        onSubmit = viewModel::submitHouseRequest
                    )

                    if (uiState.isDistrictsBrowserOpen) {
                        DistrictsBrowserDialog(
                            scene = scene,
                            onDismiss = viewModel::closeDistrictsBrowser,
                            onSelectDistrict = { district ->
                                viewModel.onDistrictSelected(district)
                                val target = engine.projection.gridToWorld(
                                    district.origin.x + district.widthCells * 0.5f,
                                    district.origin.y + district.heightCells * 0.5f
                                )
                                engine.cameraController.centerOnWorld(target.x, target.y, targetZoom = 1.0f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerTopBar(subtitle: String, onRecenterClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.testTag("explorer_header_pill")
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FilledTonalIconButton(
            onClick = onRecenterClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("recenter_camera_button")
        ) {
            Icon(Icons.Rounded.CenterFocusStrong, contentDescription = stringResource(R.string.recenter_camera_cd))
        }
    }
}

@Composable
private fun ExplorerQuickActionsBar(
    hasDistricts: Boolean,
    onRequestHouseClick: () -> Unit,
    onOpenDistrictsBrowser: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onRequestHouseClick,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_house_request_button")
        ) {
            Icon(Icons.Rounded.AddHome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.explorer_request_house_btn))
        }
        if (hasDistricts) {
            FilledTonalButton(
                onClick = onOpenDistrictsBrowser,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("open_districts_browser_button")
            ) {
                Icon(Icons.Rounded.LocationCity, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.explorer_districts_btn))
            }
        }
    }
}

@Composable
private fun ExplorerDistrictsRow(districts: List<DistrictZone>, selectedDistrictId: String?, onSelectDistrict: (DistrictZone) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("explorer_districts_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        districts.forEach { district ->
            FilterChip(
                selected = district.id == selectedDistrictId,
                onClick = { onSelectDistrict(district) },
                leadingIcon = {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(district.themeColorHex)))
                },
                label = { Text(district.nameAr) },
                modifier = Modifier.testTag("explorer_district_${district.id}")
            )
        }
    }
}

@Composable
private fun ExplorerLoadingState(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.loading_city), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExplorerErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp).testTag("explorer_error_state"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag("explorer_retry_button")) { Text(stringResource(R.string.retry_load)) }
    }
}

@Composable
private fun ExplorerEmptyState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp).testTag("explorer_empty_state"), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(stringResource(R.string.empty_city_message), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry_load)) }
    }
}

@Composable
private fun ExplorerOfflineState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp).testTag("explorer_offline_state"), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry, modifier = Modifier.testTag("explorer_offline_retry_button")) { Text(stringResource(R.string.retry_load)) }
    }
}

@Composable
private fun ExplorerOfflineBanner(message: String, onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f), tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("explorer_offline_banner")) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_load)) }
        }
    }
}
