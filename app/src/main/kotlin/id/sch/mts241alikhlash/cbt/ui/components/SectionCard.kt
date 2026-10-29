package id.sch.mts241alikhlash.cbt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.WebTokens

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WebTokens.Card, shape)
            .border(1.dp, WebTokens.Border, shape)
            .clip(shape)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DMSansFontFamily,
            letterSpacing = 1.2.sp,
            color = WebTokens.Foreground.copy(alpha = 0.78f),
            modifier = Modifier
                .fillMaxWidth()
                .background(WebTokens.Foreground.copy(alpha = 0.03f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(WebTokens.Border))
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionedCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WebTokens.Card, shape)
            .border(1.dp, WebTokens.Border, shape)
            .clip(shape),
        content = content
    )
}

@Composable
fun SectionHeader(title: String, divider: Boolean = false) {
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(WebTokens.Border))
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = DMSansFontFamily,
        letterSpacing = 1.2.sp,
        color = WebTokens.Foreground.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .background(WebTokens.Foreground.copy(alpha = 0.03f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(WebTokens.Border))
}
