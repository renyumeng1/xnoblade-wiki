# Design · 复刻原版 1.2.2 的对照结论与技术方案

> 本文只记录**已核对**的事实（APK 反编译 + 实机截图 + 本地数据统计），推断项标 `[INFERENCE]`。

## 1. 对照材料

| 材料 | 位置 |
|---|---|
| 原版 APK | `apk/xenoblade2Wiki-1.2.2.apk`（包名 `cn.xenoblade2.wiki`） |
| 反编译源码 | `src/jadx/sources/cn/xenoblade2/wiki/`（832 个 Java 文件） |
| 原版数据库 | `apk/extracted/assets/xenoblade2.db` |
| 实机截图 | `/tmp/ref_home6.png`…`/tmp/ref_home8.png`、`/tmp/ref_home_scroll1.png`、`/tmp/ref_t1.png`、`/tmp/ref_quests.png`、`/tmp/ref_enemies.png`、`/tmp/ref_blades2.png`、`/tmp/ref_settings.png` |
| 我方截图 | `/tmp/our_home.png`（其他页面截图在会话过程中生成，重启后可能丢失） |

> 截图在 `/tmp`，重启后不保证存在。需要长期留存时复制到 `docs/screenshots/`。

## 2. 原版设计要素（有代码/截图依据）

### 2.1 配色（`src/jadx/.../p004ui/theme/XenoColors.java`）

反编译里 `ColorKt.Color(long)` 的实参已换算成 ARGB：

| 语义 | 浅色（默认） | 深色 |
|---|---|---|
| WikiBackground | `#FFF7F9FC` | `#FF0F1115` |
| PyraRed | `#FFD40011` | `#FFFF4D5A` |
| MythraGold | `#FFFFC107` | `#FFFFD166` |
| Purple | `#FF7653B5` | `#FFD38BFF` |
| SalvageCyan | `#FF56EBFF` | `#FF38BDF8` |
| TextMain | `#FF1A1C1F` | `#FFF1F3F7` |
| Card | `#FFFEFEFF` | `#FF161920` |
| CardBorder | `#2B5C5C60` | `#FF2A303C` |
| Secondary | `#FF73757C` | `#FF9CA3AF` |
| Raised | `#FFFFFFFF` | `#FF1E222B` |
| TopAppBar | `#FFFFF0F2` | `#FF12141A` |

结论：原版**默认浅色**，深色是可选。我方 `ui/theme/Theme.kt` 目前是硬编码深色 + 青色系（`#6FE3C4` 等），与原版不是一套配色。

### 2.2 首页 20 个入口（`p004ui/home/HomeScreenKt.java:64`）

| 英文 | 中文 | route | Accent |
|---|---|---|---|
| Journey | 游玩经历 | `journey` | PyraRed |
| Planner | 素材规划 | `planner` | MythraGold |
| Mercenary Training | 佣兵团训练 | `mercenary-training` | PyraRed |
| Blades | 异刃 | `blades` | PyraRed |
| Field Skills | 场景技能 | `fieldSkills` | MythraGold |
| Drivers | 御刃者 | `drivers` | Teal |
| Quests | 任务 | `quests` | MythraGold |
| Items | 物品 | `items` | Brown |
| Weapons | 武器 | `weapons` | Teal |
| Weather | 天气 | `weather` | SalvageCyan |
| Monsters | 怪物 | `monsters` | PyraRed |
| Collection | 采集点 | `collection` | Green |
| Salvage | 打捞点 | `salvage` | SalvageCyan |
| Chests | 宝箱 | `chests` | MythraGold |
| Maps | 交互地图 | `maps` | SalvageCyan |
| Locations | 地点 | `locations` | SalvageCyan |
| Shops | 商店 | `shops` | Orange |
| Heart-to-Hearts | 牵绊对话 | `heart` | Teal |
| Poppi Parts | 花的零件 | `poppi` | PyraRed |
| Challenge Battles | 挑战战斗 | `challenge` | Purple |

布局：`chunked(2)` → 两列大卡片网格（`HomeScreenKt.java:8813` 起）。

### 2.3 底部导航（`MainActivityKt.java:135`）

固定 5 tab，顺序为：`home 首页 / blades 异刃 / quests 任务 / enemies 怪物 / settings 设置`。

- `MainActivityKt.java:245` 另有 20 个「可配置 tab」映射（`experience/materialLists/fieldSkills/drivers/...`），说明设置页可以把任意 archive 入口加到 tab。
- 我方 `ui/App.kt:136` 目前是：首页 / 任务 / 异刃 / 地图 / 计划。

