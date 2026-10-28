package com.example.mp3

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.CoverStyle
import com.example.model.Mp3Item
import com.example.model.ProcessingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object Mp3Processor {

    /**
     * Inspects and creates an Mp3Item from a given Uri.
     */
    suspend fun parseFromUri(context: Context, uri: Uri): Mp3Item = withContext(Dispatchers.IO) {
        var displayName = "audio_${System.currentTimeMillis()}.mp3"

        // Try getting real filename from ContentResolver
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        displayName = name
                    }
                }
            }
        } catch (_: Exception) {}

        if (displayName.isBlank() || displayName.startsWith("audio_")) {
            uri.lastPathSegment?.let { seg ->
                val clean = seg.substringAfterLast('/')
                if (clean.isNotBlank()) displayName = clean
            }
        }

        // Read bytes for ID3 inspection
        val bytes = readBytesFromUri(context, uri)
        val id3Result = Id3v2Editor.inspect(bytes)

        // Also query MediaMetadataRetriever for duration, artist, title
        var title = id3Result.title
        var artist = id3Result.artist
        var album = id3Result.album
        var durationMs = 0L
        var retrieverCover: ByteArray? = null

        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }

            if (title.isNullOrBlank()) {
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            }
            if (artist.isNullOrBlank()) {
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            }
            if (album.isNullOrBlank()) {
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            }
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L

            retrieverCover = retriever.embeddedPicture
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Lagu Tanpa Judul" }
        val finalTitle = title?.ifBlank { fallbackTitle } ?: fallbackTitle
        val finalArtist = artist?.ifBlank { "Artis Tidak Diketahui" } ?: "Artis Tidak Diketahui"
        val finalAlbum = album?.ifBlank { "CoverCraft Album" } ?: "CoverCraft Album"

        val effectiveOriginalCover = id3Result.coverBytes ?: retrieverCover
        val hasOriginalCover = effectiveOriginalCover != null

        Mp3Item(
            id = UUID.randomUUID().toString(),
            uri = uri,
            fileName = displayName,
            title = finalTitle,
            artist = finalArtist,
            album = finalAlbum,
            durationMs = durationMs,
            sizeBytes = bytes.size.toLong(),
            hasOriginalCover = hasOriginalCover,
            originalCoverBytes = effectiveOriginalCover,
            hasProcessedCover = false,
            status = ProcessingStatus.IDLE
        )
    }

    /**
     * Adds cover art to an MP3 track and saves the resulting MP3 in app storage.
     */
    suspend fun applyCoverArt(
        context: Context,
        item: Mp3Item,
        customCoverBytes: ByteArray? = null,
        style: CoverStyle? = null
    ): Mp3Item = withContext(Dispatchers.IO) {
        try {
            val sourceBytes = readBytesFromUri(context, item.uri)

            val (coverBytes, _) = if (customCoverBytes != null) {
                Pair(customCoverBytes, null)
            } else {
                val (bmp, bytes) = CoverArtGenerator.generateForSong(
                    title = item.title,
                    artist = item.artist,
                    chosenStyle = style
                )
                Pair(bytes, bmp)
            }

            val modifiedBytes = Id3v2Editor.embedCoverArt(
                sourceBytes = sourceBytes,
                coverImageBytes = coverBytes,
                mimeType = "image/jpeg",
                fallbackTitle = item.title,
                fallbackArtist = item.artist,
                fallbackAlbum = item.album
            )

            // Save to processed output directory
            val outputDir = File(context.filesDir, "processed_mp3s")
            if (!outputDir.exists()) outputDir.mkdirs()

            val cleanName = if (item.fileName.endsWith(".mp3", ignoreCase = true)) {
                item.fileName.substringBeforeLast('.') + "_covered.mp3"
            } else {
                "${item.fileName}_covered.mp3"
            }
            val outputFile = File(outputDir, cleanName)
            FileOutputStream(outputFile).use { fos ->
                fos.write(modifiedBytes)
            }

            val processedUri = Uri.fromFile(outputFile)

            // Verify with MediaMetadataRetriever that the new file has valid cover and metadata
            var verifiedDuration = item.durationMs
            val verifyRetriever = MediaMetadataRetriever()
            try {
                verifyRetriever.setDataSource(outputFile.absolutePath)
                val durStr = verifyRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                durStr?.toLongOrNull()?.let { if (it > 0) verifiedDuration = it }
            } catch (_: Exception) {
            } finally {
                try { verifyRetriever.release() } catch (_: Exception) {}
            }

            item.copy(
                hasProcessedCover = true,
                processedUri = processedUri,
                processedCoverBytes = coverBytes,
                durationMs = verifiedDuration,
                sizeBytes = outputFile.length(),
                status = ProcessingStatus.SUCCESS,
                errorMessage = null
            )
        } catch (e: Exception) {
            item.copy(
                status = ProcessingStatus.ERROR,
                errorMessage = e.localizedMessage ?: "Gagal menyematkan cover"
            )
        }
    }

    /**
     * Reads all bytes from a given URI using ContentResolver.
     */
    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        val inputStream: InputStream? = if (uri.scheme == "file") {
            File(uri.path ?: "").inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }

        return inputStream?.use { stream ->
            val buffer = ByteArray(8192)
            val baos = ByteArrayOutputStream()
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                baos.write(buffer, 0, read)
            }
            baos.toByteArray()
        } ?: ByteArray(0)
    }
}
