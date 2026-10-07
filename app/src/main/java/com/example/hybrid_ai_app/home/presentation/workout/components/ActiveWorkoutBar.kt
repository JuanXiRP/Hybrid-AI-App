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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.presentation.run.runTitle
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
    MinimizedWorkoutBar(
        icon = Icons.Default.FitnessCenter,
        title = session.title.ifBlank {
            stringResource(id = if (session.isExtra) R.string.extra_strength_title else R.string.session_notif_title)
        },
        elapsedSec = (nowMillis - session.startedAt) / 1000,
        trailing = restRemaining?.let { stringResource(id = R.string.session_bar_rest, formatClock(it)) },
        onClick = onClick,
        modifier = modifier,
    )
}

/** The same bar for a run in progress, whose clock is rebuilt from its stored anchors. */
@Composable
fun ActiveRunBar(
    run: ActiveRun,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MinimizedWorkoutBar(
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
        title = runTitle(run.title, run.isExtra),
        elapsedSec = run.elapsedMs(nowMillis) / 1000,
        trailing = if (run.isPaused) stringResource(id = R.string.run_bar_paused) else null,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MinimizedWorkoutBar(
    icon: ImageVector,
    title: String,
    elapsedSec: Long,
    trailing: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatClock(elapsedSec),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
        }
    }
}
