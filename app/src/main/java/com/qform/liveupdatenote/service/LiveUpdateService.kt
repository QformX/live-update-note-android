package com.qform.liveupdatenote.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.qform.liveupdatenote.MainActivity
import com.qform.liveupdatenote.R
import com.qform.liveupdatenote.data.Note
import com.qform.liveupdatenote.data.NoteDatabase
import com.qform.liveupdatenote.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Foreground Service that manages the life cycle of the Live Update notification.
 * It observes the active note database flow and updates the system notification.
 */
class LiveUpdateService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: NoteRepository
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val NOTIFICATION_ID = 9901
        const val CHANNEL_ID = "live_update_note_channel_v6"
        
        const val ACTION_START = "com.qform.liveupdatenote.action.START"
        const val ACTION_DEACTIVATE = "com.qform.liveupdatenote.action.DEACTIVATE"
    }

    private var hasBeenActivated = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val database = NoteDatabase.getDatabase(applicationContext)
        repository = NoteRepository(database.noteDao)
        
        createNotificationChannel()
        showPlaceholderNotification() // Call startForeground synchronously to satisfy Android OS requirements
        observeActiveNote()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DEACTIVATE -> {
                // User dismissed or clicked deactivate - remove the active status in Room
                serviceScope.launch {
                    repository.setActiveNote(null)
                }
            }
            ACTION_START -> {
                // Handled by the flow observer, but ensures service is alive
            }
        }
        return START_STICKY
    }

    private fun showPlaceholderNotification() {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putCharSequence("android.shortCriticalText", "Syncing...")
        }

        val rawText = "Active note loading..."
        val formattedText = getFormattedLargeText(rawText)
        
        val bigTextStyle = Notification.BigTextStyle()
            .bigText(formattedText)
            .setBigContentTitle("LUN")

        builder.setContentTitle("LUN")
            .setContentText(formattedText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_STATUS)
            .setStyle(bigTextStyle)
            .addExtras(extras)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getFormattedLargeText(text: String): CharSequence {
        val spannable = android.text.SpannableString(text)
        // 1.25x larger text size
        spannable.setSpan(
            android.text.style.RelativeSizeSpan(1.25f),
            0,
            text.length,
            android.text.SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        // Bold styling
        spannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            0,
            text.length,
            android.text.SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun observeActiveNote() {
        serviceScope.launch {
            repository.activeNote
                .distinctUntilChanged()
                .collect { activeNote ->
                    if (activeNote != null) {
                        hasBeenActivated = true
                        updateNotification(activeNote)
                    } else {
                        if (hasBeenActivated) {
                            // User explicitly deactivated the active note, shut down service cleanly
                            stopForegroundCompat()
                            stopSelf()
                        } else {
                            // First emission is null. Double check directly in database to make sure it's not a race
                            val directActive = repository.getActiveNoteDirect()
                            if (directActive == null) {
                                stopForegroundCompat()
                                stopSelf()
                            } else {
                                hasBeenActivated = true
                                updateNotification(directActive)
                            }
                        }
                    }
                }
        }
    }

    private fun updateNotification(note: Note) {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 1. Content Intent - Click notification to open app
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags
        )

        // 2. Delete Intent - Sync state if user swipes it away
        val deleteIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LiveUpdateService::class.java).apply { action = ACTION_DEACTIVATE },
            pendingIntentFlags
        )

        // Create short preview for Android 16 Status Bar Chip (e.g., setShortCriticalText)
        val shortPreview = if (note.text.length > 15) {
            note.text.take(12) + "..."
        } else {
            note.text
        }

        val isHabit = note.type == "HABIT"

        // Load active language preferences to localize notifications
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isRu = (prefs.getString("language", "en") ?: "en") == "ru"

        val formattedTitle = if (isHabit) note.text else "LUN"
        val formattedText = if (isHabit) {
            if (isRu) {
                "Выполнено: ${note.currentSteps} из ${note.totalSteps}. Осталось: ${note.totalSteps - note.currentSteps}"
            } else {
                "Completed: ${note.currentSteps} of ${note.totalSteps}. Remaining: ${note.totalSteps - note.currentSteps}"
            }
        } else {
            getFormattedLargeText(note.text)
        }

        // Build notification using Notification.Builder as required
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val extras = android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putCharSequence("android.shortCriticalText", shortPreview)
        }

        builder.setContentTitle(formattedTitle)
            .setContentText(formattedText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .addExtras(extras)

        if (isHabit) {
            // Action Button 1: Increment steps
            val incrementIntent = Intent(this, HabitActionReceiver::class.java).apply {
                action = HabitActionReceiver.ACTION_INCREMENT
                putExtra(HabitActionReceiver.EXTRA_NOTE_ID, note.id)
            }
            val incrementPendingIntent = PendingIntent.getBroadcast(
                this,
                101,
                incrementIntent,
                pendingIntentFlags
            )
            val incrementTitle = if (isRu) "+ шаг" else "+ step"
            builder.addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification),
                    incrementTitle,
                    incrementPendingIntent
                ).build()
            )

            // Action Button 2: Reset steps (0)
            val resetIntent = Intent(this, HabitActionReceiver::class.java).apply {
                action = HabitActionReceiver.ACTION_RESET
                putExtra(HabitActionReceiver.EXTRA_NOTE_ID, note.id)
            }
            val resetPendingIntent = PendingIntent.getBroadcast(
                this,
                102,
                resetIntent,
                pendingIntentFlags
            )
            val resetTitle = if (isRu) "сбросить" else "reset"
            builder.addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notification),
                    resetTitle,
                    resetPendingIntent
                ).build()
            )

            // Configure Android 16 ProgressStyle dynamically using reflection
            applyHabitProgressStyle(builder, note)

            // Fallback: traditional progress bar for Android < 16
            if (Build.VERSION.SDK_INT < 36) {
                builder.setProgress(note.totalSteps, note.currentSteps, false)
            }
        } else {
            // Text Note Style
            val bigTextStyle = Notification.BigTextStyle()
                .bigText(formattedText)
                .setBigContentTitle(formattedTitle)
            builder.setStyle(bigTextStyle)
        }

        // Apply Android 16 Live Update configurations via compatibility reflection
        applyLiveUpdateCompat(builder, shortPreview)

        val notification = builder.build()

        // Start in foreground or update existing notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Dynamically configures the Android 16+ Notification.ProgressStyle using reflection.
     * This avoids compile-time failures on projects targeting older Android SDK compile versions (e.g. 35).
     */
    private fun applyHabitProgressStyle(builder: Notification.Builder, note: Note) {
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // Get ProgressStyle class
                val progressStyleClass = Class.forName("android.app.Notification\$ProgressStyle")
                val progressStyle = progressStyleClass.getConstructor().newInstance()

                // Call setStyledByProgress(false) as per best practices
                val setStyledByProgressMethod = progressStyleClass.getMethod("setStyledByProgress", Boolean::class.javaPrimitiveType)
                setStyledByProgressMethod.invoke(progressStyle, false)

                // 1. Set progress: progress value should be currentSteps * segmentLength (100)
                // This ensures the progress marker aligns perfectly with the segments and doesn't overshoot
                val setProgressMethod = progressStyleClass.getMethod("setProgress", Int::class.javaPrimitiveType)
                val progressValue = note.currentSteps * 100
                setProgressMethod.invoke(progressStyle, progressValue)

                // 2. Set progress tracker icon: .setProgressTrackerIcon(Icon)
                // Use R.drawable.ic_f1_car (F1 Car vector icon) as the tracker icon
                val setProgressTrackerIconMethod = progressStyleClass.getMethod("setProgressTrackerIcon", android.graphics.drawable.Icon::class.java)
                val trackerIcon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_f1_car)
                setProgressTrackerIconMethod.invoke(progressStyle, trackerIcon)

                // 3. Set segments: loop through total steps, active steps are colored, remaining are gray
                val segmentClass = Class.forName("android.app.Notification\$ProgressStyle\$Segment")
                val segmentConstructor = segmentClass.getConstructor(Int::class.javaPrimitiveType)
                val setColorMethod = segmentClass.getMethod("setColor", Int::class.javaPrimitiveType)
                val addProgressSegmentMethod = progressStyleClass.getMethod("addProgressSegment", segmentClass)

                val activeColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getColor(android.R.color.system_accent1_400)
                } else {
                    0xFF4CAF50.toInt() // fallback green
                }

                val inactiveColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getColor(android.R.color.system_neutral1_500)
                } else {
                    0xFF757575.toInt() // fallback gray
                }

                for (i in 0 until note.totalSteps) {
                    val color = if (i < note.currentSteps) activeColor else inactiveColor
                    val segment = segmentConstructor.newInstance(100) // Equal weighting segments of length 100
                    setColorMethod.invoke(segment, color)
                    addProgressSegmentMethod.invoke(progressStyle, segment)
                }

                // 4. Attach progress style to notification builder: builder.setStyle(progressStyle)
                val setStyleMethod = Notification.Builder::class.java.getMethod("setStyle", Class.forName("android.app.Notification\$Style"))
                setStyleMethod.invoke(builder, progressStyle)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Safe reflection helper to configure Android 16 Live Update Notification APIs:
     * - setRequestPromotedOngoing(true)
     * - setShortCriticalText(text)
     */
    private fun applyLiveUpdateCompat(builder: Notification.Builder, shortText: String) {
        // Safe check for Android 16 API level (36) or above
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                // Invoke setRequestPromotedOngoing(true)
                val setRequestPromotedOngoingMethod = Notification.Builder::class.java.getMethod(
                    "setRequestPromotedOngoing",
                    Boolean::class.javaPrimitiveType
                )
                setRequestPromotedOngoingMethod.invoke(builder, true)

                // Invoke setShortCriticalText(shortText)
                val setShortCriticalTextMethod = Notification.Builder::class.java.getMethod(
                    "setShortCriticalText",
                    CharSequence::class.java
                )
                setShortCriticalTextMethod.invoke(builder, shortText)
            } catch (e: Exception) {
                // Fall back silently if SDK does not contain these API methods
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
