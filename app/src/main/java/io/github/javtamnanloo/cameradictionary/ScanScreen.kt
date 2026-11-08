package io.github.javtamnanloo.cameradictionary

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.TorchState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.javtamnanloo.cameradictionary.ui.theme.SelectedWordHighlight
import io.github.javtamnanloo.cameradictionary.ui.theme.WordHighlight
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The main screen: the camera on top (or on the left in landscape), the definition below.
 * Press Read to freeze the picture; the word under the + is looked up, and you can tap
 * any other highlighted word. In portrait, a long definition scrolls the picture away.
 */
@Composable
fun ScanScreen(viewModel: ScanViewModel, speak: (String) -> Unit, snackbar: SnackbarHostState) {
    val state = viewModel.state
    val savedWords by viewModel.savedWords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var typing by rememberSaveable { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val addedMessage = "Added to your Leitner box. First review: tomorrow."
    // The confirmation is shown after the permission dialog closes, so it isn't hidden behind it.
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { snackbar.show(addedMessage) }
    }

    BackHandler(enabled = state.photo != null) { viewModel.backToCamera() }

    val onRead: () -> Unit = {
        val picture = previewView?.bitmap
        if (picture == null) {
            scope.launch { snackbar.show("The camera is still starting. Try again in a moment.") }
        } else {
            viewModel.read(picture)
        }
    }
    val onStar: () -> Unit = {
        scope.launch {
            when (viewModel.toggleStar()) {
                true -> if (ReviewReminder.shouldAskPermission(context)) {
                    // The first saved word is the right moment to ask about reminder notifications.
                    ReviewReminder.markPermissionAsked(context)
                    askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    snackbar.show(addedMessage)
                }
                false -> snackbar.show("Removed from your Leitner box.")
                null -> Unit
            }
        }
    }

    val camera: @Composable (Modifier) -> Unit = { modifier ->
        CameraBox(state, onPreviewView = { previewView = it }, onTapPhoto = viewModel::selectAt, modifier = modifier)
    }
    val definition: @Composable (modifier: Modifier, cameraHeight: Dp, bottomSpace: Dp) -> Unit = { modifier, cameraHeight, bottomSpace ->
        DefinitionPanel(state, savedWords, speak, onStar, onType = { typing = true }, onRetry = viewModel::retry, camera, cameraHeight, bottomSpace, modifier)
    }
    val buttons: @Composable (Modifier) -> Unit = { modifier ->
        ScanButtons(
            state = state,
            readEnabled = previewView != null,
            onRead = onRead,
            onPrevious = viewModel::selectPrevious,
            onNext = viewModel::selectNext,
            onNewScan = viewModel::backToCamera,
            modifier = modifier,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // Landscape: the camera and its buttons on the left, the definition on the right.
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    camera(
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                    buttons(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
                definition(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    0.dp, // the camera is on the left, not on the definition's page
                    16.dp,
                )
            }
        } else {
            // Portrait: a big camera on top, the definition below, and the buttons near your thumb.
            // The camera is the top of the definition's page, so a long definition scrolls it away.
            val cameraHeight = maxHeight * 0.6f
            Box(Modifier.fillMaxSize()) {
                definition(
                    Modifier.fillMaxSize(),
                    cameraHeight,
                    96.dp, // room for the floating buttons, so they never cover the last lines
                )
                buttons(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }

    if (typing) {
        TypeWordDialog(
            initial = state.lookup?.word.orEmpty(),
            onDismiss = { typing = false },
            onLookUp = viewModel::lookUpTyped,
        )
    }
}

// --- Camera and photo ------------------------------------------------------------------------

/** The rounded frame showing either the live camera or the frozen photo. */
@Composable
private fun CameraBox(
    state: ScanState,
    onPreviewView: (PreviewView?) -> Unit,
    onTapPhoto: (Float, Float, Float) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
    ) {
        val photo = state.photo
        if (photo == null) {
            LiveCamera(onPreviewView)
        } else {
            FrozenPhoto(photo, state.words, state.selected, onTap = onTapPhoto)
            if (state.isReading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}

/**
 * The floating buttons near the bottom of the screen, where your thumb rests. They change
 * with what you're doing: a big Read button for the live camera, or ‹ New scan › for a photo.
 */
@Composable
private fun ScanButtons(
    state: ScanState,
    readEnabled: Boolean,
    onRead: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onNewScan: () -> Unit,
    modifier: Modifier,
) {
    AnimatedContent(
        targetState = state.photo == null,
        transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
        label = "scan buttons",
        modifier = modifier,
    ) { live ->
        if (live) {
            Button(
                onClick = onRead,
                enabled = readEnabled,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .height(64.dp)
                    .widthIn(min = 220.dp),
            ) {
                Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Read", style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = onPrevious, enabled = state.selected > 0, modifier = Modifier.size(52.dp)) {
                        Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous word")
                    }
                    Button(onClick = onNewScan, modifier = Modifier.height(52.dp)) {
                        Icon(painterResource(R.drawable.ic_camera), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("New scan")
                    }
                    FilledTonalIconButton(
                        onClick = onNext,
                        enabled = state.selected < state.words.lastIndex,
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next word")
                    }
                }
            }
        }
    }
}

/** The live camera, after making sure the app may use it. */
@Composable
private fun LiveCamera(onPreviewView: (PreviewView?) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember { mutableStateOf(isGranted(context, Manifest.permission.CAMERA)) }
    var askedAutomatically by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) {
        if (hasCamera && !granted && !askedAutomatically) {
            askedAutomatically = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    // The user may allow the camera in Settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = isGranted(context, Manifest.permission.CAMERA)
        onPauseOrDispose { }
    }

    when {
        !hasCamera -> CameraMessage("This device has no camera. You can still type a word below.")
        !granted -> {
            // After "Don't allow" twice, Android stops asking, so we offer the Settings page instead.
            val blocked = denied && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
            CameraMessage(
                text = "Allow the camera to read the words you point it at. Photos stay on your phone and are never uploaded.",
                buttonText = if (blocked) "Open settings" else "Allow camera",
                onButton = { if (blocked) openAppSettings(context) else launcher.launch(Manifest.permission.CAMERA) },
            )
        }
        else -> CameraPreview(onPreviewView)
    }
}

@SuppressLint("ClickableViewAccessibility") // the touch listener below only watches; PreviewView still handles taps
@Composable
private fun CameraPreview(onPreviewView: (PreviewView?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // CameraX's controller also gives us pinch-to-zoom and tap-to-focus for free.
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(0) // preview only: "Read" takes the picture straight from the preview
        }
    }
    var torchOn by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        controller.initializationFuture.addListener({
            try { // tablets and Chromebooks may only have a front camera
                if (!controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) && controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                }
            } catch (e: Exception) {
                // No usable camera: the preview stays black, and typing still works.
            }
        }, ContextCompat.getMainExecutor(context))
        controller.torchState.observe(lifecycleOwner) { torchOn = it == TorchState.ON }
        onDispose {
            controller.torchState.removeObservers(lifecycleOwner)
            controller.unbind() // the camera is off whenever this screen isn't showing
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE // keeps the rounded corners
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                    // The camera can scroll with the page. Two fingers on it mean zoom, so the page mustn't scroll then.
                    setOnTouchListener { view, event ->
                        if (event.pointerCount > 1) view.parent?.requestDisallowInterceptTouchEvent(true)
                        false // PreviewView still handles every touch: zoom and focus
                    }
                    onPreviewView(this)
                }
            },
            onRelease = { onPreviewView(null) },
        )
        Crosshair()
        IconButton(
            onClick = { controller.enableTorch(!torchOn) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
        ) {
            Icon(
                painterResource(if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off),
                contentDescription = if (torchOn) "Turn the light off" else "Turn the light on",
                tint = Color.White,
            )
        }
        Text(
            "Pinch to zoom · tap to focus",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }
}

/** The + in the middle of the camera: the word under it is looked up first. */
@Composable
private fun Crosshair() {
    Canvas(Modifier.fillMaxSize()) {
        val arm = 14.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        for ((color, width) in listOf(Color.Black.copy(alpha = 0.45f) to 5.dp.toPx(), Color.Cyan to 2.dp.toPx())) {
            drawLine(color, center.copy(x = center.x - arm), center.copy(x = center.x + arm), width)
            drawLine(color, center.copy(y = center.y - arm), center.copy(y = center.y + arm), width)
        }
    }
}

/** The frozen photo with every word highlighted; tap a word to look it up. */
@Composable
private fun FrozenPhoto(photo: Bitmap, words: List<OcrWord>, selected: Int, onTap: (Float, Float, Float) -> Unit) {
    val image = remember(photo) { photo.asImageBitmap() }
    val density = LocalDensity.current
    val tapTolerance = with(density) { 24.dp.toPx() } // a near miss still picks the closest word
    val corner = CornerRadius(with(density) { 4.dp.toPx() })
    val outline = with(density) { 3.dp.toPx() }
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(photo) {
                detectTapGestures { tap ->
                    val fit = Fit.of(photo, size.width.toFloat(), size.height.toFloat())
                    onTap(fit.toPhotoX(tap.x), fit.toPhotoY(tap.y), tapTolerance / fit.scale)
                }
            }
    ) {
        val fit = Fit.of(photo, size.width, size.height)
        drawImage(
            image,
            dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize((photo.width * fit.scale).roundToInt(), (photo.height * fit.scale).roundToInt()),
        )
        words.forEachIndexed { index, word ->
            val isSelected = index == selected
            for (box in word.boxes) {
                val topLeft = Offset(fit.left + box.left * fit.scale, fit.top + box.top * fit.scale)
                val boxSize = Size(box.width * fit.scale, box.height * fit.scale)
                val fill = if (isSelected) SelectedWordHighlight.copy(alpha = 0.35f) else WordHighlight.copy(alpha = 0.2f)
                drawRoundRect(fill, topLeft, boxSize, corner)
                if (isSelected) drawRoundRect(SelectedWordHighlight, topLeft, boxSize, corner, style = Stroke(outline))
            }
        }
    }
}

/** How the photo is scaled and centered inside its frame, to match taps and boxes to the photo. */
private class Fit(val scale: Float, val left: Float, val top: Float) {
    fun toPhotoX(x: Float) = (x - left) / scale
    fun toPhotoY(y: Float) = (y - top) / scale

    companion object {
        fun of(photo: Bitmap, width: Float, height: Float): Fit {
            val scale = minOf(width / photo.width, height / photo.height)
            return Fit(scale, (width - photo.width * scale) / 2f, (height - photo.height * scale) / 2f)
        }
    }
}

@Composable
private fun CameraMessage(text: String, buttonText: String? = null, onButton: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(text, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
        if (buttonText != null) {
            Button(onClick = onButton, modifier = Modifier.padding(top = 16.dp)) { Text(buttonText) }
        }
    }
}

// --- Definition panel ------------------------------------------------------------------------

/**
 * The definition, as a page that scrolls. In portrait the camera (or photo) is the top of this
 * page, so scrolling through a long definition pushes the picture up and out of the way. The
 * word's top row (the word, speaker, pencil and star) then sticks to the top of the screen, and
 * the rest of the text scrolls under it.
 */
@Composable
private fun DefinitionPanel(
    state: ScanState,
    savedWords: Set<String>,
    speak: (String) -> Unit,
    onStar: () -> Unit,
    onType: () -> Unit,
    onRetry: () -> Unit,
    camera: @Composable (Modifier) -> Unit,
    cameraHeight: Dp, // 0 when the camera isn't part of this page (landscape)
    bottomSpace: Dp,
    modifier: Modifier,
) {
    val lookup = state.lookup
    val scroll = remember(lookup?.word) { ScrollState(0) } // every new word starts at the top, with the picture in view
    val stickAt = with(LocalDensity.current) { cameraHeight.roundToPx() } // the top row sticks once the camera is gone
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        // The page only scrolls when it doesn't fit, so a drag on the camera doesn't stretch it for nothing.
        modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0)
    ) {
        if (cameraHeight > 0.dp) {
            camera(
                Modifier
                    .fillMaxWidth()
                    .height(cameraHeight)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            )
        }
        if (lookup == null) {
            Hint(state.hint ?: "Point the + at a word and tap Read.", onType)
            return@Column
        }
        val found = lookup.result as? LookupResult.Found
        WordHeader(
            word = found?.entry?.word ?: lookup.word,
            phonetic = found?.entry?.phonetic.orEmpty(),
            onSpeak = { speak(found?.word ?: lookup.word) },
            modifier = Modifier
                .zIndex(1f) // drawn over the text that scrolls under it
                .offset { IntOffset(0, (scroll.value - stickAt).coerceAtLeast(0)) } // stays at the top
                .background(MaterialTheme.colorScheme.background)
                .drawWithContent {
                    drawContent()
                    if (scroll.value > stickAt) { // a thin line where the text goes under the row
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                }
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onType) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Fix the spelling, or type another word")
            }
            StarButton(starred = found != null && found.word in savedWords, enabled = found != null, onClick = onStar)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            when (val result = lookup.result) {
                null -> LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
                is LookupResult.Found -> DefinitionView(result.entry)
                LookupResult.NotFound -> Problem(
                    "No definition found for “${lookup.word}”.",
                    "If the camera misread it, tap the pencil to fix the spelling.",
                )
                LookupResult.Offline -> Problem(
                    "No internet connection.",
                    "Connect to Wi-Fi or mobile data, then try again.",
                    onRetry,
                )
                LookupResult.Unavailable -> Problem(
                    "The dictionary isn't responding.",
                    "This is usually temporary. Please try again in a moment.",
                    onRetry,
                )
            }
        }
        Spacer(Modifier.height(bottomSpace))
    }
}

@Composable
private fun Hint(text: String, onType: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        TextButton(onClick = onType, modifier = Modifier.padding(top = 8.dp)) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Type a word instead")
        }
    }
}

@Composable
private fun Problem(title: String, detail: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
        }
    }
}

/** A box for typing a word, or fixing one the camera misread. */
@Composable
private fun TypeWordDialog(initial: String, onDismiss: () -> Unit, onLookUp: (String) -> Boolean) {
    var text by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    var invalid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val submit: () -> Unit = {
        if (onLookUp(text.text)) {
            onDismiss()
        } else {
            invalid = true
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Look up a word") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    if (it.text.length <= 60) {
                        text = it
                        invalid = false
                    }
                },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    Text(if (invalid) "Please type one English word (letters only)." else "Fix a misread word, or type any word.")
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = submit) { Text("Look up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
