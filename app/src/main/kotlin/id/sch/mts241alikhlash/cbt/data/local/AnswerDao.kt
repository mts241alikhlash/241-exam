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

    @Query("SELECT * FROM cached_answers WHERE questionId = :questionId LIMIT 1")
    suspend fun getAnswer(questionId: String): CachedAnswerEntity?

    @Query("SELECT * FROM cached_answers WHERE sessionId = :sessionId AND isSynced = 0")
    suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnswer(answer: CachedAnswerEntity)

    @Query("UPDATE cached_answers SET isSynced = 1 WHERE questionId = :questionId")
    suspend fun markSynced(questionId: String)
}
