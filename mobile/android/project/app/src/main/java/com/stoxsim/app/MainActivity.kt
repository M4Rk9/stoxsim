package com.stoxsim.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stoxsim.app.ui.StoxSimApp

class MainActivity : ComponentActivity() {
    private val model by viewModels<AppViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { splash ->
            splash.animate().alpha(0f).setDuration(160L).withEndAction { splash.remove() }.start()
        }
        enableEdgeToEdge()
        receiveLink(intent)
        setContent { StoxSimApp(model) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveLink(intent)
    }

    private fun receiveLink(incoming: Intent) {
        if (incoming.action != Intent.ACTION_VIEW) return
        val raw = incoming.dataString
        // Do not leave the secret URL on the activity's reusable launch intent.
        incoming.data = null
        incoming.action = Intent.ACTION_MAIN
        model.receiveEmailLink(raw)
    }
}
