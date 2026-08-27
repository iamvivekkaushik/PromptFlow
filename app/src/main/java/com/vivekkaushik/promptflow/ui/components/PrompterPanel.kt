package com.vivekkaushik.promptflow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GFont
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.core.data.PrompterSettings
import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.PlexSans
import com.vivekkaushik.promptflow.ui.theme.fontProvider
import java.io.File

/** Resolve the prompter font from settings: custom .ttf/.otf wins, else Google Font by name. */
@Composable
fun rememberPrompterFontFamily(settings: PrompterSettings): FontFamily =
    remember(settings.fontName, settings.customFontPath) {
        if (settings.customFontPath.isNotBlank()) {
            val f = File(settings.customFontPath)
            if (f.exists()) return@remember FontFamily(Font(f))
        }
        if (settings.fontName == "IBM Plex Sans") return@remember PlexSans
        runCatching {
            FontFamily(
                GFont(googleFont = GoogleFont(settings.fontName), fontProvider = fontProvider, weight = FontWeight.Normal),
                GFont(googleFont = GoogleFont(settings.fontName), fontProvider = fontProvider, weight = FontWeight.SemiBold),
                GFont(googleFont = GoogleFont(settings.fontName), fontProvider = fontProvider, weight = FontWeight.Bold),
            )
        }.getOrDefault(PlexSans)
    }

@Composable
fun prompterTextStyle(settings: PrompterSettings, fontScale: Float = 1f): TextStyle {
    val family = rememberPrompterFontFamily(settings)
    val size = (settings.fontSizeSp * fontScale).sp
    return TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(settings.fontWeight),
        fontSize = size,
        lineHeight = size * settings.lineHeightMult,
        color = Color(settings.textColor),
    )
}

/** Static mirrored block of prompter-styled text — used by the Settings live preview. */
@Composable
fun PrompterPreviewText(settings: PrompterSettings, text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = prompterTextStyle(settings),
        modifier = modifier.graphicsLayer {
            scaleX = if (settings.mirrorH) -1f else 1f
            scaleY = if (settings.mirrorV) -1f else 1f
        }
    )
}

/**
 * Scrolling prompter viewport (spec §02, Zone B): lens caret on the camera axis,
 * eye-contact guide band at 26% height (10% lime tint + 2dp top rule),
 * text fading under top/bottom scrims. Mirroring flips the text container only.
 */
@Composable
fun PrompterViewport(
    engine: PrompterEngine,
    settings: PrompterSettings,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f,
    guideBandFraction: Float = 0.06f,
    guideBandHeightDp: Int = 44,
    scrimColor: Color = Color(0xFF12140F),
    horizontalPaddingDp: Int = 22,
) {
    val state by engine.state.collectAsState()
    val style = prompterTextStyle(settings, fontScale)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .pointerInput(settings.tapPause, settings.startDelaySec) {
                detectTapGestures(onDoubleTap = { if (settings.tapPause) engine.togglePlay(settings.startDelaySec) })
            }
    ) {
        val bandTop = maxHeight * guideBandFraction

        // Scrolling text — starts at the guide band, translated up as the clock runs.
        // Measured with UNBOUNDED height: the script is taller than the viewport, and a
        // bounded measure would truncate the text to the panel and misreport contentHeight.
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = bandTop.toPx() - state.offsetPx
                    scaleX = if (settings.mirrorH) -1f else 1f
                    scaleY = if (settings.mirrorV) -1f else 1f
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                    )
                    engine.contentHeightPx = placeable.height.toFloat()
                    // Report a size within constraints: exceeding them makes Compose
                    // auto-center the oversized content (text would start mid-script).
                    layout(placeable.width, placeable.height.coerceAtMost(constraints.maxHeight)) {
                        placeable.place(0, 0)
                    }
                }
                .padding(horizontal = horizontalPaddingDp.dp)
        ) {
            state.text.split('\n').filter { it.isNotBlank() }.forEach { line ->
                Text(
                    text = line.trim(),
                    style = style,
                    modifier = Modifier.padding(bottom = (settings.fontSizeSp * fontScale * 0.55f).dp)
                )
            }
        }

        // Eye-contact guide band
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = bandTop)
                .padding(horizontal = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Lime))
            Box(Modifier.fillMaxWidth().height(guideBandHeightDp.dp).background(Lime.copy(alpha = 0.10f)))
        }

        // Scrims (over the band, under the caret)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.08f)
                .background(Brush.verticalGradient(0f to scrimColor, 1f to Color.Transparent))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.30f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.9f to scrimColor))
        )

        // Start-delay countdown
        if (state.countdown > 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.countdown.toString(),
                    style = TextStyle(
                        fontFamily = com.vivekkaushik.promptflow.ui.theme.Sora,
                        fontWeight = FontWeight.Bold,
                        fontSize = (64 * fontScale).sp,
                        color = Lime,
                    )
                )
            }
        }

        // Lens caret — marks the camera axis
        Canvas(Modifier.align(Alignment.TopCenter).size(width = 16.dp, height = 8.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, Lime)
        }
    }
}
