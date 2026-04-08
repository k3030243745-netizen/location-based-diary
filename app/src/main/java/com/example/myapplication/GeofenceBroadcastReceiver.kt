package com.example.myapplication

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val GEOFENCE_CHANNEL_ID = "location_diary_geofence"
private const val GEOFENCE_CHANNEL_NAME = "Location Diary Reminders"
private const val TAG_GEOFENCE = "GeofenceReceiver"

// ===== Cooldown config =====
private const val GEOFENCE_PREFS = "geofence_prefs"
private const val NOTIFICATION_COOLDOWN_MS = 10 * 60 * 1000L // 10 minutes

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG_GEOFENCE, "onReceive called")

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.e(TAG_GEOFENCE, "GeofencingEvent is null")
            return
        }
        if (event.hasError()) {
            Log.e(TAG_GEOFENCE, "Geofence errorCode=${event.errorCode}")
            return
        }

        Log.d(TAG_GEOFENCE, "transition=${event.geofenceTransition}")

        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        Log.d(TAG_GEOFENCE, "ids=$ids")

        if (ids.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.get(context).diaryDao()
            ensureChannel(context)

            val prefs = context.getSharedPreferences(GEOFENCE_PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            ids.forEach { requestId ->
                val entryId = requestId.toLongOrNull() ?: return@forEach
                val entry = dao.getById(entryId) ?: return@forEach

                // ===== Cooldown check =====
                val lastNotifyKey = "last_notify_$entryId"
                val lastNotifyAt = prefs.getLong(lastNotifyKey, 0L)
                val elapsed = now - lastNotifyAt

                if (elapsed in 1 until NOTIFICATION_COOLDOWN_MS) {
                    Log.d(
                        TAG_GEOFENCE,
                        "Cooldown active for entryId=$entryId, skip notify. elapsed=${elapsed}ms"
                    )
                    return@forEach
                }

                val notif = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_map)
                    .setContentTitle(entry.title)
                    .setContentText(entry.note.ifBlank { "You entered the saved area." })
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()

                // Android 13+ 必须检查通知权限
                if (Build.VERSION.SDK_INT >= 33) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!granted) {
                        Log.w(TAG_GEOFENCE, "POST_NOTIFICATIONS not granted, skip notify.")
                        return@forEach
                    }
                }

                try {
                    NotificationManagerCompat.from(context).notify(entryId.toInt(), notif)

                    // 只有成功通知后才更新时间
                    prefs.edit().putLong(lastNotifyKey, now).apply()

                    Log.d(TAG_GEOFENCE, "Notified entryId=$entryId title=${entry.title}")
                } catch (se: SecurityException) {
                    Log.e(TAG_GEOFENCE, "Notify failed due to SecurityException", se)
                }
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val existing = nm.getNotificationChannel(GEOFENCE_CHANNEL_ID)

        if (existing != null) return

        nm.createNotificationChannel(
            NotificationChannel(
                GEOFENCE_CHANNEL_ID,
                GEOFENCE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }
}