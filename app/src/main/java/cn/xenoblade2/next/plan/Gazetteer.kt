package cn.xenoblade2.next.plan

import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.dbl
import cn.xenoblade2.next.data.int
import cn.xenoblade2.next.data.lng
import cn.xenoblade2.next.data.str
import cn.xenoblade2.next.data.strOrNull

/**
 * 内存地名词典：把「地名 / 敌人名 / 采集点名 → 地图点位」的匹配从逐条 SQL 变成一次加载。
 * 大概 265 地标 + 1169 敌人 + 1034 采集点，内存占用可忽略。
 */
object Gazetteer {

    data class Place(val name: String, val featureId: String, val mapId: String?, val region: String?, val type: String)

    /** NPC 的一处出现记录。x/z 用 `npc_game_appearance` 原值，实测与 frontier 坐标同尺度。 */
    data class Npc(val name: String, val mapId: String, val fx: Double, val fy: Double, val region: String?)

    private var places: List<Place>? = null
    private var enemyNames: List<String>? = null
    private var collectionNames: List<String>? = null
    private var collectItems: List<String>? = null
    private var regionKeys: List<String>? = null
    private var regionNamesCache: List<String>? = null
    private var npcs: List<Npc>? = null

    fun ensureLoaded() {
        if (places != null) return
        places = Db.list(
            """
            SELECT TRIM(COALESCE(f.target_name,'')) AS name, f.feature_id,
                   f.map_id, m.display_name AS region, f.target_type
            FROM frontiernav_map_marker f
            LEFT JOIN map m ON m.id = f.map_id
            WHERE f.target_name IS NOT NULL AND LENGTH(TRIM(f.target_name)) >= 2
              AND f.target_type IN ('Location','DoorPoint','WarpPoint','ElevatorPoint','JumpPoint',
                                    'Quest','Shop','Inn','HeartToHeart','SalvagePoint')
            """.trimIndent(),
        ) { c ->
            Place(c.str("name"), c.str("feature_id"), c.strOrNull("map_id"), c.strOrNull("region"), c.str("target_type"))
        }.sortedByDescending { it.name.length }

        enemyNames = Db.list("SELECT title FROM entity WHERE type = 'enemy' AND title IS NOT NULL") { c -> c.str("title") }
            .filter { it.length >= 2 }.distinct().sortedByDescending { it.length }

        collectionNames = Db.list(
            "SELECT title FROM entity WHERE type IN ('collection_point','torna_collection_point') AND title IS NOT NULL",
        ) { c -> c.str("title") }.filter { it.length >= 2 }.distinct().sortedByDescending { it.length }
    }

    fun placesInRegion(region: String?): List<Place> {
        ensureLoaded()
        val hint = normalizeRegion(region)
        if (hint.isEmpty()) return places.orEmpty()
        return places.orEmpty().filter { p ->
            val r = p.region ?: return@filter false
            r == hint || r.contains(hint) || hint.contains(r)
        }
    }

    /**
     * 在文本里找地名，返回最长匹配（≤4 个候选）。
     *
     * 两轮：先看「文本包含地名」（自然语言整句），没命中再看「地名包含文本」（用户只打了半截，
     * 如「穿岩」→「穿岩之门」）。regionHint 会做「的 / 空格」归一化，避免「古拉的」过滤掉全部点位。
     */
    fun matchPlaces(text: String, regionHint: String? = null, limit: Int = 4): List<Place> {
        if (text.isBlank()) return emptyList()
        val pool = placesInRegion(regionHint)
        val hit = ArrayList<Place>()
        val used = HashSet<String>()
        // pool 已按名字长度降序，先命中的就是最长地名
        for (p in pool) {
            if (p.mapId.isNullOrBlank()) continue
            if (used.contains(p.featureId)) continue
            if (text.contains(p.name)) {
                hit.add(p)
                used.add(p.featureId)
                if (hit.size >= limit) break
            }
        }
        if (hit.isNotEmpty()) return hit
        val q = text.trim()
        if (q.length < 2) return emptyList()
        for (p in pool) {
            if (p.mapId.isNullOrBlank()) continue
            if (used.contains(p.featureId)) continue
            if (p.name.contains(q)) {
                hit.add(p)
                used.add(p.featureId)
                if (hit.size >= limit) break
            }
        }
        return hit
    }

    private fun normalizeRegion(region: String?): String =
        region?.trim().orEmpty().replace("的", "").replace(" ", "")

