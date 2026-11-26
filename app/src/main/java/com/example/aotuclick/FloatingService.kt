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
    private var panelView: LinearLayout? = null
    private var markerView: View? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var markerLp: WindowManager.LayoutParams
    private lateinit var toggleBtn: Button

    private var running = false
    private var interval = 500L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        interval = intent?.getLongExtra("interval", 500L) ?: 500L
        if (panelView == null) setup()
        return START_NOT_STICKY
    }

    private fun overlayParams(w: Int, h: Int, px: Int, py: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = px
        lp.y = py
        return lp
    }

    private fun setup() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Chấm đỏ mục tiêu (kéo tới vị trí cần bấm)
        val size = dp(56)
        val marker = View(this)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(Color.argb(90, 255, 0, 0))
        bg.setStroke(dp(3), Color.RED)
        marker.background = bg

        markerLp = overlayParams(size, size, dp(150), dp(300))
        makeDraggable(marker, markerLp, marker)
        wm.addView(marker, markerLp)
        markerView = marker

        // Bảng điều khiển
        toggleBtn = Button(this)
        toggleBtn.text = "▶ Bắt đầu"
        toggleBtn.setOnClickListener { toggle() }

        val closeBtn = Button(this)
        closeBtn.text = "✕"
        closeBtn.setOnClickListener { stopSelf() }

        val handle = TextView(this)
        handle.text = "⠿"
        handle.textSize = 22f
        handle.setPadding(dp(10), 0, dp(10), 0)
        handle.setTextColor(Color.WHITE)

        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.HORIZONTAL
        panel.gravity = Gravity.CENTER_VERTICAL
        panel.setBackgroundColor(Color.argb(200, 30, 30, 30))
        panel.addView(handle)
        panel.addView(toggleBtn)
        panel.addView(closeBtn)

        panelLp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            dp(8), dp(80)
        )
        makeDraggable(handle, panelLp, panel)
        wm.addView(panel, panelLp)
        panelView = panel
    }

    private fun makeDraggable(touchView: View, lp: WindowManager.LayoutParams, target: View) {
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        touchView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp.x
                    iy = lp.y
                    tx = e.rawX
                    ty = e.rawY
                }
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
        val marker = markerView ?: return
        running = !running
        if (running) {
            // Cho phép cú chạm xuyên qua chấm đỏ
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
        panelView?.let { wm.removeView(it) }
        markerView?.let { wm.removeView(it) }
        panelView = null
        markerView = null
        super.onDestroy()
    }
}