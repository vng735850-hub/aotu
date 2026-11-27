// Lucian UX/UI Prompt #041 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#041
package com.example.autoclick

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.provider.Settings
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout

// Đường cong chuyển động giữ nguyên từ mẫu: --ux-spring và "ease" mặc định của CSS
private val NTB_SPRING: Interpolator = PathInterpolator(0.34f, 1.45f, 0.64f, 1f)
private val NTB_EASE: Interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)

/** Các token --ux-* của mẫu, ánh xạ sang AppTheme (chế độ sáng/tối của app). */
private object NtbColors {
    private val dark: Boolean get() = AppTheme.dark

    // --ux-surface / --ux-ink / --ux-ink-3 lấy từ AppTheme
    val surface: Int get() = AppTheme.card
    val ink: Int get() = AppTheme.text
    val ink3: Int get() = AppTheme.sub

    // Các màu nhấn giữ đúng giá trị sáng/tối của mẫu
    val accent: Int get() = if (dark) Color.rgb(0xB7, 0xA4, 0xFF) else Color.rgb(0x6A, 0x57, 0xF2)
    val accentInk: Int get() = if (dark) Color.rgb(0x0B, 0x0C, 0x12) else Color.WHITE
    val shadowStrong: Int get() = if (dark) Color.argb(184, 0, 0, 0) else Color.argb(77, 40, 30, 80)
    val grad: IntArray
        get() = if (dark) {
            intArrayOf(Color.rgb(0xB7, 0xA4, 0xFF), Color.rgb(0x7D, 0xB4, 0xFF), Color.rgb(0xA5, 0xF2, 0xD3))
        } else {
            intArrayOf(Color.rgb(0x7B, 0x61, 0xFF), Color.rgb(0x3D, 0x8B, 0xFF), Color.rgb(0x1F, 0xB8, 0x9A))
        }
}

/** Một giá trị chuyển động từ giá trị hiện tại tới đích (giống CSS transition). */
private class NtbTween(
    private val durationMs: Long,
    private val interp: Interpolator,
    private val onChange: () -> Unit
) {
    var value = 0f
        private set
    private var anim: ValueAnimator? = null

    fun to(target: Float, animate: Boolean) {
        anim?.cancel()
        anim = null
        if (!animate || value == target) {
            value = target
            onChange()
            return
        }
        val from = value
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = durationMs
        a.interpolator = interp
        a.addUpdateListener {
            val f = it.animatedValue as Float
            value = from + (target - from) * f
            onChange()
        }
        anim = a
        a.start()
    }
}

/** Biểu tượng vẽ trong hệ toạ độ 24x24 như mẫu (nét 1.9, đầu và góc tròn). */
private fun ntbIcon(kind: Int): Path {
    val p = Path()
    when (kind) {
        0 -> { // Trang chủ
            p.moveTo(4f, 11f)
            p.lineTo(12f, 4.5f)
            p.lineTo(20f, 11f)
            p.lineTo(20f, 19f)
            p.quadTo(20f, 20f, 19f, 20f)
            p.lineTo(14.5f, 20f)
            p.lineTo(14.5f, 15f)
            p.lineTo(9.5f, 15f)
            p.lineTo(9.5f, 20f)
            p.lineTo(5f, 20f)
            p.quadTo(4f, 20f, 4f, 19f)
            p.close()
        }
        1 -> { // Kịch bản (tờ giấy có dòng chữ)
            p.moveTo(7f, 3.5f)
            p.lineTo(14.5f, 3.5f)
            p.lineTo(19f, 8f)
            p.lineTo(19f, 19.5f)
            p.quadTo(19f, 20.5f, 18f, 20.5f)
            p.lineTo(7f, 20.5f)
            p.quadTo(6f, 20.5f, 6f, 19.5f)
            p.lineTo(6f, 4.5f)
            p.quadTo(6f, 3.5f, 7f, 3.5f)
            p.close()
            p.moveTo(14.5f, 3.5f)
            p.lineTo(14.5f, 8f)
            p.lineTo(19f, 8f)
            p.moveTo(9f, 12.5f)
            p.lineTo(15f, 12.5f)
            p.moveTo(9f, 16f)
            p.lineTo(15f, 16f)
        }
        else -> { // Cài đặt (hai thanh trượt)
            p.moveTo(4f, 7f)
            p.lineTo(13f, 7f)
            p.addCircle(15f, 7f, 2f, Path.Direction.CW)
            p.moveTo(17f, 7f)
            p.lineTo(20f, 7f)
            p.moveTo(4f, 17f)
            p.lineTo(7f, 17f)
            p.addCircle(9f, 17f, 2f, Path.Direction.CW)
            p.moveTo(11f, 17f)
            p.lineTo(20f, 17f)
        }
    }
    return p
}

