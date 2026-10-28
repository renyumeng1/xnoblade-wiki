package cn.xenoblade2.next.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 对齐原版 `ThemeMode`：system / light / dark，原版 1.2.2 首启即为浅色（系统深色时也不跟随），所以默认 LIGHT。 */
enum class ThemeMode(val storage: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "亮色"),
    DARK("dark", "暗色"),
    ;

    fun resolvesToDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storage == value } ?: LIGHT
    }
}

/**
 * 轻量偏好存储：主题模式、底部 Tab 选择、最近访问。
 * 全部走 SharedPreferences，不写进 Wiki 只读库。
 */
object Prefs {
    private const val FILE = "xeno_prefs"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_TABS = "bottom_tab_keys"
    private const val KEY_RECENT = "recent_entries"
    private const val KEY_MARKED = "marked_keys"
    private const val KEY_MAP_FILTERS = "map_filter_keys"
    private const val KEY_MAP_SHOWS_MARKED = "map_shows_marked"

    /** 原版固定「首页 + 设置」，中间最多 3 个可选入口。 */
    const val MAX_OPTIONAL_TABS = 3
    val DEFAULT_TAB_KEYS = listOf("blades", "quests", "monsters")

    private var sp: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var tabKeys by mutableStateOf(DEFAULT_TAB_KEYS)
        private set

    var recents by mutableStateOf<List<String>>(emptyList())
        private set

    /** 已收集标记，key 形如 `blade:910001002` / `quest:123`。 */
    var marked by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 交互地图筛选（原版 `interactive_map_settings`）：null 表示还没改过，用默认 mask。 */
    var mapFilterKeys by mutableStateOf<Set<String>?>(null)
        private set

    /** 原版「显示已收藏」开关，默认开。 */
    var mapShowsMarked by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (sp != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp = p
        themeMode = ThemeMode.fromStorage(p.getString(KEY_THEME, null))
        tabKeys = (p.getString(KEY_TABS, null) ?: DEFAULT_TAB_KEYS.joinToString("|"))
            .split("|")
            .filter { it.isNotBlank() }
            .take(MAX_OPTIONAL_TABS)
            .ifEmpty { DEFAULT_TAB_KEYS }
        recents = (p.getString(KEY_RECENT, null) ?: "")
            .split("|")
            .filter { it.isNotBlank() }
        marked = (p.getString(KEY_MARKED, null) ?: "")
            .split("|")
            .filter { it.isNotBlank() }
            .toSet()
        mapFilterKeys = p.getString(KEY_MAP_FILTERS, null)
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.toSet()
        mapShowsMarked = p.getBoolean(KEY_MAP_SHOWS_MARKED, true)
    }

    fun applyMapFilters(keys: Set<String>) {
        mapFilterKeys = keys
        sp?.edit()?.putString(KEY_MAP_FILTERS, keys.joinToString("|"))?.apply()
    }

    fun applyMapShowsMarked(value: Boolean) {
        mapShowsMarked = value
        sp?.edit()?.putBoolean(KEY_MAP_SHOWS_MARKED, value)?.apply()
    }

    /** 地图点位等无数字 id 的收藏项，直接按字符串 key 存取。 */
    fun isMarkedKey(key: String): Boolean = key in marked

    fun toggleMarkedKey(key: String) {
        val next = marked.toMutableSet()
        if (!next.add(key)) next.remove(key)
        marked = next
        sp?.edit()?.putString(KEY_MARKED, next.joinToString("|"))?.apply()
    }

    fun applyThemeMode(mode: ThemeMode) {
        themeMode = mode
        sp?.edit()?.putString(KEY_THEME, mode.storage)?.apply()
    }

    fun applyTabKeys(keys: List<String>) {
        val next = keys.distinct().take(MAX_OPTIONAL_TABS)
        tabKeys = next
        sp?.edit()?.putString(KEY_TABS, next.joinToString("|"))?.apply()
    }

    fun toggleTab(key: String) {
        val current = tabKeys
        when {
            key in current -> applyTabKeys(current - key)
            current.size < MAX_OPTIONAL_TABS -> applyTabKeys(current + key)
        }
    }

    fun moveTab(key: String, delta: Int) {
        val list = tabKeys.toMutableList()
        val index = list.indexOf(key)
        val target = index + delta
        if (index < 0 || target < 0 || target >= list.size) return
        val item = list.removeAt(index)
        list.add(target, item)
        applyTabKeys(list)
    }

    fun pushRecent(key: String) {
        if (key.isBlank() || key == "home") return
        val next = (listOf(key) + recents.filterNot { it == key }).take(8)
        recents = next
        sp?.edit()?.putString(KEY_RECENT, next.joinToString("|"))?.apply()
    }

    fun markKey(type: String, id: Long): String = "$type:$id"

    fun isMarked(type: String, id: Long): Boolean = markKey(type, id) in marked

    fun setMarked(type: String, ids: Collection<Long>, value: Boolean) {
        if (ids.isEmpty()) return
        val next = marked.toMutableSet()
        ids.forEach { id ->
            val key = markKey(type, id)
            if (value) next.add(key) else next.remove(key)
        }
        marked = next
        sp?.edit()?.putString(KEY_MARKED, next.joinToString("|"))?.apply()
    }
}
