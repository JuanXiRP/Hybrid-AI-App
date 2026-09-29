package com.example.hybrid_ai_app.home.presentation.workout.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.home.domain.model.SessionSet
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.presentation.workout.filterDecimal
import com.example.hybrid_ai_app.home.presentation.workout.filterDigits

// Column widths of the set table. The header row uses the same values, and they are what lets the
// whole table fit a 360 dp phone: everything is fixed and narrow except "Previous", which takes
// whatever is left.
val SetColumnWidth = 32.dp
val WeightColumnWidth = 52.dp
val RepsColumnWidth = 42.dp
val TargetRpeColumnWidth = 34.dp
val RpeColumnWidth = 40.dp
val CheckColumnWidth = 40.dp
val ColumnGap = 3.dp

private const val MAX_WEIGHT_LENGTH = 6
private const val MAX_REPS_LENGTH = 3
private const val MAX_RPE_LENGTH = 4

/** What a set row can do. The card builds one per set, already bound to its exercise and set. */
class SetRowActions(
    val onWeightChange: (String) -> Unit,
    val onRepsChange: (String) -> Unit,
    val onRpeChange: (String) -> Unit,
    val onToggleCompleted: () -> Unit,
    val onUsePrevious: () -> Unit,
    val onSetType: (SetType) -> Unit,
    val onWarmupUpToHere: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * One line of the set table: `Set | Previous | kg | Reps | Target RPE | RPE | ✓`.
 *
 * Completing a set strikes its values through and tints the row, the same treatment the old
 * one-card-per-exercise screen gave a finished exercise. Swiping the row towards the start deletes
 * it; the menu on the badge offers the same for anyone who cannot swipe.
 *
 * @param displayNumber the ordinal shown for a normal set; null for a warm-up, drop or failure set,
 * which show their letter instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetRow(
    displayNumber: Int?,
    set: SessionSet,
    previousSet: LoggedSetEntity?,
    actions: SetRowActions,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    // A swipe towards the start deletes the set. The row is asked to go and then put back at once:
    // if the delete is accepted the list no longer contains it and it leaves, and if it was
    // refused (a lapsed trial) it is simply back where it was instead of staying swiped away.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            actions.onRemove()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(id = R.string.session_cd_delete_set),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        SetRowContent(displayNumber, set, previousSet, actions)
    }
}

@Composable
private fun SetRowContent(
    displayNumber: Int?,
    set: SessionSet,
    previousSet: LoggedSetEntity?,
    actions: SetRowActions,
) {
    val rowColor by animateColorAsState(
        if (set.completed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "setRowColor",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowColor)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
    ) {
        SetBadge(displayNumber, set.type, actions)

        PreviousCell(
            previousSet = previousSet,
            enabled = !set.completed,
            onClick = actions.onUsePrevious,
            modifier = Modifier.weight(1f),
        )

        SessionNumberField(
            value = set.weight,
            onValueChange = actions.onWeightChange,
            filter = { filterDecimal(it, MAX_WEIGHT_LENGTH) },
            placeholder = "",
            keyboardType = KeyboardType.Decimal,
            completed = set.completed,
            modifier = Modifier.width(WeightColumnWidth),
        )
        SessionNumberField(
            value = set.reps,
            onValueChange = actions.onRepsChange,
            filter = { filterDigits(it, MAX_REPS_LENGTH) },
            placeholder = set.targetReps,
            keyboardType = KeyboardType.Number,
            completed = set.completed,
            modifier = Modifier.width(RepsColumnWidth),
        )

        // The plan's target RPE. Read-only: it is advice, and the athlete's own number goes in the
        // next column.
        Text(
            text = set.targetRpe.ifBlank { "–" },
            modifier = Modifier.width(TargetRpeColumnWidth),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )

        SessionNumberField(
            value = set.actualRpe,
            onValueChange = actions.onRpeChange,
            filter = { limitRpe(filterDecimal(it, MAX_RPE_LENGTH)) },
            placeholder = "",
            keyboardType = KeyboardType.Decimal,
            completed = set.completed,
            modifier = Modifier.width(RpeColumnWidth),
        )

        CompletionButton(completed = set.completed, onClick = actions.onToggleCompleted)
    }
}

