package com.danilkinkin.buckwheat.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Date

enum class DateRangePreset(val label: String) {
    ACTIVE_PERIOD("Active Period"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    THIS_YEAR("This Year"),
    CUSTOM("Custom")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilter(
    currentPreset: DateRangePreset,
    onPresetSelected: (DateRangePreset) -> Unit,
    onCustomRangeSelected: (Date?, Date?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustomPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(Icons.Default.DateRange, contentDescription = "Filter Date")
            Spacer(modifier = Modifier.width(8.dp))
            Text(currentPreset.label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DateRangePreset.values().forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        expanded = false
                        if (preset == DateRangePreset.CUSTOM) {
                            showCustomPicker = true
                        } else {
                            onPresetSelected(preset)
                        }
                    }
                )
            }
        }
    }

    if (showCustomPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = {
                showCustomPicker = false
            },
            confirmButton = {
                TextButton(onClick = {
                    showCustomPicker = false
                    val start = state.selectedStartDateMillis?.let { Date(it) }
                    val end = state.selectedEndDateMillis?.let { Date(it) }
                    if (start != null && end != null) {
                        onPresetSelected(DateRangePreset.CUSTOM)
                        onCustomRangeSelected(start, end)
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCustomPicker = false
                }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
