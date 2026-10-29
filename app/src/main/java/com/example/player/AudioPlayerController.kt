package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import com.example.model.Mp3Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

data class PlaybackState(
    val currentTrack: Mp3Item? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val isVerifiedPlaying: Boolean = false,
    val verificationMessage: String? = null,
    val errorMessage: String? = null
)

class AudioPlayerController(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var activeFis: FileInputStream? = null
    private var activePfd: ParcelFileDescriptor? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun playOrToggle(item: Mp3Item) {
        val current = _playbackState.value.currentTrack
        if (current?.id == item.id && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == true) {
                pause()
            } else {
                resume()
            }
            return
        }

        playNewTrack(item)
    }

    fun playNewTrack(item: Mp3Item) {
        stop()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                val targetUri = item.effectiveUri
                when {
                    targetUri.scheme == "file" || targetUri.path?.startsWith("/") == true -> {
                        val path = targetUri.path ?: ""
                        val file = File(path)
                        if (!file.exists()) {
                            throw FileNotFoundException("File audio tidak ditemukan: $path")
                        }
                        // Open FileInputStream in app process so mediaserver gets open descriptor
                        val fis = FileInputStream(file)
                        activeFis = fis
                        setDataSource(fis.fd)
                    }
                    targetUri.scheme == "content" -> {
                        val pfd = context.contentResolver.openFileDescriptor(targetUri, "r")
                        if (pfd != null) {
                            activePfd = pfd
                            setDataSource(pfd.fileDescriptor)
                        } else {
                            setDataSource(context, targetUri)
                        }
                    }
                    else -> {
                        setDataSource(context, targetUri)
                    }
                }

                setOnPreparedListener { mp ->
                    mp.start()
                    val dur = mp.duration.coerceAtLeast(item.durationMs.toInt())
                    _playbackState.update {
                        it.copy(
                            currentTrack = item,
                            isPlaying = true,
                            currentPositionMs = 0,
                            durationMs = dur,
                            isVerifiedPlaying = true,
                            verificationMessage = "MP3 Berjalan Baik (${item.sampleRate / 1000} kHz, ${item.bitrateKbps} kbps)",
                            errorMessage = null
                        )
                    }
                    startProgressTracker()
                }

                setOnCompletionListener {
                    _playbackState.update { it.copy(isPlaying = false, currentPositionMs = it.durationMs) }
                    stopProgressTracker()
                }

                setOnErrorListener { _, what, extra ->
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            isVerifiedPlaying = false,
                            errorMessage = "Pemutaran gagal (kode error: $what, $extra)"
                        )
                    }
                    true
                }

                prepareAsync()
            }

            mediaPlayer = player

            _playbackState.update {
                it.copy(
                    currentTrack = item,
                    isPlaying = false,
                    isVerifiedPlaying = false,
                    verificationMessage = "Menyiapkan audio...",
                    errorMessage = null
                )
            }
        } catch (e: Exception) {
            _playbackState.update {
                it.copy(
                    isPlaying = false,
                    isVerifiedPlaying = false,
                    errorMessage = "Gagal memuat MP3: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _playbackState.update { it.copy(isPlaying = false) }
            stopProgressTracker()
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            mediaPlayer?.start()
            _playbackState.update { it.copy(isPlaying = true) }
            startProgressTracker()
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            _playbackState.update { it.copy(currentPositionMs = positionMs) }
        } catch (_: Exception) {}
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            activeFis?.close()
        } catch (_: Exception) {}
        activeFis = null

        try {
            activePfd?.close()
        } catch (_: Exception) {}
        activePfd = null

        _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0) }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        val dur = player.duration.coerceAtLeast(_playbackState.value.durationMs)
                        _playbackState.update {
                            it.copy(currentPositionMs = pos, durationMs = dur)
                        }
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
    }
}
