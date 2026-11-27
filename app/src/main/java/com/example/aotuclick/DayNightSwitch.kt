// Lucian UX/UI Prompt #447 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#447
package com.example.autoclick

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.Switch
import kotlin.math.min

/**
 * Công tắc ngày–đêm: bản chuyển từ mẫu HTML #447 sang vẽ bằng Canvas.
 * Hệ toạ độ gốc 176 x 74 (như mẫu), tự co theo kích thước View.
 * Màu bên trong công tắc không phụ thuộc theme của app.
 * checked = true là ban đêm (chế độ tối).
 *
 * Giảm chuyển động: nếu hệ thống tắt animation (Remove animations / animator scale = 0),
 * công tắc nhảy thẳng tới trạng thái cuối, các hiệu ứng lặp (mây trôi, sao nhấp nháy) đứng yên.
 */
class DayNightSwitch(context: Context) : View(context) {

    var onChanged: ((Boolean) -> Unit)? = null

    var checked = false
        private set

    // ---- Đường cong (cubic-bezier của mẫu) ----
    private val ease = PathInterpolator(.25f, .1f, .25f, 1f)          // CSS "ease"
    private val easeUx = PathInterpolator(.2f, .8f, .2f, 1f)          // --ux-ease
    private val spring = PathInterpolator(.34f, 1.45f, .64f, 1f)      // --ux-spring
    private val easeInOut = PathInterpolator(.42f, 0f, .58f, 1f)      // ease-in-out

    private class Spec(
        val dur: Float,
        val interp: PathInterpolator,
        val delayOn: Float,
        val delayOff: Float
    )

    private val specs = arrayOf(
        Spec(600f, ease, 0f, 0f),        // 0  trời đêm
        Spec(750f, spring, 0f, 0f),      // 1  nút trượt + quay + vầng sáng
        Spec(450f, ease, 100f, 100f),    // 2  mặt trăng phủ lên mặt trời
        Spec(600f, easeUx, 0f, 50f),     // 3  mây chìm xuống
        Spec(400f, ease, 150f, 0f),      // 4  sao hiện (độ mờ)
        Spec(600f, easeUx, 150f, 0f),    // 5  sao rơi xuống
        Spec(300f, ease, 250f, 0f),      // 6  hố trăng 1: độ mờ
        Spec(300f, ease, 250f, 60f),     // 7  hố trăng 2
        Spec(300f, ease, 250f, 120f),    // 8  hố trăng 3
        Spec(500f, spring, 250f, 0f),    // 9  hố trăng 1: phóng to
        Spec(500f, spring, 250f, 60f),   // 10 hố trăng 2
        Spec(500f, spring, 250f, 120f)   // 11 hố trăng 3
    )
    private val from = FloatArray(specs.size)
    private var target = 0f
    private var t = 10_000f              // ms kể từ lúc bấm; lớn = đã xong
    private var anim: ValueAnimator? = null
    private var ambientAnim: ValueAnimator? = null
    private var ambient = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val bounds = RectF(0f, 0f, 176f, 74f)

    // ---- Shader dựng một lần (toạ độ gốc 176 x 74 hoặc toạ độ cục bộ 62 x 62 của nút) ----
    private fun c(v: Long) = v.toInt()

