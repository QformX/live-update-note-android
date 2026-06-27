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
        const val CHANNEL_ID = "live_update_note_channel_v5"
        
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

        val bigText = "Active note loading..."
        val bigTextStyle = Notification.BigTextStyle()
            .bigText(bigText)
            .setBigContentTitle(getString(R.string.app_name))

        builder.setContentTitle(getString(R.string.app_name))
            .setContentText(bigText)
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

        // 3. Action Button - "Deactivate" note directly from notification
        val deactivateActionIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, LiveUpdateService::class.java).apply { action = ACTION_DEACTIVATE },
            pendingIntentFlags
        )

        // Create short preview for Android 16 Status Bar Chip (e.g., setShortCriticalText)
        val shortPreview = if (note.text.length > 15) {
            note.text.take(12) + "..."
        } else {
            note.text
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

        val bigTextStyle = Notification.BigTextStyle()
            .bigText(note.text)
            .setBigContentTitle(getString(R.string.app_name))

        builder.setContentTitle(getString(R.string.app_name))
            .setContentText(note.text)
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
            .setStyle(bigTextStyle)
            .addExtras(extras)

        // Add explicit action button for deactivation
        val deactivateAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Notification.Action.Builder(
                R.drawable.ic_notification,
                "Deactivate",
                deactivateActionIntent
            ).build()
        } else {
            null
        }
        if (deactivateAction != null) {
            builder.addAction(deactivateAction)
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
