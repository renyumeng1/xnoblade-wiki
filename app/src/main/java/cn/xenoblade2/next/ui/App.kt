package cn.xenoblade2.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.data.Prefs
import cn.xenoblade2.next.data.SearchHit
import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.Gazetteer
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.screens.AgentScreen
import cn.xenoblade2.next.ui.screens.ArchiveScreen
import cn.xenoblade2.next.ui.screens.BladeDetailScreen
import cn.xenoblade2.next.ui.screens.BladeListScreen
import cn.xenoblade2.next.ui.screens.EnemyDetailScreen
import cn.xenoblade2.next.ui.screens.EnemyListScreen
import cn.xenoblade2.next.ui.screens.EntityDetailScreen
import cn.xenoblade2.next.ui.screens.HomeScreen
import cn.xenoblade2.next.ui.screens.MapDetailScreen
import cn.xenoblade2.next.ui.screens.MapListScreen
import cn.xenoblade2.next.ui.screens.PlanDetailScreen
import cn.xenoblade2.next.ui.screens.PlanListScreen
import cn.xenoblade2.next.ui.screens.QuestCategoryScreen
import cn.xenoblade2.next.ui.screens.QuestDetailScreen
import cn.xenoblade2.next.ui.screens.QuestListScreen
import cn.xenoblade2.next.ui.screens.SettingsScreen
import cn.xenoblade2.next.ui.nav.HOME_ENTRY_BY_KEY
import cn.xenoblade2.next.ui.theme.Palette
import cn.xenoblade2.next.ui.theme.Xc2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Agent : Screen
    data object QuestList : Screen
    data class QuestCategory(val category: String) : Screen
    data object BladeList : Screen
    data object EnemyList : Screen
    data object MapList : Screen
    data object PlanList : Screen
    data class Archive(val key: String) : Screen
    data class EntityDetail(val type: String, val id: Long) : Screen
    data class QuestDetail(val id: Long) : Screen
    data class BladeDetail(val id: Long, val name: String) : Screen
    data class EnemyDetail(val id: Long) : Screen
    data class MapDetail(val mapId: String, val planId: Long? = null) : Screen
    data class PlanDetail(val planId: Long) : Screen
}

