package com.example.autoclick

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Ô cài đặt nhanh: mở bảng nổi, hoặc bật/dừng chạy khi bảng đã mở. */
class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        ClickStore.ensureLoaded(this)
        val panel = FloatingService.instance
        if (panel != null) {
            panel.toggleRun()
        } else if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Hãy cấp quyền hiển thị trên ứng dụng khác trong Nvp Klick", Toast.LENGTH_LONG).show()
        } else {
            try {
                startForegroundService(Intent(this, FloatingService::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Không mở được bảng. Hãy mở app Nvp Klick.", Toast.LENGTH_LONG).show()
            }
        }
        refreshTile()
    }

    private fun refreshTile() {
        val t = qsTile ?: return
        t.state = if (FloatingService.instance != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.updateTile()
    }
}
