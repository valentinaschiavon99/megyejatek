package io.github.valentinaschiavon99.megyejatek.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoreDao
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoreDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScoreDatabase =
        Room.databaseBuilder(context, ScoreDatabase::class.java, "scores.db").build()

    @Provides
    fun provideScoreDao(db: ScoreDatabase): ScoreDao = db.scores()
}
