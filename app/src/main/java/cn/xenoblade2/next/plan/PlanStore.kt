package cn.xenoblade2.next.plan

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import cn.xenoblade2.next.data.Marker
import cn.xenoblade2.next.data.dblOrNull
import cn.xenoblade2.next.data.int
import cn.xenoblade2.next.data.lng
import cn.xenoblade2.next.data.str
import cn.xenoblade2.next.data.strOrNull
import java.io.File

data class Plan(
    val id: Long,
    val title: String,
    val sourceType: String,
    val sourceId: String,
    val createdAt: Long,
    val stepCount: Int = 0,
    val doneCount: Int = 0,
) {
    val progress: Float get() = if (stepCount == 0) 0f else doneCount.toFloat() / stepCount
}

/** 一条可执行的计划步骤：可以钉在地图某点，也可以只是文字。 */
data class PlanStep(
    val id: Long,
    val planId: Long,
    val ord: Int,
    val title: String,
    val detail: String,
    val mapId: String?,
    val fx: Double?,
    val fy: Double?,
    val featureId: String?,
    /** wiki 游戏内截图文件名（采集点等），为空表示没有参考图。 */
    val shot: String? = null,
    val done: Boolean,
) {
    val pinned: Boolean get() = !mapId.isNullOrBlank() && fx != null && fy != null
}

data class NewStep(
    val title: String,
    val detail: String = "",
    val mapId: String? = null,
    val fx: Double? = null,
    val fy: Double? = null,
    val featureId: String? = null,
    val shot: String? = null,
)

/**
 * 用户计划单独存一个可写库，和只读的 wiki 库分开：
 * wiki 库随 assets 更新会被整体覆盖，用户数据不能放里面。
 */
object PlanStore {

    private var db: SQLiteDatabase? = null

    private val database: SQLiteDatabase get() = db ?: error("PlanStore.init() 还没调用")

