package com.danilkinkin.buckwheat.di

import kotlinx.coroutines.sync.Mutex

/**
 * Application-scoped [Mutex] that serialises every write to [budgetDataStore].
 *
 * ## Why this exists
 *
 * Two coroutines can race at startup:
 *
 * 1. `SpendsViewModel.runChangeDayAction()` → calls `SpendsRepository.setDailyBudget()`,
 *    which calls `budgetDataStore.edit { … }`.
 * 2. `MultiBudgetViewModel` user action → calls
 *    `MultiBudgetRepository.persistCurrentStateToActiveProfile()`, which does
 *    `budgetDataStore.data.first()` (snapshot) and then `budgetProfileDao.update(…)`.
 *
 * If the DataStore write from (1) lands *between* the `data.first()` snapshot and
 * the `budgetProfileDao.update()` call in (2), the profile row is saved with
 * pre-redistribution numbers while DataStore already holds the post-redistribution
 * state. The two sources of truth diverge silently.
 *
 * Wrapping every `budgetDataStore.edit { }` call **and** the full
 * snapshot→DB-write sequence inside `persistCurrentStateToActiveProfile` with
 * this shared mutex ensures those two operations cannot interleave.
 *
 * ## Scope
 *
 * - **Held by:** every `budgetDataStore.edit { }` call site in
 *   [SpendsRepository] and [MultiBudgetRepository].
 * - **Also held across:** the `data.first()` + `budgetProfileDao.update()` pair
 *   in [MultiBudgetRepository.persistCurrentStateToActiveProfile] and the
 *   `data.first()` + `budgetDataStore.edit` pair in
 *   [MultiBudgetRepository.switchToProfile] — so that no write can slip in
 *   between the read and the subsequent DB flush.
 *
 * ## What is NOT held inside the lock
 *
 * - Read-only `budgetDataStore.data.map { }` flows — these are cold flows and
 *   holding a mutex across a cold-flow subscription would deadlock.
 * - DataStore reads that are purely for driving UI (all the `getXxx()` helpers
 *   in [SpendsRepository]).
 * - Room queries that don't touch DataStore at all.
 *
 * ## Provided via Hilt
 *
 * Declared `@Singleton` in [AppModule] so exactly one instance is shared
 * between [SpendsRepository] and [MultiBudgetRepository].
 */
class BudgetMutex {
    val mutex = Mutex()
}