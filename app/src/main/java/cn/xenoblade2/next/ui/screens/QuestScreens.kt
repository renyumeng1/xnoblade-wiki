package cn.xenoblade2.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.QuestDetail
import cn.xenoblade2.next.data.QuestSummary
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.data.WikiShots
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.cleanStepDetail
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.KeyValue
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import kotlinx.coroutines.launch
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.components.WikiShotPanel
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay

/** 原版任务页的四个分类入口。 */
private data class QuestCategoryEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val QUEST_CATEGORIES = listOf(
    QuestCategoryEntry("普通任务", "普通任务", "各地区的支线委托与报酬", Icons.AutoMirrored.Rounded.Assignment),
    QuestCategoryEntry("异刃任务", "异刃任务", "异刃专属故事与牵绊任务", Icons.Rounded.AutoAwesome),
    QuestCategoryEntry("佣兵团任务", "佣兵团任务", "派遣异刃完成的佣兵团委托", Icons.Rounded.Flag),
    QuestCategoryEntry("非正式任务", "非正式任务", "不会进入任务日志的探索、收集与解锁活动", Icons.Rounded.Explore),
)

@Composable
fun QuestListScreen(onOpenCategory: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "任务")
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(QUEST_CATEGORIES, key = { it.key }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Surface.copy(alpha = 0.88f))
                        .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenCategory(entry.key) }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF7653B5)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(entry.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            entry.subtitle,
                            color = Palette.TextDim,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("›", color = Palette.Core, fontSize = 22.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

private val STATUS_FILTERS = listOf("全部", "已收集", "未收集")

@Composable
fun QuestCategoryScreen(
    category: String,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("全部") }
    var region by remember { mutableStateOf("全部") }
    var chapter by remember { mutableStateOf("全部章节") }
    var quests by remember { mutableStateOf<List<QuestSummary>>(emptyList()) }
    var regions by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var markedVersion by remember { mutableStateOf(Prefs.marked) }

    LaunchedEffect(category) {
        loading = true
        val loaded = WikiRepo.io { WikiRepo.quests(category, "", 500) to WikiRepo.questRegions(category) }
        quests = loaded.first
        regions = loaded.second
        loading = false
    }

    val visible = quests.filter { q ->
        (keyword.isBlank() || listOf(q.display, q.title, q.npc, q.condition).any { it.contains(keyword.trim(), true) }) &&
            when (status) {
                "已收集" -> Prefs.markKey("quest", q.id) in markedVersion
                "未收集" -> Prefs.markKey("quest", q.id) !in markedVersion
                else -> true
            } &&
            (region == "全部" || q.regions.contains(region)) &&
            (chapter == "全部章节" || q.chapter.startsWith(chapter))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = category,
            onBack = onBack,
            actions = {
                TopBarTextAction(
                    text = if (selectionMode) "完成" else "多选",
                    onClick = {
                        selectionMode = !selectionMode
                        selected = emptySet()
                    },
                )
            },
        )

        QuestSearchBox(keyword) { keyword = it }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(STATUS_FILTERS) { option ->
                FilterPill(option, status == option) { status = option }
            }
        }

        if (regions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterPill("全部", region == "全部") { region = "全部" } }
                items(regions) { option -> FilterPill(option, region == option) { region = option } }
            }
            Spacer(Modifier.height(8.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterPill("全部章节", chapter == "全部章节") { chapter = "全部章节" } }
            items((1..10).map { "第${it}章" }) { option -> FilterPill(option, chapter == option) { chapter = option } }
        }

        Text(
            "$category · ${visible.size} 条",
            color = Palette.TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp),
        )

        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingBox(Modifier.fillMaxSize())
                visible.isEmpty() -> EmptyHint(
                    if (category == "非正式任务") "本地库暂未收录非正式任务（探索 / 收集 / 解锁活动）"
                    else "没有符合条件的任务",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = if (selectionMode) 88.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { quest ->
                        val marked = Prefs.markKey("quest", quest.id) in markedVersion
                        QuestCard(
                            quest = quest,
                            marked = marked,
                            selectionMode = selectionMode,
                            selected = quest.id in selected,
                            onClick = {
                                if (selectionMode) {
                                    selected = if (quest.id in selected) selected - quest.id else selected + quest.id
                                } else {
                                    onOpen(quest.id)
                                }
                            },
                        )
                    }
                }
            }

            if (selectionMode) {
                SelectionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    count = selected.size,
                    onSelectAll = { selected = visible.map { it.id }.toSet() },
                    onMark = {
                        Prefs.setMarked("quest", selected, true)
                        markedVersion = Prefs.marked
                        selected = emptySet()
                        selectionMode = false
                    },
                    onUnmark = {
                        Prefs.setMarked("quest", selected, false)
                        markedVersion = Prefs.marked
                        selected = emptySet()
                        selectionMode = false
                    },
                )
            }
        }
    }
}

