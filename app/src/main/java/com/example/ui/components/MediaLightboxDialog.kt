package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.files.MediaSaveHelper
import com.example.model.GeneratedMediaType
import com.example.model.OpenClawHost

@Composable
fun MediaLightboxDialog(
  mediaUrl: String,
  mediaType: GeneratedMediaType,
  prompt: String,
  modelUsed: String,
  durationSec: Int?,
  activeHost: OpenClawHost?,
  onDismiss: () -> Unit,
  onSaveToPhone: () -> Unit,
  onSendToChat: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val resolvedUrl = remember(mediaUrl, activeHost?.effectiveBaseUrl) {
    MediaSaveHelper.resolveDownloadUrl(mediaUrl, activeHost) ?: mediaUrl
  }
  val authHeaders = remember(resolvedUrl, activeHost?.effectiveBaseUrl, activeHost?.gatewayToken) {
    MediaSaveHelper.authHeadersForUrl(resolvedUrl, activeHost)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xF511031F))
        .safeDrawingPadding()
        .testTag("media_lightbox_dialog"),
    ) {
      when (mediaType) {
        GeneratedMediaType.IMAGE -> {
          var scale by remember { mutableFloatStateOf(1f) }
          var offset by remember { mutableStateOf(Offset.Zero) }

          AsyncImage(
            model = MediaSaveHelper.buildAuthorizedImageRequest(context, mediaUrl, activeHost),
            imageLoader = remember(context) { MediaSaveHelper.getImageLoader(context) },
            contentDescription = prompt.ifBlank { "Zoomable generated image" },
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxSize()
              .padding(bottom = 160.dp, top = 56.dp)
              .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                  val newScale = (scale * zoom).coerceIn(1f, 5f)
                  scale = newScale
                  offset = if (newScale > 1.01f) offset + pan else Offset.Zero
                }
              }
              .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
              ),
          )
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
              setOnCompletionListener { isPlaying = false }
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

          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(top = 56.dp, bottom = 180.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color(0xFFFF8CE0), modifier = Modifier.size(72.dp))
            Text(
              text = resolvedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Audio Clip" },
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(vertical = 12.dp),
            )
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
              modifier = Modifier.size(64.dp),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                modifier = Modifier.size(34.dp),
              )
            }
          }
        }
        GeneratedMediaType.DOCUMENT -> {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(top = 56.dp, bottom = 180.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Rounded.Description, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(76.dp))
            Text(
              text = resolvedUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Document" },
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(top = 12.dp),
            )
            Text(
              text = "Use Save / Open below to download into Downloads/ClawDudebro and open this file.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFFFD1F3),
              modifier = Modifier.padding(top = 6.dp),
            )
          }
        }
        GeneratedMediaType.VIDEO -> {
        var muted by remember { mutableStateOf(false) }
        var isPlaying by remember { mutableStateOf(true) }
        var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }
        var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp, bottom = 180.dp),
          contentAlignment = Alignment.Center,
        ) {
          AndroidView(
            factory = { ctx ->
              VideoView(ctx).apply {
                videoViewRef = this
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
                  if (isPlaying) start()
                }
              }
            },
            update = { vv ->
              videoViewRef = vv
              val vol = if (muted) 0f else 1f
              runCatching { playerRef?.setVolume(vol, vol) }
            },
            modifier = Modifier.fillMaxSize(),
          )

          Row(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = {
                val vv = videoViewRef
                if (vv != null) {
                  if (isPlaying) {
                    vv.pause()
                    isPlaying = false
                  } else {
                    vv.start()
                    isPlaying = true
                  }
                }
              },
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xCC2C084A),
                contentColor = Color.White,
              ),
              modifier = Modifier
                .size(48.dp)
                .testTag("lightbox_video_play_pause"),
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause video" else "Play video",
              )
            }

            IconButton(
              onClick = { muted = !muted },
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color(0xCC2C084A),
                contentColor = Color.White,
              ),
              modifier = Modifier
                .size(48.dp)
                .testTag("lightbox_video_audio_toggle"),
            ) {
              Icon(
                imageVector = if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = if (muted) "Unmute audio" else "Mute audio",
              )
            }

            if ((durationSec ?: 0) > 0) {
              Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xCC2C084A),
              ) {
                Text(
                  text = "${durationSec}s Loop",
                  style = MaterialTheme.typography.labelMedium,
                  color = Color(0xFFFFE57F),
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          }
        }
        }
      }

      // Close button at top-right
      IconButton(
        onClick = onDismiss,
        colors = IconButtonDefaults.iconButtonColors(
          containerColor = Color(0xCC2C084A),
          contentColor = Color.White,
        ),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(14.dp)
          .size(48.dp)
          .testTag("lightbox_close_button"),
      ) {
        Icon(Icons.Rounded.Close, contentDescription = "Close preview")
      }

      // Metadata & action sheet anchored at bottom
      Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xEB22083B),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Surface(
              shape = RoundedCornerShape(50),
              color = Color(0xFFFF2E93),
            ) {
              Text(
                text = mediaType.label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
              )
            }
            if (modelUsed.isNotBlank()) {
              Text(
                text = "Model: $modelUsed",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFE57F),
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          if (prompt.isNotBlank()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            ) {
              Text(
                text = prompt,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFF6E6FF),
                maxLines = 3,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            val saveButtonLabel = when (mediaType) {
              GeneratedMediaType.IMAGE -> "Save to Gallery"
              GeneratedMediaType.DOCUMENT -> "Save / Open Doc"
              else -> "Save to Phone"
            }
            OutlinedButton(
              onClick = onSaveToPhone,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              modifier = Modifier
                .weight(1f)
                .testTag("lightbox_save_phone_button"),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(saveButtonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (onSendToChat != null) {
              Button(
                onClick = onSendToChat,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFFFF6D00),
                  contentColor = Color.White,
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("lightbox_send_chat_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send to Chat")
              }
            }
          }
        }
      }
    }
  }
}
