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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vivekkaushik.promptflow.R
import com.vivekkaushik.promptflow.ui.components.PFIcon
import com.vivekkaushik.promptflow.ui.theme.Outline
import com.vivekkaushik.promptflow.ui.theme.OutlineVariant
import com.vivekkaushik.promptflow.ui.theme.PlexMono
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainer
import com.vivekkaushik.promptflow.ui.theme.SurfaceContainerHigh

private data class Library(val name: String, val author: String, val license: String, val url: String)

// Keep in sync with gradle/libs.versions.toml when dependencies change
private val LIBRARIES = listOf(
    Library("Jetpack Compose & Material 3", "Google", "Apache-2.0", "https://developer.android.com/jetpack/compose"),
    Library("AndroidX CameraX", "Google", "Apache-2.0", "https://developer.android.com/training/camerax"),
    Library("AndroidX Room", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/room"),
    Library("AndroidX Navigation Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    Library("AndroidX Lifecycle", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    Library("AndroidX DataStore", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    Library("AndroidX Core KTX", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/core"),
    Library("AndroidX Media", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/media"),
    Library("AndroidX DocumentFile", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/documentfile"),
    Library("Kotlin Standard Library", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    Library("kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("PdfBox-Android", "Tom Roush", "Apache-2.0", "https://github.com/TomRoush/PdfBox-Android"),
    Library("Sora typeface", "Sora Project Authors", "OFL-1.1", "https://fonts.google.com/specimen/Sora"),
    Library("IBM Plex Sans & Mono", "IBM", "OFL-1.1", "https://github.com/IBM/plex"),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
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
            Text("Open-source libraries", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp))
        }

        Text(
            "PromptFlow is built on the open-source software below. Tap a library to view its project page and license.",
            style = MaterialTheme.typography.bodyMedium, color = Outline,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        val uriHandler = LocalUriHandler.current
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainer).padding(vertical = 6.dp)) {
            LIBRARIES.forEachIndexed { i, lib ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { runCatching { uriHandler.openUri(lib.url) } }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(lib.name, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
                        Text(lib.author, style = MaterialTheme.typography.bodySmall, color = Outline)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(100.dp))
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(lib.license, fontFamily = PlexMono, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(8.dp))
                    PFIcon(R.drawable.ic_chevron, 13.dp, OutlineVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceContainer).padding(18.dp)) {
            Text("LICENSES", fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.5.sp, color = Outline)
            Spacer(Modifier.height(8.dp))
            Text(
                "PromptFlow itself is open source under the Apache License 2.0 — the full source is at " +
                    "github.com/iamvivekkaushik/PromptFlow.\n\n" +
                    "Apache License 2.0 — code libraries above are used under the Apache License, Version 2.0. " +
                    "You may obtain a copy at apache.org/licenses/LICENSE-2.0.\n\n" +
                    "SIL Open Font License 1.1 — the Sora and IBM Plex typefaces are used under the OFL, " +
                    "available at openfontlicense.org.",
                style = MaterialTheme.typography.bodySmall, color = Outline
            )
        }
    }
}
