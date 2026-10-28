# 异度神剑 2 · 本地 Wiki（Android）

`cn.xenoblade2.next` —— 一个**离线优先**的异度神剑 2 资料库 + 任务/开环规划器 + 可接大模型的游戏助手。

数据来自 FrontierNav 的导出库（101 张表，包含 76 张地图、6,240 个点位、514 个任务、55 把异刃、2,372 条开环节点需求），数据库随 APK 打包，不联网也能查。

## 现状

| 项 | 状态 |
| --- | --- |
| 编译 | `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL |
| SQL | `tools/check_sql.py`：wiki 库 25 条通过 / 3 条跳过（含插值）/ 0 失败，plans 库 6 条通过 / 1 条跳过 |
| 计划落点率 | `tools/anchoring_stats.py`：1,232 条任务步骤精确落点 867 条（70.4%），另有 178 条（14.4%）只到图；剩下的 187 条里 180 条是黄金之国 DLC（库里 0 张伊拉地图） |
| 真机/模拟器 | 见「验证」一节：模拟器（Pixel 7 / android-35）端到端跑通，含助手页真模型对话 |

## 构建

数据库、地图与物品图标是从原版 APK 提取的资源，**不纳入 git**（版权与体积原因）。首次 clone 后先还原：

```bash
# 需要本地已有原版 APK：apk/xenoblade2Wiki-1.2.2.apk（或已解包的 apk/extracted/）
tools/restore_assets.sh          # 复制到 app/src/main/assets/
tools/restore_assets.sh --verify # 只校验 sha256 与文件数量
```

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # 系统自带的 /usr/bin/java 是坏的
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

AGP 8.7.3 / Kotlin 2.1.0 / Compose BOM（Material3）/ compileSdk 35 / minSdk 26。

## 功能

- **任务**：514 个任务，详情页展开官方步骤（`task_workflow_step`），一键生成带地图坐标的计划。定位分三档：
  - 「精确」：内链地点 > 同地区地名（失败再全局地名兜底，跨区域任务靠这步）> 敌人刷新点 > 采集点 > NPC 出现坐标，直接给 `map_id + fx + fy`；
  - 「图级」：定不到点但同任务多数步骤落在同一张图时，至少把步骤指到那张图（只给 `map_id`）；
  - 「未定位」：留纯文字，可在地图上长按钉点并绑定回去。
- **异刃**：55 把异刃，按节点列出开环每一级的需求、效果与达成数，可勾选进度。生成开环计划时：
  - 定位链：地点内链 > 同地区地名 > 全局地名 > NPC > 敌人（含「XX 族」族名）> 采集品 > 地区主图（图级）；
  - 敌人刷新点只认同地区的：需求写了「在洛修利亚王国打倒 X」而 X 只在莫尔斯断崖刷时，不跨大陆钉点，
    detail 里直接提示「刷新点实际在 X」；
  - 只写地区的需求落到该地区点位最多的那张图（图级定位，UI 标「仅到图」）；
  - 纯战斗/操作类需求补一条「怎么刷」提示（26 条模板，如「使用异刃连击」「提升信赖度」）；
  - 最后按「同图聚集 + 最近邻」重排，减少来回跑图。
- **地图**：76 张 FrontierNav 地图，缩放平移、按类型筛选点位（地标/敌人/采集/宝箱/打捞…），计划步骤按序连线并标序号；同一张图有 ≥2 个已定位步骤时叠加一条 `RouteGraph` 估算路线（点位图 Dijkstra）。
  路线再按有名地标切成「逐段导航」：每段给方位、距离、转向（出发/直行/左转/右转/掉头），当前段在地图上金色高亮，`‹ ›` 切段。
- **计划**：任务/开环/地图选点三种来源统一进 `plans.db`，可勾选、排序、删除；进度条按完成比例。
  已定位步骤下方追加一行「路线：从第 N 步过来 西北 约 67 · 经 X · 2 段」；两步坐标相同时写「与第 N 步是同一位置」，中间跳过的无坐标步骤会标明数量。
- **助手**：两种模式。
  - 未配置模型 → 本地规则引擎（`OfflineAgent`），支持「规划 <任务名>」「开环 <异刃名>」「<敌人>在哪」「查看计划」。
  - 配置任意 OpenAI 兼容端点（DeepSeek / OpenAI / Ollama / vLLM）→ 走 tool calling，模型只能调用 17 个受限工具（查任务、查敌人刷新点、查采集点、估算同图路线、查开环需求、读写计划），**没有任意 SQL 入口**。
  - **模型名是否必填取决于端点**：留空时请求里不带 `model` 字段。Ollama / vLLM 常见「有默认模型」；
    自建中转端点的行为更严——实测某自建中转端点留空直接返回
    `HTTP 400 Field 'model' is required`，App 会把这条错误翻成「请在设置页填写模型名后重试」并回退离线规划。
    填 `grok-4.7` 后恢复正常。因此设置页按「模型名」展示，占位提示写「多数端点必填」。
  - 端点允许 `http://` 明文（局域网自建 Ollama / vLLM 常见）：`AndroidManifest` 开了 `usesCleartextTraffic`，否则系统会拦掉请求并静默回退到离线规划。
  - 回答里的 Markdown 会降级渲染（`**粗体**`、`` `行内代码` ``、`# 标题`、`- ` 列表）；回答里的 `#N` / `plan_id=N` 会变成可点的「打开计划」。
