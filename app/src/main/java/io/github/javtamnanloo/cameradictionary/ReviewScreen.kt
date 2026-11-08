package io.github.javtamnanloo.cameradictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The Leitner review: one due word at a time. Think of its meaning, tap "Show meaning",
 * then say honestly whether you knew it.
 */
@Composable
fun ReviewScreen(
    viewModel: WordsViewModel,
    words: List<SavedWord>,
    today: Long,
    speak: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    // The due words, in a shuffled order that stays the same all day.
    val due = remember(words, today) {
        words.filter { Leitner.isDue(it, today) }.sortedBy { (it.word + today).hashCode() }
    }
    val current = due.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        ScreenTitle(
            "Review",
            when {
                words.isEmpty() -> "Your Leitner box is empty"
                due.isEmpty() -> "All done for today"
                due.size == 1 -> "1 word to review"
                else -> "${due.size} words to review"
            },
        )
        if (current == null) {
            AllDone(words, today, Modifier.weight(1f))
        } else {
            key(current.word) { // a fresh card for every word
                ReviewCard(
                    word = current,
                    speak = speak,
                    onAnswer = { knewIt ->
                        val updated = viewModel.answer(current, knewIt)
                        scope.launch {
                            val message = if (knewIt) {
                                "Moved to box ${updated.box}. Next review in ${Leitner.intervalDays(updated.box)} days."
                            } else {
                                "Back to box 1. You'll see it again tomorrow."
                            }
                            if (snackbar.show(message, action = "Undo")) viewModel.restore(current)
                        }
                    },
                    onRemove = {
                        viewModel.delete(current)
                        scope.launch {
                            if (snackbar.show("Removed “${current.word}” from your Leitner box.", action = "Undo")) viewModel.restore(current)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(
    word: SavedWord,
    speak: (String) -> Unit,
    onAnswer: (knewIt: Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val entry = remember(word.entryJson) { word.entry() }
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        ElevatedCard(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Box ${word.box} of ${Leitner.LAST_BOX}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                WordHeader(entry.word.ifEmpty { word.word }, entry.phonetic, onSpeak = { speak(word.word) }) {
                    IconButton(onClick = onRemove) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove from Leitner box")
                    }
                }
                ContextQuote(word.context, Modifier.padding(top = 12.dp))
                if (revealed) {
                    DefinitionView(entry, Modifier.padding(top = 8.dp))
                } else {
                    Text(
                        "Do you know what it means? Think of the meaning, then check.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!revealed) {
            Button(
                onClick = { revealed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) { Text("Show meaning", style = MaterialTheme.typography.titleMedium) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Didn't know")
                }
                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("I knew it")
                }
            }
        }
    }
}

@Composable
private fun AllDone(words: List<SavedWord>, today: Long, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            painterResource(R.drawable.ic_school),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        if (words.isEmpty()) {
            Text("Nothing to review yet", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "When you look up a word on the Scan tab, tap the ☆ to add it to your Leitner box. " +
                    "You'll review it tomorrow, then after 3, 9, 27… days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            val nextDay = words.minOf { it.dueDay }
            val nextCount = words.count { it.dueDay == nextDay }
            Text("All done for today!", style = MaterialTheme.typography.titleLarge)
            Text(
                "Next review ${dueText(nextDay, today).removePrefix("due ")}: $nextCount ${if (nextCount == 1) "word" else "words"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            BoxOverview(words, Modifier.padding(top = 24.dp, bottom = 16.dp))
        }
    }
}

/** How many words are in each of the six boxes. */
@Composable
private fun BoxOverview(words: List<SavedWord>, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Your Leitner box", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (box in Leitner.FIRST_BOX..Leitner.LAST_BOX) {
                val count = words.count { it.box == box }
                Surface(
                    color = if (count > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Box $box", style = MaterialTheme.typography.labelSmall)
                        Text("${Leitner.intervalDays(box)}d", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