@Composable
private fun QuestSearchBox(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("搜索任务、委托人或条件", color = Palette.TextDim, fontSize = 14.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.Core),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterPill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Core else Palette.SurfaceHigh)
            .border(1.dp, if (active) Palette.Core else Palette.CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (active) Color.White else Palette.Text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun TopBarTextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Palette.Core.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = Palette.Core, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuestCard(
    quest: QuestSummary,
    marked: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface.copy(alpha = 0.9f))
            .border(
                1.dp,
                if (selected) Palette.Core else Palette.CardBorder,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) Palette.Core else Color.Transparent)
                        .border(1.5.dp, if (selected) Palette.Core else Palette.TextDim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Text("✓", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(10.dp))
            }
            Text(
                quest.display,
                color = Palette.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (marked) Tag("已收集", Palette.Done)
            Text("›", color = Palette.Core, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp))
        }
        if (quest.location.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            MetaRow(Icons.Rounded.Place, quest.location, Palette.TextDim)
        }
        if (quest.npc.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            MetaRow(Icons.Rounded.Person, quest.npc, Palette.Core)
        }
        if (quest.condition.isNotBlank()) {
            Spacer(Modifier.height(7.dp))
            MetaRow(Icons.Rounded.Lock, quest.condition, Palette.TextDim, maxLines = 2)
        }
        if (quest.chapter.isNotBlank() && quest.condition.isBlank()) {
            Spacer(Modifier.height(7.dp))
            MetaRow(Icons.Rounded.Lock, quest.chapter, Palette.TextDim)
        }
    }
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    text: String,
    color: Color,
    maxLines: Int = 1,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = color,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SelectionBar(
    modifier: Modifier = Modifier,
    count: Int,
    onSelectAll: () -> Unit,
    onMark: () -> Unit,
    onUnmark: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("已选择 $count 项", color = Palette.TextDim, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        SelectionAction("全选", true, onSelectAll)
        SelectionAction("标记", count > 0, onMark)
        SelectionAction("取消标记", count > 0, onUnmark)
    }
}

