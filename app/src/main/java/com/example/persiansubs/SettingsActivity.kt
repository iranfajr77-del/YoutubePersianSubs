package com.example.persiansubs

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var preview: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val rawPrefs = getSharedPreferences("settings", MODE_PRIVATE)

        val green = getColor(R.color.app_green)
        val black = getColor(R.color.app_black)
        val surface = getColor(R.color.app_surface)
        val textColor = getColor(R.color.app_text)
        val dim = getColor(R.color.app_text_dim)
        val greenDark = getColor(R.color.app_green_dark)

        window.statusBarColor = black
        window.navigationBarColor = black

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(20), dp(24), dp(20), dp(32))
        root.setBackgroundColor(black)

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
            bg.setStroke(dp(1), greenDark)
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

        fun updatePreview() {
            preview.textSize = prefs.textSize.toFloat()
            preview.setTextColor(prefs.textColor)
            val alpha = (prefs.bgOpacity * 255) / 100
            val bg = GradientDrawable()
            bg.setColor(Color.argb(alpha, 0, 0, 0))
            bg.cornerRadius = dp(12).toFloat()
            preview.background = bg
        }

        // عنوان
        val title = makeText("⚙  تنظیمات", 26f, green)
        title.typeface = Typeface.DEFAULT_BOLD
        root.addView(title)

        // پیش‌نمایش
        val previewCard = card()
        previewCard.addView(makeText("پیش‌نمایش زیرنویس", 14f, dim))
        preview = makeText("این یک نمونه زیرنویس فارسی است", 20f, Color.WHITE)
        preview.gravity = Gravity.CENTER
        preview.setPadding(dp(12), dp(12), dp(12), dp(12))
        val plp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        plp.topMargin = dp(10)
        preview.layoutParams = plp
        previewCard.addView(preview)
        root.addView(previewCard)

        // اندازه زیرنویس
        val sizeCard = card()
        val sizeLabel = makeText("اندازه زیرنویس: ${prefs.textSize}", 16f, textColor)
        val sizeBar = SeekBar(this)
        sizeBar.max = 22
        sizeBar.progress = prefs.textSize - 14
        styleSeek(sizeBar)
        sizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                prefs.textSize = p + 14
                sizeLabel.text = "اندازه زیرنویس: ${p + 14}"
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

        // رنگ زیرنویس
        val colorCard = card()
        colorCard.addView(makeText("رنگ زیرنویس", 16f, textColor))

        val colorNames = listOf("سفید", "سبز", "زرد", "آبی")
        val colorValues = listOf(
            Color.WHITE,
            Color.parseColor("#39FF14"),
            Color.parseColor("#FFEB3B"),
            Color.parseColor("#4FC3F7")
        )
        val chips = ArrayList<TextView>()

        fun refreshChips() {
            for (i in chips.indices) {
                val selected = prefs.textColor == colorValues[i]
                val bg = GradientDrawable()
                bg.setColor(colorValues[i])
                bg.cornerRadius = dp(12).toFloat()
                if (selected) {
                    bg.setStroke(dp(3), Color.parseColor("#FF9800"))
                }
                chips[i].background = bg
                chips[i].text = if (selected) "✓ ${colorNames[i]}" else colorNames[i]
            }
        }

        val colorRow = LinearLayout(this)
        colorRow.orientation = LinearLayout.HORIZONTAL
        colorRow.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val crlp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        crlp.topMargin = dp(12)
        colorRow.layoutParams = crlp

        for (i in colorValues.indices) {
            val chip = TextView(this)
            chip.textSize = 15f
            chip.setTextColor(Color.BLACK)
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.gravity = Gravity.CENTER
            val clp = LinearLayout.LayoutParams(0, dp(46), 1f)
            clp.leftMargin = dp(4)
            clp.rightMargin = dp(4)
            chip.layoutParams = clp
            chip.setOnClickListener {
                prefs.textColor = colorValues[i]
                refreshChips()
                updatePreview()
            }
            chips.add(chip)
            colorRow.addView(chip)
        }
        refreshChips()
        colorCard.addView(colorRow)
        root.addView(colorCard)

        // نوع ترجمه
        val modeCard = card()
        modeCard.addView(makeText("نوع ترجمه", 16f, textColor))

        val modeNames = listOf("ترجمه آنلاین", "ترجمه آفلاین", "هر دو")
        // 0 = آفلاین ، 1 = آنلاین ، 2 = هر دو
        val modeValues = listOf(1, 0, 2)
        val modeViews = ArrayList<TextView>()

        fun refreshModes() {
            for (i in modeViews.indices) {
                val selected = prefs.translationMode == modeValues[i]
                val bg = GradientDrawable()
                bg.cornerRadius = dp(14).toFloat()
                if (selected) {
                    bg.setColor(green)
                    modeViews[i].setTextColor(Color.BLACK)
                    modeViews[i].text = "✓  ${modeNames[i]}"
                } else {
                    bg.setColor(surface)
                    bg.setStroke(dp(1), green)
                    modeViews[i].setTextColor(green)
                    modeViews[i].text = modeNames[i]
                }
                modeViews[i].background = bg
            }
        }

        for (i in modeNames.indices) {
            val opt = TextView(this)
            opt.textSize = 16f
            opt.gravity = Gravity.CENTER
            opt.typeface = Typeface.DEFAULT_BOLD
            val olp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
            olp.topMargin = dp(10)
            opt.layoutParams = olp
            opt.setOnClickListener {
                prefs.translationMode = modeValues[i]
                refreshModes()
            }
            modeViews.add(opt)
            modeCard.addView(opt)
        }
        refreshModes()

        val modeNote = makeText(
            "ترجمه آنلاین با سرویس MyMemory انجام می‌شود و به اینترنت نیاز دارد. اگر اینترنت قطع باشد یا سهمیه روزانه تمام شود، ترجمه آفلاین جایگزین می‌شود.",
            12f,
            dim
        )
        modeNote.setPadding(0, dp(10), 0, 0)
        modeCard.addView(modeNote)

        // ایمیل اختیاری
        val emailLabel = makeText(
            "ایمیل (اختیاری): با وارد کردن ایمیل، سهمیه روزانه ترجمه آنلاین از حدود ۵۰۰۰ به ۵۰۰۰۰ حرف می‌رسد.",
            12f,
            dim
        )
        emailLabel.setPadding(0, dp(14), 0, dp(6))
        modeCard.addView(emailLabel)

        val emailEdit = EditText(this)
        emailEdit.setText(rawPrefs.getString("online_email", "") ?: "")
        emailEdit.hint = "example@gmail.com"
        emailEdit.setHintTextColor(dim)
        emailEdit.setTextColor(textColor)
        emailEdit.textSize = 15f
        emailEdit.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        emailEdit.textDirection = View.TEXT_DIRECTION_LTR
        emailEdit.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        emailEdit.setSingleLine(true)
        val ebg = GradientDrawable()
        ebg.setColor(black)
        ebg.cornerRadius = dp(12).toFloat()
        ebg.setStroke(dp(1), greenDark)
        emailEdit.background = ebg
        emailEdit.setPadding(dp(12), dp(10), dp(12), dp(10))
        emailEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                rawPrefs.edit()
                    .putString("online_email", s?.toString()?.trim() ?: "")
                    .apply()
            }
        })
        modeCard.addView(emailEdit)
        root.addView(modeCard)

        // حریم خصوصی
        val privacyCard = card()
        privacyCard.addView(makeText("حریم خصوصی", 18f, green))
        val privacyText = makeText(
            "• این برنامه هیچ اطلاعات شخصی‌ای جمع نمی‌کند و حساب کاربری نمی‌خواهد.\n\n" +
            "• هیچ تبلیغ، ردیاب یا ابزار آمارگیری ندارد.\n\n" +
            "• تصویر صفحه فقط روی همین گوشی و به‌طور موقت برای خواندن زیرنویس استفاده می‌شود و هرگز ذخیره یا ارسال نمی‌شود.\n\n" +
            "• ترجمه آفلاین کاملاً روی گوشی انجام می‌شود و تنها استفاده از اینترنت در آن، دانلود یک‌بارهٔ مدل ترجمه است.\n\n" +
            "• اگر ترجمه آنلاین یا «هر دو» را انتخاب کنی، فقط متن زیرنویس خوانده‌شده (نه تصویر صفحه) برای ترجمه به سرویس MyMemory فرستاده می‌شود.\n\n" +
            "• ایمیل اختیاری است. اگر وارد کنی، فقط همراه درخواست‌های ترجمه آنلاین به MyMemory می‌رسد و جای دیگری استفاده نمی‌شود.\n\n" +
            "• با حذف برنامه، همه داده‌های آن از گوشی پاک می‌شود.",
            14f,
            textColor
        )
        privacyText.setPadding(0, dp(12), 0, 0)
        privacyText.setLineSpacing(dp(2).toFloat(), 1.15f)
        privacyCard.addView(privacyText)
        root.addView(privacyCard)

        // بازگشت
        val back = TextView(this)
        back.text = "بازگشت"
        back.textSize = 16f
        back.gravity = Gravity.CENTER
        back.setTextColor(Color.BLACK)
        back.typeface = Typeface.DEFAULT_BOLD
        val backBg = GradientDrawable()
        backBg.setColor(green)
        backBg.cornerRadius = dp(14).toFloat()
        back.background = backBg
        val blp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(52)
        )
        blp.topMargin = dp(18)
        back.layoutParams = blp
        back.setOnClickListener { finish() }
        root.addView(back)

        updatePreview()
    }
}
