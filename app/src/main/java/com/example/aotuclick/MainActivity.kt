package com.example.autoclick

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQ_EXPORT = 101
        private const val REQ_IMPORT = 102
    }

    // Màu nền/chữ đổi theo chế độ sáng/tối (AppTheme); màu nút giữ cố định
    private val cBg: Int get() = AppTheme.bg
    private val cCard: Int get() = AppTheme.card
    private val cField: Int get() = AppTheme.field
    private val cText: Int get() = AppTheme.text
    private val cSub: Int get() = AppTheme.sub
    private val cGreen = Color.rgb(46, 160, 67)
    private val cRed = Color.rgb(211, 47, 47)
    private val cBlue = Color.rgb(37, 99, 235)
    private val cGray: Int get() = AppTheme.gray

    private lateinit var container: FrameLayout
    private lateinit var themeSwitch: DayNightSwitch
    private val pageScrolls = arrayOfNulls<ScrollView>(3)
    private lateinit var navBar: NotchTabBar
    private var currentTab = 0

    private lateinit var startBtn: IconView
    private lateinit var startLabel: TextView
    private lateinit var overlayDot: View
    private lateinit var accDot: View
    private lateinit var overlayBtn: TextView
    private lateinit var accBtn: TextView
    private lateinit var batDot: View
    private lateinit var batBtn: TextView
    private lateinit var undoBtn: TextView
    private lateinit var startDelayInput: EditText
    private lateinit var startAtInput: EditText
    private lateinit var profileName: TextView
    private lateinit var profileInfo: TextView
    private lateinit var loopsInput: EditText
    private lateinit var stopMinInput: EditText
    private lateinit var stopTapsInput: EditText
    private lateinit var delayInput: EditText
    private lateinit var jDelayInput: EditText
    private lateinit var jPxInput: EditText
    private lateinit var alphaLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- Các hàm dựng giao diện ----------

    private fun roundBg(color: Int, radiusDp: Int, strokeColor: Int, strokeDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(color)
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
        return d
    }

    private fun ovalBg(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        return d
    }

    private fun makeCard(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundBg(cCard, 18, 0, 0)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, dp(12))
        c.layoutParams = lp
        return c
    }

    private fun textView(t: String, sizeSp: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = sizeSp
        v.setTextColor(color)
        if (bold) v.setTypeface(v.typeface, Typeface.BOLD)
        return v
    }

    private fun pill(t: String, bg: Int, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(Color.WHITE)
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setPadding(dp(14), dp(8), dp(14), dp(8))
        v.background = roundBg(bg, 18, 0, 0)
        v.setOnClickListener { onClick() }
        return v
    }

    /** Nút bo tròn chia đều chiều ngang trong một hàng. */
    private fun weightPill(t: String, bg: Int, onClick: () -> Unit): TextView {
        val v = pill(t, bg, onClick)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(3), dp(10), dp(3), 0)
        v.layoutParams = lp
        v.setPadding(dp(6), dp(10), dp(6), dp(10))
        return v
    }

    private fun numberInput(value: String): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(value)
        e.setTextColor(cText)
        e.textSize = 16f
        e.background = roundBg(cField, 10, 0, 0)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(4), 0, 0)
        e.layoutParams = lp
        return e
    }

    private fun textInput(value: String, hint: String): EditText {
        val e = numberInput(value)
        e.inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
        e.hint = hint
        e.setHintTextColor(cSub)
        return e
    }

    private fun addLabeled(parent: LinearLayout, label: String, input: EditText) {
        val l = textView(label, 13f, cSub, false)
        l.setPadding(0, dp(12), 0, 0)
        parent.addView(l)
        parent.addView(input)
    }

    private fun sectionLabel(t: String): TextView {
        val v = textView(t, 12f, cBlue, true)
        v.setPadding(0, dp(14), 0, 0)
        return v
    }

    private fun permRow(title: String, dot: View, btn: TextView): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(6), 0, dp(6))

        val dlp = LinearLayout.LayoutParams(dp(10), dp(10))
        dlp.setMargins(0, 0, dp(12), 0)
        dot.layoutParams = dlp
        row.addView(dot)

        val tv = textView(title, 14f, cText, false)
        tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tv)

        row.addView(btn)
        return row
    }

    private fun versionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ---------- Vòng đời ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClickStore.ensureLoaded(this)
        currentTab = (savedInstanceState?.getInt("tab", 0) ?: 0).coerceIn(0, 2)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // Nút ngày–đêm ở góc trên phải (giữ nguyên một đối tượng để hiệu ứng không bị ngắt khi vẽ lại màu)
        themeSwitch = DayNightSwitch(this)
        themeSwitch.setChecked(ClickStore.darkMode, false)
        themeSwitch.onChanged = { dark -> onThemeToggled(dark) }

        // Thanh tab dưới: Trang chủ · Kịch bản · Cài đặt (giữ một đối tượng để hiệu ứng không bị ngắt)
        navBar = NotchTabBar(this, listOf("Trang chủ", "Kịch bản", "Cài đặt"))
        navBar.onSelect = { i -> showTab(i) }

        container = FrameLayout(this)
        setContentView(container)
        buildUi()

        // Ẩn thanh tab khi bàn phím mở để không chiếm chỗ ô nhập
        container.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            container.getWindowVisibleDisplayFrame(r)
            val screenH = container.rootView.height
            val keyboardOpen = screenH > 0 && screenH - r.bottom > screenH * 0.15
            val want = if (keyboardOpen) View.GONE else View.VISIBLE
            if (navBar.visibility != want) navBar.visibility = want
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab)
    }

    /** Chuyển trang theo tab: lưu ô nhập, đóng bàn phím rồi chỉ hiện trang được chọn. */
    private fun showTab(i: Int) {
        if (i == currentTab) return
        saveSettings()
        currentTab = i
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
        for (k in pageScrolls.indices) {
            pageScrolls[k]?.visibility = if (k == i) View.VISIBLE else View.GONE
        }
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.decorView.systemUiVisibility = if (AppTheme.dark) {
            0
        } else {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    /** Đổi chế độ sáng/tối: lưu, đổi màu bảng nổi, dựng lại màn hình với màu mới. */
    private fun onThemeToggled(dark: Boolean) {
        saveSettings()
        ClickStore.darkMode = dark
        ClickStore.save(this)
        FloatingService.instance?.applyTheme()
        buildUi()
    }

    private fun buildUi() {
        val keepScrolls = IntArray(3) { pageScrolls[it]?.scrollY ?: 0 }
        applySystemBars()
        container.setBackgroundColor(cBg)

        // Khung chính: tiêu đề cố định ở trên, các trang cuộn ở giữa, thanh tab ở dưới
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        fun newPage(): LinearLayout {
            val p = LinearLayout(this)
            p.orientation = LinearLayout.VERTICAL
            p.setPadding(dp(18), dp(4), dp(18), dp(12))
            return p
        }
        val home = newPage()
        val scripts = newPage()
        val settings = newPage()

        // Tiêu đề (trái) + nút ngày–đêm (góc trên phải)
        val titleCol = LinearLayout(this)
        titleCol.orientation = LinearLayout.VERTICAL
        titleCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleCol.addView(textView("Nvp Klick", 28f, cText, true))
        val ver = versionName()
        val subText = if (ver.isNotEmpty()) {
            "Tự động chạm · giữ · kéo  ·  v$ver"
        } else {
            "Tự động chạm · giữ · kéo"
        }
        val sub = textView(subText, 14f, cSub, false)
        sub.setPadding(0, dp(2), 0, 0)
        titleCol.addView(sub)

        (themeSwitch.parent as? ViewGroup)?.removeView(themeSwitch)
        val swLp = LinearLayout.LayoutParams(dp(88), dp(37))
        swLp.setMargins(dp(12), dp(4), 0, 0)
        themeSwitch.layoutParams = swLp

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.setPadding(dp(18), dp(40), dp(18), dp(12))
        header.addView(titleCol)
        header.addView(themeSwitch)

        // Thẻ nút Bắt đầu
        val startCard = makeCard()
        startCard.gravity = Gravity.CENTER_HORIZONTAL
        startBtn = IconView(this, IconKind.PLAY)
        val slp = LinearLayout.LayoutParams(dp(112), dp(112))
        slp.setMargins(0, dp(6), 0, dp(10))
        startBtn.layoutParams = slp
        startBtn.setOnClickListener { onStartClicked() }
        startLabel = textView("", 14f, cSub, false)
        startLabel.gravity = Gravity.CENTER
        startCard.addView(startBtn)
        startCard.addView(startLabel)

        // Chọn kiểu bảng điều khiển
        val modeTitle = textView("KIỂU BẢNG ĐIỀU KHIỂN", 12f, cSub, true)
        modeTitle.setPadding(0, dp(14), 0, 0)
        startCard.addView(modeTitle)
        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        mode1Btn = weightPill("Kiểu 1\nMột điểm chạm", cGray) { setPanelMode(1) }
        mode2Btn = weightPill("Kiểu 2\nNhiều điểm: di chuyển + chạm", cGray) { setPanelMode(2) }
        modeRow.addView(mode1Btn)
        modeRow.addView(mode2Btn)
        startCard.addView(modeRow)
        updateModeUi()
        home.addView(startCard)

        // Thẻ quyền
        val permCard = makeCard()
        val permTitle = textView("Quyền cần thiết", 16f, cText, true)
        permTitle.setPadding(0, 0, 0, dp(6))
        permCard.addView(permTitle)

        overlayDot = View(this)
        overlayBtn = pill("Cấp quyền", cBlue) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        permCard.addView(permRow("Hiển thị trên ứng dụng khác", overlayDot, overlayBtn))

        accDot = View(this)
        accBtn = pill("Bật", cBlue) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        permCard.addView(permRow("Dịch vụ Trợ năng (Accessibility)", accDot, accBtn))

        batDot = View(this)
        batBtn = pill("Mở", cBlue) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Máy không mở được màn hình này", Toast.LENGTH_SHORT).show()
            }
        }
        permCard.addView(permRow("Không tối ưu pin (chạy nền lâu)", batDot, batBtn))
        permCard.addView(
            textView(
                "Chạy nền lâu: chọn \"Không tối ưu\" cho Nvp Klick. Máy Xiaomi/Oppo/Vivo/Samsung: vào Cài đặt > Pin > Ứng dụng này, bật \"Tự khởi chạy\" và \"Không hạn chế nền\".",
                12f, cSub, false
            )
        )
        home.addView(permCard)

        // Thẻ kịch bản
        val profCard = makeCard()
        profCard.addView(textView("Kịch bản", 16f, cText, true))
        profileName = textView("", 18f, cText, true)
        profileName.setPadding(0, dp(6), 0, 0)
        profCard.addView(profileName)
        profileInfo = textView("", 13f, cSub, false)
        profCard.addView(profileInfo)

        val rowA = LinearLayout(this)
        rowA.orientation = LinearLayout.HORIZONTAL
        rowA.addView(weightPill("Chọn", cBlue) { chooseProfile() })
        rowA.addView(weightPill("Mới", cGreen) { newProfile() })
        rowA.addView(weightPill("Nhân đôi", cGray) { duplicateProfile() })
        profCard.addView(rowA)

        val rowB = LinearLayout(this)
        rowB.orientation = LinearLayout.HORIZONTAL
        rowB.addView(weightPill("Đổi tên", cGray) { renameProfile() })
        rowB.addView(weightPill("Xoá", cRed) { deleteProfile() })
        undoBtn = weightPill("Hoàn tác", cBlue) {
            ClickStore.undo()
            afterProfileChange()
            Toast.makeText(this@MainActivity, "Đã hoàn tác", Toast.LENGTH_SHORT).show()
        }
        rowB.addView(undoBtn)
        profCard.addView(rowB)

        val rowC = LinearLayout(this)
        rowC.orientation = LinearLayout.HORIZONTAL
        rowC.addView(weightPill("Xuất file JSON", cGray) { exportProfiles() })
        rowC.addView(weightPill("Nhập file JSON", cGray) { importProfiles() })
        profCard.addView(rowC)
        scripts.addView(profCard)

        // Thẻ cài đặt
        val setCard = makeCard()
        setCard.addView(textView("Cài đặt", 16f, cText, true))

        setCard.addView(sectionLabel("THEO KỊCH BẢN"))
        loopsInput = numberInput(ClickStore.loops.toString())
        addLabeled(setCard, "Số vòng lặp (0 = lặp vô hạn)", loopsInput)
        stopMinInput = numberInput(ClickStore.stopMinutes.toString())
        addLabeled(setCard, "Tự dừng sau (phút) (0 = không giới hạn)", stopMinInput)
        stopTapsInput = numberInput(ClickStore.stopTaps.toString())
        addLabeled(setCard, "Tự dừng sau số lần thao tác (0 = không giới hạn)", stopTapsInput)

        setCard.addView(sectionLabel("CHUNG"))
        delayInput = numberInput(ClickStore.defaultDelay.toString())
        addLabeled(setCard, "Độ trễ mặc định cho nút mới (ms)", delayInput)
        jDelayInput = numberInput(ClickStore.jitterDelayPct.toString())
        addLabeled(setCard, "Ngẫu nhiên độ trễ ± (%) (0 = tắt)", jDelayInput)
        jPxInput = numberInput(ClickStore.jitterPx.toString())
        addLabeled(setCard, "Lệch vị trí chạm ngẫu nhiên ± (px) (0 = tắt)", jPxInput)

        setCard.addView(sectionLabel("HẸN GIỜ & PHÍM TẮT"))
        startDelayInput = numberInput(ClickStore.startDelaySec.toString())
        addLabeled(setCard, "Đếm ngược trước khi chạy (giây) (0 = chạy ngay)", startDelayInput)
        startAtInput = textInput(ClickStore.startAt, "vd 08:00:00")
        addLabeled(setCard, "Hẹn giờ bắt đầu lúc HH:mm:ss (để trống = không hẹn; nếu giờ đã qua thì chờ tới ngày mai)", startAtInput)

        val hotkey = CheckBox(this)
        hotkey.text = "Phím giảm âm lượng để chạy/dừng (cần bảng nổi đang mở)"
        hotkey.setTextColor(cText)
        hotkey.textSize = 13f
        hotkey.setPadding(0, dp(12), 0, 0)
        hotkey.isChecked = ClickStore.volumeHotkey
        hotkey.setOnCheckedChangeListener { _, checked ->
            ClickStore.volumeHotkey = checked
            ClickStore.save(this@MainActivity)
        }
        setCard.addView(hotkey)

        alphaLabel = textView("", 13f, cSub, false)
        alphaLabel.setPadding(0, dp(12), 0, dp(4))
        setCard.addView(alphaLabel)

        val seek = SeekBar(this)
        seek.max = 70
        seek.progress = ClickStore.panelAlpha - 30
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                ClickStore.panelAlpha = p + 30
                updateAlphaLabel()
                FloatingService.instance?.applyAlpha()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                ClickStore.save(this@MainActivity)
            }
        })
        setCard.addView(seek)
        updateAlphaLabel()
        settings.addView(setCard)

        // Nút xoá tất cả nút của kịch bản
        val clearBtn = pill("Xoá tất cả nút của kịch bản này", Color.TRANSPARENT) {
            ClickStore.snapshot()
            ClickStore.actions.clear()
            ClickStore.save(this@MainActivity)
            FloatingService.instance?.refresh()
            refreshProfileUi()
            Toast.makeText(this@MainActivity, "Đã xoá tất cả nút (có thể Hoàn tác)", Toast.LENGTH_SHORT).show()
        }
        clearBtn.background = roundBg(Color.TRANSPARENT, 18, cRed, 1)
        clearBtn.setTextColor(cRed)
        clearBtn.setPadding(dp(14), dp(12), dp(14), dp(12))
        val clp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        clp.setMargins(0, 0, 0, dp(12))
        clearBtn.layoutParams = clp
        scripts.addView(clearBtn)

        // Hướng dẫn
        val helpCard = makeCard()
        val helpTitle = textView("Cách dùng", 16f, cText, true)
        helpTitle.setPadding(0, 0, 0, dp(6))
        helpCard.addView(helpTitle)
        helpCard.addView(
            textView(
                "1. Cấp 2 quyền ở trên (hiển thị trên ứng dụng khác và Trợ năng).\n" +
                    "2. Nhấn nút ▶ lớn để mở bảng điều khiển nổi.\n" +
                    "3. Trên bảng nổi: ＋ thêm nút chạm, ➜ thêm nút kéo, － xoá nút cuối, ⚙ mở cài đặt, ˄ thu nhỏ thành bong bóng (chạm bong bóng để mở lại), ✕ đóng.\n" +
                    "4. Kéo từng nút số tới vị trí cần chạm. Chạm vào nút (không kéo) để chọn kiểu Chạm / Chạm đúp / Giữ / Kéo, chỉnh độ trễ, đổi thứ tự hoặc xoá.\n" +
                    "5. Kiểu Kéo có thêm chấm xanh ▸ là điểm kết thúc.\n" +
                    "6. Nhấn nút ▶ trên bảng nổi để chạy, nhấn lại để dừng. Ô số dưới nút ▶ hiện thời gian đã chạy (giờ:phút:giây) và số lần thao tác (×N).\n" +
                    "7. Mỗi kịch bản có danh sách nút riêng và điều kiện tự dừng riêng (số vòng, số phút, số lần thao tác).\n" +
                    "8. Thông báo của app có nút Dừng, Mở bảng và Tắt để điều khiển nhanh khi bảng đang thu nhỏ.\n" +
                    "9. Nút ⏸ trên bảng nổi để tạm dừng / chạy tiếp. Có thể đặt đếm ngược hoặc hẹn giờ ở phần Cài đặt; màn hình được giữ sáng trong lúc chờ và chạy.\n" +
                    "10. Nhóm lặp: mở nút cuối của nhóm, điền nhóm bắt đầu từ nút số mấy và số lần lặp.\n" +
                    "11. Có thể thêm ô \"Nvp Klick\" vào Cài đặt nhanh để bật bảng và chạy/dừng.",
                13f, cSub, false
            )
        )
        home.addView(helpCard)

        val pages = arrayOf(home, scripts, settings)
        val frame = FrameLayout(this)
        for (i in pages.indices) {
            val sc = ScrollView(this)
            sc.setBackgroundColor(cBg)
            sc.addView(pages[i])
            sc.visibility = if (i == currentTab) View.VISIBLE else View.GONE
            frame.addView(
                sc,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            pageScrolls[i] = sc
            val keep = keepScrolls[i]
            if (keep > 0) sc.post { sc.scrollTo(0, keep) }
        }

        (navBar.parent as? ViewGroup)?.removeView(navBar)
        col.addView(header)
        col.addView(
            frame,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        col.addView(
            navBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.removeAllViews()
        container.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        navBar.setSelectedIndex(currentTab)
        navBar.applyTheme()

        refreshProfileUi()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (::profileName.isInitialized) refreshProfileUi()
    }

    override fun onPause() {
        super.onPause()
        if (::jPxInput.isInitialized) saveSettings()
    }

    // ---------- Kịch bản ----------

    private fun refreshProfileUi() {
        val c = ClickStore.current()
        profileName.text = c.name
        undoBtn.visibility = if (ClickStore.hasUndo) View.VISIBLE else View.GONE
        profileInfo.text = "Kịch bản ${ClickStore.activeIndex + 1}/${ClickStore.profiles.size} · ${c.actions.size} nút"
    }

    /** Nạp lại các ô nhập theo kịch bản đang chọn. */
    private fun loadFields() {
        loopsInput.setText(ClickStore.loops.toString())
        stopMinInput.setText(ClickStore.stopMinutes.toString())
        stopTapsInput.setText(ClickStore.stopTaps.toString())
        delayInput.setText(ClickStore.defaultDelay.toString())
        jDelayInput.setText(ClickStore.jitterDelayPct.toString())
        jPxInput.setText(ClickStore.jitterPx.toString())
        startDelayInput.setText(ClickStore.startDelaySec.toString())
        startAtInput.setText(ClickStore.startAt)
    }

    private fun afterProfileChange() {
        ClickStore.save(this)
        FloatingService.instance?.refresh()
        loadFields()
        refreshProfileUi()
    }

    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this)
        input.setText(initial)
        input.isSingleLine = true
        input.setTextColor(cText)
        val box = LinearLayout(this)
        box.setPadding(dp(20), dp(8), dp(20), 0)
        box.addView(input)
        AlertDialog.Builder(this, AppTheme.alertTheme)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) onOk(n)
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun chooseProfile() {
        saveSettings()
        val names = ClickStore.profiles.map { it.name + " (" + it.actions.size + " nút)" }.toTypedArray()
        AlertDialog.Builder(this, AppTheme.alertTheme)
            .setTitle("Chọn kịch bản")
            .setItems(names) { _, which ->
                ClickStore.selectProfile(which)
                afterProfileChange()
            }
            .show()
    }

    private fun newProfile() {
        saveSettings()
        askName("Kịch bản mới", "Kịch bản ${ClickStore.profiles.size + 1}") { n ->
            ClickStore.addProfile(n)
            afterProfileChange()
        }
    }

    private fun duplicateProfile() {
        saveSettings()
        ClickStore.duplicateActive()
        afterProfileChange()
    }

    private fun renameProfile() {
        askName("Đổi tên kịch bản", ClickStore.current().name) { n ->
            ClickStore.current().name = n
            afterProfileChange()
        }
    }

    private fun deleteProfile() {
        val only = ClickStore.profiles.size <= 1
        val msg = if (only) {
            "Đây là kịch bản duy nhất, các nút sẽ được xoá hết nhưng kịch bản vẫn giữ lại."
        } else {
            "Xoá kịch bản \"" + ClickStore.current().name + "\"?"
        }
        AlertDialog.Builder(this, AppTheme.alertTheme)
            .setTitle("Xoá kịch bản")
            .setMessage(msg)
            .setPositiveButton("Xoá") { _, _ ->
                ClickStore.deleteActive()
                afterProfileChange()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    // ---------- Xuất / nhập file kịch bản ----------

    private fun exportProfiles() {
        saveSettings()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, "nvpklick-kichban.json")
        startActivityForResult(i, REQ_EXPORT)
    }

    private fun importProfiles() {
        saveSettings()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_IMPORT)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == REQ_EXPORT) {
                val bytes = ClickStore.exportJson(this).toByteArray(Charsets.UTF_8)
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this, "Đã xuất file kịch bản", Toast.LENGTH_SHORT).show()
            } else if (requestCode == REQ_IMPORT) {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val n = ClickStore.importJson(this, text)
                afterProfileChange()
                Toast.makeText(this, "Đã nhập $n kịch bản", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi đọc/ghi file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Xử lý ----------

    private lateinit var mode1Btn: TextView
    private lateinit var mode2Btn: TextView

    private fun updateModeUi() {
        val m = ClickStore.panelMode
        mode1Btn.background = roundBg(if (m == 1) cBlue else cGray, 18, 0, 0)
        mode2Btn.background = roundBg(if (m == 2) cBlue else cGray, 18, 0, 0)
    }

    private fun setPanelMode(m: Int) {
        if (ClickStore.panelMode == m) return
        ClickStore.panelMode = m
        ClickStore.save(this)
        updateModeUi()
        FloatingService.instance?.applyMode()
    }

    private fun updateAlphaLabel() {
        alphaLabel.text = "Độ đậm bảng điều khiển nổi: ${ClickStore.panelAlpha}%"
    }

    private fun intOf(e: EditText, max: Int): Int =
        e.text.toString().toIntOrNull()?.coerceIn(0, max) ?: 0

    private fun saveSettings() {
        ClickStore.loops = intOf(loopsInput, 1_000_000)
        ClickStore.stopMinutes = intOf(stopMinInput, 100_000)
        ClickStore.stopTaps = intOf(stopTapsInput, 100_000_000)
        ClickStore.defaultDelay = delayInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 500L
        ClickStore.jitterDelayPct = intOf(jDelayInput, 100)
        ClickStore.jitterPx = intOf(jPxInput, 100)
        ClickStore.startDelaySec = intOf(startDelayInput, 3600)
        ClickStore.startAt = startAtInput.text.toString().trim()
        ClickStore.save(this)
    }

    private fun onStartClicked() {
        saveSettings()
        if (FloatingService.instance != null) {
            FloatingService.instance?.requestQuit()
            Handler(Looper.getMainLooper()).postDelayed({ refreshStatus() }, 300L)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác trước", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startForegroundService(Intent(this, FloatingService::class.java))
        moveTaskToBack(true)
    }

    private fun refreshStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = AutoClickService.instance != null
        overlayDot.background = ovalBg(if (overlayOk) cGreen else cRed)
        accDot.background = ovalBg(if (accOk) cGreen else cRed)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val batOk = pm.isIgnoringBatteryOptimizations(packageName)
        batDot.background = ovalBg(if (batOk) cGreen else cRed)
        batBtn.text = if (batOk) "Đã tắt" else "Mở"
        overlayBtn.text = if (overlayOk) "Đã cấp" else "Cấp quyền"
        accBtn.text = if (accOk) "Đã bật" else "Bật"

        val on = FloatingService.instance != null
        startBtn.kind = if (on) IconKind.STOP else IconKind.PLAY
        startBtn.background = ovalBg(if (on) cRed else cGreen)
        startBtn.invalidate()
        startLabel.text = if (on) {
            "Bảng điều khiển đang bật\nNhấn để tắt"
        } else {
            "Nhấn để mở bảng điều khiển nổi"
        }
    }
}
