package com.example.hybrid_ai_app.home.presentation.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.presentation.workout.components.ExerciseCard
import com.example.hybrid_ai_app.home.presentation.workout.components.ExerciseCardActions

/** The scrolling part of the session: workout note, one card per exercise, and "add exercise". */
@Composable
internal fun SessionBody(
    session: WorkoutSession,
    active: WorkoutSessionUiState.Active,
    actions: ExerciseCardActions,
    onEditNotes: () -> Unit,
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (session.notes.isNotBlank()) {
            item(key = "workout-note") {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onEditNotes),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                ) {
                    Text(
                        text = session.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }

        if (session.exercises.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(id = R.string.session_empty_workout),
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(session.exercises, key = { it.id }) { exercise: SessionExercise ->
            ExerciseCard(
                exercise = exercise,
                previousSets = active.previousByExercise[exercise.id].orEmpty(),
                catalogEntry = active.catalogByExercise[exercise.id],
                actions = actions,
            )
        }

        item(key = "add-exercise") {
            OutlinedButton(
                onClick = onAddExercise,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(id = R.string.session_add_exercise), fontWeight = FontWeight.Bold)
            }
        }
    }
}
