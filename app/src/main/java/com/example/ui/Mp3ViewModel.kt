package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.CoverStyle
import com.example.model.Mp3Item
import com.example.model.ProcessingStatus
import android.os.Build
import android.provider.Settings
import com.example.mp3.AudioRecorderEngine
import com.example.mp3.FloatingOverlayService
import com.example.mp3.Mp3Processor
import com.example.mp3.RecorderState
import com.example.mp3.SampleAudioGenerator
import com.example.player.AudioPlayerController
import com.example.player.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FilterTab {
    ALL,
    NEEDS_COVER,
    HAS_COVER
}

class Mp3ViewModel(application: Application) : AndroidViewModel(application) {

    private val playerController = AudioPlayerController(application, viewModelScope)
    val playbackState: StateFlow<PlaybackState> = playerController.playbackState

    val recorderState: StateFlow<RecorderState> = AudioRecorderEngine.recorderState

    private val _overlayPermissionRequired = MutableStateFlow(false)
    val overlayPermissionRequired: StateFlow<Boolean> = _overlayPermissionRequired.asStateFlow()

    private val _tracks = MutableStateFlow<List<Mp3Item>>(emptyList())
    val tracks: StateFlow<List<Mp3Item>> = _tracks.asStateFlow()

    private val _filterTab = MutableStateFlow(FilterTab.ALL)
    val filterTab: StateFlow<FilterTab> = _filterTab.asStateFlow()

    val filteredTracks: StateFlow<List<Mp3Item>> = combine(_tracks, _filterTab) { list, filter ->
        when (filter) {
            FilterTab.ALL -> list
            FilterTab.NEEDS_COVER -> list.filter { it.needsCover }
            FilterTab.HAS_COVER -> list.filter { !it.needsCover }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isBatchProcessing = MutableStateFlow(false)
    val isBatchProcessing: StateFlow<Boolean> = _isBatchProcessing.asStateFlow()

    private val _batchProgress = MutableStateFlow(0f)
    val batchProgress: StateFlow<Float> = _batchProgress.asStateFlow()

    private val _batchStatusText = MutableStateFlow<String?>(null)
    val batchStatusText: StateFlow<String?> = _batchStatusText.asStateFlow()

    private val _selectedDetailItem = MutableStateFlow<Mp3Item?>(null)
    val selectedDetailItem: StateFlow<Mp3Item?> = _selectedDetailItem.asStateFlow()

    private val _selectedStyleForDetail = MutableStateFlow(CoverStyle.NEON_SYNTHWAVE)
    val selectedStyleForDetail: StateFlow<CoverStyle> = _selectedStyleForDetail.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        AudioRecorderEngine.init(application)

        // Automatically listen for tracks recorded from system floating overlay over other apps
        viewModelScope.launch {
            AudioRecorderEngine.newlyRecordedTracks.collect { item ->
                _tracks.update { current ->
                    if (current.none { it.id == item.id }) {
                        listOf(item) + current
                    } else {
                        current
                    }
                }
            }
        }

        // Automatically preload demo sample tracks on first launch so user can test immediately!
        loadSampleTracks()
    }

    fun setFilterTab(tab: FilterTab) {
        _filterTab.value = tab
    }

    fun selectDetailItem(item: Mp3Item?) {
        _selectedDetailItem.value = item
    }

    fun setDetailStyle(style: CoverStyle) {
        _selectedStyleForDetail.value = style
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun dismissOverlayPermissionDialog() {
        _overlayPermissionRequired.value = false
    }

    fun toggleSystemOverlay(context: android.content.Context) {
        if (recorderState.value.isSystemOverlayActive) {
            FloatingOverlayService.stop(context)
            _userMessage.value = "Bubble di atas aplikasi lain dinonaktifkan."
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                _overlayPermissionRequired.value = true
            } else {
                FloatingOverlayService.start(context)
                _userMessage.value = "Bubble melayang aktif! Anda dapat membuka aplikasi lain dan merekam suaranya."
            }
        }
    }

    fun loadSampleTracks() {
        viewModelScope.launch {
            val samples = SampleAudioGenerator.createSampleTracks(getApplication())
            _tracks.update { current ->
                val existingIds = current.map { it.title }.toSet()
                val newOnes = samples.filter { it.title !in existingIds }
                newOnes + current
            }
            _userMessage.value = "3 Contoh MP3 Uji Coba berhasil disiapkan!"
        }
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _batchStatusText.value = "Membaca & memproses ${uris.size} file audio..."
            val parsedList = mutableListOf<Mp3Item>()
            for (uri in uris) {
                try {
                    val item = Mp3Processor.parseFromUri(getApplication(), uri)
                    parsedList.add(item)
                } catch (e: Exception) {
                    _userMessage.value = "Gagal membaca file: ${e.localizedMessage}"
                }
            }

            _tracks.update { current ->
                parsedList + current.filter { c -> parsedList.none { p -> p.fileName == c.fileName } }
            }
            _batchStatusText.value = null
            _userMessage.value = "Berhasil menambahkan ${parsedList.size} file audio ke daftar."
        }
    }

