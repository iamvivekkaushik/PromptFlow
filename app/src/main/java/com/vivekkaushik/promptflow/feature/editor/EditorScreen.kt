package com.vivekkaushik.promptflow.feature.editor

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.core.data.PrompterSettings
import com.vivekkaushik.promptflow.core.data.Script
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainer
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Script editor with live word count and est. duration = wordCount / WPM (spec §04). */
@OptIn(FlowPreview::class)
@Composable
fun EditorScreen(
    scriptId: Long,
    onBack: () -> Unit,
    onOpenStudio: (Script) -> Unit,
    onOpenOverlay: (Script) -> Unit,
) {
    var script by remember { mutableStateOf<Script?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    val settings by Graph.settings.settings.collectAsState(initial = PrompterSettings())

    LaunchedEffect(scriptId) {
        Graph.db.scripts().byId(scriptId)?.let {
            script = it; title = it.title; body = it.body
        }
        loaded = true
    }

    // Autosave, debounced
    LaunchedEffect(loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { title to body }.debounce(500).collect { (t, b) ->
            script?.let { s ->
                if (t != s.title || b != s.body) {
                    val updated = s.copy(title = t.ifBlank { "Untitled script" }, body = b, updatedAt = System.currentTimeMillis())
                    Graph.db.scripts().upsert(updated)
                    script = updated
                }
            }
        }
    }

    val words = body.split(Regex("\\s+")).count { it.isNotBlank() }
    val totalSec = if (words > 0) (words * 60.0 / settings.wpm).toInt() else 0
    val est = "%d:%02d".format(totalSec / 60, totalSec % 60)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceContainerHigh).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { Text("←", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                "$words w · $est",
                fontFamily = PlexMono, fontSize = 12.sp, color = Outline,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            script?.let { s ->
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp))
                        .border(1.dp, Lime.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                        .clickable { onOpenOverlay(s.copy(title = title, body = body)) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Overlay", style = MaterialTheme.typography.labelLarge, color = Lime)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(Lime)
                        .clickable { onOpenStudio(s.copy(title = title, body = body)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("▶ Studio", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        BasicTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(Lime),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            decorationBox = { inner ->
                Box { if (title.isEmpty()) Text("Title", style = MaterialTheme.typography.headlineMedium, color = Outline); inner() }
            }
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainer)
                .padding(16.dp)
        ) {
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 26.sp),
                cursorBrush = SolidColor(Lime),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { inner ->
                    Box { if (body.isEmpty()) Text("Write or paste your script…", style = MaterialTheme.typography.bodyLarge, color = Outline); inner() }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
