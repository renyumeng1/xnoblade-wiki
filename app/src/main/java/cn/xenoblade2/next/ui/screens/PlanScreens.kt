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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.Plan
import cn.xenoblade2.next.plan.PlanStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.RouteGuide
import cn.xenoblade2.next.plan.StepHop
import cn.xenoblade2.next.ui.components.ActionRow
import cn.xenoblade2.next.ui.components.cleanStepDetail
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.components.WikiShotPanel
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun PlanListScreen(onOpen: (Long) -> Unit, onCreateQuestPlan: () -> Unit) {
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(version) {
        plans = WikiRepo.io { PlanStore.plans() }
    }

    Column(Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "计划",
            subtitle = "${plans.size} 份 · 打勾推进，按地图连点走",
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionRow("从任务生成") { onCreateQuestPlan() }
            ActionRow("空白计划") {
                scope.launch {
                    WikiRepo.io { PlanStore.createPlan("手动计划", "manual", "") }
                    version++
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (plans.isEmpty()) {
            EmptyHint("还没有计划。\n任务详情页可以「生成地图计划」，异刃页可以「生成开环计划」。")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Surface)
                            .clickable { onOpen(plan.id) }
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                plan.title,
                                color = Palette.Text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Tag(sourceLabel(plan.sourceType), sourceColor(plan.sourceType))
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            WikiRepo.io { PlanStore.deletePlan(plan.id) }
                                            version++
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "删除",
                                    tint = Palette.TextDim,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { plan.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = Palette.Core,
                                trackColor = Palette.CardBorder,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${plan.doneCount}/${plan.stepCount}", color = Palette.TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun sourceLabel(type: String) = when (type) {
    "quest" -> "任务"
    "blade" -> "开环"
    "map" -> "选点"
    else -> "手动"
}

private fun sourceColor(type: String) = when (type) {
    "quest" -> Palette.Gold
    "blade" -> Palette.Core
    "map" -> Palette.Aqua
    else -> Palette.TextDim
}

@Composable
fun PlanDetailScreen(planId: Long, onBack: () -> Unit, onOpenMap: (String) -> Unit) {
    var plan by remember { mutableStateOf<Plan?>(null) }
    var steps by remember { mutableStateOf<List<PlanStep>>(emptyList()) }
    var hops by remember { mutableStateOf<Map<Long, StepHop>>(emptyMap()) }
    var version by remember { mutableStateOf(0) }
    // 路线规划（Beta）关闭时不计算逐段方位。
    val routeBeta = Prefs.routeBeta
    val scope = rememberCoroutineScope()

    LaunchedEffect(planId, version, routeBeta) {
        val loaded = WikiRepo.io {
            val s = PlanStore.steps(planId)
            Triple(
                PlanStore.plans().firstOrNull { it.id == planId },
                s,
                if (routeBeta) RouteGuide.planHops(s) else emptyMap(),
            )
        }
        plan = loaded.first
        steps = loaded.second
        hops = loaded.third
    }

    Column(Modifier.fillMaxWidth()) {
        val done = steps.count { it.done }
        ScreenHeader(
            title = plan?.title ?: "计划",
            subtitle = if (steps.isEmpty()) "空计划" else buildString {
                append("$done/${steps.size} 已完成 · ${steps.count { it.pinned }} 步已定位")
                val coarse = steps.count { !it.pinned && it.mapId != null }
                if (coarse > 0) append(" · $coarse 步仅到图")
                if (hops.isNotEmpty()) append(" · ${hops.size} 跳有方位")
            },
            onBack = onBack,
            actions = {
                // 优先打开「已定位步骤最多」的那张图，而不是单纯第一条。
                // 分层地图（上层/下层/腹部…）天然分散，先把有坐标的一层打开更有用。
                val targetMapId = steps
                    .filter { it.mapId != null }
                    .groupBy { it.mapId!! }
                    .maxByOrNull { (_, list) -> list.count { it.pinned } * 1000 + list.size }
                    ?.key
                if (targetMapId != null) {
                    Text(
                        "看地图",
                        color = Palette.Core,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onOpenMap(targetMapId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            },
        )

        if (steps.isEmpty()) {
            EmptyHint("这份计划还没有步骤")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(steps, key = { it.id }) { step ->
                    StepRow(
                        step = step,
                        hop = hops[step.id],
                        onToggle = {
                            scope.launch {
                                WikiRepo.io { PlanStore.toggleStep(step.id, !step.done) }
                                version++
                            }
                        },
                        onOpenMap = { step.mapId?.let(onOpenMap) },
                        onDelete = {
                            scope.launch {
                                WikiRepo.io { PlanStore.deleteStep(step.id) }
                                version++
                            }
                        },
                        onMove = { up ->
                            scope.launch {
                                WikiRepo.io { PlanStore.moveStep(step.id, up) }
                                version++
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    step: PlanStep,
    hop: StepHop?,
    onToggle: () -> Unit,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧时间轴：序号 + 细竖线。不再用圆圈，避免和地图点位抢视觉。
        Column(
            Modifier
                .width(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (step.done) "✓" else "%02d".format(step.ord),
                color = if (step.done) Palette.Done else Palette.TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .width(2.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (step.done) Palette.Done.copy(alpha = 0.45f) else Palette.CardBorder),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.title,
                color = if (step.done) Palette.TextDim else Palette.Text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textDecoration = if (step.done) TextDecoration.LineThrough else null,
            )
            if (step.detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(cleanStepDetail(step.detail), color = Palette.TextDim, fontSize = 11.sp, lineHeight = 16.sp)
            }
            if (!step.shot.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                WikiShotPanel(step.shot)
            }
            hop?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        if (it.distance < 1.0) {
                            append("路线：与第 ${it.fromOrd} 步是同一位置")
                        } else {
                            append("路线：从第 ${it.fromOrd} 步过来 ${it.compass} 约 ${"%.0f".format(it.distance)}")
                        }
                        if (it.skipped > 0) append("（跳过 ${it.skipped} 步无坐标）")
                        it.via?.let { v -> append(" · 经 $v") }
                        if (it.legs > 1) append(" · ${it.legs} 段")
                    },
                    color = if (step.done) Palette.TextDim else Palette.Core.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
            if (step.mapId != null) {
                val area = WikiRepo.map(step.mapId)?.area?.takeIf { it.isNotBlank() } ?: "地图"
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Palette.Core.copy(alpha = if (step.pinned) 0.12f else 0.06f))
                            .clickable(onClick = onOpenMap)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (step.pinned) "地图 · $area" else "地图 · $area（无坐标）",
                            color = Palette.Core,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "↑",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onMove(true) }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "↓",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onMove(false) }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "删除",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        "未定位",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        "↑",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onMove(true) }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "↓",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onMove(false) }.padding(horizontal = 4.dp),
                    )
                    Text(
                        "删除",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable(onClick = onDelete).padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}
