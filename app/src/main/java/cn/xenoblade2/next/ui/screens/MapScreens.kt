package cn.xenoblade2.next.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.MapInfo
import cn.xenoblade2.next.data.MapMath
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.data.WikiShots
import cn.xenoblade2.next.plan.NewStep
import cn.xenoblade2.next.plan.PlanStep
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.RouteGraph
import cn.xenoblade2.next.plan.RouteGuide
import cn.xenoblade2.next.plan.RouteLeg
import cn.xenoblade2.next.plan.RouteResult
import cn.xenoblade2.next.ui.components.AssetImage
import cn.xenoblade2.next.ui.components.EmptyHint
import cn.xenoblade2.next.ui.components.MARKER_FILTER_DEFAULTS
import cn.xenoblade2.next.ui.components.FAVORITE_FILTER_KEY
import cn.xenoblade2.next.ui.components.MARKER_FILTERS
import cn.xenoblade2.next.ui.components.MarkerFilterGlyph
import cn.xenoblade2.next.ui.components.WikiShotPanel
import cn.xenoblade2.next.ui.components.markerFilterColor
import cn.xenoblade2.next.ui.components.markerFilterKey
import cn.xenoblade2.next.ui.components.LoadingBox
import cn.xenoblade2.next.ui.components.MarkerGlyphBadge
import cn.xenoblade2.next.ui.components.OverlayPin
import cn.xenoblade2.next.ui.components.MapCanvas
import cn.xenoblade2.next.ui.components.ScreenHeader
import cn.xenoblade2.next.ui.components.Tag
import cn.xenoblade2.next.ui.components.markerColor
import cn.xenoblade2.next.ui.components.markerTypeLabel
import cn.xenoblade2.next.ui.components.rememberMapViewState
import cn.xenoblade2.next.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun MapListScreen(onOpen: (String) -> Unit) {
    var regions by remember { mutableStateOf<List<Pair<String, List<MapInfo>>>>(emptyList()) }
    var region by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        regions = WikiRepo.io { WikiRepo.interactiveRegions() }
    }

    // 两级：地区 → 该地区的地图。同一地区的层在地区页顶部用标签切换。
    BackHandler(enabled = region != null) { region = null }

    val r = region
    when {
        regions.isEmpty() -> Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "交互地图")
            LoadingBox(Modifier.height(240.dp))
        }

        r == null -> MapRegionList(
            regions = regions,
            onOpenRegion = { region = it },
        )

        else -> {
            MapRegionLayers(
                region = r,
                maps = regions.first { it.first == r }.second,
                onBack = { region = null },
                onOpen = onOpen,
            )
        }
    }
}

/** 一级：地区列表，纯文字行，不再用 Titan 插画。 */
@Composable
private fun MapRegionList(
    regions: List<Pair<String, List<MapInfo>>>,
    onOpenRegion: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "地图",
            subtitle = "${regions.size} 个地区 · ${regions.sumOf { it.second.size }} 张地图",
        )
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(regions, key = { it.first }) { (name, maps) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Surface)
                        .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onOpenRegion(name) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Palette.Core.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${maps.size} 张地图 · ${maps.map { it.area }.distinct().size} 个区域",
                            color = Palette.TextDim,
                            fontSize = 12.sp,
                        )
                    }
                    Text("›", color = Palette.TextDim, fontSize = 20.sp)
                }
            }
        }
    }
}

/** 二级：地区内所有层。顶部标签切换，下面显示当前层的预览。 */
@Composable
private fun MapRegionLayers(
    region: String,
    maps: List<MapInfo>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var selected by remember(maps) { mutableStateOf(maps.firstOrNull()?.id) }
    val current = maps.firstOrNull { it.id == selected } ?: maps.firstOrNull()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = region,
            subtitle = "${maps.size} 张地图 · 切换层查看",
            onBack = onBack,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(maps, key = { it.id }) { m ->
                MapLayerChip(
                    text = layerLabel(m, maps),
                    active = m.id == current?.id,
                    onClick = { selected = m.id },
                )
            }
        }
        if (current != null) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                MapPreviewCard(
                    map = current,
                    layerTitle = layerLabel(current, maps),
                    onOpen = { onOpen(current.id) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "点地图或右上角按钮进入全屏。全屏里仍可切换同地区的其它层。",
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MapLayerChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) Palette.Core else Palette.Surface)
            .border(1.dp, if (active) Palette.Core else Palette.CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            color = if (active) Color.White else Palette.TextDim,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** 一级：地区目录。右侧用 Titan 插画，卡片样式对齐原版 `MapDirectoryCard`。 */
@Composable
private fun MapRegionDirectory(
    regions: List<Pair<String, List<MapInfo>>>,
    onOpenRegion: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "交互地图",
            subtitle = "${regions.size} 个地区 · ${regions.sumOf { it.second.size }} 张地图",
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(regions, key = { it.first }) { (name, maps) ->
                MapDirectoryCard(
                    title = name,
                    subtitle = "${maps.size} 张地图",
                    subtitleColor = regionAccent(name),
                    accent = regionAccent(name),
                    artwork = regionArtwork(name),
                ) { onOpenRegion(name) }
            }
        }
    }
}

