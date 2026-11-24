package com.example.data.files

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

enum class CloudDocProvider {
  GOOGLE_DOC,
  GOOGLE_SHEET,
  GOOGLE_SLIDES,
  GOOGLE_DRIVE_FILE,
  DIRECT_HTTP,
}

data class CloudDocUrlInfo(
  val originalUrl: String,
  val downloadUrl: String,
  val provider: CloudDocProvider,
  val fileId: String? = null,
  val defaultFileName: String,
  val defaultMimeType: String,
  val isGoogleWorkspaceOrDrive: Boolean,
)

sealed interface CloudFetchOutcome {
  data class Success(
    val bytes: ByteArray,
    val resolvedName: String,
    val mimeType: String,
    val finalUrl: String,
    val urlInfo: CloudDocUrlInfo,
  ) : CloudFetchOutcome

  data class NeedsAccess(
    val reason: String,
    val urlInfo: CloudDocUrlInfo,
  ) : CloudFetchOutcome

  data class Failure(
    val reason: String,
    val urlInfo: CloudDocUrlInfo,
  ) : CloudFetchOutcome
}

object GoogleDriveDocumentFetcher {

  private const val MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024
  private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) ClawDudebro/1.0 Mobile"

  private val defaultHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }

  private val DOCS_REGEX = Regex(
    """^https?://docs\.google\.com/document/(?:u/\d+/)?d/([a-zA-Z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
  )
  private val SHEETS_REGEX = Regex(
    """^https?://docs\.google\.com/spreadsheets/(?:u/\d+/)?d/([a-zA-Z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
  )
  private val SLIDES_REGEX = Regex(
    """^https?://docs\.google\.com/presentation/(?:u/\d+/)?d/([a-zA-Z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
  )
  private val DRIVE_FILE_D_REGEX = Regex(
    """^https?://drive\.google\.com/(?:u/\d+/)?file/d/([a-zA-Z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
  )
  private val DRIVE_ID_PARAM_REGEX = Regex(
    """[?&]id=([a-zA-Z0-9_-]+)""",
    RegexOption.IGNORE_CASE,
  )

  fun isGoogleDriveOrDocsUrl(rawUrl: String): Boolean {
    val lower = rawUrl.trim().lowercase(Locale.US)
    return lower.contains("drive.google.com/") ||
      lower.contains("docs.google.com/") ||
      lower.contains("drive.usercontent.google.com/")
  }

  fun parseCloudDocumentUrl(rawUrl: String): CloudDocUrlInfo {
    val trimmed = rawUrl.trim()

    DOCS_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
      return CloudDocUrlInfo(
        originalUrl = trimmed,
        downloadUrl = "https://docs.google.com/document/d/$id/export?format=txt",
        provider = CloudDocProvider.GOOGLE_DOC,
        fileId = id,
        defaultFileName = "GoogleDoc_${id.take(8)}.txt",
        defaultMimeType = "text/plain",
        isGoogleWorkspaceOrDrive = true,
      )
    }

    SHEETS_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
      return CloudDocUrlInfo(
        originalUrl = trimmed,
        downloadUrl = "https://docs.google.com/spreadsheets/d/$id/export?format=csv",
        provider = CloudDocProvider.GOOGLE_SHEET,
        fileId = id,
        defaultFileName = "GoogleSheet_${id.take(8)}.csv",
        defaultMimeType = "text/csv",
        isGoogleWorkspaceOrDrive = true,
      )
    }

    SLIDES_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
      return CloudDocUrlInfo(
        originalUrl = trimmed,
        downloadUrl = "https://docs.google.com/presentation/d/$id/export/txt",
        provider = CloudDocProvider.GOOGLE_SLIDES,
        fileId = id,
        defaultFileName = "GoogleSlides_${id.take(8)}.txt",
        defaultMimeType = "text/plain",
        isGoogleWorkspaceOrDrive = true,
      )
    }

    val driveId = DRIVE_FILE_D_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
      ?: if (trimmed.contains("drive.google.com", ignoreCase = true) ||
        trimmed.contains("drive.usercontent.google.com", ignoreCase = true)
      ) {
        DRIVE_ID_PARAM_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
      } else {
        null
      }

    if (!driveId.isNullOrBlank()) {
      return CloudDocUrlInfo(
        originalUrl = trimmed,
        downloadUrl = "https://drive.google.com/uc?export=download&id=$driveId",
        provider = CloudDocProvider.GOOGLE_DRIVE_FILE,
        fileId = driveId,
        defaultFileName = "DriveFile_${driveId.take(8)}",
        defaultMimeType = "",
        isGoogleWorkspaceOrDrive = true,
      )
    }

    val urlPathSegment = trimmed.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
    val fallbackName = urlPathSegment.takeIf { it.isNotBlank() && it.contains('.') } ?: "CloudDocument"
    return CloudDocUrlInfo(
      originalUrl = trimmed,
      downloadUrl = trimmed,
      provider = CloudDocProvider.DIRECT_HTTP,
      fileId = null,
      defaultFileName = fallbackName,
      defaultMimeType = "",
      isGoogleWorkspaceOrDrive = isGoogleDriveOrDocsUrl(trimmed),
    )
  }

  fun parseContentDispositionFilename(header: String?): String? {
    if (header.isNullOrBlank()) return null
    val extMatch = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\r\n]+)""").find(header)
    if (extMatch != null) {
      val decoded = runCatching { URLDecoder.decode(extMatch.groupValues[1].trim('"', ' '), "UTF-8") }
        .getOrNull()
      if (!decoded.isNullOrBlank()) return decoded
    }
    val quotedMatch = Regex("""filename\s*=\s*"([^"]+)"""").find(header)
    if (quotedMatch != null) {
      val name = quotedMatch.groupValues[1].trim()
      if (name.isNotBlank()) return name
    }
    val bareMatch = Regex("""filename\s*=\s*([^;\r\n]+)""").find(header)
    return bareMatch?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')?.takeIf { it.isNotBlank() }
  }

  fun extractGoogleDriveConfirmUrl(html: String, fileId: String?): String? {
    val formMatch = Regex(
      """<form[^>]+id=["']download-form["'][^>]+action=["']([^"']+)["']""",
      RegexOption.IGNORE_CASE,
    ).find(html)
    if (formMatch != null) {
      val action = formMatch.groupValues[1].replace("&amp;", "&")
      val params = Regex("""<input[^>]+name=["']([^"']+)["'][^>]+value=["']([^"']*)["']""")
        .findAll(html)
        .map { "${it.groupValues[1]}=${it.groupValues[2]}" }
        .toList()
      if (action.startsWith("http", ignoreCase = true)) {
        val sep = if (action.contains('?')) "&" else "?"
        return if (params.isNotEmpty()) "$action$sep${params.joinToString("&")}" else action
      }
    }
    val confirmMatch = Regex("""confirm=([a-zA-Z0-9_-]+)""").find(html)?.groupValues?.getOrNull(1)
    if (!confirmMatch.isNullOrBlank() && !fileId.isNullOrBlank()) {
      return "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$confirmMatch"
    }
    return null
  }

  fun isLikelyGoogleAuthHtml(bytes: ByteArray, mimeType: String, finalUrl: String): Boolean {
    val lowerUrl = finalUrl.lowercase(Locale.US)
    if (lowerUrl.contains("accounts.google.com") || lowerUrl.contains("servicelogin")) {
      return true
    }
    val lowerMime = mimeType.lowercase(Locale.US)
    if (!lowerMime.contains("html") && bytes.isNotEmpty()) {
      val head = String(bytes, 0, minOf(bytes.size, 256), StandardCharsets.UTF_8).trimStart()
      if (!head.startsWith("<!doctype", ignoreCase = true) && !head.startsWith("<html", ignoreCase = true)) {
        return false
      }
    }
    val sample = String(bytes, 0, minOf(bytes.size, 16_384), StandardCharsets.UTF_8)
      .lowercase(Locale.US)
    return sample.contains("accounts.google.com") ||
      sample.contains("servicelogin") ||
      sample.contains("you need access") ||
      sample.contains("request access") ||
      sample.contains("sign in - google") ||
      sample.contains("access denied")
  }

  fun fetchDocument(
    rawUrl: String,
    customNameHint: String = "",
    httpClient: OkHttpClient = defaultHttpClient,
  ): CloudFetchOutcome {
    val urlInfo = parseCloudDocumentUrl(rawUrl)
    if (!urlInfo.downloadUrl.startsWith("http://", ignoreCase = true) &&
      !urlInfo.downloadUrl.startsWith("https://", ignoreCase = true)
    ) {
      return CloudFetchOutcome.Failure("Invalid document URL: $rawUrl", urlInfo)
    }

    return try {
      val firstOutcome = executeSingleDownload(urlInfo.downloadUrl, urlInfo, customNameHint, httpClient)
      if (firstOutcome is CloudFetchOutcome.Success &&
        firstOutcome.mimeType.contains("html", ignoreCase = true) &&
        urlInfo.provider == CloudDocProvider.GOOGLE_DRIVE_FILE
      ) {
        val html = String(firstOutcome.bytes, StandardCharsets.UTF_8)
        val confirmUrl = extractGoogleDriveConfirmUrl(html, urlInfo.fileId)
        if (!confirmUrl.isNullOrBlank()) {
          return executeSingleDownload(confirmUrl, urlInfo, customNameHint, httpClient)
        }
        if (isLikelyGoogleAuthHtml(firstOutcome.bytes, firstOutcome.mimeType, firstOutcome.finalUrl)) {
          return CloudFetchOutcome.NeedsAccess(
            reason = "Google Drive file requires sign-in or 'Anyone with the link' sharing (or use Pick PDF / Docs -> Google Drive).",
            urlInfo = urlInfo,
          )
        }
      }
      firstOutcome
    } catch (e: Exception) {
      CloudFetchOutcome.Failure(
        reason = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Failed to fetch document URL",
        urlInfo = urlInfo,
      )
    }
  }

  private fun executeSingleDownload(
    targetUrl: String,
    urlInfo: CloudDocUrlInfo,
    customNameHint: String,
    httpClient: OkHttpClient,
  ): CloudFetchOutcome {
    val req = Request.Builder()
      .url(targetUrl)
      .get()
      .addHeader("User-Agent", USER_AGENT)
      .build()

    httpClient.newCall(req).execute().use { resp ->
      val finalUrl = resp.request.url.toString()
      val contentType = resp.header("Content-Type").orEmpty()
        .substringBefore(';')
        .trim()
        .lowercase(Locale.US)
      val dispositionName = parseContentDispositionFilename(resp.header("Content-Disposition"))
      val rawBytes = resp.body?.byteStream()?.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < MAX_DOWNLOAD_BYTES) {
          val n = input.read(buf, 0, minOf(buf.size, MAX_DOWNLOAD_BYTES - total))
          if (n <= 0) break
          out.write(buf, 0, n)
          total += n
        }
        out.toByteArray()
      } ?: ByteArray(0)

      if (resp.code == 401 || resp.code == 403 ||
        (urlInfo.isGoogleWorkspaceOrDrive && isLikelyGoogleAuthHtml(rawBytes, contentType, finalUrl))
      ) {
        return CloudFetchOutcome.NeedsAccess(
          reason = "This Google Drive/Docs link is private (HTTP ${resp.code}). Set sharing to 'Anyone with the link' or pick it via Pick PDF / Docs.",
          urlInfo = urlInfo,
        )
      }

      if (!resp.isSuccessful) {
        return CloudFetchOutcome.Failure(
          reason = "HTTP ${resp.code} while downloading ${urlInfo.defaultFileName}",
          urlInfo = urlInfo,
        )
      }

      if (rawBytes.isEmpty()) {
        return CloudFetchOutcome.Failure(
          reason = "Document returned empty content (${urlInfo.defaultFileName}).",
          urlInfo = urlInfo,
        )
      }

      val resolvedName = customNameHint.trim().takeIf { it.isNotBlank() }
        ?: dispositionName
        ?: urlInfo.defaultFileName
      val resolvedMime = contentType.ifBlank { urlInfo.defaultMimeType }

      return CloudFetchOutcome.Success(
        bytes = rawBytes,
        resolvedName = resolvedName,
        mimeType = resolvedMime,
        finalUrl = finalUrl,
        urlInfo = urlInfo,
      )
    }
  }
}
