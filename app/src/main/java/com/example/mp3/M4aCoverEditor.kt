package com.example.mp3

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin parser and cover art embedder for MPEG-4 Audio (.m4a / .mp4 / .aac) files.
 * Handles the native Apple/ISO MPEG-4 metadata structure (moov -> udta -> meta -> ilst -> covr).
 * Automatically updates 32-bit chunk offsets in 'stco' if 'moov' shifts 'mdat'.
 */
object M4aCoverEditor {

    private const val BOX_FTYP = "ftyp"
    private const val BOX_MOOV = "moov"
    private const val BOX_MDAT = "mdat"
    private const val BOX_UDTA = "udta"
    private const val BOX_META = "meta"
    private const val BOX_HDLR = "hdlr"
    private const val BOX_ILST = "ilst"
    private const val BOX_COVR = "covr"
    private const val BOX_DATA = "data"
    private const val BOX_STCO = "stco"

    data class M4aInspection(
        val isM4a: Boolean,
        val hasCover: Boolean,
        val coverBytes: ByteArray? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null
    )

    /**
     * Inspects M4A file for existing cover art and metadata tags.
     */
    fun inspect(bytes: ByteArray): M4aInspection {
        if (bytes.size < 8) return M4aInspection(isM4a = false, hasCover = false)
        val boxType = String(bytes, 4, 4, Charsets.ISO_8859_1)
        if (boxType != BOX_FTYP) return M4aInspection(isM4a = false, hasCover = false)

        var cursor = 0
        var foundCover: ByteArray? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        while (cursor + 8 <= bytes.size) {
            val size = readUint32(bytes, cursor).toInt()
            if (size < 8 || cursor + size > bytes.size) break
            val type = String(bytes, cursor + 4, 4, Charsets.ISO_8859_1)

            if (type == BOX_MOOV) {
                val moovBytes = bytes.copyOfRange(cursor + 8, cursor + size)
                val ilstBytes = findBoxPayload(moovBytes, listOf(BOX_UDTA, BOX_META, BOX_ILST))
                if (ilstBytes != null) {
                    val parsed = parseIlst(ilstBytes)
                    foundCover = parsed.coverBytes
                    title = parsed.title
                    artist = parsed.artist
                    album = parsed.album
                }
                break
            }
            cursor += size
        }

        return M4aInspection(
            isM4a = true,
            hasCover = foundCover != null,
            coverBytes = foundCover,
            title = title,
            artist = artist,
            album = album
        )
    }

    /**
     * Embeds JPEG or PNG cover art and metadata into an M4A file.
     */
    fun embedCoverArt(
        sourceBytes: ByteArray,
        coverImageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        fallbackTitle: String = "Rekaman Audio",
        fallbackArtist: String = "Suara Pengguna",
        fallbackAlbum: String = "recLish Recordings"
    ): ByteArray {
        val inspection = inspect(sourceBytes)
        val effectiveTitle = inspection.title ?: fallbackTitle
        val effectiveArtist = inspection.artist ?: fallbackArtist
        val effectiveAlbum = inspection.album ?: fallbackAlbum

        // Find top-level boxes: ftyp, moov, mdat, etc.
        data class TopBox(val type: String, val start: Int, val length: Int)
        val boxes = mutableListOf<TopBox>()
        var cursor = 0
        while (cursor + 8 <= sourceBytes.size) {
            val size = readUint32(sourceBytes, cursor).toInt()
            if (size < 8 || cursor + size > sourceBytes.size) break
            val type = String(sourceBytes, cursor + 4, 4, Charsets.ISO_8859_1)
            boxes.add(TopBox(type, cursor, size))
            cursor += size
        }

        val moovBox = boxes.find { it.type == BOX_MOOV } ?: return sourceBytes
        val mdatBox = boxes.find { it.type == BOX_MDAT }

        val moovPayload = sourceBytes.copyOfRange(moovBox.start + 8, moovBox.start + moovBox.length)

        // Separate moov children: keep non-udta boxes
        val nonUdtaChildren = mutableListOf<ByteArray>()
        var moovCursor = 0
        var existingUdtaPayload: ByteArray? = null

        while (moovCursor + 8 <= moovPayload.size) {
            val childSize = readUint32(moovPayload, moovCursor).toInt()
            if (childSize < 8 || moovCursor + childSize > moovPayload.size) break
            val childType = String(moovPayload, moovCursor + 4, 4, Charsets.ISO_8859_1)
            val childBoxBytes = moovPayload.copyOfRange(moovCursor, moovCursor + childSize)

            if (childType == BOX_UDTA) {
                existingUdtaPayload = moovPayload.copyOfRange(moovCursor + 8, moovCursor + childSize)
            } else {
                nonUdtaChildren.add(childBoxBytes)
            }
            moovCursor += childSize
        }

        // Build new ilst items (covr, ©nam, ©ART, ©alb)
        val isPng = mimeType.contains("png", ignoreCase = true)
        val imgTypeFlag = if (isPng) 14 else 13 // 13 = JPEG, 14 = PNG

        val newIlst = ByteArrayOutputStream()
        // 1. covr
        newIlst.write(buildIlstDataBox(BOX_COVR, imgTypeFlag, coverImageBytes))
        // 2. ©nam (Title)
        newIlst.write(buildIlstDataBox("©nam", 1, effectiveTitle.toByteArray(Charsets.UTF_8)))
        // 3. ©ART (Artist)
        newIlst.write(buildIlstDataBox("©ART", 1, effectiveArtist.toByteArray(Charsets.UTF_8)))
        // 4. ©alb (Album)
        newIlst.write(buildIlstDataBox("©alb", 1, effectiveAlbum.toByteArray(Charsets.UTF_8)))

        val newIlstBytes = buildBox(BOX_ILST, newIlst.toByteArray())

        // Build hdlr (Handler Box for metadata)
        val hdlrPayload = byteArrayOf(
            0x00, 0x00, 0x00, 0x00, // version & flags
            0x00, 0x00, 0x00, 0x00, // pre-defined
            'm'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'r'.code.toByte(), // handler type: mdir
            'a'.code.toByte(), 'p'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), // sub-type: appl
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // reserved
            0x00 // name (null terminated empty string)
        )
        val hdlrBox = buildBox(BOX_HDLR, hdlrPayload)

