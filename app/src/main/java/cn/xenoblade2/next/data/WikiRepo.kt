package cn.xenoblade2.next.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只读查询层：所有 SQL 都集中在这里，UI 不直接碰 Cursor。 */
object WikiRepo {

    private var mapListCache: List<MapInfo>? = null
    private val mapById = HashMap<String, MapInfo>()
    private val markerByMap = HashMap<String, List<Marker>>()
    private val markersByFeature = HashMap<String, Marker>()
    private val affineX = HashMap<String, Affine1D?>()
    private val affineY = HashMap<String, Affine1D?>()

    suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---------------- 地图 ----------------

    fun maps(): List<MapInfo> {
        mapListCache?.let { return it }
        val list = Db.list(
            """
            SELECT id, name, image_path, display_name, menu_group, width, height,
                   lower_x, lower_z, upper_x, upper_z, marker_count
            FROM map
            ORDER BY display_name, menu_group
            """.trimIndent(),
        ) { c ->
            MapInfo(
                id = c.str("id"),
                gameName = c.str("name"),
                imagePath = c.str("image_path"),
                region = c.str("display_name"),
                area = c.str("menu_group"),
                width = c.dblOrNull("width") ?: 1.0,
                height = c.dblOrNull("height") ?: 1.0,
                lowerX = c.dblOrNull("lower_x") ?: -180.0,
                lowerZ = c.dblOrNull("lower_z") ?: -85.05,
                upperX = c.dblOrNull("upper_x") ?: 180.0,
                upperZ = c.dblOrNull("upper_z") ?: 85.05,
                markerCount = c.intOr("marker_count", 0),
            )
        }
        // 同一 menu_group 常常有多张图（世界树 4 张「下层」），补一个代表性地标名当副标题
        val labels = landmarkLabels()
        val labeled = list.map { m -> labels[m.id]?.let { m.copy(landmark = it) } ?: m }
        mapListCache = labeled
        labeled.forEach { mapById[it.id] = it }
        return labeled
    }

    /** map_id → 该图最有代表性的地标名。 */
    private fun landmarkLabels(): Map<String, String> {
        val byMap = HashMap<String, MutableList<String>>()
        Db.list(
            "SELECT map_id, target_name FROM frontiernav_map_marker WHERE target_type = 'Location'",
        ) { c ->
            val id = c.str("map_id")
            val name = c.str("target_name").trim()
            if (id.isNotBlank() && name.isNotEmpty()) byMap.getOrPut(id) { ArrayList() }.add(name)
        }
        return byMap.mapValues { (_, names) -> pickLandmark(names) }
    }

    /**
     * 选层名：优先「第一层/水星天/外围」这类结构性地点，避开「第 8 电梯」这种附属设施，
     * 同类里取最短的（越长越像细分房间）。
     */
    private fun pickLandmark(names: List<String>): String {
        val usable = names.filterNot { it.contains("电梯") }
        val structural = usable.filter { it.contains('层') || it.contains('天') || it.contains("外围") }
        val pool = structural.ifEmpty { usable.ifEmpty { names } }
        return pool.minByOrNull { it.length } ?: ""
    }

    fun map(id: String?): MapInfo? {
        if (id.isNullOrBlank()) return null
        mapById[id]?.let { return it }
        if (mapListCache == null) maps()
        return mapById[id]
    }

    fun regions(): List<String> = maps().map { it.region }.distinct().sorted()

    fun markersOf(mapId: String): List<Marker> {
        markerByMap[mapId]?.let { return it }
        val list = Db.list(
            """
            SELECT feature_id, map_id, map_game_name, frontier_x, frontier_y, world_x, world_z,
                   target_type, COALESCE(target_name, target_game_name, feature_id) AS target_name, target_id
            FROM frontiernav_map_marker
            WHERE map_id = ?
            ORDER BY CASE target_type
                WHEN 'Location' THEN 0 WHEN 'Quest' THEN 1 WHEN 'CollectionPoint' THEN 3
                WHEN 'EnemySpawnPoint' THEN 4 ELSE 2 END
            """.trimIndent(),
            listOf(mapId),
        ) { c -> marker(c) }
        markerByMap[mapId] = list
        list.forEach { markersByFeature[it.featureId] = it }
        return list
    }

