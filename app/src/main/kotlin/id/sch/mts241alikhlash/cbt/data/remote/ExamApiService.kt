package id.sch.mts241alikhlash.cbt.data.remote

import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.model.ViolationCountDto
import id.sch.mts241alikhlash.cbt.data.model.ViolationReport
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ExamApiService {
    @GET("student-exams/{sessionId}/questions")
    suspend fun getQuestions(
        @Path("sessionId") sessionId: String
    ): List<PaperQuestionDto>

    @GET("student-exams/{sessionId}/answers")
    suspend fun getAnswers(
        @Path("sessionId") sessionId: String
    ): List<SavedAnswerDto>

    @POST("student-exams/{sessionId}/answers")
    suspend fun saveAnswer(
        @Path("sessionId") sessionId: String,
        @Body request: SaveAnswerRequest
    ): SavedAnswerAckDto

    @POST("student-exams/{sessionId}/submit")
    suspend fun submit(
        @Path("sessionId") sessionId: String,
        @Body request: SubmitRequest
    ): SubmittedSessionDto

    @POST("student-exams/{sessionId}/violations")
    suspend fun reportViolations(
        @Path("sessionId") sessionId: String,
        @Body request: ViolationReport
    ): ViolationCountDto = ViolationCountDto(request.count)
}
