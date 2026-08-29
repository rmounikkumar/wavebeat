package com.wavebeat

import android.Manifest
import android.animation.AnimatorInflater
import android.app.AlertDialog
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.media.MediaMetadataRetriever
import java.io.FileInputStream
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.database.ContentObserver
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.concurrent.thread
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

class PullScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ScrollView(context, attrs) {
    var pullToRefresh: (() -> Unit)? = null
    private var downY = 0f
    private var triggered = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                triggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!triggered && scrollY == 0 && ev.y - downY > resources.displayMetrics.density * 80f) {
                    triggered = true
                    pullToRefresh?.invoke()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

class PullListView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListView(context, attrs) {
    var pullToRefresh: (() -> Unit)? = null
    private var downY = 0f
    private var triggered = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                triggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val atTop = firstVisiblePosition == 0 && (getChildAt(0)?.top ?: 0) == 0
                if (!triggered && atTop && ev.y - downY > resources.displayMetrics.density * 80f) {
                    triggered = true
                    pullToRefresh?.invoke()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var albumArt: ImageView
    private lateinit var playBtn: ImageButton
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var shuffleBtn: ImageButton
    private lateinit var repeatBtn: ImageButton
    private lateinit var sleepBtn: ImageButton
    private lateinit var sleepLabel: TextView
    private lateinit var presetGrid: GridLayout
    private lateinit var songListView: ListView
    private lateinit var songListAdapter: SongAdapter
    private lateinit var libraryRoot: LinearLayout
    private lateinit var libSongsPill: TextView
    private lateinit var libPlaylistsPill: TextView
    private lateinit var libFavPill: TextView
    private lateinit var libCount: TextView
    private lateinit var newPlaylistBtn: TextView
    private lateinit var favListView: ListView
    private lateinit var playlistListView: ListView
    private lateinit var playlistDetailRoot: LinearLayout
    private lateinit var playlistDetailBack: ImageButton
    private lateinit var playlistDetailTitle: TextView
    private lateinit var playlistDetailCount: TextView
    private lateinit var playlistDetailRename: ImageButton
    private lateinit var playlistDetailDelete: ImageButton
    private lateinit var playlistDetailAddSongs: TextView
    private lateinit var playlistDetailListView: ListView
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniProgress: ProgressBar
    private lateinit var homeRoot: LinearLayout
    private lateinit var homeGrid: GridLayout
    private lateinit var playerOverlay: FrameLayout
    private lateinit var playerOverlayClose: ImageButton
    private val homeCards = mutableMapOf<Int, HomeCard>()

    private class HomeCard(val root: LinearLayout, val title: TextView, val artist: TextView, val scrim: View)
    private lateinit var lyricsBtn: TextView
    private lateinit var lyricsPanelTitle: TextView
    private lateinit var lyricsText: TextView
    private val lyricsCache = mutableMapOf<Long, String>()
    private lateinit var favBtn: ImageButton
    private lateinit var favListAdapter: SongAdapter
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var detailAdapter: SongAdapter

    private var libSub = 0
    private var currentPlaylistName: String? = null
    private val favoriteIds = mutableSetOf<Long>()
    private val playlists = LinkedHashMap<String, MutableList<Long>>()
    private val favSongs = mutableListOf<Song>()
    private val playlistDetailSongs = mutableListOf<Song>()
private lateinit var btn8D: SwitchCompat
private lateinit var btn3D: SwitchCompat
private lateinit var btnBassBoost: SwitchCompat
private lateinit var btnReverb: SwitchCompat
private lateinit var btnLoudness: SwitchCompat
    private lateinit var intensitySeek: SeekBar
    private lateinit var intensityVal: TextView
    private lateinit var tweakIntensitySeek: SeekBar
    private lateinit var tweakIntensityVal: TextView
    private lateinit var dolbyBtn: TextView
    private lateinit var playerScroll: ScrollView
    private lateinit var audioScroll: ScrollView
    private lateinit var settingsScroll: ScrollView
    private lateinit var autoEnhanceSwitch: SwitchCompat
    private lateinit var danceSwitch: SwitchCompat
    private lateinit var autoNextSwitch: SwitchCompat
    private lateinit var resumeSwitch: SwitchCompat
    private lateinit var hapticSwitch: SwitchCompat
    private lateinit var navHapticSwitch: SwitchCompat
    private lateinit var keepAwakeSwitch: SwitchCompat
    private lateinit var topBar: LinearLayout
    private lateinit var topBarTitle: TextView
    private lateinit var searchIcon: ImageButton
    private lateinit var searchField: EditText
    private lateinit var searchClear: ImageButton
    private val appPrefs by lazy { getSharedPreferences("wavebeat_state", MODE_PRIVATE) }
    private lateinit var bottomNav: BottomNavigationView
    private var selectedTab = 0
    private var navHapticReady = false
    private var lastNavItemId = 0
    private val askedSongIds = mutableSetOf<Long>()
    private val pendingRecommend = mutableSetOf<Long>()
    private var pendingPlayRecommend: Long? = null
    private var dancePlaying = false
    private var dancePostPending = false

    private val danceRunnable = object : Runnable {
        override fun run() {
            dancePostPending = false
            albumArt.invalidate()
            if (::miniArt.isInitialized && miniArt.drawable is DancingLogoDrawable) {
                miniArt.invalidate()
            }
            if (dancePlaying) {
                albumArt.postDelayed(this, 50)
                dancePostPending = true
            }
        }
    }

    private val songs = mutableListOf<Song>()
    private var hasPlayedThisSession = false
    private var currentSongIndex = 0

    private var isShuffle = false
    private var repeatMode = 0
    private var is8D = false
    private var is3D = false
    private var isBass = false
    private var isReverb = false
    private var isLoud = false
    private var sleepState = 0

    private val MODE_SONGS = 0
    private val MODE_FAVORITES = 1
    private val MODE_PLAYLIST_DETAIL = 2
    private val KEY_SONGS_CACHE = "songs_cache"

    private val presets = listOf(
        MusicService.EQ_PRESET_FLAT,
        MusicService.EQ_PRESET_POP,
        MusicService.EQ_PRESET_ROCK,
        MusicService.EQ_PRESET_JAZZ,
        MusicService.EQ_PRESET_BASS,
        MusicService.EQ_PRESET_TREBLE,
        MusicService.EQ_PRESET_VOCAL
    )
    private val presetChips = mutableMapOf<String, TextView>()

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdatingSeekBar = false

    data class Song(
        val id: Long,
        val title: String,
        val artist: String,
        val uri: Uri,
        val duration: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        restoreState(savedInstanceState)
        loadFavorites()
        playlists.putAll(loadPlaylists())
        restoreSongsCache()
        initViews()
        checkPermissions()
        setupMediaController()
        registerLibraryObserver()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::searchField.isInitialized && searchField.visibility == View.VISIBLE) {
                    closeSearch(false)
                } else if (playerOverlayVisible()) {
                    closePlayerOverlay()
                } else if (::playlistDetailRoot.isInitialized && playlistDetailRoot.visibility == View.VISIBLE) {
                    closePlaylistDetail()
                } else {
                    isEnabled = false
                    this@MainActivity.onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
        navHapticReady = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentSongIndex", currentSongIndex)
        outState.putBoolean("isShuffle", isShuffle)
        outState.putInt("repeatMode", repeatMode)
        outState.putInt("sleepState", sleepState)
        outState.putInt("selectedTab", selectedTab)
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            currentSongIndex = it.getInt("currentSongIndex", 0)
            isShuffle = it.getBoolean("isShuffle", false)
            repeatMode = it.getInt("repeatMode", 0)
            sleepState = it.getInt("sleepState", 0)
            selectedTab = it.getInt("selectedTab", 0)
        }
        val settings = MusicService.currentSettings()
        is8D = settings["8d"] as? Boolean ?: false
        is3D = (settings["virtualizer"] as? Int ?: 0) > 0
        isBass = (settings["bass"] as? Int ?: 0) > 0
        isReverb = settings["reverb"] as? Boolean ?: false
        isLoud = settings["loudness"] as? Boolean ?: false
    }

    private fun initViews() {
        songTitle = findViewById(R.id.songTitle)
        songArtist = findViewById(R.id.songArtist)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        seekBar = findViewById(R.id.seekBar)
        albumArt = findViewById(R.id.albumArt)
        playBtn = findViewById(R.id.playBtn)
        prevBtn = findViewById(R.id.prevBtn)
        nextBtn = findViewById(R.id.nextBtn)
        shuffleBtn = findViewById(R.id.shuffleBtn)
        repeatBtn = findViewById(R.id.repeatBtn)
sleepBtn = findViewById(R.id.sleepBtn)
        favBtn = findViewById(R.id.favBtn)
        sleepLabel = findViewById(R.id.sleepLabel)
        songListView = findViewById(R.id.songListView)
        libraryRoot = findViewById(R.id.libraryRoot)
        libSongsPill = findViewById(R.id.libSongsPill)
        libPlaylistsPill = findViewById(R.id.libPlaylistsPill)
        libFavPill = findViewById(R.id.libFavPill)
        libCount = findViewById(R.id.libCount)
        newPlaylistBtn = findViewById(R.id.newPlaylistBtn)
        favListView = findViewById(R.id.favListView)
        playlistListView = findViewById(R.id.playlistListView)
        playlistDetailRoot = findViewById(R.id.playlistDetailRoot)
        playlistDetailBack = findViewById(R.id.playlistDetailBack)
        playlistDetailTitle = findViewById(R.id.playlistDetailTitle)
        playlistDetailCount = findViewById(R.id.playlistDetailCount)
        playlistDetailRename = findViewById(R.id.playlistDetailRename)
        playlistDetailDelete = findViewById(R.id.playlistDetailDelete)
        playlistDetailAddSongs = findViewById(R.id.playlistDetailAddSongs)
        playlistDetailListView = findViewById(R.id.playlistDetailListView)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniArt = findViewById(R.id.miniArt)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        miniPlay = findViewById(R.id.miniPlay)
        miniProgress = findViewById(R.id.miniProgress)
        miniPlayer.setOnClickListener { openPlayerFromMini() }
        miniPlay.setOnClickListener { togglePlay() }
        lyricsBtn = findViewById(R.id.lyricsBtn)
        lyricsBtn.setOnClickListener { toggleLyricsPanel() }
        lyricsPanelTitle = findViewById(R.id.lyricsPanelTitle)
        lyricsText = findViewById(R.id.lyricsText)
        homeRoot = findViewById(R.id.homeRoot)
        homeGrid = findViewById(R.id.homeGrid)
        playerOverlay = findViewById(R.id.playerOverlay)
        playerOverlayClose = findViewById(R.id.playerOverlayClose)
        playerOverlayClose.setOnClickListener { closePlayerOverlay() }
        presetGrid = findViewById(R.id.presetGrid)
        btn8D = findViewById(R.id.btn8D)
        btn3D = findViewById(R.id.btn3D)
        btnBassBoost = findViewById(R.id.btnBassBoost)
        btnReverb = findViewById(R.id.btnReverb)
        btnLoudness = findViewById(R.id.btnLoudness)
        intensitySeek = findViewById(R.id.intensitySeek)
        intensityVal = findViewById(R.id.intensityVal)
        tweakIntensitySeek = findViewById(R.id.tweakIntensitySeek)
        tweakIntensityVal = findViewById(R.id.tweakIntensityVal)
        dolbyBtn = findViewById(R.id.dolbyBtn)
        playerScroll = findViewById(R.id.playerScroll)
        audioScroll = findViewById(R.id.audioScroll)
        settingsScroll = findViewById(R.id.settingsScroll)
        autoEnhanceSwitch = findViewById(R.id.autoEnhanceSwitch)
        bottomNav = findViewById(R.id.bottomNav)

        (findViewById(R.id.homeScroll) as PullScrollView).pullToRefresh = { manualRescanLibrary() }
        if (::songListView.isInitialized) (songListView as PullListView).pullToRefresh = { manualRescanLibrary() }

        bottomNav.setOnItemSelectedListener { item ->
            val changed = item.itemId != lastNavItemId
            lastNavItemId = item.itemId
            when (item.itemId) {
                R.id.nav_player -> { showTab(0); if (navHapticReady && changed) tapNavHaptic(); true }
                R.id.nav_songs -> { showTab(1); if (navHapticReady && changed) tapNavHaptic(); true }
                R.id.nav_audio -> { showTab(2); if (navHapticReady && changed) tapNavHaptic(); true }
                R.id.nav_settings -> { showTab(3); if (navHapticReady && changed) tapNavHaptic(); true }
                else -> false
            }
        }
applySelectedTab()

        val enhancePrefs = getSharedPreferences("wavebeat_state", MODE_PRIVATE)
        autoEnhanceSwitch.isChecked = enhancePrefs.getBoolean("auto_enhance", true)
        autoEnhanceSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            enhancePrefs.edit().putBoolean("auto_enhance", checked).apply()
        }

        danceSwitch = findViewById(R.id.danceSwitch)
        autoNextSwitch = findViewById(R.id.autoNextSwitch)
        resumeSwitch = findViewById(R.id.resumeSwitch)
        hapticSwitch = findViewById(R.id.hapticSwitch)
        keepAwakeSwitch = findViewById(R.id.keepAwakeSwitch)
        topBar = findViewById(R.id.topBar)
        topBarTitle = findViewById(R.id.topBarTitle)
        searchIcon = findViewById(R.id.searchIcon)
        searchField = findViewById(R.id.searchField)
        searchClear = findViewById(R.id.searchClear)

        val tweakPrefs = getSharedPreferences("wavebeat_state", MODE_PRIVATE)

        danceSwitch.isChecked = tweakPrefs.getBoolean("dance_logo", true)
        danceSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("dance_logo", checked).apply()
            syncLogoDance(mediaController?.isPlaying == true)
        }

        autoNextSwitch.isChecked = tweakPrefs.getBoolean("auto_next", true)
        autoNextSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("auto_next", checked).apply()
            MusicService.setAutoNext(checked)
        }

        resumeSwitch.isChecked = tweakPrefs.getBoolean("auto_resume", false)
        resumeSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("auto_resume", checked).apply()
            MusicService.setAutoResume(checked)
        }

        hapticSwitch.isChecked = tweakPrefs.getBoolean("haptics", true)
        hapticSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("haptics", checked).apply()
            MusicService.setHaptics(checked)
        }

        navHapticSwitch = findViewById(R.id.navHapticSwitch)
        navHapticSwitch.isChecked = tweakPrefs.getBoolean("navbar_haptics", true)
        navHapticSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("navbar_haptics", checked).apply()
        }

        keepAwakeSwitch.isChecked = tweakPrefs.getBoolean("keep_screen_on", true)
        keepAwakeSwitch.setOnCheckedChangeListener { sw, checked ->
            if (sw.isPressed) tapHaptic()
            tweakPrefs.edit().putBoolean("keep_screen_on", checked).apply()
            applyKeepScreenOn(checked)
        }
        applyKeepScreenOn(tweakPrefs.getBoolean("keep_screen_on", true))

        searchIcon.setOnClickListener { openSearch() }
        searchClear.setOnClickListener {
            if (searchField.text.isNullOrBlank()) {
                closeSearch(true)
            } else {
                searchField.setText("")
                songListAdapter.setFilter("")
                refreshLibrarySub()
            }
        }
        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                songListAdapter.setFilter(s?.toString().orEmpty())
                if (libSub == 0) refreshLibrarySub()
            }
        })
        searchField.setOnEditorActionListener { _, _, _ ->
            hideKeyboard(searchField)
            true
        }

        playBtn.setOnClickListener { togglePlay() }
        prevBtn.setOnClickListener { prevSong() }
        nextBtn.setOnClickListener { nextSong() }
        shuffleBtn.setOnClickListener { toggleShuffle() }
        repeatBtn.setOnClickListener { toggleRepeat() }
        sleepBtn.setOnClickListener { cycleSleepTimer() }
        favBtn.setOnClickListener {
            if (songs.isNotEmpty()) {
                toggleFavorite(songs[currentSongIndex].id)
                Toast.makeText(this, if (songs[currentSongIndex].id in favoriteIds) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
            }
        }

        initLibrary()

        buildPresetChips()

        btn8D.setOnCheckedChangeListener { _, checked ->
            is8D = checked
            MusicService.set8D(is8D)
        }
        btn3D.setOnCheckedChangeListener { _, checked ->
            is3D = checked
            MusicService.setVirtualizerStrength(if (is3D) 750 else 0)
        }
        btnBassBoost.setOnCheckedChangeListener { _, checked ->
            isBass = checked
            MusicService.setBassStrength(if (isBass) 700 else 0)
        }
        btnReverb.setOnCheckedChangeListener { _, checked ->
            isReverb = checked
            MusicService.setReverb(isReverb)
        }
        btnLoudness.setOnCheckedChangeListener { _, checked ->
            isLoud = checked
            MusicService.setLoudness(isLoud)
        }

        dolbyBtn.setOnClickListener { openDolbyAtmos() }

        val initialIntensity = (MusicService.currentSettings()["intensity"] as? Int) ?: 50
        intensitySeek.progress = initialIntensity
        tweakIntensitySeek.progress = initialIntensity
        syncIntensityUi(initialIntensity)

        val intensityListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                syncIntensitySeekBars(seekBar, progress)
                syncIntensityUi(progress)
                if (fromUser) MusicService.setEqualizerIntensity(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (tweakIntensitySeek.isPressed || seekBar?.id == tweakIntensitySeek.id) tapHaptic()
            }
        }
        intensitySeek.setOnSeekBarChangeListener(intensityListener)
        tweakIntensitySeek.setOnSeekBarChangeListener(intensityListener)

        shuffleBtn.imageTintList = transportTint(isShuffle)
        updateRepeatButtonVisual()
        btn8D.isChecked = is8D
        btn3D.isChecked = is3D
        btnBassBoost.isChecked = isBass
        btnReverb.isChecked = isReverb
        btnLoudness.isChecked = isLoud
        selectPresetChip((MusicService.currentSettings()["preset"] as? String) ?: MusicService.EQ_PRESET_FLAT)
        MusicService.onSleepTimerUpdated = { label, active ->
            runOnUiThread {
                sleepLabel.text = label
                sleepBtn.alpha = if (active) 1.0f else 0.45f
            }
        }
        when (sleepState) {
            1 -> { sleepLabel.text = "Sleep timer: 15m"; sleepBtn.alpha = 1.0f }
            2 -> { sleepLabel.text = "Sleep timer: 30m"; sleepBtn.alpha = 1.0f }
            3 -> { sleepLabel.text = "Sleep timer: 60m"; sleepBtn.alpha = 1.0f }
            4 -> { sleepLabel.text = "Pause at end of current song"; sleepBtn.alpha = 1.0f }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun showTab(tab: Int) {
        selectedTab = tab
        if (tab != 1 && ::searchField.isInitialized && searchField.visibility == View.VISIBLE) {
            closeSearch(true)
        }
        if (playerOverlayVisible()) closePlayerOverlay()
        homeRoot.visibility = if (tab == 0) View.VISIBLE else View.GONE
        libraryRoot.visibility = if (tab == 1) View.VISIBLE else View.GONE
        audioScroll.visibility = if (tab == 2) View.VISIBLE else View.GONE
        settingsScroll.visibility = if (tab == 3) View.VISIBLE else View.GONE
        if (tab == 0) {
            buildHomeGrid()
        }
        if (tab == 1) {
            refreshLibrarySub()
        }
        updateMiniPlayer()
    }

    private fun openPlayerFromMini() {
        showPlayerOverlay()
    }

    private fun openSearch() {
        if (selectedTab != 1) showTab(1)
        selectLibrarySub(0)
        topBarTitle.visibility = View.GONE
        searchIcon.visibility = View.GONE
        searchField.visibility = View.VISIBLE
        searchClear.visibility = View.VISIBLE
        searchField.requestFocus()
        searchField.postDelayed({ showKeyboard(searchField) }, 150)
    }

    private fun closeSearch(clearFilter: Boolean) {
        hideKeyboard(searchField)
        searchField.clearFocus()
        searchField.visibility = View.GONE
        searchClear.visibility = View.GONE
        topBarTitle.visibility = View.VISIBLE
        searchIcon.visibility = View.VISIBLE
        if (clearFilter && ::songListAdapter.isInitialized) {
            songListAdapter.setFilter("")
            if (libSub == 0) refreshLibrarySub()
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun playerOverlayVisible(): Boolean =
        ::playerOverlay.isInitialized && playerOverlay.visibility == View.VISIBLE

    private fun showPlayerOverlay() {
        if (!::playerOverlay.isInitialized) return
        playerOverlay.visibility = View.VISIBLE
        updateSeekBarMax()
        if (!isUpdatingSeekBar && mediaController?.isPlaying == true) startSeekBarUpdate()
        updateMiniPlayer()
        if (syncLyricsVisible()) refreshLyricsForCurrent()
    }

    private fun closePlayerOverlay() {
        if (!::playerOverlay.isInitialized || playerOverlay.visibility == View.GONE) return
        playerOverlay.visibility = View.GONE
        updateMiniPlayer()
    }

    private fun syncLyricsVisible(): Boolean = ::lyricsText.isInitialized && lyricsText.visibility == View.VISIBLE

    private fun toggleLyricsPanel() {
        if (syncLyricsVisible()) closeLyricsPanel() else openLyricsPanel()
    }

    private fun openLyricsPanel() {
        lyricsPanelTitle.visibility = View.VISIBLE
        lyricsText.visibility = View.VISIBLE
        refreshLyricsForCurrent()
    }

    private fun closeLyricsPanel() {
        lyricsPanelTitle.visibility = View.GONE
        lyricsText.visibility = View.GONE
    }

    private fun refreshLyricsForCurrent() {
        val song = songs.getOrNull(currentSongIndex.coerceIn(0, songs.size - 1)) ?: return
        lyricsText.text = "Extracting lyricsâ€¦"
        Thread {
            var text = lyricsCache[song.id]
            if (text == null) {
                text = extractLyrics(song)
                if (text != null) lyricsCache[song.id] = text
            }
            val shown = text
            handler.post {
                if (syncLyricsVisible()) {
                    lyricsText.text = shown ?: "No lyrics found in this track"
                }
            }
        }.start()
    }

    private fun extractLyrics(song: Song): String? {
        return try {
            val pfd = contentResolver.openAssetFileDescriptor(song.uri, "r") ?: return null
            pfd.use { p ->
                val input = FileInputStream(p.fileDescriptor)
                input.use { ins ->
                    val header = ByteArray(10)
                    var off = 0
                    while (off < 10) {
                        val r = ins.read(header, off, 10 - off)
                        if (r < 0) break
                        off += r
                    }
                    if (off < 10 || !header.copyOfRange(0, 3).toString(Charsets.US_ASCII).equals("ID3", ignoreCase = true)) return null
                    val major = header[3].toInt() and 0xff
                    if (major < 2 || major > 4) return null
                    val tagSize = ((header[6].toInt() and 0x7f) shl 21) or
                            ((header[7].toInt() and 0x7f) shl 14) or
                            ((header[8].toInt() and 0x7f) shl 7) or
                            (header[9].toInt() and 0x7f)
                    if (tagSize <= 0 || tagSize > 1024 * 1024 || off < 0) return null
                    val tag = ByteArray(tagSize)
                    var toff = 0
                    while (toff < tagSize) {
                        val r = ins.read(tag, toff, tagSize - toff)
                        if (r < 0) break
                        toff += r
                    }
                    parseId3Frames(tag, major)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseId3Frames(tag: ByteArray, major: Int): String? {
        var pos = 0
        val end = tag.size
        var latest: String? = null
        while (pos + 10 <= end) {
            val id = String(tag, pos, 4, Charsets.US_ASCII)
            var size = 0
            for (i in 0 until 4) {
                val b = tag[pos + 6 + i].toInt() and 0xff
                size = if (major == 4) (size shl 7) or (b and 0x7f) else (size shl 8) or b
            }
            val dataStart = pos + 10
            val dataSize = size.coerceAtMost(end - dataStart)
            val data = tag.copyOfRange(dataStart, dataStart + dataSize)

            when (id) {
                "USLT" -> parseTextFrame(data)?.let { if (it.isNotBlank()) latest = it }
                "LYRICS" -> parseEncodingPrefixText(data)?.let { if (it.isNotBlank()) latest = it }
            }
            if (size > 0) {
                pos = dataStart + size
            } else {
                pos += 10
            }
            if (pos > end) break
        }
        return latest?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseTextFrame(data: ByteArray): String? {
        if (data.size < 4) return null
        val encoding = data[0].toInt() and 0xff
        val descEnd = findNull(data, encoding, 4)
        val start = if (descEnd >= 0) descEnd else data.size
        return decodeText(data.copyOfRange(start, data.size), encoding)
    }

    private fun parseEncodingPrefixText(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt() and 0xff
        return decodeText(data.copyOfRange(1, data.size), encoding)
    }

    private fun findNull(data: ByteArray, encoding: Int, from: Int): Int {
        var i = from
        while (i < data.size) {
            if (encoding == 1 || encoding == 2) {
                if (i + 1 < data.size && data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i + 2
                i += 2
            } else {
                if (data[i] == 0.toByte()) return i + 1
                i += 1
            }
        }
        return -1
    }

    private fun decodeText(bytes: ByteArray, encoding: Int): String {
        val body = when (encoding) {
            0 -> String(bytes, Charsets.ISO_8859_1)
            1 -> {
                var b = bytes
                if (b.size >= 2 && ((b[0].toInt() and 0xff) == 0xff && (b[1].toInt() and 0xff) == 0xfe ||
                        (b[0].toInt() and 0xff) == 0xfe && (b[1].toInt() and 0xff) == 0xff)) b = b.copyOfRange(2, b.size)
                String(b, Charsets.UTF_16)
            }
            2 -> String(bytes, Charsets.UTF_16BE)
            else -> String(bytes, Charsets.UTF_8)
        }
        return body.replace("\u0000", "").trim()
    }

    private fun buildHomeGrid() {
        if (!::homeGrid.isInitialized) return
        homeGrid.removeAllViews()
        homeCards.clear()
        if (songs.isEmpty()) return
        val density = resources.displayMetrics.density
        val cardSize = (resources.displayMetrics.widthPixels - (48 * density).toInt()) / 2
        val tv = android.util.TypedValue()
        this@MainActivity.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        val palettes = arrayOf(
            intArrayOf(Color.rgb(13, 148, 136), Color.rgb(19, 78, 74)),    // teal
            intArrayOf(Color.rgb(20, 184, 166), Color.rgb(10, 58, 52)),    // mint
            intArrayOf(Color.rgb(14, 116, 144), Color.rgb(8, 51, 68)),     // petrol
            intArrayOf(Color.rgb(5, 150, 105), Color.rgb(6, 78, 59)),      // emerald
            intArrayOf(Color.rgb(15, 118, 110), Color.rgb(4, 47, 46)),     // deep sea
            intArrayOf(Color.rgb(21, 105, 99), Color.rgb(9, 63, 44))       // pine
        )
        songs.forEachIndexed { index, song ->
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                background = getDrawable(R.drawable.home_card)?.mutate()
                if (tv.resourceId != 0) foreground = getDrawable(tv.resourceId)?.mutate()
                elevation = 1f * density
                setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            }
            val artZone = FrameLayout(this)
            val artBack = ImageView(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR, palettes[index % palettes.size]).apply {
                    cornerRadius = 12f * density
                }
            }
            artZone.addView(artBack, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val logoDisc = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(30, 255, 255, 255))
                }
            }
            artZone.addView(logoDisc, FrameLayout.LayoutParams(
                (110 * density).toInt(), (110 * density).toInt(), Gravity.CENTER))
            val logoNote = ImageView(this).apply {
                setImageResource(R.drawable.ic_music_note)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                alpha = 0.95f
            }
            artZone.addView(logoNote, FrameLayout.LayoutParams(
                (58 * density).toInt(), (58 * density).toInt(), Gravity.CENTER))
            val scrim = View(this).apply {
                background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(Color.argb(38, 29, 185, 84))
                }
                visibility = View.GONE
            }
            artZone.addView(scrim, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            val title = TextView(this).apply {
                text = song.title
                textSize = 14f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val artist = TextView(this).apply {
                text = song.artist
                textSize = 11f
                setTextColor(Color.rgb(179, 179, 183))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            root.addView(artZone, LinearLayout.LayoutParams(cardSize, cardSize))
            val pad = (8 * density).toInt()
            root.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })
            root.addView(artist, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (2 * density).toInt() })
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins((6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
            }
            root.layoutParams = lp
            root.setOnClickListener { playHomeCard(index) }
            homeGrid.addView(root)
            homeCards[index] = HomeCard(root, title, artist, scrim)
        }
    }

    private fun refreshHomeCards() {
        if (homeCards.isEmpty()) return
        songs.forEachIndexed { index, _ ->
            val card = homeCards[index] ?: return@forEachIndexed
            val isCurrent = index == currentSongIndex
            card.artist.setTextColor(if (isCurrent) Color.rgb(29, 185, 84) else Color.rgb(179, 179, 183))
            card.scrim.visibility = if (isCurrent) View.VISIBLE else View.GONE
            card.root.background = getDrawable(R.drawable.home_card)?.mutate()
        }
    }

    private fun playHomeCard(index: Int) {
        currentSongIndex = index
        refreshHomeCards()
        updateMiniPlayer()
        playSong(index)
    }

    private fun updateMiniPlayer() {
        if (!::miniPlayer.isInitialized) return
        val show = songs.isNotEmpty() && (hasPlayedThisSession || mediaController?.isPlaying == true) && !playerOverlayVisible()
        miniPlayer.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val song = songs.getOrNull(currentSongIndex.coerceIn(0, songs.size - 1)) ?: return
        miniTitle.text = song.title
        miniArtist.text = song.artist
        val playing = mediaController?.isPlaying == true
        miniPlay.setImageResource(if (playing) R.drawable.ic_spotify_pause else R.drawable.ic_spotify_play)
    }

    private fun applySelectedTab() {
        bottomNav.selectedItemId = when (selectedTab) {
            1 -> R.id.nav_songs
            2 -> R.id.nav_audio
            3 -> R.id.nav_settings
            else -> R.id.nav_player
        }
        showTab(selectedTab)
    }

    private fun initLibrary() {
        songListView.dividerHeight = (0.5f * resources.displayMetrics.density).toInt()
        favListView.dividerHeight = songListView.dividerHeight
        playlistListView.dividerHeight = songListView.dividerHeight
        playlistDetailListView.dividerHeight = songListView.dividerHeight

        songListAdapter = SongAdapter(songs, MODE_SONGS)
        songListAdapter.setFilter("")
        songListView.adapter = songListAdapter
        rebuildFavSongs()
        favListAdapter = SongAdapter(favSongs, MODE_FAVORITES)
        favListAdapter.setFilter("")
        favListView.adapter = favListAdapter
        playlistAdapter = PlaylistAdapter()
        playlistListView.adapter = playlistAdapter
        detailAdapter = SongAdapter(playlistDetailSongs, MODE_PLAYLIST_DETAIL)
        detailAdapter.setFilter("")
        playlistDetailListView.adapter = detailAdapter

        libSongsPill.setOnClickListener { selectLibrarySub(0) }
        libPlaylistsPill.setOnClickListener { selectLibrarySub(1) }
        libFavPill.setOnClickListener { selectLibrarySub(2) }
        newPlaylistBtn.setOnClickListener { createPlaylistDialog(null) }

        playlistListView.setOnItemClickListener { _, _, position, _ ->
            if (position in playlists.keys.indices) openPlaylistDetail(playlists.keys.elementAt(position))
        }
        playlistListView.setOnItemLongClickListener { _, _, position, _ ->
            if (position in playlists.keys.indices) playlistOptionsDialog(playlists.keys.elementAt(position))
            true
        }
        playlistDetailBack.setOnClickListener { closePlaylistDetail() }
        playlistDetailRename.setOnClickListener {
            currentPlaylistName?.let { renamePlaylistDialog(it) }
        }
        playlistDetailDelete.setOnClickListener {
            currentPlaylistName?.let { deletePlaylistDialog(it) }
        }
        playlistDetailAddSongs.setOnClickListener {
            currentPlaylistName?.let { addSongsDialog(it) }
        }

        selectLibrarySub(0)
    }

    private inner class SongAdapter(
        private val source: MutableList<Song>,
        private val mode: Int
    ) : BaseAdapter() {
        private val filtered = mutableListOf<Song>()
        private var query = ""

        fun setFilter(q: String) {
            query = q
            populate()
        }

        private fun populate() {
            val q = query.trim()
            filtered.clear()
            if (q.isEmpty()) {
                filtered.addAll(source)
            } else {
                source.forEach { song ->
                    if (song.title.contains(q, ignoreCase = true) || song.artist.contains(q, ignoreCase = true)) {
                        filtered.add(song)
                    }
                }
            }
            notifyDataSetChanged()
        }

        fun refresh() = populate()

        override fun getCount(): Int = filtered.size
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_song, parent, false)
            val index = view.findViewById<TextView>(R.id.songIndex)
            val title = view.findViewById<TextView>(R.id.songRowTitle)
            val artist = view.findViewById<TextView>(R.id.songRowArtist)
            val favIcon = view.findViewById<ImageButton>(R.id.favIcon)

            val song = filtered[position]
            index.text = (position + 1).toString().padStart(2, '0')
            title.text = song.title
            artist.text = song.artist.takeIf { !it.isNullOrBlank() && it != "<unknown>" } ?: "â€”"

            val active = songs.getOrNull(currentSongIndex)?.id == song.id
            title.isSelected = active
            title.setTextColor(if (active) 0xFFE94560.toInt() else 0xFFFFFFFF.toInt())
            index.setTextColor(if (active) 0xFFE94560.toInt() else 0xFF555555.toInt())
            artist.setTextColor(0xFF888888.toInt())

            when (mode) {
                MODE_FAVORITES -> {
                    favIcon.setImageResource(R.drawable.ic_fav_filled)
                    favIcon.imageTintList = ColorStateList.valueOf(0xFFE94560.toInt())
                }
                MODE_PLAYLIST_DETAIL -> {
                    favIcon.setImageResource(R.drawable.ic_fav_filled)
                    favIcon.imageTintList = ColorStateList.valueOf(0xFF00E5C7.toInt())
                }
                else -> {
                    val fav = song.id in favoriteIds
                    favIcon.setImageResource(if (fav) R.drawable.ic_fav_filled else R.drawable.ic_fav_outline)
                    favIcon.imageTintList = ColorStateList.valueOf(if (fav) 0xFFE94560.toInt() else 0xFFB6B8CC.toInt())
                }
            }
            favIcon.setOnClickListener { onRowFavoriteClicked(song, mode) }
            view.setOnClickListener { onRowClick(position) }
            view.setOnLongClickListener { onRowLongClick(position) }
            return view
        }

        private fun onRowClick(position: Int) {
            when (mode) {
                MODE_FAVORITES -> {
                    if (position in favSongs.indices) playBySongId(favSongs[position].id)
                }
                MODE_PLAYLIST_DETAIL -> {
                    if (position in playlistDetailSongs.indices) playBySongId(playlistDetailSongs[position].id)
                }
                else -> {
                    if (position in filtered.indices) playBySongId(filtered[position].id)
                }
            }
        }

        private fun onRowLongClick(position: Int): Boolean {
            return when (mode) {
                MODE_SONGS -> {
                    if (position in filtered.indices) addSongToPlaylist(filtered[position])
                    true
                }
                MODE_PLAYLIST_DETAIL -> {
                    currentPlaylistName?.let { name ->
                        if (position in playlistDetailSongs.indices) {
                            removeFromPlaylist(name, playlistDetailSongs[position].id)
                        }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private inner class PlaylistAdapter : BaseAdapter() {
        override fun getCount(): Int = playlists.size
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_playlist, parent, false)
            val name = playlists.keys.elementAt(position)
            view.findViewById<TextView>(R.id.playlistName).apply {
                text = name
                setTextColor(0xFFFFFFFF.toInt())
            }
            view.findViewById<TextView>(R.id.playlistCount).apply {
                text = "${playlists[name]?.size ?: 0} tracks"
                setTextColor(0xFF888888.toInt())
            }
            view.findViewById<ImageView>(R.id.playlistIcon)?.background =
                getDrawable(R.drawable.icon_chip)
            return view
        }
    }

    private fun buildPresetChips() {
        presetGrid.removeAllViews()
        presetChips.clear()
        for (preset in presets) {
            val chip = TextView(this).apply {
                text = preset
                textSize = 12f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                minHeight = (46 * resources.displayMetrics.density).toInt()
                elevation = 2f * resources.displayMetrics.density
                stateListAnimator = AnimatorInflater.loadStateListAnimator(this@MainActivity, R.animator.tactile_press)
                val tv = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                if (tv.resourceId != 0) foreground = context.getDrawable(tv.resourceId)
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                setMargins(5, 5, 5, 5)
            }
            chip.layoutParams = lp
            chip.setOnClickListener {
                selectPresetChip(preset)
                MusicService.setEqualizerPreset(preset)
            }
            presetGrid.addView(chip)
            presetChips[preset] = chip
        }
    }

    private fun selectPresetChip(preset: String) {
        for ((name, chip) in presetChips) {
            val active = name == preset
            chip.background = if (active) {
                getDrawable(com.wavebeat.R.drawable.chip_bg_active)
            } else {
                getDrawable(com.wavebeat.R.drawable.chip_bg)
            }
            chip.setTextColor(
                if (active) Color.rgb(0, 229, 199)
                else Color.WHITE
            )
        }
    }

    private fun maybeRecommendPreset(song: Song) {
        val prefs = getSharedPreferences("wavebeat_state", MODE_PRIVATE)
        if (!prefs.getBoolean("auto_enhance", true)) return
        if (song.id in askedSongIds || song.id in pendingRecommend) return
        pendingRecommend.add(song.id)

        thread {
            val recommended = presetForSong(song)
            if (recommended == MusicService.EQ_PRESET_FLAT) {
                pendingRecommend.remove(song.id)
                return@thread
            }
            val current = MusicService.currentSettings()["preset"] as? String ?: MusicService.EQ_PRESET_FLAT
            if (recommended == current) {
                pendingRecommend.remove(song.id)
                return@thread
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    pendingRecommend.remove(song.id)
                    return@runOnUiThread
                }
                showRecommendationDialog(song, recommended)
            }
        }
    }

    private fun presetForSong(song: Song): String {
        var genre: String? = null
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, song.uri)
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            } finally {
                runCatching { retriever.release() }
            }
        } catch (_: Exception) {
        }
        val hay = listOfNotNull(genre, song.title, song.artist).joinToString(" ").lowercase()
        fun hits(words: List<String>) = words.any { hay.contains(it) }
        return when {
            hits(listOf("classical", "orchestra", "symphony", "instrumental", "ambient", "soundtrack", "score", "lo-fi", "chill")) -> MusicService.EQ_PRESET_TREBLE
            hits(listOf("hip hop", "hip-hop", "rap", "drill", "grime", "trap", "dance", "edm", "house", "techno", "trance", "dubstep", "electronic", "k-pop", "pop")) -> MusicService.EQ_PRESET_POP
            hits(listOf("rock", "metal", "alternative", "punk", "grunge", "indie", "hardcore", "hard rock")) -> MusicService.EQ_PRESET_ROCK
            hits(listOf("jazz", "blues", "swing", "funk", "soul", "rnb", "r&b", "reggae", "latin", "country", "gospel")) -> MusicService.EQ_PRESET_JAZZ
            hits(listOf("bass", "dub", "reggaeton", "phonk", "808")) -> MusicService.EQ_PRESET_BASS
            hits(listOf("vocal", "acoustic", "folk", "ballad", "a cappella", "opera", "musical")) -> MusicService.EQ_PRESET_VOCAL
            else -> {
                val fallbacks = listOf(
                    MusicService.EQ_PRESET_POP,
                    MusicService.EQ_PRESET_ROCK,
                    MusicService.EQ_PRESET_JAZZ,
                    MusicService.EQ_PRESET_TREBLE,
                    MusicService.EQ_PRESET_BASS,
                    MusicService.EQ_PRESET_VOCAL
                )
                fallbacks[abs(song.title.hashCode() % fallbacks.size)]
            }
        }
    }

    private fun showRecommendationDialog(song: Song, preset: String) {
        val rememberPrefs = getSharedPreferences("wavebeat_state", MODE_PRIVATE)
        val checkBox = CheckBox(this).apply {
            text = "Don't ask again for songs"
            textSize = 13f
            setTextColor(0xFFB9BBCB.toInt())
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0) }
        wrap.addView(checkBox)

        fun disableAsks() {
            rememberPrefs.edit().putBoolean("auto_enhance", false).apply()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Enhance Experience")
            .setMessage("Recommended \"$preset\" for \"${song.title}\".\nApply this preset for richer, tuned sound?")
            .setView(wrap)
            .setPositiveButton("Apply") { _, _ ->
                if (checkBox.isChecked) disableAsks()
                MusicService.setEqualizerPreset(preset)
                selectPresetChip(preset)
                askedSongIds.add(song.id)
                pendingRecommend.remove(song.id)
            }
            .setNegativeButton("Not now") { _, _ ->
                if (checkBox.isChecked) disableAsks()
                askedSongIds.add(song.id)
                pendingRecommend.remove(song.id)
            }
            .setOnCancelListener {
                askedSongIds.add(song.id)
                pendingRecommend.remove(song.id)
            }
            .create()
        dialog.setOnDismissListener { pendingRecommend.remove(song.id) }
        dialog.show()
    }

    private fun loadFavorites() {
        val raw = getSharedPreferences("wavebeat_state", MODE_PRIVATE).getString("favorites", "")
        if (raw.isNullOrBlank()) return
        favoriteIds.clear()
        raw.split(',').filter { it.isNotBlank() }.forEach {
            runCatching { favoriteIds.add(it.trim().toLong()) }
        }
    }

    private fun saveFavorites() {
        getSharedPreferences("wavebeat_state", MODE_PRIVATE)
            .edit().putString("favorites", favoriteIds.joinToString(",")).apply()
    }

    private fun loadPlaylists(): LinkedHashMap<String, MutableList<Long>> {
        val map = LinkedHashMap<String, MutableList<Long>>()
        val raw = getSharedPreferences("wavebeat_state", MODE_PRIVATE).getString("playlists", "")
        if (raw.isNullOrBlank()) return map
        runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val arr = obj.optJSONArray(name) ?: continue
                val ids = mutableListOf<Long>()
                for (i in 0 until arr.length()) runCatching { ids.add(arr.getLong(i)) }
                map[name] = ids
            }
        }
        return map
    }

    private fun savePlaylists() {
        val obj = JSONObject()
        for ((name, ids) in playlists) {
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            obj.put(name, arr)
        }
        getSharedPreferences("wavebeat_state", MODE_PRIVATE)
            .edit().putString("playlists", obj.toString()).apply()
    }

    private fun selectLibrarySub(sub: Int) {
        libSub = sub
        selectPillTv(libSongsPill, sub == 0)
        selectPillTv(libPlaylistsPill, sub == 1)
        selectPillTv(libFavPill, sub == 2)
        if (sub != 1) closePlaylistDetail()
        songListView.visibility = if (sub == 0) View.VISIBLE else View.GONE
        playlistListView.visibility = if (sub == 1) View.VISIBLE else View.GONE
        favListView.visibility = if (sub == 2) View.VISIBLE else View.GONE
        newPlaylistBtn.visibility = if (sub == 1) View.VISIBLE else View.GONE
        refreshLibrarySub()
    }

    private fun selectPillTv(tv: TextView, active: Boolean) {
        tv.background = getDrawable(if (active) R.drawable.chip_bg_active else R.drawable.chip_bg)
        tv.setTextColor(if (active) Color.rgb(0, 229, 199) else Color.WHITE)
    }

    private fun refreshLibrarySub() {
        val searching = ::searchField.isInitialized && !searchField.text.isNullOrBlank()
        libCount.text = when (libSub) {
            0 -> if (searching) "${songListAdapter.count} result${if (songListAdapter.count == 1) "" else "s"}"
            else "${songs.size} tracks"
            1 -> "${playlists.size} playlists"
            2 -> "${favoriteIds.size} favorites"
            else -> ""
        }
    }

    private fun rebuildFavSongs() {
        favSongs.clear()
        favoriteIds.forEach { id ->
            songs.firstOrNull { it.id == id }?.let { favSongs.add(it) }
        }
    }

    private fun refreshFavoriteUi() {
        rebuildFavSongs()
        if (::songListAdapter.isInitialized) {
            songListAdapter.notifyDataSetChanged()
            favListAdapter.refresh()
        }
        refreshFavBtn()
        if (libSub == 2) refreshLibrarySub()
    }

    private fun toggleFavorite(id: Long) {
        if (!favoriteIds.add(id)) favoriteIds.remove(id)
        saveFavorites()
        refreshFavoriteUi()
    }

    private fun onRowFavoriteClicked(song: Song, mode: Int) {
        when (mode) {
            MODE_FAVORITES -> {
                if (favoriteIds.remove(song.id)) saveFavorites()
                refreshFavoriteUi()
            }
            MODE_PLAYLIST_DETAIL -> currentPlaylistName?.let { removeFromPlaylist(it, song.id) }
            else -> toggleFavorite(song.id)
        }
    }

    private fun tapHaptic() {
        runCatching {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        }
    }

    private fun tapNavHaptic() {
        if (!::navHapticSwitch.isInitialized || !navHapticSwitch.isChecked) return
        runCatching {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(16, 70))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(16)
            }
        }
    }

    private fun syncIntensityUi(progress: Int) {
        if (::intensityVal.isInitialized) intensityVal.text = progress.toString()
        if (::tweakIntensityVal.isInitialized) tweakIntensityVal.text = progress.toString()
    }

    private fun syncIntensitySeekBars(source: SeekBar?, progress: Int) {
        if (::intensitySeek.isInitialized && intensitySeek.id != source?.id && intensitySeek.progress != progress) {
            intensitySeek.progress = progress
        }
        if (::tweakIntensitySeek.isInitialized && tweakIntensitySeek.id != source?.id && tweakIntensitySeek.progress != progress) {
            tweakIntensitySeek.progress = progress
        }
    }

    private fun refreshFavBtn() {
        if (!::favBtn.isInitialized) return
        favBtn.alpha = if (songs.isEmpty()) 0.4f else 1f
        if (songs.isEmpty()) return
        val fav = songs[currentSongIndex].id in favoriteIds
        favBtn.setImageResource(if (fav) R.drawable.ic_fav_filled else R.drawable.ic_fav_outline)
        favBtn.imageTintList = ColorStateList.valueOf(if (fav) 0xFFE94560.toInt() else 0xFFB6B8CC.toInt())
    }

    private fun openPlaylistDetail(name: String) {
        currentPlaylistName = name
        playlistDetailTitle.text = name
        refreshPlaylistDetail()
        songListView.visibility = View.GONE
        playlistListView.visibility = View.GONE
        favListView.visibility = View.GONE
        newPlaylistBtn.visibility = View.GONE
        playlistDetailRoot.visibility = View.VISIBLE
    }

    private fun closePlaylistDetail() {
        if (::playlistDetailRoot.isInitialized && playlistDetailRoot.visibility != View.VISIBLE) return
        currentPlaylistName = null
        playlistDetailRoot.visibility = View.GONE
        songListView.visibility = if (libSub == 0) View.VISIBLE else View.GONE
        playlistListView.visibility = if (libSub == 1) View.VISIBLE else View.GONE
        favListView.visibility = if (libSub == 2) View.VISIBLE else View.GONE
        newPlaylistBtn.visibility = if (libSub == 1) View.VISIBLE else View.GONE
    }

    private fun refreshPlaylistDetail() {
        playlistDetailSongs.clear()
        currentPlaylistName?.let { name ->
            playlists[name]?.forEach { id ->
                songs.firstOrNull { it.id == id }?.let { playlistDetailSongs.add(it) }
            }
        }
        if (::detailAdapter.isInitialized) detailAdapter.refresh()
        playlistDetailCount.text = "${playlistDetailSongs.size} tracks"
    }

    private fun removeFromPlaylist(name: String, songId: Long) {
        playlists[name]?.removeAll { it == songId }
        savePlaylists()
        if (name == currentPlaylistName) refreshPlaylistDetail()
        if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
        if (libSub == 1) refreshLibrarySub()
    }

    private fun addSongToPlaylist(song: Song) {
        val names = playlists.keys.toList()
        if (names.isEmpty()) {
            createPlaylistDialog(song)
            return
        }
        val options = names + "ï¼‹ New playlistâ€¦"
        AlertDialog.Builder(this)
            .setTitle("Add to playlist")
            .setSingleChoiceItems(options.toTypedArray(), -1) { d, which ->
                d.dismiss()
                if (which == names.size) {
                    createPlaylistDialog(song)
                } else {
                    val name = names[which]
                    val list = playlists[name] ?: return@setSingleChoiceItems
                    if (list.contains(song.id)) {
                        Toast.makeText(this, "Already in \"$name\"", Toast.LENGTH_SHORT).show()
                    } else {
                        list.add(song.id)
                        savePlaylists()
                        if (name == currentPlaylistName) refreshPlaylistDetail()
                        if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
                        if (libSub == 1) refreshLibrarySub()
                    }
                }
            }
            .show()
    }

    private fun createPlaylistDialog(initial: Song?) {
        val input = EditText(this).apply {
            hint = "Playlist name"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0) }
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("New playlist")
            .setView(wrap)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                when {
                    name.isEmpty() -> Toast.makeText(this, "Name can't be empty", Toast.LENGTH_SHORT).show()
                    name.length > 30 -> Toast.makeText(this, "Keep the name under 30 characters", Toast.LENGTH_SHORT).show()
                    playlists.containsKey(name) -> Toast.makeText(this, "A playlist with that name already exists", Toast.LENGTH_SHORT).show()
                    else -> {
                        val ids = mutableListOf<Long>()
                        if (initial != null) ids.add(initial.id)
                        playlists[name] = ids
                        savePlaylists()
                        if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
                        if (libSub == 1) refreshLibrarySub()
                        openPlaylistDetail(name)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePlaylistDialog(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete playlist")
            .setMessage("Delete \"$name\"? The songs themselves won't be removed.")
            .setPositiveButton("Delete") { _, _ ->
                playlists.remove(name)
                savePlaylists()
                if (name == currentPlaylistName) closePlaylistDetail()
                if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
                if (libSub == 1) refreshLibrarySub()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playlistOptionsDialog(name: String) {
        val options = arrayOf("Add songs", "Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> addSongsDialog(name)
                    1 -> renamePlaylistDialog(name)
                    2 -> deletePlaylistDialog(name)
                }
            }
            .show()
    }

    private fun renamePlaylistDialog(name: String) {
        val input = EditText(this).apply {
            setText(name)
            setSelection(text.length)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF888888.toInt())
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val wrap = android.widget.FrameLayout(this).apply { setPadding(pad, 0, pad, 0) }
        wrap.addView(input)
        AlertDialog.Builder(this)
            .setTitle("Rename playlist")
            .setView(wrap)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString().trim()
                when {
                    newName.isEmpty() -> Toast.makeText(this, "Name can't be empty", Toast.LENGTH_SHORT).show()
                    newName.length > 30 -> Toast.makeText(this, "Keep the name under 30 characters", Toast.LENGTH_SHORT).show()
                    newName != name && playlists.containsKey(newName) ->
                        Toast.makeText(this, "A playlist with that name already exists", Toast.LENGTH_SHORT).show()
                    else -> {
                        if (newName == name) return@setPositiveButton
                        val renamed = LinkedHashMap<String, MutableList<Long>>(playlists.size)
                        for ((k, v) in playlists) {
                            renamed[if (k == name) newName else k] = v
                        }
                        playlists.clear()
                        playlists.putAll(renamed)
                        if (currentPlaylistName == name) {
                            currentPlaylistName = newName
                            playlistDetailTitle.text = newName
                        }
                        savePlaylists()
                        if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
                        if (libSub == 1) refreshLibrarySub()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSongsDialog(name: String) {
        if (songs.isEmpty()) {
            Toast.makeText(this, "No songs to add", Toast.LENGTH_SHORT).show()
            return
        }
        val existing = playlists[name]?.toMutableSet() ?: mutableSetOf()
        val labels = songs.map { it.title }
        val checked = BooleanArray(songs.size) { existing.contains(songs[it].id) }
        AlertDialog.Builder(this)
            .setTitle("Add songs to \"$name\"")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Add") { _, _ ->
                val added = mutableListOf<Long>()
                checked.forEachIndexed { i, c ->
                    if (c && !existing.contains(songs[i].id)) added.add(songs[i].id)
                }
                if (added.isEmpty()) return@setPositiveButton
                val list = playlists[name] ?: mutableListOf<Long>().also { playlists[name] = it }
                list.addAll(added)
                savePlaylists()
                if (name == currentPlaylistName) refreshPlaylistDetail()
                if (::playlistAdapter.isInitialized) playlistAdapter.notifyDataSetChanged()
                if (libSub == 1) refreshLibrarySub()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playBySongId(id: Long) {
        val lib = songs.indexOfFirst { it.id == id }
        if (lib >= 0) {
            currentSongIndex = lib
            updateSongUI()
            playSong(lib)
            updateMiniPlayer()
        }
    }

    private fun openDolbyAtmos() {
        try {
            val intent = Intent("com.motorola.dolby.dolbyui.sst.SOUNDSETTINGS_START")
            intent.component = ComponentName("com.motorola.dolby.dolbyui", "com.motorola.dolby.dolbyui.ui.sst.SSTActivity")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallback = packageManager.getLaunchIntentForPackage("com.motorola.dolby.dolbyui")
                if (fallback != null) {
                    startActivity(fallback)
                    return
                }
                Toast.makeText(this, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "Dolby Atmos is not available on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cycleSleepTimer() {
        val modes = listOf(0, 15, 30, 60, -1)
        sleepState = (sleepState + 1) % modes.size
        val minutes = modes[sleepState]
        when (minutes) {
            -1 -> {
                MusicService.setSleepTimer(0, true)
                sleepLabel.text = "Sleep timer: Stop at end of song"
                sleepBtn.alpha = 1.0f
            }
            0 -> {
                MusicService.cancelSleepTimer()
                sleepState = 0
            }
            else -> {
                MusicService.setSleepTimer(minutes, false)
                sleepLabel.text = "Sleep timer: ${minutes}m"
                sleepBtn.alpha = 1.0f
            }
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        } else {
            loadSongs()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadSongs()
            } else {
                val denied = permissions.zip(grantResults.toList())
                    .filter { it.second != PackageManager.PERMISSION_GRANTED }
                    .map { it.first.substringAfterLast('.') }
                songTitle.text = "Permission needed"
                songArtist.text = "Grant ${denied.joinToString()} to use WaveBeat"
            }
        }
    }

    private fun loadSongs() {
        Thread {
            val loadedSongs = querySongs()

            runOnUiThread {
                songs.clear()
                songs.addAll(loadedSongs)
                if (songs.isNotEmpty()) {
                    if (currentSongIndex >= songs.size) currentSongIndex = 0
                    updateSongUI()
                    buildHomeGrid()
                    if (mediaController != null) {
                        loadFullPlaylist()
                    }
                } else {
                    songTitle.text = "No songs found"
                    songArtist.text = "Add music to your device"
                }
                if (::songListAdapter.isInitialized) {
                    rebuildFavSongs()
                    songListAdapter.refresh()
                    favListAdapter.refresh()
                    playlistAdapter.notifyDataSetChanged()
                    detailAdapter.refresh()
                }
                refreshLibrarySub()
                refreshFavBtn()
                updateMiniPlayer()
                saveSongsCache()
            }
        }.start()
    }

    private fun restoreSongsCache() {
        if (songs.isNotEmpty()) return
        try {
            val raw = getSharedPreferences("wavebeat_state", MODE_PRIVATE).getString(KEY_SONGS_CACHE, null) ?: return
            val arr = JSONArray(raw)
            if (arr.length() == 0) return
            val cached = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                cached.add(
                    Song(
                        id = o.getLong("id"),
                        title = o.optString("t"),
                        artist = o.optString("a"),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, o.getLong("id")),
                        duration = o.optLong("d")
                    )
                )
            }
            if (cached.isNotEmpty()) {
                songs.clear()
                songs.addAll(cached)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveSongsCache() {
        try {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("t", s.title)
                        put("a", s.artist)
                        put("d", s.duration)
                    }
                )
            }
            getSharedPreferences("wavebeat_state", MODE_PRIVATE)
                .edit().putString(KEY_SONGS_CACHE, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun querySongs(): List<Song> {
        val loadedSongs = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)
                val artist = cursor.getString(artistCol)
                val duration = cursor.getLong(durationCol)
                if (duration > 0) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    loadedSongs.add(Song(id, title, artist, uri, duration))
                }
            }
        }
        return loadedSongs
    }

    private val libraryObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(scanRunnable)
            handler.postDelayed(scanRunnable, 2500)
        }
    }

    private val scanRunnable = Runnable { rescanLibrary() }

    private fun registerLibraryObserver() {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        try {
            contentResolver.registerContentObserver(uri, true, libraryObserver)
        } catch (_: Exception) {}
    }

    private fun unregisterLibraryObserver() {
        try {
            contentResolver.unregisterContentObserver(libraryObserver)
        } catch (_: Exception) {}
    }

    private fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= 23) {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun rescanLibrary(manual: Boolean = false) {
        if (!hasAudioPermission()) return
        Thread {
            val beforeIds = songs.map { it.id }.toSet()
            val newSongs = querySongs()

            val changed = newSongs.size != songs.size ||
                    newSongs.zip(songs).any { (n, o) -> n.id != o.id || n.title != o.title || n.artist != o.artist || n.duration != o.duration }

            if (!changed) {
                if (manual) handler.post { Toast.makeText(this, "Library is up to date", Toast.LENGTH_SHORT).show() }
                return@Thread
            }

            val currentId = songs.getOrNull(currentSongIndex)?.id
            val currentChanged = currentId == null || newSongs.none { it.id == currentId }
            val added = newSongs.size - beforeIds.size

            runOnUiThread {
                songs.clear()
                songs.addAll(newSongs)

                favoriteIds.retainAll(newSongs.map { it.id }.toSet())
                favSongs.clear()
                favSongs.addAll(songs.filter { it.id in favoriteIds })

                val existingIds = newSongs.map { it.id }.toSet()
                playlists.forEach { (_, ids) -> ids.retainAll(existingIds) }

                currentSongIndex = if (currentId != null) {
                    songs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                } else {
                    currentSongIndex.coerceIn(0, songs.size - 1)
                }

                if (currentChanged) {
                    updateSongUI()
                } else {
                    updateMiniPlayer()
                    refreshHomeCards()
                }
                buildHomeGrid()

                if (::songListAdapter.isInitialized) {
                    songListAdapter.refresh()
                    favListAdapter.refresh()
                    playlistAdapter.notifyDataSetChanged()
                    detailAdapter.refresh()
                }

                refreshLibrarySub()
                refreshFavBtn()
                saveFavorites()
                savePlaylists()
                saveSongsCache()

                val message = if (manual) {
                    if (added > 0) "Library updated â€¢ $added new song" + if (added > 1) "s" else "" + " added"
                    else "Library updated"
                } else if (added > 0) {
                    "Auto-scan: $added new song" + if (added > 1) "s" else "" + " added"
                } else {
                    null
                }
                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun manualRescanLibrary() {
        Toast.makeText(this, "Refreshing libraryâ€¦", Toast.LENGTH_SHORT).show()
        rescanLibrary(manual = true)
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        Futures.addCallback(controllerFuture!!, object : com.google.common.util.concurrent.FutureCallback<MediaController> {
            override fun onSuccess(result: MediaController) {
                runOnUiThread {
                    mediaController = result
                    setupControllerListener()
                    startSeekBarUpdate()

                    if (songs.isNotEmpty()) {
                        loadFullPlaylist()
                    }

                    MusicService.onSongChanged = { title, artist ->
                        runOnUiThread {
                            songTitle.text = title
                            songArtist.text = artist
                        }
                    }
                    MusicService.onPlaybackError = {
                        runOnUiThread {
                            songArtist.text = "Playback error â€” pick another track"
                        }
                    }
                }
            }

            override fun onFailure(t: Throwable) {
                t.printStackTrace()
            }
        }, Runnable::run)
    }

    private fun setupControllerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayButton(isPlaying)
                syncLogoDance(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    updateSeekBarMax()
                    if (mediaController?.isPlaying == true) {
                        hasPlayedThisSession = true
                        updateMiniPlayer()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = mediaController?.currentMediaItemIndex ?: 0
                if (index in songs.indices) {
                    val requested = pendingPlayRecommend
                    pendingPlayRecommend = null
                    if (requested == null || requested == songs[index].id) {
                        currentSongIndex = index
                        updateSongUI()
                        val autoAdvance = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                                mediaController?.isPlaying == true
                        val picked = requested != null && requested == songs[index].id
                        if (autoAdvance || picked) {
                            maybeRecommendPreset(songs[index])
                        }
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                runOnUiThread {
                    this@MainActivity.repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_ALL -> 0
                        Player.REPEAT_MODE_ONE -> 1
                        else -> 2
                    }
                    updateRepeatButtonVisual()
                }
            }
        })
    }

    private fun loadFullPlaylist() {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        val mediaItems = songs.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .build()
                )
                .build()
        }

        val startIndex = currentSongIndex.coerceIn(0, songs.size - 1)

        if (controller.mediaItemCount == 0) {
            controller.setMediaItems(mediaItems, startIndex, 0)
            controller.prepare()
        } else {
            currentSongIndex = controller.currentMediaItemIndex.coerceIn(0, songs.size - 1)
            runOnUiThread {
                updateMiniPlayer()
                refreshHomeCards()
            }
        }

        applyRepeatMode()
    }

    private fun applyRepeatMode() {
        mediaController?.repeatMode = when (repeatMode) {
            0 -> Player.REPEAT_MODE_ALL
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
    }

    private fun updatePlayButton(isPlaying: Boolean) {
        val newIcon = if (isPlaying) R.drawable.ic_spotify_pause else R.drawable.ic_spotify_play
        if (::miniPlay.isInitialized) miniPlay.setImageResource(newIcon)
        playBtn.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                playBtn.setImageResource(newIcon)
                playBtn.animate().alpha(1f).setDuration(120).start()
            }
            .start()
    }

    private fun syncLogoDance(isPlaying: Boolean) {
        val enabled = appPrefs.getBoolean("dance_logo", true)
        dancePlaying = isPlaying && enabled
        if (dancePlaying) {
            if (albumArt.drawable !is DancingLogoDrawable) {
                albumArt.setImageDrawable(DancingLogoDrawable())
            }
            if (::miniArt.isInitialized && miniArt.drawable !is DancingLogoDrawable) {
                miniArt.setImageDrawable(DancingLogoDrawable())
            }
            if (!dancePostPending) {
                dancePostPending = true
                albumArt.post(danceRunnable)
            }
        } else {
            albumArt.removeCallbacks(danceRunnable)
            dancePostPending = false
            if (albumArt.drawable is DancingLogoDrawable) {
                albumArt.setImageResource(R.drawable.ic_logo)
            }
            if (::miniArt.isInitialized && miniArt.drawable is DancingLogoDrawable) {
                miniArt.setImageResource(R.drawable.ic_logo)
            }
        }
    }

    private fun applyKeepScreenOn(on: Boolean) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateSeekBarMax() {
        val duration = mediaController?.duration ?: 0L
        if (duration > 0 && seekBar.max != duration.toInt()) {
            seekBar.max = duration.toInt()
            totalTime.text = formatTime(duration)
        }
        if (duration > 0 && ::miniProgress.isInitialized && miniProgress.max != duration.toInt()) {
            miniProgress.max = duration.toInt()
        }
    }

    private fun startSeekBarUpdate() {
        isUpdatingSeekBar = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isUpdatingSeekBar) return
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val position = controller.currentPosition
                    val duration = controller.duration
                    if (duration > 0 && !seekBar.isPressed) {
                        if (seekBar.max != duration.toInt()) {
                            seekBar.max = duration.toInt()
                        }
                        seekBar.progress = position.toInt()
                        miniProgress.max = duration.toInt()
                        miniProgress.progress = position.toInt()
                        currentTime.text = formatTime(position)
                        val formattedDuration = formatTime(duration)
                        if (totalTime.text.toString() != formattedDuration) {
                            totalTime.text = formattedDuration
                        }
                    }
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun stopSeekBarUpdate() {
        isUpdatingSeekBar = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateSongUI() {
        if (songs.isEmpty()) return
        val song = songs[currentSongIndex]
        if (::songListAdapter.isInitialized) {
            songListAdapter.notifyDataSetChanged()
            favListAdapter.notifyDataSetChanged()
            detailAdapter.notifyDataSetChanged()
        }
        refreshFavBtn()
        songTitle.text = song.title
        songArtist.text = song.artist
        seekBar.max = song.duration.toInt()
        seekBar.progress = 0
        miniProgress.max = song.duration.toInt()
        miniProgress.progress = 0
        currentTime.text = "0:00"
        totalTime.text = formatTime(song.duration)

        albumArt.animate()
            .scaleX(0.96f).scaleY(0.96f)
            .alpha(0.5f)
            .setDuration(150)
            .withEndAction {
                albumArt.animate()
                    .scaleX(1f).scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()

        if (::miniTitle.isInitialized) {
            miniTitle.text = song.title
            miniArtist.text = song.artist
        }

        songTitle.isSelected = true
        updateMiniPlayer()
        refreshHomeCards()
        if (syncLyricsVisible()) refreshLyricsForCurrent()
    }

    private fun playSong(index: Int) {
        val controller = mediaController ?: return
        if (songs.isEmpty() || index !in songs.indices) return

        hasPlayedThisSession = true
        pendingPlayRecommend = songs[index].id

        if (controller.mediaItemCount != songs.size) {
            loadFullPlaylist()
        }

        controller.seekToDefaultPosition(index)
        controller.play()
    }

    private fun togglePlay() {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return

        hasPlayedThisSession = true
        when {
            controller.isPlaying -> {
                controller.pause()
            }
            controller.mediaItemCount == 0 -> {
                loadFullPlaylist()
                pendingPlayRecommend = if (songs.isEmpty()) null else songs[0].id
                controller.play()
            }
            else -> {
                controller.play()
            }
        }
    }

    private fun nextSong() {
        if (songs.isEmpty()) return
        val controller = mediaController
        MusicService.markNextIntent()

        if (isShuffle) {
            var newIndex: Int
            do {
                newIndex = (0 until songs.size).random()
            } while (newIndex == currentSongIndex && songs.size > 1)
            currentSongIndex = newIndex
        } else {
            if (repeatMode == 2) {
                currentSongIndex = if (currentSongIndex + 1 >= songs.size) currentSongIndex else currentSongIndex + 1
            } else {
                currentSongIndex = (currentSongIndex + 1) % songs.size
            }
        }

        updateSongUI()
        if (controller != null && controller.mediaItemCount == songs.size) {
            controller.seekToDefaultPosition(currentSongIndex)
            controller.play()
        } else {
            loadFullPlaylist()
            controller?.play()
        }
    }

    private fun prevSong() {
        if (songs.isEmpty()) return
        val controller = mediaController
        MusicService.markNextIntent()

        if (controller != null && controller.isPlaying && controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        currentSongIndex = if (currentSongIndex - 1 < 0) songs.size - 1 else currentSongIndex - 1
        updateSongUI()

        if (controller != null && controller.mediaItemCount == songs.size) {
            controller.seekToDefaultPosition(currentSongIndex)
            controller.play()
        } else {
            loadFullPlaylist()
            controller?.play()
        }
    }

    private fun toggleShuffle() {
        isShuffle = !isShuffle
        shuffleBtn.imageTintList = transportTint(isShuffle)
    }

    private fun toggleRepeat() {
        repeatMode = if (repeatMode == 2) 0 else repeatMode + 1
        updateRepeatButtonVisual()
        applyRepeatMode()
    }

    private fun transportTint(active: Boolean): ColorStateList =
        ColorStateList.valueOf(if (active) 0xFF00E5C7.toInt() else 0xFF9CA0A6.toInt())

    private fun updateRepeatButtonVisual() {
        when (repeatMode) {
            0 -> {
                repeatBtn.imageTintList = transportTint(false)
                repeatBtn.setImageResource(R.drawable.ic_repeat)
            }
            1 -> {
                repeatBtn.imageTintList = transportTint(true)
                repeatBtn.setImageResource(R.drawable.ic_repeat_one)
            }
            2 -> {
                repeatBtn.imageTintList = transportTint(true)
                repeatBtn.setImageResource(R.drawable.ic_repeat)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) - java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn(appPrefs.getBoolean("keep_screen_on", true))
        if (isUpdatingSeekBar) {
            handler.removeCallbacksAndMessages(null)
        }
        startSeekBarUpdate()
    }

    override fun onPause() {
        super.onPause()
        stopSeekBarUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterLibraryObserver()
        stopSeekBarUpdate()
        MusicService.onSleepTimerUpdated = null
        MusicService.onPlaybackError = null
        mediaController = null
        controllerFuture?.let { future ->
            try {
                MediaController.releaseFuture(future)
            } catch (_: Exception) {}
        }
        controllerFuture = null
        MusicService.onSongChanged = null
    }
}