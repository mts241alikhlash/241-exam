package id.sch.mts241alikhlash.cbt.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExamSummaryDto(
    val id: String,
    val title: String,
    val subjectId: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int,
    val status: String,
    val sessionId: String? = null
)

@Serializable
data class StartSessionRequest(
    val accessCode: String,
    val deviceId: String
)

@Serializable
data class StartedSessionDto(
    val sessionId: String,
    val deadline: Long,
    val serverTime: Long
)

@Serializable
data class SubmittedSessionDto(
    val status: String,
    val submittedAt: String? = null
)
