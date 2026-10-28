# Implement · 功能对齐，视觉和信息架构自研

## Order

1. 抓取 xenoblade2.cn 的地图 JSON、点位坐标、底图、点位图标和条目正文。
2. 再改 `ui-parity`：纸页主题、四分组图鉴、两级地图。
3. 用户手测通过后做 `route-planning`、`affinity-clarity`、`agent-integration`。

不要并行开新功能。

## Checklist

### 抓取

- [ ] 运行 `tools/fetch_xenoblade2_wiki.py`。产物在 `data/wiki-xenoblade2/`，不入库。
- [ ] 确认 `json/Mapinfo`、`json/Gmk`、`maps/`、`icons/` 和主要命名空间正文都在。
- [ ] 和本地 `map`、`collection_marker` 对过之后，才写回 `xenoblade2.db`。缺的补，已有的不整表替换。

### 去原版化

- [ ] `ui/theme/Theme.kt` 换成 `design.md` 第 8.2 节。大标题准备 Newsreader，列表仍用系统无衬线。
- [ ] 图鉴改为四个内嵌分组，不再用两列英文大卡。
- [ ] 底部改为图鉴 / 地图 / 记录 / 设置。去掉红金悬浮按钮。
- [ ] 地图改为地区列表加分层段控件。去掉 Titan 插画卡。
- [ ] 点位图形参考站点 marker，按类型区分，不只换颜色。

### 验证

- [ ] `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew :app:assembleDebug`
- [ ] `adb install -r app/build/outputs/apk/debug/app-debug.apk` 后截图。
- [ ] 和原版截图并排时，主色、分组方式、图标、排版都不应认成同一个应用。

## Rollback

- 抓取目录可以整份删除。
- 界面改动限制在 `app/src/main/java/cn/xenoblade2/next/ui/`。不要改 `plans.db`。

## 2026-10-28 本轮进展（实机已验）

### 计划里的游戏内实景截图

- `data/WikiShots.kt`：`colle_ma02a_c001` → `File:Colle_ma02a_c001-screenshot.jpg`，
  走 `https://xenoblade2.cn/api.php` 取 900px 缩略图，缓存到 `cacheDir/wiki-shots/`，LruCache 6 张。
- `ui/components/WikiShot.kt`：折叠按钮「看现场截图 / wiki 实景」，展开后显示图片并署名 CC BY-NC-SA 4.0。
- 计划步骤（`PlanScreens`）、任务步骤（`QuestScreens`）、地图点位卡（`MarkerCard`，仅 CollectionPoint）三处接入。
- `PlanStore.plan_step` 新增 `shot` 列，带 `ALTER TABLE` 迁移。
- 实机验证：任务·芙涅克斯冒险谭 第 4 步展开后能拿到采集点实景图。

### 计划文案清理

- `Planner.stepDetail()`：备注/正文/地图/关联统一按行去重；备注里已有地图名时不再单列「地图：…」。
- `cleanStepDetail()`（显示层）：老计划里的重复行、「1. … 43.0 分」候选打分、`ma07a_b_floor_01` 内部图层名一并清掉。
- 采集候选不再把打分表写进 detail，改为「有 N 个候选采集点，暂未自动钉点」，明细留给任务页选择器。

### 定位修正

- 有 `mapOnly` 的步骤不再被「同任务主图」覆盖（第 8 步原来会跳回利贝拉里塔斯）。
- 图级兜底先看步骤文本里的地区（第 11 步「在檀佩兰提亚…」落到檀佩兰提亚·中央平原）。
- 采集候选贴在同一处（同图、坐标差 < 8）时直接自动钉点。

### UI

- 设置页「底部 Tab 页设置」：勾选框改成序号圆点（1/2/3），未选中右侧「加入」。
- 系统返回键：栈底不是首页时回首页，不再直接退出 App。
- 任务详情有旧计划时多一个「重新生成」，会先删旧计划再建新的。

### 待办

- [ ] 计划地图的「逐段导航」浮层仍压住地图左上角，考虑移到地图下方或可折叠。
- [ ] 分层地图的点位仍需逐张核对（古拉上层/下层、英维迪亚腹部各层）。

### 第二轮（同日）

- 逐段导航从地图左上角浮层改成地图下方的导航条，不再盖住路线。
- 计划点位徽标改用步骤序号（原来用列表下标，合并后对不上「第 N 步」）。
- 同一地标上的多个步骤合并成一个胶囊徽标（如 `1·2·5·17`），不再只看到最上面那个数字。
