# Research · 本仓库实现地图

| 文件 | 职责 |
|---|---|
| `ui/App.kt` | 手写导航栈；`TABS`（:136）；Agent FAB |
| `ui/theme/Theme.kt` | `Palette` 深色硬编码，待改浅色双套 |
| `ui/screens/HomeScreen.kt` | 统计卡 + 4 宫格 + 进行中计划 |
| `ui/screens/BladeScreens.kt` | 异刃列表（小圆头像）、详情、生成开环计划（:175/:197） |
| `ui/screens/QuestScreens.kt` | 任务扁平列表 + chip |
| `ui/screens/MapScreens.kt` | 地图列表/详情；计划只看点模式（:153）；层 chips（:315）；`layerLabel`（:715） |
| `ui/screens/PlanScreens.kt` | 计划详情、看地图 |
| `ui/screens/SettingsScreen.kt` | 设置 |
| `ui/screens/AgentScreen.kt` | Agent 对话 |
| `ui/components/MapCanvas.kt` | `markerColor`(:138)、`MarkerGlyph`(:176)、`drawTypeGlyph`(:206)、`placeStrict` 调用(:500/508/612/622/630)、点位半径(:609) |
| `data/MapMath.kt` | Web Mercator 纵轴 `mercY/invMercY` |
| `data/MapSnap.kt` | `MAX_FRAC=0.16`、`snap()`、`snapOrNull()` |
| `data/ItemIcons.kt` | 物品分类 → 图标；仅 `WikiRepo.kt:446` 使用 |
| `data/WikiRepo.kt` | 全部 SQL 查询 |
| `plan/PlanStore.kt` | `files/plans.db` |
| `agent/AgentEngine.kt` / `AgentTools.kt` / `OfflineAgent.kt` | OpenAI 兼容 tool calling，16 个工具 |
| `assets/blades/` | 52 张异刃立绘 |
| `assets/item-icons/` | 33 张分类图标 |
| `assets/maps/` | 87 个文件（含标记图层） |
| `assets/xenoblade2.db` | 主数据（55 异刃 / 2545 物品 / 528 环节点 / 2372 环等级 / 582 关联） |

## 入口与文档

`README.md`、`docs/PLAN.md`、`docs/DATA_NOTES.md`（第 6 节开环上限、第 7 节 Mercator 与吸附）、`docs/ARCHITECTURE.md`。
