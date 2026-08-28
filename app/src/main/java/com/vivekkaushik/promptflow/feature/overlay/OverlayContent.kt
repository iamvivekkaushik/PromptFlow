package com.vivekkaushik.promptflow.feature.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.core.data.PrompterSettings
import com.vivekkaushik.promptflow.ui.components.PrompterViewport
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.OnLime
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.PromptFlowTheme
import com.vivekkaushik.promptflow.ui.theme.Record
import com.vivekkaushik.promptflow.ui.theme.Sora
import kotlinx.coroutines.launch
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.R
import androidx.compose.foundation.layout.widthIn

/**
 * Floating window anatomy (spec §02): 32dp drag header, glass text viewport with the
 * same guide band + lens caret as Studio, 48dp control strip, corner resize chevron,
 * and a 56dp bubble state with a red dot while scrolling.
 */
@Composable
fun OverlayContent(
    widthDp: Float,
    onDrag: (Float, Float) -> Unit,
    onPinch: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    PromptFlowTheme {
        val engine = Graph.engine
        val engineState by engine.state.collectAsState()
        val settings by Graph.settings.settings.collectAsState(initial = PrompterSettings())
        val scope = rememberCoroutineScope()
        var bubble by remember { mutableStateOf(false) }

        // Voice sync runs here too — same engine, same recognizer
        LaunchedEffect(engineState.playing, settings.voiceSync) {
            if (engineState.playing && settings.voiceSync) Graph.speechSync.start() else Graph.speechSync.stop()
        }

        if (bubble) {
            // Bubble state: 56dp circle, lime ring, red pulse dot when scrolling
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10120C).copy(alpha = 0.8f))
                    .border(2.dp, Lime, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) }
                    }
                    .clickable { bubble = false },
                contentAlignment = Alignment.Center
            ) {
                PFIcon(R.drawable.ic_mark, 24.dp, Lime)
                if (engineState.playing) {
                    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
                        1f, 0.35f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pv"
                    )
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(2.dp).size(10.dp)
                            .graphicsLayer { alpha = pulse }
                            .clip(CircleShape).background(Record)
                    )
                }
            }
            return@PromptFlowTheme
        }

        Column(
            Modifier
                .width(widthDp.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0A0C08).copy(alpha = settings.overlayOpacity / 100f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) onPinch(zoom) }
                }
        ) {
            // Header — whole surface drags the window
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f))
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) }
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(Outline)) }
                }
                Text(
                    "DRAG · PINCH RESIZE",
                    fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                    letterSpacing = 1.sp, color = Outline,
                    modifier = Modifier.weight(1f),
                )
                // Live WPM — volume keys and these steppers drive the same engine value
                Row(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = 0.08f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).clickable { engine.nudgeWpm(-10) },
                        contentAlignment = Alignment.Center
                    ) { Text("−", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        "${engineState.wpm}",
                        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
                        color = Lime, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.widthIn(min = 24.dp),
                    )
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).clickable { engine.nudgeWpm(10) },
                        contentAlignment = Alignment.Center
                    ) { Text("+", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable { bubble = true },
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_pip, 12.dp, MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) { Text("✕", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            // Text viewport — min 120dp (spec), overlay text at 0.7× studio size
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                PrompterViewport(
                    engine = engine,
                    settings = settings,
                    fontScale = 0.7f,
                    guideBandFraction = 0.06f,
                    guideBandHeightDp = 34,
                    scrimColor = Color(0xFF0A0C08),
                    horizontalPaddingDp = 14,
                    modifier = Modifier.fillMaxSize()
                )
                // Corner resize chevron
                androidx.compose.foundation.Canvas(
                    Modifier.align(Alignment.BottomEnd).padding(3.dp).size(14.dp)
                ) {
                    val stroke = 2.dp.toPx()
                    val c = Outline.copy(alpha = 0.7f)
                    drawLine(c, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), stroke)
                    drawLine(c, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), stroke)
                }
            }

            // Control strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .border1Top()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Lime).clickable { engine.togglePlay(settings.startDelaySec) },
                    contentAlignment = Alignment.Center
                ) {
                    PFIcon(if (engineState.playing || engineState.countdown > 0) R.drawable.ic_pause else R.drawable.ic_play, 15.dp, OnLime)
                }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable { engine.rewind() },
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_rewind, 15.dp, MaterialTheme.colorScheme.onSurface) }
                Slider(
                    value = settings.overlayOpacity.toFloat(),
                    onValueChange = { v -> scope.launch { Graph.settings.setOverlayOpacity(v.toInt()) } },
                    valueRange = 15f..95f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color.White.copy(alpha = 0.1f))
                )
                Text("${settings.overlayOpacity}%", fontFamily = PlexMono, fontSize = 10.sp, color = Outline)
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_tune, 15.dp, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private fun Modifier.border1Top(): Modifier = this.then(
    Modifier.background(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.08f),
            0.02f to Color.Transparent,
            1f to Color.Transparent,
        )
    )
)
