package com.example.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.admin.AdminTab
import com.example.admin.AdminViewModel
import com.example.admin.R
import com.example.cityengine.CityEngine
import com.example.cityengine.model.House
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.supabase.AdminAuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAppScreen(
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var preselectedPlotForAdd by remember { mutableStateOf<String?>(null) }
    var editingHouse by remember { mutableStateOf<House?>(null) }
    var colorHouse by remember { mutableStateOf<House?>(null) }
    var movingHouse by remember { mutableStateOf<House?>(null) }
    var deletingHouse by remember { mutableStateOf<House?>(null) }

    BackHandler(enabled = uiState.activeTab != AdminTab.DASHBOARD) {
        viewModel.selectTab(AdminTab.DASHBOARD)
    }

    LaunchedEffect(uiState.statusFeedback) {
        val msg = uiState.statusFeedback
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val authenticatedAdmin = uiState.authState as? AdminAuthState.AuthenticatedAdmin
        if (authenticatedAdmin == null) {
            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.admin_header_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("admin_top_title")
                            )
                        }
                    )
                }
            ) { innerPadding ->
                AdminLoginScreen(
                    authState = uiState.authState,
                    onSignIn = viewModel::signIn,
                    onRetrySession = viewModel::retrySessionRestore,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            return@CompositionLocalProvider
        }

        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.admin_header_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("admin_top_title")
                            )
                        },
                        actions = {
                            if (uiState.requiresAuth) {
                                IconButton(
                                    onClick = viewModel::signOut,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                                        contentDescription = stringResource(R.string.admin_sign_out_label)
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            AdminTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = uiState.activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = null) },
                                    label = { Text(stringResource(tabLabelRes(tab))) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail {
                            AdminTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = uiState.activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = null) },
                                    label = { Text(stringResource(tabLabelRes(tab))) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    val snapshot = uiState.snapshot
                    val scene = uiState.sceneWithSelection
                    when {
                        uiState.isLoading && snapshot == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        uiState.isEmpty && snapshot == null -> {
                            AdminStatusRetryState(
                                message = "لا توجد بيانات مدينة مهيأة على الخادم بعد.",
                                isOffline = false,
                                onRetry = viewModel::retryLoad
                            )
                        }
                        uiState.errorMessage != null && snapshot == null -> {
                            AdminStatusRetryState(
                                message = uiState.errorMessage.orEmpty(),
                                isOffline = uiState.isOffline,
                                onRetry = viewModel::retryLoad
                            )
                        }
                        snapshot != null && scene != null -> {
                            val engine = remember(scene.gridWidth, scene.gridHeight) {
                                CityEngine(
                                    initialGridWidth = scene.gridWidth,
                                    initialGridHeight = scene.gridHeight
                                )
                            }

                            when (uiState.activeTab) {
                                AdminTab.DASHBOARD -> AdminDashboardScreen(
                                    snapshot = snapshot,
                                    engine = engine,
                                    isOffline = uiState.isOffline,
                                    offlineMessage = uiState.offlineMessage,
                                    adminDisplayLabel = if (uiState.requiresAuth) {
                                        authenticatedAdmin.email.ifBlank { authenticatedAdmin.displayName }
                                    } else {
                                        null
                                    },
                                    onNavigateTab = { viewModel.selectTab(it) },
                                    onOpenAddHouse = {
                                        preselectedPlotForAdd = uiState.availablePlots.firstOrNull()?.id
                                        showAddDialog = true
                                    }
                                )

                                AdminTab.HOUSES -> AdminHousesScreen(
                                    houses = uiState.filteredHouses,
                                    searchQuery = uiState.houseSearchQuery,
                                    canAddMoreHouses = uiState.availablePlots.isNotEmpty(),
                                    onSearchQueryChange = viewModel::updateSearchQuery,
                                    onAddHouseClick = {
                                        preselectedPlotForAdd = uiState.availablePlots.firstOrNull()?.id
                                        showAddDialog = true
                                    },
                                    onEditHouseClick = { editingHouse = it },
                                    onChangeColorClick = { colorHouse = it },
                                    onMoveHouseClick = { movingHouse = it },
                                    onDeleteHouseClick = { deletingHouse = it },
                                    onViewOnMapClick = { viewModel.focusHouseOnMap(it.id) }
                                )

                                AdminTab.PLOTS -> AdminPlotsScreen(
                                    plots = uiState.filteredPlots,
                                    housesById = snapshot.scene.houses.associateBy { it.id },
                                    activeFilter = uiState.plotFilter,
                                    validatePlot = viewModel::validatePlotForBuild,
                                    onFilterChange = viewModel::setPlotFilter,
                                    onSelectOnMap = { viewModel.focusPlotOnMap(it.id) },
                                    onBuildOnPlot = { plot ->
                                        preselectedPlotForAdd = plot.id
                                        showAddDialog = true
                                    },
                                    onInspectHouse = { house -> viewModel.focusHouseOnMap(house.id) }
                                )

                                AdminTab.MAP -> AdminMapScreen(
                                    engine = engine,
                                    scene = scene,
                                    selectedHouse = uiState.selectedHouse,
                                    selectedPlot = uiState.selectedPlot,
                                    onHouseTapped = viewModel::onMapHouseTapped,
                                    onPlotTapped = viewModel::onMapPlotTapped,
                                    onClearSelection = viewModel::clearMapSelection,
                                    onBuildOnPlot = { plot ->
                                        preselectedPlotForAdd = plot.id
                                        showAddDialog = true
                                    },
                                    onEditHouse = { editingHouse = it },
                                    onChangeColor = { colorHouse = it },
                                    onMoveHouse = { movingHouse = it },
                                    onDeleteHouse = { deletingHouse = it }
                                )
                            }
                        }
                    }
                }
            }

            // Dialogs
            if (showAddDialog) {
                AddOrEditHouseDialog(
                    editingHouse = null,
                    editingRecord = null,
                    availablePlots = uiState.availablePlots,
                    preselectedPlotId = preselectedPlotForAdd,
                    onDismiss = { showAddDialog = false },
                    onConfirm = { draft ->
                        viewModel.addHouse(draft) { res ->
                            if (res is AdminOperationResult.Success) showAddDialog = false
                        }
                    }
                )
            }

            editingHouse?.let { house ->
                val record = uiState.snapshot?.recordsByHouseId?.get(house.id)
                AddOrEditHouseDialog(
                    editingHouse = house,
                    editingRecord = record,
                    availablePlots = uiState.availablePlots,
                    preselectedPlotId = house.plotId,
                    onDismiss = { editingHouse = null },
                    onConfirm = { draft ->
                        viewModel.updateHouse(house.id, draft) { res ->
                            if (res is AdminOperationResult.Success) editingHouse = null
                        }
                    }
                )
            }

            colorHouse?.let { house ->
                ChangeColorDialog(
                    house = house,
                    onDismiss = { colorHouse = null },
                    onSelectColor = { palette ->
                        viewModel.changeHouseColor(house.id, palette) { res ->
                            if (res is AdminOperationResult.Success) colorHouse = null
                        }
                    }
                )
            }

            movingHouse?.let { house ->
                MoveHouseDialog(
                    house = house,
                    allPlots = uiState.snapshot?.plots.orEmpty(),
                    onDismiss = { movingHouse = null },
                    onConfirmMove = { targetPlotId ->
                        viewModel.moveHouse(house.id, targetPlotId) { res ->
                            if (res is AdminOperationResult.Success) movingHouse = null
                        }
                    }
                )
            }

            deletingHouse?.let { house ->
                DeleteHouseConfirmDialog(
                    house = house,
                    onDismiss = { deletingHouse = null },
                    onConfirmDelete = {
                        viewModel.deleteHouse(house.id) { res ->
                            if (res is AdminOperationResult.Success) deletingHouse = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AdminStatusRetryState(
    message: String,
    isOffline: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isOffline) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onRetry, modifier = Modifier.testTag("admin_retry_button")) {
                Text("إعادة المحاولة")
            }
        }
    }
}

private fun tabIcon(tab: AdminTab) = when (tab) {
    AdminTab.DASHBOARD -> Icons.Rounded.Dashboard
    AdminTab.HOUSES -> Icons.Rounded.Home
    AdminTab.PLOTS -> Icons.Rounded.Landscape
    AdminTab.MAP -> Icons.Rounded.Map
}

private fun tabLabelRes(tab: AdminTab): Int = when (tab) {
    AdminTab.DASHBOARD -> R.string.tab_dashboard
    AdminTab.HOUSES -> R.string.tab_houses
    AdminTab.PLOTS -> R.string.tab_plots
    AdminTab.MAP -> R.string.tab_map
}
