package com.example.autoclick

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** Các kiểu thao tác. */
enum class ActionType(val label: String, val color: Int) {
    TAP("Chạm", Color.rgb(229, 57, 53)),
    DOUBLE("Chạm đúp", Color.rgb(251, 140, 0)),
    HOLD("Giữ", Color.rgb(142, 36, 170)),
    SWIPE("Kéo", Color.rgb(67, 160, 71))
}

/**
 * Một nút (bước) trong kịch bản.
 * x, y   : tâm điểm bắt đầu (toạ độ màn hình)
 * x2, y2 : điểm kết thúc (chỉ dùng cho SWIPE)
 * delayMs: độ trễ SAU khi thực hiện nút này, trước khi sang nút kế tiếp
 * durationMs: thời gian giữ / kéo (0 = mặc định: Giữ 1000ms, Kéo 400ms)
 */
data class ClickAction(
    var type: ActionType = ActionType.TAP,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var delayMs: Long = 500L,
    var durationMs: Long = 0L,
    var groupFrom: Int = 0,    // nút này kết thúc một nhóm, nhóm bắt đầu từ nút số này (1-based, 0 = không)
    var groupRepeat: Int = 0   // tổng số lần chạy nhóm (>= 2 mới có tác dụng)
) {
    fun effectiveDuration(): Long = when {
        durationMs > 0 -> durationMs
        type == ActionType.HOLD -> 1000L
        type == ActionType.SWIPE -> 400L
        else -> 40L
    }
}

/**
 * Một kịch bản: tên + danh sách nút + điều kiện dừng riêng.
 * loops       : số vòng lặp (0 = vô hạn)
 * stopMinutes : tự dừng sau số phút này (0 = không giới hạn)
 * stopTaps    : tự dừng sau số lần thao tác này (0 = không giới hạn)
 */
class Profile(
    var name: String,
    var loops: Int = 0,
    var stopMinutes: Int = 0,
    var stopTaps: Int = 0,
    val actions: MutableList<ClickAction> = mutableListOf()
)

/** Kho dữ liệu dùng chung giữa Activity và Service, lưu vào SharedPreferences. */
object ClickStore {
    val profiles = mutableListOf<Profile>()
    var activeIndex = 0

    // Cài đặt chung
    var defaultDelay = 500L  // độ trễ mặc định cho nút mới
    var panelAlpha = 90      // độ đậm bảng nổi (30..100)
    var jitterDelayPct = 0   // ngẫu nhiên độ trễ ± % (0 = tắt)
    var jitterPx = 0         // lệch vị trí ngẫu nhiên ± px (0 = tắt)
    var darkMode = true      // chế độ tối (true) / sáng (false) của app và bảng nổi
    var startDelaySec = 0    // đếm ngược trước khi chạy (giây, 0 = chạy ngay)
    var startAt = ""         // hẹn giờ bắt đầu HH:mm[:ss] (trống = không hẹn)
    var volumeHotkey = false // phím giảm âm lượng bật/dừng
    var panelMode = 2        // kiểu bảng điều khiển: 1 = một điểm chỉ chạm, 2 = nhiều điểm (di chuyển + chạm)

    private const val PREF = "nvpklick"
    private var loaded = false

    /** Kịch bản đang chọn (luôn có ít nhất một kịch bản). */
    fun current(): Profile {
        if (profiles.isEmpty()) profiles.add(Profile("Kịch bản 1"))
        if (activeIndex < 0 || activeIndex >= profiles.size) activeIndex = 0
        return profiles[activeIndex]
    }

    // Truy cập nhanh tới dữ liệu của kịch bản đang chọn
    val actions: MutableList<ClickAction>
        get() = current().actions

    var loops: Int
        get() = current().loops
        set(v) { current().loops = v }

    var stopMinutes: Int
        get() = current().stopMinutes
        set(v) { current().stopMinutes = v }

    var stopTaps: Int
        get() = current().stopTaps
        set(v) { current().stopTaps = v }

    fun selectProfile(i: Int) {
        if (i >= 0 && i < profiles.size) activeIndex = i
    }

    fun addProfile(name: String) {
        profiles.add(Profile(name))
        activeIndex = profiles.size - 1
    }

