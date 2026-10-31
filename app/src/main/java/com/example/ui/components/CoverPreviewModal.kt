package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.CoverStyle
import com.example.model.Mp3Item
import com.example.mp3.CoverArtGenerator
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverPreviewModal(
    item: Mp3Item?,
    currentStyle: CoverStyle,
    onStyleChange: (CoverStyle) -> Unit,
    onApplyCover: (Mp3Item, ByteArray?, CoverStyle?) -> Unit,
    onPlayAudio: (Mp3Item) -> Unit,
    onExportFile: (Mp3Item, Uri) -> Unit,
    onDeleteFile: (Mp3Item) -> Unit,
    onDismiss: () -> Unit
) {
    if (item == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local custom image state if user picks one from gallery
    var customImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var customImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Live preview bitmap for generated styles
    val stylePreviewBitmap = remember(item.title, item.artist, currentStyle) {
        CoverArtGenerator.generateCover(item.title, item.artist, currentStyle, 500)
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        customImageBitmap = bmp
                        customImageBytes = CoverArtGenerator.bitmapToJpegBytes(bmp)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Export file launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mpeg")
    ) { destUri: Uri? ->
        if (destUri != null) {
            onExportFile(item, destUri)
        }
    }

    fun saveDirectlyToDeviceMusicFolder() {
        try {
            val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "recLish")
            if (!musicDir.exists()) musicDir.mkdirs()
            val targetFile = File(musicDir, item.fileName)
            val sourceUri = item.effectiveUri
            val inStream = if (sourceUri.scheme == "file") {
                File(sourceUri.path ?: "").inputStream()
            } else {
                context.contentResolver.openInputStream(sourceUri)
            }
            if (inStream != null) {
                inStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(if (item.audioFormat == com.example.mp3.AudioFormat.M4A) "audio/mp4" else "audio/mpeg"),
                    null
                )
                Toast.makeText(
                    context,
                    "✅ Tersimpan di Musik: ${targetFile.name} (Cover Terpasang)!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "Gagal membaca audio sumber", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal simpan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("cover_preview_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pengaturan Cover & Info • recLish",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Large 220x220 Cover Artwork Display
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, ElectricViolet, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                val currentCover = item.effectiveCoverBytes

                when {
                    customImageBitmap != null -> {
                        AsyncImage(
                            model = customImageBitmap,
                            contentDescription = "Pratinjau Gambar Kustom",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    currentCover != null && !item.needsCover -> {
                        AsyncImage(
                            model = currentCover,
                            contentDescription = "Cover Tersimpan",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        AsyncImage(
                            model = stylePreviewBitmap,
                            contentDescription = "Pratinjau Cover Otomatis",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track details
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.artist}  •  ${item.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // INFORMASI REKAMAN & AUDIO (User Request 4)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📋 Spesifikasi & Info Rekaman",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )

                    InfoRow(label = "Format & Saluran", value = "${item.audioFormat.displayName} • ${item.channelInfo}")
                    InfoRow(label = "Penguat Volume", value = "+${item.volumeBoostPercent}% Otomatis (Jernih)")
                    InfoRow(label = "Sumber Audio", value = item.recordSource ?: if (item.isRecordedTrack) "Audio Aplikasi" else "File Audio")
                    item.formattedRecordedDate?.let { dateStr ->
                        InfoRow(label = "Waktu Rekaman", value = dateStr)
                    }
                    InfoRow(label = "Durasi & Ukuran", value = "${item.formattedDuration} • ${item.formattedSize}")
                    InfoRow(
                        label = "Status Sampul",
                        value = if (item.hasProcessedCover) "✅ Cover Tersemat Permanen" else if (item.hasOriginalCover) "Cover Bawaan" else "⚡ Belum Ada Cover"
                    )
                    item.savedPathInfo?.let { path ->
                        InfoRow(label = "Lokasi File", value = path)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Style Selector Title
            Text(
                text = "Pilih Gaya Sampul Otomatis:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Horizontal Carousel of Cover Styles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverStyle.entries.forEach { style ->
                    val isSelected = currentStyle == style && customImageBitmap == null
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            customImageBitmap = null
                            customImageBytes = null
                            onStyleChange(style)
                        },
                        label = { Text(style.title, fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Image From Gallery Button
            OutlinedButton(
                onClick = { photoPickerLauncher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_custom_cover_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (customImageBitmap != null) "Ganti Gambar Galeri" else "Pilih Gambar Kustom Dari Galeri"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Audio Playback Verification Test Button
            OutlinedButton(
                onClick = { onPlayAudio(item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_playback_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Uji Putar Suara Audio", color = ElectricCyan)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons (Apply & Export / Save Direct)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val bytes = customImageBytes ?: CoverArtGenerator.bitmapToJpegBytes(stylePreviewBitmap)
                        onApplyCover(item, bytes, currentStyle)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_cover_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Terapkan Cover", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { saveDirectlyToDeviceMusicFolder() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_direct_music_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Simpan ke Musik", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (item.hasProcessedCover) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val exportName = item.fileName.substringBeforeLast('.') + "_covered.mp3"
                        exportLauncher.launch(exportName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_mp3_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Ekspor ke Lokasi Lain...")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Delete File Button
            OutlinedButton(
                onClick = {
                    onDeleteFile(item)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("modal_delete_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RubyError
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, RubyError.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = RubyError,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Hapus File Dari Daftar", color = RubyError, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
