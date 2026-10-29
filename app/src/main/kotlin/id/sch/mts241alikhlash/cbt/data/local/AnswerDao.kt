package id.sch.mts241alikhlash.cbt.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnswerDao {
    @Query("SELECT * FROM cached_answers WHERE sessionId = :sessionId")
    fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>>

    @Query("SELECT * FROM cached_answers WHERE sessionId = :sessionId AND questionId = :questionId LIMIT 1")
    suspend fun getAnswer(sessionId: String, questionId: String): CachedAnswerEntity?

    @Query("SELECT * FROM cached_answers WHERE sessionId = :sessionId AND isSynced = 0")
    suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity>

    @Query("SELECT COUNT(*) FROM cached_answers WHERE sessionId = :sessionId AND isSynced = 0")
    suspend fun countUnsyncedAnswers(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnswer(answer: CachedAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(answer: CachedAnswerEntity): Long

    /**
     * Marks one exact answer revision as uploaded.
     *
     * The predicate is what makes the sync race safe: if the student changed the answer
     * (or the row belongs to another session) while the upload was in flight, nothing is
     * marked and the newer revision stays pending. Returns the number of rows updated.
     */
    @Query(
        """
        UPDATE cached_answers SET isSynced = 1
        WHERE sessionId = :sessionId
          AND questionId = :questionId
          AND answer = :answer
          AND updatedAt = :updatedAt
          AND isSynced = 0
        """
    )
    suspend fun markSynced(
        sessionId: String,
        questionId: String,
        answer: String,
        updatedAt: Long
    ): Int
}
