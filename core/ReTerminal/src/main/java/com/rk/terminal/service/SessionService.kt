package com.rk.terminal.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.rk.resources.drawables
import com.rk.terminal.api.ReTerminal
import com.rk.terminal.session.TerminalSessionController
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class SessionService : Service() {
    val controller = TerminalSessionController(::handleSessionsChanged)

    private fun handleSessionsChanged() {
        if (controller.isEmpty()) {
            stopSelf()
        } else {
            updateNotification()
        }
    }

    inner class SessionBinder : Binder() {
        val sessionController: TerminalSessionController
            get() = controller

        fun terminateAllSessions() {
            controller.terminateAllSessions()
        }

        fun createSession(id: String, client: TerminalSessionClient, context: Context): TerminalSession {
            return controller.createSession(id, client, context)
        }

        fun getSession(id: String): TerminalSession? {
            return controller.getSession(id)
        }

        fun terminateSession(id: String) {
            controller.terminateSession(id)
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        controller.destroy()
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        ReTerminal.initIfPossible(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_EXIT" -> {
                controller.destroy()
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitIntent = Intent(this, SessionService::class.java).apply {
            action = "ACTION_EXIT"
        }
        val exitPendingIntent = PendingIntent.getService(
            this, 1, exitIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ReTerminal")
            .setContentText(getNotificationContentText())
            .setSmallIcon(drawables.terminal)
            .setContentIntent(pendingIntent)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "EXIT",
                    exitPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private val CHANNEL_ID = "session_service_channel"

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Session Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Terminal Service"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(1, notification)
    }

    private fun getNotificationContentText(): String {
        val count = controller.sessionList.size
        if (count == 1){
            return "1 session running"
        }
        return "$count sessions running"
    }
}
