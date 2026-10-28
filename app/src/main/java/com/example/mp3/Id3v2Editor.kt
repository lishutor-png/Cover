package com.example.mp3

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin ID3v2.3 / ID3v2.4 parser and editor for MP3 files.
 * Accurately parses existing metadata, embeds APIC (Attached Picture) frames,
 * and preserves audio bitstream intact without corruption.
 */
object Id3v2Editor {

    private const val ID3_MAGIC = "ID3"
    private const val FRAME_APIC = "APIC"
    private const val FRAME_TIT2 = "TIT2"
    private const val FRAME_TPE1 = "TPE1"
    private const val FRAME_TALB = "TALB"

    data class TagInspectionResult(
        val hasId3v2: Boolean,
        val versionMajor: Int,
        val hasCover: Boolean,
        val coverBytes: ByteArray? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val audioDataOffset: Int = 0
    )

    /**
     * Inspects MP3 binary data to check for ID3v2 header and APIC cover art frame.
     */
    fun inspect(mp3Bytes: ByteArray): TagInspectionResult {
        if (mp3Bytes.size < 10) {
            return TagInspectionResult(hasId3v2 = false, versionMajor = 0, hasCover = false, audioDataOffset = 0)
        }

        val magic = String(mp3Bytes, 0, 3, Charsets.ISO_8859_1)
        if (magic != ID3_MAGIC) {
            return TagInspectionResult(hasId3v2 = false, versionMajor = 0, hasCover = false, audioDataOffset = 0)
        }

        val versionMajor = mp3Bytes[3].toInt() and 0xFF
        val flags = mp3Bytes[5].toInt() and 0xFF
        val hasExtendedHeader = (flags and 0x40) != 0
        val tagDataSize = readSynchsafe(mp3Bytes, 6)
        val audioOffset = (10 + tagDataSize).coerceAtMost(mp3Bytes.size)

        var hasCover = false
        var extractedCover: ByteArray? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        var cursor = 10
        if (hasExtendedHeader && cursor + 4 <= audioOffset) {
            val extSize = if (versionMajor == 4) readSynchsafe(mp3Bytes, cursor) else readInt32BE(mp3Bytes, cursor)
            cursor += extSize.coerceAtLeast(4)
        }

        while (cursor + 10 <= audioOffset) {
            // Check for padding (null bytes)
            if (mp3Bytes[cursor] == 0.toByte()) {
                break
            }

            val frameId = String(mp3Bytes, cursor, 4, Charsets.ISO_8859_1)
            val frameSize = if (versionMajor == 4) {
                readSynchsafe(mp3Bytes, cursor + 4)
            } else {
                readInt32BE(mp3Bytes, cursor + 4)
            }

            cursor += 10
            if (frameSize <= 0 || cursor + frameSize > audioOffset) {
                break
            }

            val frameData = mp3Bytes.copyOfRange(cursor, cursor + frameSize)

            when (frameId) {
                FRAME_APIC -> {
                    hasCover = true
                    extractedCover = parseApicFrame(frameData)
                }
                FRAME_TIT2 -> {
                    title = parseTextFrame(frameData)
                }
                FRAME_TPE1 -> {
                    artist = parseTextFrame(frameData)
                }
                FRAME_TALB -> {
                    album = parseTextFrame(frameData)
                }
            }

            cursor += frameSize
        }

        return TagInspectionResult(
            hasId3v2 = true,
            versionMajor = versionMajor,
            hasCover = hasCover,
            coverBytes = extractedCover,
            title = title,
            artist = artist,
            album = album,
            audioDataOffset = audioOffset
        )
    }

