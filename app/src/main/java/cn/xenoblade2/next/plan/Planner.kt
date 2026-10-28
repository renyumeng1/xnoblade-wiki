package cn.xenoblade2.next.plan

import cn.xenoblade2.next.data.AffinityNode
import cn.xenoblade2.next.data.BladeSummary
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.QuestDetail
import cn.xenoblade2.next.data.QuestStep
import cn.xenoblade2.next.data.WikiRepo

/**
 * 把「任务步骤 / 开环需求」翻成可落图的计划步骤。
 *
 * 定位优先级：内链 `地点:feature_id` > 同地区最长地名匹配 > NPC 名→出现坐标
 *            > 敌人名→刷新点 > 采集品→采集点 > 同任务主图（只到图、无坐标）。
 * 都定不到就留纯文字步骤，UI 里标成「未定位」，允许用户手动钉点。
 * 实测见 `tools/anchoring_stats.py`；剩下的未命中大头是黄金之国 DLC 任务（库里没有伊拉地图）。
 */
object Planner {

    data class Anchored(
        val step: QuestStep,
        val pin: Marker?,
        val how: String,
        val notes: List<String> = emptyList(),
        /** 只能定到图、定不到点时填地图 id（图级定位），UI 上单独标一档。 */
        val mapOnly: String? = null,
    ) {
        /** 有坐标的精确落点。 */
        val pinned: Boolean get() = pin != null && !pin.mapId.isNullOrBlank()
    }

