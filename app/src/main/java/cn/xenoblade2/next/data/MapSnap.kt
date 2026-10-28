package cn.xenoblade2.next.data

/**
 * 地图图片的「可走区域」掩码：按 alpha 通道判断某个归一化坐标是不是落在图形上。
 *
 * 为什么需要它：frontier 坐标是游戏 3D 场景压平成 2D 的，多层地图（世界树、檀佩兰提亚
 * 这类）上层/下层点位会被压到地板轮廓之外，直接画就落在透明区域（看起来像"标到地图外"）。
 * 这里在**显示时**把这类点吸附到最近的不透明像素，原始数据不动。
 */
class SnapMask(
    val width: Int,
    val height: Int,
    private val opaque: BooleanArray,
) {
    /** 格 → 吸附结果缓存（下标；-1 表示附近没有地板）。地图画一帧要查上百次，不缓存太慢。 */
    private val memo = HashMap<Int, Int>(512)

    fun isOpaque(x: Int, y: Int): Boolean =
        x in 0 until width && y in 0 until height && opaque[y * width + x]

    /** 从 (x,y) 找最近的不透明格；返回 `y * width + x`，找不到返回 -1。 */
    fun snapCell(x: Int, y: Int, maxR: Int): Int {
        val key = y * width + x
        memo[key]?.let { return it }
        var bestD = Int.MAX_VALUE
        var best = -1
        for (dy in -maxR..maxR) {
            val yy = y + dy
            if (yy < 0 || yy >= height) continue
            val dyy = dy * dy
            for (dx in -maxR..maxR) {
                val d = dyy + dx * dx
                if (d >= bestD) continue
                if (isOpaque(x + dx, yy)) {
                    bestD = d
                    best = yy * width + x + dx
                    if (d == 1) break
                }
            }
        }
        memo[key] = best
        return best
    }
}

object MapSnap {

    /**
     * 吸附半径上限（占图宽比例）。
     * 室内小图、多层压平图上点位可能整体偏出一截，0.10 不够，放宽到 0.16。
     * 超过这个距离的点由 [snapOrNull] 判定为「画不了」，直接不画，避免标到空白区。
     */
    private const val MAX_FRAC = 0.16f

    /** 返回吸附后的归一化坐标。已经在地图上、或附近没有可走区域时原样返回。 */
    fun snap(mask: SnapMask?, nx: Float, ny: Float): Pair<Float, Float> {
        if (mask == null) return nx to ny
        if (nx.isNaN() || ny.isNaN()) return nx to ny
        val x0 = (nx * mask.width).toInt().coerceIn(0, mask.width - 1)
        val y0 = (ny * mask.height).toInt().coerceIn(0, mask.height - 1)
        if (mask.isOpaque(x0, y0)) return nx to ny
        val maxR = (mask.width * MAX_FRAC).toInt().coerceIn(4, mask.width)
        val idx = mask.snapCell(x0, y0, maxR)
        if (idx < 0) return nx to ny
        return (idx % mask.width + 0.5f) / mask.width to (idx / mask.width + 0.5f) / mask.height
    }

    /** 点位是否被吸附过（用于在详情卡里提示"坐标是估算的"）。 */
    /**
     * 严格版吸附：吸附不到地板就返回 null。
     *
     * 点位图标、计划步骤这类一个点一个图标的东西用这个：
     * 宁可这张图上不显示，也不要在透明区域画一个悬空的标记。
     */
    fun snapOrNull(mask: SnapMask?, nx: Float, ny: Float): Pair<Float, Float>? {
        if (mask == null) return nx to ny
        if (nx.isNaN() || ny.isNaN()) return null
        if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
        val x0 = (nx * mask.width).toInt().coerceIn(0, mask.width - 1)
        val y0 = (ny * mask.height).toInt().coerceIn(0, mask.height - 1)
        if (mask.isOpaque(x0, y0)) return nx to ny
        val maxR = (mask.width * MAX_FRAC).toInt().coerceIn(4, mask.width)
        val idx = mask.snapCell(x0, y0, maxR)
        if (idx < 0) return null
        return (idx % mask.width + 0.5f) / mask.width to (idx / mask.width + 0.5f) / mask.height
    }

    /** 点位是否被吸附过（用于在详情卡里提示"坐标是估算的"）。 */
    fun moved(orig: Pair<Float, Float>, snapped: Pair<Float, Float>): Boolean {
        val dx = (orig.first - snapped.first) * 1000f
        val dy = (orig.second - snapped.second) * 1000f
        return dx * dx + dy * dy > 0.5f
    }
}
