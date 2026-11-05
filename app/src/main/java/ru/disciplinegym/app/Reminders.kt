package ru.disciplinegym.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray

// Reminders the app page hands over: each one becomes an alarm on the phone.
object Reminders {
    private const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val MAX = 60

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL, "Напоминания", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Тренировки, вода, еда и сон"
            ch.enableVibration(true)
            nm.createNotificationChannel(ch)
        }
    }

    fun save(ctx: Context, json: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("list", json).apply()
    }

    fun saved(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("list", null)

    private fun pending(ctx: Context, slot: Int, title: String, text: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        i.putExtra("title", title)
        i.putExtra("text", text)
        i.putExtra("slot", slot)
        return PendingIntent.getBroadcast(
            ctx, 1000 + slot, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAll(ctx: Context, json: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getInt("count", 0)
        for (s in 0 until old) am.cancel(pending(ctx, s, "", ""))

        var count = 0
        try {
            val arr = JSONArray(json)
            val now = System.currentTimeMillis()
            for (i in 0 until arr.length()) {
                if (count >= MAX) break
                val o = arr.getJSONObject(i)
                val at = o.optLong("at", 0L)
                if (at <= now) continue
                val p = pending(ctx, count, o.optString("title"), o.optString("text"))
                val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                count++
            }
        } catch (e: Exception) {
        }
        prefs.edit().putInt("count", count).apply()
    }

    @Suppress("DEPRECATION")
    private fun builder(ctx: Context): Notification.Builder {
        if (Build.VERSION.SDK_INT >= 26) return Notification.Builder(ctx, CHANNEL)
        val b = Notification.Builder(ctx)
        b.setPriority(Notification.PRIORITY_HIGH)
        b.setDefaults(Notification.DEFAULT_ALL)
        return b
    }

    fun show(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java)
        open.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pi = PendingIntent.getActivity(
            ctx, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = builder(ctx)
        b.setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, b.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Discipline Gym"
        val text = intent.getStringExtra("text") ?: ""
        val slot = intent.getIntExtra("slot", 0)
        Reminders.show(ctx, 100 + slot, title, text)
    }
}

// After a phone restart alarms are wiped, so set them again from the saved list.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val json = Reminders.saved(ctx) ?: return
        Reminders.scheduleAll(ctx, json)
    }
}