- **设置**：Base URL / API Key / 模型名（多数端点必填）/ temperature，数据统计，清空计划。清空或保存配置后，
  标题、连接标签、提示文案同一帧切换（状态从输入框推导，不读 SharedPreferences 快照）。

## 坐标系

```text
nx = (frontier_x - lower_x) / (upper_x - lower_x)
ny = (frontier_y - upper_z) / (lower_z - upper_z)   # 分母为负，北在上
```

`frontier_y` 已经是墨卡托 y（度），不要再变换。反算（长按钉点用）：

```text
fx = lowerX + nx * (upperX - lowerX)
fy = upperZ + ny * (lowerZ - upperZ)
```

详见 `docs/DATA_NOTES.md`、`docs/ARCHITECTURE.md`、`docs/PLAN.md`。

## 目录

```text
app/src/main/java/cn/xenoblade2/next/
├─ data/     Db（只读 wiki 库）、WikiRepo（唯一 SQL 入口）、MapMath、Models
├─ plan/     PlanStore（plans.db）、Gazetteer（地名/敌人词典）、Planner
├─ agent/    AgentSettings、AgentTools、AgentEngine、OfflineAgent
└─ ui/       App（手写导航栈）、screens、components（含 MapCanvas）
app/src/main/assets/          ← 下列三项不纳入 git，用 tools/restore_assets.sh 还原
├─ xenoblade2.db   34 MB 只读库（原版 APK 提取）
├─ maps/           80 张地图 PNG + 7 个标记图层（原版 APK 提取）
├─ item-icons/     33 个物品分类图标（原版 APK 提取）
└─ blades/         52 张异刃立绘（非 APK 提取，随仓库提交）
tools/       check_sql.py、anchoring_stats.py、blade_anchoring.py、export_affine.py、route_stats.py、llm_probe.py、restore_assets.sh
```

## 验证

```bash
python3 tools/check_sql.py        # 用真库 prepare 所有 SQL
python3 tools/anchoring_stats.py  # 离线复刻 Planner.anchor，统计任务步骤落点率
python3 tools/blade_anchoring.py  # 离线复刻 Planner.buildBladePlan，old/new 两套开环落点对照
python3 tools/blade_anchoring.py --blade 焰 --samples 5   # 单把异刃的逐条定位明细
python3 tools/export_affine.py    # 每图仿射拟合校验
python3 tools/route_stats.py      # 路线图采样：绕行比 / 不连通回退
```

真实模型探针（需要自己的 key，直接查同一个 DB，跑 App 里同款 tool-calling 循环）：

```bash
export LLM_BASE_URL=https://api.deepseek.com/v1
export LLM_MODEL=deepseek-chat
export LLM_API_KEY=sk-...
python3 tools/llm_probe.py
```

