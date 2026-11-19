package io.github.valentinaschiavon99.megyejatek.core.data.scores

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RoundResult(
    val mode: String,
    val score: Int,
    val timeMs: Long,
    val correct: Int,
    val total: Int,
    val hints: Int,
    val gaveUp: Boolean,
)

data class GlobalScore(
    val username: String,
    val score: Int,
    val timeMs: Long,
    val correct: Int,
    val total: Int,
    val isMine: Boolean,
)

/**
 * Personal records live in Room (work offline); the global leaderboard lives in Firestore.
 * Firestore keeps writes made offline in its local queue and sends them when the phone is back
 * online, so finishing a round never needs a connection.
 */
@Singleton
class ScoresRepository @Inject constructor(
    private val dao: ScoreDao,
    private val auth: AuthRepository,
) {
    suspend fun save(result: RoundResult, fallbackName: String) {
        val player = auth.currentPlayer
        dao.insert(
            ScoreEntity(
                mode = result.mode,
                uid = player?.uid,
                playerName = player?.username ?: fallbackName,
                score = result.score,
                timeMs = result.timeMs,
                correct = result.correct,
                total = result.total,
                hints = result.hints,
                gaveUp = result.gaveUp,
                playedAt = System.currentTimeMillis(),
            ),
        )
        if (player != null && auth.isAvailable && !result.gaveUp) {
            // Not awaited: the write is queued locally and synced whenever possible.
            FirebaseFirestore.getInstance().collection(SCORES).add(
                mapOf(
                    "uid" to player.uid,
                    "username" to player.username,
                    "mode" to result.mode,
                    "score" to result.score.toLong(),
                    "timeMs" to result.timeMs,
                    "correct" to result.correct.toLong(),
                    "total" to result.total.toLong(),
                    "hints" to result.hints.toLong(),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
    }

    /** This player's best rounds on this phone (all local rounds when there is no account). */
    fun personalBest(mode: String, limit: Int = 20): Flow<List<ScoreEntity>> =
        dao.best(mode, auth.currentPlayer?.uid, limit)

    /** Top of the global leaderboard; throws on network or permission errors. */
    suspend fun globalTop(mode: String, limit: Long = 50): List<GlobalScore> {
        check(auth.isAvailable) { "Firebase not configured" }
        val me = auth.currentPlayer?.uid
        return FirebaseFirestore.getInstance().collection(SCORES)
            .whereEqualTo("mode", mode)
            .orderBy("score", Query.Direction.DESCENDING)
            .orderBy("timeMs", Query.Direction.ASCENDING)
            .limit(limit)
            .get()
            .await()
            .documents
            .map { d ->
                GlobalScore(
                    username = d.getString("username").orEmpty(),
                    score = d.getLong("score")?.toInt() ?: 0,
                    timeMs = d.getLong("timeMs") ?: 0,
                    correct = d.getLong("correct")?.toInt() ?: 0,
                    total = d.getLong("total")?.toInt() ?: 0,
                    isMine = d.getString("uid") == me,
                )
            }
    }

    companion object {
        const val SCORES = "scores"
    }
}