@Composable
private fun SelectionAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Palette.Core.copy(alpha = 0.12f) else Palette.SurfaceHigh)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (enabled) Palette.Core else Palette.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun QuestDetailScreen(
    questId: Long,
    onBack: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    var detail by remember { mutableStateOf<QuestDetail?>(null) }
    var anchored by remember { mutableStateOf<List<Planner.Anchored>>(emptyList()) }
    var overrides by remember { mutableStateOf<Map<Int, Marker>>(emptyMap()) }
    var planIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var createRequested by remember { mutableStateOf(false) }
    var recreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(questId) {
        val loaded = WikiRepo.io {
            val d = WikiRepo.questDetail(questId)
            val ov = PlanStore.pinOverrides("quest", questId.toString())
            val a = d?.let { Planner.anchorQuest(it, ov) } ?: emptyList()
            Triple(d, a, ov)
        }
        detail = loaded.first
        anchored = loaded.second
        overrides = loaded.third
        planIds = WikiRepo.io {
            PlanStore.plans().filter { it.sourceType == "quest" && it.sourceId == questId.toString() }.map { it.id }
        }
    }

    LaunchedEffect(createRequested) {
        if (!createRequested) return@LaunchedEffect
        val d = detail ?: return@LaunchedEffect
        val id = WikiRepo.io {
            // 「重新生成」先删掉这份任务之前生成的计划，避免列表里堆一串同名计划。
            if (recreate) planIds.forEach { PlanStore.deletePlan(it) }
            val steps: List<NewStep> = Planner.buildQuestPlan(d, overrides)
            val planId = PlanStore.createPlan("任务 · ${d.title}", "quest", d.id.toString())
            PlanStore.addSteps(planId, steps)
            planId
        }
        recreate = false
        createRequested = false
        creating = false
        planIds = listOf(id)
        message = "已生成计划 #$id，共 ${d.steps.size} 步"
        onOpenPlan(id)
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = detail?.title ?: "任务",
            subtitle = detail?.let { "${it.category} · ${it.chapter}" },
            onBack = onBack,
        )

        val d = detail
        if (d == null) {
            LoadingBox()
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "基本信息") {
                    KeyValue("分类", d.category)
                    KeyValue("章节", d.chapter)
                    KeyValue("NPC", d.npc)
                    KeyValue("接取地点", d.locations.joinToString("；"))
                    KeyValue("条件", d.condition.take(200))
                    KeyValue("奖励", listOfNotNull(
                        d.rewardText.takeIf { it.isNotBlank() },
                        if (d.gold > 0) "金币 ${d.gold}" else null,
                        if (d.exp > 0) "EXP ${d.exp}" else null,
                    ).joinToString(" · "))
                    if (d.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(d.description.take(600), color = Palette.TextDim, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionRow(
                        text = when {
                            creating -> "生成中…"
                            planIds.isNotEmpty() -> "已有 ${planIds.size} 份计划 · 查看"
                            else -> "生成地图计划"
                        },
                        onClick = {
                            if (planIds.isNotEmpty()) {
                                onOpenPlan(planIds.first())
                            } else if (!creating) {
                                creating = true
                                createRequested = true
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (planIds.isNotEmpty() && !creating) {
                        Text(
                            "重新生成",
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable {
                                    creating = true
                                    recreate = true
                                    createRequested = true
                                }
                                .padding(end = 8.dp),
                        )
                    }
                    Text(
                        buildString {
                            append("共 ${anchored.size} 步，${anchored.count { it.pinned }} 步已定位")
                            val coarse = anchored.count { !it.pinned && it.mapOnly != null }
                            if (coarse > 0) append(" · $coarse 步可到图")
                        },
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                    )
                }
                message?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Palette.Core, fontSize = 12.sp)
                }
            }

            items(anchored, key = { it.step.position }) { a ->
                StepCard(
                    a = a,
                    onOpenMap = onOpenMap,
                    onPick = { choice ->
                        val marker = choice.marker
                        if (marker != null) {
                            val next = overrides + (a.step.position to marker)
                            overrides = next
                            anchored = detail?.let { Planner.anchorQuest(it, next) } ?: anchored
                            scope.launch {
                                WikiRepo.io {
                                    PlanStore.setPinOverride(
                                        "quest",
                                        questId.toString(),
                                        a.step.position,
                                        marker,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}


@Composable
private fun StepCard(
    a: Planner.Anchored,
    onOpenMap: (String) -> Unit,
    onPick: (Planner.PinChoice) -> Unit,
) {
    val located = a.pinned
    val mapId = a.pin?.mapId?.takeIf { it.isNotBlank() } ?: a.mapOnly
    var shot by remember(a.pin?.featureId) { mutableStateOf<String?>(null) }
    LaunchedEffect(a.pin?.featureId) {
        val pin = a.pin
        shot = if (pin?.targetType == "CollectionPoint") {
            WikiShots.fileNameForCollection(pin.targetName)
                ?: WikiRepo.io {
                    WikiShots.fileNameForCollection(WikiRepo.collectionCodeOfFeature(pin.featureId))
                }
        } else {
            null
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(
                1.dp,
                if (located) Palette.Core.copy(alpha = 0.22f) else Palette.CardBorder,
                RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(a.step.position),
                color = if (located) Palette.Core else Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stepKindLabel(a.step.kind),
                color = Palette.TextDim,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            when {
                located -> Tag(a.how, Palette.Core)
                mapId != null -> Tag("图级定位", Palette.Core.copy(alpha = 0.7f))
                else -> Tag("未定位", Palette.TextDim)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(a.step.text, color = Palette.Text, fontSize = 14.sp, lineHeight = 20.sp)
        if (a.step.detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(cleanStepDetail(a.step.detail), color = Palette.TextDim, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (shot != null) {
            Spacer(Modifier.height(6.dp))
            WikiShotPanel(shot)
        }
        if (a.step.links.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                a.step.links.map { it.label }.distinct().joinToString("、"),
                color = Palette.Core.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
        if (!located && a.choices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "采集点有 ${a.choices.size} 个候选，选一个钉到地图：",
                color = Palette.TextDim,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            a.choices.forEach { choice ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (choice.selectable) Palette.SurfaceHigh else Palette.SurfaceHigh.copy(alpha = 0.4f))
                        .clickable(enabled = choice.selectable) { onPick(choice) }
                        .padding(horizontal = 9.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        choice.label,
                        color = if (choice.selectable) Palette.Text else Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (choice.selectable) "${"%.1f".format(choice.score)} 分" else "仅站点底图",
                        color = Palette.TextDim,
                        fontSize = 10.sp,
                    )
                }
            }
        }
        if (mapId != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Core.copy(alpha = if (located) 0.1f else 0.06f))
                    .clickable { onOpenMap(mapId) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("在地图查看", color = Palette.Core, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        val m = WikiRepo.map(mapId)
                        append(
                            listOfNotNull(m?.region, m?.area)
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" · ")
                                .ifBlank { "地图" },
                        )
                        if (!located) append(" · 无精确坐标")
                    },
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 任务步骤的原始类型是英文枚举，界面上只显示中文。 */
private fun stepKindLabel(kind: String): String {
    val raw = kind.substringAfter("官方目标 · ", kind).trim()
    return when (raw) {
        "ReachPlace" -> "前往"
        "Collect" -> "采集"
        "Battle" -> "战斗"
        "Talk" -> "对话"
        "QuestCondition" -> "任务条件"
        "UseItem" -> "使用物品"
        "Gimmick" -> "机关"
        else -> if (raw.any { it.code > 127 }) raw else "目标"
    }
}
