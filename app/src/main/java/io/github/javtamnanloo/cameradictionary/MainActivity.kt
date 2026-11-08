package io.github.javtamnanloo.cameradictionary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.javtamnanloo.cameradictionary.ui.theme.CameraDictionaryTheme
import kotlinx.coroutines.launch

/** The app's only screen container. It hosts four tabs: Scan, Review, My words and More. */
class MainActivity : ComponentActivity() {

    private lateinit var speaker: Speaker

    /** Becomes true when the reminder notification asks to open the Review tab. */
    private val openReviewRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        speaker = Speaker(this)
        openReviewRequest.value = wantsReview(intent)
        setContent {
            CameraDictionaryTheme {
                CameraDictionaryApp(speaker, openReviewRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (wantsReview(intent)) openReviewRequest.value = true
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    /**
     * Any app can start this screen, so the only thing read from the request is a yes/no
     * "open the Review tab". A malformed request is simply ignored.
     */
    private fun wantsReview(intent: Intent?): Boolean =
        try {
            intent?.getBooleanExtra(ReviewReminder.EXTRA_OPEN_REVIEW, false) == true
        } catch (e: Exception) {
            false
        }
}

private enum class Tab(val label: String, val icon: Int) {
    Scan("Scan", R.drawable.ic_camera),
    Review("Review", R.drawable.ic_review),
    Words("My words", R.drawable.ic_list),
    More("More", R.drawable.ic_more),
}

@Composable
private fun CameraDictionaryApp(speaker: Speaker, openReviewRequest: MutableState<Boolean>) {
    var tab by rememberSaveable { mutableStateOf(Tab.Scan) }
    // The screens' logic classes are created here directly (not found by reflection), so the
    // release build's code shrinker can never remove what they need. They survive rotation.
    val app = LocalContext.current.applicationContext as App
    val wordsViewModel = viewModel { WordsViewModel(app) }
    val words by wordsViewModel.words.collectAsStateWithLifecycle()
    val today = rememberToday()
    val dueCount = words.count { Leitner.isDue(it, today) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val speak: (String) -> Unit = { word ->
        if (!speaker.speak(word)) {
            scope.launch { snackbar.show("No English voice found. You can add one in Settings › Text-to-speech.") }
        }
    }

    LaunchedEffect(openReviewRequest.value) {
        if (openReviewRequest.value) {
            tab = Tab.Review
            openReviewRequest.value = false
        }
    }
    // The back button returns to Scan before it leaves the app.
    BackHandler(enabled = tab != Tab.Scan) { tab = Tab.Scan }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                for (item in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            BadgedBox(badge = {
                                if (item == Tab.Review && dueCount > 0) Badge { Text(if (dueCount > 99) "99+" else "$dueCount") }
                            }) {
                                Icon(painterResource(item.icon), contentDescription = null)
                            }
                        },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                Tab.Scan -> ScanScreen(viewModel { ScanViewModel(app) }, speak, snackbar)
                Tab.Review -> ReviewScreen(wordsViewModel, words, today, speak, snackbar)
                Tab.Words -> WordsScreen(wordsViewModel, words, today, speak, snackbar)
                Tab.More -> MoreScreen(snackbar)
            }
        }
    }
}
