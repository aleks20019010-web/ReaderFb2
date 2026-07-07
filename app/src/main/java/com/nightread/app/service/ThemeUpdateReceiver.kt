package com.nightread.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nightread.app.data.ThemeManager
import java.util.Calendar

class ThemeUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ThemeUpdateReceiver", "Alarm received! Updating theme based on time.")
        ThemeManager.applyTheme(context)
        scheduleNextThemeAlarm(context)
    }

    companion object {
        private const val REQUEST_CODE = 9081

        fun scheduleNextThemeAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ThemeUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val now = Calendar.getInstance()
            
            // Time option 1: 6:00 today
            val alarm6 = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Time option 2: 21:00 today
            val alarm21 = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetCalendar = when {
                now.before(alarm6) -> alarm6
                now.before(alarm21) -> alarm21
                else -> {
                    // It's past 21:00 today, so the next alarm is at 6:00 tomorrow
                    alarm6.add(Calendar.DAY_OF_YEAR, 1)
                    alarm6
                }
            }

            Log.d("ThemeUpdateReceiver", "Scheduling next theme alarm for: ${targetCalendar.time}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    targetCalendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    targetCalendar.timeInMillis,
                    pendingIntent
                )
            }
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ThemeUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("ThemeUpdateReceiver", "Theme update alarm cancelled.")
        }
    }
}
