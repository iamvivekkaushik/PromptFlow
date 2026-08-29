package com.vivekkaushik.promptflow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.R
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainer
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh

/**
 * In-app reference for script markup and the controls that aren't discoverable by looking
 * (marker syntax, gestures, hardware keys). Reachable from Settings and the editor toolbar.
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceContainerHigh).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { PFIcon(R.drawable.ic_back, 19.dp, MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Help & shortcuts", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp))
        }

        HelpCard("SCRIPT MARKERS") {
            Text(
                "Type these anywhere in a script, or use the buttons above the editor. " +
                    "Markers are never read aloud and never count toward word count or duration.",
                style = MaterialTheme.typography.bodyMedium, color = Outline
            )
            MarkerHelp(
                syntax = "## Section name",
                title = "Section header",
                body = "Shows as a lime heading in the prompter. Use the rewind button (or Page Up on a " +
                    "keyboard) to jump back through sections while filming.",
            )
            MarkerHelp(
                syntax = "[[pause]]",
                title = "Stop and wait",
                body = "Scrolling stops when this line reaches the reading band and waits for you. " +
                    "Tap play — or double-tap the text — to carry on. Good for ad-libs and B-roll setups.",
            )
            MarkerHelp(
                syntax = "[[pause 3s]]",
                title = "Timed hold",
                body = "Same, but resumes on its own after the countdown. Any number works: " +
                    "[[pause 5s]], [[pause 10s]].",
            )
            MarkerHelp(
                syntax = "[[b-roll: drone shot]]",
                title = "Stage direction",
                body = "A dim note to yourself — reminders, camera moves, props. Shown in the prompter " +
                    "but never spoken. [[note: …]] works the same way.",
            )
        }

        HelpCard("WHILE THE PROMPTER RUNS") {
            TipRow("Double-tap the text", "Pause or resume (can be turned off in Settings).")
            TipRow("Volume up / down", "Trim speed by ±10 WPM, in the Studio and the overlay.")
            TipRow("Rewind button", "Tap for the previous section, long-press to jump back to the top.")
            TipRow("Bluetooth clicker or keyboard", "Play/pause on the media key or space; Page Up steps back a section.")
            TipRow("Start delay", "Set a 3, 5 or 10 second countdown in Settings so you can get into position.")
        }

        HelpCard("VOICE SYNC") {
            Text(
                "Turn on Voice-activated scroll in Settings and the prompter listens as you read, " +
                    "keeping your current line in the reading band — speed up, slow down or pause to " +
                    "think and the script follows.",
                style = MaterialTheme.typography.bodyMedium, color = Outline
            )
            TipRow("Needs the microphone", "Recognition runs on your device; nothing is uploaded by the app.")
            TipRow("Paused while recording", "The Studio recording owns the mic, so sync resumes when you stop.")
        }

        HelpCard("STUDIO") {
            TipRow("Tap the viewfinder", "Focus there, and drag the slider beside the ring for exposure.")
            TipRow("Pinch, or tap the zoom pill", "Zoom freely, or cycle 1× / 2× / 4×.")
            TipRow("Top chips", "Switch quality (4K/1080p/720p) and frame rate; toggle torch and the grid.")
            TipRow("Side rail", "Mirror the text, flip the camera, and change framing (16:9 / 4:3 / 1:1).")
            TipRow("Takes", "Every recording is saved to Movies/PromptFlow and listed on its script.")
            TipRow("Selfie mirroring", "Front-camera takes save the way the viewfinder looks — change it under Settings → Recording.")
        }

        HelpCard("FLOATING OVERLAY") {
            Text(
                "Start the overlay from a script, then open any camera app — the prompter floats on top.",
                style = MaterialTheme.typography.bodyMedium, color = Outline
            )
            TipRow("Drag the header", "Move the panel anywhere on screen.")
            TipRow("Pinch the panel", "Resize it; the corner chevron marks the grip.")
            TipRow("Header buttons", "Collapse to a bubble, adjust WPM, or close the overlay.")
            TipRow("Opacity slider", "Fade the panel so it sits lightly over the viewfinder.")
        }

        HelpCard("IMPORTING") {
            Text(
                "Import brings in .txt, .docx, .pdf and .md files from your device. Markdown headings " +
                    "become prompter sections automatically, so an outlined document arrives ready to film.",
                style = MaterialTheme.typography.bodyMedium, color = Outline
            )
        }
    }
}

@Composable
private fun HelpCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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

/** Marker reference: the literal syntax, then what it does. */
@Composable
private fun MarkerHelp(syntax: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(SurfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(syntax, fontFamily = PlexMono, fontSize = 12.sp, color = Lime)
        }
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Outline)
    }
}

@Composable
private fun TipRow(label: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(Lime))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Outline)
        }
    }
}
