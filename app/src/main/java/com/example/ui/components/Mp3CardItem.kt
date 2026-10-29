package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Mp3Item
import com.example.model.ProcessingStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RubyError

@Composable
fun Mp3CardItem(
    item: Mp3Item,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onPlayClick: () -> Unit,
    onAddCoverClick: () -> Unit,
    onDetailClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrentTrack -> ElectricCyan
            item.hasProcessedCover -> EmeraldSuccess.copy(alpha = 0.5f)
            item.needsCover -> AmberWarning.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        },
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mp3_card_${item.id}")
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onDetailClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Art Thumbnail (72x72)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val coverBytes = item.effectiveCoverBytes
                    if (coverBytes != null && coverBytes.isNotEmpty()) {
                        AsyncImage(
                            model = coverBytes,
                            contentDescription = "Cover Art untuk ${item.title}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Styled Placeholder for tracks without cover
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "NO COVER",
                                color = AmberWarning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Playing indicator overlay badge
                    if (isCurrentTrack && isPlaying) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            shape = CircleShape,
                            color = ElectricCyan
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Sedang diputar",
                                tint = Color.Black,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Track Title and Artist details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Status Chips & Meta
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            item.status == ProcessingStatus.PROCESSING -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricViolet.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 2.dp,
                                            color = ElectricViolet
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Menyematkan...",
                                            fontSize = 11.sp,
                                            color = ElectricViolet,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            item.hasProcessedCover -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldSuccess.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldSuccess,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Cover Terpasang",
                                            fontSize = 11.sp,
                                            color = EmeraldSuccess,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            item.hasOriginalCover -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "Ada Cover Bawaan",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AmberWarning.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Warning,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Perlu Cover",
                                            fontSize = 11.sp,
                                            color = AmberWarning,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "•  ${item.formattedDuration}  •  ${item.formattedSize}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Play / Pause Circle Button
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .testTag("play_button_${item.id}")
                            .size(42.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isCurrentTrack && isPlaying) ElectricCyan else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isCurrentTrack && isPlaying) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isCurrentTrack && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentTrack && isPlaying) "Jeda audio" else "Putar audio untuk verifikasi"
                        )
                    }

                    // Delete Button
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .testTag("delete_button_${item.id}")
                            .size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Hapus file ini",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Quick Actions Footer if needs cover or error
            if (item.needsCover || item.status == ProcessingStatus.ERROR) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAddCoverClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_cover_btn_${item.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Beri Cover Otomatis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onDetailClick,
                        modifier = Modifier.testTag("detail_btn_${item.id}"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Opsi", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
