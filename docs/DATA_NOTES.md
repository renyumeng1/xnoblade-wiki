# 数据说明（xenoblade2.db）

本文件记录 `app/src/main/assets/xenoblade2.db` 的结构事实与踩坑点。所有数字都是在本仓库的库上跑出来的，不是估计值。

## 1. 规模

| 表 | 行数 | 说明 |
| --- | --- | --- |
| `map` | 76 | 每张 FrontierNav 地图，含 `lower_x/lower_z/upper_x/upper_z` 与像素尺寸 |
| `frontiernav_map_marker` | 6,240 | 全类型点位；其中 5,555 带 `world_x/world_z` |
| `quest` | 514 | 任务主体（`entity_id` 关联 `entity`） |
| `task_workflow_step` | 1,232 | 205 个任务的官方步骤 |
| `task_workflow_link` | 1,275 | 步骤内链，其中 `target LIKE '地点:%'` 349 条 |
| `blade` | 55 | 异刃 |
| `blade_affinity_node` / `blade_affinity_level` | 528 / 2,372 | 开环节点与逐级需求 |
| `blade_affinity_level_link` | 582 | 开环需求内链 |
| `route_connection` | **0** | 没有连通图，别指望做最短路 |
| `collection_point` | 1,532 | 采集点 |
| `item` | 2,545 | 物品 |
| `npc_game_appearance` | 1,676 | NPC 出现记录；923 行能 `JOIN map ON m.name = game_map`（255 个 NPC） |

总表数 101，另有 FTS5 表 `entity_fts`（`entity` 的全文索引）。

## 2. 关键字段

### map

```sql
id TEXT PRIMARY KEY, name TEXT, image_path TEXT, display_name TEXT, menu_group TEXT,
width REAL, height REAL, lower_x REAL, lower_y REAL, lower_z REAL,
upper_x REAL, upper_y REAL, upper_z REAL, marker_count INTEGER
```

- `display_name` = 地区（古拉 / 阿伐利缇亚 …），`menu_group` = 子区域（托里戈斯 上层 …）。
- `image_path` 形如 `maps/frontiernav-argentum-bazaar.png`，直接从 assets 读。

### frontiernav_map_marker

```sql
feature_id TEXT PRIMARY KEY, map_id TEXT, map_name TEXT, map_game_name TEXT,
frontier_x REAL NOT NULL, frontier_y REAL NOT NULL, world_x REAL, world_z REAL,
transform_rmse REAL, target_id TEXT, target_type TEXT, target_name TEXT,
target_game_name TEXT, target_category TEXT, target_name_en TEXT
```

实测类型分布（前几名）：`EnemySpawnPoint` 3,213、`CollectionPoint` 1,033、`Location` 452、`TreasurePoint` 427、`Quest` 131、`SalvagePoint` 61。

### 敌人三张表的关系（容易搞反）

```sql
enemy      (entity_id INTEGER PK → entity.id, is_torna, enemy_code TEXT)
enemy_stats(enemy_id INTEGER → enemy.entity_id, position, level, hp, ...)   -- 整数 entity id
enemy_spawn(enemy_id TEXT  → enemy.enemy_code, spawn_id TEXT, x, y, z, ...) -- 文本 code
```

正确 join（已验证：1,169 条 `enemy_stats` 全部能落到 `enemy`）：

```sql
SELECT e.id, e.title,
       (SELECT MIN(level) FROM enemy_stats s WHERE s.enemy_id = e.id) AS level,
       f.map_id, f.feature_id, f.world_x, f.world_z
FROM entity e
JOIN enemy en            ON en.entity_id = e.id
JOIN enemy_spawn sp      ON sp.enemy_id = en.enemy_code
JOIN frontiernav_map_marker f ON f.target_id = sp.spawn_id
WHERE e.title LIKE '%' || ? || '%'
GROUP BY f.feature_id
```

`enemy_stats.enemy_id` 是整数 entity id，不是 `enemy_code`；把两者互连会得到 0 行。

### 任务

- `quest.condition_text / description / gold / exp / category / npc / available_chapter`
- `quest_location(quest_id, position, location)`：文本地点，形如 `古拉·托里戈斯·上层`。
- `task_workflow_step.kind` 取值：`官方目标 · Battle|Collect|Gimmick|QuestCondition|ReachPlace|Talk|UseItem`。
- `task_workflow_link.target` 形如 `地点:ma-05-a-b-01-36-63-map-feature`，`label` 是给玩家看的地名。

### 开环

- `blade_affinity_node(blade_id → blade.entity_id, name, category, position)`
- `blade_affinity_level(node_id, level, effect, requirement, achievement_id, achievement_count)`
- `blade_affinity_level_link(level_id, target, label)`：`target` 与任务内链同格式，可直接 `LIKE '地点:%'` 取 feature_id。

## 3. 地图坐标换算（已跑通）

```text
nx = (frontier_x - lower_x) / (upper_x - lower_x)
ny = (frontier_y - upper_z) / (lower_z - upper_z)
```

`frontier_y` 已经是 Web Mercator 的 y 值（单位是度），**不要再做一次墨卡托变换**。`ny` 的分母是 `lower_z - upper_z`（负数），所以北在上。

世界坐标（`world_x/world_z`，游戏内坐标系）到 `frontier_x/frontier_y` 需要线性拟合：`f = a*w + b`，用同一张图上所有带 world 坐标的点做最小二乘。实测 x 方向几乎无误差，y 方向因多层地形压平会有 0.1~2.6 的偏差；`Affine1D.usable` 要求样本 ≥3 且 RMSE < 25。

