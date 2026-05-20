package com.example.hybrid_ai_app.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.hybrid_ai_app.core.data.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Instantiating the manager (Tomorrow we will inject this via Hilt into a ViewModel)
    val preferencesManager = remember { PreferencesManager(context) }

    val currentLanguage by preferencesManager.languageFlow.collectAsState(initial = "en")
    val isDarkMode by preferencesManager.darkModeFlow.collectAsState(initial = true)

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
            // Language Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Language / Idioma", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilterChip(
                            selected = currentLanguage == "en",
                            onClick = { coroutineScope.launch { preferencesManager.saveLanguage("en") } },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = currentLanguage == "es",
                            onClick = { coroutineScope.launch { preferencesManager.saveLanguage("es") } },
                            label = { Text("Español") }
                        )
                    }
                }
            }

            // Theme Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { isDark ->
                            coroutineScope.launch { preferencesManager.toggleDarkMode(isDark) }
                        }
                    )
                }
            }
        }
    }
}