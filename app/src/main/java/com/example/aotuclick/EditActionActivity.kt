package com.example.autoclick

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class EditActionActivity : Activity() {

    private lateinit var action: ClickAction
    private var index = -1
    private lateinit var group: RadioGroup
    private lateinit var delayInput: EditText
    private lateinit var durInput: EditText
    private lateinit var durLabel: TextView
    private val radioIds = HashMap<Int, ActionType>()

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

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Nút số ${index + 1}"
            textSize = 20f
        })

        root.addView(label("Kiểu thao tác"))
        group = RadioGroup(this)
        ActionType.values().forEach { t ->
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = when (t) {
                    ActionType.TAP -> "Chạm (1 lần)"
                    ActionType.DOUBLE -> "Chạm đúp"
                    ActionType.HOLD -> "Giữ (nhấn giữ)"
                    ActionType.SWIPE -> "Kéo (từ chấm số → chấm xanh ▸)"
                }
            }
            radioIds[rb.id] = t
            group.addView(rb)
            if (t == action.type) group.check(rb.id)
        }
        root.addView(group)

        root.addView(label("Độ trễ sau nút này (ms) — chờ bao lâu rồi mới sang nút kế tiếp"))
        delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(action.delayMs.toString())
        }
        root.addView(delayInput)

        durLabel = label("Thời gian giữ / kéo (ms) — để trống = mặc định (Giữ 1000, Kéo 400)")
        root.addView(durLabel)
        durInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            if (action.durationMs > 0) setText(action.durationMs.toString())
        }
        root.addView(durInput)

        group.setOnCheckedChangeListener { _, _ -> updateDurationVisibility() }
        updateDurationVisibility()

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(weightBtn("↑ Lên") { move(-1) })
        row1.addView(weightBtn("↓ Xuống") { move(1) })
        row1.addView(weightBtn("🗑 Xoá") { delete() })
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(weightBtn("Huỷ") { finish() })
        row2.addView(weightBtn("Lưu") {
            applyFields()
            commit()
            finish()
        })
        root.addView(row2)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun weightBtn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
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
            val w = resources.displayMetrics.widthPixels
            val off = 140 * resources.displayMetrics.density
            action.x2 = if (action.x + off < w) action.x + off else action.x - off
            action.y2 = action.y
        }
        action.type = newType
        action.delayMs = delayInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: action.delayMs
        action.durationMs = durInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 0L
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