package com.callonlines.softphone

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import org.linphone.core.tools.service.CoreService

class CallOnLinesCoreService : CoreService() {

    override fun onCreate() {
        DiagLog.i("CoreService onCreate")
        super.onCreate()
    }

    override fun createServiceNotification() {
        DiagLog.i("CoreService createServiceNotification")
        val openCall = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openCall,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mServiceNotification = NotificationCompat.Builder(this, SoftphoneApp.CHANNEL_CALL_ID)
            .setContentTitle("CallOnLines")
            .setContentText("Llamada en curso")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

    override fun showForegroundServiceNotification(isVideoCall: Boolean) {
        DiagLog.i("CoreService showForegroundServiceNotification(video=$isVideoCall)")
        try {
            if (mServiceNotification == null) createServiceNotification()
            val notif: Notification = mServiceNotification ?: NotificationCompat.Builder(
                this, SoftphoneApp.CHANNEL_CALL_ID
            ).setContentTitle("CallOnLines")
              .setContentText("Llamada")
              .setSmallIcon(android.R.drawable.sym_action_call)
              .setOngoing(true)
              .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                try {
                    startForeground(NOTIF_ID, notif, type)
                    DiagLog.i("startForeground OK (typed)")
                } catch (e: Throwable) {
                    DiagLog.e("startForeground typed failed", e)
                    try { startForeground(NOTIF_ID, notif); DiagLog.i("startForeground OK (no type)") }
                    catch (e2: Throwable) { DiagLog.e("startForeground untyped also failed", e2) }
                }
            } else {
                startForeground(NOTIF_ID, notif)
                DiagLog.i("startForeground OK (pre-Q)")
            }
        } catch (t: Throwable) {
            DiagLog.e("showForegroundServiceNotification threw", t)
        }
    }

    override fun hideForegroundServiceNotification() {
        DiagLog.i("CoreService hideForegroundServiceNotification")
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (t: Throwable) {
            DiagLog.e("stopForeground failed", t)
        }
    }

    private companion object { const val NOTIF_ID = 4242 }
}
