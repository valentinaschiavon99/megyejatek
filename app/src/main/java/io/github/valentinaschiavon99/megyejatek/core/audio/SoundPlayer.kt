package io.github.valentinaschiavon99.megyejatek.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class Sound(@param:RawRes val res: Int) {
    Correct(R.raw.sound_correct),
    Wrong(R.raw.sound_wrong),
    Finish(R.raw.sound_finish),
    Lose(R.raw.sound_lose),
}

/**
 * Short game sounds through SoundPool (low latency, preloaded). Plays as "game" audio, so the
 * media volume and Do Not Disturb apply, and stays silent when "Hangok" is off in the settings.
 */
@Singleton
class SoundPlayer @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids: Map<Sound, Int> = Sound.entries.associateWith { pool.load(context, it.res, 1) }

    @Volatile private var enabled = true

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { enabled = it.sound }
        }
    }

    fun play(sound: Sound) {
        if (!enabled) return
        val id = ids[sound] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    private companion object {
        const val VOLUME = 0.7f
    }
}
