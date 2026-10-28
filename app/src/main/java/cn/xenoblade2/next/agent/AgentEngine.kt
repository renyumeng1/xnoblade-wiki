package cn.xenoblade2.next.agent

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class AgentEvent {
    data class ToolUse(val name: String, val args: String) : AgentEvent()
    data class Delta(val text: String) : AgentEvent()
    data class Final(val text: String) : AgentEvent()
    data class Failure(val message: String) : AgentEvent()
}

/** 多轮对话容器：保留 message 数组，供下一次调用带上上下文。 */
class Conversation {
    val messages: MutableList<JSONObject> = ArrayList()

    fun reset() = messages.clear()

    fun addUser(text: String) {
        messages.add(JSONObject().put("role", "user").put("content", text))
    }
}

/**
 * 工具调用型 Agent 循环。
 *
 * 没配置 Base URL / Key 时直接走 OfflineAgent，保证离线可用、零网络请求。
 */
class AgentEngine(
    private val settings: AgentSettings,
    private val tools: AgentTools,
) {

    companion object {
        private const val MAX_ROUNDS = 8
        private const val TAG = "Xc2Agent"

        private const val SYSTEM_PROMPT = """
你是《异度神剑2》Wiki App 内的行动规划助手。你只能通过工具读取本机离线数据库。
规则：
1. 先给结论，再给依据；用简体中文。
2. 凡涉及"在哪、怎么走、去哪打怪"，必须先调用 find_place / find_enemy / map_markers 拿到 map_id 和坐标，禁止编造地名与坐标。
3. 需要可执行清单时，优先用 create_quest_plan / create_blade_plan；若用户要自定义路线，就用 list_plans 找计划再 add_plan_step 逐条加。生成计划后必须在回答里写「计划 #<plan_id>」，客户端据此显示可点开的入口。
4. 工具返回空或报错就直说"库里没有"，不要猜。
5. 问"去哪采某个物品"用 collect_spots；问"怎么走"用 route_between，按返回的 legs 分段说（每段转向 + 方位 + 距离，最多复述 5~6 段），并说明这是点位图估算、不是真实路网。
6. find_place 的 name 只放单个地名或区域名，地区放 region；返回的 maps 是区域级（无坐标），places 才有点位坐标。
7. 回答不超过 400 字，必要时用短列表。
"""
    }

    suspend fun run(userInput: String, conversation: Conversation, emit: (AgentEvent) -> Unit) {
        conversation.addUser(userInput)

        // 只记「走没走远程、端点是什么、key 长度」，不落 key 本身，方便排查静默回退
        Log.i(TAG, "run remote=${settings.remoteEnabled} url=${settings.endpoint()} keyLen=${settings.apiKey.length}")

        if (!settings.remoteEnabled) {
            val text = try {
                OfflineAgent.handle(userInput) { }
            } catch (t: Throwable) {
                "离线规划出错：${t.message}"
            }
            emit(AgentEvent.Final(text))
            conversation.messages.add(JSONObject().put("role", "assistant").put("content", text))
            return
        }

        repeat(MAX_ROUNDS) { round ->
            val message = try {
                request(conversation.messages)
            } catch (t: Throwable) {
                Log.w(TAG, "request failed", t)
                emit(AgentEvent.Failure("请求失败：${t.message ?: t.javaClass.simpleName}；已回退到离线规划"))
                val text = runCatching { OfflineAgent.handle(userInput) { } }
                    .getOrElse { "离线规划也不可用：${it.message}" }
                emit(AgentEvent.Final(text))
                conversation.messages.add(JSONObject().put("role", "assistant").put("content", text))
                return
            }

            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                val text = message.optString("content").ifBlank { "（模型没有返回内容）" }
                conversation.messages.add(message)
                emit(AgentEvent.Final(text))
                return
            }

            conversation.messages.add(message)
            for (k in 0 until calls.length()) {
                val call = calls.getJSONObject(k)
                val fn = call.optJSONObject("function") ?: continue
                val name = fn.optString("name")
                val rawArgs = fn.optString("arguments").ifBlank { "{}" }
                emit(AgentEvent.ToolUse(name, rawArgs))
                val args = runCatching { JSONObject(rawArgs) }.getOrElse { JSONObject() }
                val result = tools.call(name, args)
                conversation.messages.add(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.optString("id"))
                        .put("content", result),
                )
            }
            if (round == MAX_ROUNDS - 1) {
                emit(AgentEvent.Final("工具调用轮次达到上限，先停在这里。可以让我总结当前计划。"))
            }
        }
    }

    private fun request(messages: List<JSONObject>): JSONObject {
        val payload = JSONObject().apply {
            // 模型名留空就不发这个字段。Ollama / vLLM 常有默认模型，部分中转端点会直接 400（见下方分支）。
            if (settings.model.isNotBlank()) put("model", settings.model)
            put("temperature", settings.temperature.toDouble())
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT.trimIndent()))
                messages.forEach { put(it) }
            })
            put("tools", tools.specs())
            put("tool_choice", "auto")
        }

        val conn = URL(settings.endpoint()).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                // 有些端点（本机实测的自建中转）把 model 当必填，留空直接 400。
                // 直接抛 HTTP 400 用户看不懂，这里换成可操作的提示。
                if (code == 400 && looksLikeModelRequired(body)) {
                    throw IOException("端点要求 model 字段（HTTP 400）：请在设置页填写模型名后重试，如 grok-4.7 / deepseek-chat")
                }
                throw IOException("HTTP $code ${body.take(240)}")
            }
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices")
                ?: throw IOException("响应缺少 choices：${body.take(160)}")
            if (choices.length() == 0) throw IOException("choices 为空")
            return choices.getJSONObject(0).optJSONObject("message")
                ?: throw IOException("choices[0].message 缺失")
        } finally {
            conn.disconnect()
        }
    }

    private fun looksLikeModelRequired(body: String): Boolean {
        val s = body.lowercase()
        return s.contains("model") && (s.contains("required") || s.contains("is needed"))
    }
}
