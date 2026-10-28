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

结论：原版默认浅色。**这张表只用于确认原版用了哪些语义色，不作为我方取值。** 我方当前 `ui/theme/Theme.kt` 直接照搬了这套 PyraRed + MythraGold，属于要改掉的问题，见第 8 节。

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
- **这些资源都不进 `app/`**：Titan 插画（`assets/regions/`）、marker 位图（`assets/maps/*-marker.*`）、物品分类图标。它们只用于确认「原版某个位置有一张图」。我方改用自绘图形或授权素材。

## 3. 我方现状与差距

以下是 2026-10-28 工作区未提交代码的状态，不是最初基线。

| 维度 | 原版 | 我方（当前） | 差距 |
|---|---|---|---|
| 主题 | 浅色默认 + 深色可选 | `Palette` 已分浅色/深色 | 需用截图确认默认浅色和对比度 |
| 首页 | 搜索 + 20 卡片网格 | `HomeEntries.kt` 已有 20 个入口 | 卡片尺寸、图标和滚动细节还要对截图 |
| 底部 tab | 首页/异刃/任务/怪物/设置，可配置 | 默认已是这 5 个，设置可改 | 需重启后复验 |
| 异刃页 | 大立绘 + 多选 + 属性/补正筛选 | 已有多选和分组骨架 | 卡片尺寸和筛选排版未验收 |
| 任务页 | 四大类分流 | 已有分类入口 | 列表细节未验收 |
| 地图 | 112dp 目录卡、详情页筛选菜单、全局 dock | 卡片和筛选改了一半，dock 已挂到 `MainScaffold` | 还没重新安装截图验收 |
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

- 取值换成第 8 节的自研配色，不再使用原版 token 原值。字段名可以保留（`Bg` / `Surface` / `Text` / `Core` / `Gold` …），但语义要改：`Core` 从 PyraRed 变成自研主色。
- `darkColorScheme` + `lightColorScheme` 双套；默认浅色，设置页提供「跟随系统 / 浅色 / 深色」。
- 地图容器保留深色底，不跟随主题反转，避免浅色下地图对比度不足。

### 5.2 图鉴首页（`ui/screens/HomeScreen.kt`）

信息架构按用户选定的第 2 项重做，不保留原版两列大卡片。

- 20 个功能都还在，但收进四个内嵌分组：人物（异刃、御刃者、牵绊对话）、旅程（任务、佣兵团训练、游玩经历、挑战战斗）、世界（地图、地点、商店、天气、采集、打捞、宝箱）、装备（物品、武器、花的零件、场景技能、素材规划）。
- 行标题用中文，英文只做次级说明。没有数据的入口进占位页。
- 首页不放进行中计划或 Agent。计划在「记录」，Agent 在设置。

### 5.3 导航（`ui/App.kt`）

- 底部固定 4 项：图鉴 / 地图 / 记录 / 设置。不再做原版那种可替换的 5 tab。
- 设置里改为「置顶最多 3 个图鉴入口」，存 SharedPreferences。这保留自定义入口，但不复制原版底栏编辑器。
- 地图只有两级：地区文字列表，点进去是该地区的地图，层用顶部分段控件切换。不再做 Titan 插画卡，也不再做地区 → 区域 → 缩略图三级目录。
- 详情页是大标题、事实行、相关条目分组。主操作放在页面底部，不使用红金悬浮按钮。

### 5.4 地图规划（`route-planning` 任务细化）

- 无路网时不做「最短路」承诺：路径 = 最近传送点 → 各步骤点按最近邻排序的折线，并在 UI 上标注「示意路线（非寻路）」。
- 分层地图：同一地区多层图独立坐标，步骤点必须归属到具体 `map_id`；切换层时只显示该层点位。
- 只画计划相关点模式：隐藏无关 POI，仅保留最近传送点/地标 + 计划点 + 路径。
- 点位图标用 xenoblade2.cn 的 marker，不使用原版 APK 位图。
- 采集落点不用 `collectMarkers` 现在的写法。那条 SQL 没有 `ORDER BY`，`LIMIT 8` 后取第一行，而且只 JOIN 了 FrontierNav marker，站点里有坐标但本地没钉上的 499 个点直接消失。地区对不上时还会退回全局任意一条。

采集候选改为打分，不让模型改坐标：

1. 物品名取步骤文本或对应任务页里最长的 `物品:` 名称。两个一样长就分组都保留，不猜。
2. 候选是 `collection_drop` 里掉这个物品的全部采集点，坐标用 `Gmk` 的 `collection_code`，不再要求已经有 FrontierNav marker。
3. 排除：地区和步骤不一致；投到 `Mapinfo` 范围外；没有 `Mapinfo` 的层。
4. 分数：掉率每 10% 加 1 分；步骤里的地名出现在同一张图加 40；场景技能一致加 25；和本任务上一步同一张图加 15。
5. 只有一个候选，或者最高分至少 40 且比第二名高 25，才自动钉点。否则最多显示 5 个候选，等用户点选，点选结果写进计划，重算不覆盖。