    fun init(context: Context) {
        if (db != null) return
        val f = File(context.filesDir, "plans.db")
        val opened = SQLiteDatabase.openOrCreateDatabase(f, null)
        opened.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plan(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                source_type TEXT NOT NULL DEFAULT '',
                source_id TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        opened.execSQL(
            """
            CREATE TABLE IF NOT EXISTS plan_step(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id INTEGER NOT NULL,
                ord INTEGER NOT NULL,
                title TEXT NOT NULL,
                detail TEXT NOT NULL DEFAULT '',
                map_id TEXT,
                fx REAL,
                fy REAL,
                feature_id TEXT,
                shot TEXT,
                done INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        if (!hasColumn(opened, "plan_step", "shot")) {
            opened.execSQL("ALTER TABLE plan_step ADD COLUMN shot TEXT")
        }
        opened.execSQL(
            """
            CREATE TABLE IF NOT EXISTS affinity_progress(
                level_id INTEGER PRIMARY KEY,
                current INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        opened.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pin_override(
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                step_position INTEGER NOT NULL,
                map_id TEXT NOT NULL,
                fx REAL NOT NULL,
                fy REAL NOT NULL,
                feature_id TEXT,
                PRIMARY KEY(source_type, source_id, step_position)
            )
            """.trimIndent(),
        )
        db = opened
    }

    // ---------- 计划 ----------

    @Synchronized
    fun createPlan(title: String, sourceType: String = "", sourceId: String = ""): Long {
        val cv = ContentValues().apply {
            put("title", title)
            put("source_type", sourceType)
            put("source_id", sourceId)
            put("created_at", System.currentTimeMillis())
        }
        return database.insert("plan", null, cv)
    }

    @Synchronized
    fun plans(): List<Plan> {
        val out = ArrayList<Plan>()
        database.rawQuery(
            """
            SELECT p.id, p.title, p.source_type, p.source_id, p.created_at,
                   (SELECT COUNT(*) FROM plan_step s WHERE s.plan_id = p.id) AS total,
                   (SELECT COUNT(*) FROM plan_step s WHERE s.plan_id = p.id AND s.done = 1) AS done
            FROM plan p ORDER BY p.created_at DESC
            """.trimIndent(),
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Plan(
                        id = c.lng("id"),
                        title = c.str("title"),
                        sourceType = c.str("source_type"),
                        sourceId = c.str("source_id"),
                        createdAt = c.lng("created_at"),
                        stepCount = c.int("total"),
                        doneCount = c.int("done"),
                    ),
                )
            }
        }
        return out
    }

    @Synchronized
    fun renamePlan(planId: Long, title: String) {
        database.update("plan", ContentValues().apply { put("title", title) }, "id = ?", arrayOf(planId.toString()))
    }

    @Synchronized
    fun deletePlan(planId: Long) {
        database.delete("plan_step", "plan_id = ?", arrayOf(planId.toString()))
        database.delete("plan", "id = ?", arrayOf(planId.toString()))
    }

    @Synchronized
    fun steps(planId: Long): List<PlanStep> {
        val out = ArrayList<PlanStep>()
        database.rawQuery(
            """
            SELECT id, plan_id, ord, title, detail, map_id, fx, fy, feature_id, shot, done
            FROM plan_step WHERE plan_id = ? ORDER BY ord, id
            """.trimIndent(),
            arrayOf(planId.toString()),
        ).use { c ->
            while (c.moveToNext()) out.add(step(c))
        }
        return out
    }

    private fun step(c: Cursor) = PlanStep(
        id = c.lng("id"),
        planId = c.lng("plan_id"),
        ord = c.int("ord"),
        title = c.str("title"),
        detail = c.str("detail"),
        mapId = c.strOrNull("map_id"),
        fx = c.dblOrNull("fx"),
        fy = c.dblOrNull("fy"),
        featureId = c.strOrNull("feature_id"),
        shot = c.strOrNull("shot"),
        done = c.int("done") == 1,
    )

    @Synchronized
    fun addStep(planId: Long, step: NewStep): Long {
        val next = database.rawQuery(
            "SELECT COALESCE(MAX(ord), 0) + 1 FROM plan_step WHERE plan_id = ?",
            arrayOf(planId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val cv = ContentValues().apply {
            put("plan_id", planId)
            put("ord", next)
            put("title", step.title)
            put("detail", step.detail)
            put("map_id", step.mapId)
            put("fx", step.fx)
            put("fy", step.fy)
            put("feature_id", step.featureId)
            put("shot", step.shot)
            put("done", 0)
        }
        return database.insert("plan_step", null, cv)
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == column) return true
            }
            false
        }

    /** 手动钉点：把某一步绑到地图坐标（用于自动定位失败的步骤）。 */
    @Synchronized
    fun setStepPoint(stepId: Long, mapId: String, fx: Double, fy: Double) {
        database.update(
            "plan_step",
            ContentValues().apply {
                put("map_id", mapId)
                put("fx", fx)
                put("fy", fy)
            },
            "id = ?",
            arrayOf(stepId.toString()),
        )
    }

    // ---------- 自动定位的采集点候选：用户点选后记住，重算不覆盖 ----------

    @Synchronized
    fun pinOverrides(sourceType: String, sourceId: String): Map<Int, Marker> {
        val out = HashMap<Int, Marker>()
        database.rawQuery(
            "SELECT step_position, map_id, fx, fy, feature_id FROM pin_override " +
                "WHERE source_type = ? AND source_id = ?",
            arrayOf(sourceType, sourceId),
        ).use { c ->
            while (c.moveToNext()) {
                val mapId = c.str("map_id")
                out[c.int("step_position")] = Marker(
                    featureId = c.strOrNull("feature_id") ?: "",
                    mapId = mapId,
                    mapGameName = "",
                    frontierX = c.dblOrNull("fx") ?: 0.0,
                    frontierY = c.dblOrNull("fy") ?: 0.0,
                    worldX = null,
                    worldZ = null,
                    targetType = "Manual",
                    targetName = "手动钉点",
                    targetId = "",
                )
            }
        }
        return out
    }

    @Synchronized
    fun setPinOverride(sourceType: String, sourceId: String, stepPosition: Int, marker: Marker) {
        val mapId = marker.mapId.takeIf { it.isNotBlank() } ?: return
        database.execSQL(
            """
            INSERT INTO pin_override(source_type, source_id, step_position, map_id, fx, fy, feature_id)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(source_type, source_id, step_position) DO UPDATE SET
                map_id = excluded.map_id, fx = excluded.fx, fy = excluded.fy,
                feature_id = excluded.feature_id
            """.trimIndent(),
            arrayOf<Any?>(sourceType, sourceId, stepPosition, mapId, marker.frontierX, marker.frontierY, marker.featureId),
        )
    }

    @Synchronized
    fun clearPinOverride(sourceType: String, sourceId: String, stepPosition: Int) {
        database.delete(
            "pin_override",
            "source_type = ? AND source_id = ? AND step_position = ?",
            arrayOf(sourceType, sourceId, stepPosition.toString()),
        )
    }

    @Synchronized
    fun addSteps(planId: Long, steps: List<NewStep>): Int {
        database.beginTransaction()
        try {
            steps.forEach { addStep(planId, it) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return steps.size
    }

    @Synchronized
    fun toggleStep(stepId: Long, done: Boolean) {
        database.update(
            "plan_step",
            ContentValues().apply { put("done", if (done) 1 else 0) },
            "id = ?",
            arrayOf(stepId.toString()),
        )
    }

    @Synchronized
    fun deleteStep(stepId: Long) {
        database.delete("plan_step", "id = ?", arrayOf(stepId.toString()))
    }

    @Synchronized
    fun moveStep(stepId: Long, up: Boolean) {
        val row = database.rawQuery(
            "SELECT plan_id, ord FROM plan_step WHERE id = ?",
            arrayOf(stepId.toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) to it.getInt(1) else return }
        val (planId, ord) = row
        val neighbourOrd = database.rawQuery(
            "SELECT ord FROM plan_step WHERE plan_id = ? AND ord ${if (up) "<" else ">"} ? ORDER BY ord ${if (up) "DESC" else "ASC"} LIMIT 1",
            arrayOf(planId.toString(), ord.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else return }
        database.update("plan_step", ContentValues().apply { put("ord", neighbourOrd) }, "id = ?", arrayOf(stepId.toString()))
        database.update(
            "plan_step",
            ContentValues().apply { put("ord", ord) },
            "plan_id = ? AND ord = ? AND id <> ?",
            arrayOf(planId.toString(), neighbourOrd.toString(), stepId.toString()),
        )
    }

    // ---------- 开环进度 ----------

    @Synchronized
    fun affinityProgress(levelId: Long): Int =
        database.rawQuery(
            "SELECT current FROM affinity_progress WHERE level_id = ?",
            arrayOf(levelId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun allAffinityProgress(): Map<Long, Int> {
        val out = HashMap<Long, Int>()
        database.rawQuery("SELECT level_id, current FROM affinity_progress", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getInt(1)
        }
        return out
    }

    @Synchronized
    fun setAffinityProgress(levelId: Long, current: Int) {
        database.execSQL(
            "INSERT INTO affinity_progress(level_id, current) VALUES(?, ?) " +
                "ON CONFLICT(level_id) DO UPDATE SET current = excluded.current",
            arrayOf<Any>(levelId, current.coerceAtLeast(0)),
        )
    }
}
