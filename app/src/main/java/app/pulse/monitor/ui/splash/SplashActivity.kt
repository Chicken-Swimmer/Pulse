package app.pulse.monitor.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import app.pulse.monitor.ui.home.MainActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for splash screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        startActivity(Intent(applicationContext, MainActivity::class.java))
        finish()
    }
}