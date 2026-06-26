package com.danilkinkin.buckwheat.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.data.AppViewModel
import com.danilkinkin.buckwheat.data.MultiBudgetViewModel
import com.danilkinkin.buckwheat.data.SpendsViewModel
import com.danilkinkin.buckwheat.editor.dateTimeEdit.DateTimeEditPill
import com.danilkinkin.buckwheat.editor.tagging.TaggingToolbar
import com.danilkinkin.buckwheat.editor.toolbar.EditorToolbar
import com.danilkinkin.buckwheat.ui.BuckwheatTheme

enum class AnimState { EDITING, COMMIT, IDLE, RESET }

/** Minimum horizontal drag distance (px) required to commit a budget switch. */
private const val BUDGET_SWIPE_THRESHOLD = 80f

@Composable
fun Editor(
    modifier: Modifier = Modifier,
    spendsViewModel: SpendsViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(),
    editorViewModel: EditorViewModel = hiltViewModel(),
    multiBudgetViewModel: MultiBudgetViewModel = hiltViewModel(),
    onOpenHistory: (() -> Unit)? = null,
) {
    val focusController = remember { FocusController() }
    val mode by editorViewModel.mode.observeAsState(EditMode.ADD)
    val haptic = LocalHapticFeedback.current

    val profiles by multiBudgetViewModel.profiles.observeAsState(emptyList())
    val activeId by multiBudgetViewModel.activeProfileId.observeAsState(null)

    // Accumulates horizontal drag distance within a single gesture.
    val dragAccumulator = remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Horizontal swipe to cycle budget profiles.
            // Only active when there are multiple profiles and the vertical
            // TopSheet swipe is not locked by history scroll (lockSwipeable).
            .pointerInput(profiles, activeId) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccumulator.floatValue = 0f
                        // Lock vertical sheet dragging while the user is
                        // swiping horizontally so both axes don't interfere.
                        if (profiles.size > 1) {
                            appViewModel.lockDraggable.value = true
                        }
                    },
                    onDragEnd = {
                        if (profiles.size > 1 && !appViewModel.lockSwipeable.value) {
                            val activeIndex = profiles
                                .indexOfFirst { it.uid == activeId }
                                .coerceAtLeast(0)

                            when {
                                dragAccumulator.floatValue < -BUDGET_SWIPE_THRESHOLD -> {
                                    // Swipe left → next profile (cyclic)
                                    val next = (activeIndex + 1) % profiles.size
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    multiBudgetViewModel.switchToProfile(profiles[next].uid)
                                }
                                dragAccumulator.floatValue > BUDGET_SWIPE_THRESHOLD -> {
                                    // Swipe right → previous profile (cyclic)
                                    val prev = (activeIndex - 1 + profiles.size) % profiles.size
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    multiBudgetViewModel.switchToProfile(profiles[prev].uid)
                                }
                            }
                        }
                        dragAccumulator.floatValue = 0f
                        appViewModel.lockDraggable.value = false
                    },
                    onDragCancel = {
                        dragAccumulator.floatValue = 0f
                        appViewModel.lockDraggable.value = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator.floatValue += dragAmount
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusController.focus() }
        ) {
            EditorToolbar()
            if (mode == EditMode.EDIT) {
                DateTimeEditPill()
            }
            CurrentSpendEditor(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                focusController = focusController,
            )
            TaggingToolbar(editorFocusController = focusController)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview
@Composable
fun EditorPreview() {
    BuckwheatTheme {
        Editor()
    }
}