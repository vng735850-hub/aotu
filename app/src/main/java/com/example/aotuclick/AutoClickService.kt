package com.example.autoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import kotlin.random.Random
import android.view.KeyEvent
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

    // Tạm dừng / chạy tiếp
    private var paused = false
    private var resumeAction: (() -> Unit)? = null
    val isPaused: Boolean get() = paused

    // Đếm số lần đã lặp của từng nhóm (chỉ số = nút kết thúc nhóm)
    private var groupCounts = IntArray(0)

    fun pause() {
        if (running) paused = true
    }

    fun resume() {
        if (!paused) return
        paused = false
        val r = resumeAction
        resumeAction = null
        r?.invoke()
    }

    /** Số thao tác đã thực hiện trong lần chạy gần nhất. */
    @Volatile
    var tapCount = 0
        private set

    private var tapLimit = 0

    /**
     * Chạy kịch bản theo thứ tự các nút.
     * loops    = 0 → lặp vô hạn; loops = n → lặp n vòng rồi tự dừng.
     * maxMillis> 0 → tự dừng sau khoảng thời gian này.
     * maxTaps  > 0 → tự dừng sau số thao tác này.
     */
    fun run(list: List<ClickAction>, loops: Int, maxMillis: Long, maxTaps: Int, onDone: () -> Unit) {
        stop()
        if (list.isEmpty()) {
            onDone()
            return
        }
        running = true
        paused = false
        resumeAction = null
        groupCounts = IntArray(list.size)
        generation++
        onFinished = onDone
        tapCount = 0
        tapLimit = maxTaps
        val gen = generation
        if (maxMillis > 0L) {
            handler.postDelayed({
                if (running && gen == generation) finishRun()
            }, maxMillis)
        }
        step(gen, list.map { it.copy() }, 0, 0, loops)
    }

    fun stop() {
        val wasRunning = running
        running = false
        paused = false
        resumeAction = null
        generation++
        handler.removeCallbacksAndMessages(null)
        val cb = onFinished
        onFinished = null
        if (wasRunning) cb?.invoke()
    }

    private fun finishRun() {
        running = false
        paused = false
        resumeAction = null
        generation++
        val cb = onFinished
        onFinished = null
        cb?.invoke()
    }

    private fun step(gen: Int, list: List<ClickAction>, idx: Int, round: Int, loops: Int) {
        if (!running || gen != generation) return
        if (paused) {
            resumeAction = { step(gen, list, idx, round, loops) }
            return
        }

        if (idx >= list.size) {
            groupCounts.fill(0)
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
                tapCount++
                if (tapLimit > 0 && tapCount >= tapLimit) {
                    finishRun()
                } else {
                    var nextIdx = idx + 1
                    // Nhóm lặp: nút này kết thúc nhóm bắt đầu từ nút groupFrom
                    if (a.groupRepeat > 1 && a.groupFrom in 1..(idx + 1) && idx < groupCounts.size) {
                        val c = groupCounts[idx] + 1
                        if (c < a.groupRepeat) {
                            groupCounts[idx] = c
                            nextIdx = a.groupFrom - 1
                        } else {
                            groupCounts[idx] = 0
                        }
                    }
                    handler.postDelayed(
                        { step(gen, list, nextIdx, round, loops) },
                        jitteredDelay(a.delayMs)
                    )
                }
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

    /** Độ trễ có cộng ngẫu nhiên ± % (nếu bật). */
    private fun jitteredDelay(base: Long): Long {
        val pct = ClickStore.jitterDelayPct
        if (pct <= 0) return base.coerceAtLeast(0L)
        val span = base * pct / 100.0
        val d = base + ((Random.nextDouble() * 2.0 - 1.0) * span).toLong()
        return d.coerceAtLeast(0L)
    }

    /** Lệch vị trí ngẫu nhiên ± px (nếu bật). */
    private fun jit(v: Float): Float {
        val px = ClickStore.jitterPx
        if (px <= 0) return v
        return (v + (Random.nextFloat() * 2f - 1f) * px).coerceAtLeast(0f)
    }

    private fun buildGesture(a: ClickAction): GestureDescription {
        val maxDur = GestureDescription.getMaxGestureDuration()
        val b = GestureDescription.Builder()
        val x = jit(a.x)
        val y = jit(a.y)
        when (a.type) {
            ActionType.TAP -> {
                b.addStroke(GestureDescription.StrokeDescription(point(x, y), 0, 40))
            }
            ActionType.DOUBLE -> {
                b.addStroke(GestureDescription.StrokeDescription(point(x, y), 0, 40))
                b.addStroke(GestureDescription.StrokeDescription(point(x, y), 120, 40))
            }
            ActionType.HOLD -> {
                val d = a.effectiveDuration().coerceIn(1L, maxDur)
                b.addStroke(GestureDescription.StrokeDescription(point(x, y), 0, d))
            }
            ActionType.SWIPE -> {
                val d = a.effectiveDuration().coerceIn(1L, maxDur)
                val ex = jit(a.x2)
                val ey = jit(a.y2)
                val p = Path().apply {
                    moveTo(x, y)
                    lineTo(ex, ey)
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

    /** Phím giảm âm lượng bật/dừng (chỉ khi người dùng bật tuỳ chọn và bảng nổi đang mở). */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!ClickStore.volumeHotkey) return false
        val panel = FloatingService.instance ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            handler.post { panel.toggleRun() }
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop() }
}
