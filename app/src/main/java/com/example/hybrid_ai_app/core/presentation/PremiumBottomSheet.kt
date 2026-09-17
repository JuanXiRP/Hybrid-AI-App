package com.example.hybrid_ai_app.core.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.PremiumRequiredReason

/**
 * The single upsell surface, shown when the user hits a free-tier limit.
 *
 * A bottom sheet rather than a dialog or a full-screen navigation: the user keeps their context
 * (the message they were typing, the screen they were on) and can dismiss with a swipe.
 *
 * Copy is chosen from the `code` the backend returned with its 402 — the UI never parses a
 * human-readable message to decide what to say.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumBottomSheet(
    reason: PremiumRequiredReason,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
    chatLimit: Int? = null,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFB5E4CA).copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFF165239),
                )
            }

            Text(
                text = stringResource(id = reason.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = reason.bodyText(chatLimit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onSeePlans,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF165239)),
            ) {
                Text(
                    text = stringResource(id = R.string.premium_sheet_cta),
                    fontWeight = FontWeight.Bold,
                )
            }

            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(id = R.string.premium_sheet_dismiss),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun PremiumRequiredReason.titleRes(): Int = when (this) {
    PremiumRequiredReason.CHAT_QUOTA_EXCEEDED -> R.string.premium_sheet_title_chat
    PremiumRequiredReason.PLAN_LIMIT_REACHED -> R.string.premium_sheet_title_plan
    PremiumRequiredReason.TRIAL_EXPIRED -> R.string.premium_sheet_title_trial
    PremiumRequiredReason.UNKNOWN -> R.string.premium_sheet_title_generic
}

@Composable
private fun PremiumRequiredReason.bodyText(chatLimit: Int?): String = when (this) {
    PremiumRequiredReason.CHAT_QUOTA_EXCEEDED ->
        stringResource(id = R.string.premium_sheet_body_chat, chatLimit ?: 0)

    PremiumRequiredReason.PLAN_LIMIT_REACHED -> stringResource(id = R.string.premium_sheet_body_plan)
    PremiumRequiredReason.TRIAL_EXPIRED -> stringResource(id = R.string.premium_sheet_body_trial)
    PremiumRequiredReason.UNKNOWN -> stringResource(id = R.string.premium_sheet_body_generic)
}
