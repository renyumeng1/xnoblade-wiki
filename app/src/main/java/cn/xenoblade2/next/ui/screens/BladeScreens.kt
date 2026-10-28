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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.AffinityLevel
import cn.xenoblade2.next.data.AffinityNode
import cn.xenoblade2.next.data.BladeSkillRow
import cn.xenoblade2.next.data.BladeSummary
import cn.xenoblade2.next.data.MercenaryMissionRow
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.components.WikiIcon
import cn.xenoblade2.next.ui.components.WikiIcons
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.launch

private val ELEMENTS = listOf("火", "水", "风", "冰", "雷", "地", "光", "暗")
private val MODIFIERS = listOf("生命值", "力量", "以太力", "灵巧", "敏捷", "运气")
private val BLADE_CATEGORY_ORDER = listOf("主线异刃", "稀有异刃", "二周目异刃", "NS2异刃", "扩充票异刃")

private fun elementIcon(element: String): ImageVector = when (element) {
    "火" -> Icons.Rounded.LocalFireDepartment
    "水" -> Icons.Rounded.WaterDrop
    "风" -> Icons.Rounded.Air
    "冰" -> Icons.Rounded.AcUnit
    "雷" -> Icons.Rounded.Bolt
    "地" -> Icons.Rounded.Landscape
    "光" -> Icons.Rounded.LightMode
    "暗" -> Icons.Rounded.DarkMode
    else -> Icons.Rounded.AutoAwesome
}

private fun roleColor(role: String): Color = when {
    role.contains("攻击") -> Palette.Gold
    role.contains("回复") -> Palette.Aqua
    role.contains("防御") -> Palette.Brown
    else -> Palette.TextDim
}

