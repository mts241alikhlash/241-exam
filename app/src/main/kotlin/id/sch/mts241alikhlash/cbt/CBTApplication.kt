package id.sch.mts241alikhlash.cbt

import android.app.Application
import id.sch.mts241alikhlash.cbt.data.auth.AuthApiService
import id.sch.mts241alikhlash.cbt.data.auth.AuthInterceptor
import id.sch.mts241alikhlash.cbt.data.auth.AuthRepository
import id.sch.mts241alikhlash.cbt.data.auth.KeystoreTokenStore
import id.sch.mts241alikhlash.cbt.data.auth.SupervisorVerifier
import id.sch.mts241alikhlash.cbt.data.auth.TokenAuthenticator
import id.sch.mts241alikhlash.cbt.data.auth.authLoggingInterceptor
import id.sch.mts241alikhlash.cbt.data.local.ExamDatabase
import id.sch.mts241alikhlash.cbt.data.remote.DeviceIdInterceptor
import id.sch.mts241alikhlash.cbt.data.remote.EnvelopeConverterFactory
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.remote.ExamCatalogApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
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

    lateinit var authRepository: AuthRepository
        private set

    lateinit var supervisorVerifier: SupervisorVerifier
        private set

    lateinit var catalogRepository: ExamCatalogRepository
        private set

    @Volatile
    var examActive: Boolean = false

    override fun onCreate() {
        super.onCreate()

        sessionRepository = SessionRepository(DataStoreSessionStore(this))

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val authClient = OkHttpClient.Builder()
            .addInterceptor(authLoggingInterceptor(BuildConfig.DEBUG))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val authApi = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApiService::class.java)

        supervisorVerifier = SupervisorVerifier(authApi) { deviceId() }

        authRepository = AuthRepository(
            api = authApi,
            store = KeystoreTokenStore(this),
            deviceId = { deviceId() }
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(DeviceIdInterceptor { deviceId() })
            .addInterceptor(AuthInterceptor(authRepository))
            .authenticator(TokenAuthenticator(authRepository) { examActive })
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(EnvelopeConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val api = retrofit.create(ExamApiService::class.java)
        catalogRepository = ExamCatalogRepository(
            api = retrofit.create(ExamCatalogApiService::class.java),
            deviceId = { deviceId() }
        )
        val database = ExamDatabase.getInstance(this)

        repository = ExamRepository(api, database.answerDao(), database.questionDao())
    }

    private fun deviceId(): String =
        android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            .orEmpty()
}
