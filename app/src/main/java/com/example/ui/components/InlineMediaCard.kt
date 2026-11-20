package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.files.MediaSaveHelper
import com.example.model.GeneratedMediaType
import com.example.model.OpenClawHost

@Composable
fun InlineMediaCard(
  mediaUrl: String,
  mediaType: GeneratedMediaType,
  prompt: String,
  modelUsed: String,
  durationSec: Int?,
  activeHost: OpenClawHost?,
  onAnimateThis: ((String) -> Unit)?,
  onSaveToPhone: () -> Unit,
  onCopyPrompt: () -> Unit,
  onOpenLightbox: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val resolvedUrl = remember(mediaUrl, activeHost?.baseUrl) {
    MediaSaveHelper.resolveDownloadUrl(mediaUrl, activeHost) ?: mediaUrl
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = modifier
      .fillMaxWidth()
      .testTag("inline_media_card"),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(210.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF140724))
          .clickable(onClick = onOpenLightbox),
      ) {
        if (mediaType == GeneratedMediaType.VIDEO) {
          var muted by remember { mutableStateOf(true) }
          var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

          AndroidView(
            factory = { ctx ->
              VideoView(ctx).apply {
                setVideoURI(Uri.parse(resolvedUrl))
                setOnPreparedListener { mp ->
                  playerRef = mp
                  mp.isLooping = true
                  val vol = if (muted) 0f else 1f
                  mp.setVolume(vol, vol)
                  start()
                }
              }
            },
            update = { vv ->
              val vol = if (muted) 0f else 1f
              runCatching { playerRef?.setVolume(vol, vol) }
            },
            modifier = Modifier.fillMaxSize(),
          )

          DisposableEffect(resolvedUrl) {
            onDispose {
              playerRef = null
            }
          }

          Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xBB19062C),
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(8.dp),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFFFF8CE0), modifier = Modifier.size(14.dp))
              Text(
                text = if ((durationSec ?: 0) > 0) "Video • ${durationSec}s" else "Looping Video",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          IconButton(
            onClick = { muted = !muted },
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = Color(0xBB19062C),
              contentColor = Color.White,
            ),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .size(40.dp)
              .testTag("inline_video_audio_toggle"),
          ) {
            Icon(
              imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
              contentDescription = if (muted) "Unmute video audio" else "Mute video audio",
              modifier = Modifier.size(18.dp),
            )
          }
        } else {
          AsyncImage(
            model = ImageRequest.Builder(context)
              .data(resolvedUrl)
              .crossfade(true)
              .build(),
            contentDescription = prompt.ifBlank { "Generated image preview" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )

          Surface(
            shape = CircleShape,
            color = Color(0xAA19062C),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(8.dp)
              .size(32.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Rounded.ZoomIn,
                contentDescription = "Open fullscreen preview",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        if (modelUsed.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xBB19062C),
            modifier = Modifier
              .align(Alignment.BottomStart)
              .padding(8.dp),
          ) {
            Text(
              text = modelUsed,
              style = MaterialTheme.typography.labelSmall,
              color = Color(0xFFFFE57F),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (mediaType == GeneratedMediaType.IMAGE && onAnimateThis != null) {
          OutlinedButton(
            onClick = { onAnimateThis(mediaUrl) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 42.dp)
              .testTag("media_animate_this_button"),
          ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Animate This", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }

        OutlinedButton(
          onClick = onSaveToPhone,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 42.dp)
            .testTag("media_save_phone_button"),
        ) {
          Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Save to Phone", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        OutlinedButton(
          onClick = onCopyPrompt,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 42.dp)
            .testTag("media_copy_prompt_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(4.dp))
          Text("Copy Prompt", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
