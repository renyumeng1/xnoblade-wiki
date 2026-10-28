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

    // ---------------- 交互地图（对齐原版 1.2.2 的三级目录） ----------------

    /** 原版 `interactiveRegions()` 的固定顺序，其余地区按名称排在后面。 */
    private val REGION_ORDER = listOf(
        "阿伐利缇亚商会", "云海探查船 涡潮号", "古代船", "古拉", "英维迪亚烈王国",
        "斯佩比亚帝国", "檀佩兰提亚", "阿卡狄亚教廷", "洛修利亚王国", "利贝拉里塔斯岛屿群",
        "艾尔皮斯灵洞", "莫尔斯断崖", "莫尔斯之地", "世界树", "第一低轨道站",
    )

    /** 原版 `maps()` 的过滤条件：没名字 / 没分组 / 没图的内部切片不进展，另有 _shipoff/_lodoff 变体。 */
    private fun interactiveMapList(): List<MapInfo> = maps().filter {
        it.region.isNotBlank() &&
            it.area.isNotBlank() &&
            it.imagePath.isNotBlank() &&
            !it.gameName.contains("_shipoff") &&
            !it.gameName.contains("_lodoff") &&
            it.markerCount > 0
    }

    /** 地区 → 该地区的所有地图，按原版固定顺序。 */
    fun interactiveRegions(): List<Pair<String, List<MapInfo>>> {
        val grouped = interactiveMapList().groupBy { it.region }
        val ordered = REGION_ORDER.mapNotNull { name -> grouped[name]?.let { name to it } }
        val rest = grouped.filterKeys { it !in REGION_ORDER }.toSortedMap()
            .map { (name, list) -> name to list }
        return ordered + rest
    }

    private val interactiveByMap = HashMap<String, List<Marker>>()

    /**
     * 原版 `interactiveMarkers()`：只保留交互地图真正会画出来的点位。
     *
     * - `frontiernav_map_marker` 里挑稀有怪 / 任务 / 商店 / 收集点 / 宝箱 / 地点（含地标、秘境）
     * - 并上牵绊对话表（改名取自 entity.title）
     * - 并上路径点表（category 记 point_kind）
     */
    fun interactiveMarkers(mapId: String): List<Marker> {
        interactiveByMap[mapId]?.let { return it }
        val gameName = map(mapId)?.gameName ?: mapId
        val list = Db.list(
            """
            SELECT feature_id, map_game_name,
                   COALESCE(NULLIF(target_name,''), target_game_name, '') AS target_name,
                   target_type, COALESCE(target_category,'') AS target_category,
                   frontier_x, frontier_y,
                   CASE WHEN target_type = 'TreasurePoint' AND EXISTS (
                       SELECT 1 FROM treasure_chest tc JOIN entity e ON e.id = tc.entity_id
                       WHERE e.name_en = frontiernav_map_marker.target_game_name
                         AND COALESCE(tc.appearance_condition,'') <> ''
                   ) THEN 1 ELSE 0 END AS has_condition
            FROM frontiernav_map_marker
            WHERE map_game_name = ?
              AND (
                target_type IN ('TombstonePoint','Quest','Shop','CollectionPoint','TreasurePoint')
                OR (target_type = 'Location' AND target_category IN ('Location (Location Category)','Landmark','Secret Area'))
              )
            UNION ALL
            SELECT f.feature_id, f.map_game_name, e.title, 'HeartToHeart', '', f.frontier_x, f.frontier_y, 0
            FROM heart_to_heart_marker h
            JOIN frontiernav_map_marker f ON f.feature_id = h.feature_id
            JOIN entity e ON e.id = h.entity_id
            WHERE f.map_game_name = ?
            UNION ALL
            SELECT r.feature_id, f.map_game_name, e.title, 'RouteAccessPoint', r.point_kind, f.frontier_x, f.frontier_y, 0
            FROM route_access_point r
            JOIN frontiernav_map_marker f ON f.feature_id = r.feature_id
            JOIN entity e ON e.id = r.entity_id
            WHERE f.map_game_name = ?
            """.trimIndent(),
            listOf(gameName, gameName, gameName),
        ) { c ->
            // 不复用 marker()：交互表不带 world 坐标，不能污染 route planning 的缓存
            Marker(
                featureId = c.str("feature_id"),
                mapId = mapId,
                mapGameName = c.str("map_game_name"),
                frontierX = c.dbl("frontier_x"),
                frontierY = c.dbl("frontier_y"),
                worldX = null,
                worldZ = null,
                targetType = c.str("target_type"),
                targetName = c.str("target_name"),
                targetId = "",
                targetCategory = c.strOrNull("target_category") ?: "",
                hasAppearanceCondition = c.intOr("has_condition", 0) != 0,
            )
        }
        interactiveByMap[mapId] = list
        return list
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
                   COALESCE(q.condition_text,'') AS condition_text,
                   COALESCE((SELECT ql.location FROM quest_location ql WHERE ql.quest_id = q.entity_id ORDER BY ql.position LIMIT 1),'') AS location,
                   COALESCE((SELECT group_concat(r.region,'|') FROM quest_page_region r WHERE r.quest_id = q.entity_id),'') AS regions,
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
                location = c.str("location"),
                condition = c.str("condition_text"),
                regions = c.str("regions").split('|').filter { it.isNotBlank() },
            )
        }
    }

    /** 某分类任务出现过的地区（按数量降序），用于列表筛选 chips。 */
    fun questRegions(category: String, limit: Int = 12): List<String> = Db.list(
        """
        SELECT r.region AS region, COUNT(*) AS n
        FROM quest_page_region r JOIN quest q ON q.entity_id = r.quest_id
        WHERE q.category = ?
        GROUP BY r.region ORDER BY n DESC, r.region LIMIT ?
        """.trimIndent(),
        listOf(category, limit),
    ) { c -> c.str("region") }

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
               COALESCE(bc.category,'') AS category,
               COALESCE(b.english_name,'') AS english_name,
               COALESCE(b.driver_modifier,'') AS driver_modifier,
               COALESCE(b.physical_defense,0) AS physical_defense,
               COALESCE(b.ether_defense,0) AS ether_defense,
               COALESCE(b.initial_strength,0) AS initial_strength,
               COALESCE(b.cooldown,0) AS cooldown,
               COALESCE(b.aux_core_slots,0) AS aux_core_slots,
               COALESCE(b.unlock_method,'') AS unlock_method,
               (SELECT COUNT(*) FROM blade_affinity_node n WHERE n.blade_id = b.entity_id) AS node_count
        FROM blade b JOIN entity e ON e.id = b.entity_id
        LEFT JOIN blade_category bc ON bc.blade_id = b.entity_id
        WHERE ? = '' OR e.title LIKE '%' || ? || '%' OR e.name_en LIKE '%' || ? || '%'
        ORDER BY b.entity_id
        """.trimIndent(),
        listOf(keyword, keyword, keyword),
    ) { c ->
        BladeSummary(
            id = c.lng("entity_id"),
            name = c.str("title"),
            element = c.str("element"),
            role = c.str("combat_role"),
            weapon = c.str("weapon"),
            nodeCount = c.int("node_count"),
            portrait = c.str("portrait"),
            category = c.str("category"),
            englishName = c.str("english_name"),
            modifier = c.str("driver_modifier"),
            physicalDefense = c.dbl("physical_defense"),
            etherDefense = c.dbl("ether_defense"),
            strength = c.dbl("initial_strength"),
            cooldown = c.dbl("cooldown"),
            auxSlots = c.int("aux_core_slots"),
            unlockMethod = c.str("unlock_method"),
        )
    }

    fun bladeSkills(bladeId: Long): List<BladeSkillRow> = Db.list(
        """
        SELECT id, position, category, name, COALESCE(description,'') AS description,
               COALESCE(effect,'') AS effect, COALESCE(max_level,'') AS max_level
        FROM blade_skill WHERE blade_id = ? ORDER BY position
        """.trimIndent(),
        listOf(bladeId),
    ) { c ->
        BladeSkillRow(
            id = c.lng("id"),
            position = c.int("position"),
            category = c.str("category"),
            name = c.str("name"),
            description = c.str("description"),
            effect = c.str("effect"),
            maxLevel = c.str("max_level"),
        )
    }

    /** 与这把异刃武器 / 属性匹配的佣兵团任务，匹配项越多排得越前。 */
    fun mercenaryMissionsForBlade(weapon: String, element: String, limit: Int = 20): List<MercenaryMissionRow> = Db.list(
        """
        SELECT * FROM (
            SELECT e.id AS id, COALESCE(e.title,'') AS title,
                   COALESCE((SELECT group_concat(r.kind || '|' || r.value, '~')
                             FROM mercenary_dispatch_requirement r WHERE r.mission_id = e.id),'') AS reqs,
                   COALESCE((SELECT group_concat(w.item_name || '×' || w.quantity, '~')
                             FROM mercenary_reward w WHERE w.mission_id = e.id),'') AS rewards,
                   ((EXISTS(SELECT 1 FROM mercenary_dispatch_requirement r
                            WHERE r.mission_id = e.id AND r.kind = 'weapon' AND r.value = ?)) +
                    (EXISTS(SELECT 1 FROM mercenary_dispatch_requirement r
                            WHERE r.mission_id = e.id AND r.kind = 'element' AND r.value = ?))) AS score
            FROM entity e
            WHERE e.id IN (SELECT mission_id FROM mercenary_dispatch_requirement)
        )
        WHERE score > 0
        ORDER BY score DESC, id
        LIMIT ?
        """.trimIndent(),
        listOf(weapon, element, limit),
    ) { c ->
        MercenaryMissionRow(
            id = c.lng("id"),
            title = c.str("title"),
            conditions = c.str("reqs").split('~').filter { it.isNotBlank() },
            rewards = c.str("rewards").split('~').filter { it.isNotBlank() },
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

    // ---------------- 采集候选（含站点 Gmk 坐标） ----------------

    private var wikiCollectionTable: Boolean? = null
    private var areaMapCache: Map<String, MapInfo>? = null

    /** 站点采集点表是本地导入的；没有导入时退化成只用 FrontierNav 点位。 */
    private fun hasWikiCollectionTable(): Boolean {
        wikiCollectionTable?.let { return it }
        val exists = Db.count(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'wiki_collection_point'",
        ) > 0
        wikiCollectionTable = exists
        return exists
    }

    /** `ma07a_b_floor_B01` / `ma07a_b_floor_b01` / `_shipoff` 都归一到同一个 key。 */
    private fun normalizeArea(area: String): String =
        area.trim().lowercase().removeSuffix("_shipoff").removeSuffix("_lodoff")

    private fun areaMaps(): Map<String, MapInfo> {
        areaMapCache?.let { return it }
        val out = HashMap<String, MapInfo>()
        for (m in maps()) {
            out.putIfAbsent(normalizeArea(m.gameName), m)
        }
        areaMapCache = out
        return out
    }

    /**
     * 掉这些物品的全部采集点，带上每个点的所有落点。
     *
     * 这里不替调用方挑点：同一个采集点可能出现在多层图上，选哪层要结合步骤地区、
     * 地名和上一步所在图来打分。`collection_marker` 只覆盖 1029/1532 个点，
     * 其余点靠 `wiki_collection_point`（站点 Gmk）参与打分，但没有 frontier 坐标时不能画。
     */
    fun collectCandidates(itemNames: List<String>): List<CollectCandidate> {
        val names = itemNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.isEmpty()) return emptyList()
        val hasWiki = hasWikiCollectionTable()
        val wikiCols = if (hasWiki) {
            ", w.area AS wiki_area, w.px AS wiki_px, w.py AS wiki_py, w.inside AS wiki_inside"
        } else {
            ", NULL AS wiki_area, NULL AS wiki_px, NULL AS wiki_py, NULL AS wiki_inside"
        }
        val wikiJoin = if (hasWiki) {
            " LEFT JOIN wiki_collection_point w ON w.collection_code = cp.collection_code"
        } else {
            ""
        }
        val placeholders = names.joinToString(",") { "?" }
        val rows = Db.list(
            """
            SELECT cp.entity_id AS point_id, cp.collection_code AS collection_code,
                   cp.region AS point_region, cp.field_skill AS field_skill,
                   d.item_name AS item_name, d.probability AS probability,
                   cm.marker_id AS marker_id, cm.x AS marker_x, cm.z AS marker_z,
                   m.id AS marker_map_id, m.name AS marker_map_name, m.display_name AS marker_region,
                   f.target_name AS marker_name
                   $wikiCols
            FROM collection_drop d
            JOIN collection_point cp ON cp.entity_id = d.collection_point_id
            LEFT JOIN collection_marker cm ON cm.collection_point_id = cp.entity_id
            LEFT JOIN frontiernav_map_marker f ON f.feature_id = cm.marker_id
            LEFT JOIN map m ON m.name = cm.map_name
            $wikiJoin
            WHERE d.item_name IN ($placeholders)
            ORDER BY cp.entity_id
            """.trimIndent(),
            names,
        ) { c ->
            RawCollectRow(
                pointId = c.lng("point_id"),
                code = c.str("collection_code"),
                region = c.strOrNull("point_region"),
                fieldSkill = c.strOrNull("field_skill"),
                itemName = c.str("item_name"),
                probability = c.dblOrNull("probability") ?: 0.0,
                markerId = c.strOrNull("marker_id"),
                markerX = c.dblOrNull("marker_x"),
                markerZ = c.dblOrNull("marker_z"),
                markerMapId = c.strOrNull("marker_map_id"),
                markerMapName = c.strOrNull("marker_map_name") ?: "",
                markerRegion = c.strOrNull("marker_region"),
                markerName = c.strOrNull("marker_name") ?: "",
                wikiArea = c.strOrNull("wiki_area"),
                wikiPx = c.dblOrNull("wiki_px"),
                wikiPy = c.dblOrNull("wiki_py"),
                wikiInside = c.intOr("wiki_inside", 1) == 1,
            )
        }

        val byPoint = LinkedHashMap<Long, MutableCollectCandidate>()
        for (r in rows) {
            val acc = byPoint.getOrPut(r.pointId) {
                MutableCollectCandidate(
                    pointId = r.pointId,
                    code = r.code,
                    region = r.region,
                    fieldSkill = r.fieldSkill,
                )
            }
            acc.items.add(r.itemName)
            acc.probability = maxOf(acc.probability, r.probability)
            if (!r.markerId.isNullOrBlank() && r.markerX != null && r.markerZ != null &&
                !r.markerMapId.isNullOrBlank()
            ) {
                acc.frontier[r.markerMapId] = CollectLocation(
                    mapId = r.markerMapId,
                    mapGameName = r.markerMapName,
                    region = r.markerRegion,
                    fx = r.markerX,
                    fy = r.markerZ,
                    featureId = r.markerId,
                )
            }
            val area = r.wikiArea
            if (!area.isNullOrBlank()) {
                val map = areaMaps()[normalizeArea(area)]
                acc.wiki.putIfAbsent(
                    area,
                    CollectLocation(
                        mapId = map?.id,
                        mapGameName = map?.gameName ?: area,
                        region = map?.region ?: r.region,
                        wikiArea = area,
                        px = r.wikiPx,
                        py = r.wikiPy,
                        inside = r.wikiInside,
                    ),
                )
            }
        }

        return byPoint.values.map { acc ->
            val locations = ArrayList<CollectLocation>()
            // 有 frontier 坐标时优先保留可画的点；同一张图的站点像素坐标顺手挂在上面。
            for ((mapId, loc) in acc.frontier) {
                val wiki = acc.wiki.values.firstOrNull { it.mapId == mapId }
                    ?: acc.wiki.values.firstOrNull { it.wikiArea == null }
                locations.add(
                    loc.copy(
                        wikiArea = wiki?.wikiArea,
                        px = wiki?.px,
                        py = wiki?.py,
                        inside = wiki?.inside ?: true,
                    ),
                )
            }
            val covered = locations.mapNotNull { it.mapId }.toSet()
            for (wiki in acc.wiki.values) {
                if (wiki.mapId != null && wiki.mapId in covered) continue
                locations.add(wiki)
            }
            CollectCandidate(
                collectionPointId = acc.pointId,
                collectionCode = acc.code,
                itemNames = acc.items.toList(),
                probability = acc.probability,
                region = acc.region,
                fieldSkill = acc.fieldSkill,
                locations = locations,
            )
        }
    }

    /** 地图上的 CollectionPoint marker → `colle_...` 采集点编号。 */
    fun collectionCodeOfFeature(featureId: String?): String? {
        if (featureId.isNullOrBlank()) return null
        return Db.first(
            """
            SELECT cp.collection_code
            FROM collection_marker cm
            JOIN collection_point cp ON cp.entity_id = cm.collection_point_id
            WHERE cm.marker_id = ?
            LIMIT 1
            """.trimIndent(),
            listOf(featureId),
        ) { c -> c.str("collection_code") }
    }

    private data class RawCollectRow(
        val pointId: Long,
        val code: String,
        val region: String?,
        val fieldSkill: String?,
        val itemName: String,
        val probability: Double,
        val markerId: String?,
        val markerX: Double?,
        val markerZ: Double?,
        val markerMapId: String?,
        val markerMapName: String,
        val markerRegion: String?,
        val markerName: String,
        val wikiArea: String?,
        val wikiPx: Double?,
        val wikiPy: Double?,
        val wikiInside: Boolean,
    )

    private data class MutableCollectCandidate(
        val pointId: Long,
        val code: String,
        val region: String?,
        val fieldSkill: String?,
        val items: LinkedHashSet<String> = LinkedHashSet(),
        var probability: Double = 0.0,
        val frontier: LinkedHashMap<String, CollectLocation> = LinkedHashMap(),
        val wiki: LinkedHashMap<String, CollectLocation> = LinkedHashMap(),
    )

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

    // ---------------- 通用档案页（物品 / 武器 / 地点 / 商店 / 宝箱 / 天气 …） ----------------

    fun entityCountOf(type: String): Int =
        Db.count("SELECT COUNT(*) FROM entity WHERE type = ?", listOf(type))

    fun entityRows(type: String, keyword: String = "", limit: Int = 600): List<EntityRow> {
        val kw = keyword.trim()
        val filter = if (kw.isBlank()) "" else " AND (e.title LIKE ? OR e.name_zh LIKE ?)"
        val args = ArrayList<Any?>()
        args.add(type)
        if (kw.isNotBlank()) {
            args.add("%$kw%")
            args.add("%$kw%")
        }
        args.add(limit)
        return Db.list(
            """
            SELECT e.id, COALESCE(NULLIF(e.name_zh,''), e.title) AS title, e.fields_json,
                   COALESCE(i.category,'') AS item_category, COALESCE(i.item_type,'') AS item_type
            FROM entity e
            LEFT JOIN item i ON i.entity_id = e.id
            WHERE e.type = ?$filter
            ORDER BY title COLLATE NOCASE
            LIMIT ?
            """.trimIndent(),
            args,
        ) { c ->
            EntityRow(
                id = c.lng("id"),
                title = c.str("title"),
                subtitle = subtitleOf(type, c.str("fields_json")),
                iconPath = if (type == "item") ItemIcons.path(c.str("item_category"), c.str("item_type")) else null,
            )
        }
    }

    fun entityDetail(type: String, id: Long): EntityDetailData? = Db.first(
        """
        SELECT id, type, title, COALESCE(name_zh,'') AS name_zh, COALESCE(name_en,'') AS name_en,
               fields_json
        FROM entity WHERE id = ?
        """.trimIndent(),
        listOf(id),
    ) { c ->
        EntityDetailData(
            id = c.lng("id"),
            type = c.str("type"),
            title = c.str("title"),
            nameZh = c.str("name_zh"),
            nameEn = c.str("name_en"),
            fields = parseFields(c.str("fields_json")),
        )
    }

    /** fields_json → 有序键值对（Android 的 JSONObject 用 LinkedHashMap，保持原始顺序）。 */
    fun parseFields(json: String): List<Pair<String, String>> = runCatching {
        if (json.isBlank()) return@runCatching emptyList()
        val o = org.json.JSONObject(json)
        o.keys().asSequence().mapNotNull { key ->
            val value = o.opt(key) ?: return@mapNotNull null
            val text = value.toString().trim()
            if (text.isEmpty() || text == "null") null else key to text
        }.toList()
    }.getOrDefault(emptyList())

    /** 列表页副标题：每类实体挑两个最有信息量的字段。 */
    private val subtitleKeys: Map<String, List<String>> = mapOf(
        "item" to listOf("type", "source"),
        "weapon" to listOf("内部名"),
        "location" to listOf("地图", "类别"),
        "shop" to listOf("位置", "店主"),
        "heart_to_heart" to listOf("location", "触发方式"),
        "poppiswap_part" to listOf("武技名称", "Fandom效果原文"),
        "challenge_battle" to listOf("地点", "等级限制"),
        "weather" to listOf("nameEn"),
        "driver" to listOf("种族", "性别"),
        "merc_mission" to listOf("地域", "所需时间"),
        "collection_point" to listOf("fieldSkill", "time"),
        "treasure_chest" to listOf("appearanceCondition"),
        "salvage_point" to listOf("appearanceCondition"),
    )

    private fun subtitleOf(type: String, json: String): String {
        val keys = subtitleKeys[type] ?: return ""
        val fields = parseFields(json).toMap()
        return keys.mapNotNull { k -> fields[k]?.takeIf { it.isNotBlank() } }
            .take(2)
            .joinToString(" · ")
            .take(90)
    }

    /** 场景技能：同名技能合并，记录最高等级与提供它的异刃。 */
    fun fieldSkills(): List<FieldSkillRow> = Db.list(
        """
        SELECT s.name AS name, MAX(CAST(COALESCE(NULLIF(s.max_level,''),'0') AS INTEGER)) AS lv,
               GROUP_CONCAT(DISTINCT COALESCE(NULLIF(e.name_zh,''), e.title)) AS blades
        FROM blade_skill s
        JOIN blade b ON b.entity_id = s.blade_id
        JOIN entity e ON e.id = b.entity_id
        WHERE s.category = 'field_skill'
        GROUP BY s.name
        ORDER BY s.name
        """.trimIndent(),
    ) { c ->
        FieldSkillRow(c.str("name"), c.int("lv"), c.str("blades"))
    }

    // ---------------- 怪物 ----------------

    /** 怪物列表：等级取 enemy_stats 的 MIN/MAX，地区/分类取第一条统计。 */
    fun enemyRows(keyword: String = "", limit: Int = 800): List<EnemyRow> {
        val kw = keyword.trim()
        val filter = if (kw.isBlank()) "" else " AND (e.title LIKE ? OR e.name_zh LIKE ?)"
        val args = ArrayList<Any?>()
        if (kw.isNotBlank()) {
            args.add("%$kw%")
            args.add("%$kw%")
        }
        args.add(limit)
        return Db.list(
            """
            SELECT e.id AS id, COALESCE(NULLIF(e.name_zh,''), e.title) AS name,
                   COALESCE(MIN(s.level),0) AS lv_min, COALESCE(MAX(s.level),0) AS lv_max,
                   COALESCE(MAX(s.region),'') AS region, COALESCE(MAX(s.category),'') AS category,
                   en.is_torna AS is_torna
            FROM entity e
            JOIN enemy en ON en.entity_id = e.id
            LEFT JOIN enemy_stats s ON s.enemy_id = e.id
            WHERE e.type = 'enemy'$filter
            GROUP BY e.id
            ORDER BY name COLLATE NOCASE
            LIMIT ?
            """.trimIndent(),
            args,
        ) { c ->
            EnemyRow(
                id = c.lng("id"),
                name = c.str("name"),
                levelMin = c.int("lv_min"),
                levelMax = c.int("lv_max"),
                region = c.str("region"),
                category = c.str("category"),
                isTorna = c.int("is_torna") != 0,
            )
        }
    }

    fun enemyDetail(id: Long): EnemyDetailData? {
        val base = Db.first(
            """
            SELECT e.id AS id, COALESCE(NULLIF(e.name_zh,''), e.title) AS name,
                   COALESCE(e.name_en,'') AS name_en, COALESCE(en.enemy_code,'') AS enemy_code,
                   en.is_torna AS is_torna, COALESCE(e.url,'') AS url
            FROM entity e JOIN enemy en ON en.entity_id = e.id
            WHERE e.id = ?
            """.trimIndent(),
            listOf(id),
        ) { c ->
            EnemyDetailData(
                id = c.lng("id"),
                name = c.str("name"),
                nameEn = c.str("name_en"),
                enemyCode = c.str("enemy_code"),
                isTorna = c.int("is_torna") != 0,
                url = c.str("url"),
                stats = emptyList(),
                arts = emptyList(),
                drops = emptyList(),
                spawns = emptyList(),
            )
        } ?: return null

        val stats = Db.list(
            """
            SELECT position, category, COALESCE(level,0) AS level, COALESCE(region,'') AS region,
                   COALESCE(hp,0) AS hp, COALESCE(exp,0) AS exp, COALESCE(wp,0) AS wp, COALESCE(sp,0) AS sp
            FROM enemy_stats WHERE enemy_id = ? ORDER BY position
            """.trimIndent(),
            listOf(id),
        ) { c ->
            EnemyStatRow(
                position = c.int("position"),
                category = c.str("category"),
                level = c.int("level"),
                region = c.str("region"),
                hp = c.lng("hp"),
                exp = c.int("exp"),
                wp = c.int("wp"),
                sp = c.int("sp"),
            )
        }

        val arts = Db.list(
            """
            SELECT position, name, COALESCE(type,'') AS type, COALESCE(target,'') AS target,
                   COALESCE(damage,0) AS damage, COALESCE(attribute,'') AS attribute,
                   COALESCE(cooldown,0) AS cooldown
            FROM enemy_art WHERE enemy_id = ? ORDER BY enemy_position, position
            """.trimIndent(),
            listOf(id),
        ) { c ->
            EnemyArtRow(
                position = c.int("position"),
                name = c.str("name"),
                type = c.str("type"),
                target = c.str("target"),
                damage = c.int("damage"),
                attribute = c.str("attribute"),
                cooldown = c.dbl("cooldown"),
            )
        }

        val drops = Db.list(
            """
            SELECT enemy_position AS position, item_title, probability, first_named
            FROM enemy_drop WHERE enemy_id = ?
            ORDER BY enemy_position, group_no, position
            """.trimIndent(),
            listOf(id),
        ) { c ->
            EnemyDropRow(
                position = c.int("position"),
                itemTitle = c.str("item_title"),
                probability = c.dbl("probability"),
                firstNamed = c.int("first_named") != 0,
            )
        }

        val spawns = Db.list(
            """
            SELECT DISTINCT f.map_id AS map_id, f.map_name AS map_name,
                   COALESCE(f.world_x, sp.x) AS x, COALESCE(f.world_z, sp.z) AS z,
                   COALESCE(f.target_name,'') AS area, sp.spawn_id AS spawn_id
            FROM enemy_spawn sp
            JOIN enemy en ON en.enemy_code = sp.enemy_id
            LEFT JOIN frontiernav_map_marker f ON f.target_id = sp.spawn_id
            WHERE en.entity_id = ?
            GROUP BY sp.spawn_id
            ORDER BY f.map_name
            """.trimIndent(),
            listOf(id),
        ) { c ->
            EnemySpawnArea(
                mapId = c.str("map_id"),
                mapName = c.str("map_name"),
                areaName = c.str("area"),
                x = c.dbl("x"),
                y = 0.0,
                z = c.dbl("z"),
            )
        }.filter { it.mapId.isNotBlank() }

        return base.copy(stats = stats, arts = arts, drops = drops, spawns = spawns)
    }
}

data class EntityRow(
    val id: Long,
    val title: String,
    val subtitle: String,
    val iconPath: String? = null,
)

data class EntityDetailData(
    val id: Long,
    val type: String,
    val title: String,
    val nameZh: String,
    val nameEn: String,
    val fields: List<Pair<String, String>>,
)

data class FieldSkillRow(val name: String, val maxLevel: Int, val blades: String)
