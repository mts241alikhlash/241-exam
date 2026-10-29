package id.sch.mts241alikhlash.cbt.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExamSummaryDto(
    val id: String,
    val title: String,
    @SerialName("subject_name") val subjectName: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val status: String
)

@Serializable
data class StartSessionRequest(
    @SerialName("access_code") val accessCode: String,
    @SerialName("device_id") val deviceId: String
)

@Serializable
data class StartedSessionDto(
    @SerialName("session_id") val sessionId: String,
    val deadline: Long,
    @SerialName("server_time") val serverTime: Long
)
