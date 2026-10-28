package cn.xenoblade2.next.plan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 路线的一段：从一处停靠点到下一处停靠点。
 *
 * 停靠点 = 起点、途经的有名地标（[RouteWaypoint.notable]）、终点。
 * 「敌人刷新点 / 宝箱点」这类自动命名点只用来画线，不切段，否则一张图能切出几十段。
 */
data class RouteLeg(
    val index: Int,
    val fromName: String,
    val toName: String,
    /** 这一段的折线距离（frontier 单位，和「路线估算 248」同一套）。 */
    val distance: Double,
    /** 0=北（屏幕上方），90=东，顺时针。 */
    val bearing: Double,
    /** 出发 / 直行 / 左转 / 右转 / 掉头 —— 相对上一段的方向变化。 */
    val turn: String,
    /** 终点是不是有名地标（否则是终点本身）。 */
    val landmark: Boolean,
    /** 这一段自己的折线，用于地图上加亮。 */
    val path: List<Pair<Double, Double>>,
) {
    val compass: String get() = RouteGuide.compass(bearing)
    val heading: String get() = if (turn == "出发") compass else "$turn $compass"
}

/** 计划里相邻两个已定位步骤之间的一跳。 */
data class StepHop(
    val fromOrd: Int,
    val distance: Double,
    val bearing: Double,
    /** 第一处有名地标；没有就为 null。 */
    val via: String?,
    val legs: Int,
    /** 两个已定位步骤之间夹着的、没有坐标的步骤数。 */
    val skipped: Int = 0,
) {
    val compass: String get() = RouteGuide.compass(bearing)
}

/**
 * 把 `RouteGraph` 的折线翻译成「往哪走、走多远、到哪」。
 *
 * 全部是平面几何推算：库里没有真实路网，方向只代表点位图上的大致方位，
 * 不能当游戏内寻路用。距离也没有单位，只能和别的路线互相比。
 */
object RouteGuide {

    /**
     * 短于这个距离的抖动点并进上一段，避免「往前走 2 再左转」这种噪声。
     * 取 25：一张图 400~800 的路线切成 8~15 段，人工看着还行；再小会碎成几十段。
     */
    private const val MIN_LEG = 25.0

    private val COMPASS = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

    /** frontier 坐标系里 fy 越大越靠南（见 `MapMath.normalized`），所以北 = −dy。 */
    fun bearing(fromFx: Double, fromFy: Double, toFx: Double, toFy: Double): Double {
        val dx = toFx - fromFx
        val dy = -(toFy - fromFy)
        var deg = Math.toDegrees(atan2(dx, dy))
        if (deg < 0) deg += 360.0
        return deg
    }

    fun compass(deg: Double): String {
        val norm = ((deg % 360.0) + 360.0) % 360.0
        return COMPASS[(norm / 45.0).roundToInt() % 8]
    }

    fun turnLabel(prevBearing: Double?, current: Double): String {
        if (prevBearing == null) return "出发"
        var d = current - prevBearing
        while (d > 180.0) d -= 360.0
        while (d < -180.0) d += 360.0
        return when {
            abs(d) < 22.5 -> "直行"
            abs(d) < 135.0 -> if (d > 0) "右转" else "左转"
            else -> "掉头"
        }
    }

    fun legs(
        r: RouteResult,
        fromLabel: String? = null,
        toLabel: String? = null,
    ): List<RouteLeg> {
        val pts = r.points
        if (pts.size < 2) return emptyList()

        val keep = ArrayList<Int>(pts.size)
        keep.add(0)
        var last = 0
        for (i in 1 until pts.lastIndex) {
            if (!pts[i].notable) continue
            if (length(pts, last, i) < MIN_LEG) continue
            keep.add(i)
            last = i
        }
        if (keep.last() != pts.lastIndex) keep.add(pts.lastIndex)
        if (keep.size < 2) return emptyList()

        val out = ArrayList<RouteLeg>(keep.size - 1)
        var prevBearing: Double? = null
        for (k in 1 until keep.size) {
            val a = keep[k - 1]
            val b = keep[k]
            val distance = length(pts, a, b)
            if (distance <= 0.0001 && k < keep.size - 1) continue
            val bearing = bearing(pts[a].fx, pts[a].fy, pts[b].fx, pts[b].fy)
            val isEnd = b == pts.lastIndex
            val fromName = when {
                a == 0 && !fromLabel.isNullOrBlank() -> fromLabel
                else -> pts[a].name
            }
            val toName = when {
                isEnd && !toLabel.isNullOrBlank() -> toLabel
                else -> pts[b].name
            }
            out.add(
                RouteLeg(
                    index = out.size,
                    fromName = fromName.ifBlank { "起点" }.take(24),
                    toName = toName.ifBlank { if (isEnd) "终点" else "途经点" }.take(24),
                    distance = distance,
                    bearing = bearing,
                    turn = turnLabel(prevBearing, bearing),
                    landmark = pts[b].notable && !isEnd,
                    path = (a..b).map { pts[it].fx to pts[it].fy },
                ),
            )
            prevBearing = bearing
        }
        return out
    }

    /** 一句话概览，给计划页 / 地图卡片用。 */
    fun summary(r: RouteResult): String {
        val ls = legs(r)
        if (ls.isEmpty()) return "无路线"
        val first = ls.first()
        val tail = if (ls.size == 1) "" else " · 共 ${ls.size} 段"
        return "${first.heading} 约 ${"%.0f".format(first.distance)}$tail"
    }

    /**
     * 计划里每一跳（到达该步）的方位与距离，key = 目标步骤 id。
     *
     * 连接规则：按 ord 顺序取「已定位」的步骤，相邻两个在同一张图就连一条。
     * 中间没有坐标的步骤会被跳过（`skipped` 记数量）——它们本来就没位置，
     * 不能凭空插一个点；跨图的跳也不连，因为那是「切图」不是「走过去」。
     */
    fun planHops(steps: List<PlanStep>): Map<Long, StepHop> {
        val out = HashMap<Long, StepHop>()
        var last: PlanStep? = null
        for (s in steps.sortedBy { it.ord }) {
            if (!s.pinned) continue
            val p = last
            val mapId = s.mapId
            if (p != null && mapId != null && p.mapId == mapId) {
                val r = RouteGraph.route(
                    mapId,
                    p.fx ?: 0.0,
                    p.fy ?: 0.0,
                    s.fx ?: 0.0,
                    s.fy ?: 0.0,
                    p.featureId,
                    s.featureId,
                )
                val ls = legs(r)
                if (ls.isNotEmpty()) {
                    out[s.id] = StepHop(
                        fromOrd = p.ord,
                        distance = r.distance,
                        bearing = ls.first().bearing,
                        via = ls.firstOrNull { it.landmark }?.toName,
                        legs = ls.size,
                        skipped = (s.ord - p.ord - 1).coerceAtLeast(0),
                    )
                }
            }
            last = s
        }
        return out
    }

    private fun length(pts: List<RouteWaypoint>, from: Int, to: Int): Double {
        var d = 0.0
        for (i in from + 1..to) {
            d += hypot(pts[i].fx - pts[i - 1].fx, pts[i].fy - pts[i - 1].fy)
        }
        return d
    }
}