    fun duplicateActive() {
        val c = current()
        val copy = Profile(
            c.name + " (bản sao)",
            c.loops,
            c.stopMinutes,
            c.stopTaps,
            c.actions.map { it.copy() }.toMutableList()
        )
        profiles.add(copy)
        activeIndex = profiles.size - 1
    }

    /** Xoá kịch bản đang chọn. Nếu chỉ còn một kịch bản thì làm trống nó. */
    fun deleteActive() {
        snapshot()
        if (profiles.size <= 1) {
            current().actions.clear()
            return
        }
        profiles.removeAt(activeIndex)
        activeIndex = activeIndex.coerceIn(0, profiles.size - 1)
    }

    // ---------- Lưu / tải ----------

    private fun parseActions(arr: JSONArray): MutableList<ClickAction> {
        val list = mutableListOf<ClickAction>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                ClickAction(
                    type = try {
                        ActionType.valueOf(o.getString("type"))
                    } catch (e: Exception) {
                        ActionType.TAP
                    },
                    x = o.optDouble("x").toFloat(),
                    y = o.optDouble("y").toFloat(),
                    x2 = o.optDouble("x2").toFloat(),
                    y2 = o.optDouble("y2").toFloat(),
                    delayMs = o.optLong("delay", 500L),
                    durationMs = o.optLong("duration", 0L),
                    groupFrom = o.optInt("groupFrom", 0),
                    groupRepeat = o.optInt("groupRepeat", 0)
                )
            )
        }
        return list
    }

    private fun actionsToJson(list: List<ClickAction>): JSONArray {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("type", a.type.name)
                    .put("x", a.x.toDouble())
                    .put("y", a.y.toDouble())
                    .put("x2", a.x2.toDouble())
                    .put("y2", a.y2.toDouble())
                    .put("delay", a.delayMs)
                    .put("duration", a.durationMs)
                    .put("groupFrom", a.groupFrom)
                    .put("groupRepeat", a.groupRepeat)
            )
        }
        return arr
    }

    private fun parseProfiles(arr: JSONArray): List<Profile> {
        val out = mutableListOf<Profile>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Profile(
                    name = o.optString("name", "Kịch bản ${i + 1}"),
                    loops = o.optInt("loops", 0),
                    stopMinutes = o.optInt("stopMinutes", 0),
                    stopTaps = o.optInt("stopTaps", 0),
                    actions = parseActions(o.optJSONArray("actions") ?: JSONArray())
                )
            )
        }
        return out
    }

    private fun profilesToJson(): JSONArray {
        val arr = JSONArray()
        profiles.forEach { pr ->
            arr.put(
                JSONObject()
                    .put("name", pr.name)
                    .put("loops", pr.loops)
                    .put("stopMinutes", pr.stopMinutes)
                    .put("stopTaps", pr.stopTaps)
                    .put("actions", actionsToJson(pr.actions))
            )
        }
        return arr
    }

    private fun scaleActions(list: List<ClickAction>, fx: Float, fy: Float) {
        list.forEach {
            it.x *= fx
            it.y *= fy
            it.x2 *= fx
            it.y2 *= fy
        }
    }

    /** Co giãn toạ độ mọi nút khi kích thước màn hình đổi (xoay màn hình). */
    fun rescale(oldW: Int, oldH: Int, newW: Int, newH: Int) {
        if (oldW <= 0 || oldH <= 0 || newW <= 0 || newH <= 0) return
        if (oldW == newW && oldH == newH) return
        val fx = newW.toFloat() / oldW
        val fy = newH.toFloat() / oldH
        profiles.forEach { scaleActions(it.actions, fx, fy) }
    }

    // ---------- Hoàn tác ----------

    private var undoJson: String? = null
    private var undoActive = 0

    val hasUndo: Boolean get() = undoJson != null

    /** Chụp lại toàn bộ kịch bản trước khi xoá để có thể hoàn tác. */
    fun snapshot() {
        undoJson = profilesToJson().toString()
        undoActive = activeIndex
    }

    fun undo() {
        val raw = undoJson ?: return
        try {
            val list = parseProfiles(JSONArray(raw))
            if (list.isNotEmpty()) {
                profiles.clear()
                profiles.addAll(list)
                activeIndex = undoActive
            }
        } catch (e: Exception) {
        }
        undoJson = null
        current()
    }

    // ---------- Xuất / nhập ----------

    fun exportJson(ctx: Context): String {
        val dm = ctx.applicationContext.resources.displayMetrics
        return JSONObject()
            .put("app", "NvpKlick")
            .put("version", 1)
            .put("sw", dm.widthPixels)
            .put("sh", dm.heightPixels)
            .put("profiles", profilesToJson())
            .toString(2)
    }

    /** Nhập kịch bản từ JSON, thêm vào cuối danh sách. Trả về số kịch bản đã nhập. */
    fun importJson(ctx: Context, text: String): Int {
        val t = text.trim()
        var sw = 0
        var sh = 0
        val arr = if (t.startsWith("[")) {
            JSONArray(t)
        } else {
            val o = JSONObject(t)
            sw = o.optInt("sw", 0)
            sh = o.optInt("sh", 0)
            o.getJSONArray("profiles")
        }
        val list = parseProfiles(arr)
        if (list.isEmpty()) return 0
        val dm = ctx.applicationContext.resources.displayMetrics
        if (sw > 0 && sh > 0 && (sw != dm.widthPixels || sh != dm.heightPixels)) {
            val fx = dm.widthPixels.toFloat() / sw
            val fy = dm.heightPixels.toFloat() / sh
            list.forEach { scaleActions(it.actions, fx, fy) }
        }
        profiles.addAll(list)
        activeIndex = profiles.size - list.size
        return list.size
    }

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        defaultDelay = p.getLong("defaultDelay", 500L)
        panelAlpha = p.getInt("panelAlpha", 90).coerceIn(30, 100)
        jitterDelayPct = p.getInt("jitterDelayPct", 0).coerceIn(0, 100)
        jitterPx = p.getInt("jitterPx", 0).coerceIn(0, 100)
        darkMode = p.getBoolean("darkMode", true)

        profiles.clear()
        activeIndex = 0
        try {
            val raw = p.getString("profiles", null)
            if (raw != null) {
                val arr = JSONArray(raw)
                profiles.addAll(parseProfiles(arr))
                activeIndex = p.getInt("active", 0)
            } else {
                // Chuyển dữ liệu từ v1.0 (chỉ có một danh sách nút)
                profiles.add(
                    Profile(
                        name = "Kịch bản 1",
                        loops = p.getInt("loops", 0),
                        actions = parseActions(JSONArray(p.getString("actions", "[]")))
                    )
                )
            }
        } catch (e: Exception) {
            profiles.clear()
        }
        // Dữ liệu lưu ở kích thước màn hình khác (vd. xoay ngang/dọc) → co giãn theo tỷ lệ
        val dm = ctx.applicationContext.resources.displayMetrics
        rescale(p.getInt("sw", 0), p.getInt("sh", 0), dm.widthPixels, dm.heightPixels)
        startDelaySec = p.getInt("startDelaySec", 0).coerceIn(0, 3600)
        startAt = p.getString("startAt", "") ?: ""
        volumeHotkey = p.getBoolean("volumeHotkey", false)
        panelMode = p.getInt("panelMode", 2).let { if (it == 1) 1 else 2 }
        current()
    }

    fun save(ctx: Context) {
        val arr = profilesToJson()
        val dm = ctx.applicationContext.resources.displayMetrics
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("sw", dm.widthPixels)
            .putInt("sh", dm.heightPixels)
            .putInt("startDelaySec", startDelaySec)
            .putString("startAt", startAt)
            .putBoolean("volumeHotkey", volumeHotkey)
            .putInt("panelMode", panelMode)
            .putLong("defaultDelay", defaultDelay)
            .putInt("panelAlpha", panelAlpha)
            .putInt("jitterDelayPct", jitterDelayPct)
            .putInt("jitterPx", jitterPx)
            .putBoolean("darkMode", darkMode)
            .putString("profiles", arr.toString())
            .putInt("active", activeIndex)
            .apply()
    }
}