    fun marker(featureId: String): Marker? {
        markersByFeature[featureId]?.let { return it }
        return Db.first(
            """
            SELECT feature_id, map_id, map_game_name, frontier_x, frontier_y, world_x, world_z,
                   target_type, COALESCE(target_name, target_game_name, feature_id) AS target_name, target_id
            FROM frontiernav_map_marker WHERE feature_id = ?
            """.trimIndent(),
            listOf(featureId),
        ) { c -> marker(c) }?.also { markersByFeature[it.featureId] = it }
    }

    fun markers(featureIds: Collection<String>): Map<String, Marker> {
        val missing = featureIds.distinct().filter { it.isNotBlank() && it !in markersByFeature }
        if (missing.isEmpty()) return featureIds.mapNotNull { markersByFeature[it]?.let { m -> it to m } }.toMap()
        missing.chunked(400).forEach { chunk ->
            val sql = """
                SELECT feature_id, map_id, map_game_name, frontier_x, frontier_y, world_x, world_z,
                       target_type, COALESCE(target_name, target_game_name, feature_id) AS target_name, target_id
                FROM frontiernav_map_marker WHERE feature_id IN (${chunk.joinToString(",") { "?" }})
            """.trimIndent()
            Db.list(sql, chunk) { c -> marker(c) }.forEach { markersByFeature[it.featureId] = it }
        }
        return featureIds.mapNotNull { markersByFeature[it]?.let { m -> it to m } }.toMap()
    }

    private fun marker(c: android.database.Cursor) = Marker(
        featureId = c.str("feature_id"),
        mapId = c.strOrNull("map_id") ?: "",
        mapGameName = c.strOrNull("map_game_name") ?: "",
        frontierX = c.dbl("frontier_x"),
        frontierY = c.dbl("frontier_y"),
        worldX = c.dblOrNull("world_x"),
        worldZ = c.dblOrNull("world_z"),
        targetType = c.strOrNull("target_type") ?: "",
        targetName = c.str("target_name"),
        targetId = c.strOrNull("target_id") ?: "",
    )

    /** 某张图的世界坐标 → 平面坐标仿射系数（按需拟合后缓存）。 */
    fun affineOf(mapId: String): Pair<Affine1D?, Affine1D?> {
        if (affineX.containsKey(mapId)) return affineX[mapId] to affineY[mapId]
        val pts = markersOf(mapId).filter { it.worldX != null && it.worldZ != null }
        val fx = AffineFit.fit(pts.map { it.worldX!! }, pts.map { it.frontierX })?.takeIf { it.usable }
        val fy = AffineFit.fit(pts.map { it.worldZ!! }, pts.map { it.frontierY })?.takeIf { it.usable }
        affineX[mapId] = fx
        affineY[mapId] = fy
        return fx to fy
    }

    /** 世界坐标 → 该图上的 marker 平面坐标；拟合不可用则返回 null。 */
    fun project(mapId: String, worldX: Double, worldZ: Double): Pair<Double, Double>? {
        val (fx, fy) = affineOf(mapId)
        if (fx == null || fy == null) return null
        return fx.map(worldX) to fy.map(worldZ)
    }

    fun landmarksOfRegion(region: String): List<Marker> = Db.list(
        """
        SELECT f.feature_id, f.map_id, f.map_game_name, f.frontier_x, f.frontier_y, f.world_x, f.world_z,
               f.target_type, COALESCE(f.target_name, f.target_game_name, f.feature_id) AS target_name, f.target_id
        FROM frontiernav_map_marker f
        JOIN map m ON m.id = f.map_id
        WHERE m.display_name = ? AND f.target_type IN ('Location','DoorPoint','WarpPoint','ElevatorPoint','JumpPoint')
        """.trimIndent(),
        listOf(region),
    ) { c -> marker(c) }

    fun landmarkNames(): List<Triple<String, String, String>> = Db.list(
        """
        SELECT feature_id, COALESCE(target_name,'') AS n, COALESCE(map_id,'') AS m
        FROM frontiernav_map_marker WHERE target_type = 'Location' AND target_name IS NOT NULL
        """.trimIndent(),
    ) { c -> Triple(c.str("feature_id"), c.str("n"), c.str("m")) }

