package com.example.hybrid_ai_app.core.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto

/** A day the user brought in themselves rather than one the AI wrote. */
fun DayDto.isImported(): Boolean = source == "imported"

/**
 * Marks a session that came from the plan the user imported. Deliberately quiet — the merged plan
 * is meant to read as one plan, the badge only answers "is this mine or the AI's?".
 */
@Composable
fun ImportedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(id = R.string.badge_your_plan),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
