package ru.disciplinegym.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Checks GitHub for a newer build and installs it in place: data stays, signature is the same.
class Updater(private val act: Activity) {

    private val api = "https://api.github.com/repos/biaqtaa-hub/discipline-gym/releases/latest"
    private var pendingUrl: String? = null
    private var pendingVersion: String? = null
    private var downloadId = -1L
    private var showing = false

    @Suppress("DEPRECATION")
    fun currentCode(): Long {
        val info = act.packageManager.getPackageInfo(act.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    fun currentName(): String = act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: ""

    /** quiet = true: say nothing when there is no update (used on app start). */
    fun check(quiet: Boolean) {
        Thread {
            try {
                val c = URL(api).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "DisciplineGym-App")
                val body = c.inputStream.bufferedReader().use { it.readText() }
                c.disconnect()
                val o = JSONObject(body)
                val tag = o.optString("tag_name")             // v1.0.N
                val code = tag.substringAfterLast('.').toLongOrNull() ?: 0L
                val assets = o.optJSONArray("assets")
                var url: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk")) {
                            url = a.optString("browser_download_url"); break
                        }
                    }
                }
                act.runOnUiThread {
                    if (url != null && code > currentCode()) {
                        pendingUrl = url
                        pendingVersion = tag.removePrefix("v")
                        askToUpdate()
                    } else if (!quiet) {
                        Toast.makeText(act, "У тебя последняя версия", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (!quiet) act.runOnUiThread {
                    Toast.makeText(act, "Не получилось проверить обновления. Проверь интернет.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun askToUpdate() {
        if (showing || act.isFinishing) return
        showing = true
        AlertDialog.Builder(act)
            .setTitle("Нужно обновить приложение")
            .setMessage("Вышла новая версия Discipline Gym ($pendingVersion). Обнови, чтобы получить исправления и новое. Твои тренировки и данные сохранятся.")
            .setPositiveButton("Обновить") { _, _ -> showing = false; startUpdate() }
            .setNegativeButton("Позже") { _, _ -> showing = false }
            .setOnCancelListener { showing = false }
            .show()
    }

    private fun startUpdate() {
        val url = pendingUrl ?: return
        // Android asks once to allow this app to install updates.
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, "Разреши установку обновлений для Discipline Gym и вернись назад", Toast.LENGTH_LONG).show()
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName)))
                waitingPermission = true
            } catch (e: Exception) {
                openInBrowser(url)
            }
            return
        }
        download(url)
    }

    private var waitingPermission = false

    /** Called from onResume: continue after the user allowed installs. */
    fun onResume() {
        if (waitingPermission) {
            waitingPermission = false
            if (Build.VERSION.SDK_INT < 26 || act.packageManager.canRequestPackageInstalls()) {
                pendingUrl?.let { download(it) }
            } else askToUpdate()
        }
    }

    private fun download(url: String) {
        try {
            val dir = act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            File(dir, "update.apk").delete()
            val dm = act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("Discipline Gym $pendingVersion")
                .setDescription("Скачиваю обновление")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(act, Environment.DIRECTORY_DOWNLOADS, "update.apk")
            downloadId = dm.enqueue(req)
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(done, filter, Context.RECEIVER_EXPORTED)
            else act.registerReceiver(done, filter)
            Toast.makeText(act, "Скачиваю обновление…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            openInBrowser(url)
        }
    }

    private val done = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != downloadId) return
            try { act.unregisterReceiver(this) } catch (e: Exception) {}
            val dm = act.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = dm.getUriForDownloadedFile(id)
            if (uri == null) {
                pendingUrl?.let { openInBrowser(it) }
                return
            }
            try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(uri, "application/vnd.android.package-archive")
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                act.startActivity(i)
            } catch (e: Exception) {
                pendingUrl?.let { openInBrowser(it) }
            }
        }
    }

    private fun openInBrowser(url: String) {
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
        }
    }
}