### 2.4 各页结构（截图）

| 页面 | 原版结构 |
|---|---|
| 首页 | 顶部搜索框 → 2 列大卡片网格（20 项） |
| 游玩经历 | 9 个统计卡：异刃 0/55、物品 0/2224、任务 0/146、佣兵团任务 0/279、非正式任务 0/8、怪物 0/994、宝箱 0/435、收纳包商店 0/82、牵绊对话 0/73 |
| 异刃 | 多选按钮 + 搜索框 + 全部/已收集/未收集 + 属性 chip + 补正 chip + 大立绘网格 |
| 任务 | 4 个大卡片：普通任务 / 异刃任务 / 佣兵团任务 / 非正式任务 |
| 怪物 | 搜索 + 全部/已收集/未收集 + 地区 chip + 普通/珍稀种 chip + 分组列表 |
| 设置 | 底部 Tab 页设置 / 外观（暗色模式 跟随系统）/ 内容偏好（黄金之国伊拉）/ 版本更新（网络访问说明、匿名统计、友盟+隐私政策） |

### 2.5 资源（重要事实）

- 原版 APK 的 `assets/item-icons/` 同样是 **33 个分类图标**（4 个子目录 31 个 + 根目录 2 个），**没有逐物品图标**。原版是按物品分类映射分类图标，不是每个物品一张图。
  → 所以我方「物品图标缺失」的根因大概率是 UI 没做映射/没显示，而不是缺资源。`[INFERENCE]` 需在 `icons-markers` 任务里用截图确认。
- 原版 APK **不含** `assets/blades/`，但数据库 `blade.portrait` 写的是 `blades/pyra.png` 这类路径；我方 `assets/blades/` 有 52 张。
- 我方数据库 55 个异刃：53 行有 portrait 路径，其中 `blades/dagas.png` 重复一次 → 52 个唯一文件且都存在；`MOMO`、`MOMO (i)` 两行没有 portrait。
- 原版刻意不做的部分：更新检查、远程数据库增量更新、友盟统计、隐私弹窗。复刻时**保持不做**。

## 3. 我方现状与差距

| 维度 | 原版 | 我方（当前） | 差距 |
|---|---|---|---|
| 主题 | 浅色默认 + 深色可选 | 强制深色 | 需重做 `Palette` |
| 首页 | 搜索 + 20 卡片网格 | 统计卡 + 4 宫格快捷 + 进行中计划 + Agent FAB | 需重排 |
| 底部 tab | 5 个，可配置 | 5 个，固定（首页/任务/异刃/地图/计划） | tab 集合与可配置性 |
| 异刃页 | 大立绘 + 属性/补正筛选 | 小圆头像列表 | 需重做列表 |
| 任务页 | 四大类分流 | 扁平列表 + chip | 需分流 |
| 缺失模块 | Journey/Planner/Mercenary/FieldSkills/Drivers/Items/Weapons/Weather/Collection/Salvage/Chests/Locations/Shops/Heart/Poppi/Challenge | 无入口 | 需 16 个页面或「未收录」占位页 |
| 记录 | 有收集进度、勾选收藏 | 部分 | 需补本地收藏/收集状态 |

*注：原版统计里的 2224 物品 / 146 任务与原版库版本相关，我方库是 2545 物品 / 514 任务，数字不需要强行对齐。*

## 4. 已有实现与验证证据

### 4.1 地图投影与吸附（已完成，待用户手测复验）

- `data/MapMath.kt`：`frontiernav-*` 图纵轴改 Web Mercator（`mercY` / `invMercY`）。
- `data/MapSnap.kt`：`MAX_FRAC = 0.16`；新增 `snapOrNull()`，吸附失败返回 null。
- `ui/components/MapCanvas.kt:500/508/612/622/630`：点位图标、地标文字、计划 pin、点击命中判定走 `placeStrict`；折线仍走宽松 `place()`。
- 证据（`docs/DATA_NOTES.md` 第 7 节）：全库 6,239 点位，线性纵轴 63.8% 落在地板，Mercator 97.9%；严格模式只隐藏 10/6239（0.16%），集中在 `cliffs-of-morytha-lower-level-map`(3)、`spirit-crucible-lower-level`(3)、`spirit-crucible-bone-road`(2)、`leftheria-fonsett-waters`(1)、`cliffs-of-morytha-upper-level-map`(1)。
- 地图放大后盖住标题栏：`ui/screens/MapScreens.kt` 地图区外层已加 `clipToBounds`。