/** 二级：地区内按 menu_group（Area）分组。缩略图用该 Area 第一张地图。 */
@Composable
private fun MapAreaDirectory(
    region: String,
    maps: List<MapInfo>,
    onBack: () -> Unit,
    onOpenArea: (String) -> Unit,
) {
    val areas = remember(maps) { maps.groupBy { it.area }.toList() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = region,
            subtitle = "${areas.size} 个区域 · ${maps.size} 张地图",
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(areas, key = { it.first }) { (area, list) ->
                MapDirectoryCard(
                    title = area,
                    subtitle = "${list.size} 张地图",
                    subtitleColor = areaAccent(area),
                    accent = areaAccent(area),
                    artwork = list.first().imagePath,
                ) { onOpenArea(area) }
            }
        }
    }
}

/** 三级：Area 内的地图预览卡片，右上角圆形按钮进全屏交互地图。 */
@Composable
private fun MapAreaMaps(
    region: String,
    area: String,
    maps: List<MapInfo>,
    allMaps: List<MapInfo>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = area,
            subtitle = "$region · ${maps.size} 张地图",
            onBack = onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(maps, key = { it.id }) { map ->
                MapPreviewCard(
                    map = map,
                    layerTitle = layerLabel(map, allMaps),
                    onOpen = { onOpen(map.id) },
                )
            }
        }
    }
}

/**
 * 原版 `MapDirectoryCard`（InteractiveMapScreensKt.java:2397）：
 * 112dp 高、16dp 圆角、卡片 92% 不透明、强调色 15% 铺层；
 * 右侧 188dp 宽插画贴边裁切；内层 Row 左右 18dp；
 * 竖条 4×60dp 圆角 3dp，标题 24sp Bold，副标题 12sp SemiBold。
 */
@Composable
private fun MapDirectoryCard(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    accent: Color,
    artwork: String?,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface.copy(alpha = 0.92f))
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        // 强调色薄薄铺一层，保持原版「白卡 + 15% 主题色」的观感
        Box(Modifier.matchParentSize().background(accent.copy(alpha = 0.15f)))

        if (!artwork.isNullOrBlank()) {
            AssetImage(
                path = artwork,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(188.dp)
                    .fillMaxHeight(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
            )
        }

        Row(
            Modifier.fillMaxSize().padding(end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(20.dp))
            Box(
                Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    title,
                    color = Palette.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    color = subtitleColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Area 内单张地图的预览卡：地图缩略图 + 点位圆点 + 右上角展开按钮。 */
@Composable
private fun MapPreviewCard(
    map: MapInfo,
    layerTitle: String,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${map.region}·${map.area}",
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (layerTitle.isBlank()) "${map.markerCount} 个点位" else "$layerTitle · ${map.markerCount} 个点位",
                    color = areaAccent(map.area),
                    fontSize = 10.sp,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xF2FFFFFF))
                    .border(1.dp, Color(0x1F000000), CircleShape)
                    .clickable(onClick = onOpen),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.OpenInFull,
                    contentDescription = "打开大图",
                    tint = Color(0xFF1A1C1F),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        MapPreview(
            map = map,
            markers = emptyList(),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen),
        )
    }
}

/** 只读小地图：Fit 居中显示图片，并按同一坐标变换叠点位圆点。 */
@Composable
private fun MapPreview(map: MapInfo, markers: List<Marker>, modifier: Modifier) {
    Box(modifier.background(Color(0xFF0B1018)), contentAlignment = Alignment.Center) {
        AssetImage(
            path = map.imagePath,
            modifier = Modifier.fillMaxSize(),
            contentDescription = map.area,
        )
        Canvas(Modifier.fillMaxSize()) {
            val imgAspect = map.aspect
            val viewAspect = size.width / size.height
            val w = if (imgAspect > viewAspect) size.width else size.height * imgAspect
            val h = if (imgAspect > viewAspect) size.width / imgAspect else size.height
            val left = (size.width - w) / 2f
            val top = (size.height - h) / 2f
            markers.forEach { m ->
                val n = MapMath.normalized(map, m.frontierX, m.frontierY) ?: return@forEach
                drawCircle(
                    color = markerFilterColor(markerFilterKey(m)),
                    radius = 2.4.dp.toPx(),
                    center = Offset(left + n.first * w, top + n.second * h),
                )
            }
        }
    }
}

