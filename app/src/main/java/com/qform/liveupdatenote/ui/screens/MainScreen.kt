package com.qform.liveupdatenote.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qform.liveupdatenote.data.Note
import com.qform.liveupdatenote.ui.NoteViewModel
import com.qform.liveupdatenote.ui.ThemeMode
import kotlinx.coroutines.delay

enum class NavigationTab {
    NOTES, SETTINGS
}

val NavigationTabSaver = Saver<NavigationTab, String>(
    save = { it.name },
    restore = { NavigationTab.valueOf(it) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: NoteViewModel,
    hasNotificationPermission: Boolean,
    isLiveUpdatesPromotedEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onOpenPromotionSettings: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable(stateSaver = NavigationTabSaver) { mutableStateOf(NavigationTab.NOTES) }
    
    val notes by viewModel.allNotes.collectAsState()
    val activeNote by viewModel.activeNote.collectAsState()
    
    val currentLanguage by viewModel.language.collectAsState()
    val isRu = currentLanguage == "ru"

    var showAddDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var noteType by remember { mutableStateOf("TEXT") }
    var totalStepsStr by remember { mutableStateOf("1") }
    
    // Focus requester and keyboard summon logic
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(showAddDialog) {
        if (showAddDialog) {
            // Tiny delay ensures the dialog window is fully composed and attached
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (currentTab == NavigationTab.NOTES) {
                            if (isRu) "Заметки" else "LUN"
                        } else {
                            if (isRu) "Настройки" else "Settings"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.height(68.dp)
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.NOTES,
                    onClick = { currentTab = NavigationTab.NOTES },
                    icon = { Icon(Icons.Default.List, contentDescription = "Notes") }
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    onClick = { currentTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == NavigationTab.NOTES) {
                var isFabExpanded by rememberSaveable { mutableStateOf(false) }

                androidx.activity.compose.BackHandler(isFabExpanded) {
                    isFabExpanded = false
                }

                FloatingActionButtonMenu(
                    expanded = isFabExpanded,
                    button = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                if (isFabExpanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                PlainTooltip(
                                    modifier = Modifier.semantics {
                                        liveRegion = LiveRegionMode.Assertive
                                        paneTitle = if (isRu) "Меню действий" else "Action menu"
                                    }
                                ) {
                                    Text(if (isRu) "Меню действий" else "Action menu")
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            ToggleFloatingActionButton(
                                modifier = Modifier.semantics {
                                    stateDescription = if (isFabExpanded) "Expanded" else "Collapsed"
                                    contentDescription = if (isRu) "Меню действий" else "Action menu"
                                },
                                checked = isFabExpanded,
                                onCheckedChange = { isFabExpanded = !isFabExpanded }
                            ) {
                                val imageVector = if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
                                Icon(
                                    painter = rememberVectorPainter(imageVector),
                                    contentDescription = null,
                                    modifier = Modifier.animateIcon({ checkedProgress })
                                )
                            }
                        }
                    }
                ) {
                    // Regular Note MenuItem
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabExpanded = false
                            noteType = "TEXT"
                            showAddDialog = true
                        },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text(text = if (isRu) "Обычная заметка" else "Regular Note") }
                    )

                    // Habit Tracker MenuItem
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabExpanded = false
                            noteType = "HABIT"
                            showAddDialog = true
                        },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        text = { Text(text = if (isRu) "Трекер привычек" else "Habit Tracker") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            if (currentTab == NavigationTab.NOTES) {
                // Notes List Screen
                Column(modifier = Modifier.fillMaxSize()) {
                    // Notification permission warning banner
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Уведомления отключены" else "Notifications Disabled",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = if (isRu) "Разрешите показ уведомлений, чтобы закреплять заметки." else "Live Update notifications require post permission to show on your lockscreen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onRequestPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isRu) "Разрешить" else "Allow", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Android 16 Promoted Live Updates warning banner
                    if (!isLiveUpdatesPromotedEnabled) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Live Updates Disabled",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isRu) "Продвижение отключено" else "Live Updates Disabled",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = if (isRu) "Включите продвижение в настройках для показа заметок на AOD и статус-чипе." else "Live Update system promotion is disabled. Enable it in settings to show status chips and AOD notes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = onOpenPromotionSettings,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isRu) "Включить" else "Enable", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (notes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "✍️",
                                    fontSize = 64.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    text = if (isRu) "Заметок пока нет" else "No notes created yet",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                NoteItemCard(
                                    note = note,
                                    isActive = note.isActive,
                                    isRu = isRu,
                                    onToggleActive = { viewModel.toggleNoteActive(context, note) },
                                    onDelete = { viewModel.deleteNote(note) },
                                    onIncrement = { viewModel.incrementSteps(note) },
                                    onReset = { viewModel.resetSteps(note) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Interactive Settings Screen
                SettingsScreen(viewModel = viewModel, isRu = isRu)
            }
        }
    }

    // Dialog for adding a new note
    if (showAddDialog) {
        Dialog(onDismissRequest = {
            showAddDialog = false
            noteText = ""
            noteType = "TEXT"
            totalStepsStr = "1"
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (noteType == "HABIT") {
                            if (isRu) "Новый трекер привычек" else "New Habit Tracker"
                        } else {
                            if (isRu) "Новая заметка" else "New Note"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester), // Focus request target
                        label = {
                            Text(
                                if (noteType == "HABIT") {
                                    if (isRu) "Название привычки" else "Habit Name"
                                } else {
                                    if (isRu) "Текст заметки" else "Note Content"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                if (noteType == "HABIT") {
                                    if (isRu) "Например: Пить воду" else "e.g., Drink water"
                                } else {
                                    if (isRu) "Напишите ваши мысли..." else "Write your thoughts..."
                                }
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (noteType == "HABIT") {
                        OutlinedTextField(
                            value = totalStepsStr,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    totalStepsStr = newValue
                                }
                            },
                            label = { Text(if (isRu) "Целевой прогресс (шагов)" else "Target Steps") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showAddDialog = false
                                noteText = ""
                                noteType = "TEXT"
                                totalStepsStr = "1"
                            }
                        ) {
                            Text(if (isRu) "Отмена" else "Cancel", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (noteText.isNotBlank()) {
                                    val totalSteps = totalStepsStr.toIntOrNull() ?: 1
                                    viewModel.insertNote(
                                        text = noteText,
                                        type = noteType,
                                        totalSteps = if (totalSteps > 0) totalSteps else 1
                                    )
                                    showAddDialog = false
                                    noteText = ""
                                    noteType = "TEXT"
                                    totalStepsStr = "1"
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isRu) "Сохранить" else "Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteItemCard(
    note: Note,
    isActive: Boolean,
    isRu: Boolean,
    onToggleActive: () -> Unit,
    onDelete: () -> Unit,
    onIncrement: () -> Unit,
    onReset: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "card_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "card_content"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleActive() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        color = contentColor
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Note",
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        }
                    )
                }
            }

            if (note.type == "HABIT") {
                Spacer(modifier = Modifier.height(12.dp))
                
                val progressText = if (isRu) {
                    "Выполнено: ${note.currentSteps} из ${note.totalSteps}"
                } else {
                    "Progress: ${note.currentSteps} of ${note.totalSteps}"
                }

                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                val progressFraction = if (note.totalSteps > 0) {
                    note.currentSteps.toFloat() / note.totalSteps
                } else {
                    0f
                }

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            // Prevent event propagation so clicking the button doesn't toggle active state of the note card
                            onIncrement()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isRu) "+ шаг" else "+ step", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            // Prevent event propagation so clicking the button doesn't toggle active state of the note card
                            onReset()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = contentColor
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isRu) "сбросить" else "reset", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: NoteViewModel,
    isRu: Boolean
) {
    val context = LocalContext.current
    val currentTheme by viewModel.themeMode.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isRu) "Тема оформления" else "App Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Light
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRu) "Светлая" else "Light",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = currentTheme == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) }
                    )
                }

                // Dark
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeMode(ThemeMode.DARK) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRu) "Тёмная" else "Dark",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = currentTheme == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) }
                    )
                }

                // System
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isRu) "Как на устройстве" else "System default",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = currentTheme == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) }
                    )
                }
            }
        }

        // Language Selection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isRu) "Язык приложения" else "Language",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Russian
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLanguage(context, "ru") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Русский",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = currentLanguage == "ru",
                        onClick = { viewModel.setLanguage(context, "ru") }
                    )
                }

                // English
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setLanguage(context, "en") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "English",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    RadioButton(
                        selected = currentLanguage == "en",
                        onClick = { viewModel.setLanguage(context, "en") }
                    )
                }
            }
        }

        // App Info Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isRu) "Информация о приложении" else "App Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ListItem(
                    headlineContent = { Text(if (isRu) "Версия" else "Version", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("1.1.0 (API 35/36 Live Updates)") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
