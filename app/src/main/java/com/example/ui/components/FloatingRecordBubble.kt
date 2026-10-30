package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.Mp3Item
import com.example.mp3.AudioRecordSource
import com.example.mp3.RecorderState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError
import kotlin.math.roundToInt

@Composable
fun FloatingRecordBubble(
    recorderState: RecorderState,
    onStartInternalAudio: (resultCode: Int, data: android.content.Intent) -> Boolean,
    onStartMic: () -> Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSaveAndProcess: (autoAddCover: Boolean, autoPlay: Boolean) -> Unit,
    onCancel: () -> Unit,
    onToggleMinimize: () -> Unit,
    onCloseBubble: () -> Unit,
    onSetSource: (AudioRecordSource) -> Unit,
    onPlayItem: (Mp3Item) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!recorderState.isBubbleVisible) return

    val context = LocalContext.current

    // Drag offset for floating bubble
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var showCancelConfirmDialog by remember { mutableStateOf(false) }

    // MediaProjection permission launcher for Internal Audio Capture
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            onStartInternalAudio(result.resultCode, result.data!!)
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartMic()
        }
    }

    fun requestStartRecording() {
        if (recorderState.source == AudioRecordSource.INTERNAL_APP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                if (projectionManager != null) {
                    mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                } else {
                    onStartMic()
                }
            } else {
                onStartMic()
            }
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMic) {
                onStartMic()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Pulsing animation for active recording
    val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .padding(12.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        if (recorderState.isBubbleMinimized) {
            // ==========================================
            // MINIMIZED FLOATING BUBBLE PILL WIDGET
            // ==========================================
            Surface(
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        width = 1.5.dp,
                        color = if (recorderState.isRecording) RubyError else ElectricViolet,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .clickable { onToggleMinimize() }
                    .testTag("floating_bubble_pill"),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (recorderState.isRecording) {
                        // Pulsing recording dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .scale(pulseScale)
                                .background(RubyError, CircleShape)
                        )

                        val minutes = recorderState.elapsedSeconds / 60
                        val seconds = recorderState.elapsedSeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = RubyError,
                            fontFamily = FontFamily.Monospace
                        )

                        // Mini visualizer bars (4 bars)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            recorderState.waveformHistory.takeLast(4).forEach { amp ->
                                val barHeight = (16.dp * amp).coerceIn(4.dp, 16.dp)
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(ElectricCyan)
                                )
                            }
                        }

                        // Quick stop/save icon
                        IconButton(
                            onClick = { onSaveAndProcess(true, false) },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("bubble_mini_save_btn"),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = EmeraldSuccess,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Simpan",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // Idle pill
                        Icon(
                            imageVector = if (recorderState.source == AudioRecordSource.INTERNAL_APP) Icons.Default.Headphones else Icons.Default.Mic,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (recorderState.source == AudioRecordSource.INTERNAL_APP) "Audio Internal" else "Mikrofon HD",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Buka Panel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        } else {
            // ==========================================
            // EXPANDED RECORDING CONSOLE BUBBLE
            // ==========================================
            Card(
                modifier = Modifier
                    .width(360.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .border(
                        width = 1.5.dp,
                        color = if (recorderState.isRecording) RubyError.copy(alpha = 0.8f) else ElectricViolet.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .testTag("floating_bubble_expanded_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (recorderState.isRecording) RubyError.copy(alpha = 0.2f) else ElectricViolet.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (recorderState.isRecording) Icons.Default.FiberManualRecord else Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = if (recorderState.isRecording) RubyError else ElectricViolet,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Bubble Perekam Cerdas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (recorderState.isRecording) "🔴 Sedang Merekam..." else "Kualitas Jernih 48kHz HD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (recorderState.isRecording) RubyError else ElectricCyan,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleMinimize,
                                modifier = Modifier.size(32.dp).testTag("bubble_minimize_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloseFullscreen,
                                    contentDescription = "Kecilkan ke Bubble",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (recorderState.isRecording) {
                                        showCancelConfirmDialog = true
                                    } else {
                                        onCloseBubble()
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("bubble_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Source Switcher Pills (Only enabled when not actively recording)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = recorderState.source == AudioRecordSource.INTERNAL_APP,
                            onClick = { onSetSource(AudioRecordSource.INTERNAL_APP) },
                            enabled = !recorderState.isRecording,
                            label = { Text("Output Aplikasi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("source_internal_chip"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.25f),
                                selectedLabelColor = ElectricCyan,
                                selectedLeadingIconColor = ElectricCyan
                            )
                        )

                        FilterChip(
                            selected = recorderState.source == AudioRecordSource.MICROPHONE,
                            onClick = { onSetSource(AudioRecordSource.MICROPHONE) },
                            enabled = !recorderState.isRecording,
                            label = { Text("Mikrofon HD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("source_mic_chip"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricViolet.copy(alpha = 0.25f),
                                selectedLabelColor = ElectricViolet,
                                selectedLeadingIconColor = ElectricViolet
                            )
                        )
                    }

                    // Description of active source
                    Text(
                        text = recorderState.source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )

                    // Waveform Equalizer Display
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Animated Dynamic Waveform Bars (16 Bars)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                recorderState.waveformHistory.forEachIndexed { index, amp ->
                                    val barHeight = if (recorderState.isRecording && !recorderState.isPaused) {
                                        (54.dp * amp).coerceIn(6.dp, 56.dp)
                                    } else {
                                        6.dp
                                    }

                                    val barColor by animateColorAsState(
                                        targetValue = when {
                                            !recorderState.isRecording -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            recorderState.isPaused -> AmberWarning
                                            index % 2 == 0 -> ElectricCyan
                                            else -> ElectricViolet
                                        },
                                        label = "barColor"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Timer & Audio Format Specs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val minutes = recorderState.elapsedSeconds / 60
                        val seconds = recorderState.elapsedSeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (recorderState.isRecording) RubyError else MaterialTheme.colorScheme.onSurface
                        )

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmeraldSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "48 kHz • 192 kbps HD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Format MPEG-4 AAC (.m4a)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Error Message (if any)
                    recorderState.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RubyError.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = RubyError,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Success Banner with Instant Playback option (if item just recorded)
                    recorderState.lastRecordedItem?.let { item ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldSuccess.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "✅ Rekaman Berhasil!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess
                                    )
                                    Text(
                                        text = "${item.title} (Cover Terpasang)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { onPlayItem(item) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                    modifier = Modifier.testTag("play_recorded_now_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Putar", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ==========================================
                    // BUTTONS IN BUBBLE (Rekam, Batal, Simpan)
                    // ==========================================
                    if (!recorderState.isRecording) {
                        // Start Record Button
                        Button(
                            onClick = { requestStartRecording() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("bubble_start_recording_btn"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricViolet
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = RubyError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (recorderState.source == AudioRecordSource.INTERNAL_APP) "Mulai Rekam Audio Aplikasi" else "Mulai Rekam Mikrofon",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Action row when active recording
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Batal Button
                            OutlinedButton(
                                onClick = { showCancelConfirmDialog = true },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp)
                                    .testTag("bubble_cancel_record_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = RubyError
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Batal", fontSize = 12.sp)
                            }

                            // Jeda / Lanjut Button
                            FilledTonalButton(
                                onClick = {
                                    if (recorderState.isPaused) onResume() else onPause()
                                },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(46.dp)
                                    .testTag("bubble_pause_resume_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (recorderState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (recorderState.isPaused) "Lanjut" else "Jeda", fontSize = 12.sp)
                            }

                            // Simpan Rekaman Button (Prominent)
                            Button(
                                onClick = { onSaveAndProcess(true, false) },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(46.dp)
                                    .testTag("bubble_save_record_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricViolet
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick minimize hint
                        Text(
                            text = "💡 Tips: Anda dapat mengecilkan panel ini ke bubble melayang untuk memutar lagu atau membuka aplikasi lain sambil merekam.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Cancel
    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Batalkan Rekaman?") },
            text = { Text("Rekaman yang sedang berlangsung akan dibuang dan tidak disimpan.") },
            confirmButton = {
                Button(
                    onClick = {
                        onCancel()
                        showCancelConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Buang")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Lanjutkan Rekam")
                }
            }
        )
    }
}
