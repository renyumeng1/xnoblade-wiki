package cn.xenoblade2.next.plan

import cn.xenoblade2.next.data.AffinityNode
import cn.xenoblade2.next.data.BladeSummary
import cn.xenoblade2.next.data.CollectCandidate
import cn.xenoblade2.next.data.CollectLocation
import cn.xenoblade2.next.data.MapMath
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.QuestDetail
import cn.xenoblade2.next.data.QuestStep
import cn.xenoblade2.next.data.WikiShots
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

    /** 游戏内部地图/图层名（ma07a_b_floor_01 之类），不该出现在用户可见文案里。 */
    private val INTERNAL_NAME = Regex("^ma\\d.*")

    data class Anchored(
        val step: QuestStep,
        val pin: Marker?,
        val how: String,
        val notes: List<String> = emptyList(),
        /** 只能定到图、定不到点时填地图 id（图级定位），UI 上单独标一档。 */
        val mapOnly: String? = null,
        /** 采集点有多个候选时给用户点选；选中的结果由 PlanStore 记住。 */
        val choices: List<PinChoice> = emptyList(),
    ) {
        /** 有坐标的精确落点。 */
        val pinned: Boolean get() = pin != null && !pin.mapId.isNullOrBlank()
    }

    data class PinChoice(
        val label: String,
        val score: Double,
        val mapId: String?,
        val marker: Marker?,
        val itemNames: List<String>,
    ) {
        val selectable: Boolean get() = marker != null && !marker.mapId.isNullOrBlank()
    }

    fun regionsOf(detail: QuestDetail): List<String> =
        detail.locations.map { it.substringBefore("·").substringBefore("·").trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun markerOf(featureId: String?): Marker? =
        if (featureId.isNullOrBlank()) null else WikiRepo.marker(featureId)

    /** 地图 id 只在数据层用，界面上统一显示「地区 · 区域」。 */
    private fun mapLabel(mapId: String?): String {
        if (mapId.isNullOrBlank()) return ""
        val m = WikiRepo.map(mapId) ?: return "未知地图"
        return listOf(m.region, m.area)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .ifBlank { "未知地图" }
    }

    /** 采集点 → 站点截图文件名。marker 的 target_name 有时是「地区/技能」而不是编号。 */
    private fun collectShot(m: Marker?): String? {
        if (m?.targetType != "CollectionPoint") return null
        return WikiShots.fileNameForCollection(m.targetName)
            ?: WikiShots.fileNameForCollection(WikiRepo.collectionCodeOfFeature(m.featureId))
    }

    /** 单步定位。 */
    fun anchor(
        step: QuestStep,
        regionHint: String?,
        regions: List<String>,
        prevMapId: String? = null,
        override: Marker? = null,
    ): Anchored {
        if (override != null) {
            // 用户钉点只存了 feature_id；补一次库查询，拿回 target_type/target_name
            // （采集点截图、地图图标都依赖它们）。
            val full = markerOf(override.featureId) ?: override
            return Anchored(
                step,
                full,
                "手动钉点",
                listOf("已按你的选择固定在 ${mapLabel(full.mapId)}"),
            )
        }
        // 采集步骤先按「物品 → 采集点」打分。地点内链通常只是「在某某岛/某某区域」，
        // 指不到具体采集点；先走内链会把整座岛当成落点，等于没规划。
        if (step.kind.contains("Collect")) {
            val linkMaps = step.links.mapNotNull { link ->
                markerOf(link.placeFeatureId)?.mapId?.takeIf { it.isNotBlank() }
            }.toSet()
            val hit = collectTarget(
                step.text,
                regionHint ?: regions.firstOrNull(),
                prevMapId,
                linkMaps,
            )
            if (hit != null) {
                return Anchored(step, hit.pin, hit.how, hit.notes, hit.mapOnly, hit.choices)
            }
        }
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
                return Anchored(step, m, "地名匹配", listOf("地点：${place!!.name}"))
            }
        }
        // 2b) 任务跨区域：quest_location 只记接取地（如「英维迪亚烈王国·冯斯麦姆港」），
        //     但步骤可能跑到别的图（名流帽要去阿伐利缇亚市场）。地区内没命中就全局再找一次。
        val globalPlace = Gazetteer.matchPlaces(step.text, null, 1).firstOrNull()
        val globalMarker = markerOf(globalPlace?.featureId)
        if (globalMarker != null && !globalMarker.mapId.isNullOrBlank()) {
            return Anchored(step, globalMarker, "地名匹配", listOf("地点：${globalPlace!!.name}"))
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
        return Anchored(step, null, "未定位")
    }

    private data class CollectResult(
        val pin: Marker?,
        val mapOnly: String?,
        val notes: List<String>,
        val how: String,
        val choices: List<PinChoice> = emptyList(),
    )

    private data class ScoredCollect(
        val candidate: CollectCandidate,
        val score: Double,
        val location: CollectLocation?,
        val reasons: List<String>,
    )

    private fun normalizeRegion(s: String?): String =
        s?.trim().orEmpty().replace("的", "").replace(" ", "").replace("　", "")

    private fun regionMatches(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        val x = normalizeRegion(a)
        val y = normalizeRegion(b)
        return x == y || x.contains(y) || y.contains(x)
    }

    private fun dropPercent(p: Double): Double = if (p <= 1.0) p * 100.0 else p

    /**
     * 采集品 → 采集点。按设计文档 5.4 打分，不拿「第一条」当答案。
     *
     * 排除：地区与步骤不一致、站点投影落在 Mapinfo 外、没有 Mapinfo 的层。
     * 加分：掉率 /10、地名同图 +40、场景技能一致 +25、和上一步同图 +15。
     * 只有一个候选，或最高分 ≥40 且领先第二名 ≥25，才自动钉点；否则最多列 5 个候选。
     */
    private fun collectTarget(
        text: String,
        regionHint: String?,
        prevMapId: String?,
        extraPlaceMaps: Set<String> = emptySet(),
    ): CollectResult? {
        val items = Gazetteer.matchCollectItems(text)
        if (items.isEmpty()) {
            Gazetteer.matchCollection(text)?.let {
                return CollectResult(null, null, listOf("采集品：$it（没有掉落映射）"), "未定位")
            }
            return null
        }
        val candidates = WikiRepo.collectCandidates(items)
        val itemLabel = "采集品：${items.joinToString("、")}"
        if (candidates.isEmpty()) {
            return CollectResult(null, null, listOf(itemLabel, "库里没有这些物品的采集点"), "未定位")
        }
        val stepRegion = Gazetteer.matchRegion(text) ?: regionHint
        val placeMaps = (Gazetteer.matchPlaces(text, stepRegion, 8)
            .mapNotNull { it.mapId }
            .toSet() + extraPlaceMaps)
        val scored = candidates
            .mapNotNull { scoreCollect(it, text, stepRegion, placeMaps, prevMapId) }
            // 同分时按掉率高的排前面（只在同一处候选之间比，不跨图）。
            // 原始 probability 有 0~1 与 0~100 两套口径（见 dropPercent），统一换算成百分比再比。
            .sortedWith(
                compareByDescending<ScoredCollect> { it.score }
                    .thenByDescending { dropPercent(it.candidate.probability) },
            )
        if (scored.isEmpty()) {
            return CollectResult(
                null,
                null,
                listOf(itemLabel, "候选采集点都不在步骤地区${stepRegion?.let { "（$it）" } ?: ""}"),
                "采集点待选",
            )
        }

        val top = scored.first()
        val second = scored.getOrNull(1)
        // 两个候选贴在一起（同一张图、坐标差不到 8）就是同一个采集点，不必再让人选。
        fun sameSpot(a: CollectLocation?, b: CollectLocation?): Boolean {
            if (a == null || b == null || a.mapId == null || a.mapId != b.mapId) return false
            val ax = a.fx ?: return false
            val ay = a.fy ?: return false
            val bx = b.fx ?: return false
            val by = b.fy ?: return false
            val dx = ax - bx
            val dy = ay - by
            return dx * dx + dy * dy <= 8.0 * 8.0
        }
        val autoPin = scored.size == 1 ||
            (top.score >= 40.0 && second == null) ||
            (top.score >= 40.0 && second != null && top.score - second.score >= 25.0) ||
            sameSpot(top.location, second?.location)
        val notes = ArrayList<String>()
        notes.add(itemLabel)
        notes.addAll(top.reasons)

        if (autoPin) {
            val loc = top.location
            val pin = loc?.takeIf { it.drawable }?.toMarker(top.candidate)
            if (pin != null) {
                notes.add("采集点：${describeLocation(loc)}")
                return CollectResult(pin, null, notes, "采集点")
            }
            // 赢家只有站点底图坐标：不要退回去钉一个分数更低的点。
            loc?.let {
                notes.add("采集点：${describeLocation(it)}")
                notes.add("这个点只有站点底图坐标，当前 FrontierNav 底图上不画")
            }
            return CollectResult(null, loc?.mapId, notes, "采集点待选")
        }

        // 候选明细交给任务页的选择器（choices），计划里只留一句人话来。
        notes.add("有 ${scored.size} 个候选采集点，暂未自动钉点")
        top.location?.mapId?.let { id ->
            if (scored.count { it.location?.mapId == id } == scored.size) {
                notes.add("候选都在同一张图，先落到这张图")
            }
        }
        val choices = ArrayList<PinChoice>()
        scored.take(5).forEachIndexed { i, s ->
            val loc = s.location
            choices.add(
                PinChoice(
                    label = describeLocation(loc),
                    score = s.score,
                    mapId = loc?.mapId,
                    marker = loc?.takeIf { it.drawable }?.toMarker(s.candidate),
                    itemNames = s.candidate.itemNames,
                ),
            )
        }
        return CollectResult(null, top.location?.mapId, notes, "采集点待选", choices)
    }

    private fun scoreCollect(
        candidate: CollectCandidate,
        text: String,
        stepRegion: String?,
        placeMaps: Set<String>,
        prevMapId: String?,
    ): ScoredCollect? {
        // 地区和步骤不一致的直接排除；地区和地图都没有时保留，交给后续候选列表。
        if (!candidate.region.isNullOrBlank() && !stepRegion.isNullOrBlank() &&
            !regionMatches(candidate.region, stepRegion)
        ) {
            return null
        }
        val valid = candidate.locations.filter { loc ->
            // 站点点必须投在 Mapinfo 范围内；frontier 点不依赖站点投影。
            loc.wikiArea == null || loc.inside
        }
        if (valid.isEmpty()) return null
        if (candidate.region.isNullOrBlank() && !stepRegion.isNullOrBlank()) {
            val known = valid.mapNotNull { it.region?.takeIf(String::isNotBlank) }
            if (known.isNotEmpty() && known.none { regionMatches(it, stepRegion) }) return null
        }

        val pct = dropPercent(candidate.probability)
        val base = (pct / 10.0).coerceIn(0.0, 10.0)
        var bestLoc: CollectLocation? = null
        var bestScore = Double.NEGATIVE_INFINITY
        val reasons = ArrayList<String>()

        for (loc in valid) {
            var score = base
            if (loc.mapId != null && loc.mapId in placeMaps) score += 40.0
            if (!candidate.fieldSkill.isNullOrBlank() && text.contains(candidate.fieldSkill)) score += 25.0
            if (loc.mapId != null && loc.mapId == prevMapId) score += 15.0
            // 同分时优先能直接画出来的 frontier 点。
            if (loc.drawable) score += 0.01
            if (score > bestScore) {
                bestScore = score
                bestLoc = loc
            }
        }
        bestLoc?.let { loc ->
            if (loc.mapId != null && loc.mapId in placeMaps) {
                reasons.add("步骤地名在 ${describeLocation(loc)}")
            }
            if (loc.mapId != null && loc.mapId == prevMapId) reasons.add("和上一步同一张图")
        }
        if (!candidate.fieldSkill.isNullOrBlank() && text.contains(candidate.fieldSkill)) {
            reasons.add("场景技能对得上：${candidate.fieldSkill}")
        }
        reasons.add("掉率 ${"%.0f".format(pct)}%")
        return ScoredCollect(candidate, bestScore, bestLoc, reasons.distinct())
    }

    private fun describeLocation(loc: CollectLocation?): String {
        if (loc == null) return "位置未知"
        val map = WikiRepo.map(loc.mapId)
        val area = map?.let { "${it.region}·${it.area}" } ?: loc.mapGameName
        // `ma07a_b_floor_01` 这类是游戏内部图层名，不往用户面前放。
        val layer = loc.wikiArea?.takeIf { !area.contains(it) && !INTERNAL_NAME.matches(it) }
        return listOfNotNull(area.takeIf { it.isNotBlank() }, layer).joinToString(" / ")
    }

    private fun CollectLocation.toMarker(candidate: CollectCandidate): Marker = Marker(
        featureId = featureId ?: "",
        mapId = mapId ?: "",
        mapGameName = mapGameName,
        frontierX = fx ?: 0.0,
        frontierY = fy ?: 0.0,
        worldX = null,
        worldZ = null,
        targetType = "CollectionPoint",
        targetName = candidate.collectionCode,
        targetId = "",
    )

    /** 任务页里显式写出的物品名（如有），用于补上步骤里没写物品的情况。 */
    fun questCollectItems(detail: QuestDetail): List<String> {
        val text = buildString {
            append(detail.description)
            detail.steps.forEach { append('\n').append(it.text).append('\n').append(it.detail) }
        }
        return Gazetteer.matchCollectItems(text)
    }

    fun anchorQuest(detail: QuestDetail, overrides: Map<Int, Marker> = emptyMap()): List<Anchored> {
        val regions = regionsOf(detail)
        val hint = regions.firstOrNull()
        var prevMap: String? = null
        val raw = detail.steps.map { step ->
            val a = anchor(step, hint, regions, prevMap, overrides[step.position])
            a.pin?.mapId?.takeIf { it.isNotBlank() }?.let { prevMap = it }
                ?: a.mapOnly?.let { prevMap = it }
            a
        }
        // 图级兜底：同任务里多数已定位步骤落在哪张图，剩下没坐标的步骤至少指到那张图。
        // 只给地图 id、不给坐标，UI 标成「图级定位」，用户长按地图钉点就能补坐标。
        val mainMap = raw.mapNotNull { it.pin?.mapId?.takeIf { m -> m.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: return raw
        return raw.map { a ->
            // 已经有坐标、或者第 8 步这种「采集候选已经确定到哪张图」的，别再被主图覆盖。
            if (a.pin != null || a.mapOnly != null) {
                a
            } else {
                // 步骤里点名了别的地区（如「在檀佩兰提亚…」）就落到那个地区的主图，
                // 不要被任务接取地所在的主图带偏。
                val region = Gazetteer.matchRegion(a.step.text)
                val fallback = region?.let { regionMainMap(it) } ?: mainMap
                a.copy(
                    mapOnly = fallback,
                    notes = a.notes + "所在图：${WikiRepo.map(fallback)?.let { "${it.region}·${it.area}" } ?: fallback}（估算，坐标待确认）",
                )
            }
        }
    }

    fun buildQuestPlan(detail: QuestDetail, overrides: Map<Int, Marker> = emptyMap()): List<NewStep> =
        anchorQuest(detail, overrides).map { a ->
            val m = a.pin
            NewStep(
                title = "第${a.step.position}步 · ${a.step.text}",
                detail = stepDetail(a, m),
                mapId = m?.mapId?.takeIf { it.isNotBlank() } ?: a.mapOnly,
                fx = m?.frontierX,
                fy = m?.frontierY,
                featureId = m?.featureId,
                shot = collectShot(m),
            )
        }

    /**
     * 组装步骤说明：wiki 原文 + 定位备注 + 地图 + 关联。
     *
     * 同一句话经常出现两次（wiki 正文里一条、定位备注里一条，或多条备注重复），
     * 所以最后按行去重；备注里已经写了地图名时不再单列「地图：…」。
     */
    private fun stepDetail(a: Anchored, m: Marker?): String {
        val lines = ArrayList<String>()
        if (a.step.detail.isNotBlank()) lines.addAll(a.step.detail.lines())
        lines.addAll(a.notes)
        val mapText = m?.mapId?.takeIf { it.isNotBlank() }?.let { mapLabel(it) }
        if (mapText != null && lines.none { it.contains(mapText) }) lines.add("地图：$mapText")
        if (a.step.links.isNotEmpty()) {
            lines.add("关联：" + a.step.links.map { it.label }.distinct().joinToString("、"))
        }
        val seen = HashSet<String>()
        return lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && seen.add(it) }
            .joinToString("\n")
    }

    /**
     * 开环计划：只列还没习得的等级需求，按「先易后难 + 同图聚集」排。
     *
     * 定位顺序：地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（含「XX族」族名）> 采集品。
     * 只提到地区、定不到具体点的需求，退到该地区主图（图级定位，UI 标「仅到图」）。
     * 纯战斗/操作类需求没有地点可落，补一条「怎么刷」提示，避免计划只剩一句计数。
     *
     * @param levelIds 非空时只处理这些等级（用户在圆环页勾选的范围）；
     *   为 null 时保持旧行为，处理全部等级。已习得等级仍受 [skipLearned] 控制。
     */
    fun buildBladePlan(
        blade: BladeSummary,
        nodes: List<AffinityNode>,
        skipLearned: Boolean = true,
        levelIds: Set<Long>? = null,
    ): List<NewStep> {
        val out = ArrayList<NewStep>()
        for (node in nodes) {
            for (lv in node.levels) {
                if (levelIds != null && lv.id !in levelIds) continue
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
                    collectTarget(req, regionHint, null)?.pin
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
                                append("\n地图：${mapLabel(pin.mapId)} · $label")
                            } else if (mapOnly != null) {
                                append("\n地图：${mapLabel(mapOnly)}（只能到图，具体点位待确认）")
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
                        shot = collectShot(pin),
                    ),
                )
            }
        }
        return orderForWalking(out)
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

    /**
     * 拜访顺序：按地图分组（组的顺序 = 该图首次出现的位置），组内最近邻串点。
     *
     * - 起点沿用上一组的最后一个坐标；第一组没有上一组时，保留本组第一个点的位置。
     * - 同坐标的点保持原有相对顺序：`minByOrNull` 遇到并列取先出现的那个。
     * - 画不上图的步骤（`mapId` 为空、`fx/fy` 为空、或投影落在图外）不参与排序，按原顺序排在最后。
     *
     * 复杂度 O(n²)（地图元数据走 [WikiRepo.map] 的缓存，不额外查库），只在生成计划时算一次。
     * 纯启发式，不承诺全局最优。
     */
    fun orderForWalking(steps: List<NewStep>): List<NewStep> {
        val groups = LinkedHashMap<String, MutableList<NewStep>>()
        val unlocated = ArrayList<NewStep>()
        for (s in steps) {
            val mapId = s.mapId
            val fx = s.fx
            val fy = s.fy
            if (mapId != null && fx != null && fy != null && drawableOn(mapId, fx, fy)) {
                groups.getOrPut(mapId) { ArrayList() }.add(s)
            } else {
                unlocated.add(s)
            }
        }
        if (groups.isEmpty()) return steps

        val out = ArrayList<NewStep>(steps.size)
        var cursorFx: Double? = null
        var cursorFy: Double? = null
        for (group in groups.values) {
            val remaining = group.toMutableList()
            // 第一组从本组第一个点起步，保持它在新列表的第一位；之后各组承接上一组终点。
            if (cursorFx == null || cursorFy == null) {
                val first = remaining.removeAt(0)
                out.add(first)
                cursorFx = first.fx
                cursorFy = first.fy
            }
            while (remaining.isNotEmpty()) {
                val cx = cursorFx ?: 0.0
                val cy = cursorFy ?: 0.0
                val next = remaining.minByOrNull { s ->
                    val dx = (s.fx ?: 0.0) - cx
                    val dy = (s.fy ?: 0.0) - cy
                    dx * dx + dy * dy
                } ?: break
                remaining.remove(next)
                out.add(next)
                cursorFx = next.fx
                cursorFy = next.fy
            }
        }
        out.addAll(unlocated)
        return out
    }

    /** 投影落在图外的坐标画不上图，和地图页的过滤口径保持一致；图查不到时无从判断，按有坐标处理。 */
    private fun drawableOn(mapId: String, fx: Double, fy: Double): Boolean {
        val info = WikiRepo.map(mapId) ?: return true
        return MapMath.normalized(info, fx, fy) != null
    }
}
