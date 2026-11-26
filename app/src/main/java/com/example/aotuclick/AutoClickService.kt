package com.example.autoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {

    companion object {
        @Volatile var instance: AutoClickService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var generation = 0
    private var onFinished: (() -> Unit)? = null

    val isRunning: Boolean get() = running

    /**
     * Chạy kịch bản theo thứ tự các nút.
     * loops = 0 → lặp vô hạn; loops = n → lặp n vòng rồi tự dừng.
     */
    fun run(list: List<ClickAction>, loops: Int, onDone: () -> Unit) {
        stop()
        if (list.isEmpty()) {
            onDone()
            return
        }
        running = true
        generation++
        onFinished = onDone
        step(generation, list.map { it.copy() }, 0, 0, loops)
    }

    fun stop() {
        val wasRunning = running
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        val cb = onFinished
        onFinished = null
        if (wasRunning) cb?.invoke()
    }

    private fun finishRun() {
        running = false
        generation++
        val cb = onFinished
        onFinished = null
        cb?.invoke()
    }

    private fun step(gen: Int, list: List<ClickAction>, idx: Int, round: Int, loops: Int) {
        if (!running || gen != generation) return

        if (idx >= list.size) {
            val done = round + 1
            if (loops > 0 && done >= loops) {
                finishRun()
            } else {
                step(gen, list, 0, done, loops)
            }
            return
        }

        val a = list[idx]
        fun next() {
            if (running && gen == generation) {
                handler.postDelayed(
                    { step(gen, list, idx + 1, round, loops) },
                    a.delayMs.coerceAtLeast(0L)
                )
            }
        }
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = next()
            override fun onCancelled(gestureDescription: GestureDescription?) = next()
        }

        val ok = dispatchGesture(buildGesture(a), callback, null)
        if (!ok) {
            // Không gửi được cử chỉ → thử lại bước kế tiếp sau một nhịp ngắn, tránh vòng lặp nóng
            handler.postDelayed(
                { step(gen, list, idx + 1, round, loops) },
                a.delayMs.coerceAtLeast(50L)
            )
        }
    }

    private fun buildGesture(a: ClickAction): GestureDescription {
        val maxDur = GestureDescription.getMaxGestureDuration()
        val b = GestureDescription.Builder()
        when (a.type) {
            ActionType.TAP -> {
                b.addStroke(GestureDescription.StrokeDescription(point(a.x, a.y), 0, 40))
            }
            ActionType.DOUBLE -> {
                b.addStroke(GestureDescription.StrokeDescription(point(a.x, a.y), 0, 40))
                b.addStroke(GestureDescription.StrokeDescription(point(a.x, a.y), 120, 40))
            }
            ActionType.HOLD -> {
                val d = a.effectiveDuration().coerceIn(1L, maxDur)
                b.addStroke(GestureDescription.StrokeDescription(point(a.x, a.y), 0, d))
            }
            ActionType.SWIPE -> {
                val d = a.effectiveDuration().coerceIn(1L, maxDur)
                val p = Path().apply {
                    moveTo(a.x, a.y)
                    lineTo(a.x2, a.y2)
                }
                b.addStroke(GestureDescription.StrokeDescription(p, 0, d))
            }
        }
        return b.build()
    }

    private fun point(x: Float, y: Float) = Path().apply { moveTo(x, y) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stop()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop() }
}
