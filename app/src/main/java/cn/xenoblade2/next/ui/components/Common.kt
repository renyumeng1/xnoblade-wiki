package cn.xenoblade2.next.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.ui.theme.Palette

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun Tag(text: String, color: Color = Palette.Core, filled: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(if (filled) Modifier.background(color.copy(alpha = 0.18f)) else Modifier)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = color, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Core, strokeWidth = 3.dp)
    }
}

@Composable
fun KeyValue(k: String, v: String) {
    if (v.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(k, color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Text(v, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options) { option ->
            val active = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (active) Palette.Core.copy(alpha = 0.2f) else Palette.SurfaceHigh)
                    .border(
                        1.dp,
                        if (active) Palette.Core else Palette.CardBorder,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    option,
                    color = if (active) Palette.Core else Palette.TextDim,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ActionRow(text: String, color: Color = Palette.Core, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 步骤说明按行去重。
 *
 * 老计划里常见两种重复：wiki 正文和定位备注各写了一遍同样的「地点：X」；
 * 备注已经写明「已按你的选择固定在 X」时后面又跟一行「地图：X」。
 * 显示层统一清一遍，避免用户看到两行一样的信息。
 */
fun cleanStepDetail(text: String): String {
    if (text.isBlank()) return text
    val lines = LinkedHashSet(
        text.lines()
            .flatMap { line ->
                // 「地点：A；角色：B」这种一行塞两条信息的，拆开后再去重，
                // 否则和后面单独一行的「地点：A」看起来完全重复。
                val parts = line.split('；').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1 && parts.first().contains('：')) parts else listOf(line.trim())
            }
            // 老版本生成计划时把候选打分一起写进了 detail，这些行对玩家没意义。
            .filterNot { it.matches(Regex("""^\d+\.\s.*""")) }
            .map {
                it.replace(Regex("""\s*/\s*ma\d[\w]*"""), "")
                    .replace("分数不够明确，先不自动钉点：", "暂未自动钉点")
                    .trimEnd('：')
            }
            .filter { it.isNotEmpty() },
    )
    val redundantMaps = lines
        .filter { it.startsWith("地图：") }
        .map { it.removePrefix("地图：") }
        .filter { label -> lines.any { other -> other != "地图：$label" && other.contains(label) } }
        .toSet()
    return lines
        .filterNot { it.startsWith("地图：") && it.removePrefix("地图：") in redundantMaps }
        .joinToString("\n")
}
