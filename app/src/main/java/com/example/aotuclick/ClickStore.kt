package com.example.autoclick

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class ActionType(val label: String, val color: Int) {
    TAP("Chạm", Color.rgb(229, 57, 53)),
    DOUBLE("Chạm đúp", Color.rgb(251, 140, 0)),
    HOLD("Giữ", Color.rgb(142, 36, 170)),
    SWIPE("Kéo", Color.rgb(67, 160, 71))
}

/**
 * x, y   : tâm điểm bắt đầu
 * x2, y2 : điểm kết thúc (chỉ dùng cho SWIPE)
 * delayMs: độ trễ SAU nút này, trước khi sang nút kế tiếp
 * durationMs: thời gian giữ/kéo (0 = mặc định: Giữ 1000ms, Kéo 400ms)
 */
data class ClickAction(
    var type: ActionType = ActionType.TAP,
    var x: Float = 0f,
    var y: Float = 0f,
    var x2: Float = 0f,
    var y2: Float = 0f,
    var delayMs: Long = 500L,
    var durationMs: Long = 0L
) {
    fun effectiveDuration(): Long = when {
        durationMs > 0 -> durationMs
        type == ActionType.HOLD -> 1000L
        type == ActionType.SWIPE -> 400L
        else -> 40L
    }
}

object ClickStore {
    val actions = mutableListOf<ClickAction>()
    var loops = 0            // 0 = lặp vô hạn
    var defaultDelay = 500L

    private const val PREF = "nvpklick"
    private var loaded = false

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        loops = p.getInt("loops", 0)
        defaultDelay = p.getLong("defaultDelay", 500L)
        actions.clear()
        try {
            val arr = JSONArray(p.getString("actions", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                actions.add(
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
                        durationMs = o.optLong("duration", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            actions.clear()
        }
    }

    fun save(ctx: Context) {
        val arr = JSONArray()
        actions.forEach { a ->
            arr.put(
                JSONObject()
                    .put("type", a.type.name)
                    .put("x", a.x.toDouble())
                    .put("y", a.y.toDouble())
                    .put("x2", a.x2.toDouble())
                    .put("y2", a.y2.toDouble())
                    .put("delay", a.delayMs)
                    .put("duration", a.durationMs)
            )
        }
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("loops", loops)
            .putLong("defaultDelay", defaultDelay)
            .putString("actions", arr.toString())
            .apply()
    }
}