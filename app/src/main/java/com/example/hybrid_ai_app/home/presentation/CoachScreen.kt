package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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

enum class MessageSender {
    COACH, USER
}

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val hasMetricsCard: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(navController: NavController) {
    var inputText by remember { mutableStateOf("") }

    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage("m1", "¡Hola Juan! He analizado tu sesión de fuerza de hoy. Tu RPE en Banca ha sido óptimo, pero veo margen de mejora en la recuperación.", MessageSender.COACH),
            ChatMessage("m2", "Para maximizar la transferencia al bloque de carrera de mañana, te sugiero priorizar estos dos pilares esta noche:", MessageSender.COACH, hasMetricsCard = true),
            ChatMessage("m3", "Perfecto Coach, ¿aumento los carbohidratos en la cena de hoy?", MessageSender.USER)
        )
    }

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = "GEMINI COACH AI",
                onProfileClick = { navController.navigate(Screen.Settings.route) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Chat Message Feed
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(chatMessages) { message ->
                    ChatBubbleRow(message = message)
                }
            }

            // Input Interaction Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Chat TextField
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask Gemini Coach...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Audio Input Mock Button (Microphone structure)
                    IconButton(
                        onClick = { /* Will interconnect with SpeechToText API */ },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, // Placeholder for mic icon
                            contentDescription = "Voice Input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Send Action Button
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                chatMessages.add(ChatMessage(System.currentTimeMillis().toString(), inputText, MessageSender.USER))
                                inputText = ""
                            }
                        },
                        shape = RoundedCornerShape(50),
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(message: ChatMessage) {
    val isCoach = message.sender == MessageSender.COACH

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCoach) Alignment.Start else Alignment.End
    ) {
        // Text Bubble
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isCoach) 4.dp else 16.dp,
                        bottomEnd = if (isCoach) 16.dp else 4.dp
                    )
                )
                .background(
                    if (isCoach) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCoach) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            )
        }

        // Conditional Rich Metric UI Cards inside the chat pipeline
        if (isCoach && message.hasMetricsCard) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleep Recommendation Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Deep Sleep", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("8.5 hrs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Target rest window", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                }
                // Hydration Recommendation Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Hydration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("+1.2L", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Add mineral salts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}