package com.example.autoclick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class IconKind { PLAY, PAUSE, STOP, PLUS, MINUS, GEAR, CLOSE, GRIP, COLLAPSE, SWIPE, LOGO, EYE, EYE_OFF }

/** Biểu tượng vẽ hoàn toàn bằng code, không cần file ảnh hay tài nguyên. */
class IconView(
    context: Context,
    var kind: IconKind,
    var tint: Int = Color.WHITE
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val s = min(width, height) * 0.5f * 0.6f
        paint.color = tint
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = s * 0.28f

        when (kind) {
            IconKind.PLAY -> {
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(cx - s * 0.4f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.65f, cy)
                path.lineTo(cx - s * 0.4f, cy + s * 0.65f)
                path.close()
                canvas.drawPath(path, paint)
            }
            IconKind.PAUSE -> {
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(cx - s * 0.55f, cy - s * 0.6f, cx - s * 0.15f, cy + s * 0.6f, s * 0.1f, s * 0.1f, paint)
                canvas.drawRoundRect(cx + s * 0.15f, cy - s * 0.6f, cx + s * 0.55f, cy + s * 0.6f, s * 0.1f, s * 0.1f, paint)
            }
            IconKind.STOP -> {
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, s * 0.15f, s * 0.15f, paint)
            }
            IconKind.PLUS -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.65f, cy, cx + s * 0.65f, cy, paint)
                canvas.drawLine(cx, cy - s * 0.65f, cx, cy + s * 0.65f, paint)
            }
            IconKind.MINUS -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.65f, cy, cx + s * 0.65f, cy, paint)
            }
            IconKind.CLOSE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.55f, cy - s * 0.55f, cx + s * 0.55f, cy + s * 0.55f, paint)
                canvas.drawLine(cx - s * 0.55f, cy + s * 0.55f, cx + s * 0.55f, cy - s * 0.55f, paint)
            }
            IconKind.GEAR -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.26f
                canvas.drawCircle(cx, cy, s * 0.42f, paint)
                for (i in 0 until 8) {
                    val a = i * Math.PI / 4.0
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    canvas.drawLine(
                        cx + ca * s * 0.62f, cy + sa * s * 0.62f,
                        cx + ca * s * 0.85f, cy + sa * s * 0.85f,
                        paint
                    )
                }
            }
            IconKind.COLLAPSE -> {
                paint.style = Paint.Style.STROKE
                path.reset()
                path.moveTo(cx - s * 0.6f, cy + s * 0.3f)
                path.lineTo(cx, cy - s * 0.3f)
                path.lineTo(cx + s * 0.6f, cy + s * 0.3f)
                canvas.drawPath(path, paint)
            }
            IconKind.SWIPE -> {
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.7f, cy, cx + s * 0.7f, cy, paint)
                path.reset()
                path.moveTo(cx + s * 0.2f, cy - s * 0.5f)
                path.lineTo(cx + s * 0.7f, cy)
                path.lineTo(cx + s * 0.2f, cy + s * 0.5f)
                canvas.drawPath(path, paint)
            }
            IconKind.EYE, IconKind.EYE_OFF -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.2f
                path.reset()
                path.moveTo(cx - s * 0.85f, cy)
                path.quadTo(cx, cy - s * 0.95f, cx + s * 0.85f, cy)
                path.quadTo(cx, cy + s * 0.95f, cx - s * 0.85f, cy)
                canvas.drawPath(path, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, s * 0.26f, paint)
                if (kind == IconKind.EYE_OFF) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = s * 0.26f
                    canvas.drawLine(cx - s * 0.7f, cy + s * 0.75f, cx + s * 0.7f, cy - s * 0.75f, paint)
                }
            }
            IconKind.LOGO -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.2f
                canvas.drawCircle(cx, cy, s * 0.6f, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, s * 0.22f, paint)
            }
            IconKind.GRIP -> {
                paint.style = Paint.Style.FILL
                for (row in -1..1) {
                    for (col in 0..1) {
                        canvas.drawCircle(
                            cx + (col - 0.5f) * s * 0.7f,
                            cy + row * s * 0.55f,
                            s * 0.12f,
                            paint
                        )
                    }
                }
            }
        }
    }
}
