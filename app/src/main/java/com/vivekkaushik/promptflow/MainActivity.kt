package com.vivekkaushik.promptflow

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.vivekkaushik.promptflow.core.data.Script
import com.vivekkaushik.promptflow.feature.editor.EditorScreen
import com.vivekkaushik.promptflow.feature.library.LibraryScreen
import com.vivekkaushik.promptflow.feature.overlay.OverlayService
import com.vivekkaushik.promptflow.feature.settings.SettingsScreen
import com.vivekkaushik.promptflow.feature.studio.StudioScreen
import com.vivekkaushik.promptflow.ui.theme.PromptFlowTheme

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PromptFlowTheme {
                val nav = rememberNavController()
                navController = nav
                AppNavHost(
                    nav = nav,
                    startOverlay = ::startOverlay,
                )
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    intent.getStringExtra(EXTRA_ROUTE)?.let { route -> nav.navigate(route) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { route -> navController?.navigate(route) }
    }

    /** Overlay launch flow: overlay permission → notification permission → start service, then background the app. */
    private fun startOverlay(script: Script) {
        Graph.engine.load(script)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow PromptFlow to display over other apps, then try again", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        OverlayService.start(this)
        Toast.makeText(this, "Overlay running — open your camera app", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    /**
     * Hardware remotes, in-app path (spec §04): BT clickers & keyboards arrive as key events.
     * Volume keys trim WPM, page/media/dpad keys drive transport — consumed only while a
     * prompter surface is active.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (Graph.hardwareKeysActive) {
            val engine = Graph.engine
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> { engine.nudgeWpm(10); return true }
                KeyEvent.KEYCODE_VOLUME_DOWN -> { engine.nudgeWpm(-10); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_DPAD_CENTER -> { engine.togglePlay(); return true }
                KeyEvent.KEYCODE_PAGE_DOWN,
                KeyEvent.KEYCODE_DPAD_DOWN -> { engine.togglePlay(); return true }
                KeyEvent.KEYCODE_PAGE_UP,
                KeyEvent.KEYCODE_DPAD_UP -> { engine.rewind(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

@Composable
private fun AppNavHost(nav: NavHostController, startOverlay: (Script) -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    NavHost(
        navController = nav,
        startDestination = "library",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        composable("library") {
            androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                LibraryScreen(
                    onOpenStudio = { script -> Graph.engine.load(script, script.progress); nav.navigate("studio") },
                    onOpenOverlay = startOverlay,
                    onEdit = { id -> nav.navigate("editor/$id") },
                    onSettings = { nav.navigate("settings") },
                )
            }
        }
        composable(
            "editor/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { backStack ->
            val id = backStack.arguments?.getLong("id") ?: return@composable
            androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                EditorScreen(
                    scriptId = id,
                    onBack = { nav.popBackStack() },
                    onOpenStudio = { script -> Graph.engine.load(script); nav.navigate("studio") },
                )
            }
        }
        composable("studio") {
            // Edge-to-edge, no safe padding — camera surface fills the screen (spec §02)
            StudioScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
}
