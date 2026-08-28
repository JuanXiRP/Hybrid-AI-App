package com.example.hybrid_ai_app.home.presentation

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.util.NotificationHelper

@Composable
fun PaywallScreen(
    navController: NavController,
    viewModel: PaywallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Instantiate the Notification Helper
    val notificationHelper = remember { NotificationHelper(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // One-shot events: a Channel, so rotating the device does not re-notify or re-navigate.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.PurchaseSucceeded -> {
                    notificationHelper.showPremiumWelcomeNotification()
                    navController.popBackStack()
                }

                PaywallEvent.PurchaseRestored -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.purchase_restored))
                    navController.popBackStack()
                }

                PaywallEvent.NothingToRestore ->
                    snackbarHostState.showSnackbar(context.getString(R.string.purchase_nothing_to_restore))

                PaywallEvent.PurchaseCancelled ->
                    snackbarHostState.showSnackbar(context.getString(R.string.purchase_cancelled))

                PaywallEvent.PurchasePending ->
                    snackbarHostState.showSnackbar(context.getString(R.string.purchase_pending))

                is PaywallEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Header Image Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.paywall_bg_image),
                    contentDescription = stringResource(id = R.string.cd_profile_settings),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = Color(0xFF1E5641).copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.exclusive_access),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.paywall_title),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(id = R.string.feature_ai_generations_title),
                    description = stringResource(id = R.string.feature_ai_generations_desc)
                )
                FeatureCard(
                    icon = Icons.Default.Email,
                    title = stringResource(id = R.string.feature_coach_title),
                    description = stringResource(id = R.string.feature_coach_desc)
                )
                FeatureCard(
                    icon = Icons.Default.Star,
                    title = stringResource(id = R.string.feature_analytics_title),
                    description = stringResource(id = R.string.feature_analytics_desc)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PriceLabel(uiState)

                Text(
                    text = stringResource(id = R.string.pricing_sub_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Button(
                    onClick = {
                        (context as? Activity)?.let(viewModel::launchPurchaseFlow)
                    },
                    // Without a resolved offer there is nothing to buy — Play would reject the flow.
                    enabled = uiState.offer != null && !uiState.isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF165239))
                ) {
                    if (uiState.isPurchasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = stringResource(id = R.string.btn_unlock_premium),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = viewModel::restorePurchases,
                    enabled = !uiState.isBusy
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = stringResource(id = R.string.btn_restore_purchase),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLabel(uiState: PaywallUiState) {
    val text = when {
        uiState.offer != null -> stringResource(
            id = R.string.pricing_label,
            uiState.offer.formattedPrice,
            billingPeriodLabel(uiState.offer.billingPeriodIso),
        )

        uiState.offerError != null -> stringResource(id = R.string.pricing_label_unavailable)
        else -> stringResource(id = R.string.pricing_label_loading)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (uiState.offerError != null) {
            MaterialTheme.colorScheme.error
        } else {
            Color(0xFF4A7C59)
        }
    )
}

/**
 * Maps Play's ISO-8601 billing period onto a localized noun. We only sell P1M today; the other
 * branches keep the label honest if the Play Console product is ever changed.
 */
@Composable
private fun billingPeriodLabel(iso: String): String = when (iso) {
    "P1M" -> stringResource(id = R.string.period_month)
    "P1Y" -> stringResource(id = R.string.period_year)
    "P1W" -> stringResource(id = R.string.period_week)
    "P1D" -> stringResource(id = R.string.period_day)
    else -> iso
}

@Composable
fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFB5E4CA).copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF165239))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