    fun showRecorderBubble() {
        AudioRecorderEngine.showBubble()
    }

    fun hideRecorderBubble() {
        AudioRecorderEngine.hideBubble()
    }

    fun toggleMinimizeRecorderBubble() {
        AudioRecorderEngine.toggleMinimizeBubble()
    }

    fun setRecorderSource(source: com.example.mp3.AudioRecordSource) {
        AudioRecorderEngine.setSource(source)
    }

    fun startInternalAudioRecording(resultCode: Int, data: android.content.Intent): Boolean {
        return AudioRecorderEngine.startInternalAudioRecording(resultCode, data)
    }

    fun startMicRecording(): Boolean {
        if (playbackState.value.isPlaying) {
            pausePlayback()
        }
        return AudioRecorderEngine.startMicRecording()
    }

    fun pauseRecording() {
        AudioRecorderEngine.pauseRecording()
    }

    fun resumeRecording() {
        AudioRecorderEngine.resumeRecording()
    }

    fun stopVoiceRecordingAndProcess(autoAddCover: Boolean = true, autoPlayAfterSave: Boolean = false) {
        AudioRecorderEngine.stopAndSaveRecording(autoAddCover) { item ->
            if (autoPlayAfterSave && item != null) {
                playTrack(item)
            }
        }
    }

    fun cancelVoiceRecording() {
        AudioRecorderEngine.cancelRecording()
        _userMessage.value = "Sesi rekaman telah dibatalkan."
    }

    fun clearLastRecordedItem() {
        AudioRecorderEngine.clearLastRecordedItem()
    }

    /**
     * Otomatis tambahkan cover pada file MP3/M4A yang belum punya cover (atau semua jika forceAll = true).
     */
    fun processAllMissingCovers(forceAll: Boolean = false) {
        val targets = if (forceAll) {
            _tracks.value
        } else {
            _tracks.value.filter { it.needsCover || it.status == ProcessingStatus.ERROR }
        }

        if (targets.isEmpty()) {
            _userMessage.value = "Semua file audio sudah memiliki sampul cover!"
            return
        }

        viewModelScope.launch {
            _isBatchProcessing.value = true
            _batchProgress.value = 0f

            var completed = 0
            val total = targets.size

            for (item in targets) {
                _batchStatusText.value = "Membuat & menyematkan cover untuk (${completed + 1}/$total): ${item.title}"

                _tracks.update { list ->
                    list.map { if (it.id == item.id) it.copy(status = ProcessingStatus.PROCESSING) else it }
                }

                val processed = Mp3Processor.applyCoverArt(
                    context = getApplication(),
                    item = item
                )

                _tracks.update { list ->
                    list.map { if (it.id == item.id) processed else it }
                }

                completed++
                _batchProgress.value = completed.toFloat() / total
            }

            _isBatchProcessing.value = false
            _batchStatusText.value = null
            _userMessage.value = "Sukses! $completed file berhasil dipasangi cover art."
        }
    }

