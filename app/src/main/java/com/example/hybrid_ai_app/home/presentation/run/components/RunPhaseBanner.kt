package com.example.hybrid_ai_app.home.presentation.run.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.RunProgress
import com.example.hybrid_ai_app.home.presentation.run.color
import com.example.hybrid_ai_app.home.presentation.run.label
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/**
 * The live view of a guided run: what to do now, how long is left, and what comes next. Shows
 * nothing for a free run, which has no progress.
 */
@Composable
fun RunPhaseBanner(progress: RunProgress?, modifier: Modifier = Modifier) {
    if (progress == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val segment = progress.segment
        if (progress.finished || segment == null) {
            Text(
                text = stringResource(R.string.run_banner_done),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        val color = segment.phase.color()
        Text(
            text = segment.label(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
        Text(
            text = formatClock(progress.remainingSec.toLong()),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        LinearProgressIndicator(
            progress = { 1f - progress.remainingSec.toFloat() / segment.durationSec },
            modifier = Modifier.fillMaxWidth(),
            color = color,
        )
        progress.next?.let { next ->
            Text(
                text = stringResource(R.string.run_banner_next_format, next.label(), formatClock(next.durationSec.toLong())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
