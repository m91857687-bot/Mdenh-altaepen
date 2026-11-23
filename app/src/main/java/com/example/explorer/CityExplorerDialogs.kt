package com.example.explorer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cityengine.model.CitySceneState
import com.example.cityengine.model.DistrictZone

@Composable
fun HouseRequestDialog(
    state: HouseRequestDialogState,
    onDismiss: () -> Unit,
    onSubmit: (applicantName: String, phone: String?, email: String?, notes: String?) -> Unit
) {
    if (!state.isOpen) return

    BackHandler(enabled = !state.isSubmitting) {
        onDismiss()
    }

    var applicantName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        modifier = Modifier.testTag("house_request_dialog"),
        icon = {
            Icon(
                imageVector = if (state.successMessage != null) Icons.Rounded.CheckCircle else Icons.Rounded.AddHome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.request_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.successMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().testTag("request_success_banner")
                    ) {
                        Text(
                            text = state.successMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.request_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = applicantName,
                        onValueChange = { applicantName = it },
                        label = { Text(stringResource(R.string.request_name_label)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("request_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(stringResource(R.string.request_phone_label)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("request_phone_input")
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(stringResource(R.string.request_email_label)) },
                        singleLine = true,
                        enabled = !state.isSubmitting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("request_email_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.request_notes_label)) },
                        minLines = 2,
                        maxLines = 4,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth().testTag("request_notes_input")
                    )
                    if (state.errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().testTag("request_error_banner")
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.successMessage != null) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("request_close_button")
                ) {
                    Text(stringResource(R.string.request_cancel_btn))
                }
            } else {
                Button(
                    onClick = { onSubmit(applicantName, phone, email, notes) },
                    enabled = !state.isSubmitting,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("request_submit_button")
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.request_submitting_label))
                    } else {
                        Text(stringResource(R.string.request_submit_btn))
                    }
                }
            }
        },
        dismissButton = {
            if (state.successMessage == null) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("request_cancel_button")
                ) {
                    Text(stringResource(R.string.request_cancel_btn))
                }
            }
        }
    )
}

@Composable
fun DistrictsBrowserDialog(
    scene: CitySceneState,
    onDismiss: () -> Unit,
    onSelectDistrict: (DistrictZone) -> Unit
) {
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("districts_browser_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationCity, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.districts_browser_title), fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_card_cd))
                }
            }
        },
        text = {
            if (scene.districts.isEmpty()) {
                Text(
                    text = stringResource(R.string.districts_browser_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scene.districts, key = { it.id }) { district ->
                        val plotsCount = scene.plots.count { it.districtId == district.id || district.contains(it.coord) }
                        val housesCount = scene.houses.count { district.contains(it.coord) }
                        val buildings = scene.publicBuildings.filter { it.districtId == district.id || district.contains(it.coord) }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDistrict(district) }
                                .testTag("district_browser_item_${district.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(district.themeColorHex))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(district.nameAr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("(${district.code})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = "${stringResource(R.string.explorer_district_stats_format, plotsCount, buildings.size)} • ${stringResource(R.string.districts_browser_houses_format, housesCount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (buildings.isNotEmpty()) {
                                    Text(
                                        text = buildings.joinToString(" • ") { it.nameAr },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                OutlinedButton(
                                    onClick = { onSelectDistrict(district) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(stringResource(R.string.districts_browser_focus_btn))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text(stringResource(R.string.request_cancel_btn))
            }
        }
    )
}
