# 实现记录 · ui-parity

任务拆成两批推进，第二批（本文件主体）是 2026-10-28 用户手测反馈后的返工。

## 批 A（已完成，提交 `01e85ef` / `fc87096`）

- [x] `ui/theme/Theme.kt` 拆浅/深两套 `Palette`，默认浅色
- [x] 首页 20 入口卡片 + 搜索 + 浮动操作区
- [x] 异刃页大立绘网格、属性/补正 chip、收集筛选
- [x] 任务页四大类 → 分类列表 → 详情
- [x] 怪物页搜索 + 地区/珍稀种 chip + 分组
- [x] 地图页标记图标、计划点聚焦、实景截图面板

## 批 B（本轮：底栏 Tab 设置返工）

用户实测问题：*「我选了 tab 页设置没有切换」*、*「数字还是没有居中」*。

### 根因

1. `App.kt` 底栏写死 4 项（图鉴/地图/记录/设置），`Prefs.tabKeys` 只影响设置页列表，不影响底栏 →
   表现为「选了没切换」。
2. `SettingsScreen.kt` 里 `remember(tabVersion) { Prefs.tabKeys }` 把快照列表缓存住了，
   点击后 `tabVersion` 不递增就读不到新值，列表原地不动。
3. 序号圆点用 `Box + Text` 居中，受字体行高留白影响，视觉偏上/偏下。

### 改动

- [x] `App.kt`：底栏改为 `图鉴` + `Prefs.tabKeys.mapNotNull { HOME_ENTRY_BY_KEY[it] }` + `设置`，
      `distinctBy { it.screen }` 去重；删除死变量 `topLevel` 与无用 import。
- [x] `App.kt`：`Screen.Archive` 作栈底 Tab 时 `onBack` 退栈无效 → 改为「有栈 pop，否则回图鉴」。
- [x] `SettingsScreen.kt`：拆「底部导航」（序号 + 上移/下移/移除）与「可添加功能」（右侧「添加」）两组。
- [x] `SettingsScreen.kt`：满 3 项点「添加」给中文提示「已达到最多 3 项，先移除一个再添加」，配置不变。
- [x] `SettingsScreen.kt`：`TabIndexDot` 改 `Canvas + rememberTextMeasurer`，按 `getLineTop/getLineBottom`
      的行框居中；数字色改 `MaterialTheme.colorScheme.onPrimary`（暗色下白字对比仅 1.8:1）。
- [x] `SettingsScreen.kt`：直接读 `Prefs.tabKeys`，移除 `remember(tabVersion)` 缓存。

### 验证

- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL（trellis-check 子代理独立复跑一次）
- 待人工：模拟器上确认底栏 5 项不挤、序号居中、满额提示、重启保留

### 遗留（转出到其他任务/后续批次）

- 设置页仍是单页滚动，未做「Tab 设置 / 外观 / 内容偏好 / 关于」分段 —— 需要单独一批。
- 「Core 底 + 白字」在暗色主题下的对比度问题散布在 `QuestScreens.kt` / `MapFilters.kt` /
  `MapScreens.kt` / `BladeScreens.kt`，统一换 `onPrimary` 会一次改多处视觉，等手测确认后再动。
- `TAB_OPTION_KEYS` 顺序与原版（游玩经历/素材规划/异刃…）不同，只影响设置页列表顺序。

## 回滚点

- 批 B 前的提交：`774caf6`。底栏逻辑集中在 `App.kt:160-172` 与 `SettingsScreen.kt` 的
  `底部 Tab 页设置` 段落，可整段回退而不影响其他页面。
