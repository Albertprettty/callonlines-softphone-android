package com.callonlines.softphone

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import org.linphone.core.tools.service.CoreService

class CallOnLinesCoreService : CoreService() {

    override fun createServiceNotification() {
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
            .setSmallIcon(R.drawable.ic_call_small)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun showForegroundServiceNotification(isVideoCall: Boolean) {
        if (mServiceNotification == null) createServiceNotification()
        val notif: Notification = mServiceNotification ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            try {
                startForeground(NOTIF_ID, notif, type)
            } catch (e: Exception) {
                e.printStackTrace()
                try { startForeground(NOTIF_ID, notif) } catch (_: Exception) {}
            }
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    override fun hideForegroundServiceNotification() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    private companion object {
        const val NOTIF_ID = 4242
    }
}
