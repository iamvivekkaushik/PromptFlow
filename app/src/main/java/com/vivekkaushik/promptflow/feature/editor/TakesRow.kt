package com.vivekkaushik.promptflow.feature.editor

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.R
import com.vivekkaushik.promptflow.core.data.Take
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.Record
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Horizontal strip of a script's saved Studio takes: tap to play, ✕ to delete (spec: Phase 1). */
@Composable
fun TakesRow(scriptId: Long, modifier: Modifier = Modifier) {
    val takes by remember(scriptId) { Graph.db.takes().observeForScript(scriptId) }
        .collectAsState(initial = emptyList())
    if (takes.isEmpty()) return

    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Take?>(null) }

    pendingDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = SurfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete this take?") },
            text = { Text("The video is deleted from your gallery too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.delete(Uri.parse(doomed.uri), null, null) }
                        }
                        Graph.db.takes().delete(doomed.id)
                    }
                    pendingDelete = null
                }) { Text("Delete", color = Record) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "TAKES · ${takes.size}",
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
            letterSpacing = 1.5.sp, color = Outline,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            takes.forEach { take -> TakeCard(take, onDelete = { pendingDelete = take }) }
        }
    }
}

@Composable
private fun TakeCard(take: Take, onDelete: () -> Unit) {
    val context = LocalContext.current
    // null = loading, Result(null bitmap) = file missing
    val thumb by produceState<Result<Bitmap?>?>(initialValue = null, take.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, Uri.parse(take.uri))
                    r.getFrameAtTime(0)
                }
            }
        }
    }
    val missing = thumb?.isFailure == true || (thumb?.isSuccess == true && thumb?.getOrNull() == null)

    Column(Modifier.width(120.dp)) {
        Box(
            Modifier.size(120.dp, 68.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceContainerHigh)
                .clickable(enabled = !missing) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(take.uri), "video/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    }
                }
        ) {
            thumb?.getOrNull()?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp, 68.dp))
            }
            if (missing) {
                Text(
                    "file missing",
                    fontFamily = PlexMono, fontSize = 10.sp, color = Outline,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Box(Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    PFIcon(R.drawable.ic_play, 12.dp, Color.White)
                }
                Text(
                    DateUtils.formatElapsedTime(take.durationMs / 1000),
                    fontFamily = PlexMono, fontSize = 10.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { PFIcon(R.drawable.ic_delete, 10.dp, Color.White) }
        }
        Text(
            "${take.quality} · ${take.fps}fps · ${DateUtils.getRelativeTimeSpanString(take.createdAt).toString().lowercase()}",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = Outline,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
