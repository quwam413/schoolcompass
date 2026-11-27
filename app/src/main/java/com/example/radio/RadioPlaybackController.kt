package com.example.radio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RadioStation(
    val id: String,
    val name: String,
    val tagline: String,
    val category: String,
    val frequencyLabel: String,
    val streamUrl: String,
    val country: String = "Global"
)

enum class RadioStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ERROR
}

data class RadioPlaybackState(
    val stations: List<RadioStation> = DefaultRadioStations,
    val currentIndex: Int = 0,
    val status: RadioStatus = RadioStatus.IDLE,
    val errorMessage: String? = null
) {
    val currentStation: RadioStation
        get() = stations.getOrElse(currentIndex) { stations.first() }

    val isPlayingOrBuffering: Boolean
        get() = status == RadioStatus.PLAYING || status == RadioStatus.BUFFERING
}

val DefaultRadioStations = listOf(
    RadioStation(
        id = "somafm_groovesalad",
        name = "Groove Salad Focus",
        tagline = "Ambient chill beats for studying & homework",
        category = "Study Focus",
        frequencyLabel = "128k MP3",
        streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
        country = "Global"
    ),
    RadioStation(
        id = "wnyc_public_radio",
        name = "WNYC 93.9 FM Public Radio",
        tagline = "Education, world news, science & culture live",
        category = "News & Education",
        frequencyLabel = "93.9 FM",
        streamUrl = "https://fm939.wnyc.org/wnycfm-web",
        country = "Global"
    ),
    RadioStation(
        id = "ng_info_993",
        name = "Nigeria Info 99.3 FM Lagos",
        tagline = "Breaking Nigerian news, talk, education & public debate",
        category = "Nigeria News",
        frequencyLabel = "99.3 FM Lagos",
        streamUrl = "https://nigeriainfo.streamguys1.com/live",
        country = "Nigeria"
    ),
    RadioStation(
        id = "ng_cool_969",
        name = "Cool FM 96.9 Lagos",
        tagline = "Nigeria's premier hit music & youth campus station",
        category = "Nigeria Radio",
        frequencyLabel = "96.9 FM Lagos",
        streamUrl = "https://icestream.coolfm.ng/coolfm-lagos.mp3",
        country = "Nigeria"
    ),
    RadioStation(
        id = "ng_wazobia_951",
        name = "Wazobia FM 95.1 Lagos",
        tagline = "Lively Pidgin news, Nigerian culture & community talk",
        category = "Nigeria Radio",
        frequencyLabel = "95.1 FM Lagos",
        streamUrl = "https://icestream.coolfm.ng/wazobia-lagos.mp3",
        country = "Nigeria"
    ),
    RadioStation(
        id = "ng_classic_973",
        name = "Classic FM 97.3 Lagos",
        tagline = "Timeless classic hits, jazz & focus listening from Lagos",
        category = "Nigeria Classics",
        frequencyLabel = "97.3 FM Lagos",
        streamUrl = "https://classicfm973.radioca.st/stream",
        country = "Nigeria"
    ),
    RadioStation(
        id = "ng_fresh_1059",
        name = "Fresh FM 105.9 Ibadan",
        tagline = "Southwest Nigeria news, education & Yoruba cultural shows",
        category = "Nigeria Radio",
        frequencyLabel = "105.9 FM Ibadan",
        streamUrl = "https://stream.zeno.fm/49y3q9n16uhvv",
        country = "Nigeria"
    ),
    RadioStation(
        id = "ng_inspiration_923",
        name = "Inspiration FM 92.3 Lagos",
        tagline = "Family values, student motivation & uplifting Nigerian talk",
        category = "Nigeria Radio",
        frequencyLabel = "92.3 FM Lagos",
        streamUrl = "https://stream.zeno.fm/v6k53q1s7xhvv",
        country = "Nigeria"
    ),
    RadioStation(
        id = "somafm_deepspace",
        name = "Deep Space One",
        tagline = "Space ambient for STEM, coding & deep reading",
        category = "Science & STEM",
        frequencyLabel = "128k MP3",
        streamUrl = "https://ice1.somafm.com/deepspaceone-128-mp3",
        country = "Global"
    ),
    RadioStation(
        id = "radioparadise_mellow",
        name = "Radio Paradise Mellow",
        tagline = "Acoustic & calming mix for campus prep",
        category = "Calm Study",
        frequencyLabel = "128k AAC",
        streamUrl = "https://stream.radioparadise.com/mellow-128",
        country = "Global"
    ),
    RadioStation(
        id = "radioparadise_main",
        name = "Radio Paradise Eclectic",
        tagline = "Global modern & classic listener-supported stream",
        category = "World Music",
        frequencyLabel = "128k AAC",
        streamUrl = "https://stream.radioparadise.com/aac-128",
        country = "Global"
    ),
    RadioStation(
        id = "somafm_dronezone",
        name = "Drone Zone Quiet",
        tagline = "Atmospheric textures with zero distractions",
        category = "Deep Focus",
        frequencyLabel = "128k MP3",
        streamUrl = "https://ice1.somafm.com/dronezone-128-mp3",
        country = "Global"
    )
)

