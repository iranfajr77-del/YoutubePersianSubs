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
import android.text.Html
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    @Volatile
    private var lastSource = ""
    private var pendingSource: String? = null
    private val cache = LinkedHashMap<String, String>()

    private val netExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val onlineCache = LinkedHashMap<String, String>()
    private var onlineBlockedUntil = 0L
    private var quotaToastShown = false

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

    // نقطه ورود متن جدید: بر اساس نوع ترجمه (آفلاین / آنلاین / هر دو)
    private fun handleText(text: String) {
        if (text == lastSource) return
        lastSource = text
        val mode = Prefs(this).translationMode
        val canOnline = mode != 0 &&
            System.currentTimeMillis() >= onlineBlockedUntil &&
            text.toByteArray(Charsets.UTF_8).size <= 450
        if (canOnline) {
            val oc = onlineCache[text]
            if (oc != null) {
                box?.text = oc
                return
            }
            requestOnline(text, mode == 1)
            if (mode == 2) offlineFlow(text)
            return
        }
        offlineFlow(text)
    }

    private fun requestOnline(text: String, fallbackToOffline: Boolean) {
        netExecutor.execute {
            if (text != lastSource) return@execute
            var result: String? = null
            var quota = false
            try {
                val q = URLEncoder.encode(text, "UTF-8")
                val url = URL("https://api.mymemory.translated.net/get?q=" + q + "&langpair=en%7Cfa")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 5000
                val body: String? = try {
                    val stream = if (c.responseCode in 200..299) c.inputStream else c.errorStream
                    stream?.bufferedReader()?.use { it.readText() }
                } finally {
                    c.disconnect()
                }
                if (body != null) {
                    val j = JSONObject(body)
                    val status = j.optInt("responseStatus", 0)
                    val t = j.optJSONObject("responseData")?.optString("translatedText", "") ?: ""
                    if (t.startsWith("MYMEMORY WARNING") || status == 429) {
                        quota = true
                    } else if (status == 200 && t.isNotBlank()) {
                        result = Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    }
                }
            } catch (e: Exception) {
            }
            handler.post { onlineDone(text, result, quota, fallbackToOffline) }
        }
    }

    private fun onlineDone(text: String, result: String?, quota: Boolean, fallbackToOffline: Boolean) {
        if (stopping) return
        if (!result.isNullOrEmpty()) {
            onlineCache[text] = result
            if (onlineCache.size > 200) {
                val first = onlineCache.keys.first()
                onlineCache.remove(first)
            }
            if (text == lastSource) box?.text = result
            return
        }
        if (quota) {
            onlineBlockedUntil = System.currentTimeMillis() + 3600000L
            if (!quotaToastShown) {
                quotaToastShown = true
                Toast.makeText(
                    this,
                    "سهمیه ترجمه آنلاین امروز تمام شد؛ ترجمه آفلاین ادامه می‌دهد.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            onlineBlockedUntil = System.currentTimeMillis() + 30000L
        }
        if (fallbackToOffline && text == lastSource) offlineFlow(text)
    }

    // ترجمه آفلاین (ML Kit)
    private fun offlineFlow(text: String) {
        if (!modelReady) {
            if (onlineCache[text] == null) box?.text = text
            return
        }
        val cached = cache[text]
        if (cached != null) {
            if (text == lastSource && onlineCache[text] == null) box?.text = cached
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
                if (text == lastSource && onlineCache[text] == null) box?.text = fa
            }
            .addOnFailureListener {
                if (stopping) return@addOnFailureListener
                if (text == lastSource && onlineCache[text] == null) box?.text = text
            }
            .addOnCompleteListener {
                translating = false
                val p = pendingSource
                pendingSource = null
                if (p != null && !stopping) {
                    val c = cache[p]
                    if (c != null) {
                        if (p == lastSource && onlineCache[p] == null) box?.text = c
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
        bg.setColor(Color.argb(prefs.bgOpacity * 255 / 100, 0, 0, 0))
        bg.cornerRadius = 12 * density
        tv.background = bg
    }

    private fun showOverlay() {
        readScreen()
        val prefs = Prefs(this)
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = windowManager

        val density = resources.displayMetrics.density
        val tv = TextView(this)
        tv.text = "در حال خواندن زیرنویس…"
        tv.textSize = prefs.textSize.toFloat()
        tv.setTextColor(prefs.textColor)
        tv.gravity = Gravity.CENTER
        tv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        val pad = (10 * density).toInt()
        tv.setPadding(pad, pad, pad, pad)

        val bg = GradientDrawable()
        bg.setColor(Color.argb(prefs.bgOpacity * 255 / 100, 0, 0, 0))
        bg.cornerRadius = 12 * density
        tv.background = bg
        styleKey = "${prefs.textSize}-${prefs.bgOpacity}-${prefs.textColor}"

        val params = WindowManager.LayoutParams(
            (screenW * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = defaultY()

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
        boxParams = params
    }

    private fun setupCapture() {
        val p = projection ?: return
        readScreen()
        val oldReader = reader
        val r = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        reader = r
        val vd = virtualDisplay
        if (vd == null) {
            virtualDisplay = p.createVirtualDisplay(
                "mynisalto",
                screenW,
                screenH,
                screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface,
                null,
                handler
            )
        } else {
            vd.resize(screenW, screenH, screenDpi)
            vd.surface = r.surface
        }
        oldReader?.close()
    }

    private fun overlayRect(): Rect? {
        val tv = box ?: return null
        val loc = IntArray(2)
        tv.getLocationOnScreen(loc)
        val rect = Rect(loc[0], loc[1], loc[0] + tv.width, loc[1] + tv.height)
        rect.inset(-12, -12)
        return rect
    }

    private fun captureAndRead() {
        if (busy) return
        val r = reader ?: return
        val image = try {
            r.acquireLatestImage()
        } catch (e: Exception) {
            null
        }
        if (image == null) return

        val w = image.width
        val h = image.height
        val top: Int
        val bottom: Int
        if (h > w) {
            top = (h * 0.03).toInt()
            bottom = minOf(h, w * 9 / 16 + (h * 0.06).toInt())
        } else {
            top = (h * 0.55).toInt()
            bottom = (h * 0.95).toInt()
        }
        val bandH = bottom - top
        if (bandH <= 20) {
            image.close()
            return
        }

        val crop: Bitmap
        try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowPadding = plane.rowStride - pixelStride * w
            val full = Bitmap.createBitmap(
                w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888
            )
            full.copyPixelsFromBuffer(plane.buffer)
            crop = Bitmap.createBitmap(full, 0, top, w, bandH)
            full.recycle()
        } catch (e: Exception) {
            image.close()
            return
        }
        image.close()

        busy = true
        recognizer.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { result ->
                val ov = overlayRect()
                val lines = ArrayList<String>()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        val bb = line.boundingBox
                        if (bb != null && ov != null) {
                            val rr = Rect(bb)
                            rr.offset(0, top)
                            if (Rect.intersects(rr, ov)) continue
                        }
                        lines.add(line.text)
                    }
                }
                val text = lines.joinToString(" ").trim()
                if (text.isNotEmpty()) {
                    emptyCount = 0
                    handleText(text)
                } else {
                    emptyCount++
                    if (emptyCount >= 3) {
                        lastSource = ""
                        box?.text = "…"
                    }
                }
            }
            .addOnCompleteListener {
                busy = false
                crop.recycle()
            }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (projection == null || stopping) return
        handler.postDelayed({
            if (stopping) return@postDelayed
            setupCapture()
            val p = boxParams
            val b = box
            if (p != null && b != null) {
                p.width = (screenW * 0.9).toInt()
                p.y = defaultY()
                wm?.updateViewLayout(b, p)
            }
        }, 400)
    }

    override fun onDestroy() {
        stopping = true
        running = false
        handler.removeCallbacksAndMessages(null)
        netExecutor.shutdownNow()
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
        }
        virtualDisplay = null
        reader?.close()
        reader = null
        val p = projection
        val cb = projectionCallback
        if (p != null) {
            if (cb != null) p.unregisterCallback(cb)
            try {
                p.stop()
            } catch (e: Exception) {
            }
        }
        projection = null
        recognizer.close()
        translator?.close()
        translator = null
        try {
            box?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        box = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.persiansubs.STOP"
        const val CHANNEL_ID = "overlay"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"

        @Volatile
        var running = false
    }
}