/**
 * Thanh tab dưới có khuyết tròn (mẫu #041): nút tròn nổi lên trên tab đang chọn,
 * mép trên của thanh lõm theo và trượt cùng nút (lò xo), biểu tượng nhấc vào vòng tròn,
 * nhãn hiện bên dưới. Phím trái/phải chuyển tab. Màu lấy từ AppTheme.
 */
class NotchTabBar(context: Context, labels: List<String>) : FrameLayout(context) {

    /** Gọi khi người dùng chọn một tab. */
    var onSelect: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density

    private val tabsRow = LinearLayout(context)
    private val tabs = ArrayList<TabItem>()
    private var selected = 0

    // Vị trí tâm nút tròn tính bằng dp (giống đơn vị px của mẫu để lò xo có cùng cảm giác)
    private var bx = 0f
    private var bv = 0f
    private var target = 0f
    private var placed = false
    private var running = false
    private var lastNanos = 0L
    private var acc = 0f

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPath = Path()
    private val notchPath = Path()
    private val barRect = RectF()
    private val shadowRect = RectF()

    private var gradientDark: Boolean? = null

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastNanos == 0L) lastNanos = frameTimeNanos
            acc += (frameTimeNanos - lastNanos) / 1_000_000f
            lastNanos = frameTimeNanos
            // Mẫu chạy lò xo theo từng khung 60Hz: giữ nguyên bước cố định 16.67ms
            var steps = 0
            while (acc >= 16.667f && steps < 8) {
                acc -= 16.667f
                steps++
                val a = (target - bx) * 0.16f - bv * 0.42f
                bv += a
                bx += bv
            }
            if (steps >= 8) acc = 0f
            if (Math.abs(target - bx) > 0.3f || Math.abs(bv) > 0.3f) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                bx = target
                bv = 0f
                running = false
                lastNanos = 0L
            }
            invalidate()
        }
    }

    init {
        setWillNotDraw(false)
        minimumHeight = dp(129f).toInt()

        tabsRow.orientation = LinearLayout.HORIZONTAL
        tabsRow.setPadding(dp(14f).toInt(), 0, dp(14f).toInt(), 0)
        val rowLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(84f).toInt(),
            Gravity.BOTTOM
        )
        rowLp.setMargins(dp(14f).toInt(), 0, dp(14f).toInt(), dp(10f).toInt())
        addView(tabsRow, rowLp)

        for (i in labels.indices) {
            val t = TabItem(context, i, labels[i], ntbIcon(i))
            tabs.add(t)
            tabsRow.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        if (tabs.isNotEmpty()) tabs[0].setActive(true, false)
    }

    // ---------- Điều khiển ----------

    /** Đặt tab đang chọn từ bên ngoài, không hiệu ứng và không gọi onSelect. */
    fun setSelectedIndex(i: Int) {
        select(i.coerceIn(0, tabs.size - 1), focus = false, animate = false, notify = false)
    }

    /** Vẽ lại khi đổi chế độ sáng/tối. */
    fun applyTheme() {
        invalidate()
        for (t in tabs) t.invalidate()
    }

    private fun reduceMotion(): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }

    private fun centerDp(i: Int): Float {
        val t = tabs[i]
        return (tabsRow.left + t.left + t.width / 2f) / density
    }

    private fun select(i: Int, focus: Boolean, animate: Boolean, notify: Boolean) {
        if (tabs.isEmpty()) return
        selected = i
        val anim = animate && !reduceMotion()
        for (k in tabs.indices) tabs[k].setActive(k == i, anim)
        if (focus) tabs[i].requestFocus()
        if (width > 0) {
            target = centerDp(i)
            if (!anim) {
                stopLoop()
                bx = target
                bv = 0f
                invalidate()
            } else {
                startLoop()
            }
        }
        if (notify) onSelect?.invoke(i)
    }

    private fun move(d: Int) {
        val n = tabs.size
        select((selected + d + n) % n, focus = true, animate = true, notify = true)
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastNanos = 0L
        acc = 0f
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    private fun stopLoop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (tabs.isEmpty()) return
        target = centerDp(selected)
        if (!placed || changed) {
            stopLoop()
            bx = target
            bv = 0f
            placed = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
        bx = target
        bv = 0f
    }

    // ---------- Vẽ thanh, vết khuyết và nút tròn ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val margin = dp(14f)
        val barB = height - dp(10f)
        val barT = barB - dp(84f)
        val barL = margin
        val barR = width - margin
        val radius = dp(28f)
        val cx = bx * density
        val cy = barT - dp(4f)

        // Bóng dưới thanh (box-shadow 0 24px 40px -24px)
        val sh = NtbColors.shadowStrong
        shadowPaint.color = Color.argb(Color.alpha(sh) / 4, Color.red(sh), Color.green(sh), Color.blue(sh))
        for (i in 0..3) {
            val g = dp(4f * i)
            shadowRect.set(barL + dp(16f) - g, barT + dp(40f) - g, barR - dp(16f) + g, barB + dp(8f) + g * 0.5f)
            canvas.drawRoundRect(shadowRect, radius + g, radius + g, shadowPaint)
        }

        // Thanh nền bo góc, khoét hình tròn bán kính 42 tại (x, -4) như mặt nạ radial-gradient
        barRect.set(barL, barT, barR, barB)
        barPath.reset()
        barPath.addRoundRect(barRect, radius, radius, Path.Direction.CW)
        notchPath.reset()
        notchPath.addCircle(cx, cy, dp(41f), Path.Direction.CW)
        barPath.op(notchPath, Path.Op.DIFFERENCE)
        barPaint.color = NtbColors.surface
        canvas.drawPath(barPath, barPaint)

        // Bóng màu tím dưới nút tròn (0 14px 26px -12px)
        canvas.save()
        canvas.translate(cx, cy + dp(14f))
        if (bubbleShadowPaint.shader == null) {
            bubbleShadowPaint.shader = RadialGradient(
                0f, 0f, dp(32f),
                intArrayOf(Color.argb(150, 91, 72, 255), Color.argb(90, 91, 72, 255), Color.argb(0, 91, 72, 255)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(0f, 0f, dp(32f), bubbleShadowPaint)
        canvas.restore()

        // Nút tròn 62dp, nền gradient 120 độ
        val r = dp(31f)
        val dark = AppTheme.dark
        if (gradientDark != dark || bubblePaint.shader == null) {
            gradientDark = dark
            val half = r * 2f * (0.866f + 0.5f) / 2f
            bubblePaint.shader = LinearGradient(
                -half * 0.866f, -half * 0.5f, half * 0.866f, half * 0.5f,
                NtbColors.grad, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, r, bubblePaint)
        canvas.restore()
    }

    // ---------- Từng tab ----------

    private inner class TabItem(
        ctx: Context,
        private val index: Int,
        private val label: String,
        private val icon: Path
    ) : View(ctx) {

        private val iconT = NtbTween(550, NTB_SPRING) { invalidate() }
        private val labA = NtbTween(300, NTB_EASE) { invalidate() }
        private val labT = NtbTween(450, NTB_SPRING) { invalidate() }
        private val colT = NtbTween(300, NTB_EASE) { invalidate() }

        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ringRect = RectF()
        private val argb = ArgbEvaluator()

        init {
            contentDescription = label
            isClickable = true
            isFocusable = true
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeCap = Paint.Cap.ROUND
            iconPaint.strokeJoin = Paint.Join.ROUND
            labelPaint.textAlign = Paint.Align.CENTER
            labelPaint.typeface = Typeface.DEFAULT_BOLD
            labelPaint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics
            )
            ringPaint.style = Paint.Style.STROKE
            ringPaint.strokeWidth = dp(2f)
            setOnClickListener { select(index, focus = false, animate = true, notify = true) }
        }

        fun setActive(active: Boolean, animate: Boolean) {
            isSelected = active
            val to = if (active) 1f else 0f
            iconT.to(to, animate)
            labA.to(to, animate)
            labT.to(to, animate)
            colT.to(to, animate)
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
            val d = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_LEFT -> -1
                else -> 0
            }
            if (d != 0) {
                move(d)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // Biểu tượng 26dp, đặt cách đỉnh 22dp, nhấc lên 39dp khi được chọn
            val c = argb.evaluate(colT.value.coerceIn(0f, 1f), NtbColors.ink3, NtbColors.accentInk) as Int
            iconPaint.color = c
            iconPaint.strokeWidth = if (isSelected) 2.2f else 1.9f
            val s = dp(26f) / 24f
            canvas.save()
            canvas.translate(w / 2f - dp(13f), dp(22f) - dp(39f) * iconT.value)
            canvas.scale(s, s)
            canvas.drawPath(icon, iconPaint)
            canvas.restore()

            // Nhãn: hiện dần và trượt lên từ dưới 6dp
            val alpha = (labA.value * 255f).toInt().coerceIn(0, 255)
            if (alpha > 0) {
                labelPaint.color = NtbColors.ink
                labelPaint.alpha = alpha
                val y = h - dp(12f) - labelPaint.fontMetrics.descent + dp(6f) * (1f - labT.value)
                canvas.drawText(label, w / 2f, y, labelPaint)
            }

            // Viền focus khi dùng bàn phím (outline-offset -6px)
            if (isFocused) {
                ringPaint.color = NtbColors.accent
                ringRect.set(dp(6f), dp(6f), w - dp(6f), h - dp(6f))
                canvas.drawRoundRect(ringRect, dp(18f), dp(18f), ringPaint)
            }
        }
    }
}
