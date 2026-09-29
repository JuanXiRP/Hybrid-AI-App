package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.data.local.entity.LoggedSetEntity
import com.example.hybrid_ai_app.home.domain.model.SetType
import com.example.hybrid_ai_app.home.presentation.components.HybridTopAppBar
import com.example.hybrid_ai_app.home.presentation.workout.formatClock
import com.example.hybrid_ai_app.navigation.Screen

data class LoggedExerciseMetric(
    val name: String,
    val sets: String,
    val reps: String,
    val weight: String,
    val rpe: String,
    val notes: String? = null,
    val setLogs: List<LoggedSetEntity> = emptyList(),
)

data class HistoryItem(
    val logId: Long,
    val formattedDate: String,
    val weekNumber: Int,
    val dayNumber: Int,
    // Null when neither the log nor the plan can name the session; the screen supplies the
    // localized fallback.
    val title: String?,
    val isCardio: Boolean,
    // The first exercise names, or blank when there are none (the screen then shows a localized
    // "session completed" line).
    val summary: String,
    val loggedMetrics: List<LoggedExerciseMetric> = emptyList(),
    val notes: String? = null,
    val durationSec: Long? = null,
    val isEditable: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    var selectedHistoryItem by remember { mutableStateOf<HistoryItem?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val profilePicPath by viewModel.localProfilePicPath.collectAsState(initial = null)

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = stringResource(id = R.string.history_title),
                profilePicPath = profilePicPath,
                onProfileClick = { navController.navigate(Screen.Settings.route) },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is HistoryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is HistoryUiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                is HistoryUiState.Empty -> {
                    Text(
                        text = stringResource(id = R.string.history_empty_msg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                is HistoryUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(text = stringResource(id = R.string.analytics_charts_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(text = stringResource(id = R.string.future_updates_msg), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        items(state.items, key = { it.logId }) { item ->
                            HistoryCard(
                                item = item,
                                onClick = { selectedHistoryItem = item },
                            )
                        }
                    }
                }
            }
        }

        if (selectedHistoryItem != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedHistoryItem = null },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                HistoryDetailSheetContent(
                    item = selectedHistoryItem!!,
                    onEdit = { item ->
                        selectedHistoryItem = null
                        navController.navigate(Screen.WorkoutEdit.createRoute(item.logId))
                    },
                )
            }
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (item.isCardio) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (item.isCardio) Icons.Default.Share else Icons.Default.Build,
                            contentDescription = null,
                            tint = if (item.isCardio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column {
                        Text(
                            text = item.title ?: stringResource(id = R.string.history_default_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = item.formattedDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.timeline_week_day_indicator, item.weekNumber, item.dayNumber),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.summary.ifBlank {
                    stringResource(
                        id = if (item.isCardio) R.string.history_summary_endurance else R.string.history_summary_strength,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun HistoryDetailSheetContent(item: HistoryItem, onEdit: (HistoryItem) -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.performance_log_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            // Only a log with per-set detail can be reopened in the set table; a legacy one has
            // nothing to put there and stays read-only.
            if (item.isEditable) {
                TextButton(onClick = { onEdit(item) }) {
                    Text(stringResource(id = R.string.history_edit), fontWeight = FontWeight.Bold)
                }
            }
        }

        item.durationSec?.takeIf { it > 0 }?.let { seconds ->
            Text(
                text = stringResource(id = R.string.history_duration_format, formatClock(seconds)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (item.loggedMetrics.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = R.string.no_metrics_recorded),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(item.loggedMetrics) { metric ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        if (metric.setLogs.isEmpty()) {
                            LegacyMetricRow(metric)
                        } else {
                            SetLogList(metric)
                        }
                    }
                }
            }
        }
    }
}

/** A log from before per-set detail existed: sets by reps, and the one weight. */
@Composable
private fun LegacyMetricRow(metric: LoggedExerciseMetric) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = metric.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(id = R.string.exercise_metrics_label, metric.sets, metric.reps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (metric.weight.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.metric_weight_suffix, metric.weight),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The sets of one exercise as they were performed: type, weight by reps, and effort against plan. */
@Composable
private fun SetLogList(metric: LoggedExerciseMetric) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = metric.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        metric.notes?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Normal sets are numbered; warm-up, drop and failure sets carry their letter, as on the
        // workout screen.
        var normalCount = 0
        metric.setLogs.forEach { set ->
            val label = when (SetType.fromWireName(set.type)) {
                SetType.WARMUP -> "W"
                SetType.DROP -> "D"
                SetType.FAILURE -> "F"
                SetType.NORMAL -> (++normalCount).toString()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp),
                )
                Text(text = setSummary(set), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun setSummary(set: LoggedSetEntity): String {
    val load = when {
        set.weight.isBlank() && set.reps.isBlank() -> "–"
        set.reps.isBlank() -> stringResource(id = R.string.metric_weight_suffix, set.weight)
        set.weight.isBlank() -> stringResource(id = R.string.history_set_reps_only, set.reps)
        else -> stringResource(id = R.string.history_set_load, set.weight, set.reps)
    }
    val effort = when {
        set.actualRpe.isBlank() -> null
        set.targetRpe.isBlank() -> stringResource(id = R.string.history_set_rpe, set.actualRpe)
        else -> stringResource(id = R.string.history_set_rpe_target, set.actualRpe, set.targetRpe)
    }
    return if (effort == null) load else "$load · $effort"
}
