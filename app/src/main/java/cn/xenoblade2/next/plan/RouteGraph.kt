package cn.xenoblade2.next.plan

import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.data.str
import java.util.PriorityQueue
import kotlin.math.hypot

/** 路线上的一个途经点。frontier 坐标系，和地图 marker 同源。 */
data class RouteWaypoint(
    val featureId: String,
    val name: String,
    val fx: Double,
    val fy: Double,
    val targetType: String,
    /** 是否有可读名字。敌人刷新点 / 宝箱点这类自动命名的点只参与画线，不当作"途经地标"。 */
    val notable: Boolean = true,
)

/**
 * 一条估算路线。
 *
 * 注意：库里没有真实路网（`route_connection` 为 0 行），这里是用「同图点位 + 最近的 K 个邻居」
 * 构造的几何近似图，再跑 Dijkstra。它不会穿墙判断、不知道单行道，只能当"大致方向 + 途经地标"用。
 */
data class RouteResult(
    val mapId: String,
    val points: List<RouteWaypoint>,
    val distance: Double,
    val straight: Double,
    /** 经过的真实跳跃点（电梯/传送/跳跃）数量，纯几何近似时为 0。 */
    val accessHops: Int,
    /** 图里两个点不连通、只能按直线回退时为 true。 */
    val fallback: Boolean,
) {
    val detour: Double get() = if (straight <= 0.0001) 1.0 else distance / straight
}

/**
 * 同图路线估算。
 *
 * 构图规则（全部来自本机 DB，没有外部请求）：
 * 1. 节点 = 该图 `frontiernav_map_marker`，坐标重复的合并，Location/Quest 优先。
 * 2. 边 = 每个节点到最近的 K 个节点，权重大致等于欧氏距离 × 目标点类型系数
 *    （敌人刷新点系数更高，让路线尽量走地标）。
 * 3. `route_access_point` 里同图的电梯 / 传送 / 跳跃点两两相连，用固定代价近似"上下层"。
 * 4. 每个连通分量再桥接一条最短边，保证同图任意两点可达。
 *
 * 所有阈值都是启发式的，验证方式是 `tools/route_stats.py` 的绕行比统计。
 */
object RouteGraph {

    private const val K = 6
    private const val DEDUPE_CELL = 0.75
    private const val WARP_COST = 3.0
    private const val ELEVATOR_COST = 4.0
    private const val JUMP_COST = 2.0

    private class Edge(val to: Int, val cost: Double)

    private class Graph(
        val markers: List<Marker>,
        val adj: List<MutableList<Edge>>,
        val indexByFeature: Map<String, Int>,
        val accessNodes: Set<Int>,
    )

    private val cache = HashMap<String, Graph>()

    /** 清空缓存（换库/测试时用）。 */
    fun clear() = cache.clear()

    /** 单点对路线；两点必须同图。 */
    fun route(
        mapId: String,
        fromFx: Double,
        fromFy: Double,
        toFx: Double,
        toFy: Double,
        fromFeatureId: String? = null,
        toFeatureId: String? = null,
    ): RouteResult {
        val g = graph(mapId)
        val straight = hypot(toFx - fromFx, toFy - fromFy)
        val start = nearest(g, fromFx, fromFy, fromFeatureId)
        val goal = nearest(g, toFx, toFy, toFeatureId)
        if (start < 0 || goal < 0) {
            return RouteResult(
                mapId,
                listOf(
                    RouteWaypoint(fromFeatureId ?: "", "起点", fromFx, fromFy, "", notable = false),
                    RouteWaypoint(toFeatureId ?: "", "终点", toFx, toFy, "", notable = false),
                ),
                straight,
                straight,
                0,
                true,
            )
        }
        val path = dijkstra(g, start, goal)
        if (path == null) {
            return RouteResult(
                mapId,
                listOf(
                    RouteWaypoint(fromFeatureId ?: "", "起点", fromFx, fromFy, "", notable = false),
                    RouteWaypoint(toFeatureId ?: "", "终点", toFx, toFy, "", notable = false),
                ),
                straight,
                straight,
                0,
                true,
            )
        }

        val pts = ArrayList<RouteWaypoint>(path.size + 2)
        // 合成端点不是真实地标：标成不 notable，逐段导航才不会在相邻步骤之间切出假的一段
        pts.add(RouteWaypoint(fromFeatureId ?: "", "起点", fromFx, fromFy, "", notable = false))
        var accessHops = 0
        path.forEachIndexed { i, idx ->
            val m = g.markers[idx]
            // 起点/终点吸附到的节点不重复列出来
            val firstSnap = i == 0 && hypot(m.frontierX - fromFx, m.frontierY - fromFy) < 0.75
            val lastSnap = i == path.lastIndex && hypot(m.frontierX - toFx, m.frontierY - toFy) < 0.75
            if (!firstSnap && !lastSnap) {
                pts.add(
                    RouteWaypoint(
                        m.featureId,
                        m.targetName,
                        m.frontierX,
                        m.frontierY,
                        m.targetType,
                        notable = isNotable(m),
                    ),
                )
            }
            if (idx in g.accessNodes && i > 0) accessHops++
        }
        pts.add(RouteWaypoint(toFeatureId ?: "", "终点", toFx, toFy, "", notable = false))

        var dist = 0.0
        for (i in 1 until pts.size) {
            dist += hypot(pts[i].fx - pts[i - 1].fx, pts[i].fy - pts[i - 1].fy)
        }
        return RouteResult(mapId, pts, dist, straight, accessHops, false)
    }