    private val skyShader = LinearGradient(
        0f, 0f, 0f, 74f, c(0xFF3E97EC), c(0xFF86C8FF), Shader.TileMode.CLAMP
    )
    private val nightShader = LinearGradient(
        0f, 0f, 0f, 74f, c(0xFF151840), c(0xFF2C3170), Shader.TileMode.CLAMP
    )
    private val sunShader = RadialGradient(
        21.7f, 18.6f, 59.2f,
        intArrayOf(c(0xFFFFE27A), c(0xFFFFC21F), c(0xFFF2A900)),
        floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP
    )
    private val moonShader = RadialGradient(
        21.7f, 18.6f, 59.2f,
        intArrayOf(c(0xFFF4F3FA), c(0xFFD6D3E6), c(0xFFB9B5CF)),
        floatArrayOf(0f, .65f, 1f), Shader.TileMode.CLAMP
    )
    // Bóng trong (inset) của nút: vòng chuyển màu lệch tâm, mô phỏng inset box-shadow
    private fun inset(cx: Float, cy: Float, argb: Int): RadialGradient {
        val clear = argb and 0x00FFFFFF
        return RadialGradient(
            cx, cy, 34f, intArrayOf(clear, clear, argb),
            floatArrayOf(0f, .76f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val sunShade = inset(28f, 27f, Color.argb(89, 190, 110, 0))
    private val sunLight = inset(34f, 34f, Color.argb(115, 255, 255, 255))
    private val moonShade = inset(28f, 27f, Color.argb(89, 70, 64, 110))
    private val moonLight = inset(34f, 34f, Color.argb(153, 255, 255, 255))
    private val dropShadow = RadialGradient(
        31f, 35f, 39f,
        intArrayOf(Color.argb(71, 0, 0, 0), Color.argb(71, 0, 0, 0), 0),
        floatArrayOf(0f, .79f, 1f), Shader.TileMode.CLAMP
    )

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Chế độ tối"
        clip.addRoundRect(bounds, 37f, 37f, Path.Direction.CW)
    }

    // ---------- Trạng thái ----------

    fun setChecked(value: Boolean, animate: Boolean) {
        if (value == checked && !animate) {
            // Chỉ đồng bộ lần đầu: nhảy thẳng tới trạng thái cuối
            snapTo(if (value) 1f else 0f)
            return
        }
        checked = value
        start(if (value) 1f else 0f, animate)
    }

    private fun snapTo(v: Float) {
        anim?.cancel()
        checked = v == 1f
        target = v
        for (i in from.indices) from[i] = v
        t = 10_000f
        invalidate()
    }

    private fun value(i: Int): Float {
        val s = specs[i]
        val d = if (target == 1f) s.delayOn else s.delayOff
        val x = ((t - d) / s.dur).coerceIn(0f, 1f)
        return from[i] + (target - from[i]) * s.interp.getInterpolation(x)
    }

    private fun start(newTarget: Float, animate: Boolean) {
        // Bắt đầu từ giá trị hiện tại của từng phần để đảo chiều giữa chừng vẫn mượt
        for (i in from.indices) from[i] = value(i)
        target = newTarget
        anim?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            snapTo(newTarget)
            return
        }
        t = 0f
        anim = ValueAnimator.ofFloat(0f, 1000f).apply {
            duration = 1000L
            interpolator = LinearInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------- Chuyển động lặp (mây trôi, sao nhấp nháy) ----------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) {
            ambient = true
            ambientAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { invalidate() }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        ambientAnim?.cancel()
        ambientAnim = null
        ambient = false
        super.onDetachedFromWindow()
    }

    private fun now(): Long = if (ambient) SystemClock.uptimeMillis() else 0L

    /** Dao động qua lại 0..1 (animation alternate, ease-in-out). */
    private fun drift(periodMs: Long): Float {
        val x0 = (now() % (2 * periodMs)).toFloat() / periodMs
        val x = if (x0 > 1f) 2f - x0 else x0
        return easeInOut.getInterpolation(x)
    }

    /** Sao nhấp nháy 2400ms: scale 1→.7, opacity 1→.45 ở 50%; phaseMs là animation-delay âm. */
    private fun twinkle(phaseMs: Long): Float {
        val ms = (now() - phaseMs).mod(2400L)
        val x = ms / 2400f
        return if (x < .5f) easeInOut.getInterpolation(x * 2f)
        else 1f - easeInOut.getInterpolation((x - .5f) * 2f)
    }

    // ---------- Vẽ ----------

    override fun onDraw(canvas: Canvas) {
        val sc = min(width / 176f, height / 74f)
        canvas.save()
        canvas.translate((width - 176f * sc) / 2f, (height - 74f * sc) / 2f)
        canvas.scale(sc, sc)
        canvas.clipPath(clip)

        // Nền
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = c(0xFF4FA8F7)
        canvas.drawRect(bounds, paint)

        // Trời ngày + trời đêm
        paint.shader = skyShader
        canvas.drawRect(bounds, paint)
        paint.shader = nightShader
        paint.alpha = (value(0).coerceIn(0f, 1f) * 255).toInt()
        canvas.drawRect(bounds, paint)
        paint.shader = null
        paint.alpha = 255

        drawStars(canvas)

        val tv = value(1)
        val cx = 37f + 102f * tv
        val cy = 37f

        // Vầng sáng quanh nút
        paint.color = Color.WHITE
        paint.alpha = 31; canvas.drawCircle(cx, cy, 48f, paint)
        paint.alpha = 23; canvas.drawCircle(cx, cy, 66f, paint)
        paint.alpha = 15; canvas.drawCircle(cx, cy, 85f, paint)
        paint.alpha = 255

        drawClouds(canvas)
        drawThumb(canvas, cx, cy, tv)

        canvas.restore()

        // Viền focus khi điều hướng bằng bàn phím
        if (isFocused) {
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * resources.displayMetrics.density
            paint.color = c(0xFF6A57F2)
            val h = paint.strokeWidth / 2f
            val r = Rect(0, 0, width, height)
            canvas.drawRoundRect(
                RectF(r.left + h, r.top + h, r.right - h, r.bottom - h),
                height / 2f, height / 2f, paint
            )
            paint.style = Paint.Style.FILL
        }
    }

    private fun sparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val p = Path()
        p.moveTo(cx, cy - r)
        p.quadTo(cx, cy, cx + r, cy)
        p.quadTo(cx, cy, cx, cy + r)
        p.quadTo(cx, cy, cx - r, cy)
        p.quadTo(cx, cy, cx, cy - r)
        p.close()
        canvas.drawPath(p, paint)
    }

    private fun drawStars(canvas: Canvas) {
        val op = value(4).coerceIn(0f, 1f)
        if (op <= 0.002f) return
        canvas.save()
        canvas.translate(16f, 8f - 36f * (1f - value(5)))
        paint.color = Color.WHITE
        // Thứ tự và animation-delay âm theo từng phần tử con của mẫu
        val starData = arrayOf(
            floatArrayOf(14f, 14f, 5f, 0f),       // sao 4 cánh
            floatArrayOf(42f, 10f, 4f, 800f),
            floatArrayOf(28f, 35f, 4f, 1600f),
            floatArrayOf(60f, 26f, 3f, 800f)
        )
        val dotData = arrayOf(
            floatArrayOf(7f, 31f, 1.3f, 0f),
            floatArrayOf(50f, 42f, 1.1f, 1600f),
            floatArrayOf(68f, 10f, 1.3f, 0f),
            floatArrayOf(36f, 51f, 1f, 800f),
            floatArrayOf(76f, 38f, 1f, 1600f)
        )
        for (s in starData) {
            val m = twinkle(s[3].toLong())
            paint.alpha = (op * (1f - .55f * m) * 255).toInt()
            canvas.save()
            canvas.scale(1f - .3f * m, 1f - .3f * m, s[0], s[1])
            sparkle(canvas, s[0], s[1], s[2])
            canvas.restore()
        }
        for (d in dotData) {
            val m = twinkle(d[3].toLong())
            paint.alpha = (op * (1f - .55f * m) * 255).toInt()
            canvas.save()
            canvas.scale(1f - .3f * m, 1f - .3f * m, d[0], d[1])
            canvas.drawCircle(d[0], d[1], d[2], paint)
            canvas.restore()
        }
        paint.alpha = 255
        canvas.restore()
    }

    private fun drawClouds(canvas: Canvas) {
        val down = 56f * value(3)
        if (down >= 74f) return
        canvas.save()
        canvas.translate(54f, 22f + down)   // svg 136x68 đặt right:-14 bottom:-16
        paint.style = Paint.Style.FILL

        canvas.save()
        canvas.translate(-5f * drift(7000L), 0f)
        paint.color = c(0xFFCFE7FF)
        canvas.drawCircle(34f, 46f, 18f, paint)
        canvas.drawCircle(58f, 36f, 20f, paint)
        canvas.drawCircle(86f, 40f, 18f, paint)
        canvas.drawCircle(110f, 28f, 21f, paint)
        canvas.drawCircle(132f, 40f, 18f, paint)
        canvas.restore()

        canvas.save()
        canvas.translate(-5f * drift(5500L), 0f)
        paint.color = Color.WHITE
        canvas.drawCircle(22f, 60f, 16f, paint)
        canvas.drawCircle(46f, 52f, 18f, paint)
        canvas.drawCircle(74f, 56f, 18f, paint)
        canvas.drawCircle(98f, 46f, 19f, paint)
        canvas.drawCircle(124f, 54f, 19f, paint)
        canvas.restore()

        canvas.restore()
    }

    private fun drawThumb(canvas: Canvas, cx: Float, cy: Float, tv: Float) {
        canvas.save()
        canvas.translate(cx - 31f, cy - 31f)   // toạ độ cục bộ 62 x 62

        // Bóng đổ ngoài (không quay theo nút)
        paint.shader = dropShadow
        canvas.drawCircle(31f, 35f, 39f, paint)

        canvas.rotate(200f * tv, 31f, 31f)

        // Mặt trời
        paint.shader = sunShader
        canvas.drawCircle(31f, 31f, 31f, paint)
        paint.shader = sunShade
        canvas.drawCircle(31f, 31f, 31f, paint)
        paint.shader = sunLight
        canvas.drawCircle(31f, 31f, 31f, paint)

        // Mặt trăng phủ lên
        val mo = value(2).coerceIn(0f, 1f)
        if (mo > 0.002f) {
            val a = (mo * 255).toInt()
            paint.shader = moonShader; paint.alpha = a
            canvas.drawCircle(31f, 31f, 31f, paint)
            paint.shader = moonShade; paint.alpha = a
            canvas.drawCircle(31f, 31f, 31f, paint)
            paint.shader = moonLight; paint.alpha = a
            canvas.drawCircle(31f, 31f, 31f, paint)
        }
        paint.shader = null
        paint.alpha = 255

        // Hố trăng
        val craters = arrayOf(
            floatArrayOf(22f, 24f, 6.5f),
            floatArrayOf(40f, 38f, 8f),
            floatArrayOf(39f, 18f, 3.6f)
        )
        paint.color = c(0xFFA9A4C4)
        for (k in craters.indices) {
            val op = value(6 + k).coerceIn(0f, 1f)
            val s = value(9 + k).coerceAtLeast(0f)
            if (op <= 0.002f || s <= 0.001f) continue
            paint.alpha = (op * 255).toInt()
            canvas.save()
            canvas.scale(s, s, craters[k][0], craters[k][1])
            canvas.drawCircle(craters[k][0], craters[k][1], craters[k][2], paint)
            canvas.restore()
        }
        paint.alpha = 255
        canvas.restore()
    }

    // ---------- Chạm, bàn phím, trợ năng ----------

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> press(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(false)
        }
        return super.onTouchEvent(e)
    }

    private fun press(down: Boolean) {
        val s = if (down) .97f else 1f
        animate().scaleX(s).scaleY(s).setDuration(450L).setInterpolator(spring).start()
    }

    override fun performClick(): Boolean {
        setChecked(!checked, true)
        onChanged?.invoke(checked)
        return super.performClick()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Switch::class.java.name
        info.isCheckable = true
        info.isChecked = checked
    }
}
