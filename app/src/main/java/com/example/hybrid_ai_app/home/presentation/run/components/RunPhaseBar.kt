package com.example.hybrid_ai_app.home.presentation.run.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.core.domain.model.RunStructure
import com.example.hybrid_ai_app.home.presentation.run.color

private val BAR_HEIGHT = 12.dp
private val MARKER_WIDTH = 4.dp

/**
 * The whole run as one bar, each segment as wide as it is long and coloured by its phase. During
 * the run, [progressFraction] (0..1 of the total) draws a marker where the athlete is.
 */
@Composable
fun RunPhaseBar(
    structure: RunStructure,
    modifier: Modifier = Modifier,
    progressFraction: Float? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(BAR_HEIGHT + 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(6.dp)),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            structure.segments().forEach { segment ->
                Box(
                    modifier = Modifier
                        .weight(segment.durationSec.toFloat())
                        .height(BAR_HEIGHT)
                        .background(segment.phase.color()),
                )
            }
        }
        if (progressFraction != null) {
            val x = maxWidth * progressFraction.coerceIn(0f, 1f) - MARKER_WIDTH / 2
            Box(
                modifier = Modifier
                    .offset(x = x.coerceIn(0.dp, maxWidth - MARKER_WIDTH))
                    .width(MARKER_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)),
            )
        }
    }
}
