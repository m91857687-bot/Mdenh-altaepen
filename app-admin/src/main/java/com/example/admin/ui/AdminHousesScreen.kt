package com.example.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.R
import com.example.cityengine.model.House

@Composable
fun AdminHousesScreen(
    houses: List<House>,
    searchQuery: String,
    canAddMoreHouses: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onAddHouseClick: () -> Unit,
    onEditHouseClick: (House) -> Unit,
    onChangeColorClick: (House) -> Unit,
    onMoveHouseClick: (House) -> Unit,
    onDeleteHouseClick: (House) -> Unit,
    onViewOnMapClick: (House) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddHouseClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_house_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add_house))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_houses_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("houses_search_input")
            )
            if (!canAddMoreHouses) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "جميع قطع الأراضي الـ 12 مشغولة حاليًا.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("admin_houses_list"),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(houses, key = { it.id }) { house ->
                    AdminHouseItemCard(
                        house = house,
                        onEdit = { onEditHouseClick(house) },
                        onChangeColor = { onChangeColorClick(house) },
                        onMove = { onMoveHouseClick(house) },
                        onDelete = { onDeleteHouseClick(house) },
                        onViewOnMap = { onViewOnMapClick(house) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminHouseItemCard(
    house: House,
    onEdit: () -> Unit,
    onChangeColor: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onViewOnMap: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("house_card_${house.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(house.palette.roofLeftHex))
                            .border(1.5.dp, Color(house.palette.roofTrimHex), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = house.ownerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${house.flag.countryName} • ${house.familyMembersCount} أفراد",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${house.plotId} (${house.coord.x},${house.coord.y})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "مشغول • OCCUPIED",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row {
                    IconButton(
                        onClick = onViewOnMap,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("map_house_${house.id}")
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = stringResource(R.string.action_open_map))
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_house_${house.id}")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit_house))
                    }
                    IconButton(
                        onClick = onChangeColor,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("color_house_${house.id}")
                    ) {
                        Icon(Icons.Rounded.Palette, contentDescription = stringResource(R.string.action_change_color))
                    }
                    IconButton(
                        onClick = onMove,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("move_house_${house.id}")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = stringResource(R.string.action_move_house))
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_house_${house.id}")
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.action_delete_house),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
