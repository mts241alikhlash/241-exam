package id.sch.mts241alikhlash.cbt

import id.sch.mts241alikhlash.cbt.data.model.PaperChoiceDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto

fun choices(vararg html: String): List<PaperChoiceDto> =
    html.mapIndexed { index, text -> PaperChoiceDto(('A' + index).toString(), text) }

fun ackFor(request: SaveAnswerRequest) = SavedAnswerAckDto(
    questionId = request.questionId,
    clientId = request.clientId,
    receivedAfterDeadline = false,
    updatedAt = "2026-10-27T00:00:00Z"
)
