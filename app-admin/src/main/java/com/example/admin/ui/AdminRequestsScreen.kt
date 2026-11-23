package com.example.admin.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.admin.R
import com.example.cityengine.model.Plot
import com.example.core.data.model.HouseRequest
import com.example.core.data.model.HouseRequestStatus

@Composable
fun AdminRequestsScreen(
    requests: List<HouseRequest>,
    activeFilter: HouseRequestStatus?,
    availablePlots: List<Plot>,
    onFilterChange: (HouseRequestStatus?) -> Unit,
    onUpdateStatus: (requestId: String, status: HouseRequestStatus, adminNotes: String?, targetPlotId: String?) -> Unit,
    onApproveAndBuildHouse: (HouseRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var managingRequest by remember { mutableStateOf<HouseRequest?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeFilter == null,
                onClick = { onFilterChange(null) },
                label = { Text(stringResource(R.string.filter_all)) },
                modifier = Modifier.testTag("request_filter_all")
            )
            HouseRequestStatus.entries.forEach { status ->
                FilterChip(
                    selected = activeFilter == status,
                    onClick = { onFilterChange(status) },
                    label = { Text(status.labelAr) },
                    modifier = Modifier.testTag("request_filter_${status.name.lowercase()}")
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("requests_empty_state"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.requests_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("admin_requests_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(requests, key = { it.id }) { req ->
                    RequestItemCard(
                        request = req,
                        canBuildHouse = req.assignedHouseId == null &&
                            availablePlots.isNotEmpty() &&
                            req.status != HouseRequestStatus.REJECTED &&
                            req.status != HouseRequestStatus.COMPLETED,
                        onOpenManage = { managingRequest = req },
                        onApproveAndBuild = { onApproveAndBuildHouse(req) }
                    )
                }
            }
        }
    }

    managingRequest?.let { req ->
        ManageHouseRequestDialog(
            request = req,
            availablePlots = availablePlots,
            onDismiss = { managingRequest = null },
            onConfirm = { newStatus, adminNotes, plotId ->
                onUpdateStatus(req.id, newStatus, adminNotes, plotId)
                managingRequest = null
            }
        )
    }
}

@Composable
private fun RequestItemCard(
    request: HouseRequest,
    canBuildHouse: Boolean,
    onOpenManage: () -> Unit,
    onApproveAndBuild: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("request_card_${request.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.applicantName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusContainerColor(request.status)
                ) {
                    Text(
                        text = request.status.labelAr,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusContentColor(request.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!request.phone.isNullOrBlank() || !request.email.isNullOrBlank()) {
                Text(
                    text = listOfNotNull(
                        request.phone?.takeIf { it.isNotBlank() },
                        request.email?.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = request.notes?.takeIf { it.isNotBlank() } ?: stringResource(R.string.requests_no_notes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!request.targetPlotId.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.requests_assigned_plot_format, request.targetPlotId.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!request.assignedHouseId.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.requests_assigned_house_format, request.assignedHouseId.orEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (!request.adminNotes.isNullOrBlank()) {
                Text(
                    text = request.adminNotes.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenManage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("request_update_btn_${request.id}")
                ) {
                    Text(stringResource(R.string.requests_update_status_btn))
                }
                if (canBuildHouse) {
                    Button(
                        onClick = onApproveAndBuild,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("request_build_btn_${request.id}")
                    ) {
                        Text(stringResource(R.string.requests_approve_build_btn))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageHouseRequestDialog(
    request: HouseRequest,
    availablePlots: List<Plot>,
    onDismiss: () -> Unit,
    onConfirm: (HouseRequestStatus, String?, String?) -> Unit
) {
    val allowed = remember(request) {
        (listOf(request.status) + request.status.allowedNextStatuses()).distinct()
    }
    var selectedStatus by remember(request) {
        mutableStateOf(request.status.allowedNextStatuses().firstOrNull() ?: request.status)
    }
    var adminNotes by remember(request) { mutableStateOf(request.adminNotes.orEmpty()) }
    var targetPlotId by remember(request) { mutableStateOf(request.targetPlotId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.requests_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = request.applicantName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowed.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.labelAr) },
                            modifier = Modifier.testTag("dialog_status_${st.name.lowercase()}")
                        )
                    }
                }
                if (availablePlots.isNotEmpty() && request.assignedHouseId == null) {
                    Text(
                        text = stringResource(R.string.requests_target_plot_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = targetPlotId == null,
                            onClick = { targetPlotId = null },
                            label = { Text(stringResource(R.string.editor_no_district)) }
                        )
                        availablePlots.forEach { plot ->
                            FilterChip(
                                selected = targetPlotId == plot.id,
                                onClick = { targetPlotId = plot.id },
                                label = { Text("${plot.plotCode} (${plot.coord.x},${plot.coord.y})") },
                                modifier = Modifier.testTag("dialog_request_plot_${plot.id}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = adminNotes,
                    onValueChange = { adminNotes = it },
                    label = { Text(stringResource(R.string.requests_admin_notes_label)) },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_admin_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        selectedStatus,
                        adminNotes.trim().ifEmpty { null },
                        targetPlotId
                    )
                },
                modifier = Modifier.testTag("confirm_request_status_button")
            ) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        }
    )
}

@Composable
private fun statusContainerColor(status: HouseRequestStatus) = when (status) {
    HouseRequestStatus.NEW -> MaterialTheme.colorScheme.primaryContainer
    HouseRequestStatus.UNDER_REVIEW -> MaterialTheme.colorScheme.secondaryContainer
    HouseRequestStatus.APPROVED, HouseRequestStatus.PREPARING_PLOT, HouseRequestStatus.BUILDING_HOUSE ->
        MaterialTheme.colorScheme.tertiaryContainer
    HouseRequestStatus.COMPLETED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    HouseRequestStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
}

@Composable
private fun statusContentColor(status: HouseRequestStatus) = when (status) {
    HouseRequestStatus.NEW -> MaterialTheme.colorScheme.onPrimaryContainer
    HouseRequestStatus.UNDER_REVIEW -> MaterialTheme.colorScheme.onSecondaryContainer
    HouseRequestStatus.APPROVED, HouseRequestStatus.PREPARING_PLOT, HouseRequestStatus.BUILDING_HOUSE ->
        MaterialTheme.colorScheme.onTertiaryContainer
    HouseRequestStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    HouseRequestStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
}
