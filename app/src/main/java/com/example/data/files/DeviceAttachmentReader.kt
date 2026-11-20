package com.example.data.files

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.FileLinkType
import com.example.model.LinkedFile
import com.example.model.MediaAttachmentPayload
import com.example.model.OpenClawHost
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DeviceAttachmentReader(private val context: Context) {

  private val uploadClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .writeTimeout(25, TimeUnit.SECONDS)
    .build()

  suspend fun preparePayloads(
    attachments: List<LinkedFile>,
    host: OpenClawHost?,
    promptHint: String = "",
  ): List<MediaAttachmentPayload> = withContext(Dispatchers.IO) {
    attachments.map { file ->
      if (file.linkTypeEnum != FileLinkType.ANDROID_ATTACHMENT) {
        MediaAttachmentPayload(
          displayName = file.displayName,
          uriOrPath = file.uriOrPath,
          mimeType = file.mimeOrExt,
          linkType = file.linkTypeEnum,
          extractedContent = file.notes,
        )
      } else {
        readDeviceUri(file, host, promptHint)
      }
    }
  }

  private fun readDeviceUri(
    file: LinkedFile,
    host: OpenClawHost?,
    @Suppress("UNUSED_PARAMETER") promptHint: String,
  ): MediaAttachmentPayload {
    val uri = runCatching { Uri.parse(file.uriOrPath) }.getOrNull()
      ?: return MediaAttachmentPayload(
        displayName = file.displayName,
        uriOrPath = file.uriOrPath,
        mimeType = file.mimeOrExt,
        linkType = file.linkTypeEnum,
      )

    val mime = (file.mimeOrExt.ifBlank { context.contentResolver.getType(uri).orEmpty() })
      .lowercase(Locale.US)
    val ext = file.displayName.substringAfterLast('.', "").lowercase(Locale.US)

    if (mime == "application/pdf" || ext == "pdf") {
      val pdfBytes = readBytesBounded(uri, maxBytes = 10 * 1024 * 1024)
      if (pdfBytes != null) {
        val extracted = extractPdfText(pdfBytes)
        val uploadedHostRef = tryUploadFileToOpenClaw(host, file.displayName, "application/pdf", pdfBytes)
        return MediaAttachmentPayload(
          displayName = file.displayName,
          uriOrPath = file.uriOrPath,
          mimeType = "application/pdf",
          linkType = file.linkTypeEnum,
          extractedContent = extracted.take(45_000),
          uploadedHostId = uploadedHostRef.orEmpty(),
          isPdf = true,
        )
      }
    }

    if (isPlaintextOrCode(mime, ext)) {
      val bytes = readBytesBounded(uri, maxBytes = 512 * 1024)
      val text = bytes?.toString(StandardCharsets.UTF_8).orEmpty()
        .replace("\u0000", "")
        .take(45_000)
      return MediaAttachmentPayload(
        displayName = file.displayName,
        uriOrPath = file.uriOrPath,
        mimeType = mime.ifBlank { "text/plain" },
        linkType = file.linkTypeEnum,
        extractedContent = text,
        isPdf = false,
      )
    }

    // Other binary files: upload raw bytes to host if reachable
    val rawBytes = readBytesBounded(uri, maxBytes = 8 * 1024 * 1024)
    val uploadedHostRef = if (rawBytes != null) {
      tryUploadFileToOpenClaw(
        host = host,
        filename = file.displayName,
        mimeType = mime.ifBlank { "application/octet-stream" },
        bytes = rawBytes,
      )
    } else null

    return MediaAttachmentPayload(
      displayName = file.displayName,
      uriOrPath = file.uriOrPath,
      mimeType = mime,
      linkType = file.linkTypeEnum,
      uploadedHostId = uploadedHostRef.orEmpty(),
      extractedContent = if (uploadedHostRef.isNullOrBlank()) "" else "Uploaded to host as $uploadedHostRef",
    )
  }

  private fun readBytesBounded(uri: Uri, maxBytes: Int): ByteArray? {
    return try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (total < maxBytes) {
          val read = input.read(buf, 0, minOf(buf.size, maxBytes - total))
          if (read <= 0) break
          out.write(buf, 0, read)
          total += read
        }
        out.toByteArray()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read attachment bytes: ${e.message}")
      null
    }
  }

  private fun tryUploadFileToOpenClaw(
    host: OpenClawHost?,
    filename: String,
    mimeType: String,
    bytes: ByteArray,
  ): String? {
    val cleanBase = host?.baseUrl?.trim()?.trimEnd('/').orEmpty()
    if (cleanBase.isBlank()) return null

    val endpoints = listOf("$cleanBase/v1/files", "$cleanBase/files/upload")
    val body = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("purpose", "assistants")
      .addFormDataPart(
        "file",
        filename,
        bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
      )
      .build()

    for (url in endpoints) {
      try {
        val req = Request.Builder()
          .url(url)
          .post(body)
          .apply {
            if (host?.gatewayToken?.isNotBlank() == true) {
              addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
            }
          }
          .build()

        uploadClient.newCall(req).execute().use { resp ->
          if (resp.isSuccessful) {
            val raw = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            return json?.optString("id")?.takeIf { it.isNotBlank() }
              ?: json?.optString("path")?.takeIf { it.isNotBlank() }
              ?: json?.optString("url")?.takeIf { it.isNotBlank() }
              ?: filename
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Fallback to inline extracted text
      }
    }
    return null
  }

  private fun isPlaintextOrCode(mime: String, ext: String): Boolean {
    if (mime.startsWith("text/")) return true
    if (mime in setOf(
        "application/json",
        "application/xml",
        "application/javascript",
        "application/x-javascript",
        "application/typescript",
        "application/x-yaml",
        "application/yaml",
      )
    ) return true
    return ext in CODE_AND_TEXT_EXTENSIONS
  }

  /**
   * Pure-Kotlin PDF text extractor supporting uncompressed content streams and FlateDecode streams.
   */
  private fun extractPdfText(pdfBytes: ByteArray): String {
    val rawLatin = String(pdfBytes, StandardCharsets.ISO_8859_1)
    val extractedChunks = mutableListOf<String>()

    // 1. Inspect stream ... endstream blocks
    val streamRegex = Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\n?endstream")
    for (match in streamRegex.findAll(rawLatin)) {
      val headerStart = maxOf(0, match.range.first - 260)
      val dictHeader = rawLatin.substring(headerStart, match.range.first)
      val startOffset = match.groups[1]?.range?.first ?: continue
      val endExclusive = (match.groups[1]?.range?.last ?: continue) + 1
      if (endExclusive <= startOffset || endExclusive > pdfBytes.size) continue

      val streamSlice = pdfBytes.copyOfRange(startOffset, endExclusive)
      val decodedStream = if (dictHeader.contains("FlateDecode", ignoreCase = true)) {
        inflateFlateStream(streamSlice) ?: continue
      } else {
        String(streamSlice, StandardCharsets.ISO_8859_1)
      }

      val opsText = extractOperatorsFromPdfContent(decodedStream)
      if (opsText.isNotBlank()) {
        extractedChunks.add(opsText)
      }
    }

    // 2. Fallback scan outside detected stream blocks if PDF had raw BT..ET blocks
    if (extractedChunks.isEmpty()) {
      val fallback = extractOperatorsFromPdfContent(rawLatin)
      if (fallback.isNotBlank()) extractedChunks.add(fallback)
    }

    val combined = extractedChunks.joinToString("\n\n").trim()
    return if (combined.isNotBlank()) {
      combined
    } else {
      "[Scanned or image-only PDF: no selectable embedded text stream found. Uploaded/attached raw PDF for host-side OCR.]"
    }
  }

  private fun inflateFlateStream(compressed: ByteArray): String? {
    return try {
      val inflater = Inflater()
      inflater.setInput(compressed)
      val out = ByteArrayOutputStream()
      val buf = ByteArray(4096)
      var totalOut = 0
      while (!inflater.finished() && totalOut < 2 * 1024 * 1024) {
        val count = inflater.inflate(buf)
        if (count == 0) {
          if (inflater.needsInput() || inflater.needsDictionary()) break
        } else {
          out.write(buf, 0, count)
          totalOut += count
        }
      }
      inflater.end()
      String(out.toByteArray(), StandardCharsets.ISO_8859_1)
    } catch (_: Exception) {
      null
    }
  }

  private fun extractOperatorsFromPdfContent(content: String): String {
    val sb = StringBuilder()
    var i = 0
    val len = content.length
    var inParens = false
    var parenDepth = 0
    var escapeNext = false
    val currentStr = StringBuilder()

    while (i < len) {
      val c = content[i]
      if (inParens) {
        if (escapeNext) {
          when (c) {
            'n' -> currentStr.append('\n')
            'r' -> currentStr.append('\r')
            't' -> currentStr.append('\t')
            '\\' -> currentStr.append('\\')
            '(' -> currentStr.append('(')
            ')' -> currentStr.append(')')
            else -> currentStr.append(c)
          }
          escapeNext = false
        } else {
          when (c) {
            '\\' -> escapeNext = true
            '(' -> {
              parenDepth++
              currentStr.append(c)
            }
            ')' -> {
              if (parenDepth > 0) {
                parenDepth--
                currentStr.append(c)
              } else {
                inParens = false
                val cleaned = currentStr.toString()
                if (cleaned.any { !it.isISOControl() }) {
                  sb.append(cleaned)
                }
                currentStr.setLength(0)
              }
            }
            else -> currentStr.append(c)
          }
        }
      } else {
        when (c) {
          '(' -> {
            inParens = true
            parenDepth = 0
            escapeNext = false
            currentStr.setLength(0)
          }
          '\n', '\r' -> {
            if (sb.isNotEmpty() && sb.last() != '\n' && sb.last() != ' ') {
              sb.append(' ')
            }
          }
        }
      }
      i++
    }
    return sb.toString()
      .replace(Regex("[ \\t]{2,}"), " ")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  companion object {
    private const val TAG = "DeviceAttachmentReader"
    private val CODE_AND_TEXT_EXTENSIONS = setOf(
      "txt", "md", "markdown", "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx",
      "json", "xml", "yaml", "yml", "csv", "tsv", "log", "html", "htm", "css", "scss",
      "sh", "bash", "zsh", "ps1", "bat", "gradle", "toml", "ini", "cfg", "conf",
      "properties", "env", "c", "cc", "cpp", "h", "hpp", "rs", "go", "rb", "php",
      "swift", "sql", "r", "lua", "dart"
    )
  }
}
