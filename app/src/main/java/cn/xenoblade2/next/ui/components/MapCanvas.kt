package cn.xenoblade2.next.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.MapInfo
import cn.xenoblade2.next.data.MapMath
import cn.xenoblade2.next.data.SnapMask
import cn.xenoblade2.next.data.MapSnap
import cn.xenoblade2.next.data.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 计划/开环等叠加点位。fx/fy 用 frontier 平面坐标。 */
data class OverlayPin(
    val key: String,
    val fx: Double,
    val fy: Double,
    val label: String,
    val color: Color,
    val badge: String? = null,
    val dim: Boolean = false,
)

class MapViewState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomBy(factor: Float) {
        scale = (scale * factor).coerceIn(1f, 12f)
        if (scale == 1f) offset = Offset.Zero
    }
}

@Composable
fun rememberMapViewState(): MapViewState = remember { MapViewState() }

/** 原版交互地图用的类别位图（assets/maps 下的 -marker 位图，从 1.2.2 原包提取）。 */
object MapMarkerArt {
    private const val RARE = "maps/rare-enemy-marker.webp"
    private const val QUEST = "maps/quest-marker.webp"
    private const val LOCATION = "maps/location-marker.webp"
    private const val LANDMARK = "maps/landmark-marker.png"
    private const val SECRET = "maps/secret-area-marker.png"
    private const val HEART = "maps/heart-to-heart-marker.webp"
    private const val SHOP = "maps/shop-marker.webp"
    private const val COLLECT = "maps/collection-point-marker.webp"
    private const val TREASURE = "maps/treasure-chest-marker.png"
    private const val ROUTE = "maps/route-access-marker.webp"

    private val ALL = listOf(RARE, QUEST, LOCATION, LANDMARK, SECRET, HEART, SHOP, COLLECT, TREASURE, ROUTE)
    private val cache = LruCache<String, ImageBitmap>(ALL.size)
    private var loaded = false

    fun load(context: Context): Map<String, ImageBitmap> {
        if (loaded) return ALL.mapNotNull { p -> cache.get(p)?.let { p to it } }.toMap()
        val out = HashMap<String, ImageBitmap>()
        for (p in ALL) {
            runCatching {
                context.assets.open(p).use { s -> BitmapFactory.decodeStream(s)?.asImageBitmap() }
            }.getOrNull()?.let { bmp ->
                cache.put(p, bmp)
                out[p] = bmp
            }
        }
        loaded = true
        return out
    }

    /** 只有类型、没有完整 Marker 时用（筛选条 / 详情卡）。 */
    fun pathOfType(type: String): String? = when (type) {
        "TombstonePoint" -> RARE
        "Quest" -> QUEST
        "Location" -> LOCATION
        "Landmark" -> LANDMARK
        "Secret Area" -> SECRET
        "HeartToHeart" -> HEART
        "Shop" -> SHOP
        "CollectionPoint" -> COLLECT
        "TreasurePoint" -> TREASURE
        "RouteAccessPoint" -> ROUTE
        else -> null
    }

    /** marker → 位图路径；原版只给这 10 类画图，其余仍走绘制的 pin 兜底。 */
    fun pathOf(m: Marker): String? = when {
        m.targetType == "Location" && m.targetCategory == "Secret Area" -> SECRET
        m.targetType == "Location" && m.targetCategory == "Landmark" -> LANDMARK
        m.targetType == "Location" -> LOCATION
        m.targetType == "TombstonePoint" -> RARE
        m.targetType == "Quest" -> QUEST
        m.targetType == "HeartToHeart" -> HEART
        m.targetType == "Shop" -> SHOP
        m.targetType == "CollectionPoint" -> COLLECT
        m.targetType == "TreasurePoint" -> TREASURE
        m.targetType == "RouteAccessPoint" -> ROUTE
        else -> null
    }

    /** 原版尺寸：普通 29dp，秘境 31dp。 */
    fun heightDp(m: Marker): Float =
        if (m.targetType == "Location" && m.targetCategory == "Secret Area") 31f else 29f
}

