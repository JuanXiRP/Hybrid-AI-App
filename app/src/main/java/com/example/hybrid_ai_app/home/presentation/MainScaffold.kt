package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.hybrid_ai_app.navigation.MainNavGraph
import com.example.hybrid_ai_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold() {
    // 1. UN SOLO CONTROLADOR para todo el andamiaje
    val bottomNavController = rememberNavController()

    // 2. Escuchamos la ruta del controlador correcto
    val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showWorkoutSelector by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Temporary mock data for the weekly workouts selector
    val weeklyWorkouts = remember {
        listOf(
            Pair("w1", "gym"), // ID and Type
            Pair("w2", "run"),
            Pair("w3", "gym")
        )
    }

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

            if (!isCoachScreen && !isExecutionScreen) {
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
            // El NavGraph usa el mismo controlador que los botones
            MainNavGraph(navController = bottomNavController)
        }

        // Bottom sheet displaying current week's available workouts
        if (showWorkoutSelector) {
            ModalBottomSheet(
                onDismissRequest = { showWorkoutSelector = false },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "Select Workout for Week 1",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(weeklyWorkouts) { workout ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showWorkoutSelector = false
                                        bottomNavController.navigate(
                                            Screen.WorkoutExecution.createRoute(workout.first, workout.second)
                                        )
                                    }
                            ) {
                                ListItem(
                                    headlineContent = { Text(text = if (workout.second == "gym") "Strength Session" else "Running Protocol") },
                                    supportingContent = { Text(text = "Type: ${workout.second.uppercase()}") },
                                    leadingContent = {
                                        Icon(
                                            imageVector = if (workout.second == "gym") Icons.Default.Build else Icons.Default.Share,
                                            contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}