    /**
     * Embeds JPEG/PNG image bytes into the MP3's ID3v2 APIC frame.
     * Preserves existing metadata and original MPEG audio stream completely.
     */
    fun embedCoverArt(
        sourceBytes: ByteArray,
        coverImageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        fallbackTitle: String = "Unknown Title",
        fallbackArtist: String = "Unknown Artist",
        fallbackAlbum: String = "CoverCraft Album"
    ): ByteArray {
        val inspection = inspect(sourceBytes)

        val preservedFrames = mutableListOf<ByteArray>()
        var foundTitle = inspection.title != null
        var foundArtist = inspection.artist != null
        var foundAlbum = inspection.album != null

        // Extract existing non-APIC frames from source if ID3v2 exists
        if (inspection.hasId3v2 && inspection.audioDataOffset > 10) {
            val versionMajor = inspection.versionMajor
            val flags = sourceBytes[5].toInt() and 0xFF
            val hasExtendedHeader = (flags and 0x40) != 0
            val audioOffset = inspection.audioDataOffset

            var cursor = 10
            if (hasExtendedHeader && cursor + 4 <= audioOffset) {
                val extSize = if (versionMajor == 4) readSynchsafe(sourceBytes, cursor) else readInt32BE(sourceBytes, cursor)
                cursor += extSize.coerceAtLeast(4)
            }

            while (cursor + 10 <= audioOffset) {
                if (sourceBytes[cursor] == 0.toByte()) {
                    break
                }
                val frameId = String(sourceBytes, cursor, 4, Charsets.ISO_8859_1)
                val frameSize = if (versionMajor == 4) {
                    readSynchsafe(sourceBytes, cursor + 4)
                } else {
                    readInt32BE(sourceBytes, cursor + 4)
                }

                val totalFrameBytes = 10 + frameSize
                if (frameSize <= 0 || cursor + totalFrameBytes > audioOffset) {
                    break
                }

                if (frameId != FRAME_APIC) {
                    // Convert frame to ID3v2.3 standard (normal 32-bit BE length)
                    val frameContent = sourceBytes.copyOfRange(cursor + 10, cursor + totalFrameBytes)
                    preservedFrames.add(buildFrame(frameId, frameContent))
                }

                cursor += totalFrameBytes
            }
        }

        // Add text frames if not present
        if (!foundTitle) {
            preservedFrames.add(buildTextFrame(FRAME_TIT2, fallbackTitle))
        }
        if (!foundArtist) {
            preservedFrames.add(buildTextFrame(FRAME_TPE1, fallbackArtist))
        }
        if (!foundAlbum) {
            preservedFrames.add(buildTextFrame(FRAME_TALB, fallbackAlbum))
        }

        // Build new APIC frame
        val apicFrame = buildApicFrame(coverImageBytes, mimeType)
        preservedFrames.add(apicFrame)

        // Calculate total frames size
        val framesOut = ByteArrayOutputStream()
        for (f in preservedFrames) {
            framesOut.write(f)
        }
        // Add 128 bytes padding for future in-place edits
        val padding = ByteArray(128)
        framesOut.write(padding)

        val allFrames = framesOut.toByteArray()
        val tagSize = allFrames.size

        // Build 10-byte ID3v2.3 header
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03.toByte() // ID3v2.3
        header[4] = 0x00.toByte() // revision
        header[5] = 0x00.toByte() // flags
        val synchsafeSize = toSynchsafe(tagSize)
        System.arraycopy(synchsafeSize, 0, header, 6, 4)

        // Audio payload (everything after previous ID3 header)
        val audioOffset = inspection.audioDataOffset
        val audioLength = sourceBytes.size - audioOffset

        val resultOut = ByteArrayOutputStream(header.size + allFrames.size + audioLength)
        resultOut.write(header)
        resultOut.write(allFrames)
        if (audioLength > 0) {
            resultOut.write(sourceBytes, audioOffset, audioLength)
        }

        return resultOut.toByteArray()
    }

    private fun buildApicFrame(imageBytes: ByteArray, mimeType: String): ByteArray {
        val out = ByteArrayOutputStream()
        // Text encoding: 0x00 = ISO-8859-1
        out.write(0x00)
        // MIME type (null terminated)
        out.write(mimeType.toByteArray(Charsets.ISO_8859_1))
        out.write(0x00)
        // Picture type: 0x03 = Cover (front)
        out.write(0x03)
        // Description (empty, null terminated)
        out.write(0x00)
        // Image binary data
        out.write(imageBytes)

        return buildFrame(FRAME_APIC, out.toByteArray())
    }

