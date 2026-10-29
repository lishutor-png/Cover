package com.example.mp3

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecorderState(
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentFile: File? = null,
    val errorMessage: String? = null
)

class AudioRecorderHelper(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var activeOutputFile: File? = null

    private val _recorderState = MutableStateFlow(RecorderState())
    val recorderState: StateFlow<RecorderState> = _recorderState.asStateFlow()

    fun startRecording(): Boolean {
        stopRecording()

        try {
            val recordDir = File(context.filesDir, "recordings")
            if (!recordDir.exists()) recordDir.mkdirs()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(recordDir, "Rekaman_$timestamp.m4a")
            activeOutputFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            _recorderState.value = RecorderState(
                isRecording = true,
                elapsedSeconds = 0,
                currentFile = file,
                errorMessage = null
            )

            startTimer()
            return true
        } catch (e: Exception) {
            _recorderState.value = RecorderState(
                isRecording = false,
                errorMessage = "Gagal memulai perekam audio: ${e.localizedMessage ?: "Izin atau mik tidak tersedia"}"
            )
            return false
        }
    }

    fun stopRecording(): File? {
        timerJob?.cancel()
        timerJob = null

        val recorded = activeOutputFile
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null

        _recorderState.value = RecorderState(
            isRecording = false,
            elapsedSeconds = 0,
            currentFile = null,
            errorMessage = null
        )

        return if (recorded != null && recorded.exists() && recorded.length() > 0) {
            recorded
        } else {
            null
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        timerJob = null
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        mediaRecorder = null

        activeOutputFile?.delete()
        activeOutputFile = null

        _recorderState.value = RecorderState(
            isRecording = false,
            elapsedSeconds = 0,
            currentFile = null,
            errorMessage = null
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch(Dispatchers.Main) {
            var seconds = 0
            while (isActive) {
                delay(1000)
                seconds++
                _recorderState.value = _recorderState.value.copy(elapsedSeconds = seconds)
            }
        }
    }
}
