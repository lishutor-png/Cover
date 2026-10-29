package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProcessingStatus
import com.example.ui.FilterTab
import com.example.ui.Mp3ViewModel
import com.example.ui.components.AudioPlayerBar
import com.example.ui.components.CoverPreviewModal
import com.example.ui.components.Mp3CardItem
import com.example.ui.components.RecordAudioModal
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: Mp3ViewModel,
    modifier: Modifier = Modifier
) {
    val allTracks by viewModel.tracks.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val filterTab by viewModel.filterTab.collectAsState()
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchStatusText by viewModel.batchStatusText.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val selectedDetailItem by viewModel.selectedDetailItem.collectAsState()
    val selectedStyle by viewModel.selectedStyleForDetail.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var itemToDelete by remember { mutableStateOf<com.example.model.Mp3Item?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showRecordModal by remember { mutableStateOf(false) }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // SAF Document Picker: supports MP3, M4A, AAC, and all audio containers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricViolet,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CoverCraft MP3",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Auto Cover Injector & Player",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    if (allTracks.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("clear_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Bersihkan Semua Lagu",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.loadSampleTracks() },
                        modifier = Modifier.testTag("reload_samples_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Muat Ulang Contoh",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AudioPlayerBar(
                playbackState = playbackState,
                onPlayPauseClick = {
                    if (playbackState.isPlaying) viewModel.pausePlayback() else viewModel.resumePlayback()
                },
                onSeek = { viewModel.seekTo(it) },
                onStopClick = { viewModel.pausePlayback() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Stats Card
            item {
                val totalTracks = allTracks.size
                val needsCoverCount = allTracks.count { it.needsCover || it.status == ProcessingStatus.ERROR }
                val hasCoverCount = totalTracks - needsCoverCount

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hero_stats_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Status Koleksi Audio & Rekaman",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            StatItem(
                                label = "Total File",
                                value = "$totalTracks",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatItem(
                                label = "Perlu Cover",
                                value = "$needsCoverCount",
                                color = AmberWarning
                            )
                            StatItem(
                                label = "Sudah Ada",
                                value = "$hasCoverCount",
                                color = EmeraldSuccess
                            )
                        }
                    }
                }
            }

            // Primary Batch Action Hero
            item {
                val needsCoverCount = allTracks.count { it.needsCover || it.status == ProcessingStatus.ERROR }
                val totalTracks = allTracks.size
                val canProcess = !isBatchProcessing && totalTracks > 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .testTag("batch_action_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = ElectricViolet.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = ElectricViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Otomatisasi Cover Audio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Mendukung MP3 & M4A / Rekaman Suara Ponsel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Progress Indicator if batch is active
                        AnimatedVisibility(visible = isBatchProcessing) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { batchProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = ElectricCyan,
                                    trackColor = MaterialTheme.colorScheme.surface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = batchStatusText ?: "Memproses...",
                                    fontSize = 11.sp,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        // Big Action Button
                        Button(
                            onClick = {
                                if (needsCoverCount > 0) {
                                    viewModel.processAllMissingCovers(forceAll = false)
                                } else {
                                    viewModel.processAllMissingCovers(forceAll = true)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("process_all_missing_button"),
                            enabled = canProcess,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricViolet,
                                disabledContainerColor = ElectricViolet.copy(alpha = 0.3f)
                            )
                        ) {
                            if (isBatchProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sedang Memproses...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        totalTracks == 0 -> "Pilih atau Rekam Audio Terlebih Dahulu"
                                        needsCoverCount > 0 -> "⚡ Tambah Cover Otomatis ($needsCoverCount File)"
                                        else -> "⚡ Perbarui Semua Cover ($totalTracks File)"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Quick Actions: Pick Audio, Record Voice, and Sample
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("audio/*", "application/octet-stream", "video/mp4")) },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("pick_mp3_files_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Pilih Audio", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = { showRecordModal = true },
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("record_audio_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet.copy(alpha = 0.2f),
                            contentColor = ElectricViolet
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Rekam", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.loadSampleTracks() },
                        modifier = Modifier.testTag("add_sample_tracks_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = "Contoh")
                    }
                }
            }

            // Filter Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val total = allTracks.size
                    val needs = allTracks.count { it.needsCover || it.status == ProcessingStatus.ERROR }
                    val has = total - needs

                    FilterChip(
                        selected = filterTab == FilterTab.ALL,
                        onClick = { viewModel.setFilterTab(FilterTab.ALL) },
                        label = { Text("Semua ($total)") },
                        modifier = Modifier.testTag("filter_all"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = filterTab == FilterTab.NEEDS_COVER,
                        onClick = { viewModel.setFilterTab(FilterTab.NEEDS_COVER) },
                        label = { Text("Perlu Cover ($needs)") },
                        modifier = Modifier.testTag("filter_needs_cover"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberWarning,
                            selectedLabelColor = Color.Black
                        )
                    )
                    FilterChip(
                        selected = filterTab == FilterTab.HAS_COVER,
                        onClick = { viewModel.setFilterTab(FilterTab.HAS_COVER) },
                        label = { Text("Ber-Cover ($has)") },
                        modifier = Modifier.testTag("filter_has_cover"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldSuccess,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            // Empty State
            if (filteredTracks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("empty_state_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tidak ada file audio pada kategori ini",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pilih file rekaman/MP3 ponsel Anda atau gunakan tombol 'Rekam' untuk membuat rekaman suara baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // List of MP3 & M4A items
                items(
                    items = filteredTracks,
                    key = { it.id }
                ) { track ->
                    val isPlaying = playbackState.isPlaying && playbackState.currentTrack?.id == track.id
                    val isCurrentTrack = playbackState.currentTrack?.id == track.id

                    Mp3CardItem(
                        item = track,
                        isPlaying = isPlaying,
                        isCurrentTrack = isCurrentTrack,
                        onPlayClick = { viewModel.playTrack(track) },
                        onAddCoverClick = { viewModel.processSingleItem(track) },
                        onDetailClick = { viewModel.selectDetailItem(track) },
                        onDeleteClick = { itemToDelete = track }
                    )
                }
            }

            // Bottom Spacing for floating player
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Single Track Delete Confirmation Dialog
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Hapus File Audio?")
            },
            text = {
                Text(text = "File '${target.title}' (${target.audioFormat.displayName}) akan dihapus dari daftar dan penyimpanan aplikasi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTrack(target)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Clear All Tracks Confirmation Dialog
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(text = "Hapus Semua File?")
            },
            text = {
                Text(text = "Semua lagu dan rekaman (${allTracks.size} file) akan dihapus dari daftar dan penyimpanan aplikasi.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTracks()
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus Semua")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearAllDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Built-in Voice Recorder Modal
    if (showRecordModal) {
        RecordAudioModal(
            recorderState = recorderState,
            onStartRecording = { viewModel.startVoiceRecording() },
            onStopRecordingAndProcess = { autoAddCover ->
                viewModel.stopVoiceRecordingAndProcess(autoAddCover)
                showRecordModal = false
            },
            onCancel = {
                viewModel.cancelVoiceRecording()
                showRecordModal = false
            }
        )
    }

    // Cover Preview / Customization Modal
    selectedDetailItem?.let { item ->
        CoverPreviewModal(
            item = item,
            currentStyle = selectedStyle,
            onStyleChange = { viewModel.setDetailStyle(it) },
            onApplyCover = { track, customBytes, style ->
                viewModel.processSingleItem(track, customBytes, style)
            },
            onPlayAudio = { viewModel.playTrack(it) },
            onExportFile = { track, uri -> viewModel.exportToDestination(track, uri) },
            onDeleteFile = { track -> itemToDelete = track },
            onDismiss = { viewModel.selectDetailItem(null) }
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
