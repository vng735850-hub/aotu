package com.example.autoclick

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClickStore.ensureLoaded(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Nvp Klick"
            textSize = 26f
        })

        statusText = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(statusText)

        root.addView(TextView(this).apply { text = "Số vòng lặp (0 = lặp vô hạn)" })
        val loopsInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ClickStore.loops.toString())
        }
        root.addView(loopsInput)

        root.addView(TextView(this).apply { text = "Độ trễ mặc định cho nút mới (ms)" })
        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(ClickStore.defaultDelay.toString())
        }
        root.addView(delayInput)

        root.addView(Button(this).apply {
            text = "1. Cấp quyền hiển thị trên ứng dụng khác"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(Button(this).apply {
            text = "2. Bật dịch vụ Trợ năng (Accessibility)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "3. Mở bảng điều khiển nổi"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Chưa cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                ClickStore.loops = loopsInput.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
                ClickStore.defaultDelay = delayInput.text.toString().toLongOrNull()?.coerceAtLeast(0L) ?: 500L
                ClickStore.save(this@MainActivity)
                startForegroundService(Intent(this@MainActivity, FloatingService::class.java))
                moveTaskToBack(true)
            }
        })

        root.addView(Button(this).apply {
            text = "Xoá tất cả các nút"
            setOnClickListener {
                ClickStore.actions.clear()
                ClickStore.save(this@MainActivity)
                FloatingService.instance?.refresh()
                Toast.makeText(this@MainActivity, "Đã xoá tất cả nút", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(TextView(this).apply {
            text = "Cách dùng:\n" +
                "• Nhấn ＋ trên bảng nổi để thêm nút. Các nút được đánh số 1, 2, 3… theo thứ tự chạy.\n" +
                "• Kéo từng nút tới vị trí cần thao tác.\n" +
                "• Chạm vào một nút (không kéo) để chọn kiểu: Chạm / Chạm đúp / Giữ / Kéo, chỉnh độ trễ, đổi thứ tự hoặc xoá.\n" +
                "• Kiểu Kéo có thêm chấm xanh ▸ là điểm kết thúc, hãy kéo nó tới nơi cần thả.\n" +
                "• Nhấn ▶ Bắt đầu để chạy, ⏸ Dừng để dừng."
            setPadding(0, pad, 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val overlay = if (Settings.canDrawOverlays(this)) "✔" else "✘"
        val acc = if (AutoClickService.instance != null) "✔" else "✘"
        statusText.text = "$overlay Quyền hiển thị trên ứng dụng khác\n$acc Dịch vụ Trợ năng"
    }
}