/** An RPE is 1 to 10; anything typed past 10 is ignored rather than left to fail on the backend. */
private fun limitRpe(text: String): String {
    val number = text.replace(',', '.').toDoubleOrNull() ?: return text
    return if (number > 10.0) text.dropLast(1) else text
}

@Composable
private fun SetBadge(displayNumber: Int?, type: SetType, actions: SetRowActions) {
    var menuOpen by remember { mutableStateOf(false) }

    val (label, container) = when (type) {
        SetType.NORMAL -> (displayNumber?.toString() ?: "") to MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        SetType.WARMUP -> "W" to MaterialTheme.colorScheme.tertiaryContainer
        SetType.DROP -> "D" to MaterialTheme.colorScheme.secondaryContainer
        SetType.FAILURE -> "F" to MaterialTheme.colorScheme.errorContainer
    }

    Box(modifier = Modifier.width(SetColumnWidth), contentAlignment = Alignment.Center) {
        val changeTypeCd = stringResource(id = R.string.session_cd_set_type)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .clickable(onClickLabel = changeTypeCd) { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.session_set_type_normal)) },
                onClick = {
                    menuOpen = false
                    actions.onSetType(SetType.NORMAL)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.session_set_type_warmup)) },
                onClick = {
                    menuOpen = false
                    actions.onWarmupUpToHere()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.session_set_type_drop)) },
                onClick = {
                    menuOpen = false
                    actions.onSetType(SetType.DROP)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.session_set_type_failure)) },
                onClick = {
                    menuOpen = false
                    actions.onSetType(SetType.FAILURE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(id = R.string.session_set_remove), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    actions.onRemove()
                },
            )
        }
    }
}

@Composable
private fun PreviousCell(
    previousSet: LoggedSetEntity?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPrevious = previousSet != null && previousSet.weight.isNotBlank()
    val useCd = stringResource(id = R.string.session_cd_use_previous)

    Box(
        modifier = modifier
            .height(36.dp)
            .then(if (hasPrevious && enabled) Modifier.clickable(onClickLabel = useCd, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasPrevious) previousSummary(previousSet!!) else "–",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hasPrevious) 0.8f else 0.4f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** `100 × 5`, or just `100` when the reps were not recorded. */
internal fun previousSummary(set: LoggedSetEntity): String = if (set.reps.isBlank()) set.weight else "${set.weight} × ${set.reps}"

@Composable
private fun CompletionButton(completed: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.width(CheckColumnWidth), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (completed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    },
                ),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.session_cd_toggle_set),
                tint = if (completed) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
        }
    }
}

/**
 * A compact numeric input for the set table.
 *
 * It keeps its own text state and only *reports* edits (after [filter] has removed anything that
 * cannot be part of the number): routing every keystroke through the ViewModel and back before
 * showing it makes a `BasicTextField` drop characters and jump the cursor, because the new value
 * can arrive a frame after the one being typed. The external value is adopted only when it
 * genuinely differs from what is on screen (the "use previous" shortcut, an exercise replaced).
 */
@Composable
fun SessionNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    filter: (String) -> String,
    placeholder: String,
    keyboardType: KeyboardType,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    // What was reported and has not come back yet. The ViewModel echoes each edit a frame or so
    // later; adopting that echo while the next keystroke is already on screen would rewind the
    // field, so an incoming value that matches something reported is recognised and ignored.
    val reported = remember { ArrayDeque<String>() }

    LaunchedEffect(value) {
        val echoOf = reported.indexOf(value)
        if (echoOf >= 0) {
            repeat(echoOf + 1) { reported.removeFirst() }
        } else if (field.text != value) {
            reported.clear()
            field = TextFieldValue(value, TextRange(value.length))
        }
    }

    val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (completed) 0.5f else 1f)
    val textStyle = TextStyle(
        color = contentColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
    )

    BasicTextField(
        value = field,
        onValueChange = { edited ->
            val filtered = filter(edited.text)
            field = if (filtered == edited.text) edited else TextFieldValue(filtered, TextRange(filtered.length))
            if (filtered != value) {
                reported.addLast(filtered)
                onValueChange(filtered)
            }
        },
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (field.text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(fontWeight = FontWeight.Normal, textDecoration = TextDecoration.None),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}
