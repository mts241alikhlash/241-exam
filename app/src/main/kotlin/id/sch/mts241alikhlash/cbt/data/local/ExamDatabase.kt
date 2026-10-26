package id.sch.mts241alikhlash.cbt.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedAnswerEntity::class], version = 1, exportSchema = false)
abstract class ExamDatabase : RoomDatabase() {
    abstract fun answerDao(): AnswerDao

    companion object {
        @Volatile
        private var INSTANCE: ExamDatabase? = null

        fun getInstance(context: Context): ExamDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExamDatabase::class.java,
                    "exam_cbt_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
