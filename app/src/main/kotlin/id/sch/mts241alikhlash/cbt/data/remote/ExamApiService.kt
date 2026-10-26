package id.sch.mts241alikhlash.cbt.data.remote

import id.sch.mts241alikhlash.cbt.data.model.StudentAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitAnswerRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ExamApiService {
    @GET("student-exams/{sessionId}/questions")
    suspend fun getQuestions(
        @Path("sessionId") sessionId: String
    ): List<StudentQuestionDto>

    @POST("student-exams/{sessionId}/answers")
    suspend fun saveAnswer(
        @Path("sessionId") sessionId: String,
        @Body request: SubmitAnswerRequest
    ): StudentAnswerDto
}
