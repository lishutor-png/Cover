package com.example.mp3

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import java.io.FileOutputStream
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
    val isCaptureAuthorized: Boolean = false,
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
    const val VOLUME_BOOST_MULTIPLIER = 1.30f // Boost volume by 130%

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null

    private var audioRecord: AudioRecord? = null
    private var pcmToAacMuxer: PcmToAacMuxer? = null
    private var mediaProjection: MediaProjection? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var activeOutputFile: File? = null
    private var activeSource: AudioRecordSource = AudioRecordSource.INTERNAL_APP

    // Cached pre-authorized MediaProjection token so user doesn't have to confirm transmission when hitting record!
    private var preAuthorizedToken: Pair<Int, Intent>? = null

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

    fun setPreAuthorizedToken(resultCode: Int, data: Intent) {
        preAuthorizedToken = Pair(resultCode, data)
        _recorderState.value = _recorderState.value.copy(isCaptureAuthorized = true)
    }

    fun hasPreAuthorizedToken(): Boolean = preAuthorizedToken != null

    fun getPreAuthorizedToken(): Pair<Int, Intent>? = preAuthorizedToken

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
     * Starts internal audio capture using cached pre-authorized token or provided token.
     */
    fun startInternalAudioRecording(resultCode: Int? = null, data: Intent? = null): Boolean {
        val context = appContext ?: return false
        stopCurrentSession()

        val finalCode: Int
        val finalIntent: Intent

        if (resultCode != null && data != null) {
            finalCode = resultCode
            finalIntent = data
            setPreAuthorizedToken(resultCode, data)
        } else {
            val token = preAuthorizedToken
            if (token != null) {
                finalCode = token.first
                finalIntent = token.second
            } else {
                CapturePermissionActivity.launch(context, autoStart = true)
                return false
            }
        }

        try {
            AudioCaptureForegroundService.start(context)

            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (projectionManager == null) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "MediaProjectionManager tidak tersedia pada perangkat ini."
                )
                return false
            }

            val projection = projectionManager.getMediaProjection(finalCode, finalIntent)
            if (projection == null) {
                _recorderState.value = _recorderState.value.copy(
                    errorMessage = "Izin transmisi audio tidak valid atau kadaluarsa."
                )
                preAuthorizedToken = null
                _recorderState.value = _recorderState.value.copy(isCaptureAuthorized = false)
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

            activeSource = source
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
                    // Apply 130% Volume Boost with soft-clipping protection
                    var maxAmp = 0
                    for (i in 0 until read step 2) {
                        val low = buffer[i].toInt() and 0xFF
                        val high = buffer[i + 1].toInt()
                        val rawSample = (low or (high shl 8)).toShort()

                        // Volume boost of 130% (1.30f multiplier)
                        val boosted = (rawSample * VOLUME_BOOST_MULTIPLIER).coerceIn(-32768f, 32767f).toInt().toShort()

                        buffer[i] = (boosted.toInt() and 0xFF).toByte()
                        buffer[i + 1] = ((boosted.toInt() shr 8) and 0xFF).toByte()

                        val absVal = abs(boosted.toInt())
                        if (absVal > maxAmp) maxAmp = absVal
                    }

                    muxer.feedPcmData(buffer, read, isEndOfStream = false)

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
     * saves to public Music/recLish folder, and emits the new Mp3Item to listeners.
     */
    fun stopAndSaveRecording(
        autoAddCover: Boolean = true,
        onComplete: ((Mp3Item?) -> Unit)? = null
    ) {
        val context = appContext ?: return
        val currentSource = activeSource
        val recordTimestamp = System.currentTimeMillis()

        scope.launch(Dispatchers.IO) {
            val recordedFile = stopRecording()
            if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
                val initialItem = Mp3Processor.parseFromUri(context, Uri.fromFile(recordedFile)).copy(
                    isRecordedTrack = true,
                    recordedAt = recordTimestamp,
                    recordSource = if (currentSource == AudioRecordSource.INTERNAL_APP) "Output Aplikasi (Internal)" else "Mikrofon HD",
                    volumeBoostPercent = 130,
                    channelInfo = "Stereo (48kHz, 192kbps)"
                )

                val finalItem = if (autoAddCover) {
                    Mp3Processor.applyCoverArt(
                        context = context,
                        item = initialItem
                    )
                } else {
                    initialItem
                }

                // Automatically export to device's public Music/recLish folder and notify MediaScanner!
                val exportedPath = exportToPublicMusicFolder(context, finalItem)
                val enrichedItem = finalItem.copy(
                    isRecordedTrack = true,
                    recordedAt = recordTimestamp,
                    recordSource = if (currentSource == AudioRecordSource.INTERNAL_APP) "Output Aplikasi (Internal)" else "Mikrofon HD",
                    volumeBoostPercent = 130,
                    channelInfo = "Stereo (48kHz, 192kbps)",
                    savedPathInfo = exportedPath ?: finalItem.effectiveUri.path
                )

                setLastRecordedItem(enrichedItem)
                _newlyRecordedTracks.emit(enrichedItem)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Rekaman '${enrichedItem.title}' disimpan (Volume +130% & Cover Tersemat)!",
                        Toast.LENGTH_LONG
                    ).show()
                    onComplete?.invoke(enrichedItem)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Perekaman dibatalkan atau file kosong.", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    /**
     * Exports a copy to Environment.DIRECTORY_MUSIC/recLish so it is immediately visible
     * in the phone's music player and file manager.
     */
    fun exportToPublicMusicFolder(context: Context, item: Mp3Item): String? {
        return try {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "recLish")
            if (!musicDir.exists()) musicDir.mkdirs()
            val targetFile = File(musicDir, item.fileName)
            val sourceUri = item.effectiveUri
            val inStream = if (sourceUri.scheme == "file") {
                File(sourceUri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(sourceUri)
            }
            if (inStream != null) {
                inStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Notify Android MediaScanner so phone music player immediately indexes the file & cover art
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(if (item.audioFormat == com.example.mp3.AudioFormat.M4A) "audio/mp4" else "audio/mpeg"),
                    null
                )
                targetFile.absolutePath
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Gagal ekspor ke folder Musik publik", e)
            null
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        timerJob = null

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null

        try {
            pcmToAacMuxer?.cancel()
        } catch (_: Exception) {}
        pcmToAacMuxer = null

        activeOutputFile?.let {
            if (it.exists()) it.delete()
        }
        activeOutputFile = null

        appContext?.let { AudioCaptureForegroundService.stop(it) }

        _recorderState.value = _recorderState.value.copy(
            isRecording = false,
            isPaused = false,
            elapsedSeconds = 0,
            currentAmplitude = 0f,
            currentFile = null,
            waveformHistory = List(16) { 0.08f }
        )
    }

    private fun stopCurrentSession() {
        if (_recorderState.value.isRecording) {
            cancelRecording()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive && _recorderState.value.isRecording) {
                delay(1000)
                if (!_recorderState.value.isPaused) {
                    _recorderState.value = _recorderState.value.copy(
                        elapsedSeconds = _recorderState.value.elapsedSeconds + 1
                    )
                }
            }
        }
    }
}
