package com.example.persiansubs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var box: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (box == null) showOverlay()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mynisalto", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Mynisalto")
            .setContentText("زیرنویس شناور فعال است")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun showOverlay() {
        val prefs = Prefs(this)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = windowManager

        val density = resources.displayMetrics.density
        val tv = TextView(this)
        tv.text = "این یک نمونه زیرنویس فارسی است"
        tv.textSize = prefs.textSize.toFloat()
        tv.setTextColor(Color.WHITE)
        tv.gravity = Gravity.CENTER
        tv.textDirection = View.TEXT_DIRECTION_RTL
        val pad = (10 * density).toInt()
        tv.setPadding(pad, pad, pad, pad)

        val bg = GradientDrawable()
        bg.setColor(Color.argb(prefs.bgOpacity * 255 / 100, 0, 0, 0))
        bg.cornerRadius = 12 * density
        tv.background = bg

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = (150 * density).toInt()

        var startY = 0
        var touchY = 0f
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = params.y
                    touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = startY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(tv, params)
                }
            }
            true
        }

        windowManager.addView(tv, params)
        box = tv
    }

    override fun onDestroy() {
        box?.let { wm?.removeView(it) }
        box = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.persiansubs.STOP"
        const val CHANNEL_ID = "overlay"
    }
}
