package io.github.valentinaschiavon99.megyejatek.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.valentinaschiavon99.megyejatek.core.security.InputSanitizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    /** Null until the player has entered a name in the "Új játék" dialog once. */
    val playerName: String? = null,
    /** "Vaktérkép": hide labels and county borders. */
    val blindMap: Boolean = false,
    /** Short sounds for right, wrong and the end of a round. */
    val sound: Boolean = true,
    /** The first-run account screen was answered (signed in, or "play as guest"); never shown again. */
    val onboarded: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            playerName = prefs[Keys.PLAYER_NAME]?.takeIf { it.isNotBlank() },
            blindMap = prefs[Keys.BLIND_MAP] ?: false,
            sound = prefs[Keys.SOUND] ?: true,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
        )
    }

    suspend fun setPlayerName(name: String) {
        // Sanitised here too, so nothing unclean is stored whatever the caller does.
        dataStore.edit { it[Keys.PLAYER_NAME] = InputSanitizer.nickname(name) }
    }

    suspend fun setBlindMap(enabled: Boolean) {
        dataStore.edit { it[Keys.BLIND_MAP] = enabled }
    }

    suspend fun setSound(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setOnboarded() {
        dataStore.edit { it[Keys.ONBOARDED] = true }
    }

    private object Keys {
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val BLIND_MAP = booleanPreferencesKey("blind_map")
        val SOUND = booleanPreferencesKey("sound")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
