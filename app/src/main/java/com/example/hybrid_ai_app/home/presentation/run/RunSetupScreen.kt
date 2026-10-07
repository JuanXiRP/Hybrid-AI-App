package com.example.hybrid_ai_app.home.presentation.run

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.RunLimits
import com.example.hybrid_ai_app.home.presentation.run.components.DurationStepper
import com.example.hybrid_ai_app.home.presentation.run.components.RunPhaseBar
import com.example.hybrid_ai_app.home.presentation.workout.formatClock

/**
 * The screen before a guided run: it shows what the plan asks for and lets the athlete set the
 * warm-up, the main part (continuous, or intervals with rests) and the cool-down, all by time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSetupScreen(
    viewModel: RunSetupViewModel,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val success = state as? RunSetupUiState.Success

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (success != null) runTitle(success.dayName, success.isExtra) else stringResource(R.string.executing_workout_fallback),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_go_back))
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = onStart,
                    enabled = success != null && success.canStart,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text(text = stringResource(R.string.run_setup_start), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (val current = state) {
                RunSetupUiState.Loading -> CircularProgressIndicator()
                RunSetupUiState.Error -> Text(text = stringResource(R.string.run_setup_error), style = MaterialTheme.typography.bodyLarge)
                is RunSetupUiState.Success -> SetupContent(current, viewModel)
            }
        }
    }
}

@Composable
private fun SetupContent(state: RunSetupUiState.Success, viewModel: RunSetupViewModel) {
    val config = state.config

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.instruction?.let { exercise ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.todays_mission_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        text = stringResource(R.string.cardio_metrics_format, exercise.sets, exercise.reps, exercise.rpe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val isFree = config.mode == RunMainMode.FREE

        // A free run has no phases at all, so it has no warm-up or cool-down to time either.
        if (!isFree) {
            SetupCard(title = stringResource(R.string.run_setup_warmup)) {
                DurationStepper(
                    label = stringResource(R.string.run_setup_duration),
                    value = minutes(config.warmupMin),
                    onDecrease = { viewModel.setWarmupMin(config.warmupMin - 1) },
                    onIncrease = { viewModel.setWarmupMin(config.warmupMin + 1) },
                    canDecrease = config.warmupMin > 0,
                    canIncrease = config.warmupMin < RunLimits.MAX_WARMUP_MIN,
                )
            }
        }

        SetupCard(title = stringResource(R.string.run_setup_main)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                RunMainMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = config.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, RunMainMode.entries.size),
                    ) {
                        Text(text = stringResource(mode.labelRes()))
                    }
                }
            }
            when (config.mode) {
                RunMainMode.CONTINUOUS -> DurationStepper(
                    label = stringResource(R.string.run_setup_duration),
                    value = minutes(config.continuousMin),
                    onDecrease = { viewModel.setContinuousMin(config.continuousMin - 1) },
                    onIncrease = { viewModel.setContinuousMin(config.continuousMin + 1) },
                    canDecrease = config.continuousMin > RunLimits.MIN_CONTINUOUS_MIN,
                    canIncrease = config.continuousMin < RunLimits.MAX_CONTINUOUS_MIN,
                )
                RunMainMode.INTERVALS -> {
                    DurationStepper(
                        label = stringResource(R.string.run_setup_repeats),
                        value = config.repeats.toString(),
                        onDecrease = { viewModel.setRepeats(config.repeats - 1) },
                        onIncrease = { viewModel.setRepeats(config.repeats + 1) },
                        canDecrease = config.repeats > RunLimits.MIN_REPEATS,
                        canIncrease = config.repeats < RunLimits.MAX_REPEATS,
                    )
                    DurationStepper(
                        label = stringResource(R.string.run_setup_work),
                        value = formatClock(config.workSec.toLong()),
                        onDecrease = { viewModel.setWorkSec(config.workSec - RunLimits.STEP_SEC) },
                        onIncrease = { viewModel.setWorkSec(config.workSec + RunLimits.STEP_SEC) },
                        canDecrease = config.workSec > RunLimits.MIN_WORK_SEC,
                        canIncrease = config.workSec < RunLimits.MAX_WORK_SEC,
                    )
                    DurationStepper(
                        label = stringResource(R.string.run_setup_rest),
                        value = formatClock(config.restSec.toLong()),
                        onDecrease = { viewModel.setRestSec(config.restSec - RunLimits.STEP_SEC) },
                        onIncrease = { viewModel.setRestSec(config.restSec + RunLimits.STEP_SEC) },
                        canDecrease = config.restSec > 0,
                        canIncrease = config.restSec < RunLimits.MAX_REST_SEC,
                    )
                }
                RunMainMode.FREE -> Text(
                    text = stringResource(R.string.run_setup_free_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val structure = state.structure
        if (structure != null) {
            SetupCard(title = stringResource(R.string.run_setup_cooldown)) {
                DurationStepper(
                    label = stringResource(R.string.run_setup_duration),
                    value = minutes(config.cooldownMin),
                    onDecrease = { viewModel.setCooldownMin(config.cooldownMin - 1) },
                    onIncrease = { viewModel.setCooldownMin(config.cooldownMin + 1) },
                    canDecrease = config.cooldownMin > 0,
                    canIncrease = config.cooldownMin < RunLimits.MAX_COOLDOWN_MIN,
                )
            }

            Text(
                text = stringResource(R.string.run_setup_total_format, minutes((state.totalSec + 59) / 60)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            RunPhaseBar(structure)
        }
    }
}

@StringRes
private fun RunMainMode.labelRes(): Int = when (this) {
    RunMainMode.CONTINUOUS -> R.string.run_setup_mode_continuous
    RunMainMode.INTERVALS -> R.string.run_setup_mode_intervals
    RunMainMode.FREE -> R.string.run_setup_mode_free
}

@Composable
private fun SetupCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun minutes(value: Int): String = stringResource(R.string.run_setup_minutes_format, value)
