package com.example.hybrid_ai_app.home.presentation.workout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/**
 * The floating bar that stands in for a minimized workout: its name, how long it has been going and,
 * while a rest runs, how much rest is left. Tapping it goes back to the session.
 */
@Composable
fun ActiveWorkoutBar(
    session: WorkoutSession,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val restRemaining = session.restEndsAt?.let { (it - nowMillis + 999) / 1000 }?.takeIf { it > 0 }
    val returnCd = stringResource(id = R.string.session_bar_return_cd)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = returnCd, onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(22.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { stringResource(id = R.string.session_notif_title) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatClock((nowMillis - session.startedAt) / 1000),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (restRemaining != null) {
                Text(
                    text = stringResource(id = R.string.session_bar_rest, formatClock(restRemaining)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
        }
    }
}
