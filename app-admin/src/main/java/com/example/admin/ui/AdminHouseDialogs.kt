package com.example.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cityengine.model.House
import com.example.cityengine.model.HousePalette
import com.example.cityengine.model.Plot
import com.example.cityengine.model.PlotStatus
import com.example.cityengine.world.StarterCityMap
import com.example.core.data.model.AdminHouseDraft
import com.example.core.data.model.HouseResidentSourceRecord

@Composable
fun AddOrEditHouseDialog(
    editingHouse: House?,
    editingRecord: HouseResidentSourceRecord?,
    availablePlots: List<Plot>,
    preselectedPlotId: String?,
    onDismiss: () -> Unit,
    onConfirm: (AdminHouseDraft) -> Unit
) {
    val isEditing = editingHouse != null
    val initialPlotId = editingHouse?.plotId
        ?: preselectedPlotId?.takeIf { id -> availablePlots.any { it.id == id } }
        ?: availablePlots.firstOrNull()?.id.orEmpty()

    var selectedPlotId by remember(initialPlotId) { mutableStateOf(initialPlotId) }
    var ownerName by remember(editingHouse) { mutableStateOf(editingHouse?.ownerName.orEmpty()) }
    var familyCountText by remember(editingHouse) {
        mutableStateOf((editingHouse?.familyMembersCount ?: 4).toString())
    }
    var selectedFlag by remember(editingHouse) {
        mutableStateOf(editingHouse?.flag ?: StarterCityMap.flagPresets.first())
    }
    var selectedPalette by remember(editingHouse) {
        mutableStateOf(editingHouse?.palette ?: StarterCityMap.palettePresets.first().palette)
    }
    var publicRole by remember(editingRecord) {
        mutableStateOf(editingRecord?.publicNeighborhoodRole.orEmpty())
    }
    var publicAbout by remember(editingRecord) {
        mutableStateOf(editingRecord?.publicAbout.orEmpty())
    }
    var phoneNumber by remember(editingRecord) {
        mutableStateOf(editingRecord?.rawPhoneNumber.orEmpty())
    }
    var email by remember(editingRecord) {
        mutableStateOf(editingRecord?.rawEmail.orEmpty())
    }
    var additionalPhone by remember(editingRecord) {
        mutableStateOf(editingRecord?.rawAdditionalPhone.orEmpty())
    }
    var privateNotes by remember(editingRecord) {
        mutableStateOf(editingRecord?.privateNotes.orEmpty())
    }
    var allowPhonePublic by remember(editingRecord) {
        mutableStateOf(editingRecord?.allowPhonePublic ?: false)
    }
    var allowEmailPublic by remember(editingRecord) {
        mutableStateOf(editingRecord?.allowEmailPublic ?: false)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "تعديل بيانات البيت" else "إضافة بيت جديد",
                style = MaterialTheme.typography.titleMedium,
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
                if (!isEditing) {
                    Text(
                        text = "اختيار قطعة أرض متاحة:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (availablePlots.isEmpty()) {
                        Text(
                            text = "لا توجد أراضٍ متاحة حاليًا. احذف بيتًا أو حرّر قطعة أرض أولاً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availablePlots.forEach { plot ->
                                FilterChip(
                                    selected = selectedPlotId == plot.id,
                                    onClick = { selectedPlotId = plot.id },
                                    label = { Text("${plot.id} (${plot.coord.x},${plot.coord.y})") },
                                    modifier = Modifier.testTag("pick_plot_${plot.id}")
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "الموقع الثابت: ${editingHouse.plotId} (${editingHouse.coord.x}, ${editingHouse.coord.y})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it; validationError = null },
                    label = { Text("اسم صاحب البيت") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_owner_name")
                )

                OutlinedTextField(
                    value = familyCountText,
                    onValueChange = { familyCountText = it.filter { ch -> ch.isDigit() }; validationError = null },
                    label = { Text("عدد أفراد الأسرة") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_family_count")
                )

                Text(
                    text = "لون البيت:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StarterCityMap.palettePresets.forEach { preset ->
                        val isSelected = selectedPalette == preset.palette
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(preset.palette.roofLeftHex))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { selectedPalette = preset.palette }
                                .testTag("palette_swatch_${preset.key}")
                        )
                    }
                }

                Text(
                    text = "الدولة والعلم:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StarterCityMap.flagPresets.forEach { flag ->
                        FilterChip(
                            selected = selectedFlag.countryCode == flag.countryCode,
                            onClick = { selectedFlag = flag },
                            label = { Text(flag.countryName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = publicRole,
                    onValueChange = { publicRole = it },
                    label = { Text("صفة عامة اختيارية (للزوار)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = publicAbout,
                    onValueChange = { publicAbout = it },
                    label = { Text("نبذة عامة اختيارية") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "بيانات التواصل الخاصة وموافقات النشر:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("رقم الهاتف (خاص افتراضيًا)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_private_phone")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "موافقة صريحة على نشر الهاتف",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowPhonePublic,
                        onCheckedChange = { allowPhonePublic = it },
                        modifier = Modifier.testTag("switch_allow_phone_public")
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("البريد الإلكتروني (خاص افتراضيًا)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_private_email")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "موافقة صريحة على نشر البريد",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = allowEmailPublic,
                        onCheckedChange = { allowEmailPublic = it },
                        modifier = Modifier.testTag("switch_allow_email_public")
                    )
                }

                OutlinedTextField(
                    value = additionalPhone,
                    onValueChange = { additionalPhone = it },
                    label = { Text("هاتف إضافي (خاص دائمًا)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = privateNotes,
                    onValueChange = { privateNotes = it },
                    label = { Text("ملاحظات إدارية خاصة") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                validationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val count = familyCountText.toIntOrNull() ?: 0
                    if (!isEditing && selectedPlotId.isBlank()) {
                        validationError = "يرجى اختيار قطعة أرض متاحة."
                    } else if (ownerName.trim().isEmpty()) {
                        validationError = "يرجى إدخال اسم صاحب البيت."
                    } else if (count <= 0) {
                        validationError = "يرجى إدخال عدد أفراد صحيح."
                    } else {
                        onConfirm(
                            AdminHouseDraft(
                                targetPlotId = selectedPlotId,
                                ownerName = ownerName.trim(),
                                familyMembersCount = count,
                                flag = selectedFlag,
                                palette = selectedPalette,
                                publicNeighborhoodRole = publicRole.trim().ifEmpty { null },
                                publicAbout = publicAbout.trim().ifEmpty { null },
                                phoneNumber = phoneNumber.trim().ifEmpty { null },
                                email = email.trim().ifEmpty { null },
                                additionalPhone = additionalPhone.trim().ifEmpty { null },
                                privateNotes = privateNotes.trim().ifEmpty { null },
                                allowPhonePublic = allowPhonePublic,
                                allowEmailPublic = allowEmailPublic
                            )
                        )
                    }
                },
                enabled = isEditing || availablePlots.isNotEmpty(),
                modifier = Modifier.testTag("confirm_house_dialog_button")
            ) {
                Text(if (isEditing) "حفظ التعديل" else "بناء البيت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun ChangeColorDialog(
    house: House,
    onDismiss: () -> Unit,
    onSelectColor: (HousePalette) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير لون بيت ${house.ownerName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StarterCityMap.palettePresets.forEach { preset ->
                    val selected = house.palette == preset.palette
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectColor(preset.palette) }
                            .testTag("color_option_${preset.key}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(preset.palette.roofLeftHex))
                                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = preset.labelAr,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun MoveHouseDialog(
    house: House,
    allPlots: List<Plot>,
    onDismiss: () -> Unit,
    onConfirmMove: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل بيت ${house.ownerName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "القطعة الحالية: ${house.plotId} (${house.coord.x}, ${house.coord.y})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                allPlots.forEach { plot ->
                    val isAvailable = plot.status == PlotStatus.AVAILABLE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAvailable) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable(enabled = isAvailable) { onConfirmMove(plot.id) }
                            .testTag("move_target_${plot.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${plot.id} (${plot.coord.x}, ${plot.coord.y})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isAvailable) "متاحة • انقل هنا" else "مشغولة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun DeleteHouseConfirmDialog(
    house: House,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تأكيد حذف البيت", fontWeight = FontWeight.Bold) },
        text = {
            Text("هل تريد حذف بيت ${house.ownerName} من القطعة ${house.plotId}؟ ستعود القطعة إلى حالة متاحة (AVAILABLE).")
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_house_button")
            ) {
                Text("حذف البيت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("تراجع") }
        }
    )
}
