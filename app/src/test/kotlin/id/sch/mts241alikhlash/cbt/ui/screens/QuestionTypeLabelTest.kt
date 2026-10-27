package id.sch.mts241alikhlash.cbt.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionTypeLabelTest {
    @Test
    fun `maps known types`() {
        assertEquals("PILIHAN GANDA", questionTypeLabel("multiple_choice"))
        assertEquals("BENAR / SALAH", questionTypeLabel("true_false"))
        assertEquals("URAIAN", questionTypeLabel("essay"))
        assertEquals("JAWABAN SINGKAT", questionTypeLabel("short_answer"))
    }

    @Test
    fun `unknown type falls back to readable uppercase`() {
        assertEquals("MATCHING PAIRS", questionTypeLabel("matching_pairs"))
    }

    @Test
    fun `blank type returns generic label`() {
        assertEquals("SOAL", questionTypeLabel("   "))
    }
}
