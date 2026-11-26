package dev.charlesferreira.treino

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.charlesferreira.treino.timer.RestTimer
import dev.charlesferreira.treino.ui.nav.TreinoAppRoot
import dev.charlesferreira.treino.ui.theme.TreinoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent bars with light icons: the app is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { TreinoTheme { TreinoAppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        RestTimer.appInForeground = true
        RestTimer.resumeIfActive(this)
    }

    override fun onPause() {
        RestTimer.appInForeground = false
        super.onPause()
    }
}