### 4.2 规划与 Agent

- `plan/PlanStore.kt` → `files/plans.db`，计划可持久化。
- `agent/AgentEngine.kt` 已接 OpenAI 兼容 tool calling；endpoint / model / key **只存在模拟器 SharedPreferences**，不写入仓库。
- 地图「只画计划相关点」模式已在 `MapScreens.kt` 实现（最近传送点 + 计划点 + 路径），待手测。

### 4.3 数据限制（来自用户/前序分析）

- `route_connection` 表为空 → 无真实路网，只能最近邻排序，不能图寻路。
- 采集步骤自动落点率 32%（缺「物品 → 采集点」映射）。
- 76 张图中 41 张可拟合「世界坐标 → 平面坐标」，其余多为室内小图、样本不足。

## 5. 技术方案（待实现）

### 5.1 主题（`ui/theme/Theme.kt`）

- `Palette` 拆成 `LightPalette` / `DarkPalette`，字段语义对齐原版（Background/PyraRed/MythraGold/Purple/SalvageCyan/TextMain/Card/CardBorder/Secondary/Raised/TopAppBar）。
- `darkColorScheme` + `lightColorScheme` 双套；默认浅色，设置页提供「跟随系统 / 浅色 / 深色」。
- 地图在浅色下对比度不足的顾虑：地图容器单独保留深色底 + 半透明遮罩，不强制整页深色。

### 5.2 首页（`ui/screens/HomeScreen.kt`）

- 数据模型 `HomeEntry(english, chinese, route, accent, badge?)`，20 项照抄文案与顺序（文案属功能性标识，非代码复制）。
- 两列 `LazyVerticalGrid`；无数据模块进入「未收录」占位页（复用统一 `EmptyModuleScreen(title)`），不做假数据。
- 保留我方已有的「进行中计划」区块，置于网格之上或之下由视觉稿定，避免丢掉现有能力。

### 5.3 导航（`ui/App.kt`）

- `Screen` 增加 16 个 archive 路由；底部 tab 改为 5 个（首页/异刃/任务/怪物/设置）。
- 设置页「底部 Tab 页设置」：从 20 个入口里选最多 5 个，顺序可调，存 SharedPreferences/DataStore。

### 5.4 地图规划（`route-planning` 任务细化）

- 无路网时不做「最短路」承诺：路径 = 最近传送点 → 各步骤点按最近邻排序的折线，并在 UI 上标注「示意路线（非寻路）」。
- 分层地图：同一地区多层图独立坐标，步骤点必须归属到具体 `map_id`；切换层时只显示该层点位。
- 只画计划相关点模式：隐藏无关 POI，仅保留最近传送点/地标 + 计划点 + 路径。

### 5.5 开环（`affinity-clarity`）

- 环节点详情补三块：需求（材料/条件/数量）、获取途径（采集点/怪物掉落/商店/任务）、关联任务跳转。
- 数据来源：`blade_affinity_node` / `collection_drop` / `enemy_drop` / `quest_flow_item_acquisition`。
- 缺映射时显示「暂无获取途径数据」，不编造。

### 5.6 Agent（`agent-integration`）

- 现有 tool calling 扩展 tools：`search_quest` / `blade_affinity` / `map_points` / `create_plan`。
- 大模型只能缓解三类限制：把 32% 的模糊采集点按语义推荐、按地标名做模糊匹配、把自然语言转成计划。
- **不能**凭空生成路网或缺失的坐标；回答里必须区分「数据内」与「模型推测」。
- 密钥读取顺序：SharedPreferences → BuildConfig（本地 debug 用，禁止提交）。

## 6. 待用户确认（不阻塞记录）

1. 浅色主题是否作为默认（可跟随系统），还是保留深色为主的观感？
2. 16 个缺失模块是先做「未收录」占位入口，还是挑选部分优先做全（例如 Items / Collection / Chests）？
3. 首页是否保留我方独有的「进行中计划」与 Agent 入口？

## 7. 风险

- 原版 20 个入口里多个依赖原版库中我方没有的表（如 `merc` 训练、Poppi parts、挑战战斗）→ 只能占位，属正常差距，不算 bug。
- 大规模 UI 改动与正在手测的地图逻辑冲突 → 先完成地图/开环/Agent 的验收，再做 `ui-parity` 全量改版；或分阶段切换（主题 + 首页先做）。