最近一次实测（grok-4.7，OpenAI 兼容端点，key 未入库）：**5/5 通过** ——
路线会调 `route_between` 并声明「点位图估算」；采集会调 `collect_spots`；
库里没有的地点/物品会直说没有；跨图会指出 `map_id` 不同而不是硬凑路线。

模拟器（Pixel 7 / android-35 google_apis arm64-v8a，软件渲染）上的端到端实测：

- 首启复制 34 MB 库后首页统计正确：514 任务 / 1,232 步 / 55 异刃 / 2,372 环需求 / 76 图 / 6,240 点位。
- 地图：76 张列表可见；任务计划跳地图后有编号点位与 `RouteGraph` 折线（徽标「路线估算 248 1.20×」）；
  长按地图可把未定位步骤钉在该点，计划随即从 7 步变 6 步、徽标更新为「路线估算 430 1.18×」。
- 逐段导航：计划 #1「重要工作之前」副标题「0/8 已完成 · 7 步已定位 · 4 跳有方位」，
  第 2 步「路线：从第 1 步过来 西北 约 67 · 经 克雷奥化妆品店 · 2 段」，
  第 4 步「东 约 181（跳过 1 步无坐标）· 经 克雷奥化妆品店 · 5 段」，第 5 步「与第 4 步是同一位置」。
  跳 `argentum-bazaar` 后徽标「路线估算 430 1.18×」，卡片「逐段导航 1/11 · 西北 · 32 · 前往 克雷奥化妆品店」，
  连点 `›` 走完 11 段，转向依次出现 左转 / 掉头 / 左转 / 右转，末段「右转 西北 · 22 到达终点」。
  修停靠点判定前同一条折线被切成 24 段，还夹着「前往 Door Point #85」这类编号点；修后 11 段。
- 同名点位重合（计划 #9「名流帽」，第 1、4 步落在同一 marker）：第 4 步显示
  「路线：与第 1 步是同一位置（跳过 2 步无坐标）」，地图页徽标为「计划点重合」，不再出「1/1」导航卡片。
- 助手页接真端点（grok-4.7）对话，五轮全部走通：
  1. 「穿岩之门到克雷尔水脉怎么走」→ `find_place` ×2 + `route_between`，答「点位图估算约 98、绕行比 1.12、不是真实路网」。
  2. 「开环 焰 需要哪些材料」→ `get_blade` + `search_wiki`，10 个节点（含 3 个场景技能）全部列出。
  3. 「就按这个开环生成一份执行计划」→ `create_blade_plan` → 计划 #3，36 步 / 5 步定位（多轮上下文生效）。
  4. 「甲狼兽在哪，怎么刷」→ `find_enemy` + `list_maps` + `map_markers`，给出三种变体与各自坐标。
  5. 「规划 名流帽」→ `search_wiki` + `collect_spots` + `get_quest` + `create_quest_plan` → 计划，
     点回答里的「打开计划」直接进清单页（当时还是旧定位逻辑：13 步 / 1 步定位）。
- `route_between` 逐段复述（本轮）：同一句「古拉穿岩之门到克雷尔水脉怎么走」，
  App 端工具返回 `leg_count=3` 并原样进入回答——「向东 46 → 直行东 36 → 左转东 16」，
  末尾带「起点约 (-69.1, 50.5)，终点约 (18.7, 54.3)」与「点位图估算，不是真实路网」，
  与 `tools/llm_probe.py --case 1` 的输出一致（3 段 / 东 46 / 直行东 36 / 左转东 16）。
- 模型名必填（本轮）：清空 `agent_model` 后提问，端点返回 `HTTP 400 Field 'model' is required`，
  App 显示「请求失败：端点要求 model 字段（HTTP 400）：请在设置页填写模型名后重试，如 grok-4.7 / deepseek-chat；
  已回退到离线规划」，并正常给出离线回答；填回 `grok-4.7` 后同一问题恢复模型驱动（副标题「模型驱动 · grok-4.7」，
  空模型时副标题显示「模型驱动 · 未填模型名」，不再暗示端点一定有默认模型）。
