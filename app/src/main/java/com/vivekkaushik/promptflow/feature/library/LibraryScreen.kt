package com.vivekkaushik.promptflow.feature.library

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vivekkaushik.promptflow.core.data.Script
import com.vivekkaushik.promptflow.ui.theme.Lime
import com.vivekkaushik.promptflow.ui.theme.LimeContainer
import com.vivekkaushik.promptflow.ui.theme.OnLime
import com.vivekkaushik.promptflow.ui.theme.OnLimeContainer
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.OutlineVariant
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.Sky
import com.vivekkaushik.promptflow.ui.theme.Sora
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainer
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh
import com.vivekkaushik.promptflow.ui.theme.Warning
import com.vivekkaushik.promptflow.ui.theme.Record
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.R

private fun estTime(words: Int, wpm: Int): String {
    if (words <= 0) return "0 s"
    val totalSec = (words * 60.0 / wpm).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "$m min ${if (s < 10) "0" else ""}$s s" else "$s s"
}

/** Script Library — bento dashboard (spec §01). */
@Composable
fun LibraryScreen(
    onOpenStudio: (Script) -> Unit,
    onOpenOverlay: (Script) -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
    onViewAll: () -> Unit,
) {
    val vm: LibraryViewModel = viewModel()
    val scripts by vm.scripts.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val settings by vm.settings.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.import(it) { id -> onEdit(id) } }
    }

    val continueScript = scripts.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PFIcon(R.drawable.ic_mark, 26.dp, Lime)
                Text("PromptFlow", style = MaterialTheme.typography.headlineMedium)
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(SurfaceContainerHigh).clickable(onClick = onSettings),
                contentAlignment = Alignment.Center
            ) { PFIcon(R.drawable.ic_tune, 19.dp, MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        // Search pill
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceContainer)
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PFIcon(R.drawable.ic_search, 18.dp, Outline)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Lime),
                    modifier = Modifier.fillMaxWidth()
                )
                if (query.isEmpty()) Text("Search scripts…", style = MaterialTheme.typography.bodyLarge, color = Outline)
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(SurfaceContainerHigh)
                        .clickable { vm.setQuery("") },
                    contentAlignment = Alignment.Center
                ) { Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Continue card
        if (continueScript != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(LimeContainer)
                    .clickable { onOpenStudio(continueScript) }
                    .padding(20.dp)
            ) {
                Text("CONTINUE", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.5.sp, color = Lime)
                Spacer(Modifier.height(8.dp))
                Text(continueScript.title, style = MaterialTheme.typography.titleMedium, color = OnLimeContainer)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${continueScript.wordCount} words · est. ${estTime(continueScript.wordCount, settings.wpm)} · edited ${DateUtils.getRelativeTimeSpanString(continueScript.updatedAt).toString().lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFC9CDB8)
                )
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.35f))) {
                    Box(Modifier.fillMaxWidth(continueScript.progress.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lime))
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.clip(RoundedCornerShape(100.dp)).background(Lime)
                            .clickable { onOpenStudio(continueScript) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            PFIcon(R.drawable.ic_play, 15.dp, OnLime)
                            Text("Resume in Studio", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OnLime)
                        }
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(100.dp))
                            .background(Color.Transparent)
                            .border1(OnLimeContainer.copy(alpha = 0.4f))
                            .clickable { onOpenOverlay(continueScript) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) { Text("Overlay", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = OnLimeContainer) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // New script / Import
        Row(
            Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCard(Modifier.weight(1f).fillMaxHeight(), onClick = { vm.newScript { id -> onEdit(id) } }) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Lime),
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_plus, 22.dp, OnLime) }
                Spacer(Modifier.height(12.dp))
                Text("New script", style = MaterialTheme.typography.titleSmall)
                Text("Blank draft", style = MaterialTheme.typography.bodySmall, color = Outline)
            }
            BentoCard(Modifier.weight(1f).fillMaxHeight(), onClick = {
                importLauncher.launch(com.vivekkaushik.promptflow.core.importer.ScriptImporter.MIME_TYPES)
            }) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) { PFIcon(R.drawable.ic_import, 21.dp, Lime) }
                Spacer(Modifier.height(12.dp))
                Text("Import", style = MaterialTheme.typography.titleSmall)
                Text(".txt .docx .pdf .md · from device", style = MaterialTheme.typography.bodySmall, color = Outline)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Recent list
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(SurfaceContainer).padding(vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "RECENT",
                    fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.5.sp,
                    color = Outline,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(onClick = onViewAll)
                ) {
                    Text("View all", style = MaterialTheme.typography.labelMedium, color = Lime)
                    PFIcon(R.drawable.ic_chevron, 13.dp, Lime)
                }
            }
            if (scripts.isEmpty()) {
                Text(
                    if (query.isBlank()) "No scripts yet — create or import one." else "No matches for \"$query\".",
                    style = MaterialTheme.typography.bodyMedium, color = Outline,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                )
            }
            val tints = listOf(Lime, Warning, Sky)
            scripts.take(6).forEachIndexed { i, script ->
                val tint = tints[i % tints.size]
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(script.id) }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(script.title.firstOrNull()?.uppercase() ?: "S", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = tint)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(script.title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1)
                        Text(
                            "${script.wordCount} words · ${estTime(script.wordCount, settings.wpm)}",
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(100.dp)).border1(OutlineVariant).padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(script.source, fontFamily = PlexMono, fontSize = 11.sp, color = tint)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Quick record / Typography shortcuts
        Row(
            Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoCard(Modifier.weight(1f).fillMaxHeight(), onClick = { continueScript?.let(onOpenStudio) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PFIcon(R.drawable.ic_record, 17.dp, Record)
                    Text("Quick record", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text("Studio · 4K · prompter on", style = MaterialTheme.typography.bodySmall, color = Outline)
            }
            BentoCard(Modifier.weight(1f).fillMaxHeight(), onClick = onSettings) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PFIcon(R.drawable.ic_type, 18.dp, Lime)
                    Text("Typography", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text("Size, weight, colors, mirror", style = MaterialTheme.typography.bodySmall, color = Outline)
            }
        }
    }
}

@Composable
private fun BentoCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
        content = content
    )
}

private fun Modifier.border1(color: Color): Modifier =
    this.then(Modifier.border(1.dp, color, RoundedCornerShape(100.dp)))
