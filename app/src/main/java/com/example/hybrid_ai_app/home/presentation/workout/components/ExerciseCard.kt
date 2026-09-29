package com.example.hybrid_ai_app.home.presentation.workout.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.core.domain.model.CatalogExercise
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.presentation.workout.SetField
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/** Everything the card can ask of the screen. Ids are passed back so one instance serves every card. */
class ExerciseCardActions(
    val onUpdateSet: (exerciseId: String, setId: String, field: SetField, value: String) -> Unit,
    val onToggleSet: (exerciseId: String, setId: String) -> Unit,
    val onAddSet: (exerciseId: String) -> Unit,
    val onRemoveSet: (exerciseId: String, setId: String) -> Unit,
    val onSetType: (exerciseId: String, setId: String, type: SetType) -> Unit,
    val onWarmupUpTo: (exerciseId: String, setId: String) -> Unit,
    val onInfo: (exerciseId: String) -> Unit,
    val onAlternative: (exerciseId: String) -> Unit,
    val onRest: (exerciseId: String) -> Unit,
    val onNote: (exerciseId: String) -> Unit,
    val onReorder: () -> Unit,
    val onRemove: (exerciseId: String) -> Unit,
)

/**
 * One exercise: a header (picture, name, menu) over the set table.
 *
 * When every set is ticked the name is struck through and the card takes the completed tint, the
 * same cue the previous one-card-per-exercise screen used.
 */
@Composable
fun ExerciseCard(
    exercise: SessionExercise,
    previousSets: List<LoggedSetEntity>,
    catalogEntry: CatalogExercise?,
    actions: ExerciseCardActions,
    modifier: Modifier = Modifier,
) {
    val allCompleted = exercise.sets.isNotEmpty() && exercise.sets.all { it.completed }

    val containerColor by animateColorAsState(
        if (allCompleted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
        label = "exerciseCardColor",
    )
    val contentColor by animateColorAsState(
        if (allCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "exerciseCardContent",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
            ExerciseHeader(exercise, catalogEntry, allCompleted, contentColor, actions)

            if (exercise.notes.isNotBlank()) {
                Text(
                    text = exercise.notes,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { actions.onNote(exercise.id) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.size(6.dp))
            SetTableHeader()
            Spacer(modifier = Modifier.size(2.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                // Only normal sets are numbered; warm-up, drop and failure sets show their letter.
                var normalCount = 0
                exercise.sets.forEachIndexed { index, set ->
                    val number = if (set.type == SetType.NORMAL) ++normalCount else null

                    key(set.id) {
                        SetRow(
                            displayNumber = number,
                            set = set,
                            previousSet = previousSets.getOrNull(index),
                            actions = SetRowActions(
                                onWeightChange = { actions.onUpdateSet(exercise.id, set.id, SetField.WEIGHT, it) },
                                onRepsChange = { actions.onUpdateSet(exercise.id, set.id, SetField.REPS, it) },
                                onRpeChange = { actions.onUpdateSet(exercise.id, set.id, SetField.ACTUAL_RPE, it) },
                                onToggleCompleted = { actions.onToggleSet(exercise.id, set.id) },
                                onUsePrevious = {
                                    previousSets.getOrNull(index)?.let { previous ->
                                        actions.onUpdateSet(exercise.id, set.id, SetField.WEIGHT, previous.weight)
                                        actions.onUpdateSet(exercise.id, set.id, SetField.REPS, previous.reps)
                                    }
                                },
                                onSetType = { actions.onSetType(exercise.id, set.id, it) },
                                onWarmupUpToHere = { actions.onWarmupUpTo(exercise.id, set.id) },
                                onRemove = { actions.onRemoveSet(exercise.id, set.id) },
                            ),
                        )
                    }
                }
            }

            TextButton(
                onClick = { actions.onAddSet(exercise.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(id = R.string.session_add_set))
            }
        }
    }
}

@Composable
private fun ExerciseHeader(
    exercise: SessionExercise,
    catalogEntry: CatalogExercise?,
    allCompleted: Boolean,
    contentColor: Color,
    actions: ExerciseCardActions,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(
            entry = catalogEntry,
            onClick = { actions.onInfo(exercise.id) },
        )
        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { actions.onInfo(exercise.id) },
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // The same strike-through the old screen used for a finished exercise.
                textDecoration = if (allCompleted) TextDecoration.LineThrough else null,
            )
            Text(
                text = if (exercise.restSeconds > 0) {
                    stringResource(id = R.string.session_exercise_rest_format, formatClock(exercise.restSeconds.toLong()))
                } else {
                    stringResource(id = R.string.session_exercise_rest_off)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(id = R.string.session_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_info)) },
                    onClick = {
                        menuOpen = false
                        actions.onInfo(exercise.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_alternative)) },
                    // Alternatives are computed from the catalog entry, so without one there is nothing to offer.
                    enabled = catalogEntry != null,
                    onClick = {
                        menuOpen = false
                        actions.onAlternative(exercise.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_rest)) },
                    onClick = {
                        menuOpen = false
                        actions.onRest(exercise.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_note)) },
                    onClick = {
                        menuOpen = false
                        actions.onNote(exercise.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_reorder)) },
                    onClick = {
                        menuOpen = false
                        actions.onReorder()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.session_exercise_menu_remove), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        actions.onRemove(exercise.id)
                    },
                )
            }
        }
    }
}

/** The 48 dp picture of an exercise: the catalog's first image, or a dumbbell when there is none. */
@Composable
fun ExerciseThumbnail(
    entry: CatalogExercise?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surface)
    val url = entry?.imageUrls?.firstOrNull()

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = stringResource(id = R.string.session_thumbnail_cd),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = rememberVectorPainter(Icons.Default.FitnessCenter),
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun SetTableHeader() {
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderLabel(stringResource(id = R.string.session_col_set), SetColumnWidth, style, color)
        Text(
            text = stringResource(id = R.string.session_col_previous),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        HeaderLabel(stringResource(id = R.string.session_col_kg), WeightColumnWidth, style, color)
        HeaderLabel(stringResource(id = R.string.session_col_reps), RepsColumnWidth, style, color)
        HeaderLabel(stringResource(id = R.string.session_col_target_rpe), TargetRpeColumnWidth, style, color)
        HeaderLabel(stringResource(id = R.string.session_col_rpe), RpeColumnWidth, style, color)
        Spacer(modifier = Modifier.width(CheckColumnWidth))
    }
}

@Composable
private fun HeaderLabel(
    text: String,
    width: Dp,
    style: TextStyle,
    color: Color,
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.width(width),
    )
}
