package com.example.hybrid_ai_app.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.coach.data.presentation.ChatMessage
import com.example.hybrid_ai_app.coach.data.presentation.MessageSender
import com.example.hybrid_ai_app.coach.presentation.CoachViewModel
import com.example.hybrid_ai_app.core.presentation.PremiumBottomSheet
import com.example.hybrid_ai_app.home.presentation.components.HybridTopAppBar
import com.example.hybrid_ai_app.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: CoachViewModel = hiltViewModel()
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isLoading by viewModel.isLoading.collectAsState()
    val profilePicPath by viewModel.localProfilePicPath.collectAsState(initial = null)
    val entitlement by viewModel.entitlement.collectAsState()
    val premiumPrompt by viewModel.premiumPrompt.collectAsState()

    premiumPrompt?.let { reason ->
        PremiumBottomSheet(
            reason = reason,
            chatLimit = entitlement.chatLimit,
            onDismiss = viewModel::dismissPremiumPrompt,
            onSeePlans = {
                viewModel.dismissPremiumPrompt()
                // Root graph: the paywall is full-screen, without the bottom bar.
                rootNavController.navigate(Screen.Paywall.route)
            },
        )
    }

    // Auto-scroll to the latest message when the feed expands
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            HybridTopAppBar(
                title = stringResource(id = R.string.coach_title),
                profilePicPath = profilePicPath,
                onProfileClick = { navController.navigate("settings") }
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
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(viewModel.messages) { message ->
                    ChatBubbleRow(message = message)
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            // Input Interaction Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 16.dp
            ) {
                Column {
                    ChatQuotaChip(
                        used = entitlement.chatUsed,
                        limit = entitlement.chatLimit,
                        isReadOnly = entitlement.isReadOnly,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val canSend = entitlement.canSendChatMessage

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = stringResource(
                                        id = if (canSend) {
                                            R.string.input_placeholder_coach
                                        } else {
                                            R.string.chat_input_locked
                                        }
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            enabled = !isLoading && canSend
                        )

                        IconButton(
                            onClick = { /* Voice processing execution */ },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(id = R.string.cd_voice_input),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (!canSend) {
                                    // The ViewModel decides which paywall copy applies
                                    // (quota spent vs trial over).
                                    viewModel.showPremiumPrompt()
                                } else if (inputText.isNotBlank()) {
                                    viewModel.sendUserMessage(inputText)
                                    inputText = ""
                                }
                            },
                            shape = RoundedCornerShape(50),
                            containerColor = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (canSend) Icons.Default.PlayArrow else Icons.Default.Lock,
                                contentDescription = stringResource(id = R.string.cd_send_message),
                                tint = if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "1 of 2 messages today". Hidden for premium (limit == null) and while read-only. */
@Composable
private fun ChatQuotaChip(used: Int, limit: Int?, isReadOnly: Boolean) {
    if (limit == null || isReadOnly) return

    val exhausted = used >= limit

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (exhausted) {
                stringResource(id = R.string.chat_quota_reached)
            } else {
                stringResource(id = R.string.chat_messages_counter, used, limit)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (exhausted) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
fun ChatBubbleRow(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}