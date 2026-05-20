package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hybrid_ai_app.home.presentation.components.HybridTopAppBar
import com.example.hybrid_ai_app.navigation.Screen

enum class WorkoutStatus {
    COMPLETED, ACTIVE, LOCKED, REST
}

data class CalendarDay(
    val id: String,
    val dayName: String,
    val title: String,
    val description: String,
    val status: WorkoutStatus
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(navController: NavController) {
    // 🟢 CORRECCIÓN 1: Forzamos el tipo primitivo Int explícito para evitar inferencias ambiguas
    var selectedWeek by remember { mutableStateOf(1) }

    val weeks = listOf(1, 2, 3, 4, 5, 6, 7, 8)
    val calendarDays = remember {
        listOf(
            CalendarDay("d1", "Day 1", "Push Hypertrophy", "Gym · 45 mins", WorkoutStatus.COMPLETED),
            CalendarDay("d2", "Day 2", "Zone 2 Endurance", "Run · 60 mins", WorkoutStatus.ACTIVE),
            CalendarDay("d3", "Day 3", "Active Recovery", "Rest Day", WorkoutStatus.REST),
            CalendarDay("d4", "Day 4", "Pull & Core Power", "Gym · 50 mins", WorkoutStatus.LOCKED),
            CalendarDay("d5", "Day 5", "Threshold Intervals", "Run · 45 mins", WorkoutStatus.LOCKED)
        )
    }

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = "PROGRAM CALENDAR",
                onProfileClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Macrocycle Progress Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("8-Week Hybrid Protocol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("12% Done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { 0.12f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
            }

            // Horizontal Week Selector
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(weeks) { week ->
                    val isSelected = selectedWeek == week
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedWeek = week },
                        label = { Text("Week $week", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Vertical Timeline List
            LazyColumn(
                // 🟢 CORRECCIÓN 2: Eliminada la línea basura, modificador limpio
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(calendarDays) { day ->
                    TimelineItemRow(day = day)
                }
            }
        }
    }
}

@Composable
fun TimelineItemRow(day: CalendarDay) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Node (Visual State Indicator)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when (day.status) {
                        WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        WorkoutStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        WorkoutStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
                        WorkoutStatus.REST -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (day.status) {
                WorkoutStatus.COMPLETED -> Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary)
                WorkoutStatus.ACTIVE -> Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                WorkoutStatus.LOCKED -> Icon(Icons.Default.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                WorkoutStatus.REST -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
            }
        }

        // Right Node (Dynamic Details Card)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = if (day.status == WorkoutStatus.ACTIVE) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = if (day.status == WorkoutStatus.ACTIVE) CardDefaults.outlinedCardBorder() else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.dayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (day.status == WorkoutStatus.LOCKED) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (day.status == WorkoutStatus.LOCKED) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color.Unspecified
                    )
                    Text(
                        text = day.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (day.status == WorkoutStatus.ACTIVE) {
                    Button(
                        onClick = { /* Will interconnect with FAB quickstart pipeline */ },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                    ) {
                        Text("START", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}