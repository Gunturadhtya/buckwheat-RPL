package com.danilkinkin.buckwheat.di

import android.os.Bundle
import android.util.Log
import com.danilkinkin.buckwheat.data.dao.AbTestEventDao
import com.danilkinkin.buckwheat.data.entities.AbTestEvent
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enum of the three features under A/B test.
 * [key] matches the Remote Config parameter name exactly.
 */
enum class AbFeature(val key: String) {
    ADD_MONEY("ab_add_money"),
    MULTI_CATEGORY("ab_multi_category"),
    DATE_RANGE("ab_date_range"),
}

/**
 * Single source of truth for A/B variant assignment and event logging.
 *
 * Lifecycle:
 * 1. fetchAndActivate() is called once from MainActivity's LaunchedEffect.
 * 2. _variantsReady emits true — all isVariantBFlow() flows emit their values.
 * 3. AppViewModel exposes the flows as LiveData; composables observe via observeAsState(false).
 * 4. During tasks, composables call logTaskStarted / logTaskCompleted / logSatisfaction.
 */
@Singleton
class AbTestRepository @Inject constructor(
    private val abTestEventDao: AbTestEventDao
) {
    private val remoteConfig = Firebase.remoteConfig
    private val analytics = Firebase.analytics

    // Emits false until fetchAndActivate() completes, then true.
    // All isVariantBFlow() calls wait on this before emitting.
    private val _variantsReady = MutableStateFlow(false)

    // Manual overrides for testing same user with both variants
    private val _overrides = MutableStateFlow<Map<AbFeature, String>>(emptyMap())

    init {
        Log.d("AbTestRepository", "Initializing AbTestRepository")
        // Set defaults synchronously — Remote Config returns these if the
        // network call fails or has not completed yet.
        remoteConfig.setDefaultsAsync(
            mapOf(
                AbFeature.ADD_MONEY.key to "A",
                AbFeature.MULTI_CATEGORY.key to "A",
                AbFeature.DATE_RANGE.key to "A",
            )
        )

        // minimumFetchIntervalInSeconds = 0 disables caching during the study.
        // After the study, restore this to 3600 (default) for production.
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
            }
        )
    }

    /**
     * Fetch latest Remote Config values from Firebase and activate them.
     * Must be called exactly once per app launch, from MainActivity's LaunchedEffect.
     * Suspends until the network call resolves (success or failure).
     * On failure, Remote Config falls back to the defaults set in init {}.
     */
    suspend fun fetchAndActivate() {
        Log.d("AbTestRepository", "Fetching and activating Remote Config...")
        try {
            val result = remoteConfig.fetchAndActivate().await()
            Log.d("AbTestRepository", "Remote Config fetch successful. Updated: $result")
        } catch (e: Exception) {
            // Network unavailable — defaults (Variant A) remain active.
            // Log for debugging; do not crash.
            Log.w("AbTestRepository", "Remote Config fetch failed: ${e.message}")
        } finally {
            // Always mark ready — even on failure the defaults are a valid state.
            _variantsReady.value = true
        }
    }

    fun setRespondentId(id: String) {
        analytics.setUserProperty("respondent_id", id)
        Log.d("AbTestRepository", "Respondent ID set to: $id")
    }

    /** Forces a specific variant locally, bypassing Remote Config. Set to null to clear. */
    fun setOverride(feature: AbFeature, variant: String?) {
        val current = _overrides.value.toMutableMap()
        if (variant == null) current.remove(feature) else current[feature] = variant
        _overrides.value = current
        Log.d("AbTestRepository", "Local override set: ${feature.key} -> $variant")
    }

    /** Returns the raw variant string ("A" or "B") for [feature]. */
    fun getVariant(feature: AbFeature): String {
        return _overrides.value[feature] ?: remoteConfig.getString(feature.key).ifBlank { "A" }
    }

    /**
     * Returns a Flow<Boolean> that emits false until variants are ready,
     * then emits true if the variant for [feature] is "B".
     * Consumed by AppViewModel via .asLiveData().
     */
    fun isVariantBFlow(feature: AbFeature) =
        kotlinx.coroutines.flow.combine(_variantsReady, _overrides) { ready, overrides ->
            val variant = overrides[feature] ?: if (ready) getVariant(feature) else "A"
            variant == "B"
        }

    // ── Event logging ─────────────────────────────────────────────

    /**
     * Log when a respondent begins a feature task.
     * Call this at the moment the respondent is handed the device and
     * the task scenario is read to them.
     */
    fun logTaskStarted(feature: AbFeature, respondentId: String) {
        val variant = getVariant(feature)
        Log.d("AbTestRepository", "Logging event: ab_task_started [feature=${feature.key}, variant=$variant, respondent=$respondentId]")
        
        // Offline persistence
        val event = AbTestEvent(
            eventType = "ab_task_started",
            feature = feature.key,
            variant = variant,
            respondentId = respondentId
        )
        MainScope().launch {
            abTestEventDao.insert(event)
        }

        analytics.logEvent("ab_task_started", Bundle().apply {
            putString("feature", feature.key)
            putString("variant", variant)
            putString("respondent_id", respondentId)
            putLong("timestamp_ms", System.currentTimeMillis())
        })
    }

    /**
     * Log the outcome of a feature task.
     * @param success true if the respondent completed the task goal.
     * @param durationMs elapsed milliseconds from task start to this call.
     * @param errorCount number of wrong taps or confusion moments observed.
     */
    fun logTaskCompleted(
        feature: AbFeature,
        respondentId: String,
        success: Boolean,
        durationMs: Long,
        errorCount: Int,
    ) {
        val variant = getVariant(feature)
        Log.d("AbTestRepository", "Logging event: ab_task_completed [feature=${feature.key}, variant=$variant, respondent=$respondentId, success=$success, duration=${durationMs}ms]")
        
        // Offline persistence
        val event = AbTestEvent(
            eventType = "ab_task_completed",
            feature = feature.key,
            variant = variant,
            respondentId = respondentId,
            success = if (success) 1 else 0,
            durationMs = durationMs,
            errorCount = errorCount
        )
        MainScope().launch {
            abTestEventDao.insert(event)
        }

        analytics.logEvent("ab_task_completed", Bundle().apply {
            putString("feature", feature.key)
            putString("variant", variant)
            putString("respondent_id", respondentId)
            putLong("success", if (success) 1L else 0L)
            putLong("duration_ms", durationMs)
            putLong("error_count", errorCount.toLong())
        })
    }

    /**
     * Log a single questionnaire answer.
     * Call once per question per respondent per feature.
     * @param questionId e.g. "G1", "AM2", "DR3" (matches the questionnaire IDs).
     * @param score integer 1–5.
     */
    fun logSatisfaction(
        feature: AbFeature,
        respondentId: String,
        questionId: String,
        score: Int,
    ) {
        val variant = getVariant(feature)
        Log.d("AbTestRepository", "Logging event: ab_satisfaction [feature=${feature.key}, variant=$variant, respondent=$respondentId, question=$questionId, score=$score]")
        
        // Offline persistence
        val event = AbTestEvent(
            eventType = "ab_satisfaction",
            feature = feature.key,
            variant = variant,
            respondentId = respondentId,
            questionId = questionId,
            score = score
        )
        MainScope().launch {
            abTestEventDao.insert(event)
        }

        analytics.logEvent("ab_satisfaction", Bundle().apply {
            putString("feature", feature.key)
            putString("variant", variant)
            putString("respondent_id", respondentId)
            putString("question_id", questionId)
            putLong("score", score.toLong())
        })
    }

    suspend fun getCSVData(): String {
        val events = abTestEventDao.getAllEvents()
        val header = "Timestamp,RespondentID,Event,Feature,Variant,Success,DurationMs,ErrorCount,QuestionID,Score\n"
        val body = events.joinToString("\n") { e ->
            "${e.timestamp},${e.respondentId},${e.eventType},${e.feature},${e.variant},${e.success ?: ""},${e.durationMs ?: ""},${e.errorCount ?: ""},${e.questionId ?: ""},${e.score ?: ""}"
        }
        return header + body
    }

    suspend fun clearAllData() {
        abTestEventDao.deleteAll()
        Log.d("AbTestRepository", "All local test data cleared.")
    }
}