/** 原版 `regionArtwork()`：15 个地区各有一张 Titan 插画（从 1.2.2 原包提取）。 */
private val REGION_ARTWORK = mapOf(
    "阿伐利缇亚商会" to "regions/argentum-trade-guild.webp",
    "云海探查船 涡潮号" to "regions/c-s-e-v-maelstrom.webp",
    "古代船" to "regions/ancient-ship.webp",
    "古拉" to "regions/gormott-province.webp",
    "英维迪亚烈王国" to "regions/kingdom-of-uraya.webp",
    "斯佩比亚帝国" to "regions/empire-of-mor-ardain.webp",
    "檀佩兰提亚" to "regions/temperantia.webp",
    "阿卡狄亚教廷" to "regions/indoline-praetorium.webp",
    "洛修利亚王国" to "regions/kingdom-of-tantal.webp",
    "利贝拉里塔斯岛屿群" to "regions/leftherian-archipelago.webp",
    "艾尔皮斯灵洞" to "regions/spirit-crucible-elpys.webp",
    "莫尔斯断崖" to "regions/cliffs-of-morytha.webp",
    "莫尔斯之地" to "regions/land-of-morytha.webp",
    "世界树" to "regions/world-tree.webp",
    "第一低轨道站" to "regions/first-low-orbit-station.webp",
)

private fun regionArtwork(region: String): String? = REGION_ARTWORK[region]

/** 原版 `regionAccent()`。 */
private fun regionAccent(region: String): Color = when (region) {
    "第一低轨道站" -> Color(0xFF5EAFA5)
    "洛修利亚王国" -> Color(0xFF4385D4)
    "艾尔皮斯灵洞" -> Color(0xFF5B5DD1)
    "利贝拉里塔斯岛屿群" -> Color(0xFF008F83)
    "云海探查船 涡潮号", "世界树" -> Color(0xFF56EBFF)
    "古拉" -> Color(0xFF2F9348)
    "古代船" -> Color(0xFF667891)
    "檀佩兰提亚" -> Color(0xFF2E3D4F)
    "斯佩比亚帝国" -> Color(0xFFD40011)
    "阿卡狄亚教廷" -> Color(0xFFD49A05)
    "莫尔斯之地" -> Color(0xFF66514F)
    "莫尔斯断崖" -> Color(0xFF808080)
    "英维迪亚烈王国" -> Color(0xFF7653B5)
    else -> Color(0xFFFFC107)
}

/** 原版 `areaAccent()`：按 area 名哈希从 6 色里取。 */
private fun areaAccent(area: String): Color {
    val palette = listOf(
        Color(0xFF56EBFF),
        Color(0xFFFFC107),
        Color(0xFFD40011),
        Color(0xFF2F9348),
        Color(0xFF7653B5),
        Color(0xFF008F83),
    )
    return palette[Math.floorMod(area.hashCode(), palette.size)]
}

