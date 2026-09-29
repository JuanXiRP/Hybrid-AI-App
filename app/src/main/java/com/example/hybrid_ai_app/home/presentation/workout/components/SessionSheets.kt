package com.example.hybrid_ai_app.home.presentation.workout.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.hybrid_ai_app.R
import com.example.hybrid_ai_app.core.domain.model.CatalogExercise
import com.example.hybrid_ai_app.home.domain.model.SessionExercise
import com.example.hybrid_ai_app.home.presentation.workout.formatClock
import kotlinx.coroutines.delay

private val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

private const val SEARCH_DEBOUNCE_MILLIS = 200L

// The backend's own limit for a workout note; an exercise note is shorter there, and is cut on sync.
private const val MAX_NOTE_LENGTH = 2000

/** Muscle names arrive lowercase from the catalog (`middle back`); shown capitalised. */
private fun String.asLabel(): String = replaceFirstChar { it.uppercase() }

/** `Quadriceps · Barbell`, skipping whatever the catalog leaves out. */
private fun CatalogExercise.summary(): String = (primaryMuscles.map { it.asLabel() } + listOfNotNull(equipment?.asLabel())).joinToString(" · ")

// -------------------------------------------------------------------------------------------
// Alternative exercise
// -------------------------------------------------------------------------------------------

/**
 * "The machine is busy": exercises that train the same muscle, ranked from most to least similar.
 * Picking one replaces the exercise (see `WorkoutSessionViewModel.replaceExercise`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlternativesSheet(
    exerciseName: String,
    loadAlternatives: suspend () -> List<CatalogExercise>,
    onPick: (CatalogExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    // null while loading, so an empty result and "not loaded yet" are not confused.
    var alternatives by remember { mutableStateOf<List<CatalogExercise>?>(null) }
    LaunchedEffect(Unit) { alternatives = loadAlternatives() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetTitle(
                title = stringResource(id = R.string.session_alternatives_title),
                subtitle = "$exerciseName · ${stringResource(id = R.string.session_alternatives_subtitle)}",
            )

            when (val list = alternatives) {
                null -> CenteredProgress()
                else -> if (list.isEmpty()) {
                    CenteredMessage(stringResource(id = R.string.session_alternatives_empty))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(list, key = { it.id }) { entry ->
                            CatalogListItem(entry = entry, onClick = { onPick(entry) })
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Add exercise
// -------------------------------------------------------------------------------------------

/** Search the catalog by name, narrow it by muscle, and add what is found. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExerciseSheet(
    search: suspend (query: String, muscle: String?) -> List<CatalogExercise>,
    loadMuscles: suspend () -> List<String>,
    onPick: (CatalogExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var muscles by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<List<CatalogExercise>?>(null) }

    LaunchedEffect(Unit) { muscles = loadMuscles() }
    LaunchedEffect(query, muscle) {
        // A pause after the last keystroke, so typing does not run a search per letter.
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        results = search(query, muscle)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetTitle(title = stringResource(id = R.string.session_add_title))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(id = R.string.session_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = muscle == null,
                        onClick = { muscle = null },
                        label = { Text(stringResource(id = R.string.session_filter_all)) },
                    )
                }
                items(muscles) { name ->
                    FilterChip(
                        selected = muscle == name,
                        onClick = { muscle = if (muscle == name) null else name },
                        label = { Text(name.asLabel()) },
                    )
                }
            }

            when (val list = results) {
                null -> CenteredProgress()
                else -> if (list.isEmpty()) {
                    CenteredMessage(stringResource(id = R.string.session_search_empty))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(list, key = { it.id }) { entry ->
                            CatalogListItem(entry = entry, onClick = { onPick(entry) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogListItem(entry: CatalogExercise, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { ExerciseThumbnail(entry = entry, size = 44.dp) },
        headlineContent = { Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.summary(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

// -------------------------------------------------------------------------------------------
// Exercise info
// -------------------------------------------------------------------------------------------

/** Everything the catalog knows about an exercise: pictures, muscles, equipment, level, steps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseInfoSheet(
    exerciseName: String,
    entry: CatalogExercise?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = entry?.name ?: exerciseName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )

            if (entry == null) {
                Text(
                    text = stringResource(id = R.string.session_info_unavailable),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (entry.imageUrls.isNotEmpty()) InfoImages(entry)

                InfoRow(R.string.session_info_primary, entry.primaryMuscles.joinToString { it.asLabel() })
                InfoRow(R.string.session_info_secondary, entry.secondaryMuscles.joinToString { it.asLabel() })
                InfoRow(R.string.session_info_equipment, entry.equipment?.asLabel())
                InfoRow(R.string.session_info_level, entry.level?.asLabel())

                if (entry.instructions.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(id = R.string.session_info_instructions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    entry.instructions.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(text = step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }

                // The catalog is English only, and the app does not pretend otherwise.
                Text(
                    text = stringResource(id = R.string.session_info_english_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun InfoImages(entry: CatalogExercise) {
    val pagerState = rememberPagerState(pageCount = { entry.imageUrls.size })

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) { page ->
            AsyncImage(
                model = entry.imageUrls[page],
                contentDescription = stringResource(id = R.string.session_thumbnail_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
        if (entry.imageUrls.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${entry.imageUrls.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(labelRes: Int, value: String?) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(id = R.string.session_info_none),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

// -------------------------------------------------------------------------------------------
// Reorder
// -------------------------------------------------------------------------------------------

/** Reordering with up and down buttons: no drag library, and it works with TalkBack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSheet(
    exercises: List<SessionExercise>,
    onMove: (from: Int, to: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            SheetTitle(title = stringResource(id = R.string.session_reorder_title))

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(exercises, key = { _, exercise -> exercise.id }) { index, exercise ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(exercise.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Text("${index + 1}", fontWeight = FontWeight.Bold) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(id = R.string.session_move_up))
                                }
                                IconButton(onClick = { onMove(index, index + 1) }, enabled = index < exercises.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(id = R.string.session_move_down))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------
// Rest pickers and text input
// -------------------------------------------------------------------------------------------

/** Chooses how long the rest after one exercise lasts, or turns it off (0), for this session only. */
@Composable
fun ExerciseRestDialog(
    currentSeconds: Int,
    choices: List<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.session_rest_choose_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(seconds) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == currentSeconds, onClick = { onPick(seconds) })
                        Text(
                            text = if (seconds == 0) stringResource(id = R.string.session_rest_time_off) else formatClock(seconds.toLong()),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    )
}

/** The timer button's picker: start a rest that belongs to no set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRestSheet(
    choices: List<Int>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            SheetTitle(title = stringResource(id = R.string.session_rest_manual_title))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 0 means "off" for an exercise; it makes no sense as a timer to start.
                items(choices.filter { it > 0 }) { seconds ->
                    FilterChip(
                        selected = false,
                        onClick = { onPick(seconds) },
                        label = { Text(formatClock(seconds.toLong())) },
                    )
                }
            }
        }
    }
}

/** A dialog with one multi-line text box: the workout note and the per-exercise note. */
@Composable
fun NoteDialog(
    title: String,
    hint: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NOTE_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(hint) },
                minLines = 3,
                maxLines = 6,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(id = R.string.session_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.btn_cancel)) } },
    )
}

// -------------------------------------------------------------------------------------------
// Small shared pieces
// -------------------------------------------------------------------------------------------

@Composable
private fun SheetTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
