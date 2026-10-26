package id.sch.mts241alikhlash.cbt.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudentQuestionDto(
    val id: String,
    val number: Int,
    val type: String,
    @SerialName("body_html") val bodyHtml: String,
    val choices: List<String>? = null
)

@Serializable
data class SubmitAnswerRequest(
    @SerialName("question_id") val questionId: String,
    val answer: String
)

@Serializable
data class StudentAnswerDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("question_id") val questionId: String,
    val answer: String,
    @SerialName("updated_at") val updatedAt: String
)
