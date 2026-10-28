package cn.xenoblade2.next.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.ui.theme.Palette

/**
 * 交互地图的筛选分类。顺序、标题、配色对齐原版 `MarkerFilterBar`（1.2.2）。
 *
 * key 不是单纯的 `target_type`：Location 会按 `target_category` 拆成
 * 地点 / 地标 / 秘境三类，所以统一走 [markerFilterKey]。
 */
data class MarkerFilter(
    val key: String,
    val label: String,
    val color: Color,
    val defaultOn: Boolean,
)

val MARKER_FILTERS: List<MarkerFilter> = listOf(
    MarkerFilter("TombstonePoint", "稀有怪物", Color(0xFFD40011), true),
    MarkerFilter("Quest", "任务", Color(0xFF7653B5), true),
    MarkerFilter("Location", "地点", Color(0xFF56EBFF), true),
    MarkerFilter("Landmark", "地标", Color(0xFF4385D4), true),
    MarkerFilter("Secret Area", "秘境", Color(0xFFFFC107), true),
    MarkerFilter("HeartToHeart", "牵绊对话", Color(0xFF008F83), true),
    MarkerFilter("Shop", "商店", Color(0xFFD97720), true),
    MarkerFilter("CollectionPoint", "收集点", Color(0xFF2F9348), false),
    MarkerFilter("TreasurePoint", "宝箱", Color(0xFFC9A227), false),
    MarkerFilter("RouteAccessPoint", "路径点", Color(0xFF008F83), true),
)

val MARKER_FILTER_DEFAULTS: Set<String> = MARKER_FILTERS.filter { it.defaultOn }.map { it.key }.toSet()

const val FAVORITE_FILTER_KEY = "Marked"

/** 原版 `markerEnabled` 的分类逻辑。 */
fun markerFilterKey(m: Marker): String = when {
    m.targetType == "Location" && m.targetCategory == "Secret Area" -> "Secret Area"
    m.targetType == "Location" && m.targetCategory == "Landmark" -> "Landmark"
    else -> m.targetType
}

fun markerFilterLabel(key: String): String = when (key) {
    FAVORITE_FILTER_KEY -> "已收藏"
    else -> MARKER_FILTERS.firstOrNull { it.key == key }?.label ?: markerTypeLabel(key)
}

fun markerFilterColor(key: String): Color = when (key) {
    FAVORITE_FILTER_KEY -> Color(0xFF2EAD5B)
    else -> MARKER_FILTERS.firstOrNull { it.key == key }?.color ?: markerColor(key)
}

/**
 * 筛选图标：只在 chip 里画「类别图形」，不再套水滴轮廓。
 * 未选中的 chip 用类别色画图形；选中的 chip 用白色，和原版一致。
 */
@Composable
fun MarkerFilterGlyph(key: String, tint: Color, box: Dp = 14.dp) {
    val type = if (key == "TombstonePoint") "EnemySpawnPoint" else key
    val art = rememberAssetBitmap(WikiIcons.mapPath(type) ?: MapMarkerArt.pathOfType(key))
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            modifier = Modifier.size(box),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Canvas(Modifier.size(box)) {
        drawGlyphShape(glyphOfFilter(key), Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.46f, tint)
    }
}

private fun glyphOfFilter(key: String): MarkerGlyph = when (key) {
    "TombstonePoint" -> MarkerGlyph.Enemy
    "Quest" -> MarkerGlyph.Quest
    "Location" -> MarkerGlyph.Landmark
    "Landmark" -> MarkerGlyph.Landmark
    "Secret Area" -> MarkerGlyph.Secret
    "HeartToHeart" -> MarkerGlyph.Heart
    "Shop" -> MarkerGlyph.Shop
    "CollectionPoint" -> MarkerGlyph.Collect
    "TreasurePoint" -> MarkerGlyph.Treasure
    "RouteAccessPoint" -> MarkerGlyph.Route
    FAVORITE_FILTER_KEY -> MarkerGlyph.Favorite
    else -> MarkerGlyph.Dot
}