    /**
     * Tambahkan atau ganti cover untuk satu file tertentu dengan gaya pilihan atau gambar kustom.
     */
    fun processSingleItem(item: Mp3Item, customImageBytes: ByteArray? = null, style: CoverStyle? = null) {
        viewModelScope.launch {
            _tracks.update { list ->
                list.map { if (it.id == item.id) it.copy(status = ProcessingStatus.PROCESSING) else it }
            }

            val processed = Mp3Processor.applyCoverArt(
                context = getApplication(),
                item = item,
                customCoverBytes = customImageBytes,
                style = style ?: _selectedStyleForDetail.value
            )

            _tracks.update { list ->
                list.map { if (it.id == item.id) processed else it }
            }

            if (_selectedDetailItem.value?.id == item.id) {
                _selectedDetailItem.value = processed
            }

            if (processed.status == ProcessingStatus.SUCCESS) {
                _userMessage.value = "Cover untuk '${item.title}' berhasil disematkan!"
            } else {
                _userMessage.value = "Gagal: ${processed.errorMessage ?: "Terjadi kesalahan"}"
            }
        }
    }

    fun playTrack(item: Mp3Item) {
        playerController.playOrToggle(item)
    }

    fun pausePlayback() {
        playerController.pause()
    }

    fun resumePlayback() {
        playerController.resume()
    }

    fun seekTo(positionMs: Int) {
        playerController.seekTo(positionMs)
    }

    fun exportToDestination(item: Mp3Item, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sourceUri = item.effectiveUri
                val context = getApplication<Application>()
                val inStream = if (sourceUri.scheme == "file") {
                    File(sourceUri.path ?: "").inputStream()
                } else {
                    context.contentResolver.openInputStream(sourceUri)
                }

                val outStream = context.contentResolver.openOutputStream(destUri)

                if (inStream != null && outStream != null) {
                    inStream.use { input ->
                        outStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        _userMessage.value = "File berhasil diekspor ke penyimpanan perangkat!"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _userMessage.value = "Gagal membuka stream untuk ekspor."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _userMessage.value = "Gagal ekspor: ${e.localizedMessage}"
                }
            }
        }
    }

    fun deleteTrack(item: Mp3Item) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playbackState.value.currentTrack?.id == item.id) {
                withContext(Dispatchers.Main) {
                    playerController.stop()
                }
            }

            val appPkg = getApplication<Application>().packageName

            item.processedUri?.path?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            if (item.uri.scheme == "file") {
                item.uri.path?.let { path ->
                    val f = File(path)
                    if (f.exists() && path.contains(appPkg)) {
                        f.delete()
                    }
                }
            }

            _tracks.update { list -> list.filter { it.id != item.id } }

            if (_selectedDetailItem.value?.id == item.id) {
                _selectedDetailItem.value = null
            }

            withContext(Dispatchers.Main) {
                _userMessage.value = "File '${item.title}' telah dihapus."
            }
        }
    }

    fun clearAllTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                playerController.stop()
            }
            val currentList = _tracks.value
            val appPkg = getApplication<Application>().packageName
            for (item in currentList) {
                item.processedUri?.path?.let { path ->
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
                if (item.uri.scheme == "file") {
                    item.uri.path?.let { path ->
                        val f = File(path)
                        if (f.exists() && path.contains(appPkg)) {
                            f.delete()
                        }
                    }
                }
            }
            _tracks.value = emptyList()
            _selectedDetailItem.value = null
            withContext(Dispatchers.Main) {
                _userMessage.value = "Semua file berhasil dihapus dari daftar."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.release()
        AudioRecorderEngine.cancelRecording()
    }
}