### 5.5 开环（`affinity-clarity`）

- 环节点详情补三块：需求（材料/条件/数量）、获取途径（采集点/怪物掉落/商店/任务）、关联任务跳转。
- 数据来源：`blade_affinity_node` / `collection_drop` / `enemy_drop` / `quest_flow_item_acquisition`。
- 缺映射时显示「暂无获取途径数据」，不编造。

### 5.6 Agent（`agent-integration`）

- 现有 tool calling 扩展 tools：`search_quest` / `blade_affinity` / `map_points` / `create_plan`。
- 大模型只能缓解三类限制：把 32% 的模糊采集点按语义推荐、按地标名做模糊匹配、把自然语言转成计划。
- **不能**凭空生成路网或缺失的坐标；回答里必须区分「数据内」与「模型推测」。
- 密钥读取顺序：SharedPreferences → BuildConfig（本地 debug 用，禁止提交）。

## 6. 已确认的产品决定

1. 功能要齐，视觉和信息架构都不要像原版。用户选了「皮肤 + 信息架构一起重做」。
2. 配色逻辑参考 iOS 的分组列表和单一强调色，气质参考 Anthropic 网页的暖纸和标题衬线。不使用这两家的专有字体，也不把 PyraRed、iOS systemBlue 或站点品牌色当成界面主色。
3. 20 个功能都要能到达。没有数据时进占位页。
4. 地图底图和地图里的点位图标参考 `https://xenoblade2.cn/`，不使用原版 APK 的 marker 位图和 Titan 插画。
5. 先把站点的地图 JSON、点位坐标、地图图片和条目正文抓到本地，再决定写进 `xenoblade2.db` 的哪些表。许可是 CC BY-NC-SA 4.0，署名「乐园数据管理室 https://xenoblade2.cn」。
6. 抓取完成并完成视觉改版后，才做路线规划、开环和 Agent。

## 7. 风险

- 原版 20 个入口里多个依赖原版库中我方没有的表（如 `merc` 训练、Poppi parts、挑战战斗）→ 只能占位，属正常差距，不算 bug。
- 大规模 UI 改动可能盖住已有地图逻辑。复刻阶段只对齐原版交互，不改路线算法；计划点过滤留在复刻后的 `route-planning`。

## 8. 视觉自研方案（已确认）

### 8.1 不能沿用的原版特征

1. PyraRed `#D40011` 作为界面主色。
2. 白卡、1dp 描边、15% 强调色铺层。
3. 卡片左侧色条，以及右下角红金圆形按钮。
4. 底部「首页 / 异刃 / 任务 / 怪物 / 设置」和两列英文大卡片。
5. 原版 APK 的 Titan 插画和 marker 位图。

### 8.2 纸页

角色分工照 iOS：一块画布、一块抬起的分组表面、四级文字、一根发丝分割线、一个只用于链接和选中态的强调色。冷灰画布改成暖纸，大标题用衬线，这两点是 Anthropic 网页的逻辑，不是它的品牌色。

| 语义 | 浅色 | 深色 |
|---|---|---|
| 画布 | `#F3EFE6` | `#141311` |
| 分组表面 | `#FFFCF8` | `#1E1C19` |
| 墨水 | `#1A1714` | `#F4EFE6` |
| 次级文字 | `#6B645C` | `#B7AFA4` |
| 发丝线 | `#E4DDD2` | `#3A342C` |
| 强调色 | `#0B6E6A` | `#7DCCC6` |

游戏元素色只出现在数据上（火、水、风、冰等），不进入界面骨架。地图画布单独用深色 `#101614`，保证点位可读。

字体不打包 SF Pro，也不打包 Anthropic 的专有字体。大标题用 OFL 的 Newsreader，列表用系统无衬线。字号级差按 34 / 22 / 17 / 13。数量用等宽数字。

### 8.3 地图资源

站点地图不是 FrontierNav 瓦片。`JSON:Mapinfo/<map>` 给出宽高和世界坐标范围，底图是 `文件:<Name>_map_0.png`。点位坐标在 `JSON:Gmk/collection|enemy|npc|salvage|tbox`。点位图标是站点的 `Collection-marker-icon.png`、`Enemy.png`、`Salvage.png`、`Tbox.png`、`npc.svg`。界面可以参考这些图形的形状区分，文件按 CC BY-NC-SA 4.0 使用并署名。
