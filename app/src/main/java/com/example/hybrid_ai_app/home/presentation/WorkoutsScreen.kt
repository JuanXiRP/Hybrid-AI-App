package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.remote.dto.DayDto
import com.example.hybrid_ai_app.home.presentation.components.HybridTopAppBar
import com.example.hybrid_ai_app.navigation.Screen

enum class WorkoutStatus {
    COMPLETED, ACTIVE, LOCKED, REST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedWeek by remember { mutableStateOf(1) }

    // State to manage the currently inspected workout routine inside the sheet
    var activeWorkoutDetailSheet by remember { mutableStateOf<DayDto?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val profilePicPath by viewModel.localProfilePicPath.collectAsState(initial = null)

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = stringResource(id = R.string.workouts_title),
                profilePicPath = profilePicPath,
                onProfileClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is HomeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is HomeUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is HomeUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = stringResource(id = R.string.no_plan_found))
                        Button(onClick = { navController.navigate(Screen.Onboarding.route) }) {
                            Text(text = stringResource(id = R.string.btn_generate_plan))
                        }
                    }
                }

                is HomeUiState.Success -> {
                    val plan = state.plan
                    val weeks = (1..plan.durationWeeks).toList()
                    val currentWeekData = plan.weeks.find { it.weekNumber == selectedWeek }

                    // Calculate genuine total macrocycle progress percentage
                    val totalDays = plan.durationWeeks * 7f
                    val completedDaysCount = ((state.currentWeekNumber - 1) * 7) + state.currentDayIndex
                    val progressPercentage = (completedDaysCount / totalDays).coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxSize()) {
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
                                    Text(
                                        text = stringResource(id = R.string.protocol_weeks_suffix, plan.durationWeeks),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(id = R.string.progress_done, (progressPercentage * 100).toInt()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressPercentage },
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
                                FilterChip(
                                    selected = selectedWeek == week,
                                    onClick = { selectedWeek = week },
                                    label = { Text(text = stringResource(id = R.string.week_indicator, week), fontWeight = FontWeight.Bold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }

                        // Vertical Timeline List mapping directly against real DTO models
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            currentWeekData?.days?.let { dayList ->
                                itemsIndexed(dayList) { index, day ->

                                    // 🟢 1. NUEVA LÓGICA: Evaluación flexible (Acceso Aleatorio)
                                    val status = when {
                                        day.exercises.isEmpty() -> WorkoutStatus.REST
                                        selectedWeek < state.currentWeekNumber -> WorkoutStatus.COMPLETED
                                        selectedWeek == state.currentWeekNumber -> {
                                            // Leemos directamente si este día específico está completado
                                            val isCompleted = state.weeklyCompletion.getOrElse(index) { false }
                                            if (isCompleted) {
                                                WorkoutStatus.COMPLETED
                                            } else {
                                                // TODOS los días pendientes de la semana actual están DISPONIBLES
                                                WorkoutStatus.ACTIVE
                                            }
                                        }
                                        else -> WorkoutStatus.LOCKED
                                    }

                                    TimelineItemRow(
                                        day = day,
                                        index = index,
                                        status = status,
                                        onItemClick = { activeWorkoutDetailSheet = day },
                                        onToggleComplete = {
                                            // 🟢 2. Pasamos las coordenadas exactas en lugar de un avance ciego
                                            if (status == WorkoutStatus.ACTIVE || status == WorkoutStatus.COMPLETED) {
                                                viewModel.toggleWorkoutCompletion(selectedWeek, index)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dynamic Routine Sheet with reduced rounding and better structure
        if (activeWorkoutDetailSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { activeWorkoutDetailSheet = null },
                sheetState = sheetState,
                // Reduced rounding for better space utilization and aesthetic
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surface, // Clean separation from background
                modifier = Modifier.fillMaxHeight(0.9f) // Allow it to occupy more screen height
            ) {
                WorkoutDetailSheetContent(day = activeWorkoutDetailSheet!!)
            }
        }
    }
}

@Composable
fun TimelineItemRow(
    day: DayDto,
    index: Int,
    status: WorkoutStatus,
    onItemClick: () -> Unit,
    onToggleComplete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🟢 Left Node Status Indicator Line (NOW INTERACTIVE)
        Box(
            modifier = Modifier
                .size(48.dp) // Slightly larger touch target
                .clip(CircleShape)
                .background(
                    when (status) {
                        WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        WorkoutStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                        WorkoutStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
                        WorkoutStatus.REST -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                )
                // 🟢 Added click listener specifically to the node indicator
                .clickable(
                    enabled = status == WorkoutStatus.ACTIVE || status == WorkoutStatus.COMPLETED
                ) {
                    onToggleComplete()
                },
            contentAlignment = Alignment.Center
        ) {
            when (status) {
                WorkoutStatus.COMPLETED -> Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary)
                WorkoutStatus.ACTIVE -> Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                WorkoutStatus.LOCKED -> Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                WorkoutStatus.REST -> Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
            }
        }

        // Right Card Container (Detail view trigger)
        val isCardio = day.workoutType == "cardio"

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onItemClick() }, // 🟢 The card purely opens the detail sheet now
            colors = CardDefaults.cardColors(
                containerColor = when (status) {
                    WorkoutStatus.COMPLETED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    WorkoutStatus.ACTIVE -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                }
            ),
            border = if (status == WorkoutStatus.ACTIVE) CardDefaults.outlinedCardBorder() else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Hierarchical labels for type and day number
                    Text(
                        text = "${if (isCardio) "CARDIO" else "STRENGTH"} DAY ${index + 1}".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal
                    )
                    // Day Name as main title
                    Text(
                        text = day.dayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (status == WorkoutStatus.LOCKED) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else Color.Unspecified
                    )
                    // Simple, clean exercise count or rest day message
                    Text(
                        text = if (day.exercises.isEmpty()) stringResource(id = R.string.rest_day_label) else stringResource(id = R.string.exercises_count_suffix, day.exercises.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

            }
        }
    }
}

@Composable
fun WorkoutDetailSheetContent(day: DayDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (day.exercises.isEmpty()) Icons.Default.Info else Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = day.dayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(16.dp))

        if (day.exercises.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.rest_day_detail_text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(day.exercises) { exercise ->
                    // Redesigned exercise card for superior legibility
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp) // Spacing between information lines
                        ) {
                            // Line 1: Exercise Name
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Line 2: Separate, clear metrics for Sets & Reps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp), // Spacing between items
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sets item with icon
                                ExerciseMetricItem(
                                    label = stringResource(id = R.string.metric_sets_suffix, exercise.sets),
                                    icon = Icons.Default.CheckCircle // Placeholder weights icon
                                )
                                // Reps item with icon
                                ExerciseMetricItem(
                                    label = stringResource(id = R.string.metric_reps_suffix, exercise.reps),
                                    icon = Icons.Default.DateRange // Placeholder repetition arrow icon
                                )
                            }

                            // Line 3: Clear, prominent RPE metric container
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 4.dp).align(Alignment.End) // Align to end for consistency
                            ) {
                                Text(
                                    text = stringResource(id = R.string.metric_rpe_prefix, exercise.rpe),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseMetricItem(
    label: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}