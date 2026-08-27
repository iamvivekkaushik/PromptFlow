package com.vivekkaushik.promptflow.feature.studio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Range
import android.util.Rational
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.core.data.PrompterSettings
import com.vivekkaushik.promptflow.ui.components.PrompterViewport
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.OnLime
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.Record
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.sin
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.R

private val QUALITIES = listOf(
    Quality.UHD to "4K",
    Quality.FHD to "1080p",
    Quality.HD to "720p",
)

private val ASPECTS = listOf(
    "16:9" to Rational(9, 16),  // rationals are in portrait orientation
    "4:3" to Rational(3, 4),
    "1:1" to Rational(1, 1),
)

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

/**
 * In-App Studio (spec §02): CameraX viewfinder + glass prompter panel.
 * Quality (4K/1080p/720p), frame rate (30/60) and aspect (16:9/4:3/1:1 via ViewPort,
 * so the crop applies to both viewfinder and recording) are all switchable from the
 * status chips / side rail. Prompter is UI-only — never in the footage.
 */
@Composable
fun StudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = Graph.engine
    val engineState by engine.state.collectAsState()
    val settings by Graph.settings.settings.collectAsState(initial = PrompterSettings())
    val scope = rememberCoroutineScope()

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasPermissions = grants[Manifest.permission.CAMERA] == true && grants[Manifest.permission.RECORD_AUDIO] == true
    }
    LaunchedEffect(Unit) {
        if (!hasPermissions) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var qualityIdx by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(30) }
    var aspectIdx by remember { mutableIntStateOf(0) }
    var cameraBound by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recDurationMs by remember { mutableLongStateOf(0L) }
    var audioAmplitude by remember { mutableFloatStateOf(0f) }
    val isRecording = recording != null

    val videoCapture = remember(qualityIdx, fps) {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    QUALITIES[qualityIdx].first,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                )
            )
            .build()
        VideoCapture.Builder(recorder)
            .setTargetFrameRate(Range(fps, fps))
            .build()
    }

    // Bind camera whenever the config changes. ViewPort crops preview AND recording (WYSIWYG).
    LaunchedEffect(previewView, lensFacing, videoCapture, aspectIdx, hasPermissions) {
        val pv = previewView ?: return@LaunchedEffect
        if (!hasPermissions) return@LaunchedEffect
        cameraBound = false
        val provider = awaitCameraProvider(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
        val rotation = pv.display?.rotation ?: Surface.ROTATION_0
        val group = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(videoCapture)
            .setViewPort(ViewPort.Builder(ASPECTS[aspectIdx].second, rotation).build())
            .build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, lensFacing, group)
            cameraBound = true
        } catch (e: Exception) {
            Toast.makeText(context, "Camera setup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Keep-awake while scrolling (spec §04); persist state on exit; never leave a recording dangling
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        Graph.hardwareKeysActive = true
        onDispose {
            view.keepScreenOn = false
            Graph.hardwareKeysActive = false
            recording?.stop()
            Graph.persistProgress()
            Graph.persistWpm(engine.state.value.wpm)
        }
    }

    // Voice sync: mic is shared — pause ASR while recording (spec §04)
    LaunchedEffect(engineState.playing, settings.voiceSync, isRecording, hasPermissions) {
        if (engineState.playing && settings.voiceSync && !isRecording && hasPermissions) {
            Graph.speechSync.start()
        } else {
            Graph.speechSync.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { Graph.speechSync.stop() } }

    fun cycleQuality() {
        if (isRecording) { Toast.makeText(context, "Stop recording to change quality", Toast.LENGTH_SHORT).show(); return }
        qualityIdx = (qualityIdx + 1) % QUALITIES.size
    }

    fun toggleFps() {
        if (isRecording) { Toast.makeText(context, "Stop recording to change frame rate", Toast.LENGTH_SHORT).show(); return }
        fps = if (fps == 30) 60 else 30
    }

    fun cycleAspect() {
        if (isRecording) { Toast.makeText(context, "Stop recording to change aspect", Toast.LENGTH_SHORT).show(); return }
        aspectIdx = (aspectIdx + 1) % ASPECTS.size
    }

    fun flipCamera() {
        recording?.stop()
        recording = null
        lensFacing = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0C0E0A))) {
        if (hasPermissions) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Camera & microphone access needed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Studio records video with the prompter floating above the viewfinder — the prompter never appears in your footage.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline
                )
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(Lime)
                        .clickable { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Grant access", style = MaterialTheme.typography.labelLarge, color = OnLime) }
            }
        }

        // Zone A — status chips (below the system status bar). Quality and fps chips are tappable.
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val recPulse = rememberInfiniteTransition(label = "rec").animateFloat(
                1f, 0.35f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "recPulse"
            )
            StatusChip {
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (isRecording) Record else MaterialTheme.colorScheme.onSurfaceVariant)
                        .graphicsLayer { alpha = if (isRecording) recPulse.value else 1f }
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isRecording) "REC ${formatDuration(recDurationMs)}" else "STANDBY",
                    fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    color = if (isRecording) Record else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusChip(onClick = ::cycleQuality) {
                Text("${QUALITIES[qualityIdx].second} ▾", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(onClick = ::toggleFps) {
                Text("${fps}fps ▾", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Zone B — prompter glass panel, under the punch-hole
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 44.dp)
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.46f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF10120C).copy(alpha = 0.72f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
        ) {
            PrompterViewport(engine = engine, settings = settings, modifier = Modifier.fillMaxSize())
        }

        // Zone C — side rail: mirror, flip camera, aspect
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).graphicsLayer { translationY = 60f },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RailButton(
                iconRes = R.drawable.ic_mirror_h,
                active = settings.mirrorH,
                onClick = { scope.launch { Graph.settings.setMirrorH(!settings.mirrorH) } }
            )
            RailButton(iconRes = R.drawable.ic_swap, active = false, onClick = ::flipCamera)
            RailButton(label = ASPECTS[aspectIdx].first, active = aspectIdx != 0, small = true, onClick = ::cycleAspect)
        }

        // Zones D + E — speed row and transport over bottom gradient
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color(0xFF080906).copy(alpha = 0.9f)))
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(22.dp)) {
                AudioMeter(amplitude = audioAmplitude, animate = isRecording || engineState.playing)
                Spacer(Modifier.width(8.dp))
                // Live level from the recorder's audio stats; no mic tap while idle
                val micDb = if (isRecording && audioAmplitude > 0.0001f)
                    (20 * kotlin.math.log10(audioAmplitude.toDouble())).toInt().coerceIn(-60, 0) else null
                PFIcon(R.drawable.ic_mic, 12.dp, if (micDb != null && micDb > -6) Record else Outline)
                Spacer(Modifier.width(5.dp))
                Text(
                    if (micDb != null) "MIC $micDb dB" else "MIC — dB",
                    fontFamily = PlexMono, fontSize = 10.sp,
                    color = if (micDb != null && micDb > -6) Record else Outline
                )
                Spacer(Modifier.weight(1f))
                PFIcon(R.drawable.ic_voice, 13.dp, if (settings.voiceSync) Lime else Color(0xFF5A5D50))
                Spacer(Modifier.width(5.dp))
                Text(
                    "VOICE SYNC ${if (settings.voiceSync) "ON" else "OFF"}",
                    fontFamily = PlexMono, fontSize = 10.sp,
                    color = if (settings.voiceSync) Lime else Color(0xFF5A5D50)
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperButton("−") { engine.nudgeWpm(-10) }
                Slider(
                    value = engineState.wpm.toFloat(),
                    onValueChange = { engine.setWpm(it.toInt()) },
                    valueRange = 60f..500f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = SurfaceContainerHigh)
                )
                StepperButton("+") { engine.nudgeWpm(10) }
                Text("${engineState.wpm} WPM", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Lime, modifier = Modifier.width(70.dp))
            }
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(SurfaceContainerHigh)
                        .clickable { engine.rewind() },
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_rewind, 21.dp, MaterialTheme.colorScheme.onSurface) }

                // 72dp record button; red circle idle → rounded red square while recording
                val innerSize by androidx.compose.animation.core.animateDpAsState(
                    if (isRecording) 28.dp else 58.dp, tween(220), label = "recSize"
                )
                val innerCorner by androidx.compose.animation.core.animateDpAsState(
                    if (isRecording) 8.dp else 29.dp, tween(220), label = "recCorner"
                )
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .border(4.dp, Color.White.copy(alpha = if (cameraBound) 0.85f else 0.3f), CircleShape)
                        .clickable(enabled = hasPermissions) {
                            if (!cameraBound) {
                                Toast.makeText(context, "Camera still starting…", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            val current = recording
                            if (current != null) {
                                current.stop()
                            } else {
                                recording = startRecording(context, videoCapture) { event ->
                                    when (event) {
                                        is VideoRecordEvent.Status -> {
                                            recDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                                            audioAmplitude = runCatching {
                                                event.recordingStats.audioStats.audioAmplitude.toFloat()
                                            }.getOrDefault(0f)
                                        }
                                        is VideoRecordEvent.Finalize -> {
                                            recDurationMs = 0L
                                            audioAmplitude = 0f
                                            recording = null
                                            if (event.hasError()) {
                                                Toast.makeText(context, "Recording failed (error ${event.error})", Toast.LENGTH_LONG).show()
                                            } else {
                                                Graph.markCurrentScriptRecorded()
                                                Toast.makeText(context, "Saved to Movies/PromptFlow", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                                if (!engineState.playing && engineState.countdown == 0) {
                                    engine.togglePlay(settings.startDelaySec)
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(innerSize)
                            .clip(RoundedCornerShape(innerCorner))
                            .background(if (cameraBound) Record else Record.copy(alpha = 0.4f))
                    )
                }

                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Lime)
                        .clickable { engine.togglePlay(settings.startDelaySec) },
                    contentAlignment = Alignment.Center
                ) {
                    PFIcon(if (engineState.playing || engineState.countdown > 0) R.drawable.ic_pause else R.drawable.ic_play, 21.dp, OnLime)
                }
            }
        }

        // Back — below the system status bar so it stays tappable
        Box(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 4.dp)
                .size(40.dp).clip(CircleShape).background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { PFIcon(R.drawable.ic_back, 19.dp, MaterialTheme.colorScheme.onSurface) }
    }
}

private fun startRecording(
    context: Context,
    videoCapture: VideoCapture<Recorder>,
    onEvent: (VideoRecordEvent) -> Unit,
): Recording {
    val name = "PromptFlow_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PromptFlow")
    }
    val outputOptions = MediaStoreOutputOptions.Builder(
        context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    ).setContentValues(contentValues).build()

    var pending = videoCapture.output.prepareRecording(context, outputOptions)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
        pending = pending.withAudioEnabled()
    }
    return pending.start(ContextCompat.getMainExecutor(context), onEvent)
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun StatusChip(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    var m = Modifier.clip(RoundedCornerShape(100.dp))
        .background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(
        m.padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RailButton(
    label: String? = null,
    iconRes: Int? = null,
    active: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
            .border(1.dp, if (active) Lime else Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (active) Lime else MaterialTheme.colorScheme.onSurfaceVariant
        if (iconRes != null) {
            PFIcon(iconRes, 21.dp, tint)
        } else {
            Text(
                label.orEmpty(),
                fontSize = if (small) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(SurfaceContainerHigh).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
}

/** Equalizer-style mic meter; bar heights ride the live audio amplitude while recording. */
@Composable
private fun AudioMeter(amplitude: Float, animate: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    val phase by transition.animateFloat(
        0f, (2 * Math.PI).toFloat(),
        infiniteRepeatable(tween(1100)), label = "eqPhase"
    )
    val durations = listOf(0.7f, 1.1f, 0.5f, 0.9f, 1.3f, 0.6f, 1.0f, 0.8f)
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        durations.forEachIndexed { i, d ->
            val base = if (animate) (0.35f + 0.65f * ((sin(phase / d + i * 0.9f) + 1f) / 2f)) else 0.25f
            val level = (base * (0.4f + 1.6f * amplitude.coerceIn(0f, 1f))).coerceIn(0.15f, 1f)
            Box(
                Modifier.width(4.dp).height((22 * level).dp)
                    .clip(RoundedCornerShape(2.dp)).background(Lime.copy(alpha = 0.85f))
            )
        }
    }
}
