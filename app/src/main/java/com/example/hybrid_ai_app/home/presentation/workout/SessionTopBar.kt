package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import java.text.DateFormat
import java.util.Date

/**
 * The session header: title with a live stopwatch (or, when editing, the start date), a way out,
 * Finish (Save when editing) and an overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionTopBar(
    session: WorkoutSession,
    isEdit: Boolean,
    nowMillis: Long,
    onLeave: () -> Unit,
    onFinish: () -> Unit,
    onWorkoutNote: () -> Unit,
    onChangeStart: () -> Unit,
    onDiscard: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            // Both leave the screen; the difference is what they promise. Minimizing keeps the live
            // session running, closing an edit drops the unsaved changes.
            IconButton(onClick = onLeave) {
                Icon(
                    imageVector = if (isEdit) Icons.Default.Close else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(id = if (isEdit) R.string.session_close_edit else R.string.session_minimize),
                )
            }
        },
        title = {
            Column {
                Text(
                    text = session.title.ifBlank {
                        stringResource(id = if (session.isExtra) R.string.extra_strength_title else R.string.history_default_title)
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isEdit) {
                        stringResource(
                            id = R.string.session_edit_started,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(session.startedAt)),
                        )
                    } else {
                        formatClock((nowMillis - session.startedAt) / 1000)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            Button(onClick = onFinish, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text(
                    text = stringResource(id = if (isEdit) R.string.session_save else R.string.session_finish),
                    fontWeight = FontWeight.Bold,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.session_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(id = R.string.session_menu_note)) },
                        onClick = {
                            menuOpen = false
                            onWorkoutNote()
                        },
                    )
                    if (isEdit) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.session_menu_change_start)) },
                            onClick = {
                                menuOpen = false
                                onChangeStart()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.session_menu_discard), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                onDiscard()
                            },
                        )
                    }
                }
            }
        },
    )
}
