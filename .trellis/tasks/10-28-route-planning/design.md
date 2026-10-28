# 设计 · 计划顺序优化与路线串联

## 目标形状

```
计划步骤（生成时已排序）
  ├─ 英维迪亚·腹部：3 步 → 最近邻 A→C→B
  ├─ 檀佩兰提亚·中央平原：2 步 → 从上一组终点起算 B→A
  └─ 未定位：2 步 → 原顺序排在最后
地图页：只画 计划点 + 最近传送点/地标 + 点与点之间的直线折线
```

## 算法（`Planner.orderForWalking`）

输入 `List<NewStep>`，输出同长度重排后的列表。

1. 可定位步骤按 `mapId` 分组，组的顺序 = 该图在输入里首次出现的位置。
2. 组内最近邻：
   - 起点 = 上一组的最后一个坐标（没有则取本组第一个点）；
   - 每轮选距当前点最近的点，贪心 O(n²)，n ≤ 50，足够。
3. 非法坐标（`fx/fy` 为空，或 `MapMath.normalized == null`）不参与，按原顺序追加到末尾。
4. 同图同坐标的步骤保持原有相对顺序（稳定排序，不制造来回跳）。

复杂度 O(n²)，在生成计划时算一次，不在地图渲染时算。

## 采集概率

- `Planner.collectTarget` 的评分已有 `probability / 10` 基础分。
- 调整：候选同分时，按掉率降序作为第二关键字；`scored` 排序改成
  `sortedWith(compareByDescending<ScoredCollect> { it.score }.thenByDescending { it.probability })`。
- 只在「同一处候选」之间比掉率，不跨地图比较。

## 路线功能的开关化

| 位置 | 处理 |
|---|---|
| `Prefs` | 新增 `routeBeta: Boolean`，默认 `false`，存 SharedPreferences |
| 设置页 | 新增「路线规划（Beta）」开关 + 一行说明 |
| `MapScreens` 的 `route` 计算 | 只在 `routeBeta == true` 时跑；关闭时置空 |
| 「路线估算」胶囊 / `GuidanceBar` | 只在 `routeBeta == true` 时渲染 |
| `MapCanvas.routePath` / `highlightPath` | 保留参数；关闭时传空列表 |
| `PlanScreens` 的 `StepHop` | 保留数据结构，只在 `routeBeta` 时展示「路线：…」文案 |
| `plan/RouteGraph` / `plan/RouteGuide` | 原样保留 |

`MapCanvas` 里「计划路线」那段（overlay 之间的青色折线）保留，这就是唯一的连线。
该折线不受开关影响——它是「计划点的连接线」，不是导航。

## 兼容与回滚

- `plan_step` 表结构不变，只是写入顺序不同；旧计划仍按原 `ord` 显示。
- 回滚：把 `Prefs.routeBeta` 默认值改为 `true` 即恢复旧行为，无需改代码结构。

## 权衡

- 不做跨图全局最优（TSP）：收益不抵复杂度，用户体感主要来自「同图不来回跑」。
- 不做真实寻路：`route_connection` 为空，硬做只会给出误导性的转向。