## 4. 已知数据缺陷

1. `route_connection` 为空 → 只能按「同图最近邻」排序，不能做真实寻路。
2. 约 11% 的点位没有 world 坐标（主要是室内的 NPC/任务点），只能按 frontier 坐标画，不能参与世界坐标拟合。
3. ~~`npc_appearance_map` 里 NPC 坐标与地图尺度不一致~~ —— 之前看的是错误的表。真正可用的是
   `npc_game_appearance`，它的 `x/z` 与 frontier 同尺度，见第 5 节。
4. 采集类步骤（`Collect`）只有物品名，没有「哪个采集点会掉」的映射；接 `collection_drop` /
   `collection_marker` 后自动落点 77/155（50%），剩下的多是文案里根本没写物品名。
5. 部分地名在文案里带地区前缀（`伊拉·古拉` / `檀佩兰提亚的村落外`），与 marker 的 `target_name` 不完全一致，纯字符串匹配会漏。

## 5. NPC 坐标可以直接当平面坐标用（已实测）

`npc_game_appearance` 的 `x/z` **不是**世界坐标，也不需要拟合：它的数值范围与同图
`frontiernav_map_marker.frontier_x/y` 一致，直接当 `fx/fy` 用即可。

| 地图（`game_map` → `map.id`） | NPC `x` 范围 | 同图 `frontier_x` 范围 |
| --- | --- | --- |
| `ma05a_c_03` → `gormott-torigoth-upper`（古拉·托里格镇，228 行） | -174.2 ~ 164.5 | -174.2 ~ 172.3 |
| `ma07a_c_floor_01` → `uraya-fonsa-myma`（英维迪亚·首都冯斯麦姆，127 行） | -139.7 ~ 71.8 | -153.3 ~ 114.2 |

`Gazetteer.matchNpc()` 的三条约束：

- 只取 `JOIN map m ON m.name = g.game_map` 能连上的行（1,676 行里 923 行，255 个 NPC）；
  黄金之国 DLC 的 NPC `game_map` 为空，会被 JOIN 掉，不会钉到错图。
- 名字按长度降序匹配，否则「阿尔巴」会命中「阿尔巴马格」。
- 有地区提示时只接受 `m.display_name` 同地区的出现记录，宁可放弃也不乱钉。

实测新增 12 步命中（`tools/anchoring_stats.py` 的「NPC」一栏）。


## 6. 开环需求的可定位上限

`blade_affinity_level` 共 2,372 条非空需求，其中 **444 条是「初始习得」**——`Models.AffinityLevel.learned`
把它当作已学，`Planner.buildBladePlan` 默认跳过，所以真正进入计划的只有 **1,928 条**。
（`tools/blade_anchoring.py` 默认按 1,928 统计，`--include-initial` 可把 444 条算回来。）

按当前定位链跑出来的分布：

| 落点方式 | 条数 | 占比 |
| --- | --- | --- |
| 未定位 | 1,495 | 77.5% |
| 敌人 | 186 | 9.6% |
| 地名 | 171 | 8.9% |
| 图级（只到地区主图） | 48 | 2.5% |
| 采集 | 14 | 0.7% |
| 敌人族（「XX 族」推断） | 13 | 0.7% |
| NPC | 1 | 0.1% |

未定位的大头是纯计数需求，文案里没有任何地点：

| 需求关键词 | 条数 |
| --- | --- |
| 提升信赖度 | 182 |
| 使用异刃连击 | 105 |
| 使用收纳包道具 | 102 |
| 发动御刃者连击 | 91 |
| 造成累计伤害 | 63 |
| 战斗中将牵绊提升至最高 | 52 |

这些不是匹配失败，是数据里就没有「在哪做」的信息。App 的做法是补一条「怎么刷」提示，
不硬钉一个假地点。

敌人刷新点的两条约束（`Planner.buildBladePlan`）：

- 需求里写了地区（`Gazetteer.matchRegion`）时，只接受 `map.display_name` 同地区的刷新点；
  只在别处刷的记进 `enemyElsewhere`，detail 里提示「刷新点实际在 X」，不跨大陆钉点。
- 库里**没有族表**。`Gazetteer.matchEnemyFamily` 从「…以外的甲狼兽族」切出「甲狼兽」，
  再确认有敌人名包含它；族名本身查不到刷新点，最终落点仍来自同名敌人。

## 7. 点位「不落在空白区」的两道保险

1. **投影**：`maps/frontiernav-*` 的纵轴是 Web Mercator，`MapMath.mercY` 换算。
   全库 6,239 个点位，按 alpha>24 判地板，线性模型 63.8% 落在地板上，Mercator 97.9%。
2. **吸附**：`MapSnap` 拿地图自己的 alpha 通道做「可走区域」掩码。
   - 半径上限 `MAX_FRAC` 0.16（占图宽）；0.10 时室内小图/多层压平图仍有一截点飞在图外。
   - `snapOrNull()`：吸附不到地板就返回 null，**图标直接不画**。
     点位图标、计划步骤、点击命中判定都走这个；路线折线走宽松版 `snap()`，避免折线断掉。

实测量级：严格模式下全库只会隐藏 **10 / 6239（0.16%）**，其余全部吸到地面上：
`cliffs-of-morytha-lower-level-map` 3、`spirit-crucible-lower-level` 3、
`spirit-crucible-bone-road` 2、`leftheria-fonsett-waters` 1、`cliffs-of-morytha-upper-level-map` 1。
