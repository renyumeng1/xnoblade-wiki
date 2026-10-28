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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.EntityDetailData
import cn.xenoblade2.next.data.EntityRow
import cn.xenoblade2.next.data.FieldSkillRow
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.SectionCard
import cn.xenoblade2.next.ui.nav.HOME_DESC
import cn.xenoblade2.next.ui.nav.HOME_ENTRY_BY_KEY
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.delay

/** 原版 20 个档案页里，除任务 / 异刃 / 怪物 / 地图 / 素材规划之外，其余都走这里。 */
@Composable
fun ArchiveScreen(
    key: String,
    onBack: () -> Unit,
    onOpenEntity: (String, Long) -> Unit,
) {
    val entry = HOME_ENTRY_BY_KEY[key]
    if (entry == null) {
        PlaceholderArchive("未知入口", key, onBack)
        return
    }
    when {
        key == "journey" -> JourneyScreen(onBack)
        key == "fieldSkills" -> FieldSkillScreen(onBack)
        entry.entityType == null -> PlaceholderArchive(entry.title, HOME_DESC[key].orEmpty(), onBack)
        else -> EntityListScreen(
            title = entry.title,
            subtitle = HOME_DESC[key].orEmpty(),
            type = entry.entityType,
            onBack = onBack,
            onOpen = { onOpenEntity(entry.entityType, it) },
        )
    }
}

@Composable
private fun EntityListScreen(
    title: String,
    subtitle: String,
    type: String,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<EntityRow>?>(null) }
    var total by remember { mutableStateOf(0) }

    LaunchedEffect(type) {
        total = WikiRepo.io { WikiRepo.entityCountOf(type) }
    }
    LaunchedEffect(type, query) {
        delay(if (query.isBlank()) 0 else 200)
        rows = WikiRepo.io { WikiRepo.entityRows(type, query) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = title,
            subtitle = if (total > 0) "$subtitle · 共 $total 条" else subtitle,
            onBack = onBack,
        )
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
            placeholder = "在${title}中搜索",
        )
        val data = rows
        when {
            data == null -> LoadingBox()
            data.isEmpty() -> EmptyHint("没有匹配的条目")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data, key = { it.id }) { row -> EntityRowView(row) { onOpen(row.id) } }
            }
        }
    }
}

@Composable
private fun EntityRowView(row: EntityRow, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.iconPath != null) {
            AssetImage(
                path = row.iconPath,
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)),
                contentDescription = row.title,
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, color = Palette.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (row.subtitle.isNotBlank()) {
                Text(row.subtitle, color = Palette.TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = Palette.TextDim, fontSize = 18.sp)
    }
}

@Composable
private fun PlaceholderArchive(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = title, subtitle = subtitle, onBack = onBack)
        EmptyHint("这一页还在复刻中")
    }
}

@Composable
private fun JourneyScreen(onBack: () -> Unit) {
    var stats by remember { mutableStateOf<List<Pair<String, Int>>?>(null) }
    LaunchedEffect(Unit) {
        stats = WikiRepo.io {
            listOf(
                "任务" to Db.count("SELECT COUNT(*) FROM entity WHERE type='quest'"),
                "异刃" to Db.count("SELECT COUNT(*) FROM entity WHERE type='blade'"),
                "物品" to Db.count("SELECT COUNT(*) FROM entity WHERE type='item'"),
                "怪物" to Db.count("SELECT COUNT(*) FROM entity WHERE type='enemy'"),
                "采集点" to Db.count("SELECT COUNT(*) FROM entity WHERE type='collection_point'"),
                "宝箱" to Db.count("SELECT COUNT(*) FROM entity WHERE type='treasure_chest'"),
                "牵绊对话" to Db.count("SELECT COUNT(*) FROM entity WHERE type='heart_to_heart'"),
                "挑战战斗" to Db.count("SELECT COUNT(*) FROM entity WHERE type='challenge_battle'"),
            )
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "游玩经历", subtitle = "收集进度与游玩统计", onBack = onBack)
        val data = stats
        if (data == null) {
            LoadingBox()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SectionCard(title = "图鉴规模") {
                        data.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                pair.forEach { (label, value) ->
                                    Column(Modifier.weight(1f)) {
                                        Text("$value", color = Palette.Core, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                        Text(label, color = Palette.TextDim, fontSize = 11.sp)
                                    }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "收集标记") {
                        Text(
                            "原版在这里记录「已收集 N 项」。本地收集标记（异刃 / 物品 / 宝箱勾选）尚未接入，" +
                                "接入后会与首页卡片的「N 项已收集」联动。",
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
                item {
                    SectionCard(title = "进行中的计划") {
                        Text(
                            "计划与开环进度写在应用私有目录，可在首页「素材规划」查看。",
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldSkillScreen(onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<FieldSkillRow>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        val kw = query.trim()
        rows = WikiRepo.io {
            WikiRepo.fieldSkills().filter { kw.isBlank() || it.name.contains(kw) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "场景技能", subtitle = "场景技能与持有异刃", onBack = onBack)
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp),
            placeholder = "搜索场景技能",
        )
        val data = rows
        when {
            data == null -> LoadingBox()
            data.isEmpty() -> EmptyHint("没有匹配的场景技能")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(data, key = { it.name }) { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Surface)
                            .border(1.dp, Palette.CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            if (row.maxLevel > 0) {
                                Text("Lv.${row.maxLevel}", color = Palette.Gold, fontSize = 12.sp)
                            }
                        }
                        if (row.blades.isNotBlank()) {
                            Text(
                                row.blades.replace(",", "、"),
                                color = Palette.TextDim,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EntityDetailScreen(type: String, id: Long, onBack: () -> Unit) {
    var data by remember { mutableStateOf<EntityDetailData?>(null) }
    var missing by remember { mutableStateOf(false) }
    LaunchedEffect(type, id) {
        val d = WikiRepo.io { WikiRepo.entityDetail(type, id) }
        data = d
        missing = d == null
    }
    Column(Modifier.fillMaxSize()) {
        val d = data
        ScreenHeader(
            title = d?.nameZh?.takeIf { it.isNotBlank() } ?: d?.title ?: "载入中…",
            subtitle = typeLabel(type),
            onBack = onBack,
        )
        when {
            missing -> EmptyHint("没有找到这条记录")
            d == null -> LoadingBox()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (d.title.isNotBlank() && d.title != d.nameZh) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Palette.Surface)
                                .border(1.dp, Palette.CardBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(d.title, color = Palette.Text, fontSize = 14.sp)
                        }
                    }
                }
                item {
                    SectionCard(title = "数据") {
                        if (d.fields.isEmpty()) {
                            Text("这条记录没有结构化字段", color = Palette.TextDim, fontSize = 12.sp)
                        }
                        d.fields.forEach { (k, v) -> FieldRow(k, v) }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 11.sp)
        Text(
            value.replace("<br />", "\n").replace("<br/>", "\n"),
            color = Palette.Text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

private fun typeLabel(type: String) = when (type) {
    "item" -> "物品"
    "weapon" -> "武器"
    "location" -> "地点"
    "shop" -> "商店"
    "heart_to_heart" -> "牵绊对话"
    "poppiswap_part" -> "花的零件"
    "challenge_battle" -> "挑战战斗"
    "weather" -> "天气"
    "driver" -> "御刃者"
    "merc_mission" -> "佣兵团任务"
    "collection_point" -> "采集点"
    "salvage_point" -> "打捞点"
    "treasure_chest" -> "宝箱"
    "npc" -> "NPC"
    else -> type
}
