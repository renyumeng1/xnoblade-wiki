package cn.xenoblade2.next.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 站点上的「游戏内实景截图」。
 *
 * 采集点文件名规则：`colle_ma02a_c001` → `File:Colle_ma02a_c001-screenshot.jpg`。
 * 截图按需下载到 `cacheDir/wiki-shots/`，不打包进 APK（1530 个采集点会很大）。
 */
object WikiShots {

    private const val API = "https://xenoblade2.cn/api.php"
    private const val THUMB_WIDTH = 900
    private val cache = LruCache<String, Bitmap>(6)

    fun fileNameForCollection(code: String?): String? {
        val c = code?.trim()?.takeIf { it.startsWith("colle_", ignoreCase = true) } ?: return null
        return "Colle_${c.substringAfter("colle_", "")}-screenshot.jpg"
    }

    suspend fun load(context: Context, fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(fileName)?.let { return@withContext it }
        val dir = File(context.cacheDir, "wiki-shots").apply { mkdirs() }
        val file = File(dir, fileName.replace('/', '_'))
        if (!file.exists() || file.length() == 0L) {
            val url = resolveUrl(fileName) ?: return@withContext null
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 20_000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
            }.getOrElse {
                file.delete()
                return@withContext null
            }
        }
        BitmapFactory.decodeFile(file.absolutePath)?.also { cache.put(fileName, it) }
    }

    private fun resolveUrl(fileName: String): String? {
        val title = "File:$fileName"
        val url = buildString {
            append(API)
            append("?action=query&prop=imageinfo&iiprop=url&format=json&iiurlwidth=")
            append(THUMB_WIDTH)
            append("&titles=")
            append(URLEncoder.encode(title, "UTF-8"))
        }
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val pages = JSONObject(body).getJSONObject("query").getJSONObject("pages")
            val page = pages.keys().asSequence().map { pages.getJSONObject(it) }.firstOrNull() ?: return null
            val info = page.optJSONArray("imageinfo") ?: return null
            if (info.length() == 0) return null
            val obj = info.getJSONObject(0)
            obj.optString("thumburl").ifBlank { obj.optString("url") }.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
