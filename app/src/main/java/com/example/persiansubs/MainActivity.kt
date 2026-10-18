
package com.example.persiansubs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var transStatus: TextView
    private lateinit var engineStatus: TextView
    private lateinit var downloadButton: Button
    private lateinit var mainButton: Button

    private var downloading = false
    private var translationRunning = false
    private var waitingForCaptureConsent = false

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val green = getColor(R.color.app_green)
        val black = getColor(R.color.app_black)
        val surface = getColor(R.color.app_surface)
        val textColor = getColor(R.color.app_text)
        val dim = getColor(R.color.app_text_dim)

        window.statusBarColor = black
        window.navigationBarColor = black

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(black)
        }

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(black)
        scroll.addView(root)
        setContentView(scroll)

        fun makeText(
            value: String,
            size: Float,
            color: Int,
            centered: Boolean = false
        ): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(color)
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = if (centered) {
                    Gravity.CENTER
                } else {
                    Gravity.START
                }
                includeFontPadding = true
            }
        }

        fun card(): LinearLayout {
            val background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), getColor(R.color.app_green_dark))
            }

            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = background
                setPadding(dp(16), dp(16), dp(16), dp(16))

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(16)
                }
            }
        }

        fun makeButton(
            label: String,
            filled: Boolean,
            onClick: () -> Unit
        ): Button {
            return Button(this).apply {
                text = label
                textSize = 15f
                isAllCaps = false
                minHeight = dp(52)
                minimumHeight = dp(52)
                setPadding(dp(12), dp(4), dp(12), dp(4))

                val shape = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()

                    if (filled) {
                        setColor(green)
                    } else {
                        setColor(surface)
                        setStroke(dp(1), green)
                    }
                }

                background = shape
                setTextColor(if (filled) Color.BLACK else green)

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(54)
                ).apply {
                    topMargin = dp(10)
                }

                setOnClickListener { onClick() }
            }
        }

        // Header
        root.addView(
            makeText("Mynisalto", 30f, green, true).apply {
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(0, dp(4), 0, dp(4))
            }
        )

        root.addView(
            makeText(
                "زیرنویس فارسی یوتیوب",
                16f,
                dim,
                true
            ).apply {
                setPadding(0, 0, 0, dp(8))
            }
        )

        root.addView(
            makeText(
                "ویدیو رو ببین؛ ترجمه رو فارسی بخون.",
                14f,
                textColor,
                true
            ).apply {
                setPadding(0, dp(4), 0, dp(10))
            }
        )

        // Main start / stop button
        mainButton = makeButton(
            "▶  شروع ترجمه",
            true
        ) {
            if (translationRunning) {
                onStopClicked()
            } else if (!waitingForCaptureConsent) {
                onStartClicked()
            }
        }.apply {
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(66)
            ).apply {
                topMargin = dp(20)
                bottomMargin = dp(4)
            }
        }

        root.addView(mainButton)

        root.addView(
            makeText(
                "برای شروع، مجوزهای لازم را فعال کن.",
                13f,
                dim,
                true
            ).apply {
                setPadding(0, dp(6), 0, dp(4))
            }
        )

        // Status card
        val statusCard = card()
        statusCard.addView(
            makeText("وضعیت برنامه", 18f, green)
        )

        overlayStatus = makeText("", 14f, textColor)
        overlayStatus.setPadding(0, dp(12), 0, dp(4))
        statusCard.addView(overlayStatus)

        overlayButton = makeButton(
            "فعال کردن مجوز نمایش روی برنامه‌ها",
            false
        ) {
            openOverlaySettings()
        }
        statusCard.addView(overlayButton)

        statusCard.addView(
            makeText(
                "خواندن زیرنویس صفحه",
                14f,
                textColor
            ).apply {
                setPadding(0, dp(12), 0, dp(4))
            }
        )

        engineStatus = makeText(
            "موتور ترجمه: در حال بررسی…",
            14f,
            dim
        ).apply {
            setPadding(0, dp(8), 0, dp(4))
        }
        statusCard.addView(engineStatus)

        downloadButton = makeButton(
            "دانلود مدل ترجمه (حدود ۶۰ مگابایت)",
            false
        ) {
            downloadModel()
        }
        statusCard.addView(downloadButton)

        transStatus = makeText(
            "ترجمه: متوقف",
            14f,
            dim
        ).apply {
            setPadding(0, dp(12), 0, dp(2))
        }
        statusCard.addView(transStatus)

        root.addView(statusCard)

        // Instructions card
        val helpCard = card()
        helpCard.addView(
            makeText("راهنمای استفاده", 18f, green)
        )

        val steps = makeText(
            "۱. ابتدا مدل ترجمه را با اینترنت دانلود کن.\n\n" +
            "۲. یوتیوب را باز کن و ویدیوت را پخش کن.\n\n" +
            "۳. اگر ویدیو زیرنویس انگلیسی دارد، آن را روشن کن.\n\n" +
            "۴. به Mynisalto برگرد و «شروع ترجمه» را بزن.\n\n" +
            "۵. اجازه ضبط صفحه را تأیید کن و به یوتیوب برگرد.\n\n" +
            "برای پایان کار، دکمه «توقف ترجمه» را بزن.",
            14f,
            textColor
        ).apply {
            setPadding(0, dp(12), 0, 0)
            setLineSpacing(dp(2).toFloat(), 1.15f)
        }

        helpCard.addView(steps)
        root.addView(helpCard)

        root.addView(
            makeButton("⚙  تنظیمات", false) {
                startActivity(
                    Intent(this, SettingsActivity::class.java)
                )
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshModelStatus()
    }

    private fun refreshStatus() {
        val granted = Settings.canDrawOverlays(this)

        if (granted) {
            overlayStatus.text =
                "مجوز نمایش روی برنامه‌ها: فعال  ✓"
            overlayStatus.setTextColor(
                getColor(R.color.app_green)
            )
            overlayButton.visibility = View.GONE
        } else {
            overlayStatus.text =
                "مجوز نمایش روی برنامه‌ها: غیرفعال"
            overlayStatus.setTextColor(
                Color.parseColor("#FF6B6B")
            )
            overlayButton.visibility = View.VISIBLE
        }
    }

    private fun setModelReady(ready: Boolean) {
        if (ready) {
            engineStatus.text = "موتور ترجمه: آماده  ✓"
            engineStatus.setTextColor(
                getColor(R.color.app_green)
            )
            downloadButton.visibility = View.GONE
        } else {
            engineStatus.text =
                "موتور ترجمه: مدل دانلود نشده"
            engineStatus.setTextColor(
                Color.parseColor("#FF6B6B")
            )
            downloadButton.visibility = View.VISIBLE
        }
    }

    private fun refreshModelStatus() {
        if (downloading) return

        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                val ready = models.any {
                    it.language == TranslateLanguage.PERSIAN
                }
                setModelReady(ready)
            }
            .addOnFailureListener {
                setModelReady(false)
            }
    }

    private fun downloadModel() {
        if (downloading) return

        downloading = true
        downloadButton.isEnabled = false
        engineStatus.text =
            "مدل در حال دانلود است؛ لطفاً صبر کن…"
        engineStatus.setTextColor(
            getColor(R.color.app_text)
        )

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
            downloadButton.isEnabled = true
            setModelReady(true)

            Toast.makeText(
                this,
                "مدل ترجمه آماده شد.",
                Toast.LENGTH_LONG
            ).show()
        }.addOnFailureListener { error ->
            downloading = false
            translator.close()
            downloadButton.isEnabled = true
            setModelReady(false)

            engineStatus.text =
                "دانلود ناموفق بود؛ اینترنت را بررسی کن."

            Toast.makeText(
                this,
                "خطا: ${error.message ?: "نامشخص"}",
                Toast.LENGTH_LONG
            ).show()
        }
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
                "ابتدا مجوز نمایش روی برنامه‌ها را فعال کن.",
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

        waitingForCaptureConsent = true
        mainButton.isEnabled = false

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
        mainButton.isEnabled = true

        if (resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra(OverlayService.EXTRA_CODE, resultCode)
            intent.putExtra(OverlayService.EXTRA_DATA, data)

            startForegroundService(intent)

            translationRunning = true
            mainButton.text = "■  توقف ترجمه"
            transStatus.text = "ترجمه: خواندن زیرنویس روشن است"
            transStatus.setTextColor(
                getColor(R.color.app_green)
            )

            Toast.makeText(
                this,
                "روشن شد؛ به یوتیوب برگرد.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            translationRunning = false
            mainButton.text = "▶  شروع ترجمه"

            Toast.makeText(
                this,
                "اجازه ضبط صفحه داده نشد.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onStopClicked() {
        val intent = Intent(this, OverlayService::class.java)
        intent.action = OverlayService.ACTION_STOP
        startService(intent)

        translationRunning = false
        waitingForCaptureConsent = false

        mainButton.text = "▶  شروع ترجمه"
        transStatus.text = "ترجمه: متوقف"
        transStatus.setTextColor(
            getColor(R.color.app_text_dim)
        )
    }

    companion object {
        const val REQ_CAPTURE = 100
    }
}
