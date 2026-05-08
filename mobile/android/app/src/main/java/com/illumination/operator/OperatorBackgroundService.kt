package com.illumination.operator

import android.annotation.SuppressLint
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
import android.os.PowerManager
import android.util.Log
import com.illumination.operator.engine.OperatorEngineRuntime
import com.illumination.operator.engine.OperatorToolInstaller

class OperatorBackgroundService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val appFilesDir = intent?.getStringExtra(EXTRA_APP_FILES_DIR)
            ?: filesDir.absolutePath
        startInForeground()
        acquireWakeLock()
        installRuntimeEnvironment(appFilesDir)
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        OperatorEngineRuntime.shutdown()
        super.onDestroy()
    }

    private fun startInForeground() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun installRuntimeEnvironment(appFilesDir: String) {
        runCatching {
            OperatorToolInstaller.installBundledTools(this, appFilesDir)
            OperatorToolInstaller.applyProcessEnvironment(this, appFilesDir)
        }.onFailure { error ->
            Log.w(LOG_TAG, "failed to prepare background runtime environment", error)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:codex-runtime")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock
            ?.takeIf(PowerManager.WakeLock::isHeld)
            ?.release()
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Operator runtime",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Codex work running while Operator is backgrounded."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val launchPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_operator)
            .setContentTitle("Operator is running Codex")
            .setContentText("Background processing is kept active for local turns and tools.")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(launchPendingIntent)
            .build()
    }

    companion object {
        private const val LOG_TAG = "OperatorBackground"
        private const val CHANNEL_ID = "operator-background-runtime"
        private const val NOTIFICATION_ID = 2401
        private const val ACTION_START = "com.illumination.operator.action.START_BACKGROUND_RUNTIME"
        private const val ACTION_STOP = "com.illumination.operator.action.STOP_BACKGROUND_RUNTIME"
        private const val EXTRA_APP_FILES_DIR = "appFilesDir"

        fun start(context: Context, appFilesDir: String) {
            val intent = Intent(context, OperatorBackgroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_APP_FILES_DIR, appFilesDir)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OperatorBackgroundService::class.java)
                    .setAction(ACTION_STOP),
            )
        }
    }
}
