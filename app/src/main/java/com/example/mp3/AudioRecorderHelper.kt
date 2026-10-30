package com.example.mp3

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.model.Mp3Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class AudioRecordSource(val label: String, val description: String) {
    INTERNAL_APP(
        label = "Audio Aplikasi (Internal)",
        description = "Merekam output audio aplikasi/musik/game secara jernih tanpa bising luar"
    ),
    MICROPHONE(
        label = "Mikrofon HD",
        description = "Merekam suara vokal dari mikrofon dengan fidelitas tinggi 48kHz"
    )
}

data class RecorderState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val source: AudioRecordSource = AudioRecordSource.INTERNAL_APP,
    val elapsedSeconds: Int = 0,
    val currentAmplitude: Float = 0f,
    val waveformHistory: List<Float> = List(16) { 0.08f },
    val currentFile: File? = null,
    val errorMessage: String? = null,
    val isBubbleVisible: Boolean = false,
    val isBubbleMinimized: Boolean = false,
    val isSystemOverlayActive: Boolean = false,
    val lastRecordedItem: Mp3Item? = null
)

/**
 * Global recording engine singleton shared across in-app UI and background system floating overlay.
 */
object AudioRecorderEngine {
    private const val TAG = "AudioRecorderEngine"

    const val SAMPLE_RATE = 48000
    const val CHANNEL_COUNT = 2
    const val BIT_RATE = 192000

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null

    private var audioRecord: AudioRecord? = null
    private var pcmToAacMuxer: PcmToAacMuxer? = null
    private var mediaProjection: MediaProjection? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var activeOutputFile: File? = null

    private val _recorderState = MutableStateFlow(RecorderState())
    val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

    private val _newlyRecordedTracks = MutableSharedFlow<Mp3Item>(extraBufferCapacity = 10)
    val newlyRecordedTracks: SharedFlow<Mp3Item> = _newlyRecordedTracks.asSharedFlow()

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun setSystemOverlayActive(active: Boolean) {
        _recorderState.value = _recorderState.value.copy(isSystemOverlayActive = active)
    }

    fun showBubble() {
        _recorderState.value = _recorderState.value.copy(
            isBubbleVisible = true,
            isBubbleMinimized = false
        )
    }

    fun hideBubble() {
        if (!_recorderState.value.isRecording) {
            _recorderState.value = _recorderState.value.copy(isBubbleVisible = false)
        } else {
            _recorderState.value = _recorderState.value.copy(isBubbleMinimized = true)
        }
    }

    fun toggleMinimizeBubble() {
        _recorderState.value = _recorderState.value.copy(
            isBubbleMinimized = !_recorderState.value.isBubbleMinimized
        )
    }

    fun setSource(source: AudioRecordSource) {
        if (!_recorderState.value.isRecording) {
            _recorderState.value = _recorderState.value.copy(source = source, errorMessage = null)
        }
    }

    fun clearLastRecordedItem() {
        _recorderState.value = _recorderState.value.copy(lastRecordedItem = null)
    }

    fun setLastRecordedItem(item: Mp3Item) {
        _recorderState.value = _recorderState.value.copy(lastRecordedItem = item)
    }

