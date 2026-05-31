package io.dmcc.bleptt.service

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
import androidx.core.app.NotificationCompat
import io.dmcc.bleptt.MainActivity
import io.dmcc.bleptt.PttApp
import io.dmcc.bleptt.R
import io.dmcc.bleptt.ble.PttBleClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps the BLE connection alive while the screen is off and other apps are in the foreground —
 * the whole point of using a native BLE button instead of an HID/media-key remap.
 */
class PttForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundWithNotification(client(), text = "Waiting for PTT button…")
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun observeState() {
        observer?.cancel()
        observer = scope.launch {
            val client = client()
            val app = application as PttApp
            val overlay = app.overlayController
            combine(
                client.state,
                client.pressed,
                app.overlayPreferences.settings,
            ) { state, pressed, overlaySettings ->
                Triple(state, pressed, overlaySettings)
            }
                .distinctUntilChanged()
                .collect { (state, pressed, overlaySettings) ->
                    updateNotification(describe(state, pressed))
                    if (pressed) overlay.show(overlaySettings) else overlay.hide()
                }
        }
    }

    private fun describe(state: PttBleClient.ConnectionState, pressed: Boolean): String =
        when (state) {
            is PttBleClient.ConnectionState.Connected ->
                if (pressed) "Transmitting" else "Connected • idle"
            is PttBleClient.ConnectionState.Connecting -> "Connecting…"
            is PttBleClient.ConnectionState.Scanning -> "Scanning…"
            is PttBleClient.ConnectionState.Disconnected -> "Disconnected — waiting for press"
            is PttBleClient.ConnectionState.Error -> "Error: ${state.message}"
            PttBleClient.ConnectionState.Idle -> "Idle"
        }

    private fun client(): PttBleClient = (application as PttApp).bleClient

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BLE PTT connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the BLE PTT button connected while the app is in the background."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification(
        client: PttBleClient,
        text: String,
    ) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Touch the client so it's initialised even if the activity is gone.
        client.isBluetoothEnabled()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PttForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ptt_notification)
            .setContentTitle("BLE PTT")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        observer?.cancel()
        (application as PttApp).overlayController.hide()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ble_ptt_connection"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "io.dmcc.bleptt.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PttForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PttForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
