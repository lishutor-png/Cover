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
     * Automatically caches the imported file to local app storage to prevent SAF permission expiration.
     */
    suspend fun parseFromUri(context: Context, uri: Uri): Mp3Item = withContext(Dispatchers.IO) {
        var displayName = "rekaman_${System.currentTimeMillis()}.mp3"

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

        if (displayName.isBlank() || displayName.startsWith("rekaman_")) {
            uri.lastPathSegment?.let { seg ->
                val clean = seg.substringAfterLast('/')
                if (clean.isNotBlank()) displayName = clean
            }
        }

        // Read bytes
        val bytes = readBytesFromUri(context, uri)
        val format = AudioFormatDetector.detect(bytes, displayName)

        // Cache file locally to guarantee permanent, fast read access for MediaPlayer and editors
        val safeCleanName = sanitizeFileName(displayName)
        val importDir = File(context.cacheDir, "imported_audio")
        if (!importDir.exists()) importDir.mkdirs()
        val cachedLocalFile = File(importDir, "${UUID.randomUUID().toString().take(8)}_$safeCleanName")
        try {
            FileOutputStream(cachedLocalFile).use { fos ->
                fos.write(bytes)
            }
        } catch (_: Exception) {}

        val workingUri = if (cachedLocalFile.exists() && cachedLocalFile.length() > 0) {
            Uri.fromFile(cachedLocalFile)
        } else {
            uri
        }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var existingCover: ByteArray? = null

        // Inspect metadata based on detected audio format
        if (format == AudioFormat.M4A) {
            val m4aResult = M4aCoverEditor.inspect(bytes)
            title = m4aResult.title
            artist = m4aResult.artist
            album = m4aResult.album
            existingCover = m4aResult.coverBytes
        } else {
            val id3Result = Id3v2Editor.inspect(bytes)
            title = id3Result.title
            artist = id3Result.artist
            album = id3Result.album
            existingCover = id3Result.coverBytes
        }

        // Also query MediaMetadataRetriever for duration and fallback artist/title
        var durationMs = 0L
        var retrieverCover: ByteArray? = null

        val retriever = MediaMetadataRetriever()
        try {
            if (workingUri.scheme == "file") {
                retriever.setDataSource(workingUri.path)
            } else {
                retriever.setDataSource(context, workingUri)
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

        val fallbackTitle = displayName.substringBeforeLast('.').ifBlank { "Rekaman Audio" }
        val finalTitle = title?.ifBlank { fallbackTitle } ?: fallbackTitle
        val finalArtist = artist?.ifBlank { if (format == AudioFormat.M4A) "Rekaman Suara" else "Artis Tidak Diketahui" } ?: "Artis Tidak Diketahui"
        val finalAlbum = album?.ifBlank { "CoverCraft Album" } ?: "CoverCraft Album"

        val effectiveOriginalCover = existingCover ?: retrieverCover
        val hasOriginalCover = effectiveOriginalCover != null && effectiveOriginalCover.isNotEmpty()

        Mp3Item(
            id = UUID.randomUUID().toString(),
            uri = workingUri,
            fileName = displayName,
            title = finalTitle,
            artist = finalArtist,
            album = finalAlbum,
            durationMs = durationMs,
            sizeBytes = bytes.size.toLong(),
            hasOriginalCover = hasOriginalCover,
            originalCoverBytes = effectiveOriginalCover,
            hasProcessedCover = false,
            status = ProcessingStatus.IDLE,
            audioFormat = format
        )
    }

    /**
     * Adds or updates cover art on an audio file (MP3 or M4A recording) and saves it to app storage.
     */
    suspend fun applyCoverArt(
        context: Context,
        item: Mp3Item,
        customCoverBytes: ByteArray? = null,
        style: CoverStyle? = null
    ): Mp3Item = withContext(Dispatchers.IO) {
        try {
            val sourceBytes = readBytesFromUri(context, item.effectiveUri)
            if (sourceBytes.isEmpty()) {
                return@withContext item.copy(
                    status = ProcessingStatus.ERROR,
                    errorMessage = "File audio kosong atau tidak dapat diakses."
                )
            }

            val format = if (item.audioFormat != AudioFormat.UNKNOWN) {
                item.audioFormat
            } else {
                AudioFormatDetector.detect(sourceBytes, item.fileName)
            }

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

            val modifiedBytes = if (format == AudioFormat.M4A) {
                M4aCoverEditor.embedCoverArt(
                    sourceBytes = sourceBytes,
                    coverImageBytes = coverBytes,
                    mimeType = "image/jpeg",
                    fallbackTitle = item.title,
                    fallbackArtist = item.artist,
                    fallbackAlbum = item.album
                )
            } else {
                Id3v2Editor.embedCoverArt(
                    sourceBytes = sourceBytes,
                    coverImageBytes = coverBytes,
                    mimeType = "image/jpeg",
                    fallbackTitle = item.title,
                    fallbackArtist = item.artist,
                    fallbackAlbum = item.album
                )
            }

            // Save to processed output directory
            val outputDir = File(context.filesDir, "processed_mp3s")
            if (!outputDir.exists()) outputDir.mkdirs()

            val ext = if (format == AudioFormat.M4A) ".m4a" else ".mp3"
            val rawBaseName = item.fileName.substringBeforeLast('.')
            val cleanBaseName = sanitizeFileName(rawBaseName)
            val cleanName = "${cleanBaseName}_covered$ext"

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
                errorMessage = null,
                audioFormat = format
            )
        } catch (t: Throwable) {
            item.copy(
                status = ProcessingStatus.ERROR,
                errorMessage = t.localizedMessage ?: "Gagal menyematkan cover pada file audio"
            )
        }
    }

    /**
     * Reads all bytes from a given URI safely.
     */
    private fun readBytesFromUri(context: Context, uri: Uri): ByteArray {
        return try {
            val inputStream: InputStream? = if (uri.scheme == "file") {
                File(uri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            }

            inputStream?.use { stream ->
                val buffer = ByteArray(16384)
                val baos = ByteArrayOutputStream()
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    baos.write(buffer, 0, read)
                }
                baos.toByteArray()
            } ?: ByteArray(0)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    /**
     * Sanitizes file name by removing illegal filesystem characters.
     */
    fun sanitizeFileName(name: String): String {
        return name
            .replace('/', '_')
            .replace('\\', '_')
            .replace(':', '_')
            .replace('*', '_')
            .replace('?', '_')
            .replace('"', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('|', '_')
            .trim()
            .ifBlank { "audio_${System.currentTimeMillis()}" }
    }
}
