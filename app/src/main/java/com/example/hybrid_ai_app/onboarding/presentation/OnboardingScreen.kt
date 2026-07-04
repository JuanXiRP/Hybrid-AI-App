package com.example.hybrid_ai_app.onboarding.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.ui.theme.HybridTrainingTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onFinishOnboarding: () -> Unit
) {
    if (viewModel.isLoading) {
        LoadingScreen(message = "Calibrating your hybrid macrocycle...")
    } else {
        val currentStep = viewModel.currentStep
        val state = viewModel.uiState
        val scrollState = rememberScrollState()

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // 1. BOX PRINCIPAL
        Box(modifier = Modifier.fillMaxSize()) {

            // 2. COLUMNA DE CONTENIDO
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(50.dp))

                //HEADER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.only_logo),
                        contentDescription = "Hybrid Icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(100.dp)
                    )
                    Spacer(modifier = Modifier.width(30.dp))
                    Image(
                        painter = painterResource(id = R.drawable.only_text),
                        contentDescription = "Hybrid Wordmark",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(100.dp)
                    )
                }

                //STEPPER PROGRESS BAR
                LinearProgressIndicator(
                    progress = { currentStep.toFloat() / viewModel.totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(32.dp))

                //DYNAMIC MULTI-STEP FORM
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    AnimatedContent(
                        targetState = currentStep,
                        label = "OnboardingStepTransition"
                    ) { step ->
                        when (step) {
                            1 -> StepOneMetrics(state, viewModel)
                            2 -> StepTwoProfile(state, viewModel)
                            3 -> StepThreeLogistics(state, viewModel)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                //BOTTOM NAVIGATION CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { viewModel.previousStep() },
                            modifier = Modifier.height(50.dp),
                            shape = CircleShape
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            val errorMessage = viewModel.validateCurrentStep()

                            if (errorMessage != null) {
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    snackbarHostState.showSnackbar(
                                        message = errorMessage,
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            } else {
                                if (currentStep < viewModel.totalSteps) {
                                    viewModel.nextStep()
                                } else {
                                    viewModel.submitOnboarding(
                                        onSuccess = { onFinishOnboarding() },
                                        onError = { errorMsg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = errorMsg,
                                                    duration = SnackbarDuration.Long
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.height(50.dp),
                        shape = CircleShape
                    ) {
                        Text(if (currentStep == viewModel.totalSteps) "Generate Plan" else "Next")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3. CAPA FLOTANTE DEL SNACKBAR
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
            ) { data ->
                Snackbar(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun StepOneMetrics(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text(
            text = "Your Biometrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MetricInputCard(
                label = "WEIGHT",
                value = state.weight,
                unit = "KG",
                modifier = Modifier.weight(1f),
                onValueChange = { viewModel.updateWeight(it) }
            )
            MetricInputCard(
                label = "HEIGHT",
                value = state.height,
                unit = "CM",
                modifier = Modifier.weight(1f),
                onValueChange = { viewModel.updateHeight(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        MetricInputCard(
            label = "AGE",
            value = state.age,
            unit = "",
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { viewModel.updateAge(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Biological Sex",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("male", "female").forEach { item ->
                PillSelectionButton(
                    text = item.replaceFirstChar { it.uppercase() },
                    isSelected = state.sex == item,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateSex(item) }
                )
            }
        }

        // Cycle-aware onboarding: only females provide their last period start date
        if (state.sex == "female") {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Last Period Start Date",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            PeriodDateField(
                value = state.lastPeriodDate,
                onDateSelected = { viewModel.updateLastPeriodDate(it) }
            )
            Text(
                "Used to adapt your plan to your menstrual cycle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodDateField(
    value: String,
    onDateSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value.ifBlank { "Select date" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.SemiBold
            )
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                // A period can't start in the future
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val isoDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            onDateSelected(isoDate)
                        }
                        showDialog = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(0.dp)
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun StepTwoProfile(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text(
            "Athletic Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Define your focus and baseline athletic capacity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Primary Fitness Goal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("endurance", "strength", "both").forEach { goalItem ->
                PillSelectionButton(
                    text = if (goalItem == "both") "Hybrid" else goalItem.replaceFirstChar { it.uppercase() },
                    isSelected = state.goal == goalItem,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateGoal(goalItem) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Current Experience Level",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("beginner", "intermediate", "advanced").forEach { levelItem ->
                PillSelectionButton(
                    text = levelItem.replaceFirstChar { it.uppercase() },
                    isSelected = state.fitnessLevel == levelItem,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.updateFitnessLevel(levelItem) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.injuriesInput,
            onValueChange = { viewModel.updateInjuries(it) },
            label = { Text("Medical History / Injuries (Optional)") },
            placeholder = { Text("e.g. knee tendinitis, lower back pain") },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        )
        Text(
            "Separate multiple conditions with commas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

@Composable
fun StepThreeLogistics(state: OnboardingState, viewModel: OnboardingViewModel) {
    Column {
        Text(
            "Training Logistics",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Calibrate plan execution periods and availability constraints.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Weekly Availability: ${state.daysAvailable} days",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = state.daysAvailable.toFloat(),
            onValueChange = { viewModel.updateDaysAvailable(it.toInt()) },
            valueRange = 1f..7f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "Macrocycle Macro Duration",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(4, 8, 12).forEach { weeks ->
                PillSelectionButton(
                    text = "$weeks Weeks",
                    isSelected = state.planDuration == weeks,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updatePlanDuration(weeks) }
                )
            }
        }
    }
}

@Composable
fun PillSelectionButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.4f
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun MetricInputCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                focusRequester.requestFocus()
            },
        shape = MaterialTheme.shapes.medium,
        color = if (isFocused) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.2f
            )
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 28.dp, horizontal = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        if (newValue.length <= 3) {
                            onValueChange(newValue)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused }
                        .width(85.dp),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )

                if (unit.isNotEmpty()) {
                    Text(
                        text = unit.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

