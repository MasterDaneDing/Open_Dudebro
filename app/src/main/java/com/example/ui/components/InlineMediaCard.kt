package com.example.ui.components

import android.media.AudioAttributes
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
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
  val resolvedUrl = remember(mediaUrl, activeHost?.effectiveBaseUrl) {
    MediaSaveHelper.resolveDownloadUrl(mediaUrl, activeHost) ?: mediaUrl
  }
  val authHeaders = remember(resolvedUrl, activeHost?.effectiveBaseUrl, activeHost?.gatewayToken) {
    MediaSaveHelper.authHeadersForUrl(resolvedUrl, activeHost)
  }
  val fileName = remember(resolvedUrl) {
    resolvedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "attachment" }
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
      when (mediaType) {
        GeneratedMediaType.VIDEO -> {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF140724))
              .clickable(onClick = onOpenLightbox),
          ) {
            var muted by remember { mutableStateOf(true) }
            var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  if (authHeaders.isNotEmpty()) {
                    setVideoURI(Uri.parse(resolvedUrl), authHeaders)
                  } else {
                    setVideoURI(Uri.parse(resolvedUrl))
                  }
                  setOnPreparedListener { mp ->
                    playerRef = mp
                    mp.isLooping = true
                    val vol = if (muted) 0f else 1f
                    mp.setVolume(vol, vol)
                    start()
                  }
                }
              },
              update = { _ ->
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

            if (modelUsed.isNotBlank()) {
              MediaModelBadge(
                modelUsed = modelUsed,
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(8.dp),
              )
            }
          }
        }

        GeneratedMediaType.AUDIO -> {
          var isPlaying by remember(resolvedUrl) { mutableStateOf(false) }
          var isPrepared by remember(resolvedUrl) { mutableStateOf(false) }
          val audioPlayer = remember(resolvedUrl) {
            MediaPlayer().apply {
              setAudioAttributes(
                AudioAttributes.Builder()
                  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .build()
              )
              setOnPreparedListener {
                isPrepared = true
                start()
                isPlaying = true
              }
              setOnCompletionListener {
                isPlaying = false
              }
              setOnErrorListener { _, _, _ ->
                isPlaying = false
                isPrepared = false
                true
              }
            }
          }

          DisposableEffect(resolvedUrl) {
            onDispose {
              runCatching {
                if (audioPlayer.isPlaying) audioPlayer.stop()
                audioPlayer.release()
              }
            }
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF1F0838))
              .clickable(onClick = onOpenLightbox)
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            IconButton(
              onClick = {
                if (isPlaying) {
                  runCatching { audioPlayer.pause() }
                  isPlaying = false
                } else if (isPrepared) {
                  runCatching { audioPlayer.start() }
                  isPlaying = true
                } else {
                  runCatching {
                    audioPlayer.reset()
                    if (authHeaders.isNotEmpty()) {
                      audioPlayer.setDataSource(context, Uri.parse(resolvedUrl), authHeaders)
                    } else {
                      audioPlayer.setDataSource(context, Uri.parse(resolvedUrl))
                    }
                    audioPlayer.prepareAsync()
                  }
                }
              },
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xFFFF2E93),
                contentColor = Color.White,
              ),
              modifier = Modifier
                .size(48.dp)
                .testTag("inline_audio_play_toggle"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause audio" else "Play audio",
              )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color(0xFFFF8CE0), modifier = Modifier.size(16.dp))
                Text(
                  text = fileName,
                  style = MaterialTheme.typography.labelLarge,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = if (isPlaying) "Playing audio clip..." else "Tap Play or Save to Phone",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD1F3),
              )
            }
          }
        }

        GeneratedMediaType.DOCUMENT -> {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF1F0838))
              .clickable(onClick = onSaveToPhone)
              .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFFF6D00),
              modifier = Modifier.size(44.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
              }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = fileName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${fileName.substringAfterLast('.', "DOC").uppercase()} • Tap to Save & Open",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD180),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        GeneratedMediaType.IMAGE -> {
          var retryNonce by remember(resolvedUrl) { mutableIntStateOf(0) }
          var isImageLoading by remember(resolvedUrl, retryNonce) { mutableStateOf(true) }
          var imageErrorMessage by remember(resolvedUrl, retryNonce) { mutableStateOf<String?>(null) }
          val imageRequest = remember(resolvedUrl, activeHost?.gatewayToken, retryNonce) {
            MediaSaveHelper.buildAuthorizedImageRequest(context, mediaUrl, activeHost)
          }
          val imageLoader = remember(context) { MediaSaveHelper.getImageLoader(context) }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFF140724))
              .clickable(onClick = onOpenLightbox),
          ) {
            AsyncImage(
              model = imageRequest,
              imageLoader = imageLoader,
              contentDescription = prompt.ifBlank { "Generated image preview" },
              contentScale = ContentScale.Crop,
              onLoading = {
                isImageLoading = true
                imageErrorMessage = null
              },
              onSuccess = {
                isImageLoading = false
                imageErrorMessage = null
              },
              onError = { err ->
                isImageLoading = false
                val detail = err.result.throwable.localizedMessage?.take(80).orEmpty()
                imageErrorMessage = if (detail.contains("401")) {
                  "Image auth failed (401) — check Gateway Token in Host Settings"
                } else if (detail.isNotBlank()) {
                  "Could not load image ($detail)"
                } else {
                  "Could not load image from host"
                }
              },
              modifier = Modifier.fillMaxSize(),
            )

            if (isImageLoading) {
              Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator(
                  color = Color(0xFFFF8CE0),
                  strokeWidth = 2.5.dp,
                  modifier = Modifier.size(28.dp),
                )
              }
            } else if (imageErrorMessage != null) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .background(Color(0xCC140724))
                  .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Icon(
                  Icons.Rounded.BrokenImage,
                  contentDescription = null,
                  tint = Color(0xFFFF8CE0),
                  modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                  text = imageErrorMessage.orEmpty(),
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                  onClick = { retryNonce++ },
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                  Spacer(Modifier.width(4.dp))
                  Text("Retry Image", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
              }
            }

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

            if (modelUsed.isNotBlank()) {
              MediaModelBadge(
                modelUsed = modelUsed,
                modifier = Modifier
                  .align(Alignment.BottomStart)
                  .padding(8.dp),
              )
            }
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

        val saveLabel = when (mediaType) {
          GeneratedMediaType.IMAGE -> "Save to Gallery"
          GeneratedMediaType.DOCUMENT -> "Save / Open ${fileName.substringAfterLast('.', "DOC").uppercase()}"
          else -> "Save to Phone"
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
          Text(saveLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun MediaModelBadge(
  modelUsed: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(50),
    color = Color(0xBB19062C),
    modifier = modifier,
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
