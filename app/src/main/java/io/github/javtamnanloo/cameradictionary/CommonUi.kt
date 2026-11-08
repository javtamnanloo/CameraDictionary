package io.github.javtamnanloo.cameradictionary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.javtamnanloo.cameradictionary.ui.theme.StarGold

// Small building blocks shared by the Scan, Review and My words screens.

/** The big word at the top of a definition, with its pronunciation and a "say it" button. */
@Composable
fun WordHeader(
    word: String,
    phonetic: String,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (phonetic.isNotEmpty()) {
                Text(phonetic, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onSpeak) {
            Icon(painterResource(R.drawable.ic_volume), contentDescription = "Pronounce $word")
        }
        actions()
    }
}

/** The star: filled and gold when the word is in your Leitner box. */
@Composable
fun StarButton(starred: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painterResource(if (starred) R.drawable.ic_star else R.drawable.ic_star_outline),
            contentDescription = if (starred) "Remove from Leitner box" else "Add to Leitner box",
            tint = if (starred) StarGold else LocalContentColor.current,
        )
    }
}

/** Definitions grouped by part of speech, with examples, similar words, and the source. */
@Composable
fun DefinitionView(entry: DictionaryEntry, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(modifier) {
        if (entry.note.isNotEmpty()) {
            Text(entry.note, style = type.bodyMedium, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        for (meaning in entry.meanings) {
            if (meaning.partOfSpeech.isNotEmpty()) {
                Text(
                    meaning.partOfSpeech,
                    style = type.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = colors.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            meaning.definitions.forEachIndexed { index, definition ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${index + 1}.", style = type.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.width(28.dp))
                    Column {
                        Text(definition.text, style = type.bodyLarge)
                        if (definition.example.isNotEmpty()) {
                            val example = definition.example
                            val alreadyQuoted = example.first() in "\"“'‘"
                            Text(
                                if (alreadyQuoted) example else "“$example”",
                                style = type.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            if (meaning.synonyms.isNotEmpty()) {
                Text(
                    "Similar: " + meaning.synonyms.joinToString(", "),
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 4.dp),
                )
            }
        }
        Text(
            "Source: Wiktionary (${entry.license.ifEmpty { "CC BY-SA" }})",
            style = type.labelSmall,
            color = colors.outline,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
    }
}

/** The line of text where you found a word, shown on saved words. */
@Composable
fun ContextQuote(context: String, modifier: Modifier = Modifier) {
    if (context.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Where you found it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "“$context”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** A screen's title with a short line below it. */
@Composable
fun ScreenTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "due today", "due tomorrow", "due in 9 days". */
fun dueText(dueDay: Long, today: Long): String = when (val days = dueDay - today) {
    in Long.MIN_VALUE..0L -> "due today"
    1L -> "due tomorrow"
    else -> "due in $days days"
}

/**
 * Shows a short message at the bottom of the screen, replacing any message already there.
 * A message with a button (e.g. "Undo") stays longer, so there's time to tap it.
 * Returns true if the user tapped the [action] button.
 */
suspend fun SnackbarHostState.show(message: String, action: String? = null): Boolean {
    currentSnackbarData?.dismiss()
    val duration = if (action == null) SnackbarDuration.Short else SnackbarDuration.Long
    return showSnackbar(message, actionLabel = action, duration = duration) == SnackbarResult.ActionPerformed
}

/** Today's day number. It is refreshed whenever the app comes back on screen, so it changes after midnight. */
@Composable
fun rememberToday(): Long {
    var today by remember { mutableLongStateOf(Leitner.today()) }
    LifecycleResumeEffect(Unit) {
        today = Leitner.today()
        onPauseOrDispose { }
    }
    return today
}
