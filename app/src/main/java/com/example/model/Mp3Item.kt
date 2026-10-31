package com.example.model

import android.net.Uri
import com.example.mp3.AudioFormat

enum class ProcessingStatus {
    IDLE,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Representation of an audio track (MP3, M4A voice recording, etc.) in the app.
 */
data class Mp3Item(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val hasOriginalCover: Boolean,
    val originalCoverBytes: ByteArray? = null,
    val hasProcessedCover: Boolean = false,
    val processedUri: Uri? = null,
    val processedCoverBytes: ByteArray? = null,
    val status: ProcessingStatus = ProcessingStatus.IDLE,
    val errorMessage: String? = null,
    val isSample: Boolean = false,
    val sampleRate: Int = 44100,
    val bitrateKbps: Int = 128,
    val audioFormat: AudioFormat = AudioFormat.MP3,
    val recordedAt: Long? = null,
    val recordSource: String? = null,
    val volumeBoostPercent: Int = 130,
    val channelInfo: String = "Stereo (2 Ch)",
    val isRecordedTrack: Boolean = false,
    val savedPathInfo: String? = null
) {
    val effectiveCoverBytes: ByteArray?
        get() = processedCoverBytes ?: originalCoverBytes

    val effectiveUri: Uri
        get() = processedUri ?: uri

    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "-- KB"
            val kb = sizeBytes / 1024.0
            return if (kb < 1024) {
                "%.1f KB".format(kb)
            } else {
                "%.2f MB".format(kb / 1024.0)
            }
        }

    val formattedRecordedDate: String?
        get() = if (recordedAt != null && recordedAt > 0) {
            try {
                java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id", "ID")).format(java.util.Date(recordedAt))
            } catch (_: Exception) {
                null
            }
        } else null

    val needsCover: Boolean
        get() = !hasOriginalCover && !hasProcessedCover

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Mp3Item
        return id == other.id &&
                hasProcessedCover == other.hasProcessedCover &&
                status == other.status &&
                errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + hasProcessedCover.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}
