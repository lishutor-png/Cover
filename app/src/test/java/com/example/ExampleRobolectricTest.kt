package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.mp3.AudioFormat
import com.example.mp3.AudioFormatDetector
import com.example.mp3.Id3v2Editor
import com.example.mp3.M4aCoverEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CoverCraft MP3", appName)
    }

    @Test
    fun `test audio format detection`() {
        // M4A bytes (ftyp at offset 4)
        val m4aHeader = byteArrayOf(0, 0, 0, 20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte())
        assertEquals(AudioFormat.M4A, AudioFormatDetector.detect(m4aHeader, "voice_001.m4a"))

        // MP3 with ID3
        val mp3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0, 0, 0, 0, 0)
        assertEquals(AudioFormat.MP3, AudioFormatDetector.detect(mp3Header, "song.mp3"))

        // MP3 raw sync word
        val rawMp3 = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(), 0, 0, 0, 0)
        assertEquals(AudioFormat.MP3, AudioFormatDetector.detect(rawMp3, "recording.mp3"))
    }

    @Test
    fun `test ID3v2 tag editor embeds cover and preserves audio`() {
        // Fake raw MP3 audio stream (10 frames)
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x64.toByte()

        val audioStream = ByteArrayOutputStream()
        for (i in 0 until 10) {
            audioStream.write(frame)
        }
        val rawAudio = audioStream.toByteArray()

        val dummyCover = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val modified = Id3v2Editor.embedCoverArt(
            sourceBytes = rawAudio,
            coverImageBytes = dummyCover,
            mimeType = "image/jpeg",
            fallbackTitle = "Rekaman Saya",
            fallbackArtist = "Pengguna",
            fallbackAlbum = "Koleksi Rekaman"
        )

        val inspection = Id3v2Editor.inspect(modified)
        assertTrue("Must have ID3v2", inspection.hasId3v2)
        assertTrue("Must have cover", inspection.hasCover)
        assertEquals("Rekaman Saya", inspection.title)
        assertEquals("Pengguna", inspection.artist)
        assertNotNull(inspection.coverBytes)
    }

    @Test
    fun `test M4A cover editor embeds and inspects cover`() {
        // Build minimal valid M4A container (ftyp + moov + mdat)
        val ftyp = byteArrayOf(
            0, 0, 0, 20,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
            'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()
        )

        val mdat = byteArrayOf(
            0, 0, 0, 16,
            'm'.code.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(),
            1, 2, 3, 4, 5, 6, 7, 8
        )

        val mvhd = byteArrayOf(
            0, 0, 0, 16,
            'm'.code.toByte(), 'v'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
            0, 0, 0, 0, 0, 0, 0, 0
        )
        val moovStream = ByteArrayOutputStream()
        val moovTotalSize = 8 + mvhd.size
        moovStream.write(byteArrayOf(0, 0, 0, moovTotalSize.toByte(), 'm'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()))
        moovStream.write(mvhd)
        val moov = moovStream.toByteArray()

        val m4aStream = ByteArrayOutputStream()
        m4aStream.write(ftyp)
        m4aStream.write(mdat)
        m4aStream.write(moov)
        val initialM4a = m4aStream.toByteArray()

        val dummyCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 1, 2, 3)
        val modifiedM4a = M4aCoverEditor.embedCoverArt(
            sourceBytes = initialM4a,
            coverImageBytes = dummyCover,
            mimeType = "image/jpeg",
            fallbackTitle = "Suara Rekaman 1",
            fallbackArtist = "Saya",
            fallbackAlbum = "Memo Suara"
        )

        val inspection = M4aCoverEditor.inspect(modifiedM4a)
        assertTrue("Must be M4A", inspection.isM4a)
        assertTrue("Must have cover in M4A", inspection.hasCover)
        assertEquals("Suara Rekaman 1", inspection.title)
        assertEquals("Saya", inspection.artist)
    }
}