    private fun buildTextFrame(frameId: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        // Text encoding: 0x03 = UTF-8
        out.write(0x03)
        out.write(text.toByteArray(Charsets.UTF_8))
        return buildFrame(frameId, out.toByteArray())
    }

    private fun buildFrame(frameId: String, frameContent: ByteArray): ByteArray {
        val frameBytes = ByteArray(10 + frameContent.size)
        // Frame ID (4 bytes)
        val idBytes = frameId.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(idBytes, 0, frameBytes, 0, 4.coerceAtMost(idBytes.size))

        // Frame Size in ID3v2.3 (32-bit Big Endian)
        val size = frameContent.size
        frameBytes[4] = ((size shr 24) and 0xFF).toByte()
        frameBytes[5] = ((size shr 16) and 0xFF).toByte()
        frameBytes[6] = ((size shr 8) and 0xFF).toByte()
        frameBytes[7] = (size and 0xFF).toByte()

        // Flags (2 bytes: 0x00, 0x00)
        frameBytes[8] = 0x00
        frameBytes[9] = 0x00

        // Content
        System.arraycopy(frameContent, 0, frameBytes, 10, frameContent.size)
        return frameBytes
    }

    private fun parseApicFrame(frameData: ByteArray): ByteArray? {
        if (frameData.size < 5) return null
        val encoding = frameData[0].toInt() and 0xFF
        var mimeEnd = 1
        while (mimeEnd < frameData.size && frameData[mimeEnd] != 0.toByte()) {
            mimeEnd++
        }
        if (mimeEnd + 2 >= frameData.size) return null

        val pictureType = frameData[mimeEnd + 1].toInt() and 0xFF
        var descEnd = mimeEnd + 2

        // Skip description string
        if (encoding == 1 || encoding == 2) {
            // UTF-16 with BOM: two null bytes terminate
            while (descEnd + 1 < frameData.size) {
                if (frameData[descEnd] == 0.toByte() && frameData[descEnd + 1] == 0.toByte()) {
                    descEnd += 2
                    break
                }
                descEnd += 2
            }
        } else {
            // ISO-8859-1 or UTF-8: single null byte terminates
            while (descEnd < frameData.size && frameData[descEnd] != 0.toByte()) {
                descEnd++
            }
            descEnd += 1
        }

        if (descEnd < frameData.size) {
            return frameData.copyOfRange(descEnd, frameData.size)
        }
        return null
    }

    private fun parseTextFrame(frameData: ByteArray): String? {
        if (frameData.isEmpty()) return null
        val encoding = frameData[0].toInt() and 0xFF
        val textBytes = frameData.copyOfRange(1, frameData.size)
        return try {
            when (encoding) {
                0 -> String(textBytes, Charsets.ISO_8859_1).trimEnd('\u0000')
                1 -> String(textBytes, Charsets.UTF_16).trimEnd('\u0000')
                2 -> String(textBytes, Charsets.UTF_16BE).trimEnd('\u0000')
                3 -> String(textBytes, Charsets.UTF_8).trimEnd('\u0000')
                else -> String(textBytes, Charsets.UTF_8).trimEnd('\u0000')
            }
        } catch (_: Exception) {
            null
        }
    }

    fun readSynchsafe(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0x7F) shl 21) or
                ((b[offset + 1].toInt() and 0x7F) shl 14) or
                ((b[offset + 2].toInt() and 0x7F) shl 7) or
                (b[offset + 3].toInt() and 0x7F)
    }

    fun toSynchsafe(size: Int): ByteArray {
        return byteArrayOf(
            ((size shr 21) and 0x7F).toByte(),
            ((size shr 14) and 0x7F).toByte(),
            ((size shr 7) and 0x7F).toByte(),
            (size and 0x7F).toByte()
        )
    }

    private fun readInt32BE(b: ByteArray, offset: Int): Int {
        return ((b[offset].toInt() and 0xFF) shl 24) or
                ((b[offset + 1].toInt() and 0xFF) shl 16) or
                ((b[offset + 2].toInt() and 0xFF) shl 8) or
                (b[offset + 3].toInt() and 0xFF)
    }
}
