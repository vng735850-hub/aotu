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

    private fun dp(v: Int)