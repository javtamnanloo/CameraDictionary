package io.github.javtamnanloo.cameradictionary

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

// Small helpers for permissions and for opening other apps (Settings, the browser, email).

fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Opens this app's page in the phone's Settings, e.g. to allow the camera after "Don't allow". */
fun openAppSettings(context: Context) {
    startSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

/** Opens this app's notification settings. */
fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    startSafely(context, intent)
}

/**
 * Opens one of the app's own web pages in the browser. Only fixed addresses written in this
 * code are ever opened, never links found in scanned text or dictionary answers.
 * Returns false if the phone has no browser.
 */
fun openWebPage(context: Context, url: String): Boolean =
    startSafely(context, Intent(Intent.ACTION_VIEW, url.toUri()))

/**
 * Opens the phone's email app with a new message to the app's own fixed [address].
 * Nothing is sent until the user presses Send there. Returns false if there is no email app.
 */
fun writeEmail(context: Context, address: String, subject: String, body: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()) // only email apps answer "mailto:"
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
    if (body.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, body)
    return startSafely(context, intent)
}

/** Puts [text] on the clipboard, so it can be pasted into another app. */
fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Opens another app's screen. Returns false if no app on the phone can open it. */
private fun startSafely(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