    // ---------------- 任务 ----------------

    fun questCategories(): List<Pair<String, Int>> = Db.list(
        "SELECT category, COUNT(*) AS n FROM quest GROUP BY category ORDER BY n DESC",
    ) { c -> (c.strOrNull("category") ?: "未分类") to c.int("n") }

    fun quests(category: String? = null, keyword: String = "", limit: Int = 300): List<QuestSummary> {
        val where = StringBuilder("1=1")
        val args = ArrayList<Any?>()
        if (!category.isNullOrBlank() && category != "全部") {
            where.append(" AND q.category = ?")
            args.add(category)
        }
        if (keyword.isNotBlank()) {
            where.append(" AND (e.title LIKE ? OR e.name_zh LIKE ?)")
            args.add("%$keyword%")
            args.add("%$keyword%")
        }
        args.add(limit)
        return Db.list(
            """
            SELECT e.id, COALESCE(e.title,'') AS title, COALESCE(e.name_zh,'') AS name_zh,
                   COALESCE(q.category,'') AS category, COALESCE(q.npc,'') AS npc,
                   COALESCE(q.available_chapter,'') AS chapter, q.is_torna,
                   (SELECT COUNT(*) FROM task_workflow_step s WHERE s.entity_id = e.id) AS step_count
            FROM quest q JOIN entity e ON e.id = q.entity_id
            WHERE $where
            ORDER BY q.page_order, e.id
            LIMIT ?
            """.trimIndent(),
            args,
        ) { c ->
            QuestSummary(
                id = c.lng("id"),
                title = c.str("title"),
                nameZh = c.str("name_zh"),
                category = c.str("category"),
                npc = c.str("npc"),
                chapter = c.str("chapter"),
                stepCount = c.int("step_count"),
                isTorna = c.intOr("is_torna", 0) == 1,
            )
        }
    }

    fun questDetail(id: Long): QuestDetail? {
        val head = Db.first(
            """
            SELECT e.id, COALESCE(e.title,'') AS title, COALESCE(q.category,'') AS category,
                   COALESCE(q.npc,'') AS npc, COALESCE(q.available_chapter,'') AS chapter,
                   COALESCE(q.condition_text,'') AS condition_text,
                   COALESCE(q.description,'') AS description,
                   COALESCE(q.gold,0) AS gold, COALESCE(q.exp,0) AS exp
            FROM quest q JOIN entity e ON e.id = q.entity_id WHERE e.id = ?
            """.trimIndent(),
            listOf(id),
        ) { c ->
            QuestDetail(
                id = c.lng("id"),
                title = c.str("title"),
                category = c.str("category"),
                npc = c.str("npc"),
                chapter = c.str("chapter"),
                condition = c.str("condition_text"),
                description = c.str("description"),
                gold = c.int("gold"),
                exp = c.int("exp"),
                locations = emptyList(),
                steps = emptyList(),
                rewardText = "",
            )
        } ?: return null

        val locations = Db.list(
            "SELECT location FROM quest_location WHERE quest_id = ? ORDER BY position",
            listOf(id),
        ) { c -> c.str("location") }

        val linksByStep = HashMap<Int, MutableList<StepLink>>()
        Db.list(
            "SELECT step_position, target, label FROM task_workflow_link WHERE entity_id = ? ORDER BY step_position",
            listOf(id),
        ) { c ->
            StepLink(c.int("step_position"), c.str("target"), c.str("label"))
        }.forEach { linksByStep.getOrPut(it.stepPosition) { ArrayList() }.add(it) }

        val steps = Db.list(
            """
            SELECT position, COALESCE(kind,'') AS kind, COALESCE(text,'') AS text, COALESCE(detail,'') AS detail
            FROM task_workflow_step WHERE entity_id = ? ORDER BY position
            """.trimIndent(),
            listOf(id),
        ) { c ->
            val pos = c.int("position")
            QuestStep(pos, c.str("kind"), c.str("text"), c.str("detail"), linksByStep[pos] ?: emptyList())
        }

        val rewards = Db.list(
            "SELECT COALESCE(item_name,'') AS item_name, quantity FROM quest_reward WHERE quest_id = ? ORDER BY position",
            listOf(id),
        ) { c -> "${c.str("item_name")} ×${c.intOr("quantity", 1)}" }

        return head.copy(locations = locations, steps = steps, rewardText = rewards.joinToString("、"))
    }

