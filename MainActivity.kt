package com.example.autoclick

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "AutoClick"
            textSize = 26f
        })

        val intervalInput = EditText(this).apply {
            hint = "Khoảng cách giữa các lần bấm (ms)"
            setText("500")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(intervalInput)

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
                val ms = intervalInput.text.toString().toLongOrNull() ?: 500L
                startService(
                    Intent(this@MainActivity, FloatingService::class.java)
                        .putExtra("interval", ms)
                )
                moveTaskToBack(true)
            }
        })

        root.addView(TextView(this).apply {
            text = "Kéo chấm đỏ tới vị trí cần bấm, rồi nhấn ▶ Bắt đầu."
            setPadding(0, pad, 0, 0)
        })

        setContentView(root)
    }
}
