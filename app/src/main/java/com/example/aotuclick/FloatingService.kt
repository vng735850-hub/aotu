package com.example.autoclick

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var marker: View? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var markerLp: WindowManager.LayoutParams
    private lateinit var toggleBtn: Button

    private var running = false
    private var interval = 500L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        interval = intent?.getLongExtra("interval", 500L) ?: 500L
        if (panel == null) setup()
        return START_NOT_STICKY
    }

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int) =
        WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

    private fun setup() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Dấu chấm mục tiêu (kéo tới vị trí cần bấm)
        val size = dp(56)
        marker = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(90, 255, 0, 0))
                setStroke(dp(3), Color.RED)
            }
        }
        markerLp = overlayParams(size, size, dp(150), dp(300))
        makeDraggable(marker!!, markerLp)
        wm.addView(marker, markerLp)

        // Bảng điều khiển
        toggleBtn = Button(this).apply {
            text = "▶ Bắt đầu"
            setOnClickListener { toggle() }
        }
        val closeBtn = Button(this).apply {
            text = "✕"
            setOnClickListener { stopSelf() }
        }
        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 22f
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(Color.WHITE)
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(200, 30, 30, 30))
            addView(handle)
            addView(toggleBtn)
            addView(closeBtn)
        }
        panelLp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(8), dp(80)
        )
        makeDraggable(handle, panelLp, panel)
        wm.addView(panel, panelLp)
    }

    private fun makeDraggable(touchView: View, lp: WindowManager.LayoutParams, target: View = touchView) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        touchView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ix + (e.rawX - tx).toInt()
                    lp.y = iy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(target, lp)
                }
            }
            true
        }
    }

    private fun toggle() {
        val svc = AutoClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Hãy bật dịch vụ Trợ năng AutoClick trước", Toast.LENGTH_LONG).show()
            return
        }
        running = !running
        if (running) {
            // Cho phép cú chạm xuyên qua dấu chấm
            markerLp.flags = markerLp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(marker, markerLp)
            val half = dp(56) / 2f
            svc.start(markerLp.x + half, markerLp.y + half, interval)
            toggleBtn.text = "⏸ Dừng"
        } else {
            svc.stop()
            markerLp.flags = markerLp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            wm.updateViewLayout(marker, markerLp)
            toggleBtn.text = "▶ Bắt đầu"
        }
    }

    override fun onDestroy() {
        AutoClickService.instance?.stop()
        panel?.let { wm.removeView(it) }
        marker?.let { wm.removeView(it) }
        panel = null
        marker = null
        super.onDestroy()
    }
}
