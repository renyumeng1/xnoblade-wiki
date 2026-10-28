# 架构

包名 `cn.xenoblade2.next`，单模块 Compose 应用，minSdk 26 / targetSdk 35。

## 分层

```text
MainActivity
└─ ui/App.kt            Navigator（手写栈）+ 5 个底部 Tab + 助手 FAB
   ├─ ui/screens/*     首页 / 任务 / 异刃 / 地图 / 计划 / 助手 / 设置
   └─ ui/components/*  ScreenHeader、SectionCard、MapCanvas（缩放平移 + 点位 + 计划连线 + highlightPath 高亮）
data/
├─ Db.kt                assets 库首次复制到 files，OPEN_READONLY 打开
├─ WikiRepo.kt          所有只读 SQL 的唯一入口
├─ Models.kt            纯数据类
└─ MapMath.kt           归一化坐标 + 世界坐标仿射拟合
plan/
├─ PlanStore.kt         filesDir/plans.db（可写）：plan / plan_step / affinity_progress
├─ Gazetteer.kt         内存地名/敌人/采集品词典，最长匹配
├─ Planner.kt           任务步骤、开环需求 → 带坐标的 NewStep
├─ RouteGraph.kt        同图点位 kNN + 电梯/传送点建图，Dijkstra 出折线
└─ RouteGuide.kt        折线 → 逐段 leg（方位/距离/转向）+ 计划相邻步骤 hop
agent/
├─ AgentSettings.kt     SharedPreferences：baseUrl / key / model / temperature
├─ AgentTools.kt        17 个受限工具（只读查询 + 对 plans.db 的受限写）
├─ AgentEngine.kt       OpenAI /chat/completions + tool_calls 循环（≤8 轮）
└─ OfflineAgent.kt      规则兜底，未配置模型时零网络
```

## 为什么分两个库

- Wiki 库 34 MB 只读，随 APK 分发，升级时整包替换（`Db` 里用 `db_version` 判断）。
- 用户数据（计划、勾选、开环进度）写在 `plans.db`，升级 App 不丢，也不会污染素材数据。
- `Db` 对外只暴露 `list/first/count`，UI 永远不会直接拿 Cursor。

## 计划是怎么生成的

`Planner.anchor()` 的单步定位优先级：

1. `task_workflow_link` 里 `地点:<feature_id>` 内链 → 直接取 marker；
2. 步骤文本里出现同地区地标名（最长匹配）；
3. 全局地名兜底（跨区域任务，如接取在英维迪亚、步骤跑到阿伐利缇亚）；
4. `kind` 含 `Battle` 时，文本里的敌人名 → `enemy_spawns` 的刷新点；
5. 采集类步骤 → `collection_drop` / `collection_marker`；
6. NPC 名 → `npc_game_appearance` 的 `x/z`（与 frontier 同尺度，直接当坐标）；
7. 都不中但同任务多数步骤在同一张图 → 图级定位（只给 `map_id`）；
8. 再不行 → 纯文字步骤，UI 标「未定位」，保留 `↑↓` 手动排序。

开环计划（`Planner.buildBladePlan`）的链：
`地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（整串 / 「XX 族」族名）> 采集品 > 地区主图（图级）`。
其中：

- `Gazetteer.matchRegion()` 先全长匹配 `map.display_name`，再试地区名前 3~8 字的简称；
- `Gazetteer.matchEnemyFamily()` 从「…以外的甲狼兽族」切出「甲狼兽」；
- 敌人刷新点只接受需求地区内的点，跨地区的写进 detail 提示，不钉点；
- 纯战斗/计数需求补「怎么刷」提示（26 条模板）；
- 最后用 `clusterByMap` 把同图步骤按最近邻重排，减少来回跑图。

## 逐段导航（RouteGuide）

`RouteGuide` 不重新算路，只把 `RouteGraph` 的折线切成「起点 → 有名地标 → 终点」的 leg：

- 停靠点 = `RouteGraph` 里 `notable = true` 的点（有名字的地标、传送点、电梯等）；
  合成端点「起点 / 终点」一律 `notable = false`，否则每段路线都会被切出一段假 leg。
- `autoName` 认 `Door Point / Warp Point / Elevator Point / Jump Point / Climbing Point` 这类英文点，
  但以 `#数字` 结尾的编号名不算停靠点，避免逐段导航里出现「前往 Door Point #85」。
- 相邻停靠点间距小于 `MIN_LEG = 25.0`（frontier 平面距离）的段合并，过滤点位抖动。
- 每段只报四个量：8 方位罗盘、直线距离、相对上一段的转向（±22.5° 内直行、±135° 内左/右转、其余掉头）、终点名。
  方位计算 `dx = toFx - fromFx`、`dy = -(toFy - fromFy)`，北取屏幕上方。
- 计划页的 `planHops()` 只连相邻的**已定位**步骤；中间跳过的无坐标步骤用 `skipped` 数出来，
  两点距离 < 1 视为同一位置（任务常把「问 A」和「回 A 交任务」拆成两步）。

方位与距离都基于点位图平面坐标，UI 固定标注「点位图估算，非真实路网」。

## Agent 的边界

- 工具集只有查询和「对计划表的受限写入」，**没有任意 SQL 入口**，模型无法读别的 App 数据。
- 工具输出上限 6,000 字符；超限返回 `{"truncated": true, "preview": ...}` 信封，不把 JSON 截成半截。
- 未配置 Base URL/Key 时完全不发网络请求，直接走 `OfflineAgent` 的规则匹配。
- 请求失败会 `AgentEvent.Failure` 提示并回退离线规划，聊天不会卡死。
- `model` 字段只在该项非空时发送；端点若强制要求，会返回 400，`AgentEngine` 识别后换成
  「请在设置页填写模型名后重试」这类可操作提示，而不是把裸 HTTP 错误丢给用户。

## 线程模型

- 所有 SQLite 访问都在 `WikiRepo.io { }`（`Dispatchers.IO`）里，Compose 侧用 `LaunchedEffect(version)` 触发重载。
- Agent 的 `run()` 也在 IO 上跑，事件通过 `Channel` 回到主线程更新聊天列表。
