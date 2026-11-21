package io.github.valentinaschiavon99.megyejatek

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import io.github.valentinaschiavon99.megyejatek.core.security.AppCheckProvider
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MegyejatekApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) enableStrictMode()
        installAppCheck()
    }

    /**
     * Firebase App Check: every Auth and Firestore request carries a token proving it comes from
     * this app on a genuine device. Skipped in builds without Firebase (no google-services.json).
     * Enforcement is switched on per service in the Firebase console, so this can ship first and
     * the request metrics checked before anything is blocked.
     */
    private fun installAppCheck() {
        if (FirebaseApp.getApps(this).isEmpty()) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(AppCheckProvider.factory())
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