        // Build meta box (FullBox: 4 bytes version/flags 00 00 00 00 + hdlr + ilst)
        val metaStream = ByteArrayOutputStream()
        metaStream.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        metaStream.write(hdlrBox)
        metaStream.write(newIlstBytes)
        val metaBox = buildBox(BOX_META, metaStream.toByteArray())

        // Build udta box
        val udtaStream = ByteArrayOutputStream()
        udtaStream.write(metaBox)
        val udtaBox = buildBox(BOX_UDTA, udtaStream.toByteArray())

        // Build new moov box
        val newMoovStream = ByteArrayOutputStream()
        for (child in nonUdtaChildren) {
            newMoovStream.write(child)
        }
        newMoovStream.write(udtaBox)
        var newMoovPayload = newMoovStream.toByteArray()

        val oldMoovTotalSize = moovBox.length
        val newMoovTotalSize = 8 + newMoovPayload.size
        val delta = newMoovTotalSize - oldMoovTotalSize

        // If moov is placed BEFORE mdat, update chunk offsets in stco
        if (mdatBox != null && moovBox.start < mdatBox.start && delta != 0) {
            newMoovPayload = updateStcoOffsets(newMoovPayload, delta)
        }

        val finalMoovBox = buildBox(BOX_MOOV, newMoovPayload)

        // Assemble file: replace moovBox with finalMoovBox
        val resultStream = ByteArrayOutputStream(sourceBytes.size + delta + 1024)
        for (b in boxes) {
            if (b.type == BOX_MOOV) {
                resultStream.write(finalMoovBox)
            } else {
                resultStream.write(sourceBytes, b.start, b.length)
            }
        }

