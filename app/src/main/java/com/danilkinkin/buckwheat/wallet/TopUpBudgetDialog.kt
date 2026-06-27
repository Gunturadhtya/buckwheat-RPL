package com.danilkinkin.buckwheat.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.util.fixedNumberString
import com.danilkinkin.buckwheat.util.tryConvertStringToNumber
import com.danilkinkin.buckwheat.util.join
import com.danilkinkin.buckwheat.util.visualTransformationAsCurrency
import java.math.BigDecimal

/**
 * Dialog for adding money (top-up) to the active budget.
 *
 * @param isLoading When true, the Add button is disabled to prevent double-submit.
 * @param currency The currency to format the input.
 * @param onConfirm Called with the validated [BigDecimal] amount when the user taps Add.
 * @param onDismiss Called when the user cancels or dismisses the dialog.
 */
@Composable
fun TopUpBudgetDialog(
    isLoading: Boolean,
    currency: ExtendCurrency?,
    onConfirm: (BigDecimal) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val quickAddValues = listOf("50000", "100000", "500000", "1000000")

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.add_money_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.add_money_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        val fixed = fixedNumberString(it)
                        inputText = fixed
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
                    visualTransformation = visualTransformationAsCurrency(
                        context,
                        currency ?: ExtendCurrency.none(),
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Add Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickAddValues) { value ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    val current = tryConvertStringToNumber(inputText).join().toBigDecimalOrNull() ?: BigDecimal.ZERO
                                    val added = value.toBigDecimal()
                                    inputText = (current + added).toPlainString()
                                    showError = false
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "+${value.dropLast(3)}k",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            val converted = tryConvertStringToNumber(inputText).join().toBigDecimalOrNull()
                            if (converted != null && converted > BigDecimal.ZERO) {
                                onConfirm(converted)
                            } else {
                                showError = true
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp)
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
                }
            }
        }
    }
}