/**
 * 地图顶部筛选条。默认按原版：稀有怪 / 任务 / 地点 / 地标 / 秘境 / 牵绊 / 商店 / 路径点 + 已收藏，
 * 收集点和宝箱默认关闭（太密）。
 */
@Composable
fun MarkerFilterBar(
    markers: List<Marker>,
    active: Set<String>,
    showsMarked: Boolean,
    onToggle: (String) -> Unit,
    onToggleMarked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        // 原版 `MarkerFilterBar`：LazyRow 不带 contentPadding，chip 间隔 8dp
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = FAVORITE_FILTER_KEY) {
            val color = markerFilterColor(FAVORITE_FILTER_KEY)
            FilterChip(
                label = "已收藏",
                color = color,
                on = showsMarked,
                onClick = onToggleMarked,
            )
        }
        items(MARKER_FILTERS, key = { it.key }) { f ->
            FilterChip(
                label = f.label,
                color = f.color,
                on = f.key in active,
                onClick = { onToggle(f.key) },
            )
        }
    }
}

/** 原版 `FilterChipButton`（InteractiveMapScreensKt.java:2286）：22dp 圆角、12/8dp 内边距、15dp 图标、12sp SemiBold。 */
@Composable
private fun FilterChip(
    label: String,
    color: Color,
    on: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (on) color else Palette.Surface)
            .border(1.dp, if (on) Color.Transparent else Color(0x2E000000), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (on) Color.White else color
        MarkerFilterGlyph(label.filterGlyphKey(), fg, 15.dp)
        Text(
            label,
            color = if (on) Color.White else Palette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** chip 文案 → 图形 key（只有「已收藏」需要特殊处理，其余用 label 反查）。 */
private fun String.filterGlyphKey(): String =
    if (this == "已收藏") FAVORITE_FILTER_KEY
    else MARKER_FILTERS.firstOrNull { it.label == this }?.key ?: this

/** Secret / Route / Favorite 三个图形只用于筛选条和详情卡，不参与地图 pin 回退。 */
internal fun DrawScope.drawGlyphShape(glyph: MarkerGlyph, c: Offset, r: Float, fg: Color) {
    if (glyph == MarkerGlyph.Secret) {
        // 四角星
        val path = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticBezierTo(c.x + r * 0.18f, c.y - r * 0.18f, c.x + r, c.y)
            quadraticBezierTo(c.x + r * 0.18f, c.y + r * 0.18f, c.x, c.y + r)
            quadraticBezierTo(c.x - r * 0.18f, c.y + r * 0.18f, c.x - r, c.y)
            quadraticBezierTo(c.x - r * 0.18f, c.y - r * 0.18f, c.x, c.y - r)
            close()
        }
        drawPath(path, fg)
        return
    }
    if (glyph == MarkerGlyph.Route) {
        // 折线箭头
        val p = Path().apply {
            moveTo(c.x - r * 0.85f, c.y + r * 0.55f)
            lineTo(c.x - r * 0.15f, c.y - r * 0.2f)
            lineTo(c.x + r * 0.3f, c.y + r * 0.25f)
            lineTo(c.x + r * 0.8f, c.y - r * 0.6f)
        }
        drawPath(p, fg, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.34f))
        drawCircle(fg, radius = r * 0.26f, center = Offset(c.x + r * 0.8f, c.y - r * 0.6f))
        return
    }
    if (glyph == MarkerGlyph.Favorite) {
        val path = Path()
        val outer = r
        val inner = r * 0.46f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = (-90f + i * 36f) * (Math.PI / 180f).toFloat()
            val x = c.x + radius * kotlin.math.cos(angle)
            val y = c.y + radius * kotlin.math.sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, fg)
        return
    }
    drawGlyphByEnum(glyph, c, r, fg)
}