    /** 多点串联（按传入顺序），用于计划里多个已定位步骤。任一相邻对跨图就截断。 */
    fun routeThrough(mapId: String, points: List<Triple<String?, Double, Double>>): RouteResult? {
        if (points.size < 2) return null
        val all = ArrayList<RouteWaypoint>()
        var dist = 0.0
        var straight = 0.0
        var hops = 0
        var fallback = false
        for (i in 0 until points.size - 1) {
            val (fidA, ax, ay) = points[i]
            val (fidB, bx, by) = points[i + 1]
            val r = route(mapId, ax, ay, bx, by, fidA, fidB)
            if (i == 0) all.addAll(r.points) else all.addAll(r.points.drop(1))
            dist += r.distance
            straight += r.straight
            hops += r.accessHops
            if (r.fallback) fallback = true
        }
        return RouteResult(mapId, all, dist, straight, hops, fallback)
    }

    // ---------------- 构图 ----------------

    private fun graph(mapId: String): Graph {
        cache[mapId]?.let { return it }
        val raw = WikiRepo.markersOf(mapId)
            .filter { it.frontierX.isFinite() && it.frontierY.isFinite() }

        // 1) 去重：同一个格子只留优先级最高的点，避免 Django 式的一坨重复坐标把图撑坏。
        val keep = LinkedHashMap<Pair<Long, Long>, Marker>()
        for (m in raw.sortedBy { typePriority(it.targetType) }) {
            val cell = (m.frontierX / DEDUPE_CELL).toLong() to (m.frontierY / DEDUPE_CELL).toLong()
            keep.putIfAbsent(cell, m)
        }
        val nodes = keep.values.toList()
        val n = nodes.size
        val adj = List(n) { ArrayList<Edge>() }
        val indexByFeature = HashMap<String, Int>(n)
        nodes.forEachIndexed { i, m -> indexByFeature[m.featureId] = i }

        // 2) k 近邻边
        val k = minOf(K, maxOf(0, n - 1))
        if (k > 0) {
            for (i in 0 until n) {
                val a = nodes[i]
                val best = ArrayList<Pair<Double, Int>>(n)
                for (j in 0 until n) {
                    if (i == j) continue
                    val b = nodes[j]
                    best.add(hypot(b.frontierX - a.frontierX, b.frontierY - a.frontierY) to j)
                }
                best.sortBy { it.first }
                for ((d, j) in best.take(k)) {
                    val cost = d * typeFactor(nodes[j].targetType)
                    adj[i].add(Edge(j, cost))
                    adj[j].add(Edge(i, cost))
                }
            }
        }

        // 3) 电梯 / 传送 / 跳跃点：同图内两两相连，用固定代价代表"上下层"
        val access = LinkedHashMap<Int, String>()
        for ((fid, kind) in accessPoints(mapId)) {
            val idx = indexByFeature[fid] ?: continue
            access[idx] = kind
        }
        val accessIdx = access.keys.toList()
        for (x in accessIdx.indices) {
            for (y in x + 1 until accessIdx.size) {
                val i = accessIdx[x]
                val j = accessIdx[y]
                val cost = minOf(accessCost(access[i] ?: "elevator"), accessCost(access[j] ?: "elevator"))
                adj[i].add(Edge(j, cost))
                adj[j].add(Edge(i, cost))
            }
        }

        // 4) 连通分量桥接
        connectComponents(nodes, adj)

        return Graph(nodes, adj, indexByFeature, access.keys.toSet()).also { cache[mapId] = it }
    }

