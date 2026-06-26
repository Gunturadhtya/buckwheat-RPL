package com.danilkinkin.buckwheat.wallet

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.MultiBudgetViewModel
import com.danilkinkin.buckwheat.data.entities.BudgetProfile
import com.danilkinkin.buckwheat.ui.colorEditor
import com.danilkinkin.buckwheat.ui.colorOnEditor

/** Drag threshold (px) required to commit a swipe. */
private const val SWIPE_THRESHOLD = 80f

/**
 * A horizontally-swipeable widget that lets the user cycle through all
 * [BudgetProfile] entries.  Swiping left advances to the next profile;
 * swiping right goes back to the previous one.  Both directions wrap
 * cyclically.  Below the content a row of indicator dots shows which
 * profile is currently active.
 *
 * The widget is intentionally lightweight: it does **not** host a
 * `HorizontalPager` to avoid pulling in the full Accompanist / Compose
 * Foundation pager dependency.  Instead it uses a simple drag-gesture
 * detector and animates only the active label + indicators.
 */
@Composable
fun BudgetSwitcher(
    modifier: Modifier = Modifier,
    multiBudgetViewModel: MultiBudgetViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current

    val profiles by multiBudgetViewModel.profiles.observeAsState(emptyList())
    val activeId by multiBudgetViewModel.activeProfileId.observeAsState(null)

    // ── Early-exit if there is nothing to show ───────────────────────────────
    if (profiles.isEmpty()) return

    val activeIndex = profiles.indexOfFirst { it.uid == activeId }.coerceAtLeast(0)

    // ── Swipe gesture state ──────────────────────────────────────────────────
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // ── Rename / delete dialog state ─────────────────────────────────────────
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember(activeId) {
        mutableStateOf(profiles.getOrNull(activeIndex)?.name ?: "")
    }

    // ── Name fade animation on switch ────────────────────────────────────────
    val nameAlpha by animateFloatAsState(
        label = "budgetNameAlpha",
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(profiles, activeIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccumulator < -SWIPE_THRESHOLD -> {
                                // Swipe left → next profile (cyclic)
                                val nextIndex = (activeIndex + 1) % profiles.size
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiBudgetViewModel.switchToProfile(profiles[nextIndex].uid)
                            }
                            dragAccumulator > SWIPE_THRESHOLD -> {
                                // Swipe right → previous profile (cyclic)
                                val prevIndex =
                                    (activeIndex - 1 + profiles.size) % profiles.size
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                multiBudgetViewModel.switchToProfile(profiles[prevIndex].uid)
                            }
                        }
                        dragAccumulator = 0f
                    },
                    onDragCancel = { dragAccumulator = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Active budget name row ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Rename button
            IconButton(
                onClick = {
                    renameText = profiles.getOrNull(activeIndex)?.name ?: ""
                    showRenameDialog = true
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.rename_budget),
                    modifier = Modifier.size(18.dp),
                    tint = colorOnEditor,
                )
            }

            // Budget name
            Text(
                text = profiles.getOrNull(activeIndex)?.name
                    ?: stringResource(R.string.wallet_title),
                style = MaterialTheme.typography.titleMedium,
                color = colorOnEditor,
                modifier = Modifier.alpha(nameAlpha),
            )

            // Add / delete button
            if (profiles.size > 1) {
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.finish_early),
                        modifier = Modifier.size(18.dp),
                        tint = colorOnEditor,
                    )
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Dot indicators ────────────────────────────────────────────────────
        BudgetIndicatorDots(
            count = profiles.size,
            activeIndex = activeIndex,
        )

        Spacer(Modifier.height(4.dp))
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_budget)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.budget_name_hint)) },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = profiles.getOrNull(activeIndex)?.uid ?: return@Button
                        if (renameText.isNotBlank()) {
                            multiBudgetViewModel.renameProfile(uid, renameText.trim())
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ── Delete-confirmation dialog ────────────────────────────────────────────
    if (showDeleteDialog) {
        val profileName = profiles.getOrNull(activeIndex)?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_budget_title)) },
            text = {
                Text(stringResource(R.string.delete_budget_confirmation, profileName))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = profiles.getOrNull(activeIndex)?.uid ?: return@Button
                        multiBudgetViewModel.deleteProfile(uid)
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.finish_early))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * A row of dots indicating the total number of budget profiles and which one is
 * currently active.  The active dot is wider (pill-shaped).
 */
@Composable
private fun BudgetIndicatorDots(
    count: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val isActive = index == activeIndex

            val dotWidth by animateDpAsState(
                label = "dotWidth_$index",
                targetValue = if (isActive) 20.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )

            val dotAlpha by animateFloatAsState(
                label = "dotAlpha_$index",
                targetValue = if (isActive) 1f else 0.4f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )

            Box(
                modifier = Modifier
                    .width(dotWidth)
                    .height(6.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(colorOnEditor),
            )
        }
    }
}