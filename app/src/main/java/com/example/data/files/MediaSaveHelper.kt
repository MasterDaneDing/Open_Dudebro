package com.example.data.files

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.model.GeneratedMediaType
import com.example.model.OpenClawHost
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object MediaSaveHelper {

  private const val TAG = "MediaSaveHelper"

  private val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  @Volatile
  private var cachedImageLoader: ImageLoader? = null

  fun getImageLoader(context: Context): ImageLoader {
    cachedImageLoader?.let { return it }
    return synchronized(this) {
      cachedImageLoader ?: ImageLoader.Builder(context.applicationContext)
        .okHttpClient(client)
        .crossfade(true)
        .build()
        .also { cachedImageLoader = it }
    }
  }

  private fun encodeQueryValue(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  fun isDeviceLocalFileUri(raw: String): Boolean {
    val t = raw.trim()
    return t.startsWith("file:///data/", ignoreCase = true) ||
      t.startsWith("file:///storage/", ignoreCase = true) ||
      t.startsWith("file:///sdcard/", ignoreCase = true) ||
      t.startsWith("/data/", ignoreCase = true) ||
      t.startsWith("/storage/", ignoreCase = true)
  }

  fun normalizeHostMediaSource(raw: String): String {
    var trimmed = raw.trim().trim('"', '\'', '`')
    if (trimmed.startsWith("file://localhost", ignoreCase = true) && !isDeviceLocalFileUri(trimmed)) {
      trimmed = trimmed.substring("file://localhost".length)
    } else if (trimmed.startsWith("file://", ignoreCase = true) && !isDeviceLocalFileUri(trimmed)) {
      trimmed = trimmed.substring("file://".length)
    }
    trimmed = trimmed.replace('\\', '/')
    if (trimmed.startsWith("./")) {
      trimmed = trimmed.removePrefix("./")
    }
    return trimmed
  }

  const val DEFAULT_DEVICE_ID = "claw-dudebro-apk"
  const val DEFAULT_AGENT_ID = "main"

  /**
   * Builds an OpenClaw `/__openclaw__/assistant-media` URL:
   * - Direct byte fetch: `GET /__openclaw__/assistant-media?source=<path>` (with `Authorization: Bearer <token>`)
   * - Metadata probe: `GET /__openclaw__/assistant-media?meta=1&source=<path>`
   * - Ticketed byte fetch: `GET /__openclaw__/assistant-media?mediaTicket=<t>&source=<path>`
   *
   * Never appends `sessionKey` or `agentId` query parameters.
   */
  fun buildAssistantMediaUrl(
    sourcePath: String,
    host: OpenClawHost?,
    @Suppress("UNUSED_PARAMETER") sessionKey: String = "conv:main",
    @Suppress("UNUSED_PARAMETER") agentId: String = DEFAULT_AGENT_ID,
    meta: Boolean = false,
    mediaTicket: String? = null,
    baseUrlOverride: String? = null,
  ): String? {
    val base = (baseUrlOverride ?: host?.effectiveBaseUrl)?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) return null
    val cleanSource = normalizeHostMediaSource(sourcePath)
    if (cleanSource.isBlank()) return null
    val encSource = encodeQueryValue(cleanSource)
    return buildString {
      append("$base/__openclaw__/assistant-media?")
      if (meta) append("meta=1&")
      if (!mediaTicket.isNullOrBlank()) {
        append("mediaTicket=${encodeQueryValue(mediaTicket)}&")
      }
      append("source=$encSource")
    }
  }

  /**
   * Strips any legacy `sessionKey` / `agentId` query parameters from an `/__openclaw__/assistant-media`
   * URL and normalizes it to `?source=<path>`, `?meta=1&source=<path>`, or `?mediaTicket=<t>&source=<path>`.
   */
  fun sanitizeAssistantMediaUrl(
    url: String,
    forceMeta: Boolean = false,
    mediaTicketOverride: String? = null,
  ): String {
    if (!url.contains("/__openclaw__/assistant-media", ignoreCase = true)) return url
    val parsed = url.toHttpUrlOrNull() ?: return url
    val source = parsed.queryParameter("source")?.trim().orEmpty()
    if (source.isBlank()) return url
    val base = buildString {
      append(parsed.scheme)
      append("://")
      append(parsed.host)
      val isDefaultPort = (parsed.scheme == "http" && parsed.port == 80) ||
        (parsed.scheme == "https" && parsed.port == 443)
      if (!isDefaultPort) {
        append(":")
        append(parsed.port)
      }
    }
    val ticket = mediaTicketOverride?.trim()?.takeIf { it.isNotBlank() }
      ?: parsed.queryParameter("mediaTicket")?.trim()?.takeIf { it.isNotBlank() && !forceMeta }
    val encSource = encodeQueryValue(source)
    return buildString {
      append("$base/__openclaw__/assistant-media?")
      if (forceMeta) append("meta=1&")
      if (!ticket.isNullOrBlank()) {
        append("mediaTicket=${encodeQueryValue(ticket)}&")
      }
      append("source=$encSource")
    }
  }

  private fun rewriteHostBaseIfNeeded(url: String, host: OpenClawHost?, targetBaseOverride: String? = null): String {
    val activeBase = (targetBaseOverride ?: host?.effectiveBaseUrl)?.trim()?.trimEnd('/').orEmpty()
    if (activeBase.isBlank()) return url
    val knownBases = listOfNotNull(
      host?.effectiveLanUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() },
      host?.effectiveTailscaleUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() },
      host?.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() },
    ).distinct()
    val matchedBase = knownBases.firstOrNull { url.startsWith(it, ignoreCase = true) } ?: return url
    return if (matchedBase.equals(activeBase, ignoreCase = true)) {
      url
    } else {
      activeBase + url.substring(matchedBase.length)
    }
  }

  private fun fetchUrlBytes(
    url: String,
    host: OpenClawHost?,
    deviceId: String = DEFAULT_DEVICE_ID,
  ): Pair<ByteArray, String>? {
    val primaryUrl = rewriteHostBaseIfNeeded(url, host)
    val fallbackBase = host?.fallbackBaseUrl?.takeIf { it.isNotBlank() }
    val urlsToTry = buildList {
      add(primaryUrl)
      if (fallbackBase != null) {
        val alt = rewriteHostBaseIfNeeded(url, host, targetBaseOverride = fallbackBase)
        if (!alt.equals(primaryUrl, ignoreCase = true)) {
          add(alt)
        }
      }
    }

    for (targetUrl in urlsToTry) {
      val result = try {
        fetchSingleUrlBytes(targetUrl, host, deviceId)
      } catch (_: Exception) {
        null
      }
      if (result != null) return result
    }
    return null
  }

  private fun fetchSingleUrlBytes(
    rewrittenUrl: String,
    host: OpenClawHost?,
    deviceId: String,
  ): Pair<ByteArray, String>? {
    val byteUrl = sanitizeAssistantMediaUrl(rewrittenUrl)
    val headers = authHeadersForUrl(byteUrl, host, deviceId)
    val req = Request.Builder()
      .url(byteUrl)
      .get()
      .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
      .build()

    client.newCall(req).execute().use { resp ->
      if (resp.isSuccessful) {
        val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
        if (contentType.contains("text/html", ignoreCase = true)) return null
        val bytes = resp.body?.bytes() ?: return null
        if (bytes.isEmpty()) return null
        return bytes to contentType
      }
    }

    // Two-step ticket flow: probe ?meta=1&source=<path>, then fetch bytes with ?mediaTicket=<t>&source=<path> + Bearer token
    // (never append sessionKey or agentId to byte fetch URLs)
    if (byteUrl.contains("/__openclaw__/assistant-media", ignoreCase = true) &&
      !byteUrl.contains("mediaTicket=", ignoreCase = true)
    ) {
      val metaUrl = sanitizeAssistantMediaUrl(byteUrl, forceMeta = true)
      val metaReq = Request.Builder()
        .url(metaUrl)
        .get()
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()
      client.newCall(metaReq).execute().use { metaResp ->
        if (!metaResp.isSuccessful) return null
        val metaJson = runCatching { JSONObject(metaResp.body?.string().orEmpty()) }.getOrNull() ?: return null
        val available = metaJson.optBoolean("available", true)
        val ticket = metaJson.optString("mediaTicket").trim()
        val metaMime = metaJson.optString("mimeType").trim()
        if (!available || ticket.isBlank()) return null

        val ticketUrl = sanitizeAssistantMediaUrl(byteUrl, forceMeta = false, mediaTicketOverride = ticket)
        val ticketReq = Request.Builder()
          .url(ticketUrl)
          .get()
          .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
          .build()
        client.newCall(ticketReq).execute().use { byteResp ->
          if (!byteResp.isSuccessful) return null
          val contentType = byteResp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
          if (contentType.contains("text/html", ignoreCase = true)) return null
          val bytes = byteResp.body?.bytes() ?: return null
          if (bytes.isEmpty()) return null
          return bytes to contentType.ifBlank { metaMime }
        }
      }
    }
    return null
  }

  /**
   * Decodes a `data:image/...;base64,...` URI and persists it to `filesDir/received_media/<uuid>.<ext>`
   * returning a short `file:///...` URI (safe for Firestore's 1500-char `mediaUrl` limit).
   */
  fun cacheBase64ImageToFile(filesDir: File?, dataUri: String): String? {
    if (filesDir == null) return null
    val trimmed = dataUri.trim()
    if (!trimmed.startsWith("data:", ignoreCase = true) || !trimmed.contains(";base64,")) return null
    return try {
      val header = trimmed.substringAfter("data:").substringBefore(";base64,").lowercase(Locale.US)
      val b64 = trimmed.substringAfter(";base64,").replace("\\s+".toRegex(), "")
      val bytes = Base64.decode(b64, Base64.DEFAULT)
      if (bytes.isEmpty()) return null
      val ext = when {
        header.contains("png") -> "png"
        header.contains("webp") -> "webp"
        header.contains("gif") -> "gif"
        else -> "jpg"
      }
      val dir = File(filesDir, "received_media").apply { mkdirs() }
      val target = File(dir, "img_${UUID.randomUUID()}.$ext")
      FileOutputStream(target).use { it.write(bytes) }
      "file://${target.absolutePath}"
    } catch (e: Exception) {
      Log.w(TAG, "Failed caching base64 image: ${e.message}")
      null
    }
  }

  /**
   * Attempts to download a host-returned media path (e.g. `MEDIA:/tmp/out.png` or `PATH=/abs/path.png`)
   * or URL via OpenClaw's `/__openclaw__/assistant-media` route into `filesDir/received_media`.
   */
  fun tryDownloadHostMediaToLocalFile(
    filesDir: File?,
    rawPathOrUrl: String,
    host: OpenClawHost?,
    sessionKey: String = "conv:main",
    agentId: String = DEFAULT_AGENT_ID,
    deviceId: String = DEFAULT_DEVICE_ID,
  ): String? {
    if (filesDir == null) return null
    val normalizedSource = normalizeHostMediaSource(rawPathOrUrl)
    val candidates = buildCandidateMediaUrls(rawPathOrUrl, host, sessionKey, agentId)
    for (candidate in candidates) {
      if (isDeviceLocalFileUri(candidate)) continue
      try {
        val (bytes, rawContentType) = fetchUrlBytes(candidate, host, deviceId) ?: continue
        val contentType = rawContentType.lowercase(Locale.US)
        val rawExt = normalizedSource.substringBefore('?').substringAfterLast('.', "")
          .lowercase(Locale.US)
          .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
          ?: candidate.substringBefore('?').substringAfterLast('.', "")
            .lowercase(Locale.US)
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        val ext = rawExt ?: when {
          contentType.contains("png") -> "png"
          contentType.contains("webp") -> "webp"
          contentType.contains("gif") -> "gif"
          contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
          contentType.contains("mp4") -> "mp4"
          contentType.contains("pdf") -> "pdf"
          contentType.contains("wordprocessingml") -> "docx"
          else -> "bin"
        }
        val dir = File(filesDir, "received_media").apply { mkdirs() }
        val target = File(dir, "media_${UUID.randomUUID()}.$ext")
        FileOutputStream(target).use { it.write(bytes) }
        return "file://${target.absolutePath}"
      } catch (_: Exception) {
        // Probe next candidate endpoint
      }
    }
    return null
  }

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

      val urlExt = mediaUrl.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.US)
        .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }

      val effectiveType = classifyType(mediaType, detectedMime, urlExt)
      val ext = resolveExtension(urlExt, detectedMime, effectiveType)
      val mimeType = resolveMime(detectedMime, ext, effectiveType)

      val cleanPrefix = suggestedPrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "ClawDudebro" }
      val fileName = "${cleanPrefix}_${System.currentTimeMillis()}.$ext"

      val savedMessage = writeToPublicMediaStore(context, bytes, fileName, mimeType, effectiveType)
      Result.success(savedMessage)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Downloads a document (e.g. .docx or .pdf) returned by OpenClaw, saves a copy in Downloads/ClawDudebro,
   * caches a shareable copy inside cacheDir/shared_docs, and launches ACTION_VIEW via FileProvider.
   */
  suspend fun saveAndOpenDocument(
    context: Context,
    mediaUrl: String,
    host: OpenClawHost?,
    suggestedPrefix: String = "ClawDudebro_Doc",
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val (bytes, detectedMime) = loadBytesAndMime(mediaUrl, GeneratedMediaType.DOCUMENT, host)
        ?: return@withContext Result.failure(IllegalStateException("Could not download document from OpenClaw host."))

      val urlName = mediaUrl.substringBefore('?').substringAfterLast('/')
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
      val urlExt = mediaUrl.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.US)
        .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
      val ext = urlExt ?: when {
        detectedMime.contains("wordprocessingml") -> "docx"
        detectedMime.contains("pdf") -> "pdf"
        detectedMime.contains("text") -> "txt"
        else -> "docx"
      }
      val mimeType = resolveMime(detectedMime, ext, GeneratedMediaType.DOCUMENT)
      val cleanBaseName = urlName.substringBeforeLast('.', "").ifBlank {
        suggestedPrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "ClawDudebro_Doc" }
      }
      val fileName = "${cleanBaseName}_${System.currentTimeMillis()}.$ext"

      // 1. Persist permanent copy in Downloads/ClawDudebro
      val savedSummary = writeToPublicMediaStore(context, bytes, fileName, mimeType, GeneratedMediaType.DOCUMENT)

      // 2. Write cache copy & open via FileProvider
      val cacheDoc = File(context.cacheDir, fileName)
      FileOutputStream(cacheDoc).use { it.write(bytes) }
      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cacheDoc,
      )
      val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      val launched = withContext(Dispatchers.Main) {
        try {
          context.startActivity(viewIntent)
          true
        } catch (_: ActivityNotFoundException) {
          false
        }
      }
      if (launched) {
        Result.success("$savedSummary • Opened")
      } else {
        Result.success("$savedSummary (install a $ext viewer app to open)")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun writeToPublicMediaStore(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String,
    effectiveType: GeneratedMediaType,
  ): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val (collection, relativeDir, folderLabel) = when (effectiveType) {
        GeneratedMediaType.VIDEO -> Triple(
          MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
          "${Environment.DIRECTORY_MOVIES}/ClawDudebro",
          "Movies",
        )
        GeneratedMediaType.AUDIO -> Triple(
          MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
          "${Environment.DIRECTORY_MUSIC}/ClawDudebro",
          "Music",
        )
        GeneratedMediaType.DOCUMENT -> Triple(
          MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
          "${Environment.DIRECTORY_DOWNLOADS}/ClawDudebro",
          "Downloads",
        )
        GeneratedMediaType.IMAGE -> Triple(
          MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
          "${Environment.DIRECTORY_PICTURES}/ClawDudebro",
          "Pictures",
        )
      }

      val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
      }

      val uri = context.contentResolver.insert(collection, values)
        ?: throw IllegalStateException("Failed to create MediaStore entry.")

      context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(bytes)
      } ?: throw IllegalStateException("Failed to write media stream.")

      val readyValues = ContentValues().apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
      }
      context.contentResolver.update(uri, readyValues, null, null)
      return "Saved $fileName to $folderLabel/ClawDudebro"
    } else {
      val baseDirType = when (effectiveType) {
        GeneratedMediaType.VIDEO -> Environment.DIRECTORY_MOVIES
        GeneratedMediaType.AUDIO -> Environment.DIRECTORY_MUSIC
        GeneratedMediaType.DOCUMENT -> Environment.DIRECTORY_DOWNLOADS
        GeneratedMediaType.IMAGE -> Environment.DIRECTORY_PICTURES
      }
      val dir = File(context.getExternalFilesDir(baseDirType), "ClawDudebro").apply { mkdirs() }
      val target = File(dir, fileName)
      FileOutputStream(target).use { it.write(bytes) }
      return "Saved ${target.name} to device"
    }
  }

  private fun classifyType(
    mediaType: GeneratedMediaType,
    detectedMime: String,
    urlExt: String?,
  ): GeneratedMediaType = when {
    mediaType == GeneratedMediaType.AUDIO || detectedMime.startsWith("audio/") ||
      urlExt in setOf("mp3", "wav", "m4a", "ogg", "aac") -> GeneratedMediaType.AUDIO
    mediaType == GeneratedMediaType.DOCUMENT ||
      detectedMime == "application/pdf" ||
      detectedMime.contains("wordprocessingml") ||
      urlExt in setOf("pdf", "docx", "doc", "txt", "md", "csv", "xlsx") -> GeneratedMediaType.DOCUMENT
    mediaType == GeneratedMediaType.VIDEO || detectedMime.startsWith("video/") ||
      urlExt in setOf("mp4", "webm", "mov", "mkv") -> GeneratedMediaType.VIDEO
    else -> GeneratedMediaType.IMAGE
  }

  private fun resolveExtension(
    urlExt: String?,
    detectedMime: String,
    effectiveType: GeneratedMediaType,
  ): String = urlExt ?: when {
    detectedMime.contains("png") -> "png"
    detectedMime.contains("webp") -> "webp"
    detectedMime.contains("gif") -> "gif"
    detectedMime.contains("webm") -> "webm"
    detectedMime.contains("quicktime") -> "mov"
    detectedMime.contains("mpeg") && effectiveType == GeneratedMediaType.AUDIO -> "mp3"
    detectedMime.contains("wav") -> "wav"
    detectedMime.contains("ogg") -> "ogg"
    detectedMime.contains("pdf") -> "pdf"
    detectedMime.contains("wordprocessingml") -> "docx"
    effectiveType == GeneratedMediaType.VIDEO -> "mp4"
    effectiveType == GeneratedMediaType.AUDIO -> "mp3"
    effectiveType == GeneratedMediaType.DOCUMENT -> "docx"
    else -> "jpg"
  }

  private fun resolveMime(
    detectedMime: String,
    ext: String,
    effectiveType: GeneratedMediaType,
  ): String = detectedMime.ifBlank {
    when (effectiveType) {
      GeneratedMediaType.VIDEO -> "video/$ext"
      GeneratedMediaType.AUDIO -> if (ext == "mp3") "audio/mpeg" else "audio/$ext"
      GeneratedMediaType.DOCUMENT -> when (ext) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "txt", "md" -> "text/plain"
        else -> "application/octet-stream"
      }
      GeneratedMediaType.IMAGE -> if (ext == "jpg") "image/jpeg" else "image/$ext"
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
      val b64 = trimmed.substringAfter(";base64,").replace("\\s+".toRegex(), "")
      val decoded = Base64.decode(b64, Base64.DEFAULT)
      return decoded to header
    }

    if (isDeviceLocalFileUri(trimmed)) {
      val filePath = trimmed.removePrefix("file://")
      val localFile = File(filePath)
      if (localFile.exists() && localFile.isFile) {
        val ext = localFile.extension.lowercase(Locale.US)
        val mime = when (ext) {
          "png" -> "image/png"
          "webp" -> "image/webp"
          "gif" -> "image/gif"
          "mp4" -> "video/mp4"
          "pdf" -> "application/pdf"
          "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          else -> "image/jpeg"
        }
        return localFile.readBytes() to mime
      }
    }

    val candidates = buildCandidateMediaUrls(trimmed, host)
    for (resolvedUrl in candidates) {
      if (isDeviceLocalFileUri(resolvedUrl)) continue
      try {
        val (bytes, contentType) = fetchUrlBytes(resolvedUrl, host) ?: continue
        val resolvedMime = contentType.ifBlank {
          when (mediaType) {
            GeneratedMediaType.VIDEO -> "video/mp4"
            GeneratedMediaType.AUDIO -> "audio/mpeg"
            GeneratedMediaType.DOCUMENT -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            GeneratedMediaType.IMAGE -> "image/png"
          }
        }
        return bytes to resolvedMime
      } catch (_: Exception) {
        // Try next candidate URL
      }
    }
    return null
  }

  fun authHeadersForUrl(
    resolvedUrl: String,
    host: OpenClawHost?,
    deviceId: String = DEFAULT_DEVICE_ID,
  ): Map<String, String> {
    val token = host?.gatewayToken?.trim().orEmpty()
    val bases = listOfNotNull(
      host?.effectiveBaseUrl?.takeIf { it.isNotBlank() },
      host?.effectiveLanUrl?.takeIf { it.isNotBlank() },
      host?.effectiveTailscaleUrl?.takeIf { it.isNotBlank() },
    ).map { it.trimEnd('/') }
    val isHostUrl = bases.any { resolvedUrl.startsWith(it, ignoreCase = true) } ||
      resolvedUrl.contains("/__openclaw__/assistant-media", ignoreCase = true)
    if (!isHostUrl) return emptyMap()

    return buildMap {
      if (token.isNotBlank()) {
        put("Authorization", "Bearer $token")
      }
      val cleanDevice = deviceId.trim().ifBlank { DEFAULT_DEVICE_ID }
      put("x-openclaw-device-id", cleanDevice)
      put("x-openclaw-client", DEFAULT_DEVICE_ID)
    }
  }

  fun buildAuthorizedImageRequest(
    context: Context,
    mediaUrl: String,
    host: OpenClawHost?,
    deviceId: String = DEFAULT_DEVICE_ID,
  ): ImageRequest {
    val resolvedUrl = resolveDownloadUrl(mediaUrl, host) ?: mediaUrl
    val dataModel: Any = if (isDeviceLocalFileUri(resolvedUrl)) {
      File(resolvedUrl.removePrefix("file://"))
    } else {
      sanitizeAssistantMediaUrl(rewriteHostBaseIfNeeded(resolvedUrl, host))
    }
    val headers = if (dataModel is String) authHeadersForUrl(dataModel, host, deviceId) else emptyMap()
    return ImageRequest.Builder(context)
      .data(dataModel)
      .crossfade(true)
      .apply {
        headers.forEach { (k, v) -> addHeader(k, v) }
      }
      .build()
  }

  fun buildCandidateMediaUrls(
    raw: String?,
    host: OpenClawHost?,
    sessionKey: String = "conv:main",
    agentId: String = DEFAULT_AGENT_ID,
  ): List<String> {
    val trimmed = raw?.trim().orEmpty().trim('"', '\'', '`')
    if (trimmed.isBlank()) return emptyList()
    if (trimmed.startsWith("http://", true) ||
      trimmed.startsWith("https://", true) ||
      trimmed.startsWith("data:", true) ||
      isDeviceLocalFileUri(trimmed)
    ) {
      return listOf(sanitizeAssistantMediaUrl(rewriteHostBaseIfNeeded(trimmed, host)))
    }

    val cleanSource = normalizeHostMediaSource(trimmed)
    val base = host?.effectiveBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) return listOf(cleanSource)

    val assistantMediaUrl = buildAssistantMediaUrl(cleanSource, host, sessionKey, agentId)
    val encoded = URLEncoder.encode(cleanSource, "UTF-8")
    val fileNameOnly = cleanSource.substringAfterLast('/')
    val encodedName = URLEncoder.encode(fileNameOnly, "UTF-8")
    return buildList {
      if (assistantMediaUrl != null) {
        add(assistantMediaUrl)
      }
      when {
        cleanSource.startsWith("media/", ignoreCase = true) ||
          cleanSource.startsWith("files/", ignoreCase = true) ||
          cleanSource.startsWith("outputs/", ignoreCase = true) ||
          cleanSource.startsWith("static/", ignoreCase = true) ||
          cleanSource.startsWith("generated/", ignoreCase = true) -> add("$base/$cleanSource")
        cleanSource.startsWith("/media/", ignoreCase = true) ||
          cleanSource.startsWith("/files/", ignoreCase = true) ||
          cleanSource.startsWith("/outputs/", ignoreCase = true) ||
          cleanSource.startsWith("/static/", ignoreCase = true) -> add("$base$cleanSource")
        else -> {
          add("$base/media/$encodedName")
          add("$base/files?path=$encoded")
          add("$base/__openclaw__/file?path=$encoded")
          add("$base/v1/files/content?path=$encoded")
          if (cleanSource.startsWith("/")) {
            add("$base$cleanSource")
          } else {
            add("$base/media/$cleanSource")
          }
        }
      }
    }.distinct()
  }

  fun resolveDownloadUrl(
    raw: String?,
    host: OpenClawHost?,
    sessionKey: String = "conv:main",
    agentId: String = DEFAULT_AGENT_ID,
  ): String? {
    val trimmed = raw?.trim().orEmpty().trim('"', '\'', '`')
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("http://", true) ||
      trimmed.startsWith("https://", true) ||
      trimmed.startsWith("data:", true) ||
      isDeviceLocalFileUri(trimmed)
    ) {
      return sanitizeAssistantMediaUrl(rewriteHostBaseIfNeeded(trimmed, host))
    }
    val cleanSource = normalizeHostMediaSource(trimmed)
    val base = host?.effectiveBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) return cleanSource

    return buildAssistantMediaUrl(cleanSource, host, sessionKey, agentId) ?: when {
      cleanSource.startsWith("/") -> "$base$cleanSource"
      cleanSource.startsWith("media/", ignoreCase = true) ||
        cleanSource.startsWith("files/", ignoreCase = true) ||
        cleanSource.startsWith("outputs/", ignoreCase = true) ||
        cleanSource.startsWith("static/", ignoreCase = true) ||
        cleanSource.startsWith("generated/", ignoreCase = true) -> "$base/$cleanSource"
      else -> "$base/media/$cleanSource"
    }
  }
}