    /** 任务在图上已知的点（任务 NPC / 目标点）。 */
    fun questPins(questId: Long): List<Marker> = Db.list(
        """
        SELECT f.feature_id, f.map_id, f.map_game_name, f.frontier_x, f.frontier_y, f.world_x, f.world_z,
               f.target_type, COALESCE(f.target_name, f.target_game_name, f.feature_id) AS target_name, f.target_id
        FROM quest_frontiernav_marker q
        JOIN frontiernav_map_marker f ON f.feature_id = q.feature_id
        WHERE q.quest_id = ?
        """.trimIndent(),
        listOf(questId),
    ) { c -> marker(c) }

    // ---------------- 异刃 ----------------

    fun blades(keyword: String = ""): List<BladeSummary> = Db.list(
        """
        SELECT b.entity_id, COALESCE(e.title,'') AS title, COALESCE(b.element,'') AS element,
               COALESCE(b.combat_role,'') AS combat_role, COALESCE(b.weapon,'') AS weapon,
               COALESCE(b.portrait,'') AS portrait,
               (SELECT COUNT(*) FROM blade_affinity_node n WHERE n.blade_id = b.entity_id) AS node_count
        FROM blade b JOIN entity e ON e.id = b.entity_id
        WHERE ? = '' OR e.title LIKE '%' || ? || '%' OR e.name_en LIKE '%' || ? || '%'
        ORDER BY b.entity_id
        """.trimIndent(),
        listOf(keyword, keyword, keyword),
    ) { c ->
        BladeSummary(
            c.lng("entity_id"), c.str("title"), c.str("element"), c.str("combat_role"),
            c.str("weapon"), c.int("node_count"), c.str("portrait"),
        )
    }

    fun bladeNodes(bladeId: Long): List<AffinityNode> {
        val linksByLevel = HashMap<Long, MutableList<StepLink>>()
        Db.list(
            """
            SELECT ll.level_id, ll.target, ll.label
            FROM blade_affinity_level_link ll
            JOIN blade_affinity_level l ON l.id = ll.level_id
            JOIN blade_affinity_node n ON n.id = l.node_id
            WHERE n.blade_id = ?
            """.trimIndent(),
            listOf(bladeId),
        ) { c -> Triple(c.lng("level_id"), c.str("target"), c.str("label")) }
            .forEach { (lid, t, lab) -> linksByLevel.getOrPut(lid) { ArrayList() }.add(StepLink(0, t, lab)) }

        val levelsByNode = HashMap<Long, MutableList<AffinityLevel>>()
        Db.list(
            """
            SELECT l.id, l.node_id, l.level, COALESCE(l.effect,'') AS effect,
                   COALESCE(l.requirement,'') AS requirement, l.achievement_id,
                   COALESCE(l.achievement_count,0) AS achievement_count
            FROM blade_affinity_level l
            JOIN blade_affinity_node n ON n.id = l.node_id
            WHERE n.blade_id = ?
            ORDER BY l.node_id, l.level
            """.trimIndent(),
            listOf(bladeId),
        ) { c ->
            val id = c.lng("id")
            c.lng("node_id") to AffinityLevel(
                id = id,
                level = c.int("level"),
                effect = c.str("effect"),
                requirement = c.str("requirement"),
                achievementId = c.strOrNull("achievement_id"),
                achievementCount = c.int("achievement_count"),
                links = linksByLevel[id] ?: emptyList(),
            )
        }.forEach { (nodeId, lv) -> levelsByNode.getOrPut(nodeId) { ArrayList() }.add(lv) }

        return Db.list(
            "SELECT id, name, category, position FROM blade_affinity_node WHERE blade_id = ? ORDER BY position",
            listOf(bladeId),
        ) { c ->
            val id = c.lng("id")
            AffinityNode(id, c.str("name"), c.str("category"), c.int("position"), levelsByNode[id] ?: emptyList())
        }
    }

    // ---------------- 敌人 / 搜索 ----------------

