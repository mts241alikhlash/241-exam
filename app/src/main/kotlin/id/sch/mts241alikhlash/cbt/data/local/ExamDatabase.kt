package id.sch.mts241alikhlash.cbt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CachedAnswerEntity::class, CachedQuestionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ExamDatabase : RoomDatabase() {
    abstract fun answerDao(): AnswerDao
    abstract fun questionDao(): QuestionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_questions` (
                        `sessionId` TEXT NOT NULL,
                        `id` TEXT NOT NULL,
                        `number` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `bodyHtml` TEXT NOT NULL,
                        `choicesJson` TEXT,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`sessionId`, `id`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: ExamDatabase? = null

        fun getInstance(context: Context): ExamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExamDatabase::class.java,
                    "exam_cbt_db"
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