- 名流帽重新实测（本轮新增的三档定位）：任务页统计显示「共 13 步，10 步已定位 · 3 步可到图」；
  生成的计划写入 `plans.db` 后是 13 步全部带 `map_id`、其中 10 步带 `fx/fy`（直接查库核对）。
  计划页副标题「10 步已定位 · 3 步仅到图」，第 1 步跨区域的「前往阿伐利缇亚市场 寻找露洁娜」
  落到 `argentum-bazaar · 阿伐利缇亚市场`（旧逻辑只认接取地英维迪亚，所以定位不到）；
  图级步骤显示「地图 · 格尔特蒙特（无坐标）」，点它能直接进对应地图。
- 开环计划重新实测（本轮新增的族名匹配 / 同地区刷新点 / 图级兜底 / 「怎么刷」）：焰的计划 #8 仍是 36 步，
  已定位从 5 步升到 6 步。第 1 步「火焰新星 Lv2 · 在古拉打倒珍稀种怪物以外的甲狼兽族 0/6」落到
  `gormott-upper-level-right · 甲狼兽的刷新点`（旧逻辑只做整串敌人名匹配，认不出「甲狼兽族」），
  并带「怎么刷：去需求写的地区找同名敌人反复刷；刷新点见地图」；
  「使用异刃连击」那步带「怎么刷：打血厚的敌人，攒满 3 段属性连携再收尾；一场可计 1~2 次」。
  `tools/blade_anchoring.py` 全库对照：2,372 条需求里 444 条是「初始习得」（App 生成计划时跳过），
  进入计划的 1,928 条中可定位 372 → 433 条（19.3% → 22.5%），
  且「旧逻辑钉上、新逻辑没钉上」= 0 条（无回归）。剩下 1,495 条（77.5%）是纯计数需求，见「已知限制」。

- 设置页清空配置后，助手页显示「离线规则模式」；点「查看计划」由 `OfflineAgent` 正常列出
  「#10 开环 · 绵津见 / #9 任务 · 名流帽 / #8 开环 · 焰 / #1 任务 · 重要工作之前」。
  重新填 Base URL + Key + 模型名（`grok-4.7`）保存后立刻回到「模型驱动可用」。
  这里修了一个重组遗漏：改前清空后字段已空、提示已写「离线规则模式」，但标题和 Tag 仍停在
  「模型驱动可用 / 已连接配置」——因为那两处只读 `SharedPreferences`，所在重组范围没有可观察状态。
  改成从 `baseUrl / apiKey` 推导后，保存与清空都能即时刷新（实机验证两个方向）。

模拟器（Apple Silicon）：

