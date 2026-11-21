package io.github.valentinaschiavon99.megyejatek.core.security

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the debug provider: on first start it logs a token (Logcat, tag
 * "DebugAppCheckProvider") that can be allowed in the Firebase console under App Check.
 */
internal object AppCheckProvider {
    fun factory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
}
