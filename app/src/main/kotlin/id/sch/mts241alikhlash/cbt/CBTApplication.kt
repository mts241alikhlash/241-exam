package id.sch.mts241alikhlash.cbt

import android.app.Application
import id.sch.mts241alikhlash.cbt.data.local.ExamDatabase
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import id.sch.mts241alikhlash.cbt.data.session.DataStoreSessionStore
import id.sch.mts241alikhlash.cbt.data.session.SessionRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class CBTApplication : Application() {
    lateinit var repository: ExamRepository
        private set

    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()

        sessionRepository = SessionRepository(DataStoreSessionStore(this))

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(ExamApiService::class.java)
        val database = ExamDatabase.getInstance(this)

        repository = ExamRepository(api, database.answerDao())
    }
}
