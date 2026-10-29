package id.sch.mts241alikhlash.cbt.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamDtoJsonTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exam_summary_reads_the_camel_case_wire_shape() {
        val dto = json.decodeFromString<ExamSummaryDto>(
            """{"id":"e1","title":"UTS","subjectId":"s1","startsAt":"2026-11-02T01:00:00.000Z","endsAt":"2026-11-02T04:00:00.000Z","durationMinutes":90,"status":"IN_PROGRESS","sessionId":"x"}"""
        )
        assertEquals(ExamSummaryDto("e1", "UTS", "s1", "2026-11-02T01:00:00.000Z", "2026-11-02T04:00:00.000Z", 90, "IN_PROGRESS", "x"), dto)
    }

    @Test
    fun exam_summary_allows_a_null_session() {
        val dto = json.decodeFromString<ExamSummaryDto>(
            """{"id":"e1","title":"UTS","subjectId":"s1","startsAt":"a","endsAt":"b","durationMinutes":90,"status":"OPEN","sessionId":null}"""
        )
        assertNull(dto.sessionId)
    }

    @Test
    fun started_session_reads_epoch_values() {
        val dto = json.decodeFromString<StartedSessionDto>("""{"sessionId":"s1","deadline":1700000000000,"serverTime":1699999000000}""")
        assertEquals(StartedSessionDto("s1", 1700000000000, 1699999000000), dto)
    }

    @Test
    fun paper_question_reads_choices_with_keys() {
        val dto = json.decodeFromString<PaperQuestionDto>(
            """{"id":"q1","number":3,"type":"MULTIPLE_CHOICE","bodyHtml":"<p>x</p>","points":2,"choices":[{"key":"B","html":"<p>b</p>"},{"key":"A","html":"a"}]}"""
        )
        assertEquals(listOf("B", "A"), dto.choices?.map { it.key })
        assertEquals(2.0, dto.points, 0.0)
    }

    @Test
    fun paper_question_without_choices_has_null() {
        val dto = json.decodeFromString<PaperQuestionDto>("""{"id":"q1","number":1,"type":"ESSAY","bodyHtml":"x","points":5,"choices":null}""")
        assertNull(dto.choices)
    }

    @Test
    fun saved_answer_and_ack_and_submitted() {
        val saved = json.decodeFromString<SavedAnswerDto>("""{"questionId":"q1","answer":{"keys":["A","B"]}}""")
        assertEquals(AnswerPayload.Multiple(listOf("A", "B")), AnswerPayload.fromJson(saved.answer))
        val ack = json.decodeFromString<SavedAnswerAckDto>("""{"questionId":"q1","clientId":"c","receivedAfterDeadline":true,"updatedAt":"t"}""")
        assertEquals(true, ack.receivedAfterDeadline)
        val done = json.decodeFromString<SubmittedSessionDto>("""{"status":"GRADED","submittedAt":null}""")
        assertNull(done.submittedAt)
    }

    @Test
    fun save_request_serializes_camel_case() {
        val body = json.encodeToString(SaveAnswerRequest.serializer(), SaveAnswerRequest("q1", "c1", AnswerPayload.Choice("A").toJson()))
        assertEquals("""{"questionId":"q1","clientId":"c1","answer":{"key":"A"}}""", body)
    }
}
