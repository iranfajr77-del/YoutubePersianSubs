package com.example.persiansubs

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.layoutDirection = View.LAYOUT_DIRECTION_RTL
        layout.setPadding(48, 48, 48, 48)

        val title = TextView(this)
        title.text = "زیرنویس فارسی یوتیوب"
        title.textSize = 26f
        title.gravity = Gravity.CENTER

        val message = TextView(this)
        message.text = "نسخه آزمایشی: اگر این صفحه را می‌بینید، ساخت و نصب برنامه موفق بوده است."
        message.textSize = 18f
        message.gravity = Gravity.CENTER
        message.setPadding(0, 32, 0, 0)

        layout.addView(title)
        layout.addView(message)
        setContentView(layout)
    }
}