class RadioPlaybackController(
    private val enableAudioHardware: Boolean = true
) {
    private val _state = MutableStateFlow(RadioPlaybackState())
    val state: StateFlow<RadioPlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var preparedStationId: String? = null

    fun selectAndPlay(index: Int) {
        val currentStations = _state.value.stations
        if (currentStations.isEmpty()) return
        val safeIndex = ((index % currentStations.size) + currentStations.size) % currentStations.size
        val targetStation = currentStations[safeIndex]

        _state.update {
            it.copy(
                currentIndex = safeIndex,
                status = if (enableAudioHardware) RadioStatus.BUFFERING else RadioStatus.PLAYING,
                errorMessage = null
            )
        }

        if (!enableAudioHardware) return
        startStream(targetStation)
    }

    fun togglePlayPause() {
        val snapshot = _state.value
        when (snapshot.status) {
            RadioStatus.PLAYING, RadioStatus.BUFFERING -> pause()
            RadioStatus.PAUSED -> {
                if (!enableAudioHardware) {
                    _state.update { it.copy(status = RadioStatus.PLAYING, errorMessage = null) }
                    return
                }
                val player = mediaPlayer
                if (player != null && preparedStationId == snapshot.currentStation.id) {
                    val resumed = runCatching { player.start() }.isSuccess
                    if (resumed) {
                        _state.update { it.copy(status = RadioStatus.PLAYING, errorMessage = null) }
                        return
                    }
                }
                selectAndPlay(snapshot.currentIndex)
            }
            RadioStatus.IDLE, RadioStatus.ERROR -> selectAndPlay(snapshot.currentIndex)
        }
    }

    fun pause() {
        if (enableAudioHardware) {
            runCatching {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            }
        }
        _state.update {
            it.copy(
                status = RadioStatus.PAUSED,
                errorMessage = null
            )
        }
    }

    fun skipForward() {
        val cur = _state.value
        if (cur.stations.isEmpty()) return
        val nextIndex = (cur.currentIndex + 1) % cur.stations.size
        selectAndPlay(nextIndex)
    }

    fun skipBackward() {
        val cur = _state.value
        if (cur.stations.isEmpty()) return
        val prevIndex = (cur.currentIndex - 1 + cur.stations.size) % cur.stations.size
        selectAndPlay(prevIndex)
    }

    fun addAndPlayCustomStation(name: String, streamUrl: String) {
        val trimmedUrl = streamUrl.trim()
        if (!trimmedUrl.startsWith("https://", ignoreCase = true) &&
            !trimmedUrl.startsWith("http://", ignoreCase = true)
        ) {
            _state.update {
                it.copy(errorMessage = "Enter a valid http:// or https:// radio stream URL.")
            }
            return
        }
        val cleanName = name.trim().ifBlank { "Custom Radio Stream" }.take(60)
        val customStation = RadioStation(
            id = "custom_${trimmedUrl.hashCode()}",
            name = cleanName,
            tagline = "Custom online radio stream",
            category = "Custom",
            frequencyLabel = "LIVE",
            streamUrl = trimmedUrl
        )
        val currentStations = _state.value.stations.filterNot { it.streamUrl == trimmedUrl }
        val updatedStations = listOf(customStation) + currentStations
        _state.update { it.copy(stations = updatedStations, currentIndex = 0) }
        selectAndPlay(0)
    }

    private fun startStream(station: RadioStation) {
        releasePlayerOnly()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setOnPreparedListener { mp ->
                    if (_state.value.currentStation.id == station.id &&
                        _state.value.status == RadioStatus.BUFFERING
                    ) {
                        preparedStationId = station.id
                        runCatching { mp.start() }
                            .onSuccess {
                                _state.update {
                                    it.copy(status = RadioStatus.PLAYING, errorMessage = null)
                                }
                            }
                            .onFailure { err ->
                                _state.update {
                                    it.copy(
                                        status = RadioStatus.ERROR,
                                        errorMessage = err.localizedMessage ?: "Unable to start audio playback."
                                    )
                                }
                            }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    _state.update {
                        it.copy(
                            status = RadioStatus.ERROR,
                            errorMessage = "Could not connect to ${station.name}. Try another station."
                        )
                    }
                    true
                }
                setDataSource(station.streamUrl)
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = RadioStatus.ERROR,
                    errorMessage = e.localizedMessage ?: "Unable to load ${station.name}."
                )
            }
        }
    }

    private fun releasePlayerOnly() {
        preparedStationId = null
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    fun release() {
        releasePlayerOnly()
        _state.update { it.copy(status = RadioStatus.IDLE, errorMessage = null) }
    }
}
