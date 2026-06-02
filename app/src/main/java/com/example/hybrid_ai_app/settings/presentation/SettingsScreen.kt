package com.example.hybrid_ai_app.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.hybrid_ai_app.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import com.example.hybrid_ai_app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    rootNavController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLanguage by viewModel.currentLanguage.collectAsState(initial = "en")
    val isDarkMode by viewModel.isDarkMode.collectAsState(initial = true)
    val profileState by viewModel.profileState.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()

    var showRegenerateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.settings_header), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.cd_go_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            when (val state = profileState) {
                is ProfileState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ProfileState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = stringResource(id = R.string.dialog_regenerate_title) + ": ${state.message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is ProfileState.Success -> {
                    val user = state.user
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()

                    // Observe local preferences overrides
                    val localName by viewModel.localUserName.collectAsState(initial = null)
                    val localProfilePic by viewModel.localProfilePicPath.collectAsState(initial = null)

                    var editName by remember(localName, user.name) { mutableStateOf(localName ?: user.name ?: "") }
                    var editWeight by remember(user) { mutableStateOf(user.weight?.toString() ?: "") }
                    var editGoal by remember(user) { mutableStateOf(user.goal ?: "") }
                    var editDays by remember(user) { mutableStateOf(user.daysAvailable?.toString() ?: "") }

                    val photoPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.PickVisualMedia()
                    ) { uri: Uri? ->
                        uri?.let { validUri ->
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(validUri)
                                    val profileFile = File(context.filesDir, "avatar_${System.currentTimeMillis()}.jpg")

                                    inputStream?.use { input ->
                                        profileFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    viewModel.updateProfilePicture(profileFile.absolutePath)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AsyncImage(
                                    model = localProfilePic ?: com.google.android.gms.location.places.R.drawable.powered_by_google_light,
                                    contentDescription = stringResource(id = R.string.account_details_title),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                )

                                Column {
                                    Text(text = stringResource(id = R.string.account_details_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Text(text = stringResource(id = R.string.personal_profile_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text(text = stringResource(id = R.string.label_display_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = editWeight,
                                onValueChange = { editWeight = it },
                                label = { Text(text = stringResource(id = R.string.label_weight)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = editGoal,
                                onValueChange = { editGoal = it },
                                label = { Text(text = stringResource(id = R.string.label_main_goal)) },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = editDays,
                                onValueChange = { editDays = it },
                                label = { Text(text = stringResource(id = R.string.label_days_available)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    // Save Local Data
                                    viewModel.updateLocalName(editName)

                                    // Save Cloud Data
                                    viewModel.updateProfileMetrics(
                                        weight = editWeight.toDoubleOrNull(),
                                        goal = editGoal,
                                        daysAvailable = editDays.toIntOrNull()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isUpdating
                            ) {
                                if (isUpdating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                } else {
                                    Text(text = stringResource(id = R.string.btn_save_changes))
                                }
                            }
                        }
                    }
                    if (user.isPremium != true) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rootNavController.navigate(Screen.Paywall.route) },
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFF165239))
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color.Yellow
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.upgrade_promo_title),
                                        color = androidx.compose.ui.graphics.Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(id = R.string.upgrade_promo_desc),
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }


            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(id = R.string.app_preferences_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = R.string.label_language), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = currentLanguage == "en", onClick = { viewModel.saveLanguage("en") }, label = { Text("EN") })
                            FilterChip(selected = currentLanguage == "es", onClick = { viewModel.saveLanguage("es") }, label = { Text("ES") })
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(id = R.string.label_dark_mode), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = isDarkMode, onCheckedChange = { viewModel.toggleDarkMode(it) })
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showRegenerateDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.btn_regenerate_protocol))
                }

                OutlinedButton(
                    onClick = {
                        viewModel.logout {
                            rootNavController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = stringResource(id = R.string.btn_log_out), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.btn_log_out))
                }
            }
        }

        if (showRegenerateDialog) {
            AlertDialog(
                onDismissRequest = { showRegenerateDialog = false },
                shape = RoundedCornerShape(8.dp),
                title = { Text(text = stringResource(id = R.string.dialog_regenerate_title)) },
                text = { Text(text = stringResource(id = R.string.dialog_regenerate_text)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showRegenerateDialog = false
                            viewModel.wipeDataAndRegenerate {
                                rootNavController.navigate(Screen.Onboarding.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(text = stringResource(id = R.string.btn_yes_regenerate))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRegenerateDialog = false }) {
                        Text(text = stringResource(id = R.string.btn_cancel))
                    }
                }
            )
        }
    }
}