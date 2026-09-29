package com.example.hybrid_ai_app.home.presentation.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/**
 * The rest timer, pinned to the bottom of the session screen.
 *
 * While a rest runs it shows the countdown, a progress bar, `-15 s` / `+15 s` and Skip. When none is
 * running it shrinks to a single timer button that opens the manual-rest picker, so it never takes
 * more room than it needs.
 *
 * A rest counts as running only while [restEndsAt] is in the future; one that has ended is left in
 * the session as a past timestamp and is simply treated as over.
 */
@Composable
fun RestBar(
    restEndsAt: Long?,
    restTotalSec: Int?,
    nowMillis: Long,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onManualRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingMillis = if (restEndsAt == null) 0L else (restEndsAt - nowMillis).coerceAtLeast(0)
    val running = remainingMillis > 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (running) {
            RunningRest(remainingMillis, restTotalSec, onAdjust, onSkip)
        } else {
            IdleRest(onManualRest)
        }
    }
}

@Composable
private fun RunningRest(
    remainingMillis: Long,
    restTotalSec: Int?,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    // Rounded up, so the last second reads 0:01 rather than 0:00 while the timer is still running.
    val remainingSeconds = (remainingMillis + 999) / 1000
    val fraction = restTotalSec?.takeIf { it > 0 }?.let { (remainingMillis / (it * 1000f)).coerceIn(0f, 1f) } ?: 1f

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = { onAdjust(-15) }) {
                Text(stringResource(id = R.string.session_rest_minus))
            }
            Text(
                text = formatClock(remainingSeconds),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(onClick = { onAdjust(15) }) {
                Text(stringResource(id = R.string.session_rest_plus))
            }
            TextButton(onClick = onSkip) {
                Text(stringResource(id = R.string.session_rest_skip), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IdleRest(onManualRest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onManualRest) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.session_rest_timer))
        }
    }
}
