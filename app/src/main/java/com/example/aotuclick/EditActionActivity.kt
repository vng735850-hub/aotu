package com.example.autoclick

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** Hộp thoại chỉnh sửa một nút: kiểu thao tác, độ trễ, thời gian giữ/kéo, thứ tự, xoá. */
class EditActionActivity : Activity() {

    private lateinit var action: ClickAction
    private var index = -1
    private lateinit var group: RadioGroup
    private lateinit var delayInput: EditText
    private lateinit var durInput: EditText
    private lateinit var durLabel: TextView
    private lateinit var groupFromInput: EditText
    private lateinit var groupRepeatInput: EditText
    private val radioIds = HashMap<Int, ActionType>()

    private val cCard: Int get() = AppTheme.card
    private val cField: Int get() = AppTheme.field
    private val cSub: Int get() = AppTheme.sub
    private val cBlue = Color.rgb(37, 99, 235)
    private val cGreen = Color.rgb(46, 160, 67)
    private val cRed = Color.rgb(211, 47, 47)
    private val cGray: Int get() = AppTheme.gray

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun roundBg(color: Int, radiusDp: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.cornerRadius = dp(radiusDp).toFloat()
        d.setColor(color)
        return d
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClickStore.ensureLoaded(this)
        index = intent.getIntExtra("index", -1)
        val a = ClickStore.actions.getOrNull(index)
        if (a == null) {
            finish()
            return
        }
        action = a

        window.setBackgroundDrawable(roundBg(cCard, 18))

        val pad = dp(16)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "Nút số ${index + 1}"
        title.textSize = 20f
        title.setTextColor(AppTheme.text)
        title.setTypeface(title.typeface, Typeface.BOLD)
        root.addView(title)

        root.addView(label("Kiểu thao tác"))
        group = RadioGroup(this)
        ActionType.values().forEach { t ->
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.setTextColor(AppTheme.text)
            rb.buttonTintList = ColorStateList.valueOf(cBlue)
            rb.text = when (t) {
                ActionType.TAP -> "Chạm (1 lần)"
                ActionType.DOUBLE -> "Chạm đúp"
                ActionType.HOLD -> "Giữ (nhấn giữ)"
                ActionType.SWIPE -> "Kéo (từ chấm số → chấm xanh ▸)"
            }
            radioIds[rb.id] = t
            group.addView(rb)
            if (t == action.type) group.check(rb.id)
        }
        root.addView(group)

        root.addView(label("Độ trễ sau nút này (ms) — chờ bao lâu rồi mới sang nút kế tiếp"))
        delayInput = field(action.delayMs.toString())
        root.addView(delayInput)

        durLabel = label("Thời gian giữ / kéo (ms) — để trống = mặc định (Giữ 1000, Kéo 400)")
        root.addView(durLabel)
        durInput = field(if (action.durationMs > 0) action.durationMs.toString() else "")
        root.addView(durInput)

        root.addView(label("Nhóm lặp: nút này là nút CUỐI của nhóm, nhóm bắt đầu từ nút số… (để trống = không dùng)"))
        groupFromInput = field(if (action.groupFrom > 0) action.groupFrom.toString() else "")
        root.addView(groupFromInput)
        root.addView(label("Tổng số lần chạy nhóm (từ 2 trở lên). Ví dụ nút 3 điền 1 và 5: nút 1–3 chạy 5 lần rồi mới sang nút 4"))
        groupRepeatInput = field(if (action.groupRepeat > 0) action.groupRepeat.toString() else "")
        root.addView(groupRepeatInput)

        group.setOnCheckedChangeListener { _, _ -> updateDurationVisibility() }
        updateDurationVisibility()

        // Hàng sắp xếp thứ tự + xoá
        val row1 = LinearLayout(this)
        row1.orientation = LinearLayout.HORIZONTAL
        row1.addView(weightBtn("↑ Lên", cGray) { move(-1) })
        row1.addView(weightBtn("↓ Xuống", cGray) { move(1) })
        row1.addView(weightBtn("Xoá", cRed) { delete() })
        root.addView(row1)

        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        row2.addView(weightBtn("Huỷ", cGray) { finish() })
        row2.addView(weightBtn("Lưu", cGreen) {
            applyFields()
            commit()
            finish()
        })
        root.addView(row2)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun label(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = 13f
        v.setTextColor(cSub)
        v.setPadding(0, dp(12), 0, 0)
        return v
    }

    private fun field(initial: String): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER
        e.setText(initial)
        e.setTextColor(AppTheme.text)
        e.textSize = 16f
        e.background = roundBg(cField, 10)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(4), 0, 0)
        e.layoutParams = lp
        return e
    }

    private fun weightBtn(t: String, color: Int, onClick: () -> Unit): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(Color.WHITE)
        v.textSize = 14f
        v.gravity = Gravity.CENTER
        v.setPadding(dp(8), dp(12), dp(8), dp(12))
        v.background = roundBg(color, 12)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(4), dp(12), dp(4), 0)
        v.layoutParams = lp
        v.setOnClickListener { onClick() }
        return v
    }

    private fun selectedType(): ActionType = radioIds[group.checkedRadioButtonId] ?: action.type

    private fun updateDurationVisibility() {
        val t = selectedType()
        val v = if (t == ActionType.HOLD || t == ActionType.SWIPE) View.VISIBLE else View.GONE
        durLabel.visibility = v
        durInput.visibility = v
    }

    private fun applyFields() {
        val newType = selectedType()
        if (newType == ActionType.SWIPE && action.type != ActionType.SWIPE) {
            // Đặt chấm kết thúc cách chấm đầu một đoạn hợp lý
            val w = resources.displayMetrics.widthPixels
            val off = 140 * resources.displayMetrics.density
            action.x2 = if (action.x + off < w) action.x + off else action.x - off
            action.y2 = action.y
        }
        action.type = newType
        action.delayMs = delayInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: action.delayMs
        action.durationMs = durInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        action.groupFrom = groupFromInput.text.toString().toIntOrNull()?.coerceIn(0, index + 1) ?: 0
        action.groupRepeat = groupRepeatInput.text.toString().toIntOrNull()?.coerceIn(0, 100000) ?: 0
    }

    private fun move(dir: Int) {
        applyFields()
        val to = index + dir
        if (to in 0 until ClickStore.actions.size) {
            val list = ClickStore.actions
            val tmp = list[index]
            list[index] = list[to]
            list[to] = tmp
        }
        commit()
        finish()
    }

    private fun delete() {
        ClickStore.actions.removeAt(index)
        commit()
        finish()
    }

    private fun commit() {
        ClickStore.save(this)
        FloatingService.instance?.refresh()
    }
}
