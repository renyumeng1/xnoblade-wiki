package cn.xenoblade2.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Hardware
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.xenoblade2.next.data.Db
import cn.xenoblade2.next.plan.Gazetteer
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.ui.screens.AgentScreen
import cn.xenoblade2.next.ui.screens.BladeDetailScreen
import cn.xenoblade2.next.ui.screens.BladeListScreen
import cn.xenoblade2.next.ui.screens.HomeScreen
import cn.xenoblade2.next.ui.screens.MapDetailScreen
import cn.xenoblade2.next.ui.screens.MapListScreen
import cn.xenoblade2.next.ui.screens.PlanDetailScreen
import cn.xenoblade2.next.ui.screens.PlanListScreen
import cn.xenoblade2.next.ui.screens.QuestDetailScreen
import cn.xenoblade2.next.ui.screens.QuestListScreen
import cn.xenoblade2.next.ui.screens.SettingsScreen
import cn.xenoblade2.next.ui.theme.Palette
import cn.xenoblade2.next.ui.theme.Xc2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data object QuestList : Screen
    data object BladeList : Screen
    data object MapList : Screen
    data object PlanList : Screen
    data object Settings : Screen
    data object Agent : Screen
    data class QuestDetail(val id: Long) : Screen
    data class BladeDetail(val id: Long, val name: String) : Screen
    data class MapDetail(val mapId: String, val planId: Long? = null) : Screen
    data class PlanDetail(val planId: Long) : Screen
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
                    Text("异度神剑 2 NEXT", color = Palette.Core, fontSize = 18.sp)
                    Text("正在装载本地数据库…", color = Palette.TextDim, fontSize = 12.sp)
                }
            }

            else -> MainScaffold()
        }
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Screen.Home, "首页", Icons.Outlined.AutoStories),
    Tab(Screen.QuestList, "任务", Icons.Filled.Search),
    Tab(Screen.BladeList, "异刃", Icons.Outlined.Hardware),
    Tab(Screen.MapList, "地图", Icons.Filled.Map),
    Tab(Screen.PlanList, "计划", Icons.Filled.Checklist),
)

@Composable
private fun MainScaffold() {
    val nav = remember { Navigator() }
    val current = nav.current
    val topLevel = TABS.any { it.screen == current }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.screen,
                        onClick = { if (current != tab.screen) nav.switchTop(tab.screen) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.Core,
                            selectedTextColor = Palette.Core,
                            unselectedIconColor = Palette.TextDim,
                            unselectedTextColor = Palette.TextDim,
                            indicatorColor = Palette.Core.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (topLevel) {
                ExtendedFloatingActionButton(
                    onClick = { nav.push(Screen.Agent) },
                    containerColor = Palette.Core,
                    contentColor = Palette.Bg,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Text("  助手", fontSize = 13.sp)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = current) {
                Screen.Home -> HomeScreen(
                    onOpenQuests = { nav.switchTop(Screen.QuestList) },
                    onOpenPlans = { nav.switchTop(Screen.PlanList) },
                    onOpenMaps = { nav.switchTop(Screen.MapList) },
                    onOpenBlades = { nav.switchTop(Screen.BladeList) },
                    onOpenAgent = { nav.push(Screen.Agent) },
                    onOpenSettings = { nav.push(Screen.Settings) },
                    onOpenQuest = { nav.push(Screen.QuestDetail(it)) },
                    onOpenBlade = { id, name -> nav.push(Screen.BladeDetail(id, name)) },
                )

                Screen.QuestList -> QuestListScreen(onOpen = { nav.push(Screen.QuestDetail(it)) })
                Screen.BladeList -> BladeListScreen(onOpen = { id, name -> nav.push(Screen.BladeDetail(id, name)) })
                Screen.MapList -> MapListScreen(onOpen = { nav.push(Screen.MapDetail(it)) })
                Screen.PlanList -> PlanListScreen(
                    onOpen = { nav.push(Screen.PlanDetail(it)) },
                    onCreateQuestPlan = { nav.push(Screen.QuestList) },
                )

                Screen.Agent -> AgentScreen(
                    onBack = { nav.pop() },
                    onOpenPlan = { nav.push(Screen.PlanDetail(it)) },
                )

                Screen.Settings -> SettingsScreen(onBack = { nav.pop() })

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
