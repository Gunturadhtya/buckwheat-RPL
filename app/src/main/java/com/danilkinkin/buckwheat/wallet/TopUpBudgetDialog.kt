package com.danilkinkin.buckwheat.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.R
import java.math.BigDecimal

/**
 * Parses a user-entered string into a [BigDecimal] amount.
 *
 * Rules (per implementation plan):
 * - If input contains "-" → invalid (null).
 * - If input contains "," → invalid (null). Prevents "100.000,50" being mis-parsed as 10000050.
 * - Strips "Rp", ".", and spaces (dot is a thousands separator in Rupiah).
 * - If the cleaned string is empty or not a valid number → invalid (null).
 * - If the resulting number is <= 0 → invalid (null).
 */
fun parseTopUpAmount(input: String): BigDecimal? {
    // Reject minus sign before stripping anything
    if (input.contains("-")) return null
    // Reject comma (decimal separator) to avoid dangerous mis-parse
    if (input.contains(",")) return null

    val cleaned = input
        .replace("Rp", "", ignoreCase = true)
        .replace(".", "")
        .replace(" ", "")
        .trim()

    if (cleaned.isEmpty()) return null

    val amount = cleaned.toBigDecimalOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null

    return amount
}

/**
 * Dialog for adding money (top-up) to the active budget.
 *
 * @param isLoading When true, the Add button is disabled to prevent double-submit.
 * @param onConfirm Called with the validated [BigDecimal] amount when the user taps Add.
 * @param onDismiss Called when the user cancels or dismisses the dialog.
 */
@Composable
fun TopUpBudgetDialog(
    isLoading: Boolean,
    onConfirm: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.add_money_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_money_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        showError = false
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.add_money_input_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.add_money_error_invalid)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parseTopUpAmount(inputText)
                    if (amount != null) {
                        onConfirm(amount)
                    } else {
                        showError = true
                    }
                },
                enabled = !isLoading && inputText.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.add_money_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
