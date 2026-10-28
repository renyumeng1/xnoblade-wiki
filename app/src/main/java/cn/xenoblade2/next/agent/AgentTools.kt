package cn.xenoblade2.next.agent

import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner
import cn.xenoblade2.next.plan.RouteGuide
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent 能碰到的全部能力。
 *
 * 故意不提供「任意 SQL」入口：模型只能调用这里列出的受限函数，
 * 每个函数都是只读查询或对本机计划表的受限写入。
 */
class AgentTools(private val onPlanChanged: () -> Unit) {

    companion object {
        private const val MAX_TEXT = 6000

        /** legs 最多回这么多段，再多对模型和玩家都是噪声；超出只报总数。 */
        private const val MAX_LEGS = 16
    }

    fun specs(): JSONArray {
        val arr = JSONArray()
        fun fn(name: String, desc: String, props: JSONObject, required: List<String>) {
            arr.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", name)
                            put("description", desc)
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put("properties", props)
                                    put("required", JSONArray(required))
                                    put("additionalProperties", false)
                                },
                            )
                        },
                    )
                },
            )
        }

        val s = { d: String -> JSONObject().put("type", "string").put("description", d) }
        val i = { d: String -> JSONObject().put("type", "integer").put("description", d) }

        fn(
            "list_quests",
            "按分类/关键字查任务列表。返回任务 id、标题、分类、章节、步骤数。",
            JSONObject().put("keyword", s("标题关键字，可空")).put("category", s("分类，如 普通任务/主线任务，可空"))
                .put("limit", i("最多返回条数，默认 30")),
            listOf(),
        )
        fn(
            "get_quest",
            "取任务详情：地点、描述、全部官方步骤（含每步是否已能定位到地图点位）。quest_id 与 title 至少给一个。",
            JSONObject().put("quest_id", i("任务 id")).put("title", s("任务名，模糊匹配")),
            listOf(),
        )
        fn(
            "create_quest_plan",
            "把某个任务的全部步骤生成为一份地图可用的执行计划（会自动定位地标/敌人刷新点）。返回 plan_id。",
            JSONObject().put("quest_id", i("任务 id")).put("title", s("任务名，模糊匹配")).put("plan_title", s("自定义计划名，可空")),
            listOf(),
        )
        fn(
            "list_blades",
            "列出异刃（名称/属性/定位/开环节点数）。",
            JSONObject().put("keyword", s("名称关键字，可空")),
            listOf(),
        )
        fn(
            "get_blade",
            "取异刃开环详情：每个节点每级的效果与需求原文（含进度）。",
            JSONObject().put("name", s("异刃名，模糊匹配")),
            listOf(),
        )
        fn(
            "create_blade_plan",
            "把异刃未完成的环等级需求生成为计划步骤（需求里的地名/敌人会自动定位）。返回 plan_id。",
            JSONObject().put("name", s("异刃名")).put("plan_title", s("自定义计划名，可空")),
            listOf(),
        )
        fn(
            "find_place",
            "按地名/区域名找位置。name 只放单个地名（如 穿岩之门），地区放 region（如 古拉）。" +
                "返回 places（有点位坐标）与 maps（区域/地图级，无坐标）；maps 命中说明这不是单一点位。",
            JSONObject().put("name", s("单个地名或区域名")).put("region", s("限定地区，可空")),
            listOf("name"),
        )
        fn(
            "find_enemy",
            "按敌人名找刷新点，返回 map_id / 坐标。",
            JSONObject().put("name", s("敌人名，如 甲狼兽")),
            listOf("name"),
        )
        fn(
            "list_maps",
            "列出可交互地图（id、地区、区域、点位数量）。",
            JSONObject().put("region", s("地区关键字，可空")),
            listOf(),
        )
        fn(
            "map_markers",
            "列出某张地图上的点位。target_type 可取 Location/Quest/EnemySpawnPoint/CollectionPoint/TreasurePoint 等。",
            JSONObject().put("map_id", s("地图 id，来自 list_maps")).put("target_type", s("类型过滤，可空")).put("limit", i("默认 40")),
            listOf("map_id"),
        )
        fn(
            "collect_spots",
            "查某个采集物品在哪些地图点位能采到（物品 → collection_drop → 采集点 → 地图坐标）。返回 map_id 与 frontier 坐标。",
            JSONObject()
                .put("item", s("物品名，需与库中名称一致或为其子串"))
                .put("region", s("地区名过滤，可空"))
                .put("limit", i("最多返回点数，默认 8")),
            listOf("item"),
        )
        fn(
            "route_between",
            "在同一张地图内估算两点间路线：按点位图跑最短路，返回途经地标、距离、绕行比，" +
                "以及逐段指引 legs（每段从哪到哪、转向、方位、距离）。" +
                "注意库里没有真实路网，这是几何近似，只能当方向参考；回答时必须说明这一点。",
            JSONObject()
                .put("map_id", s("地图 id，来自 find_place / map_markers"))
                .put("from", s("起点地名或点位名，模糊匹配"))
                .put("to", s("终点地名或点位名，模糊匹配")),
            listOf("map_id", "from", "to"),
        )
        fn(
            "list_plans",
            "列出本机已保存的计划及进度。",
            JSONObject(),
            listOf(),
        )
        fn(
            "get_plan",
            "取计划的所有步骤（含 step_id、是否完成、是否已定位到地图）。",
            JSONObject().put("plan_id", i("计划 id")),
            listOf("plan_id"),
        )
        fn(
            "add_plan_step",
            "往计划追加一步。x/y 用 find_place 或 map_markers 返回的 frontier 坐标；没有坐标就只给标题。",
            JSONObject()
                .put("plan_id", i("计划 id"))
                .put("title", s("步骤标题"))
                .put("detail", s("步骤说明，可空"))
                .put("map_id", s("地图 id，可空"))
                .put("x", JSONObject().put("type", "number").put("description", "frontier x"))
                .put("y", JSONObject().put("type", "number").put("description", "frontier y")),
            listOf("plan_id", "title"),
        )
        fn(
            "set_step_done",
            "把某个计划步骤标记完成/未完成。",
            JSONObject().put("step_id", i("步骤 id")).put("done", JSONObject().put("type", "boolean")),
            listOf("step_id", "done"),
        )
        fn(
            "search_wiki",
            "全库搜索实体（任务/异刃/敌人/物品/地点）。",
            JSONObject().put("query", s("关键字")),
            listOf("query"),
        )
        return arr
    }

    fun call(name: String, args: JSONObject): String = try {
        val out = when (name) {
            "list_quests" -> listQuests(args)
            "get_quest" -> getQuest(args)
            "create_quest_plan" -> createQuestPlan(args)
            "list_blades" -> listBlades(args)
            "get_blade" -> getBlade(args)
            "create_blade_plan" -> createBladePlan(args)
            "find_place" -> findPlace(args)
            "find_enemy" -> findEnemy(args)
            "list_maps" -> listMaps(args)
            "map_markers" -> mapMarkers(args)
            "collect_spots" -> collectSpots(args)
            "route_between" -> routeBetween(args)
            "list_plans" -> listPlans()
            "get_plan" -> getPlan(args)
            "add_plan_step" -> addPlanStep(args)
            "set_step_done" -> setStepDone(args)
            "search_wiki" -> searchWiki(args)
            else -> JSONObject().put("error", "未知工具 $name").toString()
        }
        if (out.length > MAX_TEXT) {
            // 超长时返回合法 JSON 信封：直接截断会把 JSON 截断成半截，
            // 模型拿不到可解析的结果，只能猜（表现为「后半被截断」）。
            JSONObject()
                .put("truncated", true)
                .put("tool", name)
                .put("note", "结果超过 $MAX_TEXT 字符，只返回前 $MAX_TEXT 字符；需要完整内容请缩小查询范围")
                .put("preview", out.take(MAX_TEXT))
                .toString()
        } else out
    } catch (t: Throwable) {
        JSONObject().put("error", t.message ?: t.javaClass.simpleName).toString()
    }

    // ---------------- 实现 ----------------

    private fun listQuests(a: JSONObject): String {
        val kw = a.optString("keyword", "")
        val cat = a.optString("category", "")
        val limit = a.optInt("limit", 30).coerceIn(1, 100)
        val list = WikiRepo.quests(cat.ifBlank { null }, kw, limit)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("title", it.display).put("category", it.category)
                    .put("npc", it.npc).put("chapter", it.chapter).put("steps", it.stepCount),
            )
        }
        return JSONObject().put("count", arr.length()).put("quests", arr).toString()
    }

    private fun findQuest(a: JSONObject): cn.xenoblade2.next.data.QuestSummary? {
        val id = a.optLong("quest_id", 0L)
        if (id > 0) return WikiRepo.quests(null, "", 5000).firstOrNull { it.id == id }
        val title = a.optString("title", "")
        if (title.isBlank()) return null
        return WikiRepo.quests(null, title, 20).firstOrNull()
            ?: WikiRepo.quests(null, "", 5000).firstOrNull { it.display.contains(title) || it.title.contains(title) }
    }

    private fun getQuest(a: JSONObject): String {
        val q = findQuest(a) ?: return JSONObject().put("error", "没找到任务").toString()
        val d = WikiRepo.questDetail(q.id) ?: return JSONObject().put("error", "任务详情缺失").toString()
        val anchored = Planner.anchorQuest(d)
        val steps = JSONArray()
        anchored.forEach { s ->
            steps.put(
                JSONObject()
                    .put("position", s.step.position)
                    .put("kind", s.step.kind)
                    .put("text", s.step.text)
                    .put("detail", s.step.detail)
                    .put("map_id", s.pin?.mapId ?: JSONObject.NULL)
                    .put("x", s.pin?.frontierX ?: JSONObject.NULL)
                    .put("y", s.pin?.frontierY ?: JSONObject.NULL)
                    .put("located_by", s.how),
            )
        }
        return JSONObject()
            .put("id", d.id).put("title", d.title).put("category", d.category)
            .put("locations", JSONArray(d.locations)).put("description", d.description)
            .put("steps", steps)
            .put("located", anchored.count { it.pin != null })
            .put("total", anchored.size)
            .toString()
    }

    private fun createQuestPlan(a: JSONObject): String {
        val q = findQuest(a) ?: return JSONObject().put("error", "没找到任务").toString()
        val d = WikiRepo.questDetail(q.id) ?: return JSONObject().put("error", "任务详情缺失").toString()
        val steps = Planner.buildQuestPlan(d)
        val title = a.optString("plan_title").ifBlank { "任务 · ${d.title}" }
        val planId = PlanStore.createPlan(title, "quest", d.id.toString())
        PlanStore.addSteps(planId, steps)
        onPlanChanged()
        return JSONObject()
            .put("plan_id", planId).put("title", title)
            .put("steps", steps.size)
            .put("located", steps.count { it.mapId != null })
            .toString()
    }

    private fun listBlades(a: JSONObject): String {
        val list = WikiRepo.blades(a.optString("keyword", ""))
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("element", it.element)
                    .put("role", it.role).put("nodes", it.nodeCount).put("id", it.id),
            )
        }
        return JSONObject().put("count", arr.length()).put("blades", arr).toString()
    }

    private fun getBlade(a: JSONObject): String {
        val name = a.optString("name", "")
        val blade = WikiRepo.blades(name).firstOrNull()
            ?: return JSONObject().put("error", "没找到异刃 $name").toString()
        val nodes = WikiRepo.bladeNodes(blade.id)
        val arr = JSONArray()
        nodes.take(24).forEach { n ->
            val levels = JSONArray()
            n.levels.forEach { lv ->
                val one = JSONObject().put("level", lv.level).put("effect", lv.effect)
                    .put("requirement", lv.requirement)
                val prog = lv.progressText
                if (!prog.isNullOrBlank()) one.put("progress", prog)
                levels.put(one)
            }
            arr.put(JSONObject().put("node", n.name).put("category", n.category).put("levels", levels))
        }
        return JSONObject().put("name", blade.name).put("element", blade.element)
            .put("role", blade.role).put("node_count", nodes.size)
            .put("omitted_nodes", (nodes.size - arr.length()).coerceAtLeast(0))
            .put("nodes", arr).toString()
    }

    private fun createBladePlan(a: JSONObject): String {
        val name = a.optString("name", "")
        val blade = WikiRepo.blades(name).firstOrNull()
            ?: return JSONObject().put("error", "没找到异刃 $name").toString()
        val steps = Planner.buildBladePlan(blade, WikiRepo.bladeNodes(blade.id))
        val title = a.optString("plan_title").ifBlank { "开环 · ${blade.name}" }
        val planId = PlanStore.createPlan(title, "blade", blade.id.toString())
        PlanStore.addSteps(planId, steps)
        onPlanChanged()
        return JSONObject().put("plan_id", planId).put("title", title)
            .put("steps", steps.size).put("located", steps.count { it.mapId != null }).toString()
    }

    /**
     * 地名解析。模型经常把「地区 + 的 + 地名」整串塞进 name（如「古拉的穿岩之门」），
     * 也可能只给区域名（如「里吉特海域」）。所以这里按三步走：
     * 整串匹配 → 拆词后再匹配 → 都不是点位就按地图/区域返回（不带坐标，避免被当成点）。
     */
    private fun findPlace(a: JSONObject): String {
        val raw = a.optString("name", "").trim()
        if (raw.isBlank()) return JSONObject().put("error", "name 不能为空").toString()
        var region = a.optString("region", "").trim().ifBlank { null }

        var hits = cn.xenoblade2.next.plan.Gazetteer.matchPlaces(raw, region, 5)
        if (hits.isEmpty()) {
            val segs = cn.xenoblade2.next.plan.Gazetteer.splitQuery(raw)
            val keys = cn.xenoblade2.next.plan.Gazetteer.regionKeywords()
            if (region == null) {
                region = segs.firstOrNull { s -> keys.any { it == s || it.contains(s) } }
            }
            val merged = LinkedHashMap<String, cn.xenoblade2.next.plan.Gazetteer.Place>()
            // 纯地区/区域名（如「古拉」「里吉特海域」）不当地标匹配，否则会命中一堆采集点
            for (s in segs.sortedByDescending { it.length }) {
                if (keys.any { it == s || it.contains(s) }) continue
                cn.xenoblade2.next.plan.Gazetteer.matchPlaces(s, region, 5)
                    .forEach { merged.putIfAbsent(it.featureId, it) }
                if (merged.size >= 5) break
            }
            hits = merged.values.take(5)
        }

        val arr = JSONArray()
        hits.forEach { p ->
            if (p.mapId.isNullOrBlank()) return@forEach
            val m = WikiRepo.marker(p.featureId) ?: return@forEach
            val map = WikiRepo.map(m.mapId)
            arr.put(
                JSONObject().put("name", p.name).put("kind", "marker").put("type", p.type)
                    .put("map_id", m.mapId)
                    .put("region", map?.region ?: "").put("area", map?.area ?: "")
                    .put("x", m.frontierX).put("y", m.frontierY).put("feature_id", m.featureId),
            )
        }

        val maps = JSONArray()
        if (arr.length() == 0) {
            matchMaps(raw, region).forEach { map ->
                maps.put(
                    JSONObject().put("name", map.second).put("kind", map.first)
                        .put("map_id", map.third.id)
                        .put("region", map.third.region).put("area", map.third.area)
                        .put("markers", map.third.markerCount),
                )
            }
        }

        return JSONObject()
            .put("count", arr.length()).put("places", arr)
            .put("map_count", maps.length()).put("maps", maps)
            .put("matched_region", region ?: "")
            .put(
                "note",
                if (arr.length() == 0 && maps.length() == 0) "库里没有这个地名"
                else if (arr.length() == 0) "这是地图/区域，不是单一点位，没有坐标；需要具体位置请再用 map_markers 或给更具体的地名"
                else "x/y 是 frontier 平面坐标，可直接用于 route_between / add_plan_step",
            )
            .toString()
    }

    /** 地图/区域名匹配：优先 menu_group（如「里吉特海域」），其次 display_name（如「古拉」）。 */
    private fun matchMaps(text: String, regionHint: String?): List<Triple<String, String, cn.xenoblade2.next.data.MapInfo>> {
        val t = text.trim()
        if (t.length < 2) return emptyList()
        val out = LinkedHashMap<String, Triple<String, String, cn.xenoblade2.next.data.MapInfo>>()
        val maps = WikiRepo.maps()
        fun add(kind: String, list: List<cn.xenoblade2.next.data.MapInfo>) {
            list.take(4).forEach { m ->
                val label = when (kind) {
                    "area" -> m.area
                    "region" -> m.region
                    else -> m.gameName
                }
                out.putIfAbsent(m.id, Triple(kind, label, m))
            }
        }
        add("area", maps.filter { it.area == t || (it.area.length >= 2 && t.contains(it.area)) })
        val hint = regionHint?.trim().orEmpty()
        add(
            "region",
            maps.filter {
                it.region == t || (it.region.length >= 2 && t.contains(it.region)) ||
                    (hint.isNotEmpty() && (it.region == hint || it.region.contains(hint)))
            },
        )
        add("map", maps.filter { it.gameName == t || it.id == t })
        return out.values.take(6)
    }

    private fun findEnemy(a: JSONObject): String {
        val name = a.optString("name", "")
        val arr = JSONArray()
        WikiRepo.enemySpawns(name, 20).forEach { e ->
            val m = WikiRepo.marker(e.featureId) ?: return@forEach
            arr.put(
                JSONObject().put("enemy", e.name).put("level", e.level)
                    .put("map_id", m.mapId).put("x", m.frontierX).put("y", m.frontierY),
            )
        }
        return JSONObject().put("count", arr.length()).put("spawns", arr).toString()
    }

    private fun listMaps(a: JSONObject): String {
        val region = a.optString("region", "")
        val arr = JSONArray()
        WikiRepo.maps().filter { region.isBlank() || it.region.contains(region) }.forEach {
            arr.put(
                JSONObject().put("map_id", it.id).put("region", it.region)
                    .put("area", it.area).put("markers", it.markerCount),
            )
        }
        return JSONObject().put("count", arr.length()).put("maps", arr).toString()
    }

    private fun mapMarkers(a: JSONObject): String {
        val mapId = a.optString("map_id", "")
        val type = a.optString("target_type", "")
        val limit = a.optInt("limit", 40).coerceIn(1, 200)
        val list = WikiRepo.markersOf(mapId)
            .filter { type.isBlank() || it.targetType.equals(type, true) }
            .take(limit)
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.targetName).put("type", it.targetType)
                    .put("x", it.frontierX).put("y", it.frontierY).put("feature_id", it.featureId),
            )
        }
        return JSONObject().put("count", arr.length()).put("markers", arr).toString()
    }

    private fun collectSpots(a: JSONObject): String {
        val item = a.optString("item", "")
        val region = a.optString("region", "").ifBlank { null }
        val limit = a.optInt("limit", 8).coerceIn(1, 30)
        val spots = WikiRepo.collectMarkers(item, region, limit)
        val arr = JSONArray()
        spots.forEach { m ->
            val map = WikiRepo.map(m.mapId)
            arr.put(
                JSONObject().put("map_id", m.mapId)
                    .put("region", map?.region ?: "")
                    .put("area", map?.area ?: "")
                    .put("x", m.frontierX).put("y", m.frontierY)
                    .put("feature_id", m.featureId),
            )
        }
        return JSONObject().put("item", item).put("count", arr.length()).put("spots", arr).toString()
    }

    private fun routeBetween(a: JSONObject): String {
        val mapId = a.optString("map_id", "")
        val fromQ = a.optString("from", "")
        val toQ = a.optString("to", "")
        if (mapId.isBlank() || fromQ.isBlank() || toQ.isBlank()) {
            return JSONObject().put("error", "map_id / from / to 都是必填").toString()
        }
        val ms = WikiRepo.markersOf(mapId)
        fun pick(q: String): cn.xenoblade2.next.data.Marker? {
            val exact = ms.firstOrNull { it.targetName == q }
            val contains = ms.firstOrNull { it.targetName.contains(q) }
            val containsRev = ms.firstOrNull { q.contains(it.targetName) && it.targetName.length >= 2 }
            return exact ?: contains ?: containsRev
        }
        val from = pick(fromQ) ?: return JSONObject()
            .put("error", "这张图上找不到起点「$fromQ」")
            .put("hint", ms.take(12).map { it.targetName }).toString()
        val to = pick(toQ) ?: return JSONObject()
            .put("error", "这张图上找不到终点「$toQ」")
            .put("hint", ms.take(12).map { it.targetName }).toString()
        val r = cn.xenoblade2.next.plan.RouteGraph.route(
            mapId,
            from.frontierX,
            from.frontierY,
            to.frontierX,
            to.frontierY,
            from.featureId,
            to.featureId,
        )
        val arr = JSONArray()
        // 只把有名字的地标当"途经点"回给模型；自动命名的敌人/宝箱点只参与画线
        r.points.filter { it.notable }.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("type", it.targetType)
                    .put("x", it.fx).put("y", it.fy),
            )
        }
        // 逐段导航：和地图页同一套切段规则（RouteGuide），模型可以直接按 legs 复述成一步一走的指引
        val legs = RouteGuide.legs(r, from.targetName, to.targetName)
        val legArr = JSONArray()
        legs.take(MAX_LEGS).forEachIndexed { i, leg ->
            legArr.put(
                JSONObject().put("n", i + 1)
                    .put("from", leg.fromName)
                    .put("to", leg.toName)
                    .put("turn", leg.turn)
                    .put("compass", leg.compass)
                    .put("distance", "%.0f".format(leg.distance)),
            )
        }
        val out = JSONObject()
            .put("map_id", mapId)
            .put("from", from.targetName)
            .put("to", to.targetName)
            .put("distance", "%.2f".format(r.distance))
            .put("straight", "%.2f".format(r.straight))
            .put("detour", "%.2f".format(r.detour))
            .put("access_hops", r.accessHops)
            .put("estimated", true)
            .put("note", "点位图几何近似，非真实路网，仅供方向参考")
            .put("waypoints", arr)
            .put("leg_count", legs.size)
            .put("legs", legArr)
        if (legs.size > MAX_LEGS) out.put("omitted_legs", legs.size - MAX_LEGS)
        return out.toString()
    }

    private fun listPlans(): String {
        val arr = JSONArray()
        PlanStore.plans().forEach {
            arr.put(
                JSONObject().put("plan_id", it.id).put("title", it.title)
                    .put("steps", it.stepCount).put("done", it.doneCount),
            )
        }
        return JSONObject().put("count", arr.length()).put("plans", arr).toString()
    }

    private fun getPlan(a: JSONObject): String {
        val planId = a.optLong("plan_id", 0L)
        val arr = JSONArray()
        PlanStore.steps(planId).forEach {
            arr.put(
                JSONObject().put("step_id", it.id).put("ord", it.ord).put("title", it.title)
                    .put("detail", it.detail).put("map_id", it.mapId ?: JSONObject.NULL)
                    .put("done", it.done),
            )
        }
        return JSONObject().put("plan_id", planId).put("steps", arr).toString()
    }

    private fun addPlanStep(a: JSONObject): String {
        val planId = a.optLong("plan_id", 0L)
        val title = a.optString("title", "")
        if (planId <= 0 || title.isBlank()) return JSONObject().put("error", "plan_id/title 必填").toString()
        val id = PlanStore.addStep(
            planId,
            NewStep(
                title = title,
                detail = a.optString("detail", ""),
                mapId = a.optString("map_id").ifBlank { null },
                fx = if (a.has("x")) a.optDouble("x") else null,
                fy = if (a.has("y")) a.optDouble("y") else null,
            ),
        )
        onPlanChanged()
        return JSONObject().put("step_id", id).toString()
    }

    private fun setStepDone(a: JSONObject): String {
        val id = a.optLong("step_id", 0L)
        PlanStore.toggleStep(id, a.optBoolean("done", true))
        onPlanChanged()
        return JSONObject().put("ok", true).toString()
    }

    private fun searchWiki(a: JSONObject): String {
        val q = a.optString("query", "")
        val arr = JSONArray()
        WikiRepo.search(q, 25).forEach {
            arr.put(JSONObject().put("id", it.id).put("type", it.type).put("title", it.title))
        }
        return JSONObject().put("count", arr.length()).put("hits", arr).toString()
    }
}
