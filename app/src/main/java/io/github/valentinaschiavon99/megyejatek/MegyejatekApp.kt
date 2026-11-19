package io.github.valentinaschiavon99.megyejatek

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MegyejatekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
    }

    /**
     * Debug builds only: logs disk or network work on the main thread, leaked closeables,
     * cleartext traffic and unsafe intents to Logcat (tag "StrictMode"), so problems show
     * up while developing instead of as jank or bugs on players' phones.
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build())
    }
}
