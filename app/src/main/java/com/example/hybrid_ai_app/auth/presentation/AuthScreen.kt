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
import androidx.compose.ui.res.stringResource
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
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption

@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: (hasCompletedOnboarding: Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Image(
                painter = painterResource(id = R.drawable.logo_hybrid_ai),
                contentDescription = stringResource(id = R.string.cd_app_logo),
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    ModeButton(
                        text = stringResource(id = R.string.btn_sign_in_mode),
                        isSelected = viewModel.isLoginMode,
                        modifier = Modifier.weight(1f),
                        onClick = { if (!viewModel.isLoginMode) viewModel.toggleAuthMode() }
                    )
                    ModeButton(
                        text = stringResource(id = R.string.btn_sign_up_mode),
                        isSelected = !viewModel.isLoginMode,
                        modifier = Modifier.weight(1f),
                        onClick = { if (viewModel.isLoginMode) viewModel.toggleAuthMode() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.updateEmail(it) },
                label = { Text(text = stringResource(id = R.string.label_email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = !viewModel.isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text(text = stringResource(id = R.string.label_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                enabled = !viewModel.isLoading
            )

            if (viewModel.isLoginMode) {
                TextButton(
                    onClick = {  },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(id = R.string.btn_forgot_password), color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // REGISTER BUTTON
            Button(
                onClick = {
                    viewModel.authenticate(
                        onSuccess = { hasCompletedOnboarding ->
                            onAuthSuccess(hasCompletedOnboarding)
                        },
                        onError = { errorMsg ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(errorMsg)
                            }
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
                        text = if (viewModel.isLoginMode) stringResource(id = R.string.btn_sign_in_mode) else stringResource(id = R.string.btn_create_account),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            //GOOGLE SIGN IN BUTTON
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        handleGoogleSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onTokenReceived = { googleToken ->
                                viewModel.loginWithGoogle(
                                    idToken = googleToken,
                                    onSuccess = { hasCompletedOnboarding -> onAuthSuccess(hasCompletedOnboarding) },
                                    onError = { errorMsg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }
                                    }
                                )
                            },
                            onAuthFailed = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Google sign-in failed. Please try again.")
                                }
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                enabled = !viewModel.isLoading
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(id = R.string.btn_continue_google), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
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
        Text(text = text, fontSize = 14.sp)
    }
}

suspend fun handleGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onTokenReceived: (String) -> Unit,
    onAuthFailed: () -> Unit
) {
    val webClientId = "394206999877-a7hjgm2ofls8k3dces9m4d5rl0u71fsn.apps.googleusercontent.com"

    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .build()

    try {
        val result = credentialManager.getCredential(
            request = request,
            context = context,
        )

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
            onTokenReceived(googleIdCredential.idToken)
        } else {
            Log.e("GoogleAuth", "Tipo de credencial inesperado: ${credential.type}")
            onAuthFailed()
        }
    } catch (e: GetCredentialCancellationException) {
        Log.e("GoogleAuth", "Cancelado o cerrado por el usuario: ${e.message}")
        onAuthFailed()
    } catch (e: NoCredentialException) {
        Log.e("GoogleAuth", "No hay credenciales (revisa cuentas/SHA-1): ${e.message}")
        onAuthFailed()
    } catch (e: GetCredentialException) {
        Log.e("GoogleAuth", "Error de credencial [${e.type}]: ${e.message}")
        onAuthFailed()
    } catch (e: Exception) {
        Log.e("GoogleAuth", "Error inesperado: ${e.message}")
        onAuthFailed()
    }
}