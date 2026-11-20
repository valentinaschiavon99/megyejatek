package io.github.valentinaschiavon99.megyejatek.core.data.scores

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One finished round, stored on the phone. */
@Entity(tableName = "scores", indices = [Index("mode", "score")])
data class ScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    /** Firebase uid, or null for rounds played without an account. */
    val uid: String?,
    val playerName: String,
    val score: Int,
    val timeMs: Long,
    val correct: Int,
    val total: Int,
    val hints: Int,
    val gaveUp: Boolean,
    val playedAt: Long,
)

@Dao
interface ScoreDao {
    @Insert
    suspend fun insert(score: ScoreEntity): Long

    /** Best completed rounds for a mode: highest score first, then fastest. */
    @Query(
        """
        SELECT * FROM scores
        WHERE mode = :mode AND gaveUp = 0 AND (:uid IS NULL OR uid = :uid)
        ORDER BY score DESC, timeMs ASC
        LIMIT :limit
        """,
    )
    fun best(mode: String, uid: String?, limit: Int): Flow<List<ScoreEntity>>

    @Query("DELETE FROM scores WHERE uid = :uid")
    suspend fun deleteForUser(uid: String): Int
}

@Database(entities = [ScoreEntity::class], version = 1, exportSchema = true)
abstract class ScoreDatabase : RoomDatabase() {
    abstract fun scores(): ScoreDao
}