    fun onInternalAudioPermissionFailed(reason: String) {
        _recorderState.value = _recorderState.value.copy(
            isRecording = false,
            errorMessage = reason
        )
        appContext?.let {
            Toast.makeText(it, "Perekam: $reason", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Starts internal audio capture using MediaProjection (AudioPlaybackCapture API).
     */
    fun startInternalAudioRecording(resultCode: Int, data: Intent): Boolean {
        val context = appContext ?: return false
        stopCurrentSession()

        try {
            AudioCaptureForegroundService.start(context)

            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "MediaProjectionManager tidak tersedia pada perangkat ini."
                )
                return false
            }

            val projection = projectionManager.getMediaProjection(resultCode, data)
            if (projection == null) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "Izin merekam audio internal ditolak atau tidak valid."
                )
                return false
            }
            mediaProjection = projection

            val record = createInternalAudioRecord(projection)
            if (record == null) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "Gagal membuat AudioRecord untuk AudioPlaybackCapture."
                )
                return false
            }

            return beginRecordingStream(record, AudioRecordSource.INTERNAL_APP)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai internal audio recording", e)
            _recorderState.value = _recorderState.value.copy(
                isRecording = false,
                errorMessage = "Gagal merekam audio aplikasi: ${e.localizedMessage}"
            )
            return false
        }
    }

    /**
     * Starts microphone recording in HD (48kHz, stereo AAC).
     */
    fun startMicRecording(): Boolean {
        val context = appContext ?: return false
        stopCurrentSession()

        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuf.coerceAtLeast(16384)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "Mikrofon tidak dapat diinisialisasi atau sedang digunakan aplikasi lain."
                )
                return false
            }

            return beginRecordingStream(record, AudioRecordSource.MICROPHONE)
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai mic recording", e)
            _recorderState.value = _recorderState.value.copy(
                isRecording = false,
                errorMessage = "Gagal memulai mikrofon: ${e.localizedMessage}"
            )
            return false
        }
    }

    private fun createInternalAudioRecord(projection: MediaProjection): AudioRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBuf.coerceAtLeast(16384)

        return AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
    }

    private fun beginRecordingStream(record: AudioRecord, source: AudioRecordSource): Boolean {
        val context = appContext ?: return false
        try {
            val recordDir = File(context.filesDir, "recordings")
            if (!recordDir.exists()) recordDir.mkdirs()

            val prefix = if (source == AudioRecordSource.INTERNAL_APP) "Audio_Aplikasi" else "Suara_Mic"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(recordDir, "${prefix}_$timestamp.m4a")
            activeOutputFile = file

            val muxer = PcmToAacMuxer(
                outputFile = file,
                sampleRate = SAMPLE_RATE,
                channelCount = CHANNEL_COUNT,
                bitRate = BIT_RATE
            )
            muxer.init()
            pcmToAacMuxer = muxer

            record.startRecording()
            audioRecord = record

            _recorderState.value = _recorderState.value.copy(
                isRecording = true,
                isPaused = false,
                source = source,
                elapsedSeconds = 0,
                currentFile = file,
                errorMessage = null,
                isBubbleVisible = true
            )

            startTimer()
            startAudioStreamPump(record, muxer)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memulai stream perekaman", e)
            _recorderState.value = _recorderState.value.copy(
                isRecording = false,
                errorMessage = "Gagal memulai perekam: ${e.localizedMessage}"
            )
            stopCurrentSession()
            return false
        }
    }

    private fun startAudioStreamPump(record: AudioRecord, muxer: PcmToAacMuxer) {
        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            val history = ArrayDeque<Float>(List(16) { 0.08f })

            while (isActive) {
                if (_recorderState.value.isPaused) {
                    delay(50)
                    continue
                }

                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    muxer.feedPcmData(buffer, read, isEndOfStream = false)

                    var maxAmp = 0
                    for (i in 0 until read step 2) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        val absVal = abs(sample.toShort().toInt())
                        if (absVal > maxAmp) maxAmp = absVal
                    }
                    val normalized = (maxAmp / 32768f).coerceIn(0.08f, 1f)

                    history.removeFirst()
                    history.addLast(normalized)

                    _recorderState.value = _recorderState.value.copy(
                        currentAmplitude = normalized,
                        waveformHistory = history.toList()
                    )
                } else if (read < 0) {
                    delay(20)
                }
            }
        }
    }

    fun pauseRecording() {
        if (_recorderState.value.isRecording && !_recorderState.value.isPaused) {
            _recorderState.value = _recorderState.value.copy(isPaused = true)
        }
    }

    fun resumeRecording() {
        if (_recorderState.value.isRecording && _recorderState.value.isPaused) {
            _recorderState.value = _recorderState.value.copy(isPaused = false)
        }
    }

    fun stopRecording(): File? {
        timerJob?.cancel()
        timerJob = null

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        val finishedFile = pcmToAacMuxer?.finishAndRelease()
        pcmToAacMuxer = null

        appContext?.let { AudioCaptureForegroundService.stop(it) }

        _recorderState.value = _recorderState.value.copy(
            isRecording = false,
            isPaused = false,
            elapsedSeconds = 0,
            currentAmplitude = 0f,
            currentFile = null,
            waveformHistory = List(16) { 0.08f }
        )

        return if (finishedFile != null && finishedFile.exists() && finishedFile.length() > 0) {
            finishedFile
        } else {
            activeOutputFile?.let { if (it.exists() && it.length() > 0) it else null }
        }
    }

    /**
     * Stops the active recording, parses metadata, generates and embeds cover art,
     * emits the new Mp3Item to listeners, and displays an informative toast.
     */
    fun stopAndSaveRecording(
        autoAddCover: Boolean = true,
        onComplete: ((Mp3Item?) -> Unit)? = null
    ) {
        val context = appContext ?: return
        scope.launch(Dispatchers.IO) {
            val recordedFile = stopRecording()
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                val initialItem = Mp3Processor.parseFromUri(context, Uri.fromFile(recordedFile))
                val finalItem = if (autoAddCover) {
                    val processed = Mp3Processor.applyCoverArt(
                        context = context,
                        item = initialItem
                    )
                    processed
                } else {
                    initialItem
                }

                setLastRecordedItem(finalItem)
                _newlyRecordedTracks.emit(finalItem)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Rekaman '${finalItem.title}' berhasil disimpan & diberi cover!",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete?.invoke(finalItem)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Perekaman dibatalkan atau file kosong.", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        timerJob = null

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        pcmToAacMuxer?.finishAndRelease()
        pcmToAacMuxer = null

        appContext?.let { AudioCaptureForegroundService.stop(it) }

        activeOutputFile?.delete()
        activeOutputFile = null

        _recorderState.value = _recorderState.value.copy(
            isRecording = false,
            isPaused = false,
            elapsedSeconds = 0,
            currentAmplitude = 0f,
            currentFile = null,
            waveformHistory = List(16) { 0.08f },
            errorMessage = null
        )
    }

    private fun stopCurrentSession() {
        cancelRecording()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main) {
            var seconds = 0
            while (isActive) {
                delay(1000)
                if (!_recorderState.value.isPaused) {
                    seconds++
                    _recorderState.value = _recorderState.value.copy(elapsedSeconds = seconds)
                }
            }
        }
    }
}
