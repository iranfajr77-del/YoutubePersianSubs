package com.example.persiansubs

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : Activity() {

    private lateinit var mainButton: TextView

    private var downloading = false
    private var translationRunning = false
    private var waitingForCaptureConsent = false
    private var startedAt = 0L

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun makeBox(label: String, filled: Boolean, size: Float): TextView {
        val green = getColor(R.color.app_green)
        val surface = getColor(R.color.app_surface)
        val tv = TextView(this)
        tv.text = label
        tv.textSize = size
        tv.gravity = Gravity.CENTER
        tv.typeface = Typeface.DEFAULT_BOLD
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        if (filled) {
            bg.setColor(green)
            tv.setTextColor(Color.BLACK)
        } else {
            bg.setColor(surface)
            bg.setStroke(dp(1), green)
            tv.setTextColor(green)
        }
        tv.background = bg
        return tv
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val green = getColor(R.color.app_green)
        val black = getColor(R.color.app_black)
        val dim = getColor(R.color.app_text_dim)

        window.statusBarColor = black
        window.navigationBarColor = black

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setBackgroundColor(black)
        root.setPadding(dp(24), dp(40), dp(24), dp(28))
        setContentView(root)

        // بالا: نام برنامه
        val title = TextView(this)
        title.text = "Mynisalto"
        title.textSize = 32f
        title.setTextColor(green)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        root.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "زیرنویس فارسی یوتیوب"
        subtitle.textSize = 16f
        subtitle.setTextColor(dim)
        subtitle.gravity = Gravity.CENTER
        subtitle.textDirection = View.TEXT_DIRECTION_RTL
        subtitle.setPadding(0, dp(6), 0, 0)
        root.addView(subtitle)

        // وسط: دکمه بزرگ شروع / توقف
        val middle = LinearLayout(this)
        middle.orientation = LinearLayout.VERTICAL
        middle.gravity = Gravity.CENTER
        middle.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        mainButton = makeBox("شروع", true, 28f)
        mainButton.textDirection = View.TEXT_DIRECTION_RTL
        mainButton.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(96)
        )
        mainButton.setOnClickListener {
            if (downloading || waitingForCaptureConsent) return@setOnClickListener
            if (translationRunning) {
                onStopClicked()
            } else {
                onStartClicked()
            }
        }
        middle.addView(mainButton)
        root.addView(middle)

        // پایین: تنظیمات و درباره برنامه
        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val settingsBtn = makeBox("⚙  تنظیمات", false, 15f)
        settingsBtn.textDirection = View.TEXT_DIRECTION_RTL
        val slp = LinearLayout.LayoutParams(0, dp(54), 1f)
        slp.leftMargin = dp(6)
        slp.rightMargin = dp(6)
        settingsBtn.layoutParams = slp
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val aboutBtn = makeBox("درباره برنامه", false, 15f)
        aboutBtn.textDirection = View.TEXT_DIRECTION_RTL
        val alp = LinearLayout.LayoutParams(0, dp(54), 1f)
        alp.leftMargin = dp(6)
        alp.rightMargin = dp(6)
        aboutBtn.layoutParams = alp
        aboutBtn.setOnClickListener { showAbout() }

        bottom.addView(settingsBtn)
        bottom.addView(aboutBtn)
        root.addView(bottom)

        refreshButton()
    }

    override fun onResume() {
        super.onResume()
        // بعد از شروع، چند ثانیه صبر می‌کنیم تا سرویس بالا بیاد
        if (System.currentTimeMillis() - startedAt > 3000) {
            translationRunning = OverlayService.running
        }
        refreshButton()
    }

    private fun refreshButton() {
        if (downloading) {
            mainButton.text = "در حال دانلود مدل…"
            mainButton.alpha = 0.6f
            return
        }
        mainButton.alpha = if (waitingForCaptureConsent) 0.6f else 1f
        mainButton.text = if (translationRunning) "توقف" else "شروع"
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("درباره برنامه")
            .setMessage(
                "Mynisalto زیرنویس انگلیسی را از روی صفحه می‌خواند و آفلاین به فارسی ترجمه می‌کند.\n\n" +
                "طرز استفاده:\n" +
                "۱. در یوتیوب زیرنویس انگلیسی ویدیو را روشن کن.\n" +
                "۲. «شروع» را بزن و اجازه ضبط صفحه را تأیید کن.\n" +
                "۳. به یوتیوب برگرد؛ ترجمه در کادر شناور نمایش داده می‌شود.\n" +
                "۴. برای پایان، به برنامه برگرد و «توقف» را بزن.\n\n" +
                "توجه: ترجمه ماشینی کامل و بدون خطا نیست و ممکن است یک تا دو ثانیه تأخیر داشته باشد."
            )
            .setPositiveButton("بستن", null)
            .show()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun onStartClicked() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "ابتدا مجوز «نمایش روی برنامه‌ها» را فعال کن، بعد برگرد و شروع را بزن.",
                Toast.LENGTH_LONG
            ).show()
            openOverlaySettings()
            return
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }

        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val ready = models.any {
                    it.language == TranslateLanguage.PERSIAN
                }
                if (ready) {
                    requestCapture()
                } else {
                    downloadModelThenStart()
                }
            }
            .addOnFailureListener {
                requestCapture()
            }
    }

    private fun downloadModelThenStart() {
        downloading = true
        refreshButton()

        Toast.makeText(
            this,
            "مدل ترجمه برای بار اول دانلود می‌شود (حدود ۶۰ مگابایت). اینترنت روشن باشد.",
            Toast.LENGTH_LONG
        ).show()

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PERSIAN)
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded(
            DownloadConditions.Builder().build()
        ).addOnSuccessListener {
            downloading = false
            translator.close()
            refreshButton()
            requestCapture()
        }.addOnFailureListener { error ->
            downloading = false
            translator.close()
            refreshButton()
            Toast.makeText(
                this,
                "دانلود مدل ناموفق بود. اینترنت را بررسی کن. (${error.message ?: "نامشخص"})",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestCapture() {
        waitingForCaptureConsent = true
        refreshButton()

        val manager =
            getSystemService(MediaProjectionManager::class.java)

        @Suppress("DEPRECATION")
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQ_CAPTURE
        )
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQ_CAPTURE) return

        waitingForCaptureConsent = false

        if (resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra(OverlayService.EXTRA_CODE, resultCode)
            intent.putExtra(OverlayService.EXTRA_DATA, data)

            startForegroundService(intent)

            translationRunning = true
            startedAt = System.currentTimeMillis()

            Toast.makeText(
                this,
                "روشن شد؛ به یوتیوب برگرد.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            translationRunning = false

            Toast.makeText(
                this,
                "اجازه ضبط صفحه داده نشد.",
                Toast.LENGTH_LONG
            ).show()
        }
        refreshButton()
    }

    private fun onStopClicked() {
        val intent = Intent(this, OverlayService::class.java)
        intent.action = OverlayService.ACTION_STOP
        startService(intent)

        translationRunning = false
        waitingForCaptureConsent = false
        startedAt = 0L
        refreshButton()
    }

    companion object {
        const val REQ_CAPTURE = 100
    }
}