@Composable
fun MapDetailScreen(
    mapId: String,
    planId: Long?,
    onBack: () -> Unit,
    onSwitchMap: (String) -> Unit,
    onOpenPlan: (Long) -> Unit,
) {
    var map by remember(mapId) { mutableStateOf(WikiRepo.map(mapId)) }
    var markers by remember(mapId) { mutableStateOf<List<Marker>>(emptyList()) }
    var planSteps by remember(mapId, planId) { mutableStateOf<List<PlanStep>>(emptyList()) }
    var allSteps by remember(mapId, planId) { mutableStateOf<List<PlanStep>>(emptyList()) }
    // 筛选按原版 `MarkerFilterBar`：全局记忆（interactive_map_settings），换图也保留
    var visibleFilters by remember(mapId) { mutableStateOf(Prefs.mapFilterKeys ?: MARKER_FILTER_DEFAULTS) }
    // 原版「显示已收藏」：关掉后隐藏已收藏的点位
    var showsMarked by remember(mapId) { mutableStateOf(Prefs.mapShowsMarked) }
    var selected by remember(mapId) { mutableStateOf<Marker?>(null) }
    var selectedPin by remember(mapId) { mutableStateOf<OverlayPin?>(null) }
    var siblings by remember(mapId) { mutableStateOf<List<MapInfo>>(emptyList()) }
    // 计划地图默认「只看计划点 + 最近传送点/地标」，避免几百个无关点位糊满屏
    var focusMode by remember(mapId, planId) { mutableStateOf(planId != null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var filterMenu by remember { mutableStateOf(false) }
    var pendingPin by remember(mapId) { mutableStateOf<Pair<Double, Double>?>(null) }
    var pinTitle by remember(mapId) { mutableStateOf("") }
    var bindStepId by remember(mapId) { mutableStateOf<Long?>(null) }
    var route by remember(mapId, planId) { mutableStateOf<RouteResult?>(null) }
    var guideLegs by remember(mapId, planId) { mutableStateOf<List<RouteLeg>>(emptyList()) }
    var legIndex by remember(mapId, planId) { mutableIntStateOf(0) }
    var showRoute by remember(mapId) { mutableStateOf(true) }
    val viewState = rememberMapViewState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(mapId) {
        val loaded = WikiRepo.io {
            val ms = WikiRepo.interactiveMarkers(mapId)
            val info = WikiRepo.map(mapId)
            // 同一地区内的分层图都可以切换（上层右半身 / 上层左半身 / 下层 / 战舰…）。
            // 只按 Area（menu_group）分组会漏掉古拉这种「不同 Area 其实是同一张巨神兽的层」。
            val layers = info?.let { i ->
                WikiRepo.maps().filter {
                    it.region == i.region &&
                        it.imagePath.isNotBlank() && it.markerCount > 0
                }
            } ?: emptyList()
            ms to layers
        }
        markers = loaded.first
        siblings = loaded.second
        map = WikiRepo.map(mapId)
    }

    LaunchedEffect(mapId, planId) {
        if (planId == null) {
            planSteps = emptyList()
            allSteps = emptyList()
            return@LaunchedEffect
        }
        val loaded = WikiRepo.io { PlanStore.steps(planId) }
        allSteps = loaded
        planSteps = loaded.filter { it.mapId == mapId }
    }

    // 计划地图的路线：以「最近的传送点/门（没有就用地标）」为起点，再串起本图所有已定位步骤。
    // 只有一个步骤也要画，否则用户不知道从哪个点开始走。
    LaunchedEffect(mapId, planSteps, markers, map) {
        // 坐标落在本图范围外的步骤画不上去（一个点都画不出来的图也没法连路线），先剔掉
        val info = map
        val pinned = planSteps
            .filter {
                it.pinned && it.fx != null && it.fy != null &&
                    (info == null || MapMath.normalized(info, it.fx, it.fy) != null)
            }
            .sortedBy { it.ord }
        if (pinned.isEmpty()) {
            route = null
            guideLegs = emptyList()
            legIndex = 0
            return@LaunchedEffect
        }
        val first = pinned.first()
        val sx = first.fx ?: 0.0
        val sy = first.fy ?: 0.0
        val anchor = markers
            .filter { it.targetType == "RouteAccessPoint" }
            .ifEmpty { markers.filter { it.targetType == "Location" } }
            .minByOrNull { m ->
                val dx = m.frontierX - sx
                val dy = m.frontierY - sy
                dx * dx + dy * dy
            }
        val waypoints = ArrayList<Triple<String?, Double, Double>>()
        anchor?.let { a ->
            val dx = a.frontierX - sx
            val dy = a.frontierY - sy
            // 和第一步几乎重合就别多切一段
            if (dx * dx + dy * dy > 1.0) waypoints.add(Triple(a.featureId, a.frontierX, a.frontierY))
        }
        pinned.forEach { waypoints.add(Triple(it.featureId, it.fx ?: 0.0, it.fy ?: 0.0)) }
        val r = if (waypoints.size < 2) null else WikiRepo.io { RouteGraph.routeThrough(mapId, waypoints) }
        route = r
        // 逐段导航：把折线切成「起点 → 地标 → 终点」，默认停在第一段。
        // 所有计划点都落在同一个 marker 上时（distance≈0）没有可走的段，直接不显示卡片。
        guideLegs = if (r == null || r.distance < 1.0) {
            emptyList()
        } else {
            val startTitle = if (waypoints.size > pinned.size) anchor?.let { markerDisplayName(it) } else first.title
            RouteGuide.legs(r, startTitle, pinned.last().title)
        }
        legIndex = 0
    }

    // 计划地图的参考点位：每个已定位步骤挑最近的「传送点/门」+ 最近的「地标」，其余一律不画
    val focusMarkers = remember(markers, planSteps, planId, map) {
        if (planId == null || markers.isEmpty()) {
            emptyList()
        } else {
            val picked = LinkedHashSet<String>()
            val info = map
            for (s in planSteps.filter { it.pinned }) {
                val sx = s.fx ?: continue
                val sy = s.fy ?: continue
                // 坐标不在本图范围内的步骤画不上去，别拿它去算参考点位
                if (info != null && MapMath.normalized(info, sx, sy) == null) continue
                fun nearest(types: Set<String>) = markers
                    .filter { it.targetType in types }
                    .minByOrNull { m ->
                        val dx = m.frontierX - sx
                        val dy = m.frontierY - sy
                        dx * dx + dy * dy
                    }
                nearest(setOf("RouteAccessPoint"))?.let { picked.add(it.featureId) }
                nearest(setOf("Location"))?.let { picked.add(it.featureId) }
            }
            markers.filter { it.featureId in picked }
        }
    }

    val m = map
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = m?.area ?: mapId,
            subtitle = m?.let {
                buildString {
                    append(it.region).append(" · ")
                    val dup = siblings.count { s -> s.area == it.area } > 1
                    if (dup && it.landmark.isNotBlank()) {
                        append(it.area).append(" · ").append(it.landmark)
                    } else {
                        append(it.area)
                    }
                    append(" · ${markers.size} 个点位")
                    if (planId != null) {
                        append(" · 本层 ${planSteps.size} 步")
                        val other = allSteps.size - planSteps.size
                        if (other > 0) append(" · 其他层 $other 步")
                    }
                }
            },
            onBack = onBack,
        )

        // 原版 `InteractiveMapDetailScreen`：左上「图标筛选」12sp 灰字，右上 48dp 圆形按钮 + DropdownMenu。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "图标筛选",
                color = Palette.TextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Palette.Surface.copy(alpha = 0.92f))
                        .border(1.dp, Color(0x1F000000), CircleShape)
                        .clickable { filterMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.FilterList,
                        contentDescription = "图标筛选",
                        tint = Palette.Text,
                        modifier = Modifier.size(22.dp),
                    )
                }
                DropdownMenu(
                    expanded = filterMenu,
                    onDismissRequest = { filterMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("已收藏", fontSize = 13.sp) },
                        leadingIcon = {
                            MarkerFilterGlyph(FAVORITE_FILTER_KEY, markerFilterColor(FAVORITE_FILTER_KEY), 16.dp)
                        },
                        trailingIcon = {
                            if (showsMarked) Icon(Icons.Rounded.Check, null, tint = Palette.Core, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showsMarked = !showsMarked
                            Prefs.applyMapShowsMarked(showsMarked)
                        },
                    )
                    MARKER_FILTERS.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label, fontSize = 13.sp) },
                            leadingIcon = { MarkerFilterGlyph(f.key, f.color, 16.dp) },
                            trailingIcon = {
                                if (f.key in visibleFilters) {
                                    Icon(Icons.Rounded.Check, null, tint = Palette.Core, modifier = Modifier.size(18.dp))
                                }
                            },
                            onClick = {
                                visibleFilters = if (f.key in visibleFilters) visibleFilters - f.key else visibleFilters + f.key
                                Prefs.applyMapFilters(visibleFilters)
                            },
                        )
                    }
                }
            }
        }

        // 同一地区常常分「上层 / 中层 / 下层 / 头部 / 腹部 …」，切层入口必须一直看得见，
        // 否则计划步骤落在别的层上时会被误以为「没规划」。
        if (siblings.size > 1 || planId != null) {
            val stepCounts = allSteps.groupingBy { it.mapId ?: "" }.eachCount()
            // 有计划步骤的层排前面，免得要横滑半天才找到真正要去的层
            val ordered = siblings.sortedWith(
                compareByDescending<MapInfo> { (stepCounts[it.id] ?: 0) > 0 }
                    .thenByDescending { it.id == mapId }
                    .thenByDescending { it.markerCount },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (planId != null) {
                    item(key = "focus") {
                        val on = focusMode
                        Text(
                            if (on) "只看计划点" else "显示全部点位",
                            color = if (on) Palette.Core else Palette.TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) Palette.Core.copy(alpha = 0.14f) else Palette.SurfaceHigh)
                                .border(
                                    1.dp,
                                    if (on) Palette.Core.copy(alpha = 0.5f) else Palette.CardBorder,
                                    RoundedCornerShape(14.dp),
                                )
                                .clickable { focusMode = !on }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                items(ordered, key = { it.id }) { s ->
                    val on = s.id == mapId
                    val steps = stepCounts[s.id] ?: 0
                    Text(
                        buildString {
                            append(layerLabel(s, siblings))
                            if (steps > 0) append(" · $steps 步")
                        },
                        color = if (on) Palette.Core else Palette.TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (on) Palette.Core.copy(alpha = 0.14f) else Palette.SurfaceHigh)
                            .border(
                                1.dp,
                                if (on) Palette.Core.copy(alpha = 0.5f) else Palette.CardBorder,
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { if (!on) onSwitchMap(s.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        if (m == null) {
            LoadingBox()
            return@Column
        }

        // 放大后地图会按缩放画到画布边界之外（Compose 默认不裁切），会把上面的标题盖掉，
        // 这里显式裁到地图区域。
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Surface)
                .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
                .clipToBounds(),
        ) {
            MapCanvas(
                map = m,
                viewState = viewState,
                // 原版「显示已收藏」关掉时，已收藏的点位不画
                markers = (if (focusMode) focusMarkers else markers)
                    .filter { showsMarked || !Prefs.isMarkedKey(markerMarkKey(it)) },
                visibleTypes = if (focusMode) focusMarkers.map { markerFilterKey(it) }.toSet() else visibleFilters,
                overlay = planSteps
                    .filter { s ->
                        val fx = s.fx
                        val fy = s.fy
                        s.pinned && fx != null && fy != null && MapMath.normalized(m, fx, fy) != null
                    }
                    .map { step ->
                        OverlayPin(
                            key = "step-${step.id}",
                            fx = step.fx ?: 0.0,
                            fy = step.fy ?: 0.0,
                            label = step.title,
                            color = if (step.done) Palette.Done else Palette.Core,
                            // 用步骤本身的序号，别用列表下标——合并显示时要能对上计划里的「第 N 步」。
                            badge = step.ord.toString(),
                            dim = step.done,
                        )
                    },
                selectedKey = selectedPin?.key,
                onTapMarker = { selected = it; selectedPin = null },
                onTapOverlay = { selectedPin = it; selected = null },
                onTapEmpty = { selected = null; selectedPin = null },
                modifier = Modifier.fillMaxSize(),
                onLongPressAt = { fx, fy ->
                    selected = null
                    selectedPin = null
                    pinTitle = ""
                    bindStepId = null
                    pendingPin = fx to fy
                },
                routePath = if (showRoute) route?.points?.map { it.fx to it.fy } ?: emptyList() else emptyList(),
                highlightPath = if (showRoute) guideLegs.getOrNull(legIndex)?.path ?: emptyList() else emptyList(),
            )

            // 路线估算开关 + 逐段导航
            route?.let { r ->
                Column(
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showRoute) Color(0xFF15304A) else Color(0xFF101720))
                            .clickable { showRoute = !showRoute }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when {
                                !showRoute -> "显示路线"
                                r.distance < 1.0 -> "计划点重合"
                                else -> "路线估算 ${"%.0f".format(r.distance)}"
                            },
                            color = if (showRoute) Color(0xFF9BD4FF) else Palette.TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (r.fallback) "直线回退" else "${"%.2f".format(r.detour)}×",
                            color = Palette.TextDim,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            // 缩放控件
            Column(
                Modifier.align(Alignment.CenterEnd).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RoundIcon(Icons.Filled.Add) { viewState.zoomBy(1.5f) }
                RoundIcon(Icons.Filled.Remove) { viewState.zoomBy(1f / 1.5f) }
                RoundIcon(Icons.Filled.CenterFocusStrong) { viewState.reset() }
            }

            // 底部信息卡
            val info = selected
            if (info != null) {
                MarkerCard(
                    marker = info,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    onAddToPlan = {
                        scope.launch {
                            val targetPlan = planId ?: WikiRepo.io {
                                PlanStore.createPlan("地图选点 · ${m.area}", "map", m.id)
                            }
                            WikiRepo.io {
                                PlanStore.addStep(
                                    targetPlan,
                                    NewStep(
                                        title = info.targetName,
                                        detail = "${m.region} · ${m.area}（${markerTypeLabel(info.targetType)}）",
                                        mapId = m.id,
                                        fx = info.frontierX,
                                        fy = info.frontierY,
                                        featureId = info.featureId,
                                    ),
                                )
                            }
                            toast = "已加入计划 #$targetPlan"
                            if (planId == null) onOpenPlan(targetPlan)
                        }
                    },
                )
            }
            val pin = selectedPin
            if (pin != null) {
                PinCard(pin, Modifier.align(Alignment.BottomCenter).padding(10.dp))
            }
            toast?.let {
                Text(
                    it,
                    color = Palette.Bg,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.Core)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // 逐段导航放到地图下方：放在图上会盖住一大块路线。
        if (showRoute) {
            guideLegs.getOrNull(legIndex)?.let { leg ->
                GuidanceBar(
                    leg = leg,
                    index = legIndex,
                    total = guideLegs.size,
                    onPrev = { if (legIndex > 0) legIndex-- },
                    onNext = { if (legIndex < guideLegs.size - 1) legIndex++ },
                )
            }
        }

        // 图层
        if (focusMode) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (focusMarkers.isEmpty()) {
                    Text("这张图没有找到传送点/地标，只显示计划点", color = Palette.TextDim, fontSize = 11.sp)
                } else {
                    Text("参考点位", color = Palette.TextDim, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                    focusMarkers.take(6).forEach { fm ->
                        MarkerGlyphBadge(fm.targetType, 15.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            markerDisplayName(fm),
                            color = Palette.Text,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 92.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
        }

        val pending = pendingPin
    if (pending != null) {
        val unpinned = if (planId != null) allSteps.filter { !it.pinned } else emptyList()
        val binding = bindStepId != null
        AlertDialog(
            onDismissRequest = { pendingPin = null; bindStepId = null },
            containerColor = Palette.Surface,
            title = { Text(if (binding) "钉到这个步骤" else "在此处钉一个步骤", color = Palette.Text) },
            text = {
                Column {
                    Text(
                        String.format("%s · %.1f, %.1f", map?.area ?: mapId, pending.first, pending.second),
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                    )
                    if (unpinned.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "绑定到计划 #$planId 里还没定位的步骤（可选）",
                            color = Palette.TextDim,
                            fontSize = 11.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState())) {
                            unpinned.forEach { step ->
                                val on = step.id == bindStepId
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (on) Palette.Core.copy(alpha = 0.16f) else Palette.SurfaceHigh)
                                        .clickable { bindStepId = if (on) null else step.id }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        step.title,
                                        color = if (on) Palette.Core else Palette.Text,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (!binding) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Palette.Bg)
                                .border(1.dp, Palette.CardBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            if (pinTitle.isEmpty()) {
                                Text("新步骤标题（可留空）", color = Palette.TextDim, fontSize = 13.sp)
                            }
                            BasicTextField(
                                value = pinTitle,
                                onValueChange = { pinTitle = it },
                                textStyle = TextStyle(color = Palette.Text, fontSize = 13.sp),
                                cursorBrush = SolidColor(Palette.Core),
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val point = pendingPin ?: return@TextButton
                    val bind = bindStepId
                    val title = pinTitle.ifBlank { "自定义点位" }
                    val boundTitle = unpinned.firstOrNull { it.id == bind }?.title
                    pendingPin = null
                    bindStepId = null
                    scope.launch {
                        if (bind != null && planId != null) {
                            WikiRepo.io { PlanStore.setStepPoint(bind, mapId, point.first, point.second) }
                            val refreshed = WikiRepo.io { PlanStore.steps(planId) }
                            allSteps = refreshed
                            planSteps = refreshed.filter { it.mapId == mapId }
                            toast = "已钉到「${boundTitle?.take(14) ?: "步骤"}」"
                        } else {
                            val targetPlan = planId ?: WikiRepo.io {
                                PlanStore.createPlan("地图选点 · ${map?.area ?: mapId}", "map", mapId)
                            }
                            WikiRepo.io {
                                PlanStore.addStep(
                                    targetPlan,
                                    NewStep(
                                        title = title,
                                        detail = "${map?.region ?: ""} · ${map?.area ?: mapId}（手动钉点）",
                                        mapId = mapId,
                                        fx = point.first,
                                        fy = point.second,
                                    ),
                                )
                            }
                            val refreshed = WikiRepo.io { PlanStore.steps(targetPlan) }
                            allSteps = refreshed
                            planSteps = refreshed.filter { it.mapId == mapId }
                            toast = "已加入计划 #$targetPlan"
                            if (planId == null) onOpenPlan(targetPlan)
                        }
                    }
                }) { Text(if (binding) "绑定" else "加入计划", color = Palette.Core) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPin = null; bindStepId = null }) {
                    Text("取消", color = Palette.TextDim)
                }
            },
        )
    }
}
}

/** 收藏用的稳定 key：同一张图的同一个点位。 */
private fun markerMarkKey(m: Marker): String = "map:${m.featureId}"

private fun typeOrder(type: String) = when (type) {
    "Location" -> 0
    "Quest" -> 1
    "EnemySpawnPoint" -> 2
    "CollectionPoint" -> 3
    "TreasurePoint" -> 4
    "SalvagePoint" -> 5
    else -> 9
}

/**
 * 点位显示名：Door Point #156 / Enemy Spawn Point #5058 这类内部编号直接换成人话，
 * 否则「参考点位」一栏会全是看不懂的英文。
 */
private fun markerDisplayName(m: Marker): String {
    val n = m.targetName.trim()
    val generic = Regex(
        """(?i)^(door|warp|elevator|jump|climb|collection|treasure|salvage|enemy|heart|interaction|key\s*item|tombstone)\s*(point|spawn\s*point)?\s*#?\d*$""",
    )
    return when {
        n.isBlank() -> markerTypeLabel(m.targetType)
        generic.matches(n) -> markerTypeLabel(m.targetType)
        else -> n
    }
}

/**
 * 同一地区有多张同 menu_group 的图（世界树 4 张「下层」）时，用代表性地标名区分，
 * 例如「下层 · 第二层水星天」；拿不到地标才退回游戏内部图号。
 */
private fun layerLabel(m: MapInfo, siblings: List<MapInfo>): String {
    val dup = siblings.count { it.area == m.area } > 1
    if (!dup) return m.area
    val extra = m.landmark.ifBlank { m.gameName }
    return if (extra.isBlank()) m.area else "${m.area} · ${extra}"
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0xCC141B25))
            .border(1.dp, Color(0xFF2A3646), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Core, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MarkerCard(marker: Marker, modifier: Modifier, onAddToPlan: () -> Unit) {
    // 采集点在 wiki 上有游戏内实景截图，卡片里直接给一个可展开的预览。
    val shot by produceState<String?>(null, marker.featureId) {
        value = if (marker.targetType == "CollectionPoint") {
            WikiRepo.io {
                WikiShots.fileNameForCollection(WikiRepo.collectionCodeOfFeature(marker.featureId))
            }
        } else null
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2141B25))
            .border(1.dp, Color(0xFF2A3646), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MarkerGlyphBadge(marker.targetType, 18.dp)
            Spacer(Modifier.width(7.dp))
            Text(markerTypeLabel(marker.targetType), color = Palette.TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(
                String.format("%.1f, %.1f", marker.frontierX, marker.frontierY),
                color = Color(0xFF5C6B7C),
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(6.dp))
            // 收藏：和筛选条最左边的「已收藏」开关联动，关掉后地图上不再显示这个点
            val markKey = markerMarkKey(marker)
            val marked = Prefs.isMarkedKey(markKey)
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { Prefs.toggleMarkedKey(markKey) },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (marked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (marked) "取消收藏" else "收藏",
                    tint = if (marked) Color(0xFFFFC107) else Color(0xFF5C6B7C),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(marker.targetName.ifBlank { marker.featureId }, color = Palette.Text, fontSize = 14.sp)
        if (!shot.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            WikiShotPanel(shot)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Palette.Core.copy(alpha = 0.14f))
                    .clickable(onClick = onAddToPlan)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("加入计划", color = Palette.Core, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PinCard(pin: OverlayPin, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF2141B25))
            .border(1.dp, Color(0xFF2A3646), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("计划步骤 ${pin.badge ?: ""}", pin.color)
            Spacer(Modifier.weight(1f))
            Text(String.format("%.1f, %.1f", pin.fx, pin.fy), color = Color(0xFF5C6B7C), fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(pin.label, color = Palette.Text, fontSize = 14.sp)
    }
}

/**
 * 逐段导航卡片：一段 = 起点/地标到下一个地标。
 *
 * 数据来自 `RouteGuide.legs()`，本质是点位图折线，不是真实路网，
 * 所以文案里只写方位 + 大致距离 + 下一处地标名。
 */
/** 地图下方的逐段导航条：一行字 + 前后切换，不挡地图。 */
@Composable
private fun GuidanceBar(
    leg: RouteLeg,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}/$total",
            color = Palette.Core,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${leg.heading} · ${"%.0f".format(leg.distance)}",
                color = Palette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (leg.landmark) "前往 ${leg.toName}" else "到达终点 ${leg.toName}",
                color = Palette.TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clickable(enabled = index > 0, onClick = onPrev)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("‹", color = if (index > 0) Palette.Text else Palette.TextDim.copy(alpha = 0.35f), fontSize = 16.sp)
        }
        Box(
            Modifier
                .clickable(enabled = index < total - 1, onClick = onNext)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("›", color = if (index < total - 1) Palette.Text else Palette.TextDim.copy(alpha = 0.35f), fontSize = 16.sp)
        }
    }
}
