package com.example.hybrid_ai_app.onboarding.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme

@Composable
fun LoadingScreen(
    message: String = "Generating your AI Plan..."
) {
    // Creates a smooth pulsing alpha effect for the text to simulate "thinking"
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "text_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // --- LOGO ---
            Image(
                painter = painterResource(id = R.drawable.logo_hybrid_ai),
                contentDescription = "Hybrid Icon",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(110.dp) // Large and central
            )


            Spacer(modifier = Modifier.height(56.dp))

            // --- INDETERMINATE PROGRESS BAR ---
            // Omitting the 'progress' parameter triggers the infinite side-to-side animation
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f) // Takes exactly 60% of the screen width for a clean look
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // --- PULSING TEXT ---
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_8")
@Composable
fun LoadingScreenPreview() {
    HybridTrainingTheme {
        LoadingScreen()
    }
}