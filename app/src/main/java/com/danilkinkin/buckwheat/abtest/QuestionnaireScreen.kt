package com.danilkinkin.buckwheat.abtest

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.di.AbFeature

const val QUESTIONNAIRE_SHEET = "questionnaire"

// Questionnaire definition — question ID to text.
// IDs match the testing plan document exactly.
private val GENERAL_QUESTIONS = listOf(
    "G1" to "Fitur ini membantu saya menyelesaikan tugas dengan lebih mudah.",
    "G2" to "Fitur ini mempercepat proses yang saya lakukan.",
    "G3" to "Fitur ini mengurangi kebingungan saat menggunakan aplikasi.",
    "G4" to "Fitur ini membuat saya merasa lebih bisa mengontrol budget.",
    "G5" to "Saya merasa fitur ini berguna untuk aplikasi budgeting.",
    "G6" to "Saya puas dengan pengalaman menggunakan fitur ini.",
)

private val SPECIFIC_QUESTIONS = mapOf(
    AbFeature.ADD_MONEY to listOf(
        "AM1" to "Fitur Add Money membantu saya menyesuaikan saldo budget.",
        "AM2" to "Saya lebih mudah memperbarui budget dengan fitur Add Money.",
        "AM3" to "Tanpa fitur Add Money, proses menyesuaikan saldo terasa lebih sulit.",
    ),
    AbFeature.MULTI_CATEGORY to listOf(
        "MC1" to "Multi Category membantu saya membagi budget berdasarkan kebutuhan.",
        "MC2" to "Multi Category membuat pengelolaan pengeluaran lebih jelas.",
        "MC3" to "Multi Category membuat saya merasa lebih terkontrol dalam menggunakan uang.",
    ),
    AbFeature.DATE_RANGE to listOf(
        "DR1" to "Date-Range Picker membantu saya menemukan data pada periode tertentu.",
        "DR2" to "Date-Range Picker membuat analisis pengeluaran lebih mudah.",
        "DR3" to "Date-Range Picker membantu saya memahami pola pengeluaran.",
    ),
)

@Composable
fun QuestionnaireScreen(
    feature: AbFeature,
    appViewModel: AppViewModel = hiltViewModel(),
    onSubmit: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val allQuestions = GENERAL_QUESTIONS + (SPECIFIC_QUESTIONS[feature] ?: emptyList())
    // Scores map: questionId -> selected score (0 = not yet answered)
    val scores = remember { mutableStateMapOf<String, Int>() }
    allQuestions.forEach { (id, _) -> scores.putIfAbsent(id, 0) }

    val allAnswered = allQuestions.all { (id, _) -> (scores[id] ?: 0) > 0 }

    Column(modifier = Modifier
        .verticalScroll(scrollState)
        .padding(16.dp)) {
        Text(
            text = "Kuesioner: ${feature.key.replace('_', ' ').uppercase()}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "Skala: 1 = Sangat tidak setuju · 5 = Sangat setuju",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 20.dp),
        )

        allQuestions.forEach { (id, text) ->
            QuestionRow(
                id = id,
                text = text,
                selected = scores[id] ?: 0,
                onSelect = { scores[id] = it },
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = {
                // Log every answer as a separate Firebase event
                allQuestions.forEach { (id, _) ->
                    val score = scores[id] ?: return@forEach
                    if (score > 0) appViewModel.logSatisfaction(feature, id, score)
                }
                onSubmit()
            },
            enabled = allAnswered,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun QuestionRow(id: String, text: String, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(text = "[$id] $text", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                FilterChip(
                    selected = selected == score,
                    onClick = { onSelect(score) },
                    label = { Text("$score") },
                )
            }
        }
    }
}
