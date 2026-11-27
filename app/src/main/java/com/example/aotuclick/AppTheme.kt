package com.example.autoclick

import android.graphics.Color

/**
 * Bảng màu dùng chung cho màn hình chính, hộp chỉnh nút và bảng điều khiển nổi.
 * Chế độ sáng/tối lấy từ ClickStore.darkMode (đổi bằng nút ngày–đêm ở góc trên phải).
 * Chế độ tối giữ nguyên màu cũ của app; chế độ sáng dùng các màu --ux-* của mẫu.
 */
object AppTheme {
    val dark: Boolean get() = ClickStore.darkMode

    val bg: Int get() = if (dark) Color.rgb(15, 17, 23) else Color.rgb(244, 242, 238)
    val card: Int get() = if (dark) Color.rgb(26, 29, 38) else Color.rgb(255, 255, 255)
    val field: Int get() = if (dark) Color.rgb(38, 42, 54) else Color.rgb(234, 231, 225)
    val text: Int get() = if (dark) Color.WHITE else Color.rgb(22, 21, 27)
    val sub: Int get() = if (dark) Color.rgb(150, 156, 170) else Color.rgb(87, 83, 95)
    val gray: Int get() = if (dark) Color.rgb(55, 62, 80) else Color.rgb(110, 106, 125)

    // Bảng điều khiển nổi: cùng màu với thẻ của app
    val panelBg: Int get() = Color.argb(240, Color.red(card), Color.green(card), Color.blue(card))
    val panelStroke: Int
        get() = if (dark) Color.argb(90, 255, 255, 255) else Color.argb(70, 22, 21, 27)
    val panelBtn: Int get() = if (dark) Color.rgb(45, 55, 72) else Color.rgb(37, 99, 235)
    val gripTint: Int
        get() = if (dark) Color.argb(210, 190, 196, 210) else Color.argb(200, 110, 106, 125)

    val alertTheme: Int
        get() = if (dark) android.R.style.Theme_Material_Dialog_Alert
        else android.R.style.Theme_Material_Light_Dialog_Alert
}