/** 首页入口 key → 顶层页面。已经有专门页面的走专门页面，其余走通用档案页。 */
fun screenForEntry(key: String): Screen = when (key) {
    "blades" -> Screen.BladeList
    "quests" -> Screen.QuestList
    "monsters" -> Screen.EnemyList
    "maps" -> Screen.MapList
    "planner" -> Screen.PlanList
    else -> Screen.Archive(key)
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    fun push(screen: Screen) {
        if (stack.last() == screen) return
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun switchTop(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                Prefs.init(context)
                Db.init(context)
                PlanStore.init(context)
                Gazetteer.ensureLoaded()
            }
            ready = true
        } catch (t: Throwable) {
            error = t.stackTraceToString()
        }
    }

    Xc2Theme {
        when {
            error != null -> Box(
                Modifier.fillMaxSize().background(Palette.Bg).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "数据初始化失败：\n\n${error?.take(1200)}",
                    color = Palette.Flame,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Start,
                )
            }

            !ready -> Box(Modifier.fillMaxSize().background(Palette.Bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("异度神剑 2", color = Palette.Core, fontSize = 18.sp)
                    Text("正在装载本地数据库…", color = Palette.TextDim, fontSize = 12.sp)
                }
            }

            else -> MainScaffold()
        }
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold() {
    val nav = remember { Navigator() }
    val current = nav.current

    // 对齐原版：图鉴 / 设置固定，中间是最多 3 个可配入口（设置页「底部 Tab 页设置」）。
    // 这里必须每次重组都读 Prefs.tabKeys（SnapshotState）；用 remember 缓存列表会读不到改动。
    val tabs = buildList {
        add(Tab(Screen.Home, "图鉴", Icons.Rounded.GridView))
        Prefs.tabKeys.forEach { key ->
            val entry = HOME_ENTRY_BY_KEY[key] ?: return@forEach
            add(Tab(screenForEntry(key), entry.title, entry.icon))
        }
        add(Tab(Screen.Settings, "设置", Icons.Rounded.Settings))
    }.distinctBy { it.screen }

    // 顶层 Tab 是「替换栈底」而不是入栈，直接按返回会退出 App。这里补一层：
    // 有栈就退栈，栈底不是首页就回首页。
    BackHandler(enabled = nav.stack.size > 1 || nav.current != Screen.Home) {
        if (nav.stack.size > 1) nav.pop() else nav.switchTop(Screen.Home)
    }

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.screen,
                        onClick = { if (current != tab.screen) nav.switchTop(tab.screen) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Core,
                            selectedTextColor = Palette.Core,
                            unselectedIconColor = Palette.TextDim,
                            unselectedTextColor = Palette.TextDim,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = current) {
                Screen.Home -> HomeScreen(
                    onOpenEntry = { key ->
                        Prefs.pushRecent(key)
                        nav.switchTop(screenForEntry(key))
                    },
                    onOpenHit = { hit -> openHit(nav, hit) },
                )

                Screen.QuestList -> QuestListScreen(
                    onOpenCategory = { nav.push(Screen.QuestCategory(it)) },
                )

                is Screen.QuestCategory -> QuestCategoryScreen(
                    category = s.category,
                    onBack = { nav.pop() },
                    onOpen = { nav.push(Screen.QuestDetail(it)) },
                )
                Screen.BladeList -> BladeListScreen(onOpen = { id, name -> nav.push(Screen.BladeDetail(id, name)) })
                Screen.EnemyList -> EnemyListScreen(onOpen = { nav.push(Screen.EnemyDetail(it)) })
                Screen.MapList -> MapListScreen(onOpen = { nav.push(Screen.MapDetail(it)) })
                Screen.PlanList -> PlanListScreen(
                    onOpen = { nav.push(Screen.PlanDetail(it)) },
                    onCreateQuestPlan = { nav.push(Screen.QuestList) },
                )

                Screen.Agent -> AgentScreen(
                    onBack = { nav.pop() },
                    onOpenPlan = { nav.push(Screen.PlanDetail(it)) },
                )

                Screen.Settings -> SettingsScreen(
                    onBack = {
                        if (nav.stack.size > 1) nav.pop() else nav.switchTop(Screen.Home)
                    },
                    onOpenAgent = { nav.push(Screen.Agent) },
                )

                is Screen.Archive -> ArchiveScreen(
                    key = s.key,
                    // Archive 可能是底栏 Tab（替换栈底），此时退栈无效，回图鉴。
                    onBack = { if (nav.stack.size > 1) nav.pop() else nav.switchTop(Screen.Home) },
                    onOpenEntity = { type, id -> nav.push(Screen.EntityDetail(type, id)) },
                )

                is Screen.EntityDetail -> EntityDetailScreen(
                    type = s.type,
                    id = s.id,
                    onBack = { nav.pop() },
                )

                is Screen.QuestDetail -> QuestDetailScreen(
                    questId = s.id,
                    onBack = { nav.pop() },
                    onOpenPlan = { nav.push(Screen.PlanDetail(it)) },
                    onOpenMap = { mapId -> nav.push(Screen.MapDetail(mapId)) },
                )

                is Screen.BladeDetail -> BladeDetailScreen(
                    bladeId = s.id,
                    onBack = { nav.pop() },
                    onOpenPlan = { nav.push(Screen.PlanDetail(it)) },
                )

                is Screen.EnemyDetail -> EnemyDetailScreen(
                    enemyId = s.id,
                    onBack = { nav.pop() },
                )

                is Screen.MapDetail -> MapDetailScreen(
                    mapId = s.mapId,
                    planId = s.planId,
                    onBack = { nav.pop() },
                    onSwitchMap = { nav.push(Screen.MapDetail(it, s.planId)) },
                    onOpenPlan = { nav.push(Screen.PlanDetail(it)) },
                )

                is Screen.PlanDetail -> PlanDetailScreen(
                    planId = s.planId,
                    onBack = { nav.pop() },
                    onOpenMap = { mapId -> nav.push(Screen.MapDetail(mapId, s.planId)) },
                )
            }
        }
    }
}

private fun openHit(nav: Navigator, hit: SearchHit) {
    Prefs.pushRecent(
        when (hit.type) {
            "blade" -> "blades"
            "quest" -> "quests"
            "enemy" -> "monsters"
            "item" -> "items"
            "location" -> "location"
            else -> "items"
        },
    )
    when (hit.type) {
        "quest" -> nav.push(Screen.QuestDetail(hit.id))
        "blade" -> nav.push(Screen.BladeDetail(hit.id, hit.title))
        "enemy" -> nav.push(Screen.EnemyDetail(hit.id))
        "location" -> nav.push(Screen.EntityDetail("location", hit.id))
        else -> nav.push(Screen.EntityDetail(hit.type, hit.id))
    }
}
