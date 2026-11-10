package com.pssst.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notifications : service actif, pssst reçus, nouveaux amis, messages. */
object Notifs {
    const val CH_SERVICE = "service"
    const val CH_SOCIAL = "social"
    const val ID_SERVICE = 1
    const val EXTRA_TARGET = "target"

    /** Cibles d'ouverture : un id de conversation, ou l'une de celles-ci. */
    const val OPEN_NEARBY = "@nearby"
    const val OPEN_FRIEND = "@friend:"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Pssst actif", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indique que Pssst vous rend visible en arrière-plan"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SOCIAL, "Pssst, amis et messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openIntent(ctx: Context, target: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (target != null) i.putExtra(EXTRA_TARGET, target)
        return PendingIntent.getActivity(
            ctx, (target ?: "main").hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun service(ctx: Context): Notification {
        val stop = PendingIntent.getService(
            ctx, 1, Intent(ctx, PssstService::class.java).setAction(PssstService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Pssst est actif")
            .setContentText("Vous êtes visible et recevez les messages des personnes autour")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent(ctx, null))
            .addAction(0, "Mettre en pause", stop)
            .build()
    }

    private fun allowed(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun social(ctx: Context, key: String, title: String, text: String, target: String?) {
        if (!allowed(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_SOCIAL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF7C4DFF.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, target))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(key.hashCode(), n)
        } catch (e: Exception) {
            // notifications refusées : ignoré
        }
    }

    fun cancel(ctx: Context, key: String) {
        NotificationManagerCompat.from(ctx).cancel(key.hashCode())
    }
}