object MapImages {
    /** 原图最大 4014x3893，全尺寸解码约 59 MB/张；手机上没必要，降到长边 2560 以内。 */
    private const val MAX_EDGE = 2560

    private val cache = object : LruCache<String, ImageBitmap>(2) {}
    private val maskCache = object : LruCache<String, SnapMask>(2) {}

    fun load(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.assets.open(path).use { ins ->
                val bmp = BitmapFactory.decodeStream(ins, null, opts) ?: return null
                maskCache.put(path, buildMask(bmp))
                bmp.asImageBitmap().also { cache.put(path, it) }
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** 和图片同源的「哪里有地板」掩码；失败返回 null（此时不吸附）。 */
    fun maskOf(context: Context, path: String): SnapMask? {
        maskCache.get(path)?.let { return it }
        load(context, path)
        return maskCache.get(path)
    }

    /**
     * 按 alpha 通道抽一张低分辨率掩码。图片本身的透明区域 = 不可走区域，
     * 点位吸附只看这个，不用再解析颜色。
     */
    private fun buildMask(src: android.graphics.Bitmap): SnapMask {
        val step = maxOf(1, maxOf(src.width, src.height) / 640)
        val w = (src.width / step).coerceAtLeast(1)
        val h = (src.height / step).coerceAtLeast(1)
        val small = if (step == 1 && src.width == w && src.height == h) src
        else android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val opaque = BooleanArray(w * h)
        for (i in px.indices) opaque[i] = (px[i] ushr 24) > 24
        return SnapMask(w, h, opaque)
    }
}

/* ---------- 类型配色 ---------- */

fun markerColor(type: String): Color = when (type) {
    "Location" -> Color(0xFF6FE3C4)
    "Quest" -> Color(0xFFF2C94C)
    "EnemySpawnPoint" -> Color(0xFFE8603C)
    "CollectionPoint" -> Color(0xFF8FD35A)
    "TreasurePoint" -> Color(0xFFC9A227)
    "SalvagePoint" -> Color(0xFF3C8FE8)
    "HeartToHeart" -> Color(0xFFE86FB4)
    "Shop", "Inn", "Exchange", "AuxCoreShop" -> Color(0xFFB78BE8)
    "DoorPoint", "WarpPoint", "ElevatorPoint", "JumpPoint", "ClimbingPoint" -> Color(0xFF7FA6D6)
    else -> Color(0xFF93A5B8)
}

fun markerTypeLabel(type: String): String = when (type) {
    "Location" -> "地标"
    "Quest" -> "任务"
    "EnemySpawnPoint" -> "敌人"
    "CollectionPoint" -> "采集"
    "TreasurePoint" -> "宝箱"
    "SalvagePoint" -> "打捞"
    "HeartToHeart" -> "对话"
    "Shop" -> "商店"
    "Inn" -> "旅店"
    "DoorPoint" -> "门"
    "WarpPoint" -> "传送"
    "ElevatorPoint" -> "电梯"
    "JumpPoint" -> "跳跃"
    "ClimbingPoint" -> "攀爬"
    "InteractionPoint" -> "交互"
    "KeyItemPoint" -> "关键道具"
    "TombstonePoint" -> "墓碑"
    else -> type
}

/**
 * 点位图标形状。地图上不同类别必须靠形状区分，颜色只是辅助
 * （色弱 / 灰底地图上单靠颜色几乎看不出来）。
 */
enum class MarkerGlyph {
    Landmark, Quest, Enemy, Collect, Treasure, Salvage, Heart, Shop, Inn, Gate, Climb, Jump, Npc, Dot,
    /** 以下三个只给筛选条 / 详情卡用，地图 pin 有专属位图。 */
    Secret, Route, Favorite,
}

fun markerGlyph(type: String): MarkerGlyph = when (type) {
    "Location" -> MarkerGlyph.Landmark
    "Quest" -> MarkerGlyph.Quest
    "EnemySpawnPoint" -> MarkerGlyph.Enemy
    "CollectionPoint" -> MarkerGlyph.Collect
    "TreasurePoint" -> MarkerGlyph.Treasure
    "SalvagePoint" -> MarkerGlyph.Salvage
    "HeartToHeart" -> MarkerGlyph.Heart
    "Shop", "Exchange", "AuxCoreShop" -> MarkerGlyph.Shop
    "Inn" -> MarkerGlyph.Inn
    "WarpPoint", "DoorPoint", "ElevatorPoint" -> MarkerGlyph.Gate
    "ClimbingPoint" -> MarkerGlyph.Climb
    "JumpPoint" -> MarkerGlyph.Jump
    "Npc" -> MarkerGlyph.Npc
    else -> MarkerGlyph.Dot
}

/**
 * 地图点位徽标。
 *
 * 形状参考 xenoblade2.cn 的地图（Leaflet 自定义 22×32 水滴 pin）：
 * 统一的水滴轮廓 + 白色描边，内部用「类别专属图形」区分，颜色只做辅助。
 * 这样在灰底地图上既醒目，也不会只靠颜色辨别。
 */
private fun DrawScope.drawMarkerPin(type: String, tip: Offset, r: Float) {
    val color = markerColor(type)
    val w = r
    val h = r * 2.9f
    val top = tip.y - h
    val cy = top + w
    val path = Path().apply {
        moveTo(tip.x - w, cy)
        arcTo(Rect(tip.x - w, top, tip.x + w, cy + w), 180f, 180f, false)
        lineTo(tip.x, tip.y)
        close()
    }
    // 外描边：先用半透明黑压出轮廓，再加一圈白边，深浅地图都撑得住
    drawPath(path, Color.Black.copy(alpha = 0.38f), style = Stroke(width = 3.4f))
    drawPath(path, color)
    drawPath(path, Color.White.copy(alpha = 0.96f), style = Stroke(width = 1.9f))
    drawTypeGlyph(markerGlyph(type), Offset(tip.x, cy), w * 0.74f, Color.White)
}

/** 画一个「深底 + 类别色描边 + 类别色图形」的点位徽标，灰底/黑底上都看得清。 */
private fun DrawScope.drawMarkerBadge(type: String, c: Offset, r: Float) {
    val color = markerColor(type)
    drawCircle(Color.Black.copy(alpha = 0.72f), radius = r + 1.3f, center = c)
    drawCircle(Color(0xFF0C121A), radius = r, center = c)
    drawCircle(color, radius = r, center = c, style = Stroke(width = (r * 0.16f).coerceAtLeast(1.2f)))
    drawTypeGlyph(markerGlyph(type), c, r, color)
}

private fun DrawScope.drawTypeGlyph(glyph: MarkerGlyph, c: Offset, r: Float, fg: Color) {
    val s = r * 0.58f
    when (glyph) {
        MarkerGlyph.Landmark -> drawPath(
            Path().apply {
                moveTo(c.x, c.y - s)
                lineTo(c.x + s, c.y)
                lineTo(c.x, c.y + s)
                lineTo(c.x - s, c.y)
                close()
            },
            fg,
        )
        MarkerGlyph.Quest -> {
            drawRect(fg, topLeft = Offset(c.x - s * 0.17f, c.y - s), size = Size(s * 0.34f, s * 1.3f))
            drawCircle(fg, radius = s * 0.19f, center = Offset(c.x, c.y + s * 0.68f))
        }
        MarkerGlyph.Enemy -> {
            val w = s * 0.82f
            drawLine(fg, Offset(c.x - w, c.y - w), Offset(c.x + w, c.y + w), strokeWidth = r * 0.3f, cap = StrokeCap.Round)
            drawLine(fg, Offset(c.x + w, c.y - w), Offset(c.x - w, c.y + w), strokeWidth = r * 0.3f, cap = StrokeCap.Round)
        }
        MarkerGlyph.Collect -> {
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y - s)
                    quadraticBezierTo(c.x + s * 1.05f, c.y - s * 0.1f, c.x, c.y + s)
                    quadraticBezierTo(c.x - s * 1.05f, c.y - s * 0.1f, c.x, c.y - s)
                    close()
                },
                fg,
            )
            drawLine(fg.copy(alpha = 0.45f), Offset(c.x, c.y - s * 0.7f), Offset(c.x, c.y + s * 0.85f), strokeWidth = r * 0.12f)
        }
        MarkerGlyph.Treasure -> {
            val w = s * 0.95f
            drawRect(
                fg,
                topLeft = Offset(c.x - w, c.y - s * 0.5f),
                size = Size(w * 2f, s * 1.4f),
                style = Stroke(r * 0.26f),
            )
            drawLine(fg, Offset(c.x - w, c.y - s * 0.05f), Offset(c.x + w, c.y - s * 0.05f), strokeWidth = r * 0.22f)
        }
        MarkerGlyph.Salvage -> {
            drawLine(fg, Offset(c.x, c.y - s), Offset(c.x, c.y + s * 0.5f), strokeWidth = r * 0.24f, cap = StrokeCap.Round)
            drawLine(fg, Offset(c.x - s * 0.72f, c.y - s * 0.36f), Offset(c.x + s * 0.72f, c.y - s * 0.36f), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
            drawArc(
                fg,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - s * 0.72f, c.y + s * 0.05f),
                size = Size(s * 1.44f, s * 1.15f),
                style = Stroke(r * 0.24f),
            )
        }
        MarkerGlyph.Heart -> drawPath(
            Path().apply {
                moveTo(c.x, c.y + s * 0.78f)
                cubicTo(c.x - s * 1.35f, c.y - s * 0.18f, c.x - s * 0.52f, c.y - s * 1.05f, c.x, c.y - s * 0.32f)
                cubicTo(c.x + s * 0.52f, c.y - s * 1.05f, c.x + s * 1.35f, c.y - s * 0.18f, c.x, c.y + s * 0.78f)
                close()
            },
            fg,
        )
        MarkerGlyph.Shop -> {
            drawRoundRect(
                fg,
                topLeft = Offset(c.x - s * 0.82f, c.y - s * 0.35f),
                size = Size(s * 1.64f, s * 1.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f),
                style = Stroke(r * 0.24f),
            )
            drawArc(
                fg,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(c.x - s * 0.4f, c.y - s * 0.85f),
                size = Size(s * 0.8f, s * 0.8f),
                style = Stroke(r * 0.22f),
            )
        }
        MarkerGlyph.Inn -> {
            drawPath(
                Path().apply {
                    moveTo(c.x, c.y - s)
                    lineTo(c.x + s, c.y)
                    lineTo(c.x + s * 0.62f, c.y)
                    lineTo(c.x + s * 0.62f, c.y + s * 0.85f)
                    lineTo(c.x - s * 0.62f, c.y + s * 0.85f)
                    lineTo(c.x - s * 0.62f, c.y)
                    lineTo(c.x - s, c.y)
                    close()
                },
                fg,
            )
        }
        MarkerGlyph.Gate -> {
            drawRoundRect(
                fg,
                topLeft = Offset(c.x - s * 0.62f, c.y - s * 0.85f),
                size = Size(s * 1.24f, s * 1.7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.6f, s * 0.6f),
                style = Stroke(r * 0.24f),
            )
            drawCircle(fg, radius = s * 0.16f, center = Offset(c.x + s * 0.28f, c.y + s * 0.2f))
        }
        MarkerGlyph.Climb -> {
            drawLine(fg, Offset(c.x - s * 0.45f, c.y - s), Offset(c.x - s * 0.45f, c.y + s), strokeWidth = r * 0.2f)
            drawLine(fg, Offset(c.x + s * 0.45f, c.y - s), Offset(c.x + s * 0.45f, c.y + s), strokeWidth = r * 0.2f)
            for (y in listOf(-0.55f, 0f, 0.55f)) {
                drawLine(fg, Offset(c.x - s * 0.45f, c.y + s * y), Offset(c.x + s * 0.45f, c.y + s * y), strokeWidth = r * 0.18f)
            }
        }
        MarkerGlyph.Jump -> {
            drawLine(fg, Offset(c.x - s * 0.8f, c.y + s * 0.25f), Offset(c.x, c.y - s * 0.7f), strokeWidth = r * 0.26f, cap = StrokeCap.Round)
            drawLine(fg, Offset(c.x, c.y - s * 0.7f), Offset(c.x + s * 0.8f, c.y + s * 0.25f), strokeWidth = r * 0.26f, cap = StrokeCap.Round)
        }
        MarkerGlyph.Npc -> {
            drawCircle(fg, radius = s * 0.38f, center = Offset(c.x, c.y - s * 0.42f))
            drawArc(
                fg,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = true,
                topLeft = Offset(c.x - s * 0.72f, c.y - s * 0.05f),
                size = Size(s * 1.44f, s * 1.15f),
            )
        }
        MarkerGlyph.Secret, MarkerGlyph.Route, MarkerGlyph.Favorite,
        MarkerGlyph.Dot,
        -> drawCircle(fg, radius = r * 0.32f, center = c)
    }
}

