# Compose 状态与主题

## 场景：读全局设置（`Prefs`）

### 1. Scope / Trigger

`Prefs`（`data/Prefs.kt`）里每个字段都是 `by mutableStateOf(...) private set`，写入口只有
`applyXxx()`。页面要在设置变更后**立即**刷新，就必须让 Compose 观测到这次快照读。

### 2. Signatures

```kotlin
object Prefs {
    var tabKeys by mutableStateOf(DEFAULT_TAB_KEYS) private set
    var routeBeta by mutableStateOf(false) private set      // 路线规划（Beta），默认关
    var mapShowsMarked by mutableStateOf(true) private set

    fun applyTabKeys(keys: List<String>)
    fun applyRouteBeta(value: Boolean)
}
```

### 3. Contracts

- 读取：在 `@Composable` 函数体里直接 `val tabs = Prefs.tabKeys`；需要在副作用里跟着变时，
  把值放进 `LaunchedEffect` 的 key。
- 写入：只调 `applyXxx()`，它同时更新快照与 `SharedPreferences`。
- 默认值：`routeBeta = false`。不要为了让「旧行为看起来没坏」把它改成 `true`。

### 4. Validation & Error Matrix

| 条件 | 结果 |
|------|------|
| 组合里直接读 `Prefs.x` | 设置页改动 → 同帧重组 |
| `remember { Prefs.x }` | 包装失效：改了设置不刷新（真实 bug，见第 7 节） |
| `remember(version) { Prefs.x }` 且 version 不自增 | 同样不刷新 |
| 在 `LaunchedEffect` 里读 | 那个读不触发重组，必须把值放进 key |

### 5. Good / Base / Bad Cases

- Good：`val tabs = Prefs.tabKeys` → `buildList { ... }` 里遍历。
- Base：`LaunchedEffect(mapId, planSteps, routeBeta) { if (!routeBeta) return@LaunchedEffect ... }`。
- Bad：`val tabs = remember(tabVersion) { Prefs.tabKeys }`。

### 6. Tests Required

没有 test 源集，用手测替代：

1. 进设置页改配置 → 返回上一页，界面已变。
2. 杀进程重开 → 配置保留。

### 7. Wrong vs Correct

#### Wrong（2026-10-28 真实 bug：用户报「选了 tab 页设置没有切换」）

```kotlin
// SettingsScreen.kt
val tabs = remember(tabVersion) { Prefs.tabKeys }   // tabVersion 点击时不递增
```

#### Correct

```kotlin
// 组合体内直接读 SnapshotState
val tabs = Prefs.tabKeys
```

---

## 场景：暗色主题下的前景色

### 1. Scope / Trigger

`Palette` 有浅/深两套取值。`Palette.Core` 亮色是深青（`#0B6E6A`），暗色是浅青（`#7DCCC6`）。
凡是「`Core` 作底色 + 文字/图标」的组合，前景色不能写死白色。

### 2. Signatures

```kotlin
// ui/theme/Theme.kt
LightColors.primary = Color(0xFF0B6E6A); LightColors.onPrimary = Color(0xFFFFFFFF)
DarkColors.primary  = Color(0xFF7DCCC6); DarkColors.onPrimary  = Color(0xFF063A37)
```

### 3. Contracts

- 底色 `Palette.Core` → 前景必须 `MaterialTheme.colorScheme.onPrimary`。
- 对比度：白字在暗色 `Core` 上约 1.85:1（远低于 4.5:1）；`onPrimary` 约 6.8:1。

### 4. Validation & Error Matrix

| 组合 | 亮色 | 暗色 |
|------|------|------|
| `Core` 底 + `onPrimary` | 6.1:1 | 6.8:1 |
| `Core` 底 + `Color.White` | 6.1:1 | **1.85:1（不可读）** |

### 5. Good / Base / Bad Cases

- Good：`TextStyle(color = MaterialTheme.colorScheme.onPrimary)`。
- Bad：`TextStyle(color = Color.White)` 画在 `drawCircle(Palette.Core)` 上。

### 6. Tests Required

设置 → 外观 → 暗色，逐个屏幕看 `Core` 底的小徽章、序号、勾选标记。

### 7. Wrong vs Correct

#### Wrong

```kotlin
Canvas(Modifier.size(20.dp)) {
    drawCircle(Palette.Core)
    drawText(measurer.measure("1", TextStyle(color = Color.White, fontSize = 10.sp)))
}
```

#### Correct

```kotlin
val onCore = MaterialTheme.colorScheme.onPrimary
Canvas(Modifier.size(20.dp)) {
    drawCircle(Palette.Core)
    drawText(measurer.measure("1", TextStyle(color = onCore, fontSize = 10.sp)))
}
```

---

## 场景：`rememberSaveable` 保存选择集

- `rememberSaveable { mutableStateOf(setOf<Long>()) }` 能正常存取：Compose 的
  `canBeSavedToBundle` 会递归检查 `SnapshotMutableState.value`，`LinkedHashSet` / `EmptySet`
  命中 `java/io/Serializable` 白名单。
- 但**必须带 key**：`rememberSaveable(bladeId) { ... }`。同一组合位置换成另一把异刃时，
  不带 key 会把上一把的 `levelId` 串过去。
- 跨进程恢复的前提是导航栈也参与 saved state；本项目 `Navigator` 用的是 `remember`，
  所以实际不可达 —— 只承诺「会话内（切 tab / 旋转）不丢」。
