package com.example

import com.example.mp3.Id3v2Editor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Id3v2EditorTest {

    @Test
    fun `synchsafe conversion is symmetric`() {
        val testValues = listOf(0, 1, 127, 128, 255, 1024, 65536, 1000000)
        for (v in testValues) {
            val synchsafeBytes = Id3v2Editor.toSynchsafe(v)
            val decoded = Id3v2Editor.readSynchsafe(synchsafeBytes, 0)
            assertEquals(v, decoded)
        }
    }

    @Test
    fun `inspect detects missing cover on raw audio bytes`() {
        // Raw dummy audio bytes without ID3 header
        val rawAudio = ByteArray(500) { 0xFF.toByte() }
        val inspection = Id3v2Editor.inspect(rawAudio)
        assertFalse(inspection.hasId3v2)
        assertFalse(inspection.hasCover)
    }

    @Test
    fun `embedCoverArt creates valid ID3v2 header with APIC frame and preserves audio`() {
        val rawAudio = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(), 0x12, 0x34)
        val dummyCover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x55, 0x66)

        val updatedMp3 = Id3v2Editor.embedCoverArt(
            sourceBytes = rawAudio,
            coverImageBytes = dummyCover,
            fallbackTitle = "Test Song",
            fallbackArtist = "Test Artist"
        )

        // Inspect the updated MP3
        val inspection = Id3v2Editor.inspect(updatedMp3)
        assertTrue(inspection.hasId3v2)
        assertEquals(3, inspection.versionMajor)
        assertTrue(inspection.hasCover)
        assertNotNull(inspection.coverBytes)
        assertEquals(dummyCover.size, inspection.coverBytes?.size)
        assertEquals("Test Song", inspection.title)
        assertEquals("Test Artist", inspection.artist)

        // Check that audio bytes at the end are exactly identical
        val audioOffset = inspection.audioDataOffset
        val extractedAudio = updatedMp3.copyOfRange(audioOffset, updatedMp3.size)
        assertEquals(rawAudio.size, extractedAudio.size)
        for (i in rawAudio.indices) {
            assertEquals(rawAudio[i], extractedAudio[i])
        }
    }
}
