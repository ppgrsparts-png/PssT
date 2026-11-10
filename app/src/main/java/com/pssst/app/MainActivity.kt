package com.pssst.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Données partagées entre l'écran et le service d'arrière-plan. */
object Holder {
    var repo: Repo? = null
    var nearby: NearbyManager? = null

    /** Vrai quand l'écran Pssst est affiché. */
    @Volatile
    var foreground = false

    @Synchronized
    fun get(ctx: Context): Pair<Repo, NearbyManager> {
        val app = ctx.applicationContext
        val r = repo ?: Repo(app).also {
            repo = it
            Notifs.channels(app)
        }
        val n = nearby ?: NearbyManager(app, r).also { nearby = it }
        return r to n
    }
}

fun requiredPermissions(): List<String> {
    val l = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= 31) {
        l.add(Manifest.permission.BLUETOOTH_SCAN)
        l.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        l.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        l.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    return l
}

/** Facultatives : l'app marche sans, mais sans notifications. */
fun optionalPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

class MainActivity : ComponentActivity() {

    private lateinit var repo: Repo
    private lateinit var nearby: NearbyManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (missing(requiredPermissions()).isEmpty()) {
            launchNearby()
        } else {
            repo.status = "Autorisations refusées : Pssst ne peut pas voir les gens autour. Autorisez-les dans Réglages > Applications > Pssst."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (r, n) = Holder.get(this)
        repo = r
        nearby = n
        handleIntent(intent)
        setContent {
            PssstTheme {
                PssstApp(repo, nearby, startNearby = { startNearby() }, stopNearby = { stopNearby() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(Notifs.EXTRA_TARGET)?.let { repo.openTarget = it }
        i?.removeExtra(Notifs.EXTRA_TARGET)
    }

    override fun onResume() {
        super.onResume()
        Holder.foreground = true
    }

    override fun onPause() {
        Holder.foreground = false
        super.onPause()
    }

    private fun missing(list: List<String>) = list.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun startNearby() {
        val toAsk = missing(requiredPermissions() + optionalPermissions())
        if (toAsk.isEmpty()) launchNearby() else permLauncher.launch(toAsk.toTypedArray())
    }

    private fun launchNearby() {
        if (!repo.visible || repo.profile == null) return
        PssstService.start(this) // reste actif en arrière-plan
        nearby.start()           // et démarre tout de suite au premier plan
    }

    private fun stopNearby() {
        PssstService.stop(this)
        nearby.stop()
    }
}
