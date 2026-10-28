package id.sch.mts241alikhlash.cbt.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PaperChoiceDto(
    val key: String,
    val html: String
)

@Serializable
data class PaperQuestionDto(
    val id: String,
    val number: Int,
    val type: String,
    val bodyHtml: String,
    val points: Double,
    val choices: List<PaperChoiceDto>? = null
)

@Serializable
data class SaveAnswerRequest(
    val questionId: String,
    val clientId: String,
    val answer: JsonObject
)

@Serializable
data class SavedAnswerAckDto(
    val questionId: String,
    val clientId: String,
    val receivedAfterDeadline: Boolean,
    val updatedAt: String
)

@Serializable
data class SavedAnswerDto(
    val questionId: String,
    val answer: JsonObject
)
