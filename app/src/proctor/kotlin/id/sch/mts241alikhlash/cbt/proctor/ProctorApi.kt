package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context
import id.sch.mts241alikhlash.cbt.CBTApplication
import id.sch.mts241alikhlash.cbt.data.model.CameraBlockedBody
import id.sch.mts241alikhlash.cbt.data.model.StreamTokenDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The proctoring endpoints live here, not on the shared `ExamApiService`, so the
 * `standard` flavor carries no reference to them and the existing test fakes of
 * that interface keep compiling unchanged.
 */
interface ProctorApiService {
    @POST("student-exams/{sessionId}/stream-token")
    suspend fun streamToken(@Path("sessionId") sessionId: String): StreamTokenDto

    /** Keeps the seat live without rotating the publish token the stream uses. */
    @POST("student-exams/{sessionId}/stream-heartbeat")
    suspend fun streamHeartbeat(@Path("sessionId") sessionId: String)

    @POST("student-exams/{sessionId}/camera-blocked")
    suspend fun reportCameraBlocked(
        @Path("sessionId") sessionId: String,
        @Body request: CameraBlockedBody,
    )
}

internal object ProctorApi {
    fun service(context: Context): ProctorApiService =
        (context.applicationContext as CBTApplication).retrofit
            .create(ProctorApiService::class.java)
}
