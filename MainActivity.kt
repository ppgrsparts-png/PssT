package com.pssst.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object Holder {
    var repo: Repo? = null
    var nearby: NearbyManager? = null
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

class MainActivity : ComponentActivity() {

    private lateinit var repo: Repo
    private lateinit var nearby: NearbyManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.all { it }) {
            nearby.start()
        } else {
            repo.status = "Autorisations refusées : Pssst ne peut pas voir les gens autour. Autorisez-les dans Réglages > Applications > Pssst."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = Holder.repo ?: Repo(applicationContext).also { Holder.repo = it }
        nearby = Holder.nearby ?: NearbyManager(applicationContext, repo).also { Holder.nearby = it }
        setContent {
            PssstTheme {
                PssstApp(repo, nearby) { startNearby() }
            }
        }
    }

    private fun startNearby() {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) nearby.start() else permLauncher.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        if (isFinishing) nearby.stop()
        super.onDestroy()
    }
}
