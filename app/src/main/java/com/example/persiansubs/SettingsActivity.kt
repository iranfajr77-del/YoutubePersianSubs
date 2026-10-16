package com.example.persiansubs

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val green = getColor(R.color.app_green)
        val black = getColor(R.color.app_black)
        val surface = getColor(R.color.app_surface)
        val textColor = getColor(R.color.app_text)
        val dim = getColor(R.color.app_text_dim)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(20), dp(24), dp(20), dp(32))

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

        fun styleSeek(sb: SeekBar) {
            sb.progressTintList = android.content.res.ColorStateList.valueOf(green)
            sb.thumbTintList = android.content.res.ColorStateList.valueOf(green)
        }

        fun button(t: String, filled: Boolean, onClick: () -> Unit): Button {
            val b = Button(this)
            b.text = t
            b.textSize = 16f
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
                dp(50)
            )
            lp.topMargin = dp(10)
            b.layoutParams = lp
            b.setOnClickListener { onClick() }
            return b
        }

        fun updatePreview() {
            preview.textSize = prefs.textSize.toFloat()
            val alpha = (prefs.bgOpacity * 255) / 100
            preview.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        }

        root.addView(makeText("تنظیمات", 26f, green))

        // پیش‌نمایش
        val previewCard = card()
        previewCard.addView(makeText("پیش‌نمایش زیرنویس", 14f, dim))
        preview = makeText("این یک نمونه زیرنویس فارسی است", 20f, Color.WHITE)
        preview.gravity = Gravity.CENTER
        preview.setPadding(dp(12), dp(12), dp(12), dp(12))
        previewCard.addView(preview)
        root.addView(previewCard)

        // اندازه متن
        val sizeCard = card()
        val sizeLabel = makeText("اندازه متن: ${prefs.textSize}", 16f, textColor)
        val sizeBar = SeekBar(this)
        sizeBar.max = 22
        sizeBar.progress = prefs.textSize - 14
        styleSeek(sizeBar)
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                prefs.textSize = p + 14
                sizeLabel.text = "اندازه متن: ${p + 14}"
                updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sizeCard.addView(sizeLabel)
        sizeCard.addView(sizeBar)
        root.addView(sizeCard)

        // شفافیت
        val opCard = card()
        val opLabel = makeText("شفافیت پس‌زمینه: ${prefs.bgOpacity}٪", 16f, textColor)
        val opBar = SeekBar(this)
        opBar.max = 100
        opBar.progress = prefs.bgOpacity
        styleSeek(opBar)
        opBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                prefs.bgOpacity = p
                opLabel.text = "شفافیت پس‌زمینه: ${p}٪"
                updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        opCard.addView(opLabel)
        opCard.addView(opBar)
        root.addView(opCard)

        // حالت ترجمه
        val modeCard = card()
        val modeDesc = makeText("", 13f, dim)
        fun updateModeText(auto: Boolean) {
            modeDesc.text = if (auto) {
                "خودکار: هر زیرنویس جدید خودبه‌خود ترجمه می‌شود."
            } else {
                "دستی: فقط با زدن دکمه ترجمه انجام می‌شود."
            }
        }
        val modeSwitch = Switch(this)
        modeSwitch.text = "ترجمه خودکار"
        modeSwitch.textSize = 16f
        modeSwitch.setTextColor(textColor)
        modeSwitch.isChecked = prefs.autoMode
        modeSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoMode = checked
            updateModeText(checked)
        }
        updateModeText(prefs.autoMode)
        modeCard.addView(modeSwitch)
        modeCard.addView(modeDesc)
        modeCard.addView(
            makeText("(این گزینه ذخیره می‌شود و در مرحله ترجمه اعمال می‌شود.)", 12f, dim)
        )
        root.addView(modeCard)

        // مدل ترجمه
        val modelCard = card()
        modelCard.addView(makeText("مدل ترجمه انگلیسی به فارسی", 16f, textColor))
        modelCard.addView(
            makeText("وضعیت: هنوز دانلود نشده. دانلود مدل در مرحله ۵ اضافه می‌شود.", 13f, dim)
        )
        root.addView(modelCard)

        // حافظه
        root.addView(button("پاک کردن حافظه ترجمه", false) {
            getSharedPreferences("translation_cache", MODE_PRIVATE).edit().clear().apply()
            Toast.makeText(this, "حافظه ترجمه پاک شد", Toast.LENGTH_SHORT).show()
        })

        // حریم خصوصی
        root.addView(button("حریم خصوصی", false) {
            AlertDialog.Builder(this)
                .setTitle("حریم خصوصی")
                .setMessage(
                    "• برنامه هیچ اطلاعات شخصی جمع نمی‌کند.\n" +
                    "• هیچ تبلیغ یا ردیاب ندارد.\n" +
                    "• زیرنویس‌ها و تصویر صفحه به هیچ سرور بیرونی فرستاده نمی‌شوند.\n" +
                    "• تنها استفاده از اینترنت، دانلود یک‌بار مدل ترجمه است."
                )
                .setPositiveButton("بستن", null)
                .show()
        })

        root.addView(button("بازگشت", true) { finish() })

        updatePreview()
    }
}
