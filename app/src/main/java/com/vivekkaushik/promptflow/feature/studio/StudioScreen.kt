package com.vivekkaushik.promptflow.feature.studio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * In-App Studio (spec §02): CameraX viewfinder + glass prompter panel.
 * Records 4K (UHD, falls back to FHD); the prompter is UI-only and never
 * appears in the footage. Zones A–E per the layout spec.
 */
@Composable
fun StudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = Graph.engine
    val engineState by engine.state.collectAsState()
    val settings by Graph.settings.settings.collectAsState(initial = PrompterSettings())
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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

    // Keep-awake while scrolling (spec §04)
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        Graph.hardwareKeysActive = true
        onDispose {
            view.keepScreenOn = false
            Graph.hardwareKeysActive = false
            Graph.persistProgress()
            Graph.persistWpm(engine.state.value.wpm)
        }
    }

    var lensFacing by remember { mutableStateOf(CameraSelector.DEFAULT_FRONT_CAMERA) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recDurationMs by remember { mutableLongStateOf(0L) }
    var audioAmplitude by remember { mutableFloatStateOf(0f) }
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.UHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD))
                )
                .build()
        )
    }

    // Voice sync: mic is shared — pause ASR while recording (spec §04)
    val isRecording = recording != null
    LaunchedEffect(engineState.playing, settings.voiceSync, isRecording, hasPermissions) {
        if (engineState.playing && settings.voiceSync && !isRecording && hasPermissions) {
            Graph.speechSync.start()
        } else {
            Graph.speechSync.stop()
        }
    }
    DisposableEffect(Unit) { onDispose { Graph.speechSync.stop() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF0C0E0A))) {
        if (hasPermissions) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, lensFacing, preview, videoCapture)
                        } catch (_: Exception) { }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
        } else {
            // Permission gate
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Camera & microphone access needed", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Studio records 4K video with the prompter floating above the viewfinder — the prompter never appears in your footage.",
                    style = MaterialTheme.typography.bodyMedium, color = Outline
                )
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(Lime)
                        .clickable { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) { Text("Grant access", style = MaterialTheme.typography.labelLarge, color = OnLime) }
            }
        }

        // Zone A — status chips
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
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
            StatusChip { Text("4K · 30fps", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // Zone B — prompter glass panel, 46% height, under the punch-hole
        Box(
            Modifier
                .align(Alignment.TopCenter)
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

        // Zone C — side rail
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).graphicsLayer { translationY = 60f },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RailButton(
                label = "⇋",
                active = settings.mirrorH,
                onClick = { scope.launch { Graph.settings.setMirrorH(!settings.mirrorH) } }
            )
            RailButton(label = "↺", active = false, onClick = {
                lensFacing = if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            })
            RailButton(label = "16:9", active = false, small = true, onClick = { })
        }

        // Zones D + E — speed row and transport over bottom gradient
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color(0xFF080906).copy(alpha = 0.9f)))
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp)
        ) {
            // Audio meter + voice sync status
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(22.dp)) {
                AudioMeter(amplitude = audioAmplitude, animate = isRecording || engineState.playing)
                Spacer(Modifier.width(8.dp))
                Text("MIC −12 dB", fontFamily = PlexMono, fontSize = 10.sp, color = Outline)
                Spacer(Modifier.weight(1f))
                Text(
                    "● VOICE SYNC ${if (settings.voiceSync) "ON" else "OFF"}",
                    fontFamily = PlexMono, fontSize = 10.sp,
                    color = if (settings.voiceSync) Lime else Color(0xFF5A5D50)
                )
            }
            Spacer(Modifier.height(12.dp))

            // Zone D — WPM slider with −/+ steppers (±10)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperButton("−") { engine.nudgeWpm(-10) }
                Slider(
                    value = engineState.wpm.toFloat(),
                    onValueChange = { engine.setWpm(it.toInt()) },
                    valueRange = 60f..240f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = SurfaceContainerHigh)
                )
                StepperButton("+") { engine.nudgeWpm(10) }
                Text("${engineState.wpm} WPM", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Lime, modifier = Modifier.width(70.dp))
            }
            Spacer(Modifier.height(14.dp))

            // Zone E — transport
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(SurfaceContainerHigh)
                        .clickable { engine.rewind() },
                    contentAlignment = Alignment.Center
                ) { Text("↺", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface) }

                // 72dp record button, 4dp white ring
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                        .padding(6.dp).clip(CircleShape)
                        .background(Record)
                        .clickable(enabled = hasPermissions) {
                            val current = recording
                            if (current != null) {
                                current.stop()
                                recording = null
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
                                        }
                                        else -> {}
                                    }
                                }
                                if (!engineState.playing) engine.togglePlay()
                            }
                        }
                )

                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Lime)
                        .clickable { engine.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (engineState.playing) "❚❚" else "▶", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnLime)
                }
            }
        }

        // Back
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp)
                .size(34.dp).clip(CircleShape).background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) { Text("←", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun StatusChip(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp))
            .background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun RailButton(label: String, active: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(Color(0xFF0C0E0A).copy(alpha = 0.7f))
            .border(1.dp, if (active) Lime else Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = if (small) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Lime else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