        return resultStream.toByteArray()
    }

    private fun updateStcoOffsets(moovPayload: ByteArray, delta: Int): ByteArray {
        val copy = moovPayload.copyOf()
        var i = 0
        while (i + 8 <= copy.size) {
            val type = String(copy, i + 4, 4, Charsets.ISO_8859_1)
            if (type == BOX_STCO && i + 16 <= copy.size) {
                val entryCount = readUint32(copy, i + 12).toInt()
                for (c in 0 until entryCount) {
                    val entryOffset = i + 16 + c * 4
                    if (entryOffset + 4 <= copy.size) {
                        val currentVal = readUint32(copy, entryOffset).toInt()
                        writeUint32(copy, entryOffset, currentVal + delta)
                    }
                }
            }
            i++
        }
        return copy
    }

    private fun buildIlstDataBox(tagType: String, typeIndicator: Int, dataBytes: ByteArray): ByteArray {
        val dataStream = ByteArrayOutputStream()
        // type indicator (1 = UTF8 text, 13 = JPEG, 14 = PNG)
        dataStream.write(
            byteArrayOf(
                ((typeIndicator shr 24) and 0xFF).toByte(),
                ((typeIndicator shr 16) and 0xFF).toByte(),
                ((typeIndicator shr 8) and 0xFF).toByte(),
                (typeIndicator and 0xFF).toByte()
            )
        )
        // locale / reserved: 00 00 00 00
        dataStream.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        dataStream.write(dataBytes)

        val dataBox = buildBox(BOX_DATA, dataStream.toByteArray())
        return buildBox(tagType, dataBox)
    }

    private fun buildBox(type: String, payload: ByteArray): ByteArray {
        val totalSize = 8 + payload.size
        val out = ByteArray(totalSize)
        out[0] = ((totalSize shr 24) and 0xFF).toByte()
        out[1] = ((totalSize shr 16) and 0xFF).toByte()
        out[2] = ((totalSize shr 8) and 0xFF).toByte()
        out[3] = (totalSize and 0xFF).toByte()
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        System.arraycopy(typeBytes, 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    private fun findBoxPayload(bytes: ByteArray, path: List<String>): ByteArray? {
        if (path.isEmpty()) return bytes
        val target = path.first()
        val remaining = path.drop(1)

        var cursor = 0
        while (cursor + 8 <= bytes.size) {
            val size = readUint32(bytes, cursor).toInt()
            if (size < 8 || cursor + size > bytes.size) break
            val type = String(bytes, cursor + 4, 4, Charsets.ISO_8859_1)

            if (type == target) {
                // If it's a meta box, skip the 4-byte version/flags if present
                val payloadStart = if (type == BOX_META && cursor + 12 <= bytes.size) {
                    cursor + 12
                } else {
                    cursor + 8
                }
                val payload = bytes.copyOfRange(payloadStart, cursor + size)
                return findBoxPayload(payload, remaining)
            }
            cursor += size
        }
        return null
    }

    private fun parseIlst(ilstBytes: ByteArray): M4aInspection {
        var cursor = 0
        var coverBytes: ByteArray? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        while (cursor + 8 <= ilstBytes.size) {
            val size = readUint32(ilstBytes, cursor).toInt()
            if (size < 8 || cursor + size > ilstBytes.size) break
            val type = String(ilstBytes, cursor + 4, 4, Charsets.ISO_8859_1)
            val tagPayload = ilstBytes.copyOfRange(cursor + 8, cursor + size)

            val dataContent = extractDataBoxContent(tagPayload)
            if (dataContent != null) {
                when (type) {
                    BOX_COVR -> coverBytes = dataContent
                    "©nam", "titl" -> title = String(dataContent, Charsets.UTF_8).trimEnd('\u0000')
                    "©ART", "perf" -> artist = String(dataContent, Charsets.UTF_8).trimEnd('\u0000')
                    "©alb" -> album = String(dataContent, Charsets.UTF_8).trimEnd('\u0000')
                }
            }
            cursor += size
        }

        return M4aInspection(
            isM4a = true,
            hasCover = coverBytes != null,
            coverBytes = coverBytes,
            title = title,
            artist = artist,
            album = album
        )
    }

    private fun extractDataBoxContent(tagPayload: ByteArray): ByteArray? {
        var cursor = 0
        while (cursor + 8 <= tagPayload.size) {
            val size = readUint32(tagPayload, cursor).toInt()
            if (size < 8 || cursor + size > tagPayload.size) break
            val type = String(tagPayload, cursor + 4, 4, Charsets.ISO_8859_1)
            if (type == BOX_DATA && cursor + 16 <= tagPayload.size) {
                // 'data' box: 4 bytes size, 4 bytes 'data', 4 bytes type, 4 bytes locale, then payload
                return tagPayload.copyOfRange(cursor + 16, cursor + size)
            }
            cursor += size
        }
        return null
    }

    private fun readUint32(b: ByteArray, offset: Int): Long {
        return ((b[offset].toLong() and 0xFF) shl 24) or
                ((b[offset + 1].toLong() and 0xFF) shl 16) or
                ((b[offset + 2].toLong() and 0xFF) shl 8) or
                (b[offset + 3].toLong() and 0xFF)
    }

    private fun writeUint32(b: ByteArray, offset: Int, value: Int) {
        b[offset] = ((value shr 24) and 0xFF).toByte()
        b[offset + 1] = ((value shr 16) and 0xFF).toByte()
        b[offset + 2] = ((value shr 8) and 0xFF).toByte()
        b[offset + 3] = (value and 0xFF).toByte()
    }
}
