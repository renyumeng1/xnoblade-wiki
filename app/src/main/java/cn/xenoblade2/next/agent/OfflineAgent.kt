package cn.xenoblade2.next.agent

import cn.xenoblade2.next.data.WikiRepo
import cn.xenoblade2.next.plan.Gazetteer
import cn.xenoblade2.next.plan.PlanStore
import cn.xenoblade2.next.plan.Planner

/**
 * 没有配模型时的确定性兜底：纯规则匹配意图，不联网、不生成文本。
 * 覆盖四类高频诉求：开环规划、任务规划、地点检索、计划查看。
 */
object OfflineAgent {

    private val BATTLE_HINT = listOf("开环", "环", "异刃")

    fun handle(input: String, onPlanChanged: () -> Unit): String {
        val text = input.trim()
        if (text.isEmpty()) return help()

        if (text.contains("计划") && (text.contains("查看") || text.contains("列表") || text == "计划")) {
            val plans = PlanStore.plans()
            if (plans.isEmpty()) return "还没有计划。可以发「规划 名流帽」或「开环 焰」。"
            return buildString {
                append("本机有 ${plans.size} 份计划：\n")
                plans.take(12).forEach {
                    append("· #${it.id} ${it.title} —— ${it.doneCount}/${it.stepCount}\n")
                }
            }.trim()
        }

        val blade = matchBlade(text)
        if (blade != null && (BATTLE_HINT.any { text.contains(it) } || text.length <= blade.name.length + 2)) {
            return planBlade(blade, onPlanChanged)
        }

        val quest = matchQuest(text)
        if (quest != null) return planQuest(quest.id, onPlanChanged)

        val place = Gazetteer.matchPlaces(text, null, 1).firstOrNull()
        if (place != null) {
            val m = WikiRepo.marker(place.featureId)
            if (m != null && !m.mapId.isNullOrBlank()) {
                val region = WikiRepo.map(m.mapId)?.region ?: ""
                return "${place.name} 在 $region（地图 ${m.mapId}），坐标 ${fmt(m.frontierX)}, ${fmt(m.frontierY)}。\n" +
                    "点「地图」页选这张图就能看到它。"
            }
        }

        val enemy = Gazetteer.matchEnemy(text)
        if (enemy != null) {
            val spawns = WikiRepo.enemySpawns(enemy, 6)
            if (spawns.isNotEmpty()) {
                return "「$enemy」有 ${spawns.size} 个刷新点，主要分布：\n" + spawns.take(5).joinToString("\n") { e ->
                    val m = WikiRepo.marker(e.featureId)
                    val region = WikiRepo.map(m?.mapId)?.let { "${it.region} · ${it.area}" } ?: e.mapId
                    "· $region" + (if (e.level > 0) "（Lv${e.level}）" else "")
                }
            }
        }

        return help()
    }

    private fun matchBlade(text: String): cn.xenoblade2.next.data.BladeSummary? =
        WikiRepo.blades().filter { it.name.length >= 1 && text.contains(it.name) }.maxByOrNull { it.name.length }

    private fun matchQuest(text: String): cn.xenoblade2.next.data.QuestSummary? =
        WikiRepo.quests(null, "", 2000)
            .filter { it.display.length >= 2 && text.contains(it.display) }
            .maxByOrNull { it.display.length }

    private fun planBlade(blade: cn.xenoblade2.next.data.BladeSummary, onPlanChanged: () -> Unit): String {
        val nodes = WikiRepo.bladeNodes(blade.id)
        val steps = Planner.buildBladePlan(blade, nodes)
        if (steps.isEmpty()) return "${blade.name} 的开环需求都已经记完了。"
        val planId = PlanStore.createPlan("开环 · ${blade.name}", "blade", blade.id.toString())
        PlanStore.addSteps(planId, steps)
        onPlanChanged()
        val located = steps.count { it.mapId != null }
        val byMap = steps.filter { it.mapId != null }.groupingBy { it.mapId!! }.eachCount()
        return buildString {
            append("已生成「${blade.name}」开环计划 #$planId，共 ${steps.size} 级需求。\n")
            append("能直接落图的有 $located 条")
            if (byMap.isNotEmpty()) {
                append("，分布在：")
                append(byMap.entries.sortedByDescending { it.value }.take(4).joinToString("、") { (m, c) ->
                    "${WikiRepo.map(m)?.area ?: m}×$c"
                })
            }
            append("。\n")
            append("前三条：\n")
            steps.take(3).forEach { append("· ${it.title}\n") }
            append("去「计划」页打勾推进，同图步骤已按最近邻排好顺序。")
        }
    }

    private fun planQuest(questId: Long, onPlanChanged: () -> Unit): String {
        val d = WikiRepo.questDetail(questId) ?: return "任务详情缺失。"
        val steps = Planner.buildQuestPlan(d)
        if (steps.isEmpty()) return "「${d.title}」没有可拆解的步骤（库里没有 task_workflow_step）。"
        val planId = PlanStore.createPlan("任务 · ${d.title}", "quest", d.id.toString())
        PlanStore.addSteps(planId, steps)
        onPlanChanged()
        val located = steps.count { it.mapId != null }
        return buildString {
            append("已生成「${d.title}」计划 #$planId：${steps.size} 步，其中 $located 步能直接钉到地图。\n")
            if (d.locations.isNotEmpty()) append("接取地点：${d.locations.joinToString("；")}\n")
            append("按顺序执行：\n")
            steps.take(5).forEach { append("· ${it.title}\n") }
            if (steps.size > 5) append("……其余 ${steps.size - 5} 步在「计划」页。")
        }
    }

    private fun fmt(v: Double) = String.format("%.2f", v)

    private fun help() = """
        当前是离线规划模式（设置里填 Base URL + Key 可切到模型驱动）。能直接听懂：
        · 规划 名流帽 —— 把任务拆成带地图点位的步骤
        · 开环 焰 —— 列出未完成环需求并自动定位
        · 甲狼兽在哪 / 阿伐利缇亚市场 —— 查刷新点与地标坐标
        · 查看计划 —— 列出本机计划进度
    """.trimIndent()
}
