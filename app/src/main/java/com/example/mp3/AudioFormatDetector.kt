package com.example.mp3

import java.io.File

enum class AudioFormat(val displayName: String, val extension: String) {
    MP3("MP3", "mp3"),
    M4A("M4A (Rekaman)", "m4a"),
    WAV("WAV", "wav"),
    UNKNOWN("Audio", "mp3")
}

object AudioFormatDetector {

    /**
     * Detects audio container format from binary header bytes and file extension.
     */
    fun detect(bytes: ByteArray, fileName: String? = null): AudioFormat {
        if (bytes.size >= 8) {
            // Check for M4A / MP4 container ("ftyp" box at offset 4..7)
            val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
            if (boxType == "ftyp") {
                return AudioFormat.M4A
            }

            // Check for WAV ("RIFF" at 0..3 and "WAVE" at 8..11)
            if (bytes.size >= 12) {
                val riff = String(bytes, 0, 4, Charsets.ISO_8859_1)
                val wave = String(bytes, 8, 4, Charsets.ISO_8859_1)
                if (riff == "RIFF" && wave == "WAVE") {
                    return AudioFormat.WAV
                }
            }

            // Check for ID3v2 header at start of MP3
            val id3 = String(bytes, 0, 3, Charsets.ISO_8859_1)
            if (id3 == "ID3") {
                return AudioFormat.MP3
            }

            // Check for MPEG audio frame sync (0xFF followed by 0xEx or 0xFx)
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val layer = (b1 shr 1) and 0x03
                if (layer != 0) {
                    return AudioFormat.MP3
                }
            }
        }

        // Fallback to filename extension
        if (!fileName.isNullOrBlank()) {
            val lower = fileName.lowercase()
            return when {
                lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".mp4") || lower.endsWith(".3gp") -> AudioFormat.M4A
                lower.endsWith(".wav") -> AudioFormat.WAV
                lower.endsWith(".mp3") -> AudioFormat.MP3
                else -> AudioFormat.UNKNOWN
            }
        }

        return AudioFormat.UNKNOWN
    }
}
