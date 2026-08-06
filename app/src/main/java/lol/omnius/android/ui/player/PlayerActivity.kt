package lol.omnius.android.ui.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
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
import lol.omnius.android.handleVolumeKey
import lol.omnius.android.routeVolumeKeys
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import lol.omnius.android.api.ApiClient
import lol.omnius.android.data.WatchHistoryManager
import lol.omnius.android.data.model.Subtitle
import lol.omnius.android.torrent.SubtitleFileInfo
import lol.omnius.android.torrent.TorrentDataSource
import lol.omnius.android.torrent.TorrentStreamManager
import lol.omnius.android.torrent.TorrentStreamState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var playerReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var stateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Buffer overlay
    private var bufferOverlay: FrameLayout? = null
    private var bufferStatusLabel: TextView? = null
    private var bufferProgressLabel: TextView? = null
    private var bufferStatsLabel: TextView? = null
    private var bufferStuckLabel: TextView? = null
    private var bufferProgressBar: ProgressBar? = null

    // Rebuffering spinner (center, shown when ExoPlayer buffers mid-playback)
    private var rebufferSpinner: ProgressBar? = null

    // Controls overlay
    private var controlsOverlay: FrameLayout? = null
    private var controlsTitleLabel: TextView? = null
    private var controlsTimeLabel: TextView? = null
    private var controlsPlayIcon: TextView? = null
    private var controlsSeekBar: SeekBar? = null
    private var controlsDurationLabel: TextView? = null
    private var controlsVisible = false
    private var seekTracking = false
    private var isTorrent = false

    // Torrent stats (inside controls overlay)
    private var torrentStatsLabel: TextView? = null

    // For error recovery
    private var currentUrl: String? = null
    private var currentTitle: String? = null
    private var retryCount = 0

    // Track pending seek to prevent seekbar from snapping back
    private var pendingSeekPosition: Long = -1

    // Watch history metadata
    private var contentId: Int = 0
    private var contentType: String = ""
    private var contentImage: String = ""
    private var seriesId: Int = 0
    private var seriesTitle: String = ""
    private var seasonNumber: Int = 0
    private var episodeNumber: Int = 0
    private var torrentHash: String = ""
    private var fileIndex: Int = -1

    // External subtitle files from torrent
    private var subtitleFiles: List<SubtitleFileInfo> = emptyList()

    // API subtitles (from OpenSubtitles via backend)
    private var apiSubtitles: List<Subtitle> = emptyList()

    // Track picker overlay
    private var trackPickerOverlay: FrameLayout? = null

    private val hideControlsRunnable = Runnable { hideControls() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateSeekProgress()
            handler.postDelayed(this, 1000)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeVolumeKeys()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra("title") ?: ""
        val directUrl = intent.getStringExtra("stream_url")
        val imdbCode = intent.getStringExtra("imdb_code") ?: ""
        isTorrent = intent.getBooleanExtra("is_torrent", false)

        contentId = intent.getIntExtra("content_id", 0)
        contentType = intent.getStringExtra("content_type") ?: ""
        contentImage = intent.getStringExtra("content_image") ?: ""
        seriesId = intent.getIntExtra("series_id", 0)
        seriesTitle = intent.getStringExtra("series_title") ?: ""
        seasonNumber = intent.getIntExtra("season_number", 0)
        episodeNumber = intent.getIntExtra("episode_number", 0)
        torrentHash = intent.getStringExtra("torrent_hash") ?: ""
        fileIndex = intent.getIntExtra("file_index", -1)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Player view
        playerView = PlayerView(this).apply {
            useController = false
        }
        root.addView(playerView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        // Rebuffering spinner (center of screen)
        rebufferSpinner = ProgressBar(this).apply {
            visibility = View.GONE
        }
        root.addView(rebufferSpinner, FrameLayout.LayoutParams(
            dp(48), dp(48), Gravity.CENTER,
        ))

        // Controls overlay
        buildControlsOverlay(root, title)

        // Buffer overlay (initial torrent loading)
        buildBufferOverlay(root, title)

        // Track picker overlay (CC / audio selection)
        trackPickerOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE
        }
        root.addView(trackPickerOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        setContentView(root)

        // Fetch API subtitles in background if we have an IMDB code
        if (imdbCode.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try {
                    val response = ApiClient.getApi().searchSubtitles(imdbCode)
                    apiSubtitles = response.subtitles
                    Log.i("Player", "Found ${apiSubtitles.size} API subtitles for $imdbCode")
                } catch (e: Exception) {
                    Log.w("Player", "Failed to fetch subtitles: ${e.message}")
                }
            }
        }

        if (isTorrent) {
            stateJob = scope.launch {
                TorrentStreamManager.state.collectLatest { state ->
                    updateBufferOverlay(state)
                    updateTorrentStats(state)
                    if (state.streamReady && player == null && state.streamUrl != null) {
                        subtitleFiles = state.subtitleFiles
                        startExoPlayer(state.streamUrl, title)
                    }
                }
            }
        } else if (!directUrl.isNullOrEmpty()) {
            bufferOverlay!!.visibility = View.GONE
            startExoPlayer(directUrl, title)
        } else {
            finish()
        }
    }

    private fun buildControlsOverlay(root: FrameLayout, title: String) {
        controlsOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
        }

        // Top gradient
        val topGradient = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT),
            )
        }
        controlsOverlay!!.addView(topGradient, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(120), Gravity.TOP,
        ))

        // Bottom gradient
        val bottomGradient = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(Color.parseColor("#CC000000"), Color.TRANSPARENT),
            )
        }
        controlsOverlay!!.addView(bottomGradient, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(160), Gravity.BOTTOM,
        ))

        // Title top-left
        controlsTitleLabel = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        controlsOverlay!!.addView(controlsTitleLabel, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply {
            setMargins(dp(32), dp(28), dp(180), 0)
        })

        // Torrent stats top-right
        if (isTorrent) {
            torrentStatsLabel = TextView(this).apply {
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
            }
            controlsOverlay!!.addView(torrentStatsLabel, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            ).apply {
                setMargins(0, dp(30), dp(32), 0)
            })
        }

        // Bottom controls — 2 rows:
        // Row 1: 00:00 ------O---------- 3:23:32
        // Row 2: |>                       CC  🔊
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), 0, dp(32), dp(24))
        }

        // Row 1: seekbar row
        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        controlsTimeLabel = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        seekRow.addView(controlsTimeLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(12) })

        // Seekbar: 3 layers — background (not downloaded), buffer (downloaded), progress (playhead)
        controlsSeekBar = SeekBar(this).apply {
            max = 1000
            progress = 0
            secondaryProgress = 0
            setPadding(0, 0, 0, 0)
            minimumHeight = dp(28)

            val r = dp(1).toFloat()
            val radii = floatArrayOf(r, r, r, r, r, r, r, r)

            val bgShape = ShapeDrawable(RoundRectShape(radii, null, null)).apply {
                paint.color = Color.parseColor("#33FFFFFF")
                intrinsicHeight = dp(2)
            }
            val bufferShape = ShapeDrawable(RoundRectShape(radii, null, null)).apply {
                paint.color = Color.parseColor("#66E50914")
                intrinsicHeight = dp(2)
            }
            val bufferClip = ClipDrawable(bufferShape, Gravity.START, ClipDrawable.HORIZONTAL)
            val progressShape = ShapeDrawable(RoundRectShape(radii, null, null)).apply {
                paint.color = Color.parseColor("#E50914")
                intrinsicHeight = dp(2)
            }
            val clip = ClipDrawable(progressShape, Gravity.START, ClipDrawable.HORIZONTAL)
            val trackInset = (dp(28) - dp(2)) / 2
            progressDrawable = LayerDrawable(arrayOf(bgShape, bufferClip, clip)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.secondaryProgress)
                setId(2, android.R.id.progress)
                setLayerInset(0, 0, trackInset, 0, trackInset)
                setLayerInset(1, 0, trackInset, 0, trackInset)
                setLayerInset(2, 0, trackInset, 0, trackInset)
            }

            val thumbDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(dp(14), dp(14))
                setColor(Color.parseColor("#E50914"))
            }
            thumb = thumbDrawable
            thumbOffset = 0

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = player?.duration ?: 0L
                        if (duration > 0) {
                            controlsTimeLabel?.text = formatTime((progress.toLong() * duration) / 1000)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    seekTracking = true
                    handler.removeCallbacks(hideControlsRunnable)
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekTracking = false
                    val duration = player?.duration ?: 0L
                    if (duration > 0 && seekBar != null) {
                        playerSeekTo((seekBar.progress.toLong() * duration) / 1000)
                    }
                    scheduleHideControls()
                }
            })
        }
        seekRow.addView(controlsSeekBar, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ))

        controlsDurationLabel = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        seekRow.addView(controlsDurationLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(12) })

        bottomContainer.addView(seekRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ))

        // Row 2: a vertical button column. UP/DOWN navigates these buttons;
        // LEFT/RIGHT is reserved for seeking (seekbar is display-only).
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        // Shared style for all control buttons: fixed size, red border on focus
        val btnSize = dp(40)
        val btnRadius = dp(6).toFloat()
        val redBorder = Color.parseColor("#E50914")
        val idleBorder = Color.parseColor("#55FFFFFF")

        fun styledButton(label: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isFocusable = true
                isFocusableInTouchMode = true
                background = GradientDrawable().apply {
                    cornerRadius = btnRadius
                    setStroke(dp(2), idleBorder)
                    setColor(Color.TRANSPARENT)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    (v.background as? GradientDrawable)?.apply {
                        setStroke(dp(2), if (hasFocus) redBorder else idleBorder)
                        setColor(if (hasFocus) Color.parseColor("#22E50914") else Color.TRANSPARENT)
                    }
                    (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#CCCCCC"))
                }
                setOnClickListener { onClick() }
            }
        }

        controlsPlayIcon = styledButton("\u25B6") {
            player?.let { it.playWhenReady = !it.playWhenReady }
            scheduleHideControls()
        }
        controlsRow.addView(controlsPlayIcon, LinearLayout.LayoutParams(btnSize, btnSize))

        // CC button
        val ccButton = styledButton("CC") { showTrackPicker(C.TRACK_TYPE_TEXT) }
        controlsRow.addView(ccButton, LinearLayout.LayoutParams(btnSize, btnSize).apply {
            topMargin = dp(8)
        })

        // Audio button — speaker icon
        val audioButton = styledButton("\u266A") { showTrackPicker(C.TRACK_TYPE_AUDIO) }
        controlsRow.addView(audioButton, LinearLayout.LayoutParams(btnSize, btnSize).apply {
            topMargin = dp(8)
        })

        // Seeking owns LEFT/RIGHT, so the seekbar is display-only (not focusable).
        // UP/DOWN walks the button column; clamp the ends so focus can't escape
        // the controls into the video surface.
        controlsSeekBar!!.isFocusable = false
        controlsSeekBar!!.isFocusableInTouchMode = false
        val play = controlsPlayIcon!!
        play.id = View.generateViewId()
        ccButton.id = View.generateViewId()
        audioButton.id = View.generateViewId()
        play.nextFocusUpId = play.id
        play.nextFocusDownId = ccButton.id
        ccButton.nextFocusUpId = play.id
        ccButton.nextFocusDownId = audioButton.id
        audioButton.nextFocusUpId = ccButton.id
        audioButton.nextFocusDownId = audioButton.id

        bottomContainer.addView(controlsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        controlsOverlay!!.addView(bottomContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))

        root.addView(controlsOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    private fun buildBufferOverlay(root: FrameLayout, title: String) {
        bufferOverlay = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), 0, dp(48), 0)
        }

        content.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(16) })

        bufferStatusLabel = TextView(this).apply {
            text = "Finding peers..."
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        content.addView(bufferStatusLabel)

        bufferProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = 0
            val r = dp(3).toFloat()
            val radii = floatArrayOf(r, r, r, r, r, r, r, r)
            val bg = ShapeDrawable(RoundRectShape(radii, null, null)).apply { paint.color = Color.parseColor("#333333") }
            val ps = ShapeDrawable(RoundRectShape(radii, null, null)).apply { paint.color = Color.parseColor("#E50914") }
            progressDrawable = LayerDrawable(arrayOf(bg, ClipDrawable(ps, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
                setId(0, android.R.id.background); setId(1, android.R.id.progress)
            }
        }
        content.addView(bufferProgressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6),
        ).apply { topMargin = dp(12); bottomMargin = dp(12) })

        bufferProgressLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA")); textSize = 12f; gravity = Gravity.CENTER
        }
        content.addView(bufferProgressLabel)

        bufferStatsLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#AAAAAA")); textSize = 12f; gravity = Gravity.CENTER
        }
        content.addView(bufferStatsLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) })

        bufferStuckLabel = TextView(this).apply {
            text = "Low peer count \u2014 this torrent may be slow. Try another quality."
            setTextColor(Color.parseColor("#FF8800")); textSize = 12f; gravity = Gravity.CENTER
            visibility = View.GONE
        }
        content.addView(bufferStuckLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(8) })

        bufferOverlay!!.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER,
        ))
        root.addView(bufferOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }

    @OptIn(UnstableApi::class)
    private fun startExoPlayer(url: String, title: String) {
        if (player != null) return

        currentUrl = url
        currentTitle = title

        val mediaUri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)

        // Limit buffer to prevent OOM on Mi Box (268MB heap)
        val loadControl = DefaultLoadControl.Builder()
            // Low-RAM (Light) build: smaller buffers so the player holds less in
            // memory on a 1 GB box.
            .setBufferDurationsMs(8_000, 24_000, 1_500, 3_000)
            .build()

        val builder = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
        if (isTorrent) {
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(TorrentDataSource.Factory()))
        }

        player = builder.build().also { exo ->
            playerView?.player = exo

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            rebufferSpinner?.visibility = View.GONE
                            pendingSeekPosition = -1
                            if (!playerReady) {
                                playerReady = true
                                bufferOverlay?.animate()
                                    ?.alpha(0f)?.setDuration(300)
                                    ?.withEndAction { bufferOverlay?.visibility = View.GONE }
                                    ?.start()
                                controlsDurationLabel?.text = formatTime(exo.duration)
                                handler.post(updateProgressRunnable)
                                showControls()
                            }
                        }
                        Player.STATE_BUFFERING -> {
                            if (playerReady) {
                                rebufferSpinner?.visibility = View.VISIBLE
                                // Keep controls visible during rebuffer so user sees seek position
                                if (pendingSeekPosition >= 0 && !controlsVisible) {
                                    showControls()
                                }
                                // Don't auto-hide while buffering
                                handler.removeCallbacks(hideControlsRunnable)
                            }
                        }
                        Player.STATE_ENDED -> {
                            rebufferSpinner?.visibility = View.GONE
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Show the action you'd take: pause while playing, play while paused.
                    controlsPlayIcon?.text = if (isPlaying) "\u23F8" else "\u25B6"
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("Player", "Playback error: ${error.message}", error)
                    if (isTorrent && retryCount < 3) {
                        retryCount++
                        Log.i("Player", "Retrying playback (attempt $retryCount)")
                        val pos = exo.currentPosition
                        handler.postDelayed({
                            player?.let {
                                it.seekTo(pos)
                                it.prepare()
                                it.playWhenReady = true
                            }
                        }, 1000)
                    }
                }
            })

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(mediaUri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder().setTitle(title).build()
                )

            // Collect all subtitle sources
            val subConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

            // 1. External subtitle files from torrent
            for (sub in subtitleFiles) {
                val subFile = File(sub.path)
                if (subFile.exists() && subFile.length() > 0) {
                    subConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                            .setMimeType(sub.mimeType)
                            .setLabel(sub.name)
                            .build()
                    )
                }
            }

            // 2. API subtitles (OpenSubtitles via backend)
            for (sub in apiSubtitles) {
                if (sub.downloadUrl.isNotEmpty()) {
                    val downloadUri = Uri.parse(ApiClient.subtitleDownloadUrl(sub.downloadUrl))
                    val label = buildString {
                        append(sub.languageName.ifEmpty { sub.language })
                        if (sub.hearingImpaired) append(" [CC]")
                        sub.releaseName?.let { r -> if (r.isNotEmpty()) append(" - $r") }
                    }
                    subConfigs.add(
                        MediaItem.SubtitleConfiguration.Builder(downloadUri)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLabel(label)
                            .setLanguage(sub.language)
                            .build()
                    )
                }
            }

            if (subConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subConfigs)
                Log.i("Player", "Added ${subConfigs.size} subtitle tracks (${subtitleFiles.size} torrent, ${apiSubtitles.size} API)")
            }

            exo.setMediaItem(mediaItemBuilder.build())
            exo.prepare()
            exo.playWhenReady = true

            // Resume from saved position
            if (contentId > 0 && contentType.isNotEmpty()) {
                val saved = WatchHistoryManager.getProgress(contentId, contentType)
                if (saved != null && saved.isContinueWatching) {
                    exo.seekTo(saved.positionMs)
                }
            }
        }

        bufferStatusLabel?.text = "Preparing playback..."
        bufferProgressBar?.progress = bufferProgressBar?.max ?: 1000
    }

    private fun showControls() {
        if (bufferOverlay?.visibility == View.VISIBLE) return
        handler.removeCallbacks(hideControlsRunnable)
        controlsOverlay?.let {
            it.visibility = View.VISIBLE
            it.animate().alpha(1f).setDuration(200).start()
            // On a fresh open, land on play/pause (top of the button column).
            // Don't steal focus if a control is already focused (e.g. controls
            // kept up on rebuffer).
            if (it.findFocus() == null) controlsPlayIcon?.requestFocus()
        }
        controlsVisible = true
        updateSeekProgress()
        scheduleHideControls()
    }

    private fun hideControls() {
        controlsOverlay?.animate()?.alpha(0f)?.setDuration(200)
            ?.withEndAction { controlsOverlay?.visibility = View.GONE }?.start()
        controlsVisible = false
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun playerSeekTo(positionMs: Long) {
        val exo = player ?: return
        val duration = exo.duration

        // Immediately show target position on seekbar (don't wait for ExoPlayer)
        pendingSeekPosition = positionMs
        if (duration > 0) {
            controlsSeekBar?.progress = ((positionMs * 1000) / duration).toInt()
            controlsTimeLabel?.text = formatTime(positionMs)
        }

        exo.seekTo(positionMs)
        if (isTorrent) TorrentStreamManager.seekTo(positionMs, exo.duration)
    }

    private fun updateSeekProgress() {
        if (seekTracking) return
        val exo = player ?: return
        val duration = exo.duration
        val position = exo.currentPosition
        if (duration > 0) {
            // Update download buffer (secondary progress) for torrent
            if (isTorrent) {
                val state = TorrentStreamManager.state.value
                if (state.totalSize > 0) {
                    val bufferPct = (state.downloaded.toDouble() / state.totalSize * 1000).toInt().coerceIn(0, 1000)
                    controlsSeekBar?.secondaryProgress = bufferPct
                }
            }

            // If a seek is pending, keep showing the target position
            if (pendingSeekPosition >= 0) {
                if (kotlin.math.abs(position - pendingSeekPosition) < 3000) {
                    pendingSeekPosition = -1
                } else {
                    controlsSeekBar?.progress = ((pendingSeekPosition * 1000) / duration).toInt()
                    controlsTimeLabel?.text = formatTime(pendingSeekPosition)
                    return
                }
            }
            controlsSeekBar?.progress = ((position * 1000) / duration).toInt()
            controlsTimeLabel?.text = formatTime(position)
            controlsDurationLabel?.text = formatTime(duration)
        }
    }

    private fun updateTorrentStats(state: TorrentStreamState) {
        if (!isTorrent) return
        val label = torrentStatsLabel ?: return
        val totalSize = state.totalSize
        val downloaded = state.downloaded
        val speed = state.downloadSpeed
        val peers = state.peers
        val pct = if (totalSize > 0) (downloaded.toDouble() / totalSize * 100).toInt().coerceIn(0, 100) else 0
        val speedStr = if (speed > 0) "${formatBytes(speed)}/s" else ""
        label.text = when {
            pct >= 100 -> "100%"
            speedStr.isNotEmpty() -> "$pct% \u2022 $speedStr \u2022 $peers peers"
            else -> "$pct% \u2022 $peers peers"
        }
    }

    private fun updateBufferOverlay(state: TorrentStreamState) {
        if (playerReady) return
        when (state.status) {
            "finding_peers" -> {
                bufferStatusLabel?.text = "Finding peers..."
                bufferProgressBar?.progress = 0
                bufferProgressLabel?.text = ""
                bufferStatsLabel?.text = ""
            }
            "buffering" -> {
                bufferStatusLabel?.text = "Buffering..."
                val downloaded = state.downloaded
                val speed = state.downloadSpeed
                val peers = state.peers
                val pReady = state.piecesReady
                val pTotal = state.piecesTotal
                val pct = if (pTotal > 0) (pReady.toFloat() / pTotal * 1000).toInt().coerceIn(0, 1000) else 0
                bufferProgressBar?.progress = pct
                bufferProgressLabel?.text = if (pTotal > 0) {
                    "$pReady / $pTotal pieces \u2022 ${formatBytes(downloaded)}"
                } else {
                    formatBytes(downloaded)
                }
                bufferStatsLabel?.text = "${if (speed > 0) "${formatBytes(speed)}/s" else "Connecting..."}  \u2022  $peers peers"
            }
            "error" -> {
                bufferStatusLabel?.text = state.error ?: "Error"
                bufferStatusLabel?.setTextColor(Color.parseColor("#E50914"))
            }
        }
        bufferStuckLabel?.visibility = if (state.isStuck) View.VISIBLE else View.GONE
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%d:%02d", m, sec)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @OptIn(UnstableApi::class)
    private fun showTrackPicker(trackType: Int) {
        val exo = player ?: return
        val overlay = trackPickerOverlay ?: return
        overlay.removeAllViews()

        val title = if (trackType == C.TRACK_TYPE_TEXT) "Subtitles" else "Audio"
        val tracks = exo.currentTracks

        data class TrackOption(val label: String, val groupIndex: Int, val trackIndex: Int, val isSelected: Boolean)

        val options = mutableListOf<TrackOption>()

        // "Off" option for subtitles
        if (trackType == C.TRACK_TYPE_TEXT) {
            val allSubsDisabled = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                .all { g -> (0 until g.length).none { g.isTrackSelected(it) } }
            options.add(TrackOption("Off", -1, -1, allSubsDisabled))
        }

        for (gi in 0 until tracks.groups.size) {
            val group = tracks.groups[gi]
            if (group.type != trackType) continue
            for (ti in 0 until group.length) {
                val format = group.getTrackFormat(ti)
                val label = buildString {
                    // Language
                    format.language?.let { lang ->
                        append(java.util.Locale(lang).displayLanguage)
                    }
                    // Label from format
                    format.label?.let { l ->
                        if (isNotEmpty()) append(" - ")
                        append(l)
                    }
                    // Codec info for audio
                    if (trackType == C.TRACK_TYPE_AUDIO) {
                        format.codecs?.let { c ->
                            if (isNotEmpty()) append(" (")
                            append(c)
                            append(")")
                        }
                        if (format.channelCount > 0) {
                            append(" ${format.channelCount}ch")
                        }
                    }
                    if (isEmpty()) append("Track ${options.size + 1}")
                }
                options.add(TrackOption(label, gi, ti, group.isTrackSelected(ti)))
            }
        }

        if (options.isEmpty() || (trackType == C.TRACK_TYPE_TEXT && options.size <= 1)) {
            // No tracks available (only "Off" for subs)
            options.clear()
            options.add(TrackOption("No ${title.lowercase()} tracks available", -1, -1, false))
        }

        // Build the picker UI with scrollable list
        val outerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
        }

        outerContainer.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(16) })

        // Scrollable list of track options
        val scrollView = android.widget.ScrollView(this).apply {
            isFocusable = false
            isVerticalScrollBarEnabled = true
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for ((idx, opt) in options.withIndex()) {
            val item = TextView(this).apply {
                text = if (opt.isSelected) "\u2022 ${opt.label}" else "  ${opt.label}"
                setTextColor(if (opt.isSelected) Color.parseColor("#E50914") else Color.WHITE)
                textSize = 16f
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isFocusable = true
                isFocusableInTouchMode = true
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.TRANSPARENT)
                }
                setOnFocusChangeListener { v, hasFocus ->
                    (v.background as? GradientDrawable)?.setColor(
                        if (hasFocus) Color.parseColor("#33FFFFFF") else Color.TRANSPARENT
                    )
                    // Auto-scroll to focused item
                    if (hasFocus) {
                        scrollView.post { scrollView.smoothScrollTo(0, v.top - dp(40)) }
                    }
                }
                setOnClickListener {
                    selectTrack(trackType, opt.groupIndex, opt.trackIndex)
                    dismissTrackPicker()
                }
            }
            listContainer.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            // Focus the selected item, or first item if none selected
            if (opt.isSelected || (idx == 0 && options.none { it.isSelected })) {
                item.post { item.requestFocus() }
            }
        }

        scrollView.addView(listContainer)
        outerContainer.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        overlay.addView(outerContainer, FrameLayout.LayoutParams(
            dp(400), FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_VERTICAL or Gravity.END,
        ).apply {
            marginEnd = dp(48)
            topMargin = dp(32)
            bottomMargin = dp(32)
        })

        overlay.visibility = View.VISIBLE
        hideControls()
        handler.removeCallbacks(hideControlsRunnable)
    }

    private fun dismissTrackPicker() {
        trackPickerOverlay?.visibility = View.GONE
        showControls()
    }

    @OptIn(UnstableApi::class)
    private fun selectTrack(trackType: Int, groupIndex: Int, trackIndex: Int) {
        val exo = player ?: return

        if (groupIndex == -1) {
            // "Off" — disable all tracks of this type
            val paramsBuilder = exo.trackSelectionParameters.buildUpon()
            if (trackType == C.TRACK_TYPE_TEXT) {
                paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            }
            exo.trackSelectionParameters = paramsBuilder.build()
            return
        }

        val tracks = exo.currentTracks
        if (groupIndex >= tracks.groups.size) return
        val group = tracks.groups[groupIndex]
        if (trackIndex >= group.length) return

        val paramsBuilder = exo.trackSelectionParameters.buildUpon()
        // Re-enable track type if it was disabled
        paramsBuilder.setTrackTypeDisabled(trackType, false)
        paramsBuilder.setOverrideForType(
            TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
        )
        exo.trackSelectionParameters = paramsBuilder.build()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (handleVolumeKey(keyCode)) return true
        // Track picker open — let system handle focus nav + clicks, only intercept back
        if (trackPickerOverlay?.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { dismissTrackPicker(); return true }
            return super.onKeyDown(keyCode, event)
        }

        // Buffer overlay — only back exits
        if (bufferOverlay?.visibility == View.VISIBLE) {
            return if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); true }
            else super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (controlsVisible) { hideControls(); return true }
                finish(); return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let { it.playWhenReady = !it.playWhenReady }
                if (!controlsVisible) showControls() else scheduleHideControls()
                return true
            }
            // Control scheme: LEFT/RIGHT always seek (+/-10s); UP/DOWN walk the
            // button column (play/pause -> CC -> audio); CENTER activates the
            // focused button. The seekbar isn't focusable, so seeking never
            // competes with button navigation.
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!controlsVisible) { showControls(); controlsPlayIcon?.requestFocus(); return true }
                scheduleHideControls()
                return super.onKeyDown(keyCode, event) // activate focused button
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!controlsVisible) { showControls(); controlsPlayIcon?.requestFocus(); return true }
                val pos = player?.currentPosition ?: 0
                playerSeekTo((pos - 10_000).coerceAtLeast(0))
                scheduleHideControls(); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!controlsVisible) { showControls(); controlsPlayIcon?.requestFocus(); return true }
                val pos = player?.currentPosition ?: 0
                val dur = player?.duration ?: Long.MAX_VALUE
                playerSeekTo((pos + 10_000).coerceAtMost(dur))
                scheduleHideControls(); return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!controlsVisible) { showControls(); controlsPlayIcon?.requestFocus(); return true }
                scheduleHideControls()
                return super.onKeyDown(keyCode, event) // walk button column
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!controlsVisible) { showControls(); controlsPlayIcon?.requestFocus(); return true }
                scheduleHideControls()
                return super.onKeyDown(keyCode, event) // walk button column
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        saveWatchProgress()
        player?.pause()
        handler.removeCallbacks(updateProgressRunnable)
    }

    private fun saveWatchProgress() {
        val exo = player ?: return
        if (contentId == 0 || contentType.isEmpty()) return
        val position = exo.currentPosition
        val duration = exo.duration
        if (duration <= 0) return
        WatchHistoryManager.saveProgress(
            contentId = contentId,
            contentType = contentType,
            title = currentTitle ?: "",
            image = contentImage,
            positionMs = position,
            durationMs = duration,
            seriesId = if (seriesId > 0) seriesId else null,
            seriesTitle = seriesTitle.ifEmpty { null },
            seasonNumber = if (seasonNumber > 0) seasonNumber else null,
            episodeNumber = if (episodeNumber > 0) episodeNumber else null,
            torrentHash = torrentHash.ifEmpty { null },
            fileIndex = if (fileIndex >= 0) fileIndex else null,
            imdbCode = intent.getStringExtra("imdb_code")?.ifEmpty { null },
        )
    }
    override fun onResume() { super.onResume(); player?.play(); if (playerReady) handler.post(updateProgressRunnable) }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        stateJob?.cancel(); scope.cancel()
        player?.release(); player = null
        TorrentStreamManager.stopStream()
    }
}