    /** 地区名（map.display_name）与区域名（map.menu_group），用于识别「古拉的…」这类组合写法。 */
    fun regionKeywords(): List<String> {
        ensureLoaded()
        regionKeys?.let { return it }
        val fromPlaces = places.orEmpty().mapNotNull { it.region }
        val fromMaps = Db.list("SELECT display_name, menu_group FROM map") { c ->
            listOf(c.str("display_name"), c.str("menu_group"))
        }.flatten()
        return (fromPlaces + fromMaps)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }
            .also { regionKeys = it }
    }

    /**
     * 把「古拉的穿岩之门」「利贝拉里塔斯·里吉特海域」拆成候选片段。
     * 只按明显的连接词拆，不拆单字，避免把真实地名拆坏。
     */
    fun splitQuery(text: String): List<String> {
        val seps = listOf("的", "·", "・", "/", "、", ",", "，", "在", "从", "到", "去")
        var parts = listOf(text.trim())
        for (sep in seps) {
            parts = parts.flatMap { it.split(sep) }
        }
        return parts.map { it.trim() }.filter { it.length >= 2 }.distinct()
    }

    fun matchEnemy(text: String): String? {
        ensureLoaded()
        return enemyNames.orEmpty().firstOrNull { text.contains(it) }
    }

    fun matchCollection(text: String): String? {
        ensureLoaded()
        return collectionNames.orEmpty().firstOrNull { text.contains(it) }
    }

    /**
     * 步骤文本里的「可采集物品名」。
     * 只收录真的能落到采集点的物品（collection_drop ⋈ collection_marker ⋈ marker），
     * 否则匹配上也画不出点。按名字长度降序，先命中的是最长匹配。
     */
    fun matchCollectItem(text: String): String? {
        ensureLoaded()
        if (text.isBlank()) return null
        val pool = collectItems ?: Db.list(
            """
            SELECT DISTINCT d.item_name
            FROM collection_drop d
            JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
            JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
            WHERE LENGTH(d.item_name) >= 2
            """.trimIndent(),
        ) { c -> c.str("item_name") }
            .sortedByDescending { it.length }
            .also { collectItems = it }
        return pool.firstOrNull { text.contains(it) }
    }

    /**
     * 步骤文本里的 NPC 名 → 该 NPC 的出现坐标。
     *
     * `npc_game_appearance.x/z` 的数值范围与 `frontiernav_map_marker.frontier_x/y` 一致
     * （古拉·托里格镇 x∈[-174,164]，同图 frontier_x 也是这个区间），所以直接用，不再做世界坐标拟合。
     * 没有 game_map 的 NPC（多为黄金之国 DLC）在 SQL 里就被 JOIN 掉，不会返回错坐标。
     *
     * 名字按长度降序匹配，避免「阿尔巴」命中「阿尔巴马格」；有地区提示时只接受同地区的出现记录，
     * 宁可放弃也不乱钉点。
     */
    fun matchNpc(text: String, regionHint: String? = null): Npc? {
        ensureLoaded()
        if (text.isBlank()) return null
        val pool = npcs ?: Db.list(
            """
            SELECT TRIM(e.title) AS name, m.id AS map_id, g.x AS fx, g.z AS fy, m.display_name AS region
            FROM npc_game_appearance g
            JOIN entity e ON e.id = g.npc_id
            JOIN map m ON m.name = g.game_map
            WHERE e.title IS NOT NULL AND LENGTH(TRIM(e.title)) >= 2 AND g.game_map <> ''
            """.trimIndent(),
        ) { c ->
            Npc(c.str("name"), c.str("map_id"), c.dbl("fx"), c.dbl("fy"), c.strOrNull("region"))
        }.sortedByDescending { it.name.length }.also { npcs = it }

        val hint = normalizeRegion(regionHint)
        var any: Npc? = null
        for (n in pool) {
            if (!text.contains(n.name)) continue
            if (any == null) any = n
            if (hint.isEmpty()) return n
            val r = normalizeRegion(n.region)
            if (r.isNotEmpty() && (r.contains(hint) || hint.contains(r))) return n
        }
        return null
    }

    /**
     * 「甲狼兽族」这种族名：库里没有族表，退一步去掉「族」，用「甲狼兽」去敌人名里找。
     * 返回可直接喂给 `WikiRepo.enemySpawns` 的名片段；没有任何敌人包含它就返回 null。
     */
    fun matchEnemyFamily(text: String): String? {
        ensureLoaded()
        if (!text.contains("族")) return null
        val head = text.substringBeforeLast("族")
        val base = head
            .split("的", "在", "打倒", "讨伐", "击败", "以外", "只", " ", "　", "：", ":", "「", "」")
            .lastOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (base.length < 2) return null
        return base.takeIf { b -> enemyNames.orEmpty().any { it.contains(b) } }
    }

    /**
     * 文本里出现的地区名（`map.display_name`），最长优先。
     *
     * 开环需求里常写简称（「在利贝拉里塔打倒…」对应「利贝拉里塔斯岛屿群」），
     * 全长没命中时再拿地区名前 3~8 字去试，宁可少认也不乱认：
     * 「莫尔斯断崖」这类全长能命中的情况先走精确轮，不会被「莫尔斯之地」抢走。
     */
    fun matchRegion(text: String): String? {
        ensureLoaded()
        if (text.isBlank()) return null
        val names = regionNamesCache ?: Db.list(
            "SELECT DISTINCT display_name FROM map WHERE display_name IS NOT NULL AND LENGTH(TRIM(display_name)) >= 2",
        ) { c -> c.str("display_name") }
            .sortedByDescending { it.length }
            .also { regionNamesCache = it }
        names.firstOrNull { text.contains(it) }?.let { return it }
        for (n in names) {
            for (len in minOf(n.length - 1, 8) downTo 3) {
                if (text.contains(n.substring(0, len))) return n
            }
        }
        return null
    }

    fun allEnemyNames(): List<String> {
        ensureLoaded()
        return enemyNames.orEmpty()
    }
}
