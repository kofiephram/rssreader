package com.example.rssreader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskText = intent.getStringExtra("taskText") ?: "Task due"
        val taskId = intent.getIntExtra("taskId", 0)

        // NEW: Check if we have permission before sending notification
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val notification = NotificationCompat.Builder(context, "tasks")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Getready Reminder")
                .setContentText(taskText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(taskId, notification)
        }
    }
}