    fun regionsOf(detail: QuestDetail): List<String> =
        detail.locations.map { it.substringBefore("·").substringBefore("·").trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun markerOf(featureId: String?): Marker? =
        if (featureId.isNullOrBlank()) null else WikiRepo.marker(featureId)

    /** 单步定位。 */
    fun anchor(step: QuestStep, regionHint: String?, regions: List<String>): Anchored {
        // 1) 显式地点内链
        step.links.forEach { link ->
            val m = markerOf(link.placeFeatureId)
            if (m != null && !m.mapId.isNullOrBlank()) {
                return Anchored(step, m, "内链", listOf("地点：${link.label}"))
            }
        }
        // 2) 同地区地名出现在文本里
        for (region in (listOfNotNull(regionHint) + regions).distinct()) {
            val place = Gazetteer.matchPlaces(step.text, region, 1).firstOrNull()
            val m = markerOf(place?.featureId)
            if (m != null && !m.mapId.isNullOrBlank()) {
                return Anchored(step, m, "地名匹配", listOf("地点：${place!!.name}（${m.mapId}）"))
            }
        }
        // 2b) 任务跨区域：quest_location 只记接取地（如「英维迪亚烈王国·冯斯麦姆港」），
        //     但步骤可能跑到别的图（名流帽要去阿伐利缇亚市场）。地区内没命中就全局再找一次。
        val globalPlace = Gazetteer.matchPlaces(step.text, null, 1).firstOrNull()
        val globalMarker = markerOf(globalPlace?.featureId)
        if (globalMarker != null && !globalMarker.mapId.isNullOrBlank()) {
            return Anchored(step, globalMarker, "地名匹配", listOf("地点：${globalPlace!!.name}（${globalMarker.mapId}）"))
        }
        // 2.5) 对话/交付类步骤：NPC 名 → 该 NPC 的出现坐标
        //      `npc_game_appearance` 里只有 base game 的 NPC 带 game_map（黄金之国多为空），
        //      JOIN map 时会被过滤掉，所以不会给出错误坐标。
        if (!step.kind.contains("Battle") && !step.kind.contains("Collect")) {
            val npc = Gazetteer.matchNpc(step.text, regionHint ?: regions.firstOrNull())
            if (npc != null) {
                return Anchored(
                    step,
                    Marker(
                        featureId = "",
                        mapId = npc.mapId,
                        mapGameName = "",
                        frontierX = npc.fx,
                        frontierY = npc.fy,
                        worldX = null,
                        worldZ = null,
                        targetType = "Npc",
                        targetName = npc.name,
                        targetId = "",
                    ),
                    "NPC 位置",
                    listOf("NPC：${npc.name}"),
                )
            }
        }
        // 3) 战斗步骤：敌人名 → 刷新点
        if (step.kind.contains("Battle")) {
            val enemy = Gazetteer.matchEnemy(step.text)
            if (enemy != null) {
                val spawn = WikiRepo.enemySpawns(enemy, 12)
                    .firstOrNull { it.mapId.isNotBlank() }
                    ?.let { markerOf(it.featureId) }
                if (spawn != null) {
                    return Anchored(step, spawn, "敌人刷新点", listOf("敌人：$enemy"))
                }
                return Anchored(step, null, "未定位", listOf("敌人：$enemy（无坐标）"))
            }
        }
        // 4) 采集步骤：物品 → 采集点（collection_drop ⋈ collection_marker ⋈ marker）
        if (step.kind.contains("Collect")) {
            val hit = collectTarget(step.text, regionHint ?: regions.firstOrNull())
            if (hit != null) {
                return Anchored(
                    step,
                    hit.first,
                    "采集点",
                    listOf("采集品：${hit.second}", "采集点：${hit.first.targetName}"),
                )
            }
            Gazetteer.matchCollection(step.text)?.let {
                return Anchored(step, null, "未定位", listOf("采集品：$it"))
            }
        }
        return Anchored(step, null, "未定位")
    }

    /** 采集品 → 采集点坐标；优先同地区，找不到再全局找。 */
    private fun collectTarget(text: String, regionHint: String?): Pair<Marker, String>? {
        val item = Gazetteer.matchCollectItem(text) ?: return null
        val spots = WikiRepo.collectMarkers(item, regionHint, 8)
            .ifEmpty { WikiRepo.collectMarkers(item, null, 8) }
        val m = spots.firstOrNull() ?: return null
        return m to item
    }

    fun anchorQuest(detail: QuestDetail): List<Anchored> {
        val regions = regionsOf(detail)
        val hint = regions.firstOrNull()
        val raw = detail.steps.map { anchor(it, hint, regions) }
        // 图级兜底：同任务里多数已定位步骤落在哪张图，剩下没坐标的步骤至少指到那张图。
        // 只给地图 id、不给坐标，UI 标成「图级定位」，用户长按地图钉点就能补坐标。
        val mainMap = raw.mapNotNull { it.pin?.mapId?.takeIf { m -> m.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return raw
        return raw.map { a ->
            if (a.pin != null) a
            else a.copy(
                mapOnly = mainMap,
                notes = a.notes + "所在图：${WikiRepo.map(mainMap)?.let { "${it.region}·${it.area}" } ?: mainMap}（估算，坐标待确认）",
            )
        }
    }

    fun buildQuestPlan(detail: QuestDetail): List<NewStep> =
        anchorQuest(detail).map { a ->
            val m = a.pin
            NewStep(
                title = "第${a.step.position}步 · ${a.step.text}",
                detail = buildString {
                    if (a.step.detail.isNotBlank()) append(a.step.detail)
                    if (a.notes.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append(a.notes.joinToString("；"))
                    }
                    if (m != null && !m.mapId.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n")
                        append("地图：${m.mapId} · ${m.targetName}")
                    }
                    if (a.step.links.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append("关联：")
                        append(a.step.links.joinToString("、") { "${it.kind}:${it.label}" })
                    }
                },
                mapId = m?.mapId?.takeIf { it.isNotBlank() } ?: a.mapOnly,
                fx = m?.frontierX,
                fy = m?.frontierY,
                featureId = m?.featureId,
            )
        }

    /**
     * 开环计划：只列还没习得的等级需求，按「先易后难 + 同图聚集」排。
     *
     * 定位顺序：地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（含「XX族」族名）> 采集品。
     * 只提到地区、定不到具体点的需求，退到该地区主图（图级定位，UI 标「仅到图」）。
     * 纯战斗/操作类需求没有地点可落，补一条「怎么刷」提示，避免计划只剩一句计数。
     */
    fun buildBladePlan(blade: BladeSummary, nodes: List<AffinityNode>, skipLearned: Boolean = true): List<NewStep> {
        val out = ArrayList<NewStep>()
        for (node in nodes) {
            for (lv in node.levels) {
                if (skipLearned && lv.learned) continue
                if (lv.requirement.isBlank()) continue
                val req = lv.requirement
                val regionHint = Gazetteer.matchRegion(req)
                val linkPlace = lv.links.firstNotNullOfOrNull { markerOf(it.placeFeatureId) }
                    ?.takeIf { !it.mapId.isNullOrBlank() }
                val placeMatch = if (linkPlace == null) {
                    (Gazetteer.matchPlaces(req, regionHint, 1).firstOrNull()
                        ?: Gazetteer.matchPlaces(req, null, 1).firstOrNull())
                        ?.let { markerOf(it.featureId) }
                        ?.takeIf { !it.mapId.isNullOrBlank() }
                } else null
                val battleLike = req.contains("打倒") || req.contains("讨伐") ||
                    req.contains("击败") || req.contains("战斗")
                val npcPin = if (linkPlace == null && placeMatch == null && !battleLike) {
                    Gazetteer.matchNpc(req, regionHint)?.let { npc ->
                        Marker(
                            featureId = "",
                            mapId = npc.mapId,
                            mapGameName = "",
                            frontierX = npc.fx,
                            frontierY = npc.fy,
                            worldX = null,
                            worldZ = null,
                            targetType = "Npc",
                            targetName = npc.name,
                            targetId = "",
                        )
                    }
                } else null
                val enemyName = if (linkPlace == null && placeMatch == null && npcPin == null) {
                    Gazetteer.matchEnemy(req) ?: Gazetteer.matchEnemyFamily(req)
                } else null
                // 需求里写了地区就只认同地区的刷新点：宁可图级落点，也不把人指到别的大陆去。
                // 只在别处刷的，记下来在 detail 里说明，不让用户以为数据坏了。
                var enemyElsewhere: String? = null
                val enemyPin = enemyName?.let { name ->
                    val spawns = WikiRepo.enemySpawns(name, 40)
                    val same = spawns.firstOrNull {
                        regionHint == null || WikiRepo.map(it.mapId)?.region == regionHint
                    }
                    if (same == null && regionHint != null) {
                        enemyElsewhere = spawns.firstOrNull()?.let { s -> WikiRepo.map(s.mapId)?.region }
                    }
                    same?.let { e -> markerOf(e.featureId) }
                }
                val collectPin = if (linkPlace == null && placeMatch == null && npcPin == null && enemyPin == null) {
                    collectTarget(req, regionHint)?.first
                } else null
                val pin = linkPlace ?: placeMatch ?: npcPin ?: enemyPin ?: collectPin
                val mapOnly = if (pin == null) regionHint?.let { regionMainMap(it) } else null
                out.add(
                    NewStep(
                        title = "${node.name} Lv${lv.level} · $req",
                        detail = buildString {
                            append("效果：${lv.effect}")
                            if (lv.links.isNotEmpty()) {
                                append("\n相关：")
                                append(lv.links.joinToString("、") { "${it.kind}:${it.label}" })
                            }
                            if (pin != null) {
                                // 刷新点的 target_name 是「Enemy Spawn Point #5058」这种内部名，换成敌人名更好读
                                val label =
                                    if (enemyName != null && pin === enemyPin) "${enemyName}的刷新点"
                                    else pin.targetName
                                append("\n地图：${pin.mapId} · $label")
                            } else if (mapOnly != null) {
                                append("\n地图：$mapOnly · $regionHint（只能到图，具体点位待确认）")
                            }
                            if (enemyElsewhere != null) {
                                append("\n提示：这个敌人的刷新点实际在 $enemyElsewhere，与需求地区（$regionHint）不符，没直接钉点")
                            }
                            farmTip(req)?.let { append("\n怎么刷：$it") }
                            append("\n进度：${lv.progressText ?: "0/${lv.achievementCount.coerceAtLeast(1)}"}")
                        },
                        mapId = pin?.mapId?.takeIf { it.isNotBlank() } ?: mapOnly,
                        fx = pin?.frontierX,
                        fy = pin?.frontierY,
                        featureId = pin?.featureId,
                    ),
                )
            }
        }
        return clusterByMap(out)
    }

    /** 地区主图：点位最多的那张，用于「只知道地区」时的图级落点。 */
    private fun regionMainMap(region: String): String? =
        WikiRepo.maps().filter { it.region == region }.maxByOrNull { it.markerCount }?.id

    /** 「怎么刷」提示表。长串优先匹配，避免「完成御刃者连击」被「发动御刃者连击」抢走。 */
    private val farmTips: List<Pair<String, String>> = listOf(
        "使用异刃连击" to "打血厚的敌人，攒满 3 段属性连携再收尾；一场可计 1~2 次",
        "完成异刃连击" to "连携最后一段由这把异刃收尾才算「完成」",
        "使用异刃武技" to "战斗中武技冷却好了就放，优先短 CD 的",
        "发动御刃者连击" to "破防→倒地→浮空→猛击，任一段都算；带破防异刃起手最快",
        "完成御刃者连击" to "连击最后一段（猛击）由该角色收尾",
        "发动连锁攻击" to "队伍槽满后发动连锁，收尾选这把异刃",
        "发动暴击" to "堆暴击率或打低级怪，换高暴击武器",
        "回避攻击" to "对低级怪站着吃招，或带回避提升饰品",
        "格挡攻击" to "装备盾类异刃 / 格挡提升饰品，正面接招",
        "提升信赖度" to "做任务、送收纳包、战斗都会涨；这是多数环的前置门槛",
        "使用收纳包道具" to "买对应类别的食物/饮料塞满收纳包，buff 没了再补",
        "行走" to "传送后在城镇里绕圈跑，挂机也会累积",
        "收集道具" to "采集点会刷新，同一片采集点来回跑最省时间",
        "发现地点" to "地图上还没点亮的地标/秘境，沿边界走一圈",
        "制作料理" to "在料理店或营地做，材料够就批量刷",
        "战斗中切换异刃" to "带 2~3 把异刃轮流切，冷却好了就切",
        "救助无法战斗的同伴" to "让队友倒下后靠近，按救助键",
        "完成按键挑战" to "战斗 QTE 出现时按对提示",
        "造成累计伤害" to "找高血量 Boss 慢慢磨，伤害会全额累计",
        "造成单次伤害" to "堆一次爆发（连携收尾/必杀），单次数字要过线",
        "完成佣兵团任务" to "派佣兵团接任务再回来收，耗时长但能并行",
        "完成挑战战斗" to "挑战之地（DLC）里的战斗",
        "查看牵绊对话" to "到对应地标触发，队伍里要带齐参与的人",
        "收集回复瓶" to "打怪掉的回复瓶捡起来，带回复瓶提升更省事",
        "击败敌人" to "任意敌人都算，挑刷新快的低级怪",
        "打倒" to "去需求写的地区找同名敌人反复刷；刷新点见地图",
    ).sortedByDescending { it.first.length }

    private fun farmTip(req: String): String? = farmTips.firstOrNull { req.contains(it.first) }?.second

    /** 保持地图出现顺序，同图内按最近邻重排，减少来回跑图。 */
    fun clusterByMap(steps: List<NewStep>): List<NewStep> {
        val order = LinkedHashMap<String, MutableList<NewStep>>()
        for (s in steps) {
            val key = s.mapId ?: "\u0000none"
            order.getOrPut(key) { ArrayList() }.add(s)
        }
        val out = ArrayList<NewStep>(steps.size)
        for ((key, group) in order) {
            if (key == "\u0000none" || group.size < 3) {
                out.addAll(group)
                continue
            }
            val remaining = group.toMutableList()
            var cursor = remaining.removeAt(0)
            out.add(cursor)
            while (remaining.isNotEmpty()) {
                val next = remaining.minByOrNull { s ->
                    val dx = (s.fx ?: 0.0) - (cursor.fx ?: 0.0)
                    val dy = (s.fy ?: 0.0) - (cursor.fy ?: 0.0)
                    dx * dx + dy * dy
                } ?: break
                remaining.remove(next)
                out.add(next)
                cursor = next
            }
        }
        return out
    }
}
