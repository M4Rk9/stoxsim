package com.stoxsim.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stoxsim.app.ui.StoxSimApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 31) splashScreen.setOnExitAnimationListener { splash ->
            splash.animate().alpha(0f).setDuration(160L).withEndAction { splash.remove() }.start()
        }
        enableEdgeToEdge()
        setContent { StoxSimApp(viewModel<AppViewModel>()) }
    }
}
