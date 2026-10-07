package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.ActiveRun
import com.example.hybrid_ai_app.core.presentation.ImportedBadge
import com.example.hybrid_ai_app.core.presentation.isImported
import com.example.hybrid_ai_app.home.domain.model.WorkoutSession
import com.example.hybrid_ai_app.home.presentation.workout.ActiveWorkoutBarViewModel
import com.example.hybrid_ai_app.home.presentation.workout.SessionLinks
import com.example.hybrid_ai_app.home.presentation.workout.WorkoutSessionViewModel
import com.example.hybrid_ai_app.home.presentation.workout.components.ActiveRunBar
import com.example.hybrid_ai_app.home.presentation.workout.components.ActiveWorkoutBar
import com.example.hybrid_ai_app.home.presentation.workout.rememberNowMillis
import com.example.hybrid_ai_app.navigation.MainNavGraph
import com.example.hybrid_ai_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    rootNavController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
    barViewModel: ActiveWorkoutBarViewModel = hiltViewModel(),
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showWorkoutSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val uiState by viewModel.uiState.collectAsState()
    val activeSession by barViewModel.session.collectAsState()
    val activeRun by barViewModel.run.collectAsState()

    // A tap on the workout notification asks to open a session; this is the one place that owns the
    // controller for that route. Consumed at once so it does not fire again.
    val requestedRoute by SessionLinks.pending.collectAsState()
    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { route ->
            bottomNavController.navigate(route) { launchSingleTop = true }
            SessionLinks.consume()
        }
    }

    val onWorkoutScreen = currentRoute?.startsWith("workout_execution") == true ||
        currentRoute?.startsWith("workout_edit") == true ||
        currentRoute?.startsWith("extra_workout") == true

    Scaffold(
        bottomBar = {
            Column {
                // The floating stand-in for a workout that was minimized rather than finished.
                if (!onWorkoutScreen) {
                    activeRun?.let { run ->
                        LiveRunBar(run) {
                            bottomNavController.navigate(SessionLinks.routeFor(run)) { launchSingleTop = true }
                        }
                    }
                    activeSession?.let { session ->
                        LiveWorkoutBar(session) {
                            bottomNavController.navigate(SessionLinks.routeFor(session)) { launchSingleTop = true }
                        }
                    }
                }
                NavigationBar {
                    // The label already names the destination, so the icon's content description is
                    // null: TalkBack would otherwise read every tab twice.
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_home)) },
                        selected = currentRoute == Screen.Home.route,
                        onClick = { bottomNavController.navigate(Screen.Home.route) { launchSingleTop = true } },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_workouts)) },
                        selected = currentRoute == Screen.Workouts.route,
                        onClick = { bottomNavController.navigate(Screen.Workouts.route) { launchSingleTop = true } },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Face, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_coach)) },
                        selected = currentRoute == Screen.Coach.route,
                        onClick = { bottomNavController.navigate(Screen.Coach.route) { launchSingleTop = true } },
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text(stringResource(id = R.string.nav_history)) },
                        selected = currentRoute == Screen.History.route,
                        onClick = { bottomNavController.navigate(Screen.History.route) { launchSingleTop = true } },
                    )
                }
            }
        },
        floatingActionButton = {
            val isCoachScreen = currentRoute == Screen.Coach.route
            val isExecutionScreen = onWorkoutScreen
            val isOnboarding = currentRoute == Screen.Onboarding.route

            if (!isCoachScreen && !isExecutionScreen && !isOnboarding) {
                FloatingActionButton(
                    onClick = { showWorkoutSelector = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(id = R.string.quickstart_cd),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            MainNavGraph(
                navController = bottomNavController,
                rootNavController = rootNavController,
            )
        }

        if (showWorkoutSelector) {
            ModalBottomSheet(
                onDismissRequest = { showWorkoutSelector = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                // Extra workouts do not need a plan, so they come first and are always offered.
                ExtraWorkoutChooser(
                    onStrength = {
                        showWorkoutSelector = false
                        bottomNavController.navigate(Screen.ExtraWorkout.createRoute(WorkoutSessionViewModel.KIND_STRENGTH))
                    },
                    onRun = {
                        showWorkoutSelector = false
                        bottomNavController.navigate(Screen.ExtraWorkout.createRoute(WorkoutSessionViewModel.KIND_RUN))
                    },
                )
                when (val state = uiState) {
                    is HomeUiState.Success -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(id = R.string.quickstart_select_title, state.currentWeekNumber),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp),
                            )

                            val pendingWorkouts = state.currentWeek.days.mapIndexedNotNull { index, day ->
                                val isCompleted = state.weeklyCompletion[index]
                                val isRestDay = day.exercises.isEmpty()
                                val isCardio = day.workoutType == "cardio"

                                if (!isCompleted && !isRestDay) {
                                    Triple(index, day, isCardio)
                                } else {
                                    null
                                }
                            }

                            if (pendingWorkouts.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stringResource(id = R.string.quickstart_all_done),
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(pendingWorkouts) { (originalDayIndex, day, isCardio) ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showWorkoutSelector = false
                                                    bottomNavController.navigate(
                                                        Screen.WorkoutExecution.createRoute(state.currentWeekNumber, originalDayIndex),
                                                    )
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isCardio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            ),
                                        ) {
                                            ListItem(
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                headlineContent = {
                                                    Text(
                                                        text = day.dayName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCardio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                                supportingContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        Text(text = pluralStringResource(id = R.plurals.quickstart_exercises_count, count = day.exercises.size, day.exercises.size))
                                                        if (day.isImported()) ImportedBadge()
                                                    }
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        imageVector = if (isCardio) Icons.Default.Share else Icons.Default.Build,
                                                        contentDescription = null,
                                                        tint = if (isCardio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(id = R.string.quickstart_no_plan))
                        }
                    }
                }
            }
        }
    }
}

/** "Add an extra workout": a strength session or a run that is not in the plan. */
@Composable
private fun ExtraWorkoutChooser(onStrength: () -> Unit, onRun: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(id = R.string.extra_workout_header),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(id = R.string.extra_workout_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onStrength, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.extra_workout_strength))
            }
            OutlinedButton(onClick = onRun, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(id = R.string.extra_workout_run))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

/** The minimized-run bar with its own clock, so the ticker runs only while the bar is on screen. */
@Composable
private fun LiveRunBar(run: ActiveRun, onClick: () -> Unit) {
    val nowMillis by rememberNowMillis()
    ActiveRunBar(run = run, nowMillis = nowMillis, onClick = onClick)
}

/** The minimized-workout bar with its own clock, so the ticker runs only while the bar is on screen. */
@Composable
private fun LiveWorkoutBar(session: WorkoutSession, onClick: () -> Unit) {
    val nowMillis by rememberNowMillis()
    ActiveWorkoutBar(session = session, nowMillis = nowMillis, onClick = onClick)
}
