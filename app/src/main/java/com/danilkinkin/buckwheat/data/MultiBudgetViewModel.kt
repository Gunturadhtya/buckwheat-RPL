package com.danilkinkin.buckwheat.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.danilkinkin.buckwheat.di.MultiBudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MultiBudgetViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val multiBudgetRepository: MultiBudgetRepository,
) : ViewModel() {

    val profiles = multiBudgetRepository.getAllProfiles()

    val activeProfileId = multiBudgetRepository.getActiveProfileId().asLiveData()

    init {
        // Migrate pre-multi-budget installs: if the user already has a budget
        // configured in DataStore but no BudgetProfile rows exist yet (because
        // the profile table was only introduced with multi-budget support),
        // create a default profile row that snapshots the current DataStore
        // state. Without this, the first call to createAndSwitchToProfile()
        // would silently discard the existing budget because
        // persistCurrentStateToActiveProfile() returns early when activeId is null.
        viewModelScope.launch {
            multiBudgetRepository.ensureProfileExists()
        }
    }

    /**
     * Saves the current DataStore state into the active profile row, then
     * switches the active profile to [uid] and loads its values into DataStore.
     */
    fun switchToProfile(uid: Int) {
        viewModelScope.launch {
            multiBudgetRepository.persistCurrentStateToActiveProfile()
            multiBudgetRepository.switchToProfile(uid)
        }
    }

    /**
     * Creates a new blank budget profile with the given [name] and immediately
     * switches to it.
     */
    fun createAndSwitchToProfile(name: String) {
        viewModelScope.launch {
            multiBudgetRepository.persistCurrentStateToActiveProfile()
            val newUid = multiBudgetRepository.createProfile(name)
            multiBudgetRepository.switchToProfile(newUid)
        }
    }

    fun renameProfile(uid: Int, newName: String) {
        viewModelScope.launch {
            multiBudgetRepository.renameProfile(uid, newName)
        }
    }

    fun deleteProfile(uid: Int) {
        viewModelScope.launch {
            multiBudgetRepository.deleteProfile(uid)
        }
    }
}