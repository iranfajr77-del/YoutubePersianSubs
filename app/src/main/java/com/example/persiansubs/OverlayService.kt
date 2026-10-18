package com.example.persiansubs

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var box: TextView? = null
    private var boxParams: WindowManager.LayoutParams? = null

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var translator: Translator? = null
    private var modelReady = false
    private var translating = false
    private var lastSource = ""
    private var pendingSource: String? = null
    private val cache = LinkedHashMap<String, String>()

    private var screenW = 0
    private var screenH = 0
    private var screenDpi = 0
    private var busy = false
    private var emptyCount = 0
    private var stopping = false
    private var styleKey = ""

    private val ticker = object : Runnable {
        override fun run() {
            if (stopping) return
            applyStyle()
            captureAndRead()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForeground()
        if (projection != null) return START_NOT_STICKY
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent.getIntExtra(EXTRA_CODE, 0)
        val data = readData(intent)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data)
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                handler.post { stopSelf() }
            }
        }
        p.registerCallback(cb, handler)
        projection = p
        projectionCallback = cb
        showOverlay()
        setupTranslator()
        setupCapture()
        running = true
        handler.postDelayed(ticker, 1000)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun readData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra<Intent>(EXTRA_DATA)
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mynisalto", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Mynisalto")
            .setContentText("در حال ترجمه زیرنویس صفحه")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }

    private fun setupTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.PERSIAN)
            .build()
        val t = Translation.getClient(options)
        translator = t
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                modelReady = true
            }
            .addOnFailureListener {
                if (!stopping) box?.text = "مدل ترجمه آماده نیست. از صفحه اصلی دانلود کنید."
            }
    }

    private fun handleText(text: String) {
        if (text == lastSource) return
        lastSource = text
        if (!modelReady) {
            box?.text = text
            return
        }
        val cached = cache[text]
        if (cached != null) {
            box?.text = cached
            return
        }
        if (translating) {
            pendingSource = text
            return
        }
        translate(text)
    }

    private fun translate(text: String) {
        val t = translator ?: return
        translating = true
        t.translate(text)
            .addOnSuccessListener { fa ->
                if (stopping) return@addOnSuccessListener
                cache[text] = fa
                if (cache.size > 200) {
                    val first = cache.keys.first()
                    cache.remove(first)
                }
                if (text == lastSource) box?.text = fa
            }
            .addOnFailureListener {
                if (stopping) return@addOnFailureListener
                if (text == lastSource) box?.text = text
            }
            .addOnCompleteListener {
                translating = false
                val p = pendingSource
                pendingSource = null
                if (p != null && !stopping) {
                    val c = cache[p]
                    if (c != null) {
                        if (p == lastSource) box?.text = c
                    } else {
                        translate(p)
                    }
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun readScreen() {
        val dm = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(dm)
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        screenDpi = dm.densityDpi
    }

    private fun defaultY(): Int {
        return if (screenH > screenW) {
            (screenW * 9 / 16 + screenH * 0.08).toInt()
        } else {
            (screenH * 0.08).toInt()
        }
    }

    private fun applyStyle() {
        val tv = box ?: return
        val prefs = Prefs(this)
        val key = "${prefs.textSize}-${prefs.bgOpacity}-${prefs.textColor}"
        if (key == styleKey) return
        styleKey = key
        val density = resources.displayMetrics.density
        tv.textSize = prefs.textSize.toFloat()
        tv.setTextColor(prefs.textColor)
        val bg = GradientDrawable()
        bg.setColor
