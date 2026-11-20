package com.example.data.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import com.example.model.GeneratedMediaType
import com.example.model.OpenClawHost
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object MediaSaveHelper {

  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

  suspend fun saveMediaToPhone(
    context: Context,
    mediaUrl: String,
    mediaType: GeneratedMediaType,
    host: OpenClawHost?,
    suggestedPrefix: String = "ClawDudebro",
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val (bytes, detectedMime) = loadBytesAndMime(mediaUrl, mediaType, host)
        ?: return@withContext Result.failure(IllegalStateException("Could not download media from host or URL."))

      val isVideo = mediaType == GeneratedMediaType.VIDEO || detectedMime.startsWith("video/")
      val ext = when {
        detectedMime.contains("png") -> "png"
        detectedMime.contains("webp") -> "webp"
        detectedMime.contains("gif") -> "gif"
        detectedMime.contains("webm") -> "webm"
        detectedMime.contains("quicktime") -> "mov"
        isVideo -> "mp4"
        else -> "jpg"
      }
      val mimeType = detectedMime.ifBlank {
        if (isVideo) "video/$ext" else "image/$ext"
      }
      val cleanPrefix = suggestedPrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "ClawDudebro" }
      val fileName = "${cleanPrefix}_${System.currentTimeMillis()}.$ext"

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collection = if (isVideo) {
          MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
          MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativeDir = if (isVideo) {
          "${Environment.DIRECTORY_MOVIES}/ClawDudebro"
        } else {
          "${Environment.DIRECTORY_PICTURES}/ClawDudebro"
        }

        val values = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
          put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
          put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
          put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(collection, values)
          ?: return@withContext Result.failure(IllegalStateException("Failed to create MediaStore entry."))

        context.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(bytes)
        } ?: return@withContext Result.failure(IllegalStateException("Failed to write media stream."))

        val readyValues = ContentValues().apply {
          put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        context.contentResolver.update(uri, readyValues, null, null)
        Result.success("Saved $fileName to ${if (isVideo) "Movies" else "Pictures"}/ClawDudebro")
      } else {
        val dir = File(
          context.getExternalFilesDir(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES),
          "ClawDudebro",
        ).apply { mkdirs() }
        val target = File(dir, fileName)
        FileOutputStream(target).use { it.write(bytes) }
        Result.success("Saved ${target.name} to device")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun loadBytesAndMime(
    mediaUrl: String,
    mediaType: GeneratedMediaType,
    host: OpenClawHost?,
  ): Pair<ByteArray, String>? {
    val trimmed = mediaUrl.trim()
    if (trimmed.startsWith("data:", ignoreCase = true) && trimmed.contains(";base64,")) {
      val header = trimmed.substringAfter("data:").substringBefore(";base64,")
      val b64 = trimmed.substringAfter(";base64,")
      val decoded = Base64.decode(b64, Base64.DEFAULT)
      return decoded to header
    }

    val resolvedUrl = resolveDownloadUrl(trimmed, host) ?: return null
    val req = Request.Builder()
      .url(resolvedUrl)
      .get()
      .apply {
        if (host?.gatewayToken?.isNotBlank() == true &&
          (resolvedUrl.startsWith(host.baseUrl.trim().trimEnd('/')))
        ) {
          addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
        }
      }
      .build()

    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val bytes = resp.body?.bytes() ?: return null
      val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
        .ifBlank { if (mediaType == GeneratedMediaType.VIDEO) "video/mp4" else "image/png" }
      return bytes to contentType
    }
  }

  fun resolveDownloadUrl(raw: String, host: OpenClawHost?): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) || trimmed.startsWith("data:", true)) {
      return trimmed
    }
    val base = host?.baseUrl?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) return null
    return if (trimmed.startsWith("/")) {
      "$base$trimmed"
    } else {
      "$base/files/raw?path=${android.net.Uri.encode(trimmed)}"
    }
  }
}
