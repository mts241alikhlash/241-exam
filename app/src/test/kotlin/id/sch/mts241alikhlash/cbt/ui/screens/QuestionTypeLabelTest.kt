package id.sch.mts241alikhlash.cbt.ui.screens

import java.util.Locale
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

    /**
     * Turkish casing is the classic locale trap: `"I".lowercase()` is "ı" and
     * `"i".uppercase()` is "İ", so a locale-sensitive mapping silently misses every
     * `*_choice` key and mangles the fallback. The mapping is ASCII identifiers, so
     * it must behave identically under every default locale.
     */
    @Test
    fun `mapping is independent of the default locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("PILIHAN GANDA", questionTypeLabel("multiple_choice"))
            assertEquals("BENAR / SALAH", questionTypeLabel("true_false"))
            assertEquals("JAWABAN SINGKAT", questionTypeLabel("short_answer"))
            assertEquals("MATCHING PAIRS", questionTypeLabel("matching_pairs"))
            assertEquals("PILIHAN GANDA", questionTypeLabel("MULTIPLE_CHOICE"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
