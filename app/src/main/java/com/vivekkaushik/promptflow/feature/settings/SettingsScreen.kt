package com.vivekkaushik.promptflow.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.core.data.PrompterSettings
import com.vivekkaushik.promptflow.ui.components.PrompterPreviewText
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.LimeContainer
import com.vivekkaushik.promptflow.ui.theme.OnLimeContainer
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.OutlineVariant
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainer
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh
import kotlinx.coroutines.launch
import java.io.File
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.R

private val GOOGLE_FONTS = listOf(
    "IBM Plex Sans", "Sora", "Inter", "Roboto", "Open Sans", "Lato",
    "Montserrat", "Poppins", "Merriweather", "Literata", "Atkinson Hyperlegible",
)

// High-contrast prompter text colors (spec §03: ≥7:1 pairs on the dark glass)
private val SWATCHES = listOf(0xFFE4E3DB, 0xFFC7E86C, 0xFFFFB94E, 0xFF7FD4FF)

/** Prompter settings: typography, color, mirror & smart scroll, hardware remotes (spec screen 4). */
@Composable
@Preview(name = "Settings Screen", apiLevel = 36)
fun SettingsScreen(onBack: () -> Unit = {}, onOpenLicenses: () -> Unit = {}) {
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val settings by if (isPreview) {
        remember { mutableStateOf(PrompterSettings()) }
    } else {
        Graph.settings.settings.collectAsState(initial = PrompterSettings())
    }
    val scope = rememberCoroutineScope()
    val store = if (isPreview) null else Graph.settings

    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = appCtx
            val dir = File(ctx.filesDir, "fonts").apply { mkdirs() }
            val dest = File(dir, "custom_font")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            store?.setCustomFontPath(dest.absolutePath)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceContainerHigh).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { PFIcon(R.drawable.ic_back, 19.dp, MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Prompter settings", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp))
        }

        // Live preview
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainer).height(170.dp).padding(8.dp)
        ) {
            PrompterPreviewText(settings, "The quick brown fox jumps over the lazy dog while reading at pace.")
            Text("LIVE PREVIEW", fontFamily = PlexMono, fontSize = 10.sp, color = Outline, modifier = Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(16.dp))

        // TYPOGRAPHY
        SettingsCard("TYPOGRAPHY") {
            SliderRow("Size", settings.fontSizeSp.toFloat(), 18f..100f, "${settings.fontSizeSp}sp") {
                scope.launch { store?.setFontSize(it.toInt()) }
            }
            SliderRow("Line spacing", settings.lineHeightMult, 1.2f..2.2f, "${"%.1f".format(settings.lineHeightMult)}×") {
                scope.launch { store?.setLineHeight((it * 10).toInt() / 10f) }
            }
            // Weight segmented control
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Weight", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(100.dp))
                ) {
                    listOf("Regular" to 400, "Semi-Bold" to 600, "Bold" to 700).forEach { (name, w) ->
                        val selected = settings.fontWeight == w
                        Box(
                            Modifier.weight(1f)
                                .background(if (selected) LimeContainer else Color.Transparent)
                                .clickable { scope.launch { store?.setWeight(w) } }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) OnLimeContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // Font picker + custom file
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Font", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                var expanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                            .clickable { expanded = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (settings.customFontPath.isNotBlank()) "Custom font" else settings.fontName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("Fonts ▾", style = MaterialTheme.typography.bodySmall, color = Lime, maxLines = 1)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        GOOGLE_FONTS.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { expanded = false; scope.launch { store?.setFontName(name) } }
                            )
                        }
                    }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
                        .clickable { fontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream")) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) { Text(".ttf / .otf", style = MaterialTheme.typography.bodySmall, color = Outline) }
            }
        }

        // COLOR
        SettingsCard("COLOR") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(88.dp)) {
                    Text("Text color", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("#" + settings.textColor.toString(16).takeLast(6).uppercase(), fontFamily = PlexMono, fontSize = 11.sp, color = Outline, maxLines = 1)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SWATCHES.forEach { c ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .border(2.dp, if (settings.textColor == c) Lime else Color.Transparent, CircleShape)
                                .padding(3.dp).clip(CircleShape).background(Color(c))
                                .clickable { scope.launch { store?.setTextColor(c) } }
                        )
                    }
                }
            }
        }

        // MIRROR & SMART SCROLL
        SettingsCard("MIRROR & SMART SCROLL") {
            ToggleRow("Voice-activated scroll", "Match speed to your speaking pace", settings.voiceSync) { scope.launch { store?.setVoiceSync(it) } }
            ToggleRow("Mirror horizontal", "For beam-splitter glass rigs", settings.mirrorH) { scope.launch { store?.setMirrorH(it) } }
            ToggleRow("Mirror vertical", "For overhead rig mounts", settings.mirrorV) { scope.launch { store?.setMirrorV(it) } }
            ToggleRow("Double-tap to pause", "Anywhere on the prompter text", settings.tapPause) { scope.launch { store?.setTapPause(it) } }
            // Start delay: countdown shown in the prompter before scrolling begins
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(88.dp)) {
                    Text("Start delay", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Countdown before scroll", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Outline)
                }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(100.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(100.dp))
                ) {
                    listOf(0, 3, 5, 10).forEach { sec ->
                        val selected = settings.startDelaySec == sec
                        Box(
                            Modifier.weight(1f)
                                .background(if (selected) LimeContainer else Color.Transparent)
                                .clickable { scope.launch { store?.setStartDelay(sec) } }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (sec == 0) "Off" else "${sec}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) OnLimeContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ABOUT
        SettingsCard("ABOUT") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val version = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "1.0"
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Version", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(version, fontFamily = PlexMono, fontSize = 12.sp, color = Outline)
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenLicenses),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Open-source libraries", style = MaterialTheme.typography.bodyLarge)
                    Text("Licenses & attributions", style = MaterialTheme.typography.bodySmall, color = Outline)
                }
                PFIcon(R.drawable.ic_chevron, 14.dp, Outline)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainer)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.5.sp, color = Outline)
        content()
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    readout: String,
    onFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            onValueChangeFinished = onFinished,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = SurfaceContainerHigh)
        )
        Text(readout, fontFamily = PlexMono, fontSize = 12.sp, color = Lime, modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ToggleRow(name: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Outline)
        }
        // Track + knob, standard M3 easing 300ms (spec §03 motion)
        val knobX by animateDpAsState(if (checked) 22.dp else 0.dp, tween(300), label = "knob")
        Box(
            Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(100.dp))
                .background(if (checked) LimeContainer else SurfaceContainerHigh)
                .border(1.dp, OutlineVariant, RoundedCornerShape(100.dp))
                .clickable { onChange(!checked) }
        ) {
            Box(
                Modifier.padding(3.dp).size(22.dp).offset(x = knobX).clip(CircleShape)
                    .background(if (checked) Lime else Outline)
            )
        }
    }
}

