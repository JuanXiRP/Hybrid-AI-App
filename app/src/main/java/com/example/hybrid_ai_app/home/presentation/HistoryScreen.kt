package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hybrid_ai_app.home.presentation.components.HybridTopAppBar
import com.example.hybrid_ai_app.navigation.Screen

enum class WorkoutIntensity {
    HIGH, MODERATE, LOW
}

data class WorkoutHistoryItem(
    val id: String,
    val date: String,
    val title: String,
    val type: String, // "gym" or "run"
    val duration: String,
    val calories: String,
    val intensity: WorkoutIntensity
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    val historyItems = remember {
        listOf(
            WorkoutHistoryItem("1", "May 18, 2026", "Push Hypertrophy", "gym", "55 min", "420 kcal", WorkoutIntensity.HIGH),
            WorkoutHistoryItem("2", "May 17, 2026", "Zone 2 Steady Run", "run", "45 min", "510 kcal", WorkoutIntensity.MODERATE),
            WorkoutHistoryItem("3", "May 15, 2026", "Pull & Core Power", "gym", "60 min", "480 kcal", WorkoutIntensity.HIGH),
            WorkoutHistoryItem("4", "May 14, 2026", "Active Recovery Walk", "run", "30 min", "200 kcal", WorkoutIntensity.LOW)
        )
    }

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = "PERFORMANCE HISTORY",
                onProfileClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // Placeholder para futuras gráficas
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Analytics & Charts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Available in future updates", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Lista del historial de entrenamientos
            items(historyItems) { item ->
                HistoryCard(item)
            }
        }
    }
}

@Composable
fun HistoryCard(item: WorkoutHistoryItem) {
    // Definimos los colores semánticos según la intensidad
    val intensityColor = when (item.intensity) {
        WorkoutIntensity.HIGH -> MaterialTheme.colorScheme.error // Rojo/Atención
        WorkoutIntensity.MODERATE -> MaterialTheme.colorScheme.primary // Azul/Principal
        WorkoutIntensity.LOW -> MaterialTheme.colorScheme.tertiary // Verde/Terciario
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila superior: Icono, Título, Fecha y Badge de Intensidad
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.type == "gym") Icons.Default.Build else Icons.Default.Share,
                            contentDescription = item.type,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(text = item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = item.date, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Badge de Intensidad
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(intensityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.intensity.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = intensityColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            // Fila inferior: Métricas de rendimiento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricText(label = "Duration", value = item.duration)
                MetricText(label = "Calories", value = item.calories)
            }
        }
    }
}

@Composable
fun MetricText(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}