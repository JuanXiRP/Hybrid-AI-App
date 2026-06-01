package com.example.hybrid_ai_app.auth.presentation

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hybrid_ai_app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Required for Google Sign-In UI components
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // --- APP LOGO ---
        Image(
            painter = painterResource(id = R.drawable.logo_hybrid_ai), // Placeholder
            contentDescription = "App Logo",
            modifier = Modifier.size(200.dp)
        )


        Spacer(modifier = Modifier.height(48.dp))

        // --- AUTH MODE SELECTOR (Pill Toggle) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Row(modifier = Modifier.padding(4.dp)) {
                ModeButton(
                    text = "Sign In",
                    isSelected = viewModel.isLoginMode,
                    modifier = Modifier.weight(1f),
                    onClick = { if (!viewModel.isLoginMode) viewModel.toggleAuthMode() }
                )
                ModeButton(
                    text = "Sign Up",
                    isSelected = !viewModel.isLoginMode,
                    modifier = Modifier.weight(1f),
                    onClick = { if (viewModel.isLoginMode) viewModel.toggleAuthMode() }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- INPUT FIELDS ---
        OutlinedTextField(
            value = viewModel.email, // 🟢 Conectado al estado
            onValueChange = { viewModel.updateEmail(it) }, // 🟢 Conectado a la acción
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            enabled = !viewModel.isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.password, // 🟢 Conectado al estado
            onValueChange = { viewModel.updatePassword(it) }, // 🟢 Conectado a la acción
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            enabled = !viewModel.isLoading
        )

        if (viewModel.isLoginMode) {
            TextButton(
                onClick = { /* TODO: Forgot Password Flow */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Forgot password?", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- MAIN ACTION BUTTON ---
        Button(
            onClick = {
                // 🟢 Ahora ejecuta la lógica, guarda el token y luego navega
                viewModel.authenticate(
                    onSuccess = { onAuthSuccess() },
                    onError = { errorMsg ->
                        // Aquí podrías mostrar un Snackbar o un Toast con el errorMsg
                        Log.e("Auth", "Error de Login: $errorMsg")
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            enabled = !viewModel.isLoading
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    text = if (viewModel.isLoginMode) "Sign In" else "Create Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- MODERN GOOGLE SIGN IN BUTTON ---
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    handleGoogleSignIn(context, credentialManager) { googleToken ->
                        Log.d("AuthScreen", "Google Token ready for backend: $googleToken")
                        // TODO: viewModel.loginWithGoogle(googleToken)
                        onAuthSuccess()
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // TODO: Add Google vector icon here
                Text("Continue with Google", color = MaterialTheme.colorScheme.onBackground)
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun ModeButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = if (isSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null,
        shape = CircleShape
    ) {
        Text(text, fontSize = 14.sp)
    }
}

// Handles the native Google Bottom Sheet UI and token extraction
suspend fun handleGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onTokenReceived: (String) -> Unit
) {
    val webClientId = "543093378504-kfijjptjpfahl605fum5i5gp99kjqlcc.apps.googleusercontent.com"

    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false) // Allows user to pick any account
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            request = request,
            context = context,
        )

        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            // Success: send this token to the backend
            val idToken = credential.idToken
            onTokenReceived(idToken)
        } else {
            Log.e("GoogleAuth", "Unexpected credential type")
        }
    } catch (e: GetCredentialException) {
        Log.e("GoogleAuth", "Sign In failed: ${e.message}")
    }
}