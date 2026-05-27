package com.example.hybrid_ai_app.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState(initial = "en")
    val isDarkMode by viewModel.isDarkMode.collectAsState(initial = true)

    // 🟢 Observe the new Profile State
    val profileState by viewModel.profileState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 🟢 New Profile Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Personal Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = profileState) {
                        is ProfileState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        is ProfileState.Error -> {
                            Text("Unable to load profile data.", color = MaterialTheme.colorScheme.error)
                        }
                        is ProfileState.Success -> {
                            val user = state.user
                            // Replace these with your actual user model variables
                            ProfileRow(label = "Goal", value = user.goal?.uppercase() ?: "N/A")
                            ProfileRow(label = "Fitness Level", value = user.fitnessLevel?.uppercase() ?: "N/A")
                            ProfileRow(label = "Weight", value = "${user.weight} kg")
                            ProfileRow(label = "Availability", value = "${user.daysAvailable} days/week")
                        }
                    }
                }
            }

            // Existing Settings...
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language / Idioma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilterChip(selected = currentLanguage == "en", onClick = { viewModel.saveLanguage("en") }, label = { Text("English") })
                        FilterChip(selected = currentLanguage == "es", onClick = { viewModel.saveLanguage("es") }, label = { Text("Español") })
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Switch(checked = isDarkMode, onCheckedChange = { isDark -> viewModel.toggleDarkMode(isDark) })
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 🟢 Logout Button
            Button(
                onClick = {
                    viewModel.logout {
                        // Triggers when token is cleared. Navigates back to Root Auth screen.
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true } // Wipes the entire backstack
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Logout", modifier = Modifier.padding(end = 8.dp))
                Text("Logout")
            }
        }
    }
}

// 🟢 Helper Composable for clean profile rows
@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}