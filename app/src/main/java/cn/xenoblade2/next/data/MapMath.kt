package cn.xenoblade2.next.data

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 地图坐标换算。公式来自对原 App 反编译结果（`WikiMapComponentsKt.normalizedPoint`，
 * 见 docs/DATA_NOTES.md）：
 *   nx = (frontierX - lowerX) / (upperX - lowerX)
 *   ny = (mercY(frontierY) - mercY(upperZ)) / (mercY(lowerZ) - mercY(upperZ))
 *
 * 关键点：`maps/frontiernav-*` 的图是 **Web Mercator 投影**画出来的，纵轴不能直接线性映射。
 * 之前这里当成线性的，整张图的点位会整体偏上/偏下：全库 6239 个点里只有 47% 落在图上
 * 的地板像素上，改成 mercY 之后是 97.9%（脚本实测，见 docs/DATA_NOTES.md）。
 */
object MapMath {

    /** 保留一点边界容差，避免贴边的点被整条丢掉。 */
    private const val TOL = 0.004

    private const val MAX_LAT = 85.05112878

    /** 纬度 → Web Mercator 归一化 y（0 = 北，1 = 南），和原 App 的 webMercatorY 一致。 */
    private fun mercY(lat: Double): Double {
        val l = lat.coerceIn(-MAX_LAT, MAX_LAT)
        return (1.0 - ln(tan(l * PI / 360.0 + PI / 4.0)) / PI) / 2.0
    }

    /** mercY 的反函数，用于把屏幕坐标还原成 frontier 纵向坐标。 */
    private fun invMercY(v: Double): Double = atan(sinh(PI * (1.0 - 2.0 * v))) * 180.0 / PI

    fun normalized(m: MapInfo, fx: Double, fy: Double): Pair<Float, Float>? {
        val dw = m.upperX - m.lowerX
        if (dw == 0.0) return null
        val nx = (fx - m.lowerX) / dw
        val ny = if (m.mercator) {
            val top = mercY(m.upperZ)
            val bottom = mercY(m.lowerZ)
            val span = bottom - top
            if (span == 0.0) return null
            (mercY(fy) - top) / span
        } else {
            val dh = m.lowerZ - m.upperZ
            if (dh == 0.0) return null
            (fy - m.upperZ) / dh
        }
        if (nx < -TOL || nx > 1 + TOL || ny < -TOL || ny > 1 + TOL) return null
        return nx.toFloat().coerceIn(0f, 1f) to ny.toFloat().coerceIn(0f, 1f)
    }

    /** normalized 的 y 逆运算：归一化 y → frontier 纵向坐标（长按钉点用）。 */
    fun frontierY(m: MapInfo, ny: Double): Double {
        val v = ny.coerceIn(0.0, 1.0)
        if (!m.mercator) return m.upperZ + v * (m.lowerZ - m.upperZ)
        val top = mercY(m.upperZ)
        val bottom = mercY(m.lowerZ)
        return invMercY(top + v * (bottom - top))
    }

    /** 缩放后的屏幕像素位置。 */
    fun screen(
        m: MapInfo,
        fx: Double,
        fy: Double,
        left: Float,
        top: Float,
        drawW: Float,
        drawH: Float,
    ): Pair<Float, Float>? {
        val (nx, ny) = normalized(m, fx, fy) ?: return null
        return (left + nx * drawW) to (top + ny * drawH)
    }
}

/**
 * 游戏世界坐标 → FrontierNav 平面坐标的仿射拟合：f = a * w + b。
 * 用同一张地图上所有带 world 坐标的 marker 做最小二乘；x 方向实测 RMSE≈0，
 * y 方向因为多层地形压平会有 0.1~2.6 的偏差，够画点位。
 */
data class Affine1D(val a: Double, val b: Double, val rmse: Double, val samples: Int) {
    fun map(v: Double): Double = a * v + b
    val usable: Boolean get() = samples >= 3 && rmse < 25.0
}

object AffineFit {

    fun fit(us: List<Double>, vs: List<Double>): Affine1D? {
        if (us.size < 2 || us.size != vs.size) return null
        val n = us.size
        val mu = us.average()
        val mv = vs.average()
        var cov = 0.0
        var varU = 0.0
        for (i in 0 until n) {
            cov += (us[i] - mu) * (vs[i] - mv)
            varU += (us[i] - mu) * (us[i] - mu)
        }
        if (varU < 1e-9) return null
        val a = cov / varU
        val b = mv - a * mu
        var se = 0.0
        for (i in 0 until n) {
            val d = a * us[i] + b - vs[i]
            se += d * d
        }
        return Affine1D(a, b, kotlin.math.sqrt(se / n), n)
    }
}
