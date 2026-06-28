package com.danilkinkin.buckwheat.editor.toolbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.base.ButtonRow
import com.danilkinkin.buckwheat.base.Divider
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.PathState
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.editor.EditorViewModel
import com.danilkinkin.buckwheat.analytics.ANALYTICS_SHEET
import com.danilkinkin.buckwheat.onboarding.ON_BOARDING_SHEET
import com.danilkinkin.buckwheat.recalcBudget.RECALCULATE_DAILY_BUDGET_SHEET
import com.danilkinkin.buckwheat.abtest.QUESTIONNAIRE_SHEET
import com.danilkinkin.buckwheat.di.AbFeature
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.countDaysToToday
import com.danilkinkin.buckwheat.util.prettyDate
import java.math.BigDecimal

const val DEBUG_MENU_SHEET = "debugMenu"

@Composable
fun DebugMenu(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding().coerceAtLeast(16.dp)

    val startPeriodDate by spendsViewModel.startPeriodDate.observeAsState()
    val finishPeriodDate by spendsViewModel.finishPeriodDate.observeAsState()
    val lastChangeDailyBudgetDate by spendsViewModel.lastChangeDailyBudgetDate.observeAsState()

    val wholeDays = startPeriodDate?.let { start -> finishPeriodDate?.let { finish -> countDays(finish, start) } } ?: 0
    val restDays = finishPeriodDate?.let { countDaysToToday(it) } ?: 0
    val spentDays = wholeDays - restDays
    val countDaysFromLastChangeDailyBudget = lastChangeDailyBudgetDate?.let { countDaysToToday(it) } ?: 0

    val budget by spendsViewModel.budget.observeAsState(BigDecimal.ZERO)
    val spent by spendsViewModel.spent.observeAsState(BigDecimal.ZERO)
    val spentFromDailyBudget by spendsViewModel.spentFromDailyBudget.observeAsState(BigDecimal.ZERO)
    val howMuchBudgetRest by spendsViewModel.howMuchBudgetRest().observeAsState(BigDecimal.ZERO)

    val isAbAddMoney by appViewModel.isAbAddMoney.observeAsState(false)
    val isAbMultiCategory by appViewModel.isAbMultiCategory.observeAsState(false)
    val isAbDateRange by appViewModel.isAbDateRange.observeAsState(false)
    val respondentId by appViewModel.currentRespondentId.observeAsState("R00")
    var respondentIdInput by remember { mutableStateOf(respondentId) }

    val scrollState = rememberScrollState()

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column(
            modifier = Modifier
                .padding(bottom = navigationBarHeight)
                .verticalScroll(scrollState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Debug menu",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Header("Actions")
            ButtonRow(
                text = "Open daily summary screen",
                iconInset = false,
                onClick = {
                    appViewModel.openSheet(PathState(RECALCULATE_DAILY_BUDGET_SHEET))
                    onClose()
                },
            )
            ButtonRow(
                text = "Open period summary screen",
                iconInset = false,
                onClick = {
                    appViewModel.openSheet(PathState(ANALYTICS_SHEET))
                    onClose()
                },
            )
            ButtonRow(
                text = "Open onboarding screen",
                iconInset = false,
                onClick = {
                    appViewModel.openSheet(PathState(ON_BOARDING_SHEET))
                    onClose()
                },
            )
            ButtonRow(
                text = "Force crash app",
                iconInset = false,
                onClick = {
                    throw Error("Test crash app")
                },
            )
            Header("Debug budget")
            Spacer(Modifier.height(16.dp))
            MonospaceText("Начало ------------- ${startPeriodDate?.let { prettyDate(
                date = it,
                pattern = "dd.MM.yyyy HH:mm:ss",
                simplifyIfToday = false,
            ) }}")
            MonospaceText("Конец -------------- ${finishPeriodDate?.let { prettyDate(
                date = it,
                pattern = "dd.MM.yyyy HH:mm:ss",
                simplifyIfToday = false,
            ) }}")
            MonospaceText("Посл. пересчет ----- ${lastChangeDailyBudgetDate?.let { prettyDate(
                date = it,
                pattern = "dd.MM.yyyy HH:mm:ss",
                simplifyIfToday = false,
            ) }}")
            Spacer(Modifier.height(16.dp))


            MonospaceText("Всего дней -------------------- $wholeDays")
            MonospaceText("Прошло дней ------------------- $spentDays")
            MonospaceText("Осталось дней ----------------- $restDays")
            MonospaceText("Дней с последнего пересчета --- $countDaysFromLastChangeDailyBudget")
            Spacer(Modifier.height(16.dp))


            MonospaceText("Весь бюджет ------------------- $budget")
            MonospaceText("Потрачено из бюджета ---------- ${spent + spentFromDailyBudget}")
            MonospaceText("Оставшийся бюджет ------------- $howMuchBudgetRest")
            Spacer(Modifier.height(16.dp))


            val dailyBudget = spendsViewModel.dailyBudget.value!!
            val currentSpent = editorViewModel.currentSpent

            val restTodayBudget = dailyBudget - spentFromDailyBudget - currentSpent

            MonospaceText("Бюджет на сегодня ------------- $dailyBudget")
            MonospaceText("Потрачено из дн. бюджета ------ $spentFromDailyBudget")
            MonospaceText("Текущяя трата ----------------- $currentSpent")
            MonospaceText("Осталось на сегодня ----------- $restTodayBudget")
            Spacer(Modifier.height(16.dp))

            // ── A/B Testing Controls ─────────────────────────────────────────────
            Header("A/B Testing")
            Spacer(Modifier.height(12.dp))
            // Respondent ID setter
            Text(
                text = "Respondent ID",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = respondentIdInput,
                    onValueChange = { respondentIdInput = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("e.g. R01") },
                )
                Button(onClick = { appViewModel.setRespondentId(respondentIdInput.trim()) }) {
                    Text("Set")
                }
            }
            Spacer(Modifier.height(4.dp))
            // Current variant display & Manual Overrides
            Text(
                text = "Active variants (Blue = forced locally)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            AbVariantOverrideRow(
                label = "Add Money",
                isB = isAbAddMoney,
                onSetA = { appViewModel.setAbOverride(AbFeature.ADD_MONEY, "A") },
                onSetB = { appViewModel.setAbOverride(AbFeature.ADD_MONEY, "B") }
            )
            AbVariantOverrideRow(
                label = "Multi Category",
                isB = isAbMultiCategory,
                onSetA = { appViewModel.setAbOverride(AbFeature.MULTI_CATEGORY, "A") },
                onSetB = { appViewModel.setAbOverride(AbFeature.MULTI_CATEGORY, "B") }
            )
            AbVariantOverrideRow(
                label = "Date-Range Picker",
                isB = isAbDateRange,
                onSetA = { appViewModel.setAbOverride(AbFeature.DATE_RANGE, "A") },
                onSetB = { appViewModel.setAbOverride(AbFeature.DATE_RANGE, "B") }
            )
            Spacer(Modifier.height(16.dp))

            // Task control for each feature
            listOf(
                "Add Money" to AbFeature.ADD_MONEY,
                "Multi Category" to AbFeature.MULTI_CATEGORY,
                "Date-Range" to AbFeature.DATE_RANGE,
            ).forEach { (label, feature) ->
                val isActive = appViewModel.activeTask.value == feature
                Header(label)
                
                Button(
                    onClick = { appViewModel.startTask(feature) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    colors = if (isActive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isActive) "● TASK ACTIVE ($label)" else "Start task ($label)")
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { if (isActive) appViewModel.errorCount.value++ },
                        modifier = Modifier.weight(1f),
                        enabled = isActive
                    ) { Text("+Error (now: ${if (isActive) appViewModel.errorCount.value else 0})") }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            appViewModel.completeTask(feature, success = true)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isActive,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("✓ Success") }
                    OutlinedButton(
                        onClick = {
                            appViewModel.completeTask(feature, success = false)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isActive
                    ) { Text("✗ Fail") }
                }

                ButtonRow(
                    text = "Open questionnaire ($label)",
                    iconInset = false,
                    onClick = {
                        appViewModel.openSheet(
                            PathState(
                                QUESTIONNAIRE_SHEET,
                                mapOf("feature" to feature)
                            )
                        )
                        onClose()
                    }
                )
            }

            // ── Data Export ─────────────────────────────────────────────
            val context = LocalContext.current
            Header("Study Data Export")
            ButtonRow(
                text = "Export results to CSV",
                iconInset = false,
                onClick = { appViewModel.exportTestData(context) }
            )
            ButtonRow(
                text = "Clear all testing data",
                iconInset = false,
                onClick = { appViewModel.clearTestData() }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AbVariantOverrideRow(
    label: String,
    isB: Boolean,
    onSetA: () -> Unit,
    onSetB: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !isB,
                onClick = onSetA,
                label = { Text("A") }
            )
            FilterChip(
                selected = isB,
                onClick = onSetB,
                label = { Text("B") }
            )
        }
    }
}

@Composable
private fun AbVariantRow(label: String, isB: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = if (isB) "Variant B" else "Variant A",
            style = MaterialTheme.typography.bodyMedium,
            // Green for B, muted for A
            color = if (isB) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
        )
    }
}

@Composable
fun Header(title: String) {
    Divider()
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp, 0.dp),
    )
}

@Composable
fun MonospaceText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp, 0.dp),
        fontFamily = FontFamily.Monospace
    )
}

@Preview
@Composable
private fun PreviewDefault() {
    BuckwheatTheme {
        DebugMenu()
    }
}