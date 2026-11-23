package com.example.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cityengine.model.DistrictZone
import com.example.cityengine.model.HouseDesignStyle
import com.example.cityengine.ui.ArabFlagBadge
import com.example.core.data.model.PublicHouseDetails

@Composable
fun SelectedHouseCard(
    house: PublicHouseDetails,
    designStyle: HouseDesignStyle,
    district: DistrictZone?,
    isWideScreen: Boolean,
    onDismiss: () -> Unit
) {
    val maxCardWidth = if (isWideScreen) 420.dp else 560.dp
    val headline = house.houseTitle?.takeIf { it.isNotBlank() }
        ?: house.visibleOwnerName
        ?: stringResource(R.string.house_hidden_owner_title)
    val showSecondaryOwner = house.houseTitle != null && house.visibleOwnerName != null

    ElevatedCard(
        modifier = Modifier.widthIn(max = maxCardWidth).fillMaxWidth().testTag("selected_house_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    ArabFlagBadge(
                        flag = house.flag,
                        width = 44.dp,
                        height = 30.dp,
                        modifier = Modifier.testTag("house_flag_badge")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("house_owner_name")
                        )
                        if (showSecondaryOwner) {
                            Text(
                                text = "${stringResource(R.string.house_owner_label)}: ${house.visibleOwnerName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("house_secondary_owner_name")
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (house.isTestData) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp).testTag("test_data_badge")
                                ) {
                                    Text(
                                        stringResource(R.string.phase1_test_badge),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (district != null) {
                                DistrictInlineBadge(district = district, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("close_house_card_button")
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close_card_cd),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoStatTile(
                    icon = Icons.Rounded.Public,
                    label = stringResource(R.string.house_country_label),
                    value = house.countryName,
                    modifier = Modifier.weight(1f).testTag("house_country_value")
                )
                InfoStatTile(
                    icon = Icons.Rounded.HomeWork,
                    label = stringResource(R.string.house_design_style_label),
                    value = designStyleLabel(designStyle),
                    modifier = Modifier.weight(1f).testTag("house_design_style_value")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.fillMaxWidth().testTag("house_public_info_section")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.house_public_info_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (house.hasAdditionalPublicInfo) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            house.publicRole?.let { role ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.testTag("house_public_role")
                                ) {
                                    Text(
                                        role,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            house.publicStatus?.let { statusText ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                    modifier = Modifier.testTag("house_public_status")
                                ) {
                                    Text(
                                        statusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        house.publicAbout?.let { about ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                about,
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
                            stringResource(R.string.house_no_public_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("house_no_public_info")
                        )
                    }
                }
            }

            if (house.hasApprovedContact) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("house_approved_contact_section")) {
                    Text(stringResource(R.string.house_approved_contact_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    house.approvedPhone?.let { ContactRow(Icons.Rounded.Phone, it) }
                    house.approvedEmail?.let { ContactRow(Icons.Rounded.Email, it) }
                }
            }
        }
    }
}

@Composable
private fun designStyleLabel(style: HouseDesignStyle): String = when (style) {
    HouseDesignStyle.CLASSIC_HOME -> stringResource(R.string.house_design_classic)
    HouseDesignStyle.MODERN_VILLA -> stringResource(R.string.house_design_modern)
    HouseDesignStyle.COURTYARD_HOUSE -> stringResource(R.string.house_design_courtyard)
    HouseDesignStyle.TOWNHOME -> stringResource(R.string.house_design_townhome)
    HouseDesignStyle.COTTAGE -> stringResource(R.string.house_design_cottage)
}

@Composable
private fun ContactRow(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InfoStatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