    private fun accessPoints(mapId: String): List<Pair<String, String>> = Db.list(
        """
        SELECT a.feature_id, a.point_kind
        FROM route_access_point a
        JOIN frontiernav_map_marker f ON f.feature_id = a.feature_id
        WHERE f.map_id = ?
        """.trimIndent(),
        listOf(mapId),
    ) { c -> c.str("feature_id") to c.str("point_kind") }

    private fun accessCost(kind: String): Double = when (kind) {
        "warp" -> WARP_COST
        "jump" -> JUMP_COST
        else -> ELEVATOR_COST
    }

    private val autoName = Regex(
        """^(Enemy Spawn Point|Treasure Point|Collection Point|Salvage Point|Door Point|Warp Point|Elevator Point|Jump Point|Climbing Point)\s*#""",
    )

    /** "Door Point #85" 这种编号名对玩家没有意义，逐段导航里不该当停靠点。 */
    private val numberedName = Regex("""#\s*\d+$""")

    private fun isNotable(m: Marker): Boolean = when (m.targetType) {
        "Location", "Quest" -> true
        else -> !autoName.containsMatchIn(m.targetName) && !numberedName.containsMatchIn(m.targetName)
    }

    private fun typePriority(t: String): Int = when (t) {
        "Location" -> 0
        "Quest" -> 1
        "CollectionPoint" -> 2
        "TreasurePoint" -> 3
        "EnemySpawnPoint" -> 5
        else -> 4
    }

    /** 途经系数：敌人点更"贵"，路线会尽量贴着地标走。 */
    private fun typeFactor(t: String): Double = when (t) {
        "Location" -> 1.0
        "Quest" -> 1.0
        "CollectionPoint" -> 1.05
        "TreasurePoint" -> 1.05
        "EnemySpawnPoint" -> 1.35
        else -> 1.12
    }

    private fun connectComponents(nodes: List<Marker>, adj: List<MutableList<Edge>>) {
        val n = nodes.size
        if (n <= 1) return
        while (true) {
            val comp = IntArray(n) { -1 }
            var c = 0
            for (i in 0 until n) {
                if (comp[i] >= 0) continue
                val stack = ArrayDeque<Int>()
                stack.addLast(i)
                comp[i] = c
                while (stack.isNotEmpty()) {
                    val u = stack.removeLast()
                    for (e in adj[u]) if (comp[e.to] < 0) {
                        comp[e.to] = c
                        stack.addLast(e.to)
                    }
                }
                c++
            }
            if (c <= 1) return
            // 找距离最近的一对不同分量的点，连一条边，然后重新分分量
            var best = Double.MAX_VALUE
            var bi = -1
            var bj = -1
            for (i in 0 until n) for (j in i + 1 until n) {
                if (comp[i] == comp[j]) continue
                val d = hypot(nodes[j].frontierX - nodes[i].frontierX, nodes[j].frontierY - nodes[i].frontierY)
                if (d < best) {
                    best = d
                    bi = i
                    bj = j
                }
            }
            if (bi < 0 || bj < 0) return
            adj[bi].add(Edge(bj, best * 1.6))
            adj[bj].add(Edge(bi, best * 1.6))
        }
    }

    // ---------------- 最短路 ----------------

    private fun nearest(g: Graph, fx: Double, fy: Double, featureId: String?): Int {
        if (!featureId.isNullOrBlank()) g.indexByFeature[featureId]?.let { return it }
        var best = -1
        var bestD = Double.MAX_VALUE
        g.markers.forEachIndexed { i, m ->
            val d = hypot(m.frontierX - fx, m.frontierY - fy)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    private fun dijkstra(g: Graph, start: Int, goal: Int): List<Int>? {
        val n = g.markers.size
        if (start !in 0 until n || goal !in 0 until n) return null
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val done = BooleanArray(n)
        val pq = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        dist[start] = 0.0
        pq.add(0.0 to start)
        while (pq.isNotEmpty()) {
            val head = pq.poll() ?: break
            val d = head.first
            val u = head.second
            if (done[u]) continue
            done[u] = true
            if (u == goal) break
            for (e in g.adj[u]) {
                val nd = d + e.cost
                if (nd < dist[e.to]) {
                    dist[e.to] = nd
                    prev[e.to] = u
                    pq.add(nd to e.to)
                }
            }
        }
        if (dist[goal] == Double.MAX_VALUE) return null
        val out = ArrayList<Int>()
        var cur = goal
        while (cur >= 0) {
            out.add(cur)
            if (cur == start) break
            cur = prev[cur]
        }
        out.reverse()
        return out
    }
}
