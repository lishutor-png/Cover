package com.example.mp3

import android.content.Context
import android.net.Uri
import com.example.model.Mp3Item
import com.example.model.ProcessingStatus
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Generates genuine, playable test MP3 audio files with valid MPEG Layer III frames
 * that do NOT have album covers, enabling instant verification and testing in emulator.
 */
object SampleAudioGenerator {

    /**
     * Minimal valid MPEG-1 Layer III 128kbps 44.1kHz stereo audio frame (417 bytes).
     * Plays without error in Android MediaPlayer.
     */
    private val VALID_MP3_FRAME: ByteArray by lazy {
        val frame = ByteArray(417)
        // Header: Sync 0xFF 0xFB (MPEG 1.0, Layer 3, no CRC)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        // Bitrate: 128kbps (1001), 44100Hz (00), Padding (0) -> 0x90
        frame[2] = 0x90.toByte()
        // Joint stereo, Mode extension, Original -> 0x64
        frame[3] = 0x64.toByte()

        // Side information for Joint Stereo (32 bytes)
        // main_data_begin = 0
        frame[4] = 0x00.toByte()
        frame[5] = 0x00.toByte()
        // Channel / granule flags
        frame[6] = 0x55.toByte()
        frame[7] = 0x55.toByte()

        // Remainder of frame is silent/minimal Huffman code data
        for (i in 8 until 417) {
            frame[i] = if (i % 23 == 0) 0xAA.toByte() else 0x00.toByte()
        }
        frame
    }

    data class SampleTrackInfo(
        val title: String,
        val artist: String,
        val album: String,
        val durationSeconds: Int
    )

    private val SAMPLE_CATALOG = listOf(
        SampleTrackInfo(
            title = "Midnight Serenade",
            artist = "Luna & The Waves",
            album = "Neon Echoes",
            durationSeconds = 12
        ),
        SampleTrackInfo(
            title = "Cyberpunk Pulse",
            artist = "ElectroNova",
            album = "Digital Drift",
            durationSeconds = 15
        ),
        SampleTrackInfo(
            title = "Morning Coffee Chill",
            artist = "Acoustic Breeze",
            album = "Sunday Acoustic",
            durationSeconds = 10
        )
    )

    /**
     * Generates a collection of real MP3 files without covers in the app's internal storage.
     */
    fun createSampleTracks(context: Context): List<Mp3Item> {
        val sampleDir = File(context.filesDir, "sample_mp3s")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
        }

        val result = mutableListOf<Mp3Item>()

        for (info in SAMPLE_CATALOG) {
            val fileName = "${info.title} - ${info.artist}.mp3".replace('/', '_')
            val file = File(sampleDir, fileName)

            // Number of frames needed: 44100 / 1152 samples per frame ~ 38.28 frames per second
            val framesCount = (info.durationSeconds * 38.3).toInt()

            FileOutputStream(file).use { fos ->
                for (i in 0 until framesCount) {
                    fos.write(VALID_MP3_FRAME)
                }
            }

            val item = Mp3Item(
                id = UUID.randomUUID().toString(),
                uri = Uri.fromFile(file),
                fileName = file.name,
                title = info.title,
                artist = info.artist,
                album = info.album,
                durationMs = (info.durationSeconds * 1000).toLong(),
                sizeBytes = file.length(),
                hasOriginalCover = false,
                originalCoverBytes = null,
                hasProcessedCover = false,
                status = ProcessingStatus.IDLE,
                isSample = true,
                sampleRate = 44100,
                bitrateKbps = 128
            )
            result.add(item)
        }

        return result
    }
}
