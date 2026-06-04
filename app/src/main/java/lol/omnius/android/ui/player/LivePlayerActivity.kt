package lol.omnius.android.ui.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import lol.omnius.android.handleVolumeKey
import lol.omnius.android.routeVolumeKeys
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class LivePlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private val handler = Handler(Looper.getMainLooper())

    // Channel list for up/down switching
    private var channelNames: Array<String> = emptyArray()
    private var channelUrls: Array<String> = emptyArray()
    private var currentIndex: Int = 0

    // UI
    private var channelLabel: TextView? = null
    private var loadingSpinner: ProgressBar? = null
    private var errorLabel: TextView? = null
    private var topGradient: View? = null
    private var hintLabel: TextView? = null

    private val hideOverlayRunnable = Runnable { hideOverlay() }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeVolumeKeys()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelNames = intent.getStringArrayExtra("channel_names") ?: emptyArray()
        channelUrls = intent.getStringArrayExtra("channel_urls") ?: emptyArray()
        currentIndex = intent.getIntExtra("channel_index", 0)
            .coerceIn(0, (channelUrls.size - 1).coerceAtLeast(0))

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        // Player view
        playerView = PlayerView(this).apply { useController = false }
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        // Loading spinner
        loadingSpinner = ProgressBar(this)
        root.addView(loadingSpinner, FrameLayout.LayoutParams(
            dp(48), dp(48), Gravity.CENTER,
        ))

        // Channel name overlay (top-left, with gradient)
        topGradient = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#AA000000"), Color.TRANSPARENT),
            )
        }
        root.addView(topGradient, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(100), Gravity.TOP,
        ))

        channelLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            visibility = View.GONE
        }
        root.addView(channelLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply { setMargins(dp(32), dp(28), dp(32), 0) })

        // Error label
        errorLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#E50914"))
            textSize = 14f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(errorLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        // Channel number + up/down hint (bottom-right)
        hintLabel = TextView(this).apply {
            text = "\u25B2 \u25BC  Switch channel"
            setTextColor(Color.parseColor("#66FFFFFF"))
            textSize = 11f
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        root.addView(hintLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END,
        ).apply { setMargins(0, 0, dp(24), dp(16)) })

        setContentView(root)

        if (channelUrls.isNotEmpty()) {
            playChannel(currentIndex)
        } else {
            errorLabel?.text = "No channels available"
            errorLabel?.visibility = View.VISIBLE
            loadingSpinner?.visibility = View.GONE
        }
    }

    @OptIn(UnstableApi::class)
    private fun playChannel(index: Int) {
        currentIndex = index.coerceIn(0, channelUrls.size - 1)

        val url = channelUrls[currentIndex]
        val name = channelNames.getOrElse(currentIndex) { "Channel ${currentIndex + 1}" }

        // Show channel name
        showChannelName(name)

        // Show loading
        loadingSpinner?.visibility = View.VISIBLE
        errorLabel?.visibility = View.GONE

        // Release old player
        player?.release()
        player = null

        val exo = ExoPlayer.Builder(this).build()
        playerView?.player = exo
        player = exo

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        loadingSpinner?.visibility = View.GONE
                        errorLabel?.visibility = View.GONE
                    }
                    Player.STATE_BUFFERING -> {
                        loadingSpinner?.visibility = View.VISIBLE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("LivePlayer", "Error: ${error.message}", error)
                loadingSpinner?.visibility = View.GONE
                errorLabel?.text = "Channel unavailable"
                errorLabel?.visibility = View.VISIBLE
            }
        })

        val mediaItem = MediaItem.fromUri(Uri.parse(url))
        exo.setMediaItem(mediaItem)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun showChannelName(name: String) {
        channelLabel?.text = name
        showOverlay()
    }

    private fun showOverlay() {
        handler.removeCallbacks(hideOverlayRunnable)
        listOf(channelLabel, topGradient, hintLabel).forEach { v ->
            v?.alpha = 1f
            v?.visibility = View.VISIBLE
        }
        handler.postDelayed(hideOverlayRunnable, 1000)
    }

    private fun hideOverlay() {
        listOf(channelLabel, topGradient, hintLabel).forEach { v ->
            v?.animate()?.alpha(0f)?.setDuration(300)
                ?.withEndAction { v.visibility = View.GONE }?.start()
        }
    }

    private fun switchChannel(delta: Int) {
        if (channelUrls.isEmpty()) return
        val newIndex = (currentIndex + delta).let {
            when {
                it < 0 -> channelUrls.size - 1
                it >= channelUrls.size -> 0
                else -> it
            }
        }
        playChannel(newIndex)
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleVolumeKey(keyCode)) return true
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { finish(); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { switchChannel(-1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { switchChannel(1); return true }
            else -> { showOverlay(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { super.onPause(); player?.pause() }
    override fun onResume() { super.onResume(); player?.play() }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
