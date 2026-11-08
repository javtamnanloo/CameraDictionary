package io.github.javtamnanloo.cameradictionary

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The "time to review" notification. About once a day, at the hour the user picked on the
 * More tab, Android runs [ReviewReminderWorker], which posts a notification only if words
 * are due.
 */
object ReviewReminder {

    const val EXTRA_OPEN_REVIEW = "open_review"
    private const val DEFAULT_HOUR = 18 // 6 pm, phone's local time
    private const val CHANNEL_ID = "review_reminders"
    private const val NOTIFICATION_ID = 1
    private const val WORK_NAME = "daily_review_reminder"
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "daily_reminder_enabled"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_ASKED = "asked_notification_permission"

    /** Called when the app starts: creates the notification channel and keeps the schedule in sync. */
    fun setUp(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Review reminders")
            .setDescription("A daily reminder when words in your Leitner box are due")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
        if (isEnabled(context)) schedule(context, ExistingPeriodicWorkPolicy.KEEP) else cancel(context)
    }

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) schedule(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) else cancel(context)
    }

    /** The hour of the day (0–23) when the reminder comes. */
    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)

    fun setHour(context: Context, hour: Int) {
        prefs(context).edit { putInt(KEY_HOUR, hour.coerceIn(0, 23)) }
        if (isEnabled(context)) schedule(context, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE)
    }

    /** False if the user blocked notifications (Android 13+ also needs the user's permission). */
    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || isGranted(context, Manifest.permission.POST_NOTIFICATIONS))

    /** True only the first time it matters, so the user is asked once and never nagged. */
    fun shouldAskPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && isEnabled(context) &&
            !isGranted(context, Manifest.permission.POST_NOTIFICATIONS) && !prefs(context).getBoolean(KEY_ASKED, false)

    fun markPermissionAsked(context: Context) = prefs(context).edit { putBoolean(KEY_ASKED, true) }

    @SuppressLint("MissingPermission") // checked by canNotify() on the first line
    fun showNotification(context: Context, dueCount: Int) {
        if (!canNotify(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_REVIEW, true)
        // FLAG_IMMUTABLE: no other app can change what this notification opens.
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (dueCount == 1) "1 word is waiting in your Leitner box." else "$dueCount words are waiting in your Leitner box."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to review your words")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The user turned notifications off a moment ago; nothing to do.
        }
    }

    private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(millisUntil(hour(context)), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }

    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Milliseconds from now until the next time the clock shows [hour]:00. */
    private fun millisUntil(hour: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis - now.timeInMillis
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Runs about once a day in the background: counts due words and, if any, shows the reminder. */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as App
        val due = app.database.savedWords().countDue(Leitner.today())
        if (due > 0) ReviewReminder.showNotification(applicationContext, due)
        return Result.success()
    }
}
