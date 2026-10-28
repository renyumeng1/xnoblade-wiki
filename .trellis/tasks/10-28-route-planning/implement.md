# 实施 · 计划顺序优化 + 路线 Beta 开关

## 顺序

1. `data/Prefs.kt`：`routeBeta`（默认 false）+ getter/setter。
2. `ui/screens/SettingsScreen.kt`：外观卡下面新增「路线规划（Beta）」开关。
3. `ui/screens/MapScreens.kt`：
   - 读 `Prefs.routeBeta` 到 `var routeBeta` 并订阅设置变化（`LaunchedEffect` 或进入页面时读取）；
   - `routeBeta == false` 时跳过 `RouteGraph.routeThrough`；
   - 路线胶囊 / `GuidanceBar` 仅在开启时渲染；
   - `routePath` / `highlightPath` 关闭时传 `emptyList()`。
4. `ui/screens/PlanScreens.kt`：`hops` 文案仅在开启时显示。
5. `plan/Planner.kt`：
   - 新增 `orderForWalking(steps)`：按 `mapId` 分组 + 组内最近邻；
   - `buildBladePlan` 末尾调用它；任务计划保持原顺序；
   - 采集候选同分时按掉率降序。

## 验收命令

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

手测：

1. 设置页确认「路线规划（Beta）」默认关闭。
2. 计划 → 看地图：没有「路线估算」，只有点与点连线 + 参考点位。
3. 打开开关，重进地图：路线估算与逐段导航出现；杀掉 App 重开仍为开。
4. 生成一份开环计划：同图步骤连续，不来回跳。

## 回滚点

- 开关默认值改回 `true` 即可恢复旧体验。
- `orderForWalking` 是纯函数，出问题可从 `buildBladePlan` 摘掉。
