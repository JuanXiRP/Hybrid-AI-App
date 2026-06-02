package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.navigation.MainNavGraph
import com.example.hybrid_ai_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    rootNavController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val bottomNavController = rememberNavController()
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showWorkoutSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentRoute == Screen.Home.route,
                    onClick = { bottomNavController.navigate(Screen.Home.route) { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Workouts") },
                    label = { Text("Workouts") },
                    selected = currentRoute == Screen.Workouts.route,
                    onClick = { bottomNavController.navigate(Screen.Workouts.route) { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Face, contentDescription = "Coach AI") },
                    label = { Text("Coach") },
                    selected = currentRoute == Screen.Coach.route,
                    onClick = { bottomNavController.navigate(Screen.Coach.route) { launchSingleTop = true } }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "History") },
                    label = { Text("History") },
                    selected = currentRoute == Screen.History.route,
                    onClick = { bottomNavController.navigate(Screen.History.route) { launchSingleTop = true } }
                )
            }
        },
        floatingActionButton = {
            val isCoachScreen = currentRoute == Screen.Coach.route
            val isExecutionScreen = currentRoute?.startsWith("workout_execution") == true
            val isOnboarding = currentRoute == Screen.Onboarding.route

            if (!isCoachScreen && !isExecutionScreen && !isOnboarding) {
                FloatingActionButton(
                    onClick = { showWorkoutSelector = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Quick Start Workout",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            MainNavGraph(
                navController = bottomNavController,
                rootNavController = rootNavController
            )
        }

        if (showWorkoutSelector) {
            ModalBottomSheet(
                onDismissRequest = { showWorkoutSelector = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                when (val state = uiState) {
                    is HomeUiState.Success -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = "Select Session · Week ${state.currentWeekNumber}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            val pendingWorkouts = state.currentWeek.days.mapIndexedNotNull { index, day ->
                                val isCompleted = state.weeklyCompletion[index]
                                val isRestDay = day.exercises.isEmpty()
                                val isCardio = day.workoutType == "cardio"

                                if (!isCompleted && !isRestDay) {
                                    Triple(index, day, isCardio)
                                } else null
                            }

                            if (pendingWorkouts.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "All active sessions for this week are completed! Enjoy your recovery.",
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
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
                                                        Screen.WorkoutExecution.createRoute(state.currentWeekNumber, originalDayIndex)
                                                    )
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isCardio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            ListItem(
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                headlineContent = {
                                                    Text(
                                                        text = day.dayName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCardio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                supportingContent = { Text(text = "${day.exercises.size} Exercises") },
                                                leadingContent = {
                                                    Icon(
                                                        imageVector = if (isCardio) Icons.Default.Share else Icons.Default.Build,
                                                        contentDescription = null,
                                                        tint = if (isCardio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
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
                            Text("No active plan to select from.")
                        }
                    }
                }
            }
        }
    }
}