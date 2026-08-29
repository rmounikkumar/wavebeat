package com.wavebeat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@androidx.annotation.OptIn(UnstableApi::class)
class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private var rotationProcessor: RotationAudioProcessor? = null
    private var lastActionFactory: MediaNotification.ActionFactory? = null
    private var lastSession: MediaSession? = null
    private var consecutiveErrors = 0

    private val prefs by lazy { getSharedPreferences("wavebeat_state", MODE_PRIVATE) }
    private val stateSaver = object : Runnable {
        override fun run() {
            savePlaybackState()
            handler.postDelayed(this, 10_000)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sleepTask: Runnable? = null
    private var sleepRemainingMs: Long = 0L
    private var sleepEndOfSong = false

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: EnvironmentalReverb? = null
    private var loudness: LoudnessEnhancer? = null
    private var attachedSessionId = 0

    private var notificationArt: Bitmap? = null
    private val artExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    companion object {
        const val CHANNEL_ID = "wavebeat_channel"
        const val NOTIFICATION_ID = 1

        var audioSessionId: Int = 0
            private set
        var onSongChanged: ((title: String, artist: String) -> Unit)? = null
        var onPlaybackError: (() -> Unit)? = null
        var onSleepTimerUpdated: ((label: String, active: Boolean) -> Unit)? = null

        @Volatile
        var autoNextEnabled = true
        @Volatile
        var hapticsEnabled = true
        @Volatile
        var resumeEnabled = true
        @Volatile
        private var nextIntent = false

        fun setAutoNext(enabled: Boolean) {
            autoNextEnabled = enabled
        }

        fun setHaptics(enabled: Boolean) {
            hapticsEnabled = enabled
        }

        fun setAutoResume(enabled: Boolean) {
            resumeEnabled = enabled
        }

        fun markNextIntent() {
            nextIntent = true
        }

        const val EQ_PRESET_FLAT = "Flat"
        const val EQ_PRESET_POP = "Pop"
        const val EQ_PRESET_ROCK = "Rock"
        const val EQ_PRESET_JAZZ = "Jazz"
        const val EQ_PRESET_BASS = "Bass Boost"
        const val EQ_PRESET_TREBLE = "Treble"
        const val EQ_PRESET_VOCAL = "Vocal"

        private const val DEFAULT_PRESET = EQ_PRESET_FLAT
        private var eqPreset = DEFAULT_PRESET
        private var eqIntensity = 50
        private var is8D = false
        private var bassStrength = 0
        private var virtStrength = 0
        private var reverbEnabled = false
        private var loudnessEnabled = false

        private var instance: MusicService? = null

        fun instanceOrNull(): MusicService? = instance

        fun setEqualizerPreset(preset: String) {
            eqPreset = preset
            instance?.applyEffects()
        }

        fun setEqualizerIntensity(value: Int) {
            eqIntensity = value.coerceIn(0, 100)
            instance?.applyEffects()
        }

        fun set8D(enabled: Boolean) {
            is8D = enabled
            instance?.let {
                it.rotationProcessor?.enabled = enabled
                it.applyEffects()
            }
        }

        fun setBassStrength(value: Int) {
            bassStrength = value.coerceIn(0, 1000)
            instance?.applyEffects()
        }

        fun setVirtualizerStrength(value: Int) {
            virtStrength = value.coerceIn(0, 1000)
            instance?.applyEffects()
        }

        fun setReverb(enabled: Boolean) {
            reverbEnabled = enabled
            instance?.applyEffects()
        }

        fun setLoudness(enabled: Boolean) {
            loudnessEnabled = enabled
            instance?.applyEffects()
        }

        fun currentSettings(): Map<String, Any> = mapOf(
            "preset" to eqPreset,
            "intensity" to eqIntensity,
            "8d" to is8D,
            "bass" to bassStrength,
            "virtualizer" to virtStrength,
            "reverb" to reverbEnabled,
            "loudness" to loudnessEnabled
        )

        fun setSleepTimer(minutes: Int, endOfSong: Boolean) {
            instance?.setupSleepTimer(minutes, endOfSong)
        }

        fun cancelSleepTimer() {
            instance?.setupSleepTimer(-1, false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        autoNextEnabled = prefs.getBoolean("auto_next", true)
        hapticsEnabled = prefs.getBoolean("haptics", true)
        resumeEnabled = prefs.getBoolean("auto_resume", false)
        createNotificationChannel()
        initPlayer()
    }

    private fun initPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20000, 60000, 1500, 2000)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this))

        val rotation = RotationAudioProcessor().apply { enabled = is8D }
        rotationProcessor = rotation

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(RotationRenderersFactory(this, rotation))
            .build()
            .apply {
                playWhenReady = false
                repeatMode = Player.REPEAT_MODE_ALL

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) savePlaybackState()
                        updateNotification()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                consecutiveErrors = 0
                                updateNotification()
                                updateCurrentSongInfo()
                            }
                            Player.STATE_BUFFERING -> updateNotification()
                            Player.STATE_ENDED -> {
                                if (sleepEndOfSong) {
                                    sleepRemainingMs = 0
                                    sleepEndOfSong = false
                                    sleepTask?.let { handler.removeCallbacks(it) }
                                    sleepTask = null
                                    sleepUpdateLabel(active = false)
                                    updateNotification()
                                }
                            }
                            else -> {}
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        consecutiveErrors = 0
                        val p = player
                        val manual = nextIntent
                        nextIntent = false
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && !manual && !autoNextEnabled) {
                            p?.pause()
                        }
                        if (hapticsEnabled) {
                            vibrateTick()
                        }
                        if (p != null && p.mediaItemCount > 0) {
                            savePlaylistSnapshot(
                                (0 until p.mediaItemCount).map { p.getMediaItemAt(it) },
                                p.currentMediaItemIndex
                            )
                        }
                        savePlaybackState()
                        updateNotification()
                        updateCurrentSongInfo()
                        loadNotificationArt(mediaItem?.localConfiguration?.uri)
                        if (sleepEndOfSong && sleepRemainingMs > 0 &&
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                        ) {
                            pauseForSleepTimer()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        consecutiveErrors++
                        if (consecutiveErrors > 3) {
                            pause()
                            consecutiveErrors = 0
                            onPlaybackError?.invoke()
                            return
                        }
                        if (mediaItemCount > 0 && currentMediaItemIndex < mediaItemCount - 1) {
                            seekToNextMediaItem()
                        } else if (mediaItemCount > 1) {
                            seekToDefaultPosition(0)
                        }
                    }
                })
            }

        audioSessionId = player!!.audioSessionId
        attachAudioEffects()

        mediaSession = MediaSession.Builder(this, player!!).build()
        handler.post(stateSaver)
        restorePlaybackState()
        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                session: MediaSession,
                commandButtons: com.google.common.collect.ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                callback: MediaNotification.Provider.Callback
            ): MediaNotification {
                lastActionFactory = actionFactory
                lastSession = session
                return MediaNotification(NOTIFICATION_ID, buildNotification())
            }

            override fun handleCustomCommand(
                session: MediaSession,
                customAction: String,
                extras: android.os.Bundle
            ): Boolean {
                return false
            }
        })
    }

    fun loadPlaylist(items: List<MediaItem>, startIndex: Int) {
        player?.let { p ->
            p.setMediaItems(items, startIndex, 0)
            p.prepare()
            p.play()
            savePlaylistSnapshot(items, startIndex)
            savePlaybackState()
            updateNotification()
        }
    }

    private fun savePlaylistSnapshot(items: List<MediaItem>, startIndex: Int) {
        if (items.isEmpty()) return
        val arr = JSONArray()
        for (item in items) {
            val uri = item.localConfiguration?.uri?.toString() ?: continue
            val obj = JSONObject().apply {
                put("u", uri)
                put("t", item.mediaMetadata.title?.toString() ?: "")
                put("a", item.mediaMetadata.artist?.toString() ?: "")
            }
            arr.put(obj)
        }
        if (arr.length() > 0) {
            prefs.edit().putString("playlist", arr.toString()).putInt("index", startIndex).apply()
        }
    }

    private fun savePlaybackState() {
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        prefs.edit()
            .putInt("index", p.currentMediaItemIndex)
            .putLong("position", p.currentPosition)
            .putBoolean("playing", p.playWhenReady)
            .apply()
    }

    private fun restorePlaybackState() {
        val p = player ?: return
        if (p.mediaItemCount > 0) return
        val json = prefs.getString("playlist", null) ?: return
        val pIndex = prefs.getInt("index", 0)
        val position = prefs.getLong("position", 0L)
        val wasPlaying = prefs.getBoolean("playing", false)
        try {
            val arr = JSONArray(json)
            val items = mutableListOf<MediaItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uri = Uri.parse(obj.getString("u"))
                items.add(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(obj.optString("t"))
                                .setArtist(obj.optString("a"))
                                .build()
                        )
                        .build()
                )
            }
            if (items.isEmpty()) return
            val start = pIndex.coerceIn(0, items.size - 1)
            p.setMediaItems(items, start, position.coerceAtLeast(0L))
            p.prepare()
            if (wasPlaying && resumeEnabled) p.play() else p.pause()
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCurrentSongInfo() {
        val p = player ?: return
        if (p.mediaItemCount > 0 && p.currentMediaItemIndex < p.mediaItemCount) {
            val item = p.currentMediaItem
            val title = item?.mediaMetadata?.title?.toString() ?: "Unknown"
            val artist = item?.mediaMetadata?.artist?.toString() ?: "Unknown"
            onSongChanged?.invoke(title, artist)
        }
    }

    // ---------- Audio effects ----------

    private fun attachAudioEffects() {
        val sessionId = audioSessionId
        if (sessionId == 0) return
        if (attachedSessionId != 0 && attachedSessionId == sessionId) return

        releaseEffects()

        try {
            equalizer = Equalizer(0, sessionId)
            bassBoost = BassBoost(0, sessionId)
            virtualizer = Virtualizer(0, sessionId)
            try {
                reverb = EnvironmentalReverb(0, sessionId)
            } catch (_: Exception) {
                reverb = null
            }
            try {
                loudness = LoudnessEnhancer(sessionId)
            } catch (_: Exception) {
                loudness = null
            }
            attachedSessionId = sessionId
            applyEffects()
        } catch (e: Exception) {
            releaseEffects()
            attachedSessionId = 0
        }
    }

    private fun releaseEffects() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
        virtualizer?.release(); virtualizer = null
        reverb?.release(); reverb = null
        loudness?.release(); loudness = null
        attachedSessionId = 0
    }

    private fun applyEffects() {
        attachedSessionId = audioSessionId
        if (attachedSessionId == 0) return

        val eq = equalizer
        if (eq != null) {
            try {
                eq.enabled = true
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val lo = range[0]
                val hi = range[1]
                val levels = presetLevels(eqPreset, numBands)
                val baseFactor = 0.2f + (eqIntensity / 100f) * 1.4f
                for (i in 0 until numBands) {
                    val level = levels[i] * baseFactor
                    val clamped = level.coerceIn(lo.toFloat(), hi.toFloat()).toInt().toShort()
                    eq.setBandLevel(i.toShort(), clamped)
                }
            } catch (_: Exception) {}
        }

        val bass = bassBoost
        if (bass != null) {
            try {
                bass.enabled = bassStrength > 0
                if (bassStrength > 0) bass.setStrength(bassStrength.toShort())
            } catch (_: Exception) {}
        }

        val virt = virtualizer
        if (virt != null) {
            try {
                virt.enabled = virtStrength > 0
                if (virtStrength > 0) virt.setStrength(virtStrength.toShort())
            } catch (_: Exception) {}
        }

        val rev = reverb
        if (rev != null) {
            try {
                rev.enabled = reverbEnabled
                if (reverbEnabled) {
                    rev.decayTime = 1800
                    rev.roomLevel = 300
                    rev.density = 1000
                    rev.diffusion = 600
                }
            } catch (_: Exception) {}
        }

        val loud = loudness
        if (loud != null) {
            try {
                loud.enabled = loudnessEnabled
                if (loudnessEnabled) loud.setTargetGain(600)
            } catch (_: Exception) {}
        }
    }

    private fun presetLevels(preset: String, numBands: Int): FloatArray {
        val base = when (preset) {
            EQ_PRESET_POP -> floatArrayOf(-300f, 180f, 500f, 250f, -120f)
            EQ_PRESET_ROCK -> floatArrayOf(400f, 180f, -120f, 180f, 400f)
            EQ_PRESET_JAZZ -> floatArrayOf(300f, 120f, -100f, 120f, 380f)
            EQ_PRESET_BASS -> floatArrayOf(600f, 420f, 120f, 0f, 0f)
            EQ_PRESET_TREBLE -> floatArrayOf(0f, 0f, 120f, 400f, 620f)
            EQ_PRESET_VOCAL -> floatArrayOf(-120f, 200f, 380f, 200f, -120f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }
        return if (numBands <= 0) base else interpolate(base, numBands)
    }

    private fun interpolate(base: FloatArray, n: Int): FloatArray {
        if (n == base.size) return base.copyOf()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val pos = i.toFloat() / (n - 1) * (base.size - 1)
            val idx = pos.toInt().coerceIn(0, base.size - 2)
            val frac = pos - idx
            out[i] = base[idx] + (base[idx + 1] - base[idx]) * frac
        }
        return out
    }

    // ---------- Sleep timer ----------

    private fun setupSleepTimer(minutes: Int, endOfSong: Boolean) {
        sleepTask?.let { handler.removeCallbacks(it) }
        sleepTask = null
        sleepEndOfSong = false

        if (endOfSong) {
            sleepEndOfSong = true
            sleepRemainingMs = Long.MAX_VALUE
            sleepUpdateLabel(active = true)
            updateNotification()
            return
        }

        if (minutes <= 0) {
            sleepRemainingMs = 0
            sleepUpdateLabel(active = false)
            updateNotification()
            return
        }

        sleepRemainingMs = minutes * 60_000L
        val task = Runnable {
            pauseForSleepTimer()
            sleepRemainingMs = 0
            sleepTask = null
            sleepUpdateLabel(active = false)
        }
        sleepTask = task
        handler.postDelayed(task, sleepRemainingMs)
        sleepUpdateLabel(active = true)
    }

    private fun pauseForSleepTimer() {
        player?.pause()
        sleepRemainingMs = 0
        sleepEndOfSong = false
        sleepTask?.let { handler.removeCallbacks(it) }
        sleepTask = null
        updateNotification()
    }

    private fun sleepUpdateLabel(active: Boolean) {
        val label = when {
            !active -> ""
            sleepEndOfSong -> "Stop at end of song"
            sleepRemainingMs >= 60 * 60_000L -> "${sleepRemainingMs / 3600_000}h"
            else -> "${sleepRemainingMs / 60_000}m"
        }
        onSleepTimerUpdated?.invoke(label, label.isNotEmpty())
    }

    // ---------- Notification ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WaveBeat Music",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = player?.isPlaying ?: false
        val currentItem = player?.currentMediaItem
        val title = currentItem?.mediaMetadata?.title?.toString() ?: "WaveBeat"
        val artist = currentItem?.mediaMetadata?.artist?.toString() ?: ""
        val subtitle = if (artist.isNotEmpty()) artist else if (isPlaying) "Playing" else "Paused"

        val duration = player?.duration ?: 0L
        val position = player?.currentPosition ?: 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wavebeat)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        addMediaAction(builder, 1, R.drawable.ic_stat_prev, "Previous", Player.COMMAND_SEEK_TO_PREVIOUS)
        addMediaAction(
            builder,
            2,
            if (isPlaying) R.drawable.ic_stat_pause else R.drawable.ic_stat_play,
            if (isPlaying) "Pause" else "Play",
            Player.COMMAND_PLAY_PAUSE
        )
        addMediaAction(builder, 3, R.drawable.ic_stat_next, "Next", Player.COMMAND_SEEK_TO_NEXT)

        notificationArt?.let {
            builder.setLargeIcon(it)
        }

        if (sleepRemainingMs > 0) {
            val sub = if (sleepEndOfSong) {
                "Sleep timer: Stop at end of song"
            } else if (sleepRemainingMs >= 60 * 60_000L) {
                "Sleep timer: ${sleepRemainingMs / 3600_000}h"
            } else {
                "Sleep timer: ${sleepRemainingMs / 60000}m"
            }
            builder.setSubText(sub)
        }

        if (duration > 0) {
            builder.setProgress(duration.toInt(), position.toInt(), false)
        }

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(false)
        )

        return builder.build()
    }

    private fun addMediaAction(
        builder: NotificationCompat.Builder,
        index: Int,
        iconRes: Int,
        label: String,
        playerCommand: Int
    ) {
        val factory = lastActionFactory
        val session = lastSession
        if (factory != null && session != null) {
            builder.addAction(
                factory.createMediaAction(
                    session,
                    androidx.core.graphics.drawable.IconCompat.createWithResource(this, iconRes),
                    label,
                    playerCommand
                )
            )
        } else {
            // Fallback: directly target the manifest receiver so the broadcast is explicit.
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setComponent(android.content.ComponentName(this@MusicService, androidx.media.session.MediaButtonReceiver::class.java))
                val keyCode = when (playerCommand) {
                    Player.COMMAND_SEEK_TO_PREVIOUS -> android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    Player.COMMAND_PLAY_PAUSE -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    else -> android.view.KeyEvent.KEYCODE_MEDIA_NEXT
                }
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                )
            }
            val pending = PendingIntent.getBroadcast(
                this, index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(iconRes, label, pending)
        }
    }

    private fun loadNotificationArt(uri: android.net.Uri?) {
        if (uri == null) return
        artExecutor.execute {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, uri)
                val data = retriever.embeddedPicture
                retriever.release()
                if (data != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (bmp != null) {
                        val scaled = android.graphics.Bitmap.createScaledBitmap(
                            bmp, 128, 128, true
                        )
                        if (scaled != bmp) bmp.recycle()
                        notificationArt = scaled
                        handler.post { updateNotification() }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val keyEvent = intent?.getParcelableExtra<android.view.KeyEvent>(Intent.EXTRA_KEY_EVENT)
        android.util.Log.i(
            "WaveBeatSvc",
            "onStartCommand action=${intent?.action} hasBtn=${intent?.getBooleanExtra("android.media.session.IsTrusted", false)} key=${keyEvent?.keyCode}"
        )
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        instance = null
        sleepTask?.let { handler.removeCallbacks(it) }
        sleepTask = null
        artExecutor.shutdownNow()
        releaseEffects()
        notificationArt?.recycle()
        notificationArt = null
        val session = mediaSession
        session?.player?.release()
        session?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun vibrateTick() {
        runCatching {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(24, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(24)
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class RotationRenderersFactory(
    context: Context,
    processor: RotationAudioProcessor
) : DefaultRenderersFactory(context) {
    private val audioSink: AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(processor))
        .build()

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink = audioSink
}