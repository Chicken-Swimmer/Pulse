package app.pulse.monitor

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.pulse.monitor.utils.Constants
import app.pulse.monitor.utils.SharedPrefsManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import app.pulse.monitor.utils.NetworkRestore
import app.pulse.monitor.utils.Utils

class MyApplication : Application(), DefaultLifecycleObserver {

    object ActivityVisibility {
        var appIsVisible: Boolean = false
        @JvmStatic
        fun resumeApp() { appIsVisible = true }
        @JvmStatic
        fun pauseApp() { appIsVisible = false }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        SharedPrefsManager.init(this)
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> false }
                .build()
        )
        if (SharedPrefsManager.customPrefs.getInt(Constants.THEME_MODE, -1) == -1) {
            SharedPrefsManager.customPrefs.edit()
                .putInt(Constants.THEME_MODE, Constants.THEME_MODE_DARK)
                .apply()
        }
        Utils.applyThemeMode(Utils.getCurrentThemeMode())
        NetworkRestore.register(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStop(owner)
        ActivityVisibility.pauseApp()
    }

    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        ActivityVisibility.resumeApp()
    }
}