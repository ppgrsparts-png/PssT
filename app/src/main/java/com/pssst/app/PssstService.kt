package com.pssst.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Service au premier plan : garde Pssst visible et à l'écoute
 * même quand l'application est fermée ou l'écran éteint.
 */
class PssstService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val (repo, nearby) = Holder.get(this)

        if (intent?.action == ACTION_STOP) {
            repo.setVisibleFlag(false)
            nearby.stop()
            repo.status = "En pause : vous n’êtes plus visible et ne voyez plus personne."
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            ServiceCompat.startForeground(
                this, Notifs.ID_SERVICE, Notifs.service(this),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            )
        } catch (e: Exception) {
            // Autorisations manquantes : Pssst fonctionnera seulement application ouverte
            stopSelf()
            return START_NOT_STICKY
        }

        if (repo.profile == null || !repo.visible) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        nearby.start()
        return START_STICKY
    }

    companion object {
        const val ACTION_STOP = "com.pssst.app.STOP"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, PssstService::class.java))
            } catch (e: Exception) {
                // démarrage refusé par le système : l'app reste fonctionnelle au premier plan
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PssstService::class.java))
        }
    }
}
