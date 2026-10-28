# 计划算法契约

## `Planner.buildBladePlan`

```kotlin
fun buildBladePlan(
    blade: BladeSummary,
    nodes: List<AffinityNode>,
    skipLearned: Boolean = true,
    levelIds: Set<Long>? = null,   // null = 全部等级（旧行为）
): List<NewStep>
```

过滤按顺序生效：

1. `levelIds != null` 时先按 `AffinityLevel.id` 过滤。
2. 再按 `skipLearned` 跳过 `level.learned`（`requirement == "初始习得"` 或空）。
3. `requirement` 为空的行不生成步骤。
4. 定位顺序：地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（含族名）> 采集品；
   只有地区时退到该地区主图（`regionMainMap`）。
5. 末尾过 `orderForWalking`。

调用方：`BladeScreens.kt`（勾选后生成）、`OfflineAgent.kt`、`AgentTools.kt`。
**`blade` 参数当前未被使用**；改签名要同时改这三处（都是位置参数调用）。

## `Planner.orderForWalking`

```kotlin
fun orderForWalking(steps: List<NewStep>): List<NewStep>
```

纯启发式，O(n²)，只在生成计划时算一次。

| 规则 | 说明 |
|------|------|
| 可定位 | `mapId != null && fx != null && fy != null && drawableOn(mapId, fx, fy)` |
| 分组 | 按 `mapId`，组序 = 该图在输入里**首次出现**的位置 |
| 组内 | 最近邻贪心；并列距离取先出现的元素（稳定） |
| 跨组 | 游标延续上一组最后一个坐标；第一组保留本组第一个点在首位 |
| 不可定位 | 按原相对顺序统一追加到末尾 |
| 全部不可定位 | 原样返回 `steps` |

`drawableOn` 用 `MapMath.normalized(info, fx, fy) != null` 判定，与地图页过滤口径一致
（`WikiRepo.map` 有 `mapById` 缓存，不额外查库）。**图查不到时按有坐标处理**，不要在这里抛错。

### 不要做的事

- 不要改成全局 TSP：`route_connection` 表为空，没有真实路网，跨图距离无意义。
- 不要把任务计划（`buildQuestPlan`）也接上 `orderForWalking`：任务步骤有剧情先后顺序。

## 采集候选排序

```kotlin
.sortedWith(
    compareByDescending<ScoredCollect> { it.score }
        .thenByDescending { dropPercent(it.candidate.probability) },
)
```

`collection_drop.probability` 库里两套口径并存（`1.0` 与 `35.0`），**必须**先过 `dropPercent`
再比较，否则 `1.0`（=100%）会排在 `35.0`（=35%）后面。

## 路线规划（Beta）

- `Prefs.routeBeta` 默认 `false`；关闭时不调用 `RouteGraph.routeThrough`，不渲染
  「路线估算」胶囊与 `GuidanceBar`（`‹ ›`）。
- `MapCanvas` 由 `overlay` 生成的计划点折线**不受开关影响** —— 那是默认体验的一部分。
- `AgentTools.route` 不受开关约束。
