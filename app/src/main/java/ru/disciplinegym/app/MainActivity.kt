package ru.disciplinegym.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0D1311")
        window.navigationBarColor = Color.parseColor("#0D1311")

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0D1311"))
        setContentView(web)

        // Serve the app page from inside the APK over a local https address,
        // so saved data (localStorage) works like on a normal site.
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: Exception) {
                }
                return true
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("ru", "RU"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        Reminders.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.__onBack && window.__onBack()) ? '1' : '0'") { result ->
            if (result != "\"1\"") finish()
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun vibrateNow(timings: LongArray) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(timings, -1))
        } else {
            v.vibrate(timings, -1)
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun isNative(): Boolean = true

        @JavascriptInterface
        fun speak(text: String): Boolean {
            if (!ttsReady) return false
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "say")
            return true
        }

        @JavascriptInterface
        fun keepAwake(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @JavascriptInterface
        fun vibrate(pattern: String) {
            try {
                val parts = pattern.split(",").mapNotNull { it.trim().toLongOrNull() }
                if (parts.isEmpty()) return
                val timings = LongArray(parts.size + 1)
                timings[0] = 0
                for (i in parts.indices) timings[i + 1] = parts[i]
                vibrateNow(timings)
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun schedule(json: String) {
            Reminders.save(this@MainActivity, json)
            Reminders.scheduleAll(this@MainActivity, json)
        }

        @JavascriptInterface
        fun testNotify() {
            Reminders.show(this@MainActivity, 999, "Discipline Gym", "Уведомления работают. Так будут приходить напоминания.")
        }

        @JavascriptInterface
        fun copy(text: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Discipline Gym", text))
            }
        }

        @JavascriptInterface
        fun openBatterySettings() {
            runOnUiThread {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    i.data = Uri.parse("package:$packageName")
                    startActivity(i)
                } catch (e: Exception) {
                    try {
                        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        i.data = Uri.parse("package:$packageName")
                        startActivity(i)
                    } catch (e2: Exception) {
                    }
                }
            }
        }
    }
}
