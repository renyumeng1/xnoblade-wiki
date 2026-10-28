package cn.xenoblade2.next.agent

import android.content.Context

/**
 * Agent 连接配置。默认空 → 走完全离线的规则规划器，不发任何网络请求。
 * 兼容 OpenAI /chat/completions 协议（自建 Ollama、DeepSeek、通义、vLLM 都能填）。
 */
class AgentSettings(context: Context) {

    private val prefs = context.getSharedPreferences("xc2_next", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("agent_base_url", "") ?: ""
        set(v) = prefs.edit().putString("agent_base_url", v.trim()).apply()

    var apiKey: String
        get() = prefs.getString("agent_api_key", "") ?: ""
        set(v) = prefs.edit().putString("agent_api_key", v.trim()).apply()

    /** 留空 = 请求里不带 model 字段。是否合法取决于端点：Ollama/vLLM 常有默认模型，部分中转端点会 400。 */
    var model: String
        get() = prefs.getString("agent_model", "") ?: ""
        set(v) = prefs.edit().putString("agent_model", v.trim()).apply()

    var temperature: Float
        get() = prefs.getFloat("agent_temp", 0.2f)
        set(v) = prefs.edit().putFloat("agent_temp", v).apply()

    val remoteEnabled: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()

    /** 统一去掉末尾斜杠，允许用户直接粘贴 .../v1 或完整 endpoint。 */
    fun endpoint(): String {
        val base = baseUrl.trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }
}
