package com.vivekkaushik.promptflow.feature.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.media.VolumeProviderCompat
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.MainActivity
import com.vivekkaushik.promptflow.R
import kotlin.math.roundToInt

/**
 * Floating overlay (spec §01/§04): a system window above any third-party camera or
 * streaming app. Hosted in a foreground service (specialUse), ComposeView with its
 * own lifecycle owners — the window is NOT_FOCUSABLE so media keys arrive through a
 * MediaSession, and volume keys trim WPM via VolumeProviderCompat (playback-remote pattern).
 */
class OverlayService : LifecycleService(), ViewModelStoreOwner, SavedStateRegistryOwner {

    override val viewModelStore = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var mediaSession: MediaSessionCompat? = null

    private val density: Float get() = resources.displayMetrics.density

    /** Panel width in dp, applied inside Compose so the window can stay WRAP_CONTENT
     *  (a fixed window width would stretch the 56dp bubble into a pill). */
    private val widthDp = androidx.compose.runtime.mutableFloatStateOf(300f)

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification()
        if (overlayView == null) {
            addOverlay()
            setupMediaSession()
        }
        return START_STICKY
    }

    private fun addOverlay() {
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).roundToInt()
            y = (180 * density).roundToInt()
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            keepScreenOn = true
            setContent {
                OverlayContent(
                    widthDp = widthDp.floatValue,
                    onDrag = { dx, dy -> moveBy(dx, dy) },
                    onPinch = { zoom -> resizeBy(zoom) },
                    onOpenSettings = ::openAppSettings,
                    onClose = { stopSelf() },
                )
            }
        }
        overlayView = view
        windowManager.addView(view, params)
    }

    private fun moveBy(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        overlayView?.let { windowManager.updateViewLayout(it, params) }
    }

    /** Pinch anywhere resizes: min 200dp, max 90% of screen width (spec §02). */
    private fun resizeBy(zoom: Float) {
        val maxWDp = resources.displayMetrics.widthPixels * 0.9f / density
        widthDp.floatValue = (widthDp.floatValue * zoom).coerceIn(200f, maxWDp)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_ROUTE, "settings")
            }
        )
    }

    /** Media keys → play/pause; volume keys → live WPM trim (spec §04 hardware remotes). */
    private fun setupMediaSession() {
        val session = MediaSessionCompat(this, "PromptFlowOverlay")
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .build()
        )
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() { Graph.engine.togglePlay() }
            override fun onPause() { Graph.engine.togglePlay() }
        })
        session.setPlaybackToRemote(object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                if (direction != 0) Graph.engine.nudgeWpm(direction * 10)
            }
        })
        session.isActive = true
        mediaSession = session
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Teleprompter overlay", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PromptFlow overlay running")
            .setContentText("The prompter is floating above your apps.")
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onBind(intent: Intent) = super.onBind(intent)

    override fun onDestroy() {
        Graph.speechSync.stop()
        Graph.persistProgress()
        mediaSession?.release()
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 41
        const val ACTION_STOP = "com.vivekkaushik.promptflow.STOP_OVERLAY"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
