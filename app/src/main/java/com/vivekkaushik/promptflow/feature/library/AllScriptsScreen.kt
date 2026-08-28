package com.vivekkaushik.promptflow.feature.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.R

private val FILTERS = listOf("All", "Drafts", "Recorded")

/** All Scripts (design v2): full searchable, filterable list of every script on device. */
@Composable
fun AllScriptsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenStudio: (Script) -> Unit,
) {
    val vm: LibraryViewModel = viewModel()
    val scripts by vm.scripts.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val settings by vm.settings.collectAsState()
    var filter by remember { mutableStateOf("All") }

    val filtered = when (filter) {
        "Drafts" -> scripts.filter { !it.recorded }
        "Recorded" -> scripts.filter { it.recorded }
        else -> scripts
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(SurfaceContainer).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { PFIcon(R.drawable.ic_back, 19.dp, MaterialTheme.colorScheme.onSurface) }
            Text("All scripts", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            Text("${filtered.size} ${if (filtered.size == 1) "script" else "scripts"} · on device", fontFamily = PlexMono, fontSize = 11.sp, color = Outline)
        }

        // Search pill
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceContainer)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PFIcon(R.drawable.ic_search, 17.dp, Outline)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Lime),
                    modifier = Modifier.fillMaxWidth()
                )
                if (query.isEmpty()) Text("Search scripts…", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = Outline)
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(SurfaceContainerHigh)
                        .clickable { vm.setQuery("") },
                    contentAlignment = Alignment.Center
                ) { Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FILTERS.forEach { name ->
                val selected = filter == name
                Box(
                    Modifier.clip(RoundedCornerShape(100.dp))
                        .border(1.dp, if (selected) LimeContainer else OutlineVariant, RoundedCornerShape(100.dp))
                        .background(if (selected) LimeContainer else Color.Transparent)
                        .clickable { filter = name }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) OnLimeContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        if (filtered.isEmpty()) {
            Text(
                if (query.isBlank()) "Nothing here yet." else "No matches for \"$query\".",
                style = MaterialTheme.typography.bodyMedium, color = Outline,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // Script rows
        var pendingDelete by remember { mutableStateOf<Script?>(null) }
        pendingDelete?.let { doomed ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = SurfaceContainerHigh,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = { Text("Delete \"${doomed.title}\"?") },
                text = { Text("The script is removed from this device. Recordings in your gallery are not affected.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        vm.delete(doomed.id)
                        pendingDelete = null
                    }) { Text("Delete", color = com.vivekkaushik.promptflow.ui.theme.Record) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val tints = listOf(Lime, Warning, Sky)
            filtered.forEachIndexed { i, script ->
                val tint = tints[i % tints.size]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceContainer)
                        .clickable { onEdit(script.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(script.title.firstOrNull()?.uppercase() ?: "S", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = tint)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(script.title, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1)
                        Text(
                            "${script.wordCount} words · ${estTime(script.wordCount, settings.wpm)}",
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            DateUtils.getRelativeTimeSpanString(script.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                            fontFamily = PlexMono, fontSize = 11.sp, color = Outline
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Manual recorded/draft toggle — also set automatically when a Studio take saves
                            Box(
                                Modifier.clip(RoundedCornerShape(100.dp))
                                    .border(1.dp, if (script.recorded) Lime else OutlineVariant, RoundedCornerShape(100.dp))
                                    .clickable { vm.setRecorded(script.id, !script.recorded) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    if (script.recorded) "✓ Rec" else "Draft",
                                    fontFamily = PlexMono, fontSize = 11.sp,
                                    color = if (script.recorded) Lime else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(100.dp)).background(Lime)
                                    .clickable { onOpenStudio(script) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    PFIcon(R.drawable.ic_play, 12.dp, OnLime)
                                    Text("Play", fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = OnLime)
                                }
                            }
                            Box(
                                Modifier.size(26.dp).clip(CircleShape)
                                    .border(1.dp, OutlineVariant, CircleShape)
                                    .clickable { pendingDelete = script },
                                contentAlignment = Alignment.Center
                            ) {
                                PFIcon(R.drawable.ic_delete, 13.dp, MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun estTime(words: Int, wpm: Int): String {
    if (words <= 0) return "0 s"
    val totalSec = (words * 60.0 / wpm).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "$m min ${if (s < 10) "0" else ""}$s s" else "$s s"
}