private fun numText(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
fun BladeListScreen(onOpen: (Long, String) -> Unit) {
    var blades by remember { mutableStateOf<List<BladeSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("全部") }
    var element by remember { mutableStateOf("全属性") }
    var modifier by remember { mutableStateOf("全部补正") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var markedVersion by remember { mutableStateOf(Prefs.marked) }

    LaunchedEffect(Unit) {
        blades = WikiRepo.io { WikiRepo.blades() }
        loaded = true
    }

    val visible = blades.filter { blade ->
        (keyword.isBlank() || listOf(blade.name, blade.englishName, blade.weapon, blade.role).any {
            it.contains(keyword.trim(), true)
        }) &&
            when (status) {
                "已收集" -> Prefs.markKey("blade", blade.id) in markedVersion
                "未收集" -> Prefs.markKey("blade", blade.id) !in markedVersion
                else -> true
            } &&
            (element == "全属性" || blade.element == element) &&
            (modifier == "全部补正" || blade.modifier.contains(modifier))
    }

    val groups = buildList {
        BLADE_CATEGORY_ORDER.forEach { category ->
            val list = visible.filter { it.category == category }
            if (list.isNotEmpty()) add(category to list)
        }
        val known = BLADE_CATEGORY_ORDER.toSet()
        visible.filter { it.category !in known }
            .groupBy { it.category.ifBlank { "其他异刃" } }
            .forEach { (category, list) -> add(category to list) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "异刃图鉴",
            actions = {
                BladeTopAction(if (selectionMode) "完成" else "多选") {
                    selectionMode = !selectionMode
                    selected = emptySet()
                }
            },
        )

        BladeSearchBox(keyword) { keyword = it }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("全部", "已收集", "未收集")) { option ->
                BladeFilterPill(option, status == option, filled = true) { status = option }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                BladeFilterPill("全属性", element == "全属性") { element = "全属性" }
            }
            items(ELEMENTS) { option ->
                ElementOrb(option, element == option) { element = option }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { BladeFilterPill("全部补正", modifier == "全部补正") { modifier = "全部补正" } }
            items(MODIFIERS) { option ->
                BladeFilterPill(option, modifier == option) { modifier = option }
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                !loaded -> LoadingBox(Modifier.fillMaxSize())
                visible.isEmpty() -> EmptyHint("没有符合条件的异刃", Modifier.fillMaxSize())
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = if (selectionMode) 96.dp else 100.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEach { (category, list) ->
                        item(key = "header:$category", span = { GridItemSpan(maxLineSpan) }) {
                            BladeCategoryHeader(category, list.size)
                        }
                        gridItems(list, key = { it.id }) { blade ->
                            BladeCard(
                                blade = blade,
                                marked = Prefs.markKey("blade", blade.id) in markedVersion,
                                selectionMode = selectionMode,
                                selected = blade.id in selected,
                                onClick = {
                                    if (selectionMode) {
                                        selected = if (blade.id in selected) selected - blade.id else selected + blade.id
                                    } else {
                                        onOpen(blade.id, blade.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (selectionMode) {
                BladeSelectionBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    count = selected.size,
                    onSelectAll = { selected = visible.map { it.id }.toSet() },
                    onMark = {
                        Prefs.setMarked("blade", selected, true)
                        markedVersion = Prefs.marked
                        selected = emptySet()
                        selectionMode = false
                    },
                    onUnmark = {
                        Prefs.setMarked("blade", selected, false)
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
private fun BladeSearchBox(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("搜索异刃…", color = Palette.TextDim, fontSize = 15.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp),
                cursorBrush = SolidColor(Palette.Core),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BladeFilterPill(text: String, active: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    active && filled -> Palette.Core
                    active -> Palette.Core.copy(alpha = 0.08f)
                    else -> Palette.SurfaceHigh
                },
            )
            .border(
                1.dp,
                when {
                    active -> Color.Transparent
                    else -> Palette.CardBorder
                },
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (active && filled) Color.White else if (active) Palette.Core else Palette.Text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}


/** 优先用 xenoblade2.cn 的元素菱形图标，缺资源时回退 Material 矢量图标。 */
@Composable
private fun ElementGlyph(element: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    if (!WikiIcon(WikiIcons.elementPath(element), size = size, contentDescription = element)) {
        Icon(elementIcon(element), contentDescription = element, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun ElementOrb(element: String, active: Boolean, onClick: () -> Unit) {
    val color = when (element) {
        "火" -> Color(0xFFE8603C)
        "水" -> Color(0xFF3C8FE8)
        "风" -> Color(0xFF4FBF7A)
        "冰" -> Color(0xFF5BC8E8)
        "雷" -> Color(0xFF9B6BD6)
        "地" -> Color(0xFFC9A227)
        "光" -> Color(0xFFE8C547)
        "暗" -> Color(0xFF7A4FA3)
        else -> Palette.TextDim
    }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) color.copy(alpha = 0.16f) else Palette.SurfaceHigh)
            .border(1.5.dp, if (active) color else Palette.CardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ElementGlyph(element, color, 20.dp)
    }
}

@Composable
private fun BladeTopAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Core.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = Palette.Core, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BladeCategoryHeader(category: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(category, color = Palette.Core, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("$count", color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun BladeCard(
    blade: BladeSummary,
    marked: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, if (selected) Palette.Core else Palette.CardBorder, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .padding(10.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(blade.elementColor).copy(alpha = 0.10f)),
        ) {
            if (blade.portrait.isNotBlank()) {
                AssetImage(
                    path = blade.portrait,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = blade.name,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ElementGlyph(blade.element, Color(blade.elementColor), 48.dp)
                }
            }
            if (marked) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.42f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("已收集", color = Color.White, fontSize = 10.sp)
                }
            }
            if (selectionMode) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) Palette.Core else Color.Black.copy(alpha = 0.35f))
                        .border(1.5.dp, if (selected) Palette.Core else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Text("✓", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                blade.name,
                color = Palette.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                blade.weapon.ifBlank { "—" },
                color = Palette.TextDim,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                blade.role.ifBlank { "—" },
                color = roleColor(blade.role),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BladeSelectionBar(
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
fun BladeDetailScreen(bladeId: Long, onBack: () -> Unit, onOpenPlan: (Long) -> Unit) {
    var blade by remember { mutableStateOf<BladeSummary?>(null) }
    var nodes by remember { mutableStateOf<List<AffinityNode>>(emptyList()) }
    var skills by remember { mutableStateOf<List<BladeSkillRow>>(emptyList()) }
    var mercenaries by remember { mutableStateOf<List<MercenaryMissionRow>>(emptyList()) }
    var progress by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var creating by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var marked by remember { mutableStateOf(false) }
    // 选择态只在会话内保留：不入库，避免污染 affinity_progress（那张表只记已习得进度）。
    // 用 bladeId 作 key：同一处组合位置换一把异刃时不要把上一把的 level id 带过来。
    var picked by rememberSaveable(bladeId) { mutableStateOf(setOf<Long>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bladeId) {
        val loaded = WikiRepo.io {
            val b = WikiRepo.blades().firstOrNull { it.id == bladeId }
            val n = WikiRepo.bladeNodes(bladeId)
            val s = WikiRepo.bladeSkills(bladeId)
            val m = b?.let { WikiRepo.mercenaryMissionsForBlade(it.weapon, it.element) } ?: emptyList()
            Quad(b, n, s, m, PlanStore.allAffinityProgress())
        }
        blade = loaded.blade
        nodes = loaded.nodes
        skills = loaded.skills
        mercenaries = loaded.mercenaries
        progress = loaded.progress
        marked = Prefs.isMarked("blade", bladeId)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = blade?.name ?: "异刃", onBack = onBack)

        val b = blade
        if (b == null) {
            LoadingBox(Modifier.fillMaxSize())
            return@Column
        }

        val total = nodes.sumOf { it.levels.size }
        val done = nodes.sumOf { n -> n.levels.count { it.learned } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "hero") {
                BladeHero(b, marked) {
                    marked = !marked
                    Prefs.setMarked("blade", listOf(b.id), marked)
                }
            }
            item(key = "tabs") {
                BladeTabs(tab) { tab = it }
            }

            when (tab) {
                0 -> item(key = "attributes") { BladeAttributes(b) }

                1 -> {
                    item(key = "affinity-head") {
                        // 可勾选的等级：没标「初始习得」、本地进度也没记满。
                        val openLevels = nodes.flatMap { pickableLevels(it, progress) }
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)) {
                            SectionCard(title = "开环概览") {
                                Text(
                                    "${nodes.size} 个节点 · $total 级需求 · 已习得 $done 级",
                                    color = Palette.TextDim,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { if (total == 0) 0f else done.toFloat() / total },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Color(b.elementColor),
                                    trackColor = Palette.CardBorder,
                                )
                                Spacer(Modifier.height(10.dp))
                                AffinityPickBar(
                                    open = openLevels,
                                    picked = picked,
                                    onPickMany = { ids, select ->
                                        picked = if (select) picked + ids else picked - ids.toSet()
                                    },
                                    onClear = { picked = emptySet() },
                                )
                                Spacer(Modifier.height(10.dp))
                                val chosen = picked.size
                                if (chosen == 0) {
                                    Text(
                                        if (openLevels.isEmpty()) "圆环已全部习得" else "先选择要做的环",
                                        color = Palette.TextDim,
                                        fontSize = 13.sp,
                                    )
                                } else {
                                    ActionRow(
                                        text = if (creating) "生成中…" else "生成开环计划（已选 $chosen 级）",
                                        onClick = {
                                            if (creating) return@ActionRow
                                            creating = true
                                            val ids = picked
                                            scope.launch {
                                                val id = WikiRepo.io {
                                                    val steps: List<NewStep> = Planner.buildBladePlan(b, nodes, levelIds = ids)
                                                    val planId = PlanStore.createPlan("开环 · ${b.name}", "blade", b.id.toString())
                                                    PlanStore.addSteps(planId, steps)
                                                    planId
                                                }
                                                creating = false
                                                picked = emptySet()
                                                onOpenPlan(id)
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "勾选节点或等级后再生成，只包含选中的部分；" +
                                        "带地名/敌名/NPC 的需求自动钉点，只写地区的落到该地区主图，" +
                                        "纯战斗类需求补「怎么刷」提示；同图步骤按最近邻排序。",
                                    color = Palette.TextDim,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                    if (nodes.isEmpty()) {
                        item(key = "affinity-empty") { EmptyHint("暂无牵绊圆环数据") }
                    } else {
                        items(nodes, key = { "node:${it.id}" }) { node ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                NodeCard(
                                    node = node,
                                    progress = progress,
                                    picked = picked,
                                    onToggleLevel = { id ->
                                        picked = if (id in picked) picked - id else picked + id
                                    },
                                    onToggleNode = { ids, select ->
                                        picked = if (select) picked + ids else picked - ids.toSet()
                                    },
                                    onProgress = { level, value ->
                                        progress = progress.toMutableMap().also { it[level.id] = value }
                                        // 进度记满就不再算进计划范围，避免给已做完的环生成步骤。
                                        if (affinityDone(level, value)) picked = picked - level.id
                                        scope.launch { WikiRepo.io { PlanStore.setAffinityProgress(level.id, value) } }
                                    },
                                )
                            }
                        }
                    }
                }

                2 -> {
                    if (skills.isEmpty()) {
                        item(key = "skills-empty") { EmptyHint("暂无技能数据") }
                    } else {
                        skillGroups(skills).forEach { (label, list) ->
                            item(key = "skill-head:$label") { SkillGroupHeader(label, list.size) }
                            items(list, key = { "skill:${it.id}" }) { skill ->
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    SkillCard(skill)
                                }
                            }
                        }
                    }
                }

                else -> {
                    item(key = "merc-head") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            SectionCard(title = "佣兵团训练") {
                                Text(
                                    "按武器与属性匹配的佣兵团任务，匹配项越多越靠前。派遣前可在「任务 → 佣兵团任务」查看完整条件与报酬。",
                                    color = Palette.TextDim,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                    if (mercenaries.isEmpty()) {
                        item(key = "merc-empty") { EmptyHint("暂无匹配的佣兵团任务") }
                    } else {
                        items(mercenaries, key = { "merc:${it.id}" }) { mission ->
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                MercenaryCard(mission)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quad(
    val blade: BladeSummary?,
    val nodes: List<AffinityNode>,
    val skills: List<BladeSkillRow>,
    val mercenaries: List<MercenaryMissionRow>,
    val progress: Map<Long, Int>,
)

private fun skillGroups(skills: List<BladeSkillRow>): List<Pair<String, List<BladeSkillRow>>> {
    val order = listOf("special" to "必杀技", "blade_art" to "异刃武技", "battle_skill" to "战斗技能", "field_skill" to "场景技能")
    val out = order.mapNotNull { (key, label) ->
        skills.filter { it.category == key }.takeIf { it.isNotEmpty() }?.let { label to it }
    }.toMutableList()
    skills.filter { it.category !in order.map { o -> o.first } }
        .takeIf { it.isNotEmpty() }
        ?.let { out.add("其他" to it) }
    return out
}

@Composable
private fun BladeHero(blade: BladeSummary, marked: Boolean, onToggleMark: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(260.dp).background(Color(0xFF20242B))) {
        if (blade.portrait.isNotBlank()) {
            AssetImage(
                path = blade.portrait,
                modifier = Modifier.fillMaxSize(),
                contentDescription = blade.name,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(blade.elementColor).copy(alpha = 0.24f))
                        .border(1.dp, Color(blade.elementColor), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ElementGlyph(blade.element, Color(blade.elementColor), 18.dp)
                }
                Spacer(Modifier.width(8.dp))
                if (WikiIcon(WikiIcons.weaponPath(blade.weapon), size = 18.dp)) {
                    Spacer(Modifier.width(5.dp))
                }
                Text(blade.weapon, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
                if (blade.modifier.isNotBlank()) {
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "属性修正 ${blade.modifier}",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(blade.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(
            onClick = onToggleMark,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Icon(
                if (marked) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (marked) "取消收集" else "标记为已收集",
                tint = if (marked) Palette.Gold else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private val BLADE_TABS = listOf("能力", "牵绊圆环", "技能", "佣兵团训练")

@Composable
private fun BladeTabs(current: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Surface)) {
        Row(Modifier.fillMaxWidth()) {
            BLADE_TABS.forEachIndexed { index, label ->
                val active = index == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        color = if (active) Palette.Core else Palette.TextDim,
                        fontSize = 14.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (active) Palette.Core else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Palette.CardBorder)
    }
}

@Composable
private fun BladeAttributes(blade: BladeSummary) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        SectionCard(title = "基础能力") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AbilityCell("初始强度", numText(blade.strength), Modifier.weight(1f))
                AbilityCell("冷却时间（秒）", numText(blade.cooldown), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AbilityCell("物理防御", numText(blade.physicalDefense), Modifier.weight(1f))
                AbilityCell("以太防御", numText(blade.etherDefense), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionCard(title = "资料") {
            InfoRow("战斗类别", blade.role)
            InfoRow("武器", blade.weapon)
            InfoRow("辅助核心槽", if (blade.auxSlots > 0) "${blade.auxSlots}" else "—")
            InfoRow("御刃者能力修正", blade.modifier)
            InfoRow("获得方式", blade.unlockMethod)
        }
    }
}

@Composable
private fun AbilityCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Palette.TextDim, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Palette.Core, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.width(108.dp))
        Text(
            value.ifBlank { "—" },
            color = Palette.Text,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SkillGroupHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.Core, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("$count", color = Palette.TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun SkillCard(skill: BladeSkillRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                skill.name,
                color = Palette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (skill.maxLevel.isNotBlank()) Tag("Lv.${skill.maxLevel}", Palette.Gold)
        }
        if (skill.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(skill.description, color = Palette.TextDim, fontSize = 13.sp, lineHeight = 19.sp)
        }
        if (skill.effect.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Core.copy(alpha = 0.07f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    skill.effect,
                    color = Palette.Text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

private val MERC_KIND_LABELS = mapOf(
    "race" to "种族",
    "weapon" to "武器",
    "element" to "属性",
    "gender" to "性别",
    "strength" to "强度",
)

@Composable
private fun MercenaryCard(mission: MercenaryMissionRow) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(mission.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (mission.conditions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                mission.conditions.joinToString("  ·  ") { raw ->
                    val parts = raw.split('|', limit = 2)
                    val label = MERC_KIND_LABELS[parts.first()] ?: parts.first()
                    if (parts.size > 1) "$label：${parts[1]}" else label
                },
                color = Palette.TextDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        if (mission.rewards.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Tag("报酬", Palette.Green)
                Spacer(Modifier.width(8.dp))
                Text(
                    mission.rewards.joinToString("  "),
                    color = Palette.Text,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: AffinityNode,
    progress: Map<Long, Int>,
    picked: Set<Long>,
    onToggleLevel: (Long) -> Unit,
    onToggleNode: (List<Long>, Boolean) -> Unit,
    onProgress: (AffinityLevel, Int) -> Unit,
) {
    val open = pickableLevels(node, progress)
    val allPicked = open.isNotEmpty() && open.all { it.id in picked }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(node.name, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Tag(categoryLabel(node.category), Palette.Aqua)
            if (open.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    if (allPicked) "移出" else "加入",
                    color = if (allPicked) Palette.Flame else Palette.Core,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleNode(open.map { it.id }, !allPicked) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        node.levels.forEach { level ->
            LevelRow(
                level = level,
                current = progress[level.id] ?: 0,
                selected = level.id in picked,
                onToggle = { onToggleLevel(level.id) },
                onProgress = { onProgress(level, it) },
            )
        }
    }
}

private fun categoryLabel(c: String) = when (c) {
    "special" -> "必杀"
    "battle_skill" -> "战斗技能"
    "field_skill" -> "场景技能"
    "blade_skill" -> "异刃技能"
    else -> c
}

/** 需求里写的次数目标：「0/6」的 6，或 achievement_count。没有目标返回 null。 */
private fun affinityTarget(level: AffinityLevel): Int? =
    Regex("""(\d+)\s*/\s*(\d+)""").find(level.requirement)?.groupValues?.get(2)?.toIntOrNull()
        ?: level.achievementCount.takeIf { it > 0 }

/** 这条环算不算做完了：库里标了「初始习得」，或本地进度已达到需求次数。 */
private fun affinityDone(level: AffinityLevel, current: Int): Boolean {
    if (level.learned) return true
    val target = affinityTarget(level) ?: return false
    return current >= target
}

/**
 * 能在圆环页勾进计划的等级。
 *
 * 节点级「加入」、等级行圆点、顶部「第 N 层」都走这一个判断，
 * 否则会出现「加入」把已完成的行也选进去、但那一行根本没有圆点的情况。
 */
private fun pickableLevels(node: AffinityNode, progress: Map<Long, Int>): List<AffinityLevel> =
    node.levels.filter { !affinityDone(it, progress[it.id] ?: 0) }

@Composable
private fun LevelRow(
    level: AffinityLevel,
    current: Int,
    selected: Boolean,
    onToggle: () -> Unit,
    onProgress: (Int) -> Unit,
) {
    val target = affinityTarget(level)
    val value = current.coerceAtMost(target ?: Int.MAX_VALUE)
    val learned = affinityDone(level, current)

    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!learned) {
                PickDot(selected, onToggle)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "Lv${level.level}",
                color = if (learned) Palette.Done else Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(34.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(level.effect, color = Palette.Text, fontSize = 12.sp, lineHeight = 17.sp)
                Text(
                    level.requirement.ifBlank { "—" },
                    color = if (level.learned) Palette.Done else Palette.TextDim,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                if (level.links.isNotEmpty()) {
                    Text(
                        level.links.joinToString("  ") { "${it.kind}·${it.label}" },
                        color = Palette.Aqua,
                        fontSize = 10.sp,
                    )
                }
            }
            if (!level.learned && target != null) {
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$value/$target",
                        color = if (learned) Palette.Done else Palette.Core,
                        fontSize = 11.sp,
                    )
                    Row {
                        MiniButton("−") { onProgress((value - 1).coerceAtLeast(0)) }
                        Spacer(Modifier.width(4.dp))
                        MiniButton("+") { onProgress(value + 1) }
                    }
                }
            }
        }
        if (!level.learned && target != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { if (target == 0) 0f else value.toFloat() / target },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = if (learned) Palette.Done else Palette.Core,
                trackColor = Palette.CardBorder,
            )
        }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Palette.SurfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.TextDim, fontSize = 12.sp)
    }
}

/** 等级行左侧的选择圆点：选中填实心，未选只留边框。已习得等级不显示。 */
@Composable
private fun PickDot(selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (selected) Palette.Core else Color.Transparent)
            .border(1.dp, if (selected) Palette.Core else Palette.CardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Surface))
        }
    }
}

/**
 * 圆环页顶部的快捷选择：按层批量勾选、全选未习得、清空。
 * 「第 N 层」再点一次是取消该层，而不是叠加。
 */
@Composable
private fun AffinityPickBar(
    open: List<AffinityLevel>,
    picked: Set<Long>,
    onPickMany: (List<Long>, Boolean) -> Unit,
    onClear: () -> Unit,
) {
    if (open.isEmpty()) return
    val layers = open.map { it.level }.distinct().sorted()

    Text(
        "已选 ${picked.size} 级",
        color = Palette.Text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(layers) { layer ->
            val ids = open.filter { it.level == layer }.map { it.id }
            val active = ids.isNotEmpty() && ids.all { it in picked }
            PickChip("第 $layer 层", active) { onPickMany(ids, !active) }
        }
        item {
            PickChip("全选未习得", open.all { it.id in picked }) { onPickMany(open.map { it.id }, true) }
        }
        item { PickChip("清空", false) { onClear() } }
    }
}

@Composable
private fun PickChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Palette.Core.copy(alpha = 0.2f) else Palette.SurfaceHigh)
            .border(1.dp, if (active) Palette.Core else Palette.CardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (active) Palette.Core else Palette.TextDim,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
