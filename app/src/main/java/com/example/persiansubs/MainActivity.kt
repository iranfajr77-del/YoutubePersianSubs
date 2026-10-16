package com.example.persiansubs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var transStatus: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val green = getColor(R.color.app_green)
        val black = getColor(R.color.app_black)
        val surface = getColor(R.color.app_surface)
        val textColor = getColor(R.color.app_text)
        val dim = getColor(R.color.app_text_dim)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(20), dp(28), dp(20), dp(32))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(black)
        scroll.addView(root)
        setContentView(scroll)

        fun makeText(t: String, size: Float, color: Int): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.textSize = size
            tv.setTextColor(color)
            tv.textDirection = View.TEXT_DIRECTION_RTL
            tv.gravity = Gravity.START
            return tv
        }

        fun card(): LinearLayout {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable()
            bg.setColor(surface)
            bg.cornerRadius = dp(16).toFloat()
            bg.setStroke(dp(1), getColor(R.color.app_green_dark))
            c.background = bg
            c.setPadding(dp(16), dp(14), dp(16), dp(14))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(14)
            c.layoutParams = lp
            return c
        }

        fun button(t: String, filled: Boolean, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = t
            b.textSize = 17f
            b.isAllCaps = false
            val bg = GradientDrawable()
            bg.cornerRadius = dp(14).toFloat()
            if (filled) {
                bg.setColor(green)
                b.setTextColor(Color.BLACK)
            } else {
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke(dp(1), green)
                b.setTextColor(green)
            }
            b.background = bg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            )
            lp.topMargin = dp(12)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }

        // عنوان
        root.addView(makeText("Mynisalto", 30f, green))
        root.addView(makeText("زیرنویس فارسی یوتیوب", 16f, dim))

        // دکمه‌ها
        root.addView(button("شروع", true) { onStartClicked() })
        root.addView(button("توقف", false) { onStopClicked() })

        // وضعیت‌ها
        val statusCard = card()
        statusCard.addView(makeText("وضعیت", 18f, green))

        overlayStatus = makeText("", 15f, textColor)
        overlayStatus.setPadding(0, dp(10), 0, 0)
        statusCard.addView(overlayStatus)

        overlayButton = button("فعال کردن مجوز نمایش روی برنامه‌ها", false) {
            openOverlaySettings()
        }
        statusCard.addView(overlayButton)

        val readStatus = makeText(
            "خواندن زیرنویس صفحه: هنوز اضافه نشده (مرحله ۴)", 15f, dim
        )
        readStatus.setPadding(0, dp(12), 0, 0)
        statusCard.addView(readStatus)

        val engineStatus = makeText(
            "موتور ترجمه: هنوز آماده نیست (مرحله ۵)", 15f, dim
        )
        engineStatus.setPadding(0, dp(8), 0, 0)
        statusCard.addView(engineStatus)

        transStatus = makeText("ترجمه: متوقف", 15f, dim)
        transStatus.setPadding(0, dp(8), 0, 0)
        statusCard.addView(transStatus)

        root.addView(statusCard)

        // راهنما
        val helpCard = card()
        helpCard.addView(makeText("طرز استفاده", 18f, green))
        val steps = makeText(
            "۱. برنامه یوتیوب را باز کنید و ویدیوی دلخواه را پخش کنید.\n" +
            "۲. روی ویدیو بزنید و دکمه CC (زیرنویس) را روشن کنید.\n" +
            "۳. زبان زیرنویس را روی انگلیسی (English) بگذارید.\n" +
            "۴. برنامه Mynisalto را باز کنید و شروع را بزنید.\n" +
            "۵. دوباره به یوتیوب برگردید تا زیرنویس فارسی را ببینید.\n\n" +
            "توجه: فعلاً کادر شناور فقط یک متن نمونه نشان می‌دهد. ترجمه واقعی در مراحل ۴ و ۵ اضافه می‌شود.",
            15f, textColor
        )
        steps.setPadding(0, dp(10), 0, 0)
        steps.setLineSpacing(0f, 1.3f)
        helpCard.addView(steps)
        root.addView(helpCard)

        // تنظیمات
        root.addView(button("تنظیمات", false) {
            startActivity(Intent(this, SettingsAc
