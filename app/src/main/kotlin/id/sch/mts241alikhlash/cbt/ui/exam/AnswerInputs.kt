package id.sch.mts241alikhlash.cbt.ui.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import id.sch.mts241alikhlash.cbt.data.model.AnswerPayload
import id.sch.mts241alikhlash.cbt.data.model.PaperChoiceDto
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.ui.theme.BorderFocus
import id.sch.mts241alikhlash.cbt.ui.theme.BorderSubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandMuted
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavySubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary
import id.sch.mts241alikhlash.cbt.ui.theme.TextSecondary

data class ImageContext(val sessionId: String, val baseUrl: String, val imageLoader: ImageLoader?)

fun AnswerPayload?.isFilled(): Boolean = when (this) {
    null -> false
    is AnswerPayload.Multiple -> keys.isNotEmpty()
    is AnswerPayload.Text -> text.isNotBlank()
    else -> true
}

@Composable
fun QuestionAnswerInput(
    question: PaperQuestionDto,
    answer: AnswerPayload?,
    draft: String?,
    enabled: Boolean,
    images: ImageContext,
    onAnswer: (AnswerPayload) -> Unit,
    onToggleKey: (String) -> Unit,
    onTextChange: (String) -> Unit
) {
    val choices = question.choices.orEmpty()
    when (question.type.uppercase()) {
        "MULTIPLE_CHOICE" -> ChoiceInput(
            choices = choices,
            selected = (answer as? AnswerPayload.Choice)?.key,
            enabled = enabled,
            images = images,
            onSelect = { onAnswer(AnswerPayload.Choice(it)) }
        )
        "MULTIPLE_RESPONSE" -> MultipleResponseInput(
            choices = choices,
            selected = (answer as? AnswerPayload.Multiple)?.keys.orEmpty().toSet(),
            enabled = enabled,
            images = images,
            onToggle = onToggleKey
        )
        "TRUE_FALSE" -> TrueFalseInput(
            selected = (answer as? AnswerPayload.Bool)?.value,
            enabled = enabled,
            onSelect = { onAnswer(AnswerPayload.Bool(it)) }
        )
        "SHORT_ANSWER" -> TextAnswerInput(
            value = draft ?: (answer as? AnswerPayload.Text)?.text.orEmpty(),
            multiline = false,
            enabled = enabled,
            onChange = onTextChange
        )
        else -> TextAnswerInput(
            value = draft ?: (answer as? AnswerPayload.Text)?.text.orEmpty(),
            multiline = true,
            enabled = enabled,
            onChange = onTextChange
        )
    }
}

@Composable
fun ChoiceInput(
    choices: List<PaperChoiceDto>,
    selected: String?,
    enabled: Boolean,
    images: ImageContext,
    onSelect: (String) -> Unit
) {
    Column {
        choices.forEachIndexed { index, choice ->
            ChoiceRow(
                letter = ('A' + index).toString(),
                html = choice.html,
                selected = choice.key == selected,
                multi = false,
                enabled = enabled,
                images = images,
                onClick = { onSelect(choice.key) }
            )
        }
    }
}

@Composable
fun MultipleResponseInput(
    choices: List<PaperChoiceDto>,
    selected: Set<String>,
    enabled: Boolean,
    images: ImageContext,
    onToggle: (String) -> Unit
) {
    Column {
        Text(
            text = "Pilih semua jawaban yang benar",
            fontFamily = DMSansFontFamily,
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        choices.forEachIndexed { index, choice ->
            ChoiceRow(
                letter = ('A' + index).toString(),
                html = choice.html,
                selected = choice.key in selected,
                multi = true,
                enabled = enabled,
                images = images,
                onClick = { onToggle(choice.key) }
            )
        }
    }
}

@Composable
fun TrueFalseInput(selected: Boolean?, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Column {
        ChoiceRow("B", "Benar", selected == true, multi = false, enabled = enabled, images = null, onClick = { onSelect(true) })
        ChoiceRow("S", "Salah", selected == false, multi = false, enabled = enabled, images = null, onClick = { onSelect(false) })
    }
}

@Composable
fun TextAnswerInput(value: String, multiline: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = !multiline,
            minLines = if (multiline) 6 else 1,
            placeholder = { Text(if (multiline) "Tulis jawaban Anda di sini" else "Tulis jawaban singkat") },
            modifier = Modifier.fillMaxWidth()
        )
        if (value.length >= COUNTER_FROM) {
            Text(
                text = "${value.length} / $LIMIT",
                fontFamily = DMSansFontFamily,
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private const val COUNTER_FROM = 15_000
private const val LIMIT = 20_000

@Composable
private fun ChoiceRow(
    letter: String,
    html: String,
    selected: Boolean,
    multi: Boolean,
    enabled: Boolean,
    images: ImageContext?,
    onClick: () -> Unit
) {
    val badgeShape = if (multi) RoundedCornerShape(6.dp) else CircleShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) BorderFocus else BorderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (selected) BrandNavySubtle else BrandSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(badgeShape)
                .background(if (selected) BrandNavy else BrandMuted)
                .border(1.dp, if (selected) BrandNavy else BorderSubtle, badgeShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text = letter,
                fontFamily = DMSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (selected) Color.White else TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (images != null) {
            HtmlContent(
                html = html,
                sessionId = images.sessionId,
                baseUrl = images.baseUrl,
                imageLoader = images.imageLoader,
                fontSize = 14,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = html,
                fontFamily = DMSansFontFamily,
                fontSize = 14.sp,
                color = if (selected) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = BrandNavy,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
