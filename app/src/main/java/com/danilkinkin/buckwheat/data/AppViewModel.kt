package com.danilkinkin.buckwheat.data

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.danilkinkin.buckwheat.base.balloon.BalloonController
import com.danilkinkin.buckwheat.di.AbFeature
import com.danilkinkin.buckwheat.di.AbTestRepository
import com.danilkinkin.buckwheat.di.SettingsRepository
import com.danilkinkin.buckwheat.di.TUTORS
import com.danilkinkin.buckwheat.effects.ConfettiController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SystemBarState (
    val statusBarColor: Color,
    val statusBarDarkIcons: Boolean,
    val navigationBarDarkIcons: Boolean,
    val navigationBarColor: Color,
)

data class PathState (
    val name: String,
    val args: Map<String, Any?> = emptyMap(),
    val callback: (result: Map<String, Any?>) -> Unit = {},
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val abTestRepository: AbTestRepository,
) : ViewModel() {

    var _snackbarHostState = SnackbarHostState()
        private set
    fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration =
            if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
        snackbarResult: (SnackbarResult) -> Unit = {},
    ) {
        viewModelScope.launch {
            _snackbarHostState.currentSnackbarData?.dismiss()

            val result = _snackbarHostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = duration,
            )

            snackbarResult(result)
        }
    }

    var confettiController = ConfettiController()
        private set


    var balloonController = BalloonController()
        private set

    var topSheetDown: MutableState<Boolean> = mutableStateOf(false)

    var lockSwipeable: MutableState<Boolean> = mutableStateOf(false)

    var lockDraggable: MutableState<Boolean> = mutableStateOf(false)

    var showSystemKeyboard: MutableState<Boolean> = mutableStateOf(false)

    var statusBarStack: MutableList<() -> SystemBarState> = emptyList<() -> SystemBarState>().toMutableList()

    var sheetStates: MutableLiveData<Map<String, PathState>> = MutableLiveData(emptyMap())

    var isDebug = settingsRepository.isDebug().asLiveData()

    var showSpentCardByDefault = settingsRepository.isShowSpentCardByDefault().asLiveData()

    fun getTutorialStage(name: TUTORS) = settingsRepository.getTutorialStage(name).asLiveData()

    fun setShowSpentCardByDefault(showByDefault: Boolean) {
        viewModelScope.launch {
            settingsRepository.switchShowSpentCardByDefault(showByDefault)
        }
    }

    // ── A/B test — variant flags ─────────────────────────────────────────
    // Each starts as false (Variant A) and recomposes to true if Remote Config
    // assigns Variant B after fetchAndActivate() completes.
    val isAbAddMoney: LiveData<Boolean> =
        abTestRepository.isVariantBFlow(AbFeature.ADD_MONEY).asLiveData()
    val isAbMultiCategory: LiveData<Boolean> =
        abTestRepository.isVariantBFlow(AbFeature.MULTI_CATEGORY).asLiveData()
    val isAbDateRange: LiveData<Boolean> =
        abTestRepository.isVariantBFlow(AbFeature.DATE_RANGE).asLiveData()

    // ── A/B test — current respondent ID ─────────────────────────────────
    // Set by the test facilitator via DebugMenu before each respondent session.
    // Passed to every logEvent call so events can be grouped per respondent.
    var currentRespondentId: MutableLiveData<String> = MutableLiveData("R00")
    fun setRespondentId(id: String) {
        currentRespondentId.value = id
        abTestRepository.setRespondentId(id)
    }

    // ── A/B test — task timer ─────────────────────────────────────────────
    // taskStartMs holds System.currentTimeMillis() when a task begins.
    // ViewModel scope keeps it alive across recompositions.
    private var taskStartMs: Long = 0L
    var activeTask: MutableState<AbFeature?> = mutableStateOf(null)
    var errorCount: MutableState<Int> = mutableStateOf(0)

    fun startTask(feature: AbFeature) {
        taskStartMs = System.currentTimeMillis()
        activeTask.value = feature
        errorCount.value = 0
        abTestRepository.logTaskStarted(
            feature = feature,
            respondentId = currentRespondentId.value ?: "R00",
        )
    }

    fun completeTask(feature: AbFeature, success: Boolean) {
        val duration = System.currentTimeMillis() - taskStartMs
        abTestRepository.logTaskCompleted(
            feature = feature,
            respondentId = currentRespondentId.value ?: "R00",
            success = success,
            durationMs = duration,
            errorCount = errorCount.value,
        )
        activeTask.value = null
    }

    fun logSatisfaction(feature: AbFeature, questionId: String, score: Int) {
        abTestRepository.logSatisfaction(
            feature = feature,
            respondentId = currentRespondentId.value ?: "R00",
            questionId = questionId,
            score = score,
        )
    }

    fun setAbOverride(feature: AbFeature, variant: String?) {
        abTestRepository.setOverride(feature, variant)
    }

    fun exportTestData(context: Context) {
        viewModelScope.launch {
            val csvData = abTestRepository.getCSVData()
            val fileName = "buckwheat_ab_test_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            file.writeText(csvData)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Export A/B Test Data"))
        }
    }

    fun clearTestData() {
        viewModelScope.launch {
            abTestRepository.clearAllData()
        }
    }

    fun setIsDebug(debug: Boolean) {
        viewModelScope.launch {
            settingsRepository.switchDebug(debug)
        }
    }

    fun openSheet(state: PathState) {
        sheetStates.value = sheetStates.value!!.plus(Pair(state.name, state))
    }

    fun closeSheet(name: String) {
        sheetStates.value = sheetStates.value!!.minus(name)
    }

    fun passTutorial(name: TUTORS) {
        viewModelScope.launch {
            settingsRepository.passTutorial(name)
        }
    }

    fun activateTutorial(name: TUTORS) {
        viewModelScope.launch {
            settingsRepository.activateTutorial(name)
        }
    }
}