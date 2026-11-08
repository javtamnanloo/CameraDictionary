package io.github.javtamnanloo.cameradictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Every word in your Leitner box. Tap one to see it in full; delete one, or all of them. */
@Composable
fun WordsScreen(
    viewModel: WordsViewModel,
    words: List<SavedWord>,
    today: Long,
    speak: (String) -> Unit,
    snackbar: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle(
                "My words",
                when (words.size) {
                    0 -> "Your Leitner box is empty"
                    1 -> "1 word in your Leitner box"
                    else -> "${words.size} words in your Leitner box"
                },
                Modifier.weight(1f),
            )
            if (words.isNotEmpty()) {
                TextButton(
                    onClick = { confirmDeleteAll = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete all")
                }
            }
        }

        if (words.isEmpty()) {
            EmptyWords()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 16.dp),
            ) {
                items(words, key = { it.word }) { word ->
                    WordRow(
                        word = word,
                        today = today,
                        expanded = expanded == word.word,
                        onClick = { expanded = if (expanded == word.word) null else word.word },
                        onSpeak = { speak(word.word) },
                        onDelete = {
                            viewModel.delete(word)
                            scope.launch {
                                if (snackbar.show("Deleted “${word.word}”.", action = "Undo")) viewModel.restore(word)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            icon = { Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null) },
            title = { Text(if (words.size == 1) "Delete your only word?" else "Delete all ${words.size} words?") },
            text = { Text("This removes every word and its review progress from your Leitner box. It can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        confirmDeleteAll = false
                        scope.launch { snackbar.show("All words deleted.") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WordRow(
    word: SavedWord,
    today: Long,
    expanded: Boolean,
    onClick: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
) {
    val entry = remember(word.entryJson) { word.entry() }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.word.ifEmpty { word.word }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!expanded) {
                    Text(
                        entry.meanings.firstOrNull()?.definitions?.firstOrNull()?.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Box ${word.box} · ${dueText(word.dueDay, today)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                IconButton(onClick = onSpeak) {
                    Icon(painterResource(R.drawable.ic_volume), contentDescription = "Pronounce ${word.word}")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete ${word.word}")
            }
        }
        if (expanded) {
            if (entry.phonetic.isNotEmpty()) {
                Text(entry.phonetic, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ContextQuote(word.context, Modifier.padding(top = 8.dp))
            DefinitionView(entry)
        }
    }
}

@Composable
private fun EmptyWords() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_star_outline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 24.dp)
                .size(64.dp),
        )
        Text("No saved words yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Look up a word on the Scan tab and tap the ☆ to save it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
