package id.sch.mts241alikhlash.cbt.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_answers")
data class CachedAnswerEntity(
    @PrimaryKey val questionId: String,
    val sessionId: String,
    val answer: String,
    val isDoubtful: Boolean = false,
    val isSynced: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
