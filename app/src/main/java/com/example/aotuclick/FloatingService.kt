package com.example.autoclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        @Volatile var instance: FloatingService? = null
        private const val CHANNEL_ID = "nvpklick_running"
        private const val NOTI_ID = 1
        private const val ALERT_CHANNEL = "nvpklick_alert"
        private const val ALERT_ID = 2
        private const val ACTION_STOP_RUN = "com.example.autoclick.STOP_RUN"
        private const val ACTION_SHOW = "com.example.autoclick.SHOW_PANEL"
        private const val ACTION_QUIT = "com.example.autoclick.QUIT"
        // 999 giờ 59 phút 59 giây
        private const val MAX_SECONDS = 3_599_999L
    }

    /** Một nút trên màn hình: chấm bắt đầu (+ chấm kết thúc nếu là Kéo). */
    private class Item(
        val action: ClickAction,
        val view: TextView,
        val lp: WindowManager.LayoutParams,
        val endView: TextView?,
        val endLp: WindowManager.LayoutParams?
    )

    private lateinit var wm: WindowManager
    private var panelView: LinearLayout? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var playBtn: IconView
    private lateinit var pauseBtn: IconView
    private var hideBtn: IconView? = null
    private lateinit var timerText: TextView
    private lateinit var bubbleView: IconView
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private val items = mutableListOf<Item>()

    private var running = false
    private var destroyed = false
    private var minimized = false
    private var userQuit = false
    private var markersHidden = false

    /** Kiểu 1: một điểm chỉ chạm. Kiểu 2: nhiều điểm (di chuyển + chạm). */
    private val single: Boolean get() = ClickStore.panelMode == 1

    // Kích thước màn hình lần cuối (để co giãn toạ độ khi xoay)
    private var lastW = 0
    private var lastH = 0
    private var panelX = -1
    private var panelY = -1

    // Hẹn giờ / đếm ngược trước khi chạy
    private var pendingStart = false
    private var countdownEnd = 0L
    private var lastTick = 0L

    // Bộ đếm thời gian chạy
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    private var elapsedMs = 0L
    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            // Đang tạm dừng thì không tính thời gian
            if (AutoClickService.instance?.isPaused == true) startTime += now - lastTick
            lastTick = now
            elapsedMs = now - startTime
            showTimer()
            if (running && !destroyed) handler.postDelayed(this, 200L)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val markerSize get() = dp(52)
    private val endSize get() = dp(40)

    private val colGreen = Color.rgb(46, 160, 67)
    private val colRed = Color.rgb(211, 47, 47)

    // Các thành phần của bảng nổi cần đổi màu khi đổi chế độ sáng/tối
    private var gripView: IconView? = null
    private val toolBtns = mutableListOf<IconView>()
    private var panelBgDrawable: GradientDrawable? = null
    private var timerBgDrawable: GradientDrawable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val dm = resources.displayMetrics
        lastW = dm.widthPixels
        lastH = dm.heightPixels
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        ClickStore.ensureLoaded(this)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(ALERT_ID)
        when (intent?.action) {
            ACTION_STOP_RUN -> {
                if (pendingStart) cancelCountdown()
                AutoClickService.instance?.stop()
            }
            ACTION_SHOW -> expand()
            ACTION_QUIT -> requestQuit()
            else -> {
                if (panelView == null) {
                    setup()
                } else {
                    refresh()
                    applyAlpha()
                }
            }
        }
        // STICKY: nếu hệ thống dọn bộ nhớ và tắt dịch vụ, Android sẽ dựng lại bảng nổi
        return START_STICKY
    }

    /** Người dùng chủ động tắt: không hiện cảnh báo "bảng đã tắt". */
    fun requestQuit() {
        userQuit = true
        stopSelf()
    }

    /** Bật/dừng chạy từ bên ngoài (phím âm lượng, ô cài đặt nhanh). */
    fun toggleRun() {
        if (destroyed || panelView == null) return
        toggle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (destroyed || panelView == null) return
        val dm = resources.displayMetrics
        val nw = dm.widthPixels
        val nh = dm.heightPixels
        if (lastW <= 0 || lastH <= 0 || (nw == lastW && nh == lastH)) return

        if (pendingStart) cancelCountdown()
        if (running) {
            AutoClickService.instance?.stop()
            setRunningUi(false)
        }
        val wasMin = minimized
        val cur = if (wasMin) bubbleLp else panelLp
        panelX = (cur.x.toLong() * nw / lastW).toInt().coerceIn(0, (nw - dp(48)).coerceAtLeast(0))
        panelY = (cur.y.toLong() * nh / lastH).toInt().coerceIn(0, (nh - dp(48)).coerceAtLeast(0))

        ClickStore.rescale(lastW, lastH, nw, nh)
        lastW = nw
        lastH = nh
        ClickStore.save(this)

        teardownViews()
        setup()
        if (wasMin) minimize()
    }

    private fun teardownViews() {
        clearMarkers()
        panelView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        if (::bubbleView.isInitialized) {
            try { wm.removeView(bubbleView) } catch (e: Exception) {}
        }
        panelView = null
        minimized = false
    }

    /** Đổi màu bảng nổi theo chế độ sáng/tối hiện tại của app. */
    fun applyTheme() {
        if (destroyed || panelView == null) return
        panelBgDrawable?.let {
            it.setColor(AppTheme.panelBg)
            it.setStroke(dp(1), AppTheme.panelStroke)
        }
        timerBgDrawable?.setColor(AppTheme.field)
        timerText.setTextColor(AppTheme.text)
        gripView?.let {
            it.tint = AppTheme.gripTint
            it.invalidate()
        }
        toolBtns.forEach { it.background = ovalBg(AppTheme.panelBtn) }
        panelView?.invalidate()
    }

    /** Cập nhật độ đậm của bảng nổi theo cài đặt. */
    fun applyAlpha() {
        panelView?.alpha = ClickStore.panelAlpha / 100f
    }

    // ---------- Foreground notification ----------

    private fun svcIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, FloatingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun notiAction(label: String, iconRes: Int, action: String, code: Int): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, iconRes), label, svcIntent(action, code)
        ).build()

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nvp Klick", NotificationManager.IMPORTANCE_LOW)
        )
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val noti = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Nvp Klick")
            .setContentText("Bảng điều khiển nổi đang bật")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(notiAction("Dừng", android.R.drawable.ic_media_pause, ACTION_STOP_RUN, 1))
            .addAction(notiAction("Mở bảng", android.R.drawable.ic_menu_view, ACTION_SHOW, 2))
            .addAction(notiAction("Tắt", android.R.drawable.ic_menu_close_clear_cancel, ACTION_QUIT, 3))
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTI_ID, noti, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTI_ID, noti)
        }
    }

    // ---------- Cửa sổ nổi ----------

    private fun overlayParams(w: Int, h: Int, px: Int, py: Int): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            // Tai thỏ / đục lỗ: cho phép cửa sổ nổi đi tới sát mép, không bị cắt hoặc đẩy lệch
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.x = px
        lp.y = py
        return lp
    }

    private fun ovalBg(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun circleButton(kind: IconKind, bg: Int, sizeDp: Int, onClick: () -> Unit): IconView {
        val v = IconView(this, kind)
        v.background = ovalBg(bg)
        val lp = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        v.layoutParams = lp
        v.setOnClickListener { onClick() }
        return v
    }

    private fun setup() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        // Xoay ngang: bảng nằm ngang cho vừa chiều cao màn hình
        val horiz = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        // Tay nắm kéo bảng
        val grip = IconView(this, IconKind.GRIP, AppTheme.gripTint)
        grip.layoutParams = if (horiz) {
            LinearLayout.LayoutParams(dp(26), dp(44))
        } else {
            LinearLayout.LayoutParams(dp(44), dp(26))
        }
        gripView = grip

        playBtn = circleButton(IconKind.PLAY, colGreen, 50) { toggle() }

        // Ô hiển thị thời gian chạy: 000:00:00
        timerText = TextView(this)
        timerText.setTextColor(AppTheme.text)
        timerText.textSize = 10f
        timerText.typeface = Typeface.MONOSPACE
        timerText.gravity = Gravity.CENTER
        timerText.setPadding(dp(6), dp(2), dp(6), dp(2))
        val tbg = GradientDrawable()
        tbg.shape = GradientDrawable.RECTANGLE
        tbg.cornerRadius = dp(8).toFloat()
        tbg.setColor(AppTheme.field)
        timerText.background = tbg
        timerBgDrawable = tbg
        val tlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        tlp.setMargins(0, dp(2), 0, dp(4))
        timerText.layoutParams = tlp
        showTimer()

        pauseBtn = circleButton(IconKind.PAUSE, AppTheme.panelBtn, 36) { togglePause() }
        val hide = circleButton(if (markersHidden) IconKind.EYE_OFF else IconKind.EYE, AppTheme.panelBtn, 36) { toggleMarkersHidden() }
        hideBtn = hide
        val addBtn = circleButton(IconKind.PLUS, AppTheme.panelBtn, 36) { addAction(ActionType.TAP) }
        val swipeBtn = circleButton(IconKind.SWIPE, AppTheme.panelBtn, 36) { addAction(ActionType.SWIPE) }
        val removeBtn = circleButton(IconKind.MINUS, AppTheme.panelBtn, 36) { removeLastAction() }
        val settingsBtn = circleButton(IconKind.GEAR, AppTheme.panelBtn, 36) { openSettings() }
        val minBtn = circleButton(IconKind.COLLAPSE, AppTheme.panelBtn, 36) { minimize() }
        val closeBtn = circleButton(IconKind.CLOSE, Color.rgb(150, 50, 50), 36) { requestQuit() }
        toolBtns.clear()
        toolBtns.addAll(listOf(pauseBtn, hide, addBtn, swipeBtn, removeBtn, settingsBtn, minBtn))

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = dp(30).toFloat()
        bg.setColor(AppTheme.panelBg)
        bg.setStroke(dp(1), AppTheme.panelStroke)
        panelBgDrawable = bg

        val panel = LinearLayout(this)
        panel.orientation = if (horiz) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        panel.gravity = if (horiz) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
        panel.setPadding(dp(6), dp(4), dp(6), dp(6))
        panel.background = bg
        panel.alpha = ClickStore.panelAlpha / 100f
        panel.addView(grip)
        panel.addView(playBtn)
        panel.addView(timerText)
        panel.addView(pauseBtn)
        panel.addView(hide)
        if (!single) {
            panel.addView(addBtn)
            panel.addView(swipeBtn)
            panel.addView(removeBtn)
        }
        panel.addView(settingsBtn)
        panel.addView(minBtn)
        panel.addView(closeBtn)

        panelLp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (panelX >= 0) panelX else dp(8),
            if (panelY >= 0) panelY else dp(100)
        )
        attachTouch(grip, panelLp, panel, null, null)
        wm.addView(panel, panelLp)
        panelView = panel

        // Bong bóng khi thu nhỏ: kéo để di chuyển, chạm để mở lại bảng
        bubbleView = IconView(this, IconKind.LOGO)
        bubbleView.background = ovalBg(colGreen)
        bubbleLp = overlayParams(dp(48), dp(48), dp(8), dp(100))
        attachTouch(bubbleView, bubbleLp, bubbleView, null, { expand() })

        rebuildMarkers()
        setRunningUi(running)
    }

    // ---------- Thu nhỏ / mở rộng ----------

    private fun updateBubbleColor() {
        bubbleView.background = ovalBg(if (running) colRed else colGreen)
    }

    private fun minimize() {
        val p = panelView ?: return
        if (minimized) return
        minimized = true
        bubbleLp.x = panelLp.x
        bubbleLp.y = panelLp.y
        updateBubbleColor()
        try { wm.removeView(p) } catch (e: Exception) {}
        try { wm.addView(bubbleView, bubbleLp) } catch (e: Exception) {}
    }

    private fun expand() {
        val p = panelView ?: return
        if (!minimized) return
        minimized = false
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - p.width).coerceAtLeast(0)
        val maxY = (dm.heightPixels - p.height).coerceAtLeast(0)
        panelLp.x = bubbleLp.x.coerceIn(0, maxX)
        panelLp.y = bubbleLp.y.coerceIn(0, maxY)
        try { wm.removeView(bubbleView) } catch (e: Exception) {}
        try { wm.addView(p, panelLp) } catch (e: Exception) {}
    }

    // ---------- Bộ đếm thời gian ----------

    private fun showTimer() {
        val totalSec = (elapsedMs / 1000L).coerceIn(0L, MAX_SECONDS)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        val n = AutoClickService.instance?.tapCount ?: 0
        timerText.text = String.format(Locale.US, "%03d:%02d:%02d\n×%d", h, m, s, n)
    }

    // ---------- Các nút đánh số ----------

    /** Vẽ lại toàn bộ nút từ ClickStore (gọi sau khi sửa / xoá / đổi thứ tự). */
    fun refresh() {
        if (destroyed || panelView == null) return
        if (pendingStart) cancelCountdown()
        if (running) AutoClickService.instance?.stop()
        rebuildMarkers()
    }

    private fun clearMarkers() {
        items.forEach {
            try { wm.removeView(it.view) } catch (e: Exception) {}
            it.endView?.let { v -> try { wm.removeView(v) } catch (e: Exception) {} }
        }
        items.clear()
    }

    private fun rebuildMarkers() {
        clearMarkers()
        if (single) ensureSingle()
        ClickStore.actions.forEachIndexed { i, a ->
            if (!single || i == 0) addMarker(i, a)
        }
    }

    /** Kiểu 1: luôn có đúng một điểm chạm (tạo nếu chưa có, ép về loại Chạm). */
    private fun ensureSingle() {
        val dm = resources.displayMetrics
        if (ClickStore.actions.isEmpty()) {
            ClickStore.actions.add(
                ClickAction(
                    type = ActionType.TAP,
                    x = dm.widthPixels / 2f,
                    y = dm.heightPixels / 2f,
                    x2 = dm.widthPixels / 2f,
                    y2 = dm.heightPixels / 2f,
                    delayMs = ClickStore.defaultDelay
                )
            )
        }
        ClickStore.actions[0].type = ActionType.TAP
        ClickStore.save(this)
    }

    /** Ẩn / hiện toàn bộ các nút trên màn hình (vẫn chạy bình thường khi đang ẩn). */
    private fun toggleMarkersHidden() {
        markersHidden = !markersHidden
        applyMarkersVisibility()
    }

    private fun applyMarkersVisibility() {
        val vis = if (markersHidden) View.INVISIBLE else View.VISIBLE
        items.forEach {
            it.view.visibility = vis
            it.endView?.visibility = vis
        }
        hideBtn?.let {
            it.kind = if (markersHidden) IconKind.EYE_OFF else IconKind.EYE
            it.invalidate()
        }
    }

    /** Đổi kiểu bảng điều khiển (gọi từ màn hình chính): dựng lại bảng, giữ nguyên vị trí. */
    fun applyMode() {
        if (destroyed || panelView == null) return
        if (pendingStart) cancelCountdown()
        if (running) {
            AutoClickService.instance?.stop()
            setRunningUi(false)
        }
        val wasMin = minimized
        val cur = if (wasMin) bubbleLp else panelLp
        panelX = cur.x
        panelY = cur.y
        teardownViews()
        setup()
        if (wasMin) minimize()
    }

    private fun makeCircle(label: String, color: Int, size: Int): TextView {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(dp(3), color)
        }
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            textSize = if (size >= dp(50)) 20f else 14f
            gravity = Gravity.CENTER
            background = bg
        }
    }

    private fun addMarker(index: Int, a: ClickAction) {
        val n = (index + 1).toString()
        val half = markerSize / 2

        val view = makeCircle(n, a.type.color, markerSize)
        val lp = overlayParams(markerSize, markerSize, (a.x - half).toInt(), (a.y - half).toInt())
        attachTouch(view, lp, view,
            onMove = { x, y -> a.x = x + half.toFloat(); a.y = y + half.toFloat() },
            onClick = { openEditor(index) })
        if (running) lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        wm.addView(view, lp)

        var endView: TextView? = null
        var endLp: WindowManager.LayoutParams? = null
        if (a.type == ActionType.SWIPE) {
            val eh = endSize / 2
            val ev = makeCircle("$n▸", Color.rgb(30, 136, 229), endSize)
            val elp = overlayParams(endSize, endSize, (a.x2 - eh).toInt(), (a.y2 - eh).toInt())
            attachTouch(ev, elp, ev,
                onMove = { x, y -> a.x2 = x + eh.toFloat(); a.y2 = y + eh.toFloat() },
                onClick = { openEditor(index) })
            if (running) elp.flags = elp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            wm.addView(ev, elp)
            endView = ev
            endLp = elp
        }
        items.add(Item(a, view, lp, endView, endLp))
        if (markersHidden) {
            view.visibility = View.INVISIBLE
            endView?.visibility = View.INVISIBLE
        }
    }

    /** Thêm một nút mới: Chạm (TAP) hoặc Kéo (SWIPE, có chấm bắt đầu và chấm kết thúc). */
    private fun addAction(type: ActionType) {
        if (running) {
            Toast.makeText(this, "Hãy dừng trước khi thêm nút", Toast.LENGTH_SHORT).show()
            return
        }
        if (markersHidden) {
            markersHidden = false
            applyMarkersVisibility()
        }
        val dm = resources.displayMetrics
        val n = ClickStore.actions.size
        val minC = dp(30).toFloat()
        val maxX = (dm.widthPixels - dp(30)).toFloat()
        val maxY = (dm.heightPixels - dp(30)).toFloat()
        val x = (dm.widthPixels / 2f + (n % 6) * dp(14)).coerceIn(minC, maxX)
        val y = (dm.heightPixels / 2f + (n % 6) * dp(14)).coerceIn(minC, maxY)
        val x2 = if (x + dp(140) <= maxX) x + dp(140) else (x - dp(140)).coerceAtLeast(minC)
        val a = ClickAction(
            type = type,
            x = x, y = y,
            x2 = x2,
            y2 = y,
            delayMs = ClickStore.defaultDelay
        )
        ClickStore.actions.add(a)
        ClickStore.save(this)
        addMarker(ClickStore.actions.size - 1, a)
        if (type == ActionType.SWIPE) {
            Toast.makeText(this, "Kéo chấm xanh ▸ tới nơi cần thả", Toast.LENGTH_SHORT).show()
        }
    }

    /** Xoá nút cuối cùng trong danh sách. */
    private fun removeLastAction() {
        if (running) {
            Toast.makeText(this, "Hãy dừng trước khi xoá nút", Toast.LENGTH_SHORT).show()
            return
        }
        if (ClickStore.actions.isEmpty()) return
        ClickStore.actions.removeAt(ClickStore.actions.size - 1)
        ClickStore.save(this)
        rebuildMarkers()
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun openEditor(index: Int) {
        if (running) return
        startActivity(
            Intent(this, EditActionActivity::class.java)
                .putExtra("index", index)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    /**
     * Kéo thả + bấm.
     * onMove  : cập nhật toạ độ khi kéo (nhận lp.x, lp.y)
     * onClick : gọi khi chạm mà không kéo
     */
    private fun attachTouch(
        touchView: View,
        lp: WindowManager.LayoutParams,
        target: View,
        onMove: ((Int, Int) -> Unit)?,
        onClick: (() -> Unit)?
    ) {
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        var dragged = false
        val slop = dp(6)
        touchView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp.x; iy = lp.y
                    tx = e.rawX; ty = e.rawY
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx
                    val dy = e.rawY - ty
                    if (!dragged && (abs(dx) > slop || abs(dy) > slop)) dragged = true
                    if (dragged) {
                        lp.x = ix + dx.toInt()
                        lp.y = iy + dy.toInt()
                        try { wm.updateViewLayout(target, lp) } catch (ex: Exception) {}
                        onMove?.invoke(lp.x, lp.y)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) ClickStore.save(this) else onClick?.invoke()
                }
            }
            true
        }
    }

    // ---------- Chạy / dừng ----------

    private fun setRunningUi(r: Boolean) {
        val was = running
        running = r
        if (destroyed) return

        // Bộ đếm: bắt đầu từ 000:00:00 mỗi lần chạy, giữ nguyên số khi dừng
        handler.removeCallbacks(ticker)
        if (r) {
            if (!was) {
                startTime = SystemClock.elapsedRealtime()
                lastTick = startTime
                elapsedMs = 0L
            }
            handler.post(ticker)
        } else if (was) {
            elapsedMs = SystemClock.elapsedRealtime() - startTime
            showTimer()
        }

        playBtn.kind = if (r) IconKind.STOP else IconKind.PLAY
        playBtn.background = ovalBg(if (r) colRed else colGreen)
        playBtn.invalidate()
        if (!r) {
            pauseBtn.kind = IconKind.PAUSE
            pauseBtn.invalidate()
        }
        updateBubbleColor()
        setKeepOn(r)
        setMarkersTouchable(!r)
    }

    private fun setMarkersTouchable(touchable: Boolean) {
        items.forEach { item ->
            setTouchable(item.view, item.lp, touchable)
            val ev = item.endView
            val elp = item.endLp
            if (ev != null && elp != null) setTouchable(ev, elp, touchable)
        }
    }

    /** Giữ màn hình sáng khi đang chạy hoặc đang chờ hẹn giờ (không cần quyền WakeLock). */
    private fun setKeepOn(on: Boolean) {
        if (panelView == null) return
        val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        panelLp.flags = if (on) panelLp.flags or flag else panelLp.flags and flag.inv()
        bubbleLp.flags = if (on) bubbleLp.flags or flag else bubbleLp.flags and flag.inv()
        try {
            if (minimized) wm.updateViewLayout(bubbleView, bubbleLp)
            else panelView?.let { wm.updateViewLayout(it, panelLp) }
        } catch (e: Exception) {}
    }

    private fun togglePause() {
        val svc = AutoClickService.instance
        if (!running || svc == null) {
            Toast.makeText(this, "Chỉ tạm dừng được khi đang chạy", Toast.LENGTH_SHORT).show()
            return
        }
        if (svc.isPaused) svc.resume() else svc.pause()
        pauseBtn.kind = if (svc.isPaused) IconKind.PLAY else IconKind.PAUSE
        pauseBtn.invalidate()
    }

    private fun setTouchable(v: View, lp: WindowManager.LayoutParams, touchable: Boolean) {
        lp.flags = if (touchable) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        try { wm.updateViewLayout(v, lp) } catch (e: Exception) {}
    }

    // ---------- Hẹn giờ / đếm ngược ----------

    /** Thời gian chờ trước khi chạy: hẹn giờ HH:mm[:ss] nếu có, nếu không thì đếm ngược. */
    private fun startDelayMs(): Long {
        val t = ClickStore.startAt.trim()
        if (t.isNotEmpty()) {
            val p = t.split(":")
            val h = p.getOrNull(0)?.trim()?.toIntOrNull()
            val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            val sec = p.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            if (h != null && h in 0..23 && m in 0..59 && sec in 0..59) {
                val c = Calendar.getInstance()
                val now = c.timeInMillis
                c.set(Calendar.HOUR_OF_DAY, h)
                c.set(Calendar.MINUTE, m)
                c.set(Calendar.SECOND, sec)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_MONTH, 1)
                return c.timeInMillis - now
            }
        }
        return ClickStore.startDelaySec * 1000L
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (!pendingStart || destroyed) return
            val left = countdownEnd - SystemClock.elapsedRealtime()
            if (left <= 0L) {
                pendingStart = false
                startRunNow()
                return
            }
            val sec = (left + 999L) / 1000L
            timerText.text = String.format(
                Locale.US, "Chờ\n%02d:%02d:%02d", sec / 3600L, (sec % 3600L) / 60L, sec % 60L
            )
            handler.postDelayed(this, 200L)
        }
    }

    private fun setPendingUi(p: Boolean) {
        playBtn.kind = if (p) IconKind.STOP else IconKind.PLAY
        playBtn.background = ovalBg(if (p) colRed else colGreen)
        playBtn.invalidate()
        setKeepOn(p)
        setMarkersTouchable(!p)
    }

    private fun cancelCountdown() {
        pendingStart = false
        handler.removeCallbacks(countdownTick)
        setPendingUi(false)
        showTimer()
    }

    private fun startRunNow() {
        val svc = AutoClickService.instance
        if (svc == null) {
            setPendingUi(false)
            showTimer()
            Toast.makeText(this, "Dịch vụ Trợ năng chưa bật", Toast.LENGTH_LONG).show()
            return
        }
        handler.removeCallbacks(countdownTick)
        setRunningUi(true)
        val list = if (single) {
            ClickStore.actions.take(1).map { it.copy(type = ActionType.TAP, groupFrom = 0, groupRepeat = 0) }
        } else {
            ClickStore.actions
        }
        svc.run(
            list,
            ClickStore.loops,
            ClickStore.stopMinutes * 60_000L,
            ClickStore.stopTaps
        ) { setRunningUi(false) }
    }

    private fun toggle() {
        if (pendingStart) {
            cancelCountdown()
            return
        }
        val svc = AutoClickService.instance
        if (svc == null) {
            Toast.makeText(this, "Hãy bật dịch vụ Trợ năng Nvp Klick trước", Toast.LENGTH_LONG).show()
            return
        }
        if (running) {
            svc.stop()          // callback sẽ cập nhật giao diện
            setRunningUi(false) // phòng trường hợp callback không được gọi
            return
        }
        if (single) ensureSingle()
        if (ClickStore.actions.isEmpty()) {
            Toast.makeText(this, "Chưa có nút nào. Nhấn ＋ để thêm", Toast.LENGTH_SHORT).show()
            return
        }

        // Lấy toạ độ tâm thật trên màn hình của từng chấm
        val loc = IntArray(2)
        items.forEach { item ->
            item.view.getLocationOnScreen(loc)
            item.action.x = loc[0] + item.view.width / 2f
            item.action.y = loc[1] + item.view.height / 2f
            val ev = item.endView
            if (ev != null) {
                ev.getLocationOnScreen(loc)
                item.action.x2 = loc[0] + ev.width / 2f
                item.action.y2 = loc[1] + ev.height / 2f
            }
        }
        ClickStore.save(this)

        val delay = startDelayMs()
        if (delay > 0L) {
            pendingStart = true
            countdownEnd = SystemClock.elapsedRealtime() + delay
            setPendingUi(true)
            handler.post(countdownTick)
        } else {
            startRunNow()
        }
    }

    private fun postStoppedNotice() {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL, "Nvp Klick - cảnh báo", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val pi = PendingIntent.getActivity(
                this, 4, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(this, ALERT_CHANNEL)
                .setContentTitle("Bảng điều khiển đã tắt")
                .setContentText("Nvp Klick đã bị dừng. Chạm để mở lại.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(ALERT_ID, n)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        AutoClickService.instance?.stop()
        destroyed = true
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(countdownTick)
        if (!userQuit) postStoppedNotice()
        instance = null
        clearMarkers()
        panelView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        if (::bubbleView.isInitialized) {
            try { wm.removeView(bubbleView) } catch (e: Exception) {}
        }
        panelView = null
        super.onDestroy()
    }
}