    /** 按名字模糊找敌人刷新点（带地图 id 与坐标）。 */
    fun enemySpawns(nameLike: String, limit: Int = 40): List<EnemyAt> {
        if (nameLike.isBlank()) return emptyList()
        return Db.list(
            """
            SELECT e.id AS id, e.title AS title,
                   COALESCE((SELECT MIN(level) FROM enemy_stats s WHERE s.enemy_id = e.id), 0) AS level,
                   f.map_id AS map_id, f.feature_id AS feature_id, f.world_x, f.world_z
            FROM entity e
            JOIN enemy en ON en.entity_id = e.id
            JOIN enemy_spawn sp ON sp.enemy_id = en.enemy_code
            JOIN frontiernav_map_marker f ON f.target_id = sp.spawn_id
            WHERE e.title LIKE '%' || ? || '%'
            GROUP BY f.feature_id
            LIMIT ?
            """.trimIndent(),
            listOf(nameLike, limit),
        ) { c ->
            EnemyAt(
                entityId = c.lng("id"),
                name = c.str("title"),
                level = c.int("level"),
                mapId = c.strOrNull("map_id") ?: "",
                featureId = c.str("feature_id"),
                worldX = c.dblOrNull("world_x"),
                worldZ = c.dblOrNull("world_z"),
            )
        }.filter { it.mapId.isNotBlank() }
    }

    /**
     * 采集物品 → 采集点坐标。
     * collection_drop(item_name → collection_point_id) + collection_marker(→ marker_id)
     * + frontiernav_map_marker(feature_id = marker_id)，实测 1029/1029 全部能 join 上。
     */
    fun collectMarkers(itemName: String, regionHint: String? = null, limit: Int = 8): List<Marker> {
        if (itemName.isBlank()) return emptyList()
        val regionSql = if (regionHint.isNullOrBlank()) "" else
            " AND f.map_id IN (SELECT id FROM map WHERE display_name = ?)"
        val args = if (regionHint.isNullOrBlank()) listOf<Any?>(itemName, limit)
        else listOf<Any?>(itemName, regionHint, limit)
        return Db.list(
            """
            SELECT DISTINCT f.feature_id, f.map_id, f.map_game_name, f.frontier_x, f.frontier_y,
                   f.world_x, f.world_z, f.target_type,
                   COALESCE(f.target_name, f.target_game_name, f.feature_id) AS target_name, f.target_id
            FROM collection_drop d
            JOIN collection_marker cm ON cm.collection_point_id = d.collection_point_id
            JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
            WHERE d.item_name = ?$regionSql
            LIMIT ?
            """.trimIndent(),
            args,
        ) { c -> marker(c) }
    }

    fun search(keyword: String, limit: Int = 60): List<SearchHit> {
        if (keyword.isBlank()) return emptyList()
        val kw = "%$keyword%"
        return Db.list(
            """
            SELECT e.id, e.type, COALESCE(e.title,'') AS title, COALESCE(e.name_zh, e.source_title, '') AS sub,
                   COALESCE(i.category,'') AS item_category, COALESCE(i.item_type,'') AS item_type,
                   COALESCE(b.portrait,'') AS blade_portrait
            FROM entity e
            LEFT JOIN item i ON i.entity_id = e.id
            LEFT JOIN blade b ON b.entity_id = e.id
            WHERE e.title LIKE ? OR e.name_zh LIKE ? OR e.source_title LIKE ?
            ORDER BY CASE e.type
                WHEN 'quest' THEN 0 WHEN 'blade' THEN 1 WHEN 'enemy' THEN 2 WHEN 'location' THEN 3
                WHEN 'item' THEN 4 ELSE 5 END, e.id
            LIMIT ?
            """.trimIndent(),
            listOf(kw, kw, kw, limit),
        ) { c ->
            val type = c.str("type")
            SearchHit(
                c.lng("id"), type, c.str("title"), c.str("sub"),
                when (type) {
                    "item" -> ItemIcons.path(c.str("item_category"), c.str("item_type"))
                    "blade" -> c.str("blade_portrait").ifBlank { null }
                    else -> null
                },
            )
        }
    }

    fun itemCount(): Int = Db.count("SELECT COUNT(*) FROM entity")
}
