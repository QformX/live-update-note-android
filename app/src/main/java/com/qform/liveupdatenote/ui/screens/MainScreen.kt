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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    var currentTab by remember { mutableStateOf(NavigationTab.NOTES) }
    
    val notes by viewModel.allNotes.collectAsState()
    val activeNote by viewModel.activeNote.collectAsState()
    
    val currentLanguage by viewModel.language.collectAsState()
    val isRu = currentLanguage == "ru"

    var showAddDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    
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
                            if (isRu) "Заметки" else "Live Notes"
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
                modifier = Modifier.height(64.dp)
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.NOTES,
                    onClick = { currentTab = NavigationTab.NOTES },
                    icon = { Icon(Icons.Default.List, contentDescription = "Notes") },
                    label = { Text(if (isRu) "Заметки" else "Notes", fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.SETTINGS,
                    onClick = { currentTab = NavigationTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(if (isRu) "Настройки" else "Settings", fontWeight = FontWeight.Bold) }
                )
            }
        },
        floatingActionButton = {
            if (currentTab == NavigationTab.NOTES) {
                // Simple plus (+) in a circle FAB
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp),
                    modifier = Modifier.size(84.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add, 
                        contentDescription = if (isRu) "Добавить заметку" else "Add Note",
                        modifier = Modifier.size(42.dp)
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
                                    onToggleActive = { viewModel.toggleNoteActive(context, note) },
                                    onDelete = { viewModel.deleteNote(note) }
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
                        text = if (isRu) "Создать заметку" else "Create Note",
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
                        placeholder = { Text(if (isRu) "Напишите ваши мысли..." else "Write your thoughts...") },
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                showAddDialog = false
                                noteText = ""
                            }
                        ) {
                            Text(if (isRu) "Отмена" else "Cancel", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (noteText.isNotBlank()) {
                                    viewModel.insertNote(noteText)
                                    showAddDialog = false
                                    noteText = ""
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
    onToggleActive: () -> Unit,
    onDelete: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
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

                ListItem(
                    headlineContent = { Text(if (isRu) "База данных" else "Database Sync", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(if (isRu) "Локальная БД Room (100% оффлайн)" else "Local Room Database (100% Offline)") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}