```bash
sdkmanager --install "emulator" "system-images;android-35;google_apis;arm64-v8a"
avdmanager create avd -n xc2 -k "system-images;android-35;google_apis;arm64-v8a"
emulator -avd xc2 -no-snapshot -gpu swiftshader_indirect
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 数据来源与授权

- 地点、任务、异刃、地图数据来自 **FrontierNav**（社区项目）与 **Xeno Series Wiki / Fandom**，版权归原社区与 Monolith Soft 所有；本项目只做个人学习用途，不用于商业分发。
- 原 Gitee 项目 `x4595605/xenoblade2-wiki` 未开源：本项目只参考其功能形态（任务攻略 / 地图点位 / 开环查询）后重新实现，反编译源码仅在本地 `src/`、`apk/` 做对照，**不进入 git、不打包**。
- 从原版 APK 提取的数据库、地图、物品分类图标同样**不进入 git**（`.gitignore` 已排除），本地用 `tools/restore_assets.sh` 还原；仓库里只保留自己写的代码、文档与 `blades/` 立绘。
- 未申请 `AD_ID`，未接入任何统计 SDK。

## 已知限制

- `route_connection` 表为空 → 没有真实路网。`RouteGraph` 用同图点位 k 近邻 + 电梯/传送点构建近似图，
  只能给「大致方向 + 途经地标」，不做穿墙/单行道判断。0 条真实边的现状没有变。
  「逐段导航」也建立在这条估算折线上：按有名地标切段后，方位、距离、转向都由平面坐标相减算出，同样不是真实路口。
- 采集（Collect）步骤自动落点 77/155（50%），剩下的是文案里根本没写物品名（如「委托鸣制作时尚的帽子」）。
- 开环需求 2,372 条里 444 条是「初始习得」（计划跳过）；剩下的 1,928 条里 1,495 条（77.5%）是纯计数
  （提升信赖度 / 使用异刃连击 / 行走 / 收集道具），文案里没有地点，钉不上点属数据本身如此；
  能定位 433 条（22.5%），其中图级 48 条（2.5%）。
- 「XX 族」没有族表：靠从需求文本切出族名（如「甲狼兽族」→「甲狼兽」），再按敌人名包含推断刷新点（13 条）。
  族名本身查不到刷新点，最终落点仍是同名敌人的点位。
- 「图级定位」只到图、不给坐标：178/1,232 步（14.4%）落在这一档。计划里点开只会到该图默认视野，
  需要长按地图钉点补坐标（长按弹窗可直接绑定到这条步骤）。
- 黄金之国 DLC 的 180 步全部定不到：库里 0 张伊拉地图，`frontiernav_location` 里也没有伊拉地名，
  属数据缺口，不要再当匹配 bug 查。
- `find_place` 能处理「古拉的穿岩之门」这类组合写法和「里吉特海域」这类区域名：前者拆词后取点位，
  后者返回 `maps`（区域级、无坐标）。但区域名本身不是单一点位，不能直接当路线端点。
- 76 张图里 41 张能拟合「世界坐标 → 平面坐标」，其余多为室内小图、样本不足；但 `enemy_spawn` 3,385/3,689
  行可直接 join 到 marker 拿平面坐标，不走拟合。
- 工具输出上限 6,000 字符，超限返回 `{"truncated": true, "preview": "..."}` 信封（不再把 JSON 截成半截，
  此前 `get_blade` 在 2,600 字符处被截断，模型只能回「后半被截断」）。`get_blade` 单次最多 24 个节点，
  超出会在 `omitted_nodes` 里报数量。

## 大模型能补什么、不能补什么

接入大模型解决的是「把玩家的话翻译成对库的操作」，不是「替库里没有的数据编答案」。

**能补的**：

- 意图编排：一句「先把焰开到 5 环，再去打名流帽」拆成 `get_blade` → `create_blade_plan` →
  `search_wiki` → `create_quest_plan`，并按依赖排序。
- 选点和解释：在多个同名敌人/多个采集点里挑一个合理的，说明「为什么先做这步」「这张图要带什么」。
- 纠错：工具返回空结果时换关键词重查（`find_enemy` → `search_wiki`），而不是直接说「没有」。
- 长文本归纳：把 36 步开环计划压成「先刷信赖度，再打这 4 个敌人」这种可读摘要。

**不能补的**（换更大的模型也没用）：

- `route_connection` 为空 → 模型没有真实路权。`route_between` 仍是同图点位 kNN 图上的 Dijkstra，
  只能给「大致方向 + 途经地标」；让模型「凭游戏记忆」报路线就是幻觉。
- 物品 → 采集点没有映射 → 模型不知道「某某草」在哪个采集点掉，只能猜；
  任务 Collect 落点 50%、开环纯计数需求 77.5% 不可定位，都是数据缺口。
- 76 张图里 35 张样本不足、拟合不出「世界坐标 → 平面坐标」→ 模型算不出坐标，
  只能落到图级或让用户长按钉点。
- 族名 → 刷新点没有族表 → 「XX 族」只能按同名敌人推断，不能凭空生成刷新点。

所以正确分工是：**模型做编排层（选工具、排顺序、解释、纠错），数据层保证事实**。
要真正提升路线质量，得补 `route_connection`、`collection_drop`/采集点映射、地图控制点，
而不是只换模型。实测边界见 `tools/llm_probe.py`（grok-4.7 5/5：会声明「点位图估算」、
空结果会直说没有、跨图会指出 `map_id` 不同）。
