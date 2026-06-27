package com.danilkinkin.buckwheat.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.analytics.RestAndSpentBudgetCard
import com.danilkinkin.buckwheat.analytics.WholeBudgetCard
import com.danilkinkin.buckwheat.data.ExtendCurrency
import java.math.BigDecimal
import java.util.Date

@Composable
fun BudgetSummary(
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    onEdit: () -> Unit = {},
) {
    val currency by spendsViewModel.currency.observeAsState(ExtendCurrency.none())

    val wholeBudget by spendsViewModel.budget.observeAsState(BigDecimal.ZERO)
    val startDateState by spendsViewModel.startPeriodDate.observeAsState()
    val finishDateState by spendsViewModel.finishPeriodDate.observeAsState()

    // Guard against a brand-new / empty profile where these values may be null.
    // When any required value is missing we fall back to edit mode via onEdit()
    // so the user is prompted to set up the budget rather than seeing a crash.
    val startDate = startDateState ?: run { onEdit(); return }
    val finishDate = finishDateState ?: run { onEdit(); return }

    Column(Modifier.padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)) {
        RestAndSpentBudgetCard(
            modifier = Modifier,
            bigVariant = true
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            WholeBudgetCard(
                modifier = Modifier.weight(1f),
                bigVariant = false,
                budget = wholeBudget,
                currency = currency,
                startDate = startDate,
                finishDate = finishDate,
            )
            DaysLeftCard(
                startDate = startDate,
                finishDate = finishDate,
            )
        }
        EditButton(onClick = { onEdit() })
    }
}