package io.github.javtamnanloo.cameradictionary

import android.Manifest
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

private const val PRIVACY_POLICY_URL = "https://github.com/javtamnanloo/CameraDictionary/blob/main/PRIVACY.md"
private const val CONTACT_EMAIL = "jav.tamnanloo@gmail.com"
private const val COFFEE_URL = "https://buymeacoffee.com/javtamnanloo"

/**
 * Google Play has made other apps remove donation links, so the version uploaded there may need
 * this set to false, which hides "Buy me a coffee". See docs/PUBLISHING.md.
 */
private const val SHOW_COFFEE_BUTTON = true

/** The More tab: the reminder, contact and support, and information about the app. */
@Composable
fun MoreScreen(snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Opens the email app. Without one, the address is copied so it can be pasted anywhere.
    val email: (subject: String, body: String) -> Unit = { subject, body ->
        if (!writeEmail(context, CONTACT_EMAIL, subject, body)) {
            copyToClipboard(context, "Email address", CONTACT_EMAIL)
            scope.launch { snackbar.show("No email app found. The address is copied: $CONTACT_EMAIL") }
        }
    }
    val openPage: (String) -> Unit = { url ->
        if (!openWebPage(context, url)) scope.launch { snackbar.show("No web browser found.") }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle("More", "Reminder, contact, and about the app")

        Section("Notifications") {
            ReminderSetting()
        }

        Section("Contact & support") {
            LinkRow(R.drawable.ic_mail, "Email me", CONTACT_EMAIL) { email("Camera Dictionary", "") }
            LinkRow(R.drawable.ic_lightbulb, "Send a suggestion", "An idea to make the app better") {
                email("Camera Dictionary: suggestion", "")
            }
            LinkRow(R.drawable.ic_bug, "Report a problem", "Something didn't work as expected") {
                email("Camera Dictionary: problem report", problemReport())
            }
            if (SHOW_COFFEE_BUTTON) {
                LinkRow(R.drawable.ic_coffee, "Buy me a coffee", "Support the app at buymeacoffee.com") { openPage(COFFEE_URL) }
            }
        }

        Section("About") {
            Text("Camera Dictionary", style = MaterialTheme.typography.titleMedium)
            Text(
                "Point your camera at a word to get its English meaning, then remember it with a Leitner box.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            LinkRow(
                R.drawable.ic_privacy,
                "Privacy policy",
                "Photos stay on your phone. Only the word you look up is sent.",
            ) { openPage(PRIVACY_POLICY_URL) }
            Text("Credits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Definitions: Wiktionary contributors (CC BY-SA), via the Free Dictionary API and Wiktionary. " +
                    "Text recognition: Google ML Kit. Icons: Material Symbols (Apache License 2.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * The start of a problem report email: two questions, and the details that help find the cause.
 * The user sees all of it in their email app and can change or delete anything before sending.
 */
private fun problemReport(): String = """
    What happened?


    What did you expect to happen?


    ---
    App: Camera Dictionary ${BuildConfig.VERSION_NAME}
    Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
    Phone: ${Build.MANUFACTURER} ${Build.MODEL}
""".trimIndent()

/** A titled card that groups related items. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** One tappable row with an icon, a title and a short explanation. */
@Composable
private fun LinkRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The reminder switch and, when it's on, a scrollable row of hours to choose the time. */
@Composable
private fun ReminderSetting() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReviewReminder.isEnabled(context)) }
    var hour by remember { mutableIntStateOf(ReviewReminder.hour(context)) }
    var canNotify by remember { mutableStateOf(ReviewReminder.canNotify(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = ReviewReminder.canNotify(context)
    }
    LifecycleResumeEffect(Unit) {
        canNotify = ReviewReminder.canNotify(context) // the user may have changed it in Settings
        onPauseOrDispose { }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Reminder", style = MaterialTheme.typography.bodyLarge, modifier = Modifier
            .weight(1f)
            .padding(start = 16.dp))
        Switch(
            checked = enabled && canNotify,
            onCheckedChange = { turnOn ->
                ReviewReminder.setEnabled(context, turnOn)
                enabled = turnOn
                if (turnOn && !canNotify) {
                    if (Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openNotificationSettings(context)
                    }
                }
            },
        )
    }
    if (enabled && canNotify) {
        HourPicker(hour) { newHour ->
            hour = newHour
            ReviewReminder.setHour(context, newHour)
        }
    }
    if (enabled && !canNotify) {
        Text(
            "Notifications are turned off for this app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = { openNotificationSettings(context) }) { Text("Open notification settings") }
    }
}

/** The 24 hours of the day as a row you can scroll sideways; tap one to choose it. */
@Composable
private fun HourPicker(selected: Int, onSelect: (Int) -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp),
    ) {
        items(24) { hour ->
            FilterChip(
                selected = hour == selected,
                onClick = { onSelect(hour) },
                label = { Text(hourLabel(context, hour)) },
            )
        }
    }
}

/** "6 PM" or "18:00", following the phone's 12- or 24-hour clock setting. */
private fun hourLabel(context: Context, hour: Int): String = when {
    DateFormat.is24HourFormat(context) -> "%02d:00".format(hour)
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}
