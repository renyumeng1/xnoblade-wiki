package cn.xenoblade2.next.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 只读打开打包在 assets 里的 wiki 数据库。
 *
 * 首次启动时把 assets/xenoblade2.db 复制到 getDatabasePath("wiki.sqlite")，
 * 之后直接 openDatabase(OPEN_READONLY)，不建 Room 实体（90 张表，实体化收益低）。
 */
object Db {

    private const val ASSET_DB = "xenoblade2.db"

    /** 与 release.json 里 databaseVersion=20260921.1 对齐；换了 assets 就改这里。 */
    private const val DB_VERSION = 2_026_0921L

    private var opened: SQLiteDatabase? = null

    val db: SQLiteDatabase
        get() = opened ?: error("Db.init() 还没调用")

    fun init(context: Context) {
        if (opened != null) return
        val target = context.getDatabasePath("wiki.sqlite")
        target.parentFile?.mkdirs()
        val prefs = context.getSharedPreferences("xc2_next", Context.MODE_PRIVATE)
        val needCopy = !target.exists() ||
            target.length() == 0L ||
            prefs.getLong("db_version", -1L) != DB_VERSION
        if (needCopy) {
            val tmp = File(target.absolutePath + ".tmp")
            if (tmp.exists()) tmp.delete()
            context.assets.open(ASSET_DB).use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
            }
            if (target.exists()) target.delete()
            check(tmp.renameTo(target)) { "数据库落盘失败: ${target.absolutePath}" }
            prefs.edit().putLong("db_version", DB_VERSION).apply()
        }
        opened = SQLiteDatabase.openDatabase(
            target.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
    }

    fun <T> list(sql: String, args: List<Any?> = emptyList(), mapper: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(mapper(c))
        }
        return out
    }

    fun <T> first(sql: String, args: List<Any?> = emptyList(), mapper: (Cursor) -> T): T? {
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            return if (c.moveToFirst()) mapper(c) else null
        }
    }

    fun count(sql: String, args: List<Any?> = emptyList()): Int =
        first(sql, args) { it.getInt(0) } ?: 0

    fun quickCheck(): Boolean = try {
        count("SELECT COUNT(*) FROM map") > 0
    } catch (t: Throwable) {
        false
    }
}

/* ---------- Cursor 取值小工具 ---------- */

fun Cursor.str(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
fun Cursor.strOrNull(name: String): String? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getString(i)
}

fun Cursor.dbl(name: String): Double = getDouble(getColumnIndexOrThrow(name))
fun Cursor.dblOrNull(name: String): Double? {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) null else getDouble(i)
}

fun Cursor.lng(name: String): Long = getLong(getColumnIndexOrThrow(name))
fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))
fun Cursor.intOr(name: String, fallback: Int): Int {
    val i = getColumnIndexOrThrow(name)
    return if (isNull(i)) fallback else getInt(i)
}
