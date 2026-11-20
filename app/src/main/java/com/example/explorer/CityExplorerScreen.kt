package com.example.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.cityengine.CityEngine
import com.example.cityengine.model.CountryFlagStyle
import com.example.cityengine.ui.CityCanvas
import com.example.core.data.model.PublicHouseDetails

@Composable
fun CityExplorerScreen(
    modifier: Modifier = Modifier,
    viewModel: CityExplorerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.selectedHouse != null) {
        viewModel.clearSelection()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 600.dp
            val scene = uiState.scene

            when {
                uiState.isLoading -> {
                    ExplorerLoadingState(modifier = Modifier.align(Alignment.Center))
                }
                uiState.errorMessage != null && scene == null -> {
                    ExplorerErrorState(
                        message = uiState.errorMessage.orEmpty(),
                        onRetry = { viewModel.retryLoad() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                scene != null -> {
                    val engine = remember(scene.gridWidth, scene.gridHeight) {
                        CityEngine(
                            initialGridWidth = scene.gridWidth,
                            initialGridHeight = scene.gridHeight
                        )
                    }

                    CityCanvas(
                        engine = engine,
                        scene = scene,
                        onHouseTapped = { tappedHouse -> viewModel.onHouseTapped(tappedHouse) },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top floating HUD header (designed to stay compact and leave room for future search slot)
                    ExplorerTopBar(
                        onRecenterClick = {
                            val center = engine.initialFocusWorld(scene)
                            engine.cameraController.centerOnWorld(center.x, center.y, targetZoom = 1.0f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    // Bottom area: calm exploration hint or selected house info card
                    val cardAlignment = if (isWideScreen) Alignment.BottomStart else Alignment.BottomCenter
                    Box(
                        modifier = Modifier
                            .align(cardAlignment)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = cardAlignment
                    ) {
                        AnimatedVisibility(
                            visible = uiState.selectedHouse == null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
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
                            visible = uiState.selectedHouse != null,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                        ) {
                            uiState.selectedHouse?.let { houseDetails ->
                                SelectedHouseCard(
                                    house = houseDetails,
                                    isWideScreen = isWideScreen,
                                    onDismiss = { viewModel.clearSelection() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerTopBar(
    onRecenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.testTag("explorer_header_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.city_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FilledTonalIconButton(
            onClick = onRecenterClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("recenter_camera_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CenterFocusStrong,
                contentDescription = stringResource(R.string.recenter_camera_cd)
            )
        }
    }
}

@Composable
private fun SelectedHouseCard(
    house: PublicHouseDetails,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val maxCardWidth = if (isWideScreen) 420.dp else 560.dp
    val familyDisplay = house.familyMembersCount?.let {
        stringResource(R.string.house_family_count_format, it)
    } ?: stringResource(R.string.value_unavailable)

    ElevatedCard(
        modifier = Modifier
            .widthIn(max = maxCardWidth)
            .fillMaxWidth()
            .testTag("selected_house_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    FlagPreviewBadge(flag = house.flag)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = house.ownerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("house_owner_name")
                        )
                        if (house.isTestData) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .testTag("test_data_badge")
                            ) {
                                Text(
                                    text = stringResource(R.string.phase1_test_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_house_card_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close_card_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoStatTile(
                    icon = Icons.Rounded.Public,
                    label = stringResource(R.string.house_country_label),
                    value = house.countryName,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("house_country_value")
                )
                InfoStatTile(
                    icon = Icons.Rounded.Groups,
                    label = stringResource(R.string.house_family_label),
                    value = familyDisplay,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("house_family_value")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Publishable additional info section (gracefully handles missing/unavailable info)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("house_public_info_section")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.house_public_info_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (house.hasAdditionalPublicInfo) {
                        house.publicRole?.let { role ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.testTag("house_public_role")
                            ) {
                                Text(
                                    text = role,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        house.publicAbout?.let { about ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = about,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("house_public_about")
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.house_no_public_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("house_no_public_info")
                        )
                    }
                }
            }

            // Approved public contact section: strictly rendered ONLY if explicit consent exists
            if (house.hasApprovedContact) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("house_approved_contact_section")
                ) {
                    Text(
                        text = stringResource(R.string.house_approved_contact_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    house.approvedPhone?.let { phone ->
                        ContactRow(icon = Icons.Rounded.Phone, value = phone)
                    }
                    house.approvedEmail?.let { email ->
                        ContactRow(icon = Icons.Rounded.Email, value = email)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FlagPreviewBadge(flag: CountryFlagStyle) {
    Canvas(
        modifier = Modifier
            .size(width = 42.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        val colors = flag.stripeColors
        if (colors.isEmpty()) return@Canvas
        if (flag.isVerticalStripes && colors.size >= 2) {
            val split = size.width * 0.36f
            drawRect(Color(colors[0]), Offset.Zero, Size(split, size.height))
            drawRect(Color(colors[1]), Offset(split, 0f), Size(size.width - split, size.height))
        } else {
            val bandH = size.height / colors.size
            colors.forEachIndexed { idx, hex ->
                drawRect(
                    color = Color(hex),
                    topLeft = Offset(0f, idx * bandH),
                    size = Size(size.width, bandH + 0.5f)
                )
            }
        }
    }
}

@Composable
private fun ExplorerLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.loading_city),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExplorerErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry_load))
        }
    }
}