/** 供筛选条 / 列表复用同一套类别图形。 */
internal fun DrawScope.drawGlyphByEnum(glyph: MarkerGlyph, c: Offset, r: Float, fg: Color) =
    drawTypeGlyph(glyph, c, r, fg)

/** 图层筛选条 / 详情卡里用的迷你图标。 */
@Composable
fun MarkerGlyphBadge(
    type: String,
    box: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val art = rememberAssetBitmap(WikiIcons.mapPath(type) ?: MapMarkerArt.pathOfType(type))
    if (art != null) {
        Image(
            bitmap = art,
            contentDescription = null,
            modifier = modifier.size(box),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Canvas(modifier.size(box)) {
        val r = size.minDimension * 0.30f
        drawMarkerPin(type, Offset(size.width / 2f, size.height - r * 0.16f), r)
    }
}

private class Viewport(
    val vw: Float,
    val vh: Float,
    val baseW: Float,
    val baseH: Float,
    val scale: Float,
    val offset: Offset,
) {
    val left = (vw - baseW) / 2f
    val top = (vh - baseH) / 2f

    fun screen(nx: Float, ny: Float): Offset {
        val px = left + nx * baseW
        val py = top + ny * baseH
        return Offset(
            vw / 2f + (px - vw / 2f) * scale + offset.x,
            vh / 2f + (py - vh / 2f) * scale + offset.y,
        )
    }

    /** screen() 的逆运算：屏幕点 → 归一化坐标；点在图外返回 null。 */
    fun normalizedAt(p: Offset): Pair<Float, Float>? {
        if (scale <= 0f || baseW <= 0f || baseH <= 0f) return null
        val px = vw / 2f + (p.x - offset.x - vw / 2f) / scale
        val py = vh / 2f + (p.y - offset.y - vh / 2f) / scale
        val nx = (px - left) / baseW
        val ny = (py - top) / baseH
        if (nx.isNaN() || ny.isNaN() || nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
        return nx to ny
    }
}

private fun viewportOf(map: MapInfo, vw: Float, vh: Float, scale: Float, offset: Offset): Viewport {
    if (vw <= 0f || vh <= 0f) return Viewport(1f, 1f, map.aspect, 1f, scale, offset)
    val mapAspect = (map.width / map.height).toFloat()
    val viewAspect = vw / vh
    val baseW: Float
    val baseH: Float
    if (mapAspect > viewAspect) {
        baseW = vw
        baseH = vw / mapAspect
    } else {
        baseH = vh
        baseW = vh * mapAspect
    }
    return Viewport(vw, vh, baseW, baseH, scale, offset)
}

@Composable
fun MapCanvas(
    map: MapInfo,
    viewState: MapViewState,
    markers: List<Marker>,
    /** 已开启的筛选 key，见 [markerFilterKey]（Location 会拆成地点/地标/秘境）。 */
    visibleTypes: Set<String>,
    overlay: List<OverlayPin>,
    selectedKey: String?,
    onTapMarker: (Marker) -> Unit,
    onTapOverlay: (OverlayPin) -> Unit,
    onTapEmpty: () -> Unit,
    modifier: Modifier = Modifier,
    /** 长按地图空白处：回调 frontier 平面坐标，可用于手动钉点。 */
    onLongPressAt: ((fx: Double, fy: Double) -> Unit)? = null,
    /** 估算路线（frontier 坐标折线）。来自 RouteGraph，绘制在计划直线之上。 */
    routePath: List<Pair<Double, Double>> = emptyList(),
    /** 当前正在走的那一段（逐段导航），叠在 routePath 上高亮。 */
    highlightPath: List<Pair<Double, Double>> = emptyList(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val image by produceState<ImageBitmap?>(null, map.id) {
        value = withContext(Dispatchers.IO) { MapImages.load(context, map.imagePath) }
    }
    // 可走区域掩码：用来把落到图外的点位吸附到最近的地板上（显示层修正，数据不动）
    val mask by produceState<SnapMask?>(null, map.id) {
        value = withContext(Dispatchers.IO) { MapImages.maskOf(context, map.imagePath) }
    }
    // 游戏内的地图标记图标（wiki 资源）。有图标的类型直接画图，其余继续自绘水滴。
    val enemyIcon = rememberAssetBitmap(WikiIcons.mapPath("EnemySpawnPoint"))
    val collectIcon = rememberAssetBitmap(WikiIcons.mapPath("CollectionPoint"))
    val treasureIcon = rememberAssetBitmap(WikiIcons.mapPath("TreasurePoint"))
    val salvageIcon = rememberAssetBitmap(WikiIcons.mapPath("SalvagePoint"))
    val markerArt by produceState<Map<String, ImageBitmap>>(emptyMap(), map.id) {
        value = withContext(Dispatchers.IO) { MapMarkerArt.load(context) }
    }
    val wikiMarkerIcon: (String) -> ImageBitmap? = { type ->
        when (type) {
            "EnemySpawnPoint", "TombstonePoint" -> enemyIcon
            "CollectionPoint" -> collectIcon
            "TreasurePoint" -> treasureIcon
            "SalvagePoint" -> salvageIcon
            else -> null
        }
    }
    // 原版 marker 位图（29dp），一次性解码后全程复用
    /** frontier 坐标 → 屏幕坐标，途中做一次「吸附到最近可走区域」。 */
    val place: (Viewport, Double, Double) -> Offset? = { vp, fx, fy ->
        MapMath.normalized(map, fx, fy)?.let { n ->
            val sn = MapSnap.snap(mask, n.first, n.second)
            vp.screen(sn.first, sn.second)
        }
    }
    /**
     * 严格版：吸附不到地板就 **不画**。
     * 点位图标和计划步骤用这个——数据坐标再怎么偏，也不会出现悬在透明区的标记。
     */
    val placeStrict: (Viewport, Double, Double) -> Offset? = { vp, fx, fy ->
        MapMath.normalized(map, fx, fy)?.let { n ->
            MapSnap.snapOrNull(mask, n.first, n.second)?.let { sn -> vp.screen(sn.first, sn.second) }
        }
    }
    val sizeState = remember { mutableStateOf(IntSize.Zero) }
    // 缩放/平移状态由外部持有，按钮与手势共享同一份
    val scale = viewState.scale
    val offset = viewState.offset

    Box(modifier.background(Color(0xFF070A0F)), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sizeState.value = it }
                .pointerInput(map.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (viewState.scale * zoom).coerceIn(1f, 12f)
                        val ratio = newScale / viewState.scale
                        val vc = Offset(sizeState.value.width / 2f, sizeState.value.height / 2f)
                        val newOffset = centroid - vc - (centroid - vc - viewState.offset) * ratio + pan
                        viewState.scale = newScale
                        viewState.offset = if (newScale <= 1.001f) Offset.Zero else newOffset
                    }
                }
                .pointerInput(map.id, markers, overlay, onLongPressAt) {
                    detectTapGestures(
                        onLongPress = longPress@{ tap ->
                            val handler = onLongPressAt ?: return@longPress
                            val v = viewportOf(
                                map,
                                sizeState.value.width.toFloat(),
                                sizeState.value.height.toFloat(),
                                scale,
                                offset,
                            )
                            val n = v.normalizedAt(tap) ?: return@longPress
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            handler(
                                map.lowerX + n.first * (map.upperX - map.lowerX),
                                MapMath.frontierY(map, n.second.toDouble()),
                            )
                        },
                        onTap = tap@{ tap ->
                        val v = viewportOf(
                            map,
                            sizeState.value.width.toFloat(),
                            sizeState.value.height.toFloat(),
                            scale,
                            offset,
                        )
                        val threshold = with(density) { 22.dp.toPx() }
                        var best: Offset? = null
                        var bestDist = threshold
                        var hitMarker: Marker? = null
                        var hitOverlay: OverlayPin? = null

                        for (pin in overlay) {
                            val p = placeStrict(v, pin.fx, pin.fy) ?: continue
                            val d = (p - tap).getDistance()
                            if (d < bestDist) {
                                bestDist = d; best = p; hitOverlay = pin; hitMarker = null
                            }
                        }
                        for (m in markers) {
                            if (!visibleTypes.contains(markerFilterKey(m))) continue
                            val p = placeStrict(v, m.frontierX, m.frontierY) ?: continue
                            val d = (p - tap).getDistance()
                            if (d < bestDist) {
                                bestDist = d; best = p; hitMarker = m; hitOverlay = null
                            }
                        }
                        when {
                            hitOverlay != null -> onTapOverlay(hitOverlay)
                            hitMarker != null -> onTapMarker(hitMarker)
                            else -> onTapEmpty()
                        }
                        },
                    )
                },
        ) {
            val img = image
            val v = viewportOf(map, size.width, size.height, scale, offset)
            if (img != null) {
                withTransform({
                    translate(offset.x, offset.y)
                    scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
                }) {
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(v.left.roundToInt(), v.top.roundToInt()),
                        dstSize = IntSize(v.baseW.roundToInt(), v.baseH.roundToInt()),
                    )
                }
            }

            // 估算路线：同图点位图上的 Dijkstra 折线（无真实路网，只作方向参考）
            if (routePath.size >= 2) {
                val pts = routePath.mapNotNull { (fx, fy) -> place(v, fx, fy) }
                for (i in 1 until pts.size) {
                    drawLine(
                        color = Color(0xFF3C8FE8).copy(alpha = 0.30f),
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 9f,
                        cap = StrokeCap.Round,
                    )
                }
                for (i in 1 until pts.size) {
                    drawLine(
                        color = Color(0xFF7FC4FF).copy(alpha = 0.95f),
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 3f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            // 逐段导航：当前这一段加粗高亮，终点画个圈
            if (highlightPath.size >= 2) {
                val pts = highlightPath.mapNotNull { (fx, fy) -> place(v, fx, fy) }
                for (i in 1 until pts.size) {
                    drawLine(
                        color = Color(0xFFFFC24B).copy(alpha = 0.35f),
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 11f,
                        cap = StrokeCap.Round,
                    )
                }
                for (i in 1 until pts.size) {
                    drawLine(
                        color = Color(0xFFFFD37A),
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }
                pts.lastOrNull()?.let { end ->
                    drawCircle(Color(0xFFFFD37A).copy(alpha = 0.25f), radius = 14f, center = end)
                    drawCircle(Color(0xFFFFD37A), radius = 6f, center = end)
                }
            }

            // 计划路线
            if (overlay.size >= 2) {
                var prev: Offset? = null
                for (pin in overlay) {
                    val p = place(v, pin.fx, pin.fy) ?: continue
                    prev?.let {
                        drawLine(
                            color = Color(0xFF6FE3C4).copy(alpha = 0.55f),
                            start = it,
                            end = p,
                            strokeWidth = 2.5f,
                        )
                    }
                    prev = p
                }
            }

            // 地图自带点位：优先 wiki 图标；wiki 没收录的类型用同款游戏内标记。
            // 只有拿不到任何位图的冷门类型才回退到自绘水滴。
            val markerScale = (1f + (scale - 1f) * 0.10f).coerceIn(1f, 1.4f)
            val baseRadius = 7.5.dp.toPx() * markerScale
            for (m in markers) {
                if (!visibleTypes.contains(markerFilterKey(m))) continue
                val p = placeStrict(v, m.frontierX, m.frontierY) ?: continue
                if (p.x < -40 || p.y < -70 || p.x > size.width + 40 || p.y > size.height + 40) continue
                val wikiIcon = wikiMarkerIcon(m.targetType)
                val artPath = if (wikiIcon == null) MapMarkerArt.pathOf(m) else null
                val icon = wikiIcon ?: artPath?.let { markerArt[it] }
                val iconH: Float
                if (icon != null) {
                    // 所有来源的图标统一按高度缩放，避免 32×32 的旋涡比 22×32 的铲子大一圈。
                    iconH = 27.dp.toPx() * markerScale
                    val iw = iconH * icon.width / icon.height
                    drawImage(
                        image = icon,
                        dstOffset = IntOffset(
                            (p.x - iw / 2f).roundToInt(),
                            (p.y - iconH).roundToInt(),
                        ),
                        dstSize = IntSize(iw.roundToInt(), iconH.roundToInt()),
                    )
                } else {
                    drawMarkerPin(m.targetType, p, baseRadius)
                    iconH = baseRadius * 2.9f
                }
                if (m.hasAppearanceCondition) {
                    val dot = Offset(p.x + baseRadius * 0.9f, p.y - iconH + 2.dp.toPx())
                    drawCircle(Color(0xFFE53935), radius = 3.dp.toPx(), center = dot)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = dot, style = Stroke(width = 1.dp.toPx()))
                }
            }

            // 地标文字：放大到 1.6x 后才显示，避免糊成一片
            if (scale >= 1.6f) {
                for (m in markers) {
                    if (m.targetType != "Location" && m.targetType != "Quest") continue
                    if (!visibleTypes.contains(markerFilterKey(m))) continue
                    val p = placeStrict(v, m.frontierX, m.frontierY) ?: continue
                    if (p.x < 0 || p.y < 0 || p.x > size.width || p.y > size.height) continue
                    drawLabel(measurer, m.targetName, p + Offset(8f, -6f), markerColor(m.targetType))
                }
            }

            // 叠加点位（计划步骤 / 搜索结果）
            for (pin in overlay) {
                val p = placeStrict(v, pin.fx, pin.fy) ?: continue
                if (p.x < -40 || p.y < -40 || p.x > size.width + 40 || p.y > size.height + 40) continue
                val selected = pin.key == selectedKey
                val r = (if (selected) 11 else 9).dp.toPx()
                drawCircle(color = Color.White.copy(alpha = 0.9f), radius = r + 2.6f, center = p)
                drawCircle(color = Color.Black.copy(alpha = 0.85f), radius = r + 1.1f, center = p)
                drawCircle(color = if (pin.dim) pin.color.copy(alpha = 0.4f) else pin.color, radius = r, center = p)
                if (selected) {
                    drawCircle(color = Color.White, radius = r + 4.5f, center = p, style = Stroke(width = 2.4f))
                }
                pin.badge?.let { badge ->
                    val layout = measurer.measure(
                        badge,
                        TextStyle(color = Color(0xFF0B0F17), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                    drawText(
                        layout,
                        topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f),
                    )
                }
            }
        }

        if (image == null) {
            CircularProgressIndicator(color = Color(0xFF6FE3C4))
        }
    }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, at: Offset, color: Color) {
    if (text.isBlank()) return
    val layout = measurer.measure(
        text,
        TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    drawRect(
        color = Color.Black.copy(alpha = 0.55f),
        topLeft = at - Offset(2f, 1f),
        size = androidx.compose.ui.geometry.Size(layout.size.width + 4f, layout.size.height + 2f),
    )
    drawText(layout, topLeft = at)
}
