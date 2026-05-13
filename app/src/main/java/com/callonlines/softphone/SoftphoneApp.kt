package com.callonlines.softphone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SoftphoneApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        installCrashHandler()
        createNotificationChannels()
        try {
            LinphoneManager.init(this)
            DiagLog.i("LinphoneManager.init invoked")
        } catch (t: Throwable) {
            DiagLog.e("LinphoneManager.init threw", t)
        }
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                DiagLog.e("UNCAUGHT in ${thread.name}", throwable)
            } catch (_: Throwable) {}
            prev?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val callChannel = NotificationChannel(
            CHANNEL_CALL_ID,
            "Llamadas en curso",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificacion persistente mientras hay una llamada activa"
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(callChannel)
    }

    companion object {
        const val CHANNEL_CALL_ID = "callonlines_call_channel"
    }
}
