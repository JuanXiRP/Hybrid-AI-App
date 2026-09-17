package com.example.hybrid_ai_app.core.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.Entitlement
import com.example.hybrid_ai_app.core.domain.model.EntitlementStatus

/**
 * Ambient reminder of how much free trial is left. Renders nothing for premium users.
 *
 * Escalates rather than nags: neutral until three days remain, then tinted with the theme's
 * error colour, and replaced by a read-only notice once the window closes.
 */
@Composable
fun TrialBanner(
    entitlement: Entitlement,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entitlement.status == EntitlementStatus.PREMIUM) return

    val isExpired = entitlement.status == EntitlementStatus.EXPIRED
    val isUrgent = isExpired || entitlement.trialDaysLeft <= URGENT_THRESHOLD_DAYS

    val containerColor = if (isUrgent) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color(0xFFB5E4CA).copy(alpha = 0.35f)
    }
    val contentColor = if (isUrgent) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        Color(0xFF165239)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (isExpired) Icons.Default.LockClock else Icons.Default.Schedule,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )

        Text(
            text = if (isExpired) {
                stringResource(id = R.string.trial_expired_banner)
            } else {
                pluralStringResource(
                    id = R.plurals.trial_days_left,
                    count = entitlement.trialDaysLeft,
                    entitlement.trialDaysLeft,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )

        TextButton(onClick = onUpgradeClick) {
            Text(
                text = stringResource(id = R.string.trial_banner_cta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

private const val URGENT_THRESHOLD_DAYS = 3
