# 设计 · 开环计划可选范围

## 状态与数据

- 选择状态只存在 Compose 里：`Set<Long>`（`AffinityLevel.id`），用 `rememberSaveable` 保留到进程被杀。
- 不写 `plan_affinity_progress`，不新增表；生成计划时才落地成 `plan_step`。
- 「已习得」仍以 `AffinityLevel.learned` 为准；已习得等级默认不可选（置灰）。

## 交互契约

| 操作 | 结果 |
|---|---|
| 点节点标题右侧「加入」 | 该节点所有 `!learned` 等级加入选择；已全选时变为「移出」 |
| 点等级行左侧圆点 | 只切换该等级 |
| 顶部 chip「第 N 层」 | 选中所有 `level == N && !learned`；再点一次取消该层 |
| 顶部 chip「全选未习得」 | 选中所有 `!learned` 等级 |
| 顶部 chip「清空」 | 清空选择 |
| 生成按钮 | 选择为空时禁用；否则只把这批等级交给 `Planner.buildBladePlan` |

## 代码改动

1. `Planner.buildBladePlan(blade, nodes, skipLearned)` 增加可选参数
   `levelIds: Set<Long>? = null`；非空时先按 id 过滤 `node.levels`，再走现有定位逻辑。
2. `BladeScreens.kt`：
   - `BladeDetailScreen` 增加 `var picked by rememberSaveable { mutableStateOf(setOf<Long>()) }`；
   - `NodeCard` 增加 `picked` / `onToggleLevel` / `onToggleNode`；
   - 概览卡增加快捷 chip 行与「已选 N 级」计数；
   - 生成按钮的文案与禁用态跟随选择。
3. 不动机型：`AffinityNode.position` 用于排序，`level.level` 用于「第 N 层」。

## 兼容与回滚

- 旧计划不受影响（`plan_step` 结构不变）。
- 回滚点：`buildBladePlan` 的 `levelIds` 默认 `null` 等价于旧行为。

## 权衡

- 不做「跨异刃合并规划」：先把单把异刃的选择做对，避免一次改太多。
- 选择态不入库：用户关掉页面就重置，符合「临时挑一批去做」的用法；后续若要长期保存，再加 `plan_selection` 表。
