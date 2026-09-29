package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.hybrid_ai_app.R
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Finishing with unticked sets: complete them, drop them, or go back. */
@Composable
fun IncompleteSetsDialog(
    count: Int,
    onCompleteAll: () -> Unit,
    onDiscardIncomplete: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.session_incomplete_title)) },
        text = { Text(pluralStringResource(id = R.plurals.session_incomplete_message, count = count, count)) },
        // Three choices do not fit the dialog's two button slots, so they share the confirm slot.
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCompleteAll, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.session_complete_all), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDiscardIncomplete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.session_discard_incomplete))
                }
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.btn_cancel))
                }
            }
        },
    )
}

/** A different day's workout is already running: continue it, or replace it. */
@Composable
fun ConflictDialog(
    existingTitle: String,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.session_conflict_title)) },
        text = { Text(stringResource(id = R.string.session_conflict_message, existingTitle)) },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.session_conflict_resume), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(id = R.string.session_conflict_discard))
                }
            }
        },
    )
}

@Composable
fun DiscardDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.session_discard_title)) },
        text = { Text(stringResource(id = R.string.session_discard_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(id = R.string.session_discard_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    )
}

@Composable
fun RemoveExerciseDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.session_remove_exercise_title)) },
        text = { Text(stringResource(id = R.string.session_remove_exercise_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(id = R.string.session_remove_exercise_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    )
}

/**
 * Step one of changing a past workout's start: the day.
 *
 * The date picker answers with midnight UTC of the chosen day, whatever the time zone, so the
 * result is handed on as that instant and combined with a time in [StartTimePickerDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDatePickerDialog(
    initialMillis: Long,
    onDatePicked: (utcMidnightMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC, so the local date has to be re-expressed as its UTC midnight.
    val localDate = Instant.ofEpochMilli(initialMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val state = rememberDatePickerState(initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onDatePicked) ?: onDismiss() },
            ) { Text(stringResource(id = R.string.session_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    ) {
        DatePicker(state = state, title = { Text(stringResource(id = R.string.session_pick_date_title)) })
    }
}

/** Step two: the time of day. Calls back with the finished instant in epoch milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartTimePickerDialog(
    dateMillis: Long,
    initialMillis: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(initialMillis).atZone(zone).toLocalTime()
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.session_pick_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    val date = Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    val picked = date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant()
                    onPicked(picked.toEpochMilli())
                },
            ) { Text(stringResource(id = R.string.session_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    )
}
