package com.example.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.example.model.FileLinkType
import com.example.model.LinkedFile
import com.example.model.MediaAttachmentPayload
import com.example.model.OpenClawHost
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
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
    .connectTimeout(4, TimeUnit.SECONDS)
    .readTimeout(25, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false)
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

  private suspend fun readDeviceUri(
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

    if (mime.startsWith("image/") || ext in IMAGE_EXTENSIONS) {
      val rawImgBytes = readBytesBounded(uri, maxBytes = 10 * 1024 * 1024)
      if (rawImgBytes != null) {
        val (dataUrl, infoLabel) = encodeImageDataUrl(rawImgBytes, mime, ext)
        // Mirror the PDF/DOCX paths: park the original on the host too, so the model can
        // fetch full resolution if it needs detail the downscaled inline copy loses.
        val uploadedHostRef = tryUploadFileToOpenClaw(
          host,
          file.displayName,
          mime.ifBlank { "image/jpeg" },
          rawImgBytes,
        )
        val extracted = buildString {
          append("Attached image/screenshot ($infoLabel) sent inline as base64 image_url.")
          if (!uploadedHostRef.isNullOrBlank()) append(" Original also uploaded to host as $uploadedHostRef.")
        }
        return MediaAttachmentPayload(
          displayName = file.displayName,
          uriOrPath = file.uriOrPath,
          mimeType = mime.ifBlank { "image/jpeg" },
          linkType = file.linkTypeEnum,
          extractedContent = extracted,
          uploadedHostId = uploadedHostRef.orEmpty(),
          isImage = true,
          imageDataUrl = dataUrl,
        )
      }
    }

    if (mime == "application/pdf" || ext == "pdf") {
      val pdfBytes = readBytesBounded(uri, maxBytes = 12 * 1024 * 1024)
      if (pdfBytes != null) {
        val rawExtracted = extractPdfText(pdfBytes)
        val hasSelectableText = rawExtracted.isNotBlank()
        val renderedPages = if (!hasSelectableText) {
          renderPdfPagesAsDataUrls(pdfBytes, maxPages = 4)
        } else {
          emptyList()
        }
        val finalExtracted = when {
          hasSelectableText -> rawExtracted
          renderedPages.isNotEmpty() ->
            "[Scanned/image PDF: no selectable text stream; rendered ${renderedPages.size} page image(s) for multimodal visual inspection.]"
          else ->
            "[Scanned or image-only PDF: no selectable embedded text stream found. Uploaded/attached raw PDF for host-side OCR.]"
        }
        val uploadedHostRef = tryUploadFileToOpenClaw(host, file.displayName, "application/pdf", pdfBytes)
        val b64 = if (pdfBytes.size <= 3 * 1024 * 1024) Base64.encodeToString(pdfBytes, Base64.NO_WRAP) else ""
        return MediaAttachmentPayload(
          displayName = file.displayName,
          uriOrPath = file.uriOrPath,
          mimeType = "application/pdf",
          linkType = file.linkTypeEnum,
          extractedContent = finalExtracted.take(45_000),
          uploadedHostId = uploadedHostRef.orEmpty(),
          isPdf = true,
          fileBase64 = b64,
          pageImageDataUrls = renderedPages,
        )
      }
    }

    if (mime == DOCX_MIME || ext == "docx") {
      val docxBytes = readBytesBounded(uri, maxBytes = 12 * 1024 * 1024)
      if (docxBytes != null) {
        val extracted = extractDocxText(docxBytes)
        // Render the document to page images so the model sees layout, tables and chops
        // that plain text extraction flattens away.
        val renderedPages = try {
          renderDocxPagesAsDataUrls(docxBytes)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.w(TAG, "DOCX render failed: ${e.message}")
          emptyList()
        }
        val uploadedHostRef = tryUploadFileToOpenClaw(host, file.displayName, DOCX_MIME, docxBytes)
        val b64 = if (docxBytes.size <= 3 * 1024 * 1024) Base64.encodeToString(docxBytes, Base64.NO_WRAP) else ""
        return MediaAttachmentPayload(
          displayName = file.displayName,
          uriOrPath = file.uriOrPath,
          mimeType = DOCX_MIME,
          linkType = file.linkTypeEnum,
          extractedContent = extracted.take(45_000),
          uploadedHostId = uploadedHostRef.orEmpty(),
          isDocx = true,
          fileBase64 = b64,
          pageImageDataUrls = renderedPages,
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

    // Other binary files: upload raw bytes to host if reachable AND keep base64 fallback for small files
    val rawBytes = readBytesBounded(uri, maxBytes = 8 * 1024 * 1024)
    val uploadedHostRef = if (rawBytes != null) {
      tryUploadFileToOpenClaw(
        host = host,
        filename = file.displayName,
        mimeType = mime.ifBlank { "application/octet-stream" },
        bytes = rawBytes,
      )
    } else null
    val fallbackB64 = if (rawBytes != null && rawBytes.size <= 2 * 1024 * 1024) {
      Base64.encodeToString(rawBytes, Base64.NO_WRAP)
    } else ""

    return MediaAttachmentPayload(
      displayName = file.displayName,
      uriOrPath = file.uriOrPath,
      mimeType = mime,
      linkType = file.linkTypeEnum,
      uploadedHostId = uploadedHostRef.orEmpty(),
      extractedContent = if (uploadedHostRef.isNullOrBlank()) {
        "Attached file (${file.displayName}, ${rawBytes?.size ?: 0} bytes)"
      } else {
        "Uploaded to host as $uploadedHostRef"
      },
      fileBase64 = fallbackB64,
    )
  }

  private fun encodeImageDataUrl(rawBytes: ByteArray, mime: String, ext: String): Pair<String, String> {
    return try {
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
      val srcW = opts.outWidth
      val srcH = opts.outHeight
      val maxDim = maxOf(srcW, srcH)

      // inSampleSize must be a power of two to be honoured, and only halves the decode.
      // Once decoding is cheap enough, scale exactly to MAX_IMAGE_DIM so we never ship a
      // 1600px+ JPEG in the request body (the old path could post 800KB of base64 per image).
      var sampleSize = 1
      while (maxDim / sampleSize > MAX_IMAGE_DIM * 2) {
        sampleSize *= 2
      }
      val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
      if (decoded != null) {
        val oriented = applyExifOrientation(decoded, rawBytes)
        if (oriented !== decoded) decoded.recycle()
        val scaled = scaleToMaxDim(oriented, MAX_IMAGE_DIM)
        if (scaled !== oriented) oriented.recycle()
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out)
        val jpegBytes = out.toByteArray()
        val info = "${scaled.width}x${scaled.height}"
        scaled.recycle()
        val b64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$b64" to info
      } else {
        val fallbackMime = mime.ifBlank { if (ext == "png") "image/png" else "image/jpeg" }
        val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        "data:$fallbackMime;base64,$b64" to "${rawBytes.size / 1024}KB"
      }
    } catch (_: Exception) {
      val fallbackMime = mime.ifBlank { if (ext == "png") "image/png" else "image/jpeg" }
      val b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
      "data:$fallbackMime;base64,$b64" to "${rawBytes.size / 1024}KB"
    }
  }

  /**
   * Rotates/mirrors [src] to match its EXIF orientation. Re-encoding to JPEG drops the tag,
   * so without this a portrait phone photo reaches the model sideways.
   */
  private fun applyExifOrientation(src: Bitmap, rawBytes: ByteArray): Bitmap {
    return try {
      val exif = ExifInterface(ByteArrayInputStream(rawBytes))
      val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
      )
      val matrix = Matrix()
      when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
          matrix.postRotate(90f)
          matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
          matrix.postRotate(270f)
          matrix.postScale(-1f, 1f)
        }
        else -> return src
      }
      Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    } catch (_: Exception) {
      src
    }
  }

  /** Scales down so neither side exceeds [maxDim]; returns the input if already small enough. */
  private fun scaleToMaxDim(src: Bitmap, maxDim: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxDim) return src
    val ratio = maxDim.toFloat() / longest.toFloat()
    val w = (src.width * ratio).toInt().coerceAtLeast(1)
    val h = (src.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(src, w, h, true)
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
    val cleanBase = host?.effectiveBaseUrl?.trim()?.trimEnd('/').orEmpty()
    if (cleanBase.isBlank()) return null

    val endpoints = listOf(
      "$cleanBase/v1/files",
      "$cleanBase/files/upload",
      "$cleanBase/api/files/upload",
      "$cleanBase/hooks/upload",
    )
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
            return json?.optString("file_path")?.takeIf { it.isNotBlank() }
              ?: json?.optString("filePath")?.takeIf { it.isNotBlank() }
              ?: json?.optString("path")?.takeIf { it.isNotBlank() }
              ?: json?.optString("id")?.takeIf { it.isNotBlank() }
              ?: json?.optString("url")?.takeIf { it.isNotBlank() }
              ?: filename
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: java.net.ConnectException) {
        break
      } catch (e: java.net.NoRouteToHostException) {
        break
      } catch (e: java.net.UnknownHostException) {
        break
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
      var startOffset = match.groups[1]?.range?.first ?: continue
      var endExclusive = (match.groups[1]?.range?.last ?: continue) + 1
      // The regex can swallow the leading EOL and the EOL before `endstream`; trim both
      // so binary decoders see the exact payload bytes.
      while (startOffset < endExclusive && pdfBytes[startOffset].toInt().toChar() in "\r\n") startOffset++
      while (endExclusive > startOffset && pdfBytes[endExclusive - 1].toInt().toChar() in "\r\n") endExclusive--
      if (endExclusive <= startOffset || endExclusive > pdfBytes.size) continue

      var streamSlice = pdfBytes.copyOfRange(startOffset, endExclusive)
      // /Filter chains run left-to-right; decode ASCII85/ASCIIHex before inflating.
      val filters = Regex("/Filter\\s*(\\[[^\\]]*\\]|/[A-Za-z0-9]+)")
        .find(dictHeader)?.groupValues?.get(1).orEmpty()
      if (filters.contains("ASCII85Decode", ignoreCase = true)) {
        streamSlice = decodeAscii85(streamSlice) ?: continue
      }
      if (filters.contains("ASCIIHexDecode", ignoreCase = true)) {
        streamSlice = decodeAsciiHex(streamSlice) ?: continue
      }
      val decodedStream = if (filters.contains("FlateDecode", ignoreCase = true)) {
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

    return extractedChunks.joinToString("\n\n").trim()
  }

  private fun renderPdfPagesAsDataUrls(pdfBytes: ByteArray, maxPages: Int = 4): List<String> {
    var tempFile: File? = null
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    return try {
      tempFile = File.createTempFile("clawpdf_", ".pdf", context.cacheDir)
      FileOutputStream(tempFile).use { it.write(pdfBytes) }
      pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
      renderer = PdfRenderer(pfd)
      val count = minOf(renderer.pageCount, maxPages)
      buildList {
        for (i in 0 until count) {
          renderer.openPage(i).use { page ->
            val maxDim = 1400
            val scale = minOf(
              2.0f,
              maxDim.toFloat() / maxOf(1, maxOf(page.width, page.height)).toFloat()
            )
            val width = maxOf(1, (page.width * scale).toInt())
            val height = maxOf(1, (page.height * scale).toInt())
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            bitmap.recycle()
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            add("data:image/jpeg;base64,$b64")
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "PdfRenderer fallback failed: ${e.message}")
      emptyList()
    } finally {
      runCatching { renderer?.close() }
      runCatching { pfd?.close() }
      runCatching { tempFile?.delete() }
    }
  }

  /** Decodes a PDF /ASCII85Decode stream (Adobe variant, terminated by `~>`). */
  private fun decodeAscii85(input: ByteArray): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    var tuple = 0
    var count = 0
    for (raw in input) {
      val c = raw.toInt() and 0xFF
      if (c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code) continue
      if (c == '~'.code) break
      if (c == 'z'.code && count == 0) {
        out.write(byteArrayOf(0, 0, 0, 0))
        continue
      }
      val v = c - 33
      if (v < 0 || v > 84) return null
      tuple = tuple * 85 + v
      if (++count == 5) {
        out.write(byteArrayOf(
          (tuple ushr 24).toByte(), (tuple ushr 16).toByte(),
          (tuple ushr 8).toByte(), tuple.toByte(),
        ))
        tuple = 0
        count = 0
      }
    }
    if (count > 0) {
      for (i in count until 5) tuple = tuple * 85 + 84
      val bytes = byteArrayOf(
        (tuple ushr 24).toByte(), (tuple ushr 16).toByte(),
        (tuple ushr 8).toByte(), tuple.toByte(),
      )
      out.write(bytes, 0, count - 1)
    }
    return out.toByteArray()
  }

  /** Decodes a PDF /ASCIIHexDecode stream (terminated by `>`). */
  private fun decodeAsciiHex(input: ByteArray): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    var hi = -1
    for (raw in input) {
      val c = raw.toInt() and 0xFF
      if (c == '>'.code) break
      val v = when (c.toChar()) {
        in '0'..'9' -> c - '0'.code
        in 'a'..'f' -> c - 'a'.code + 10
        in 'A'..'F' -> c - 'A'.code + 10
        else -> continue
      }
      if (hi < 0) {
        hi = v
      } else {
        out.write((hi shl 4) or v)
        hi = -1
      }
    }
    if (hi >= 0) out.write(hi shl 4)
    return out.toByteArray()
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

  /**
   * Pure-Kotlin DOCX text extractor that unzips `word/document.xml` (and headers/footers),
   * preserves paragraph and table-row breaks, and strips OOXML tags cleanly.
   */
  /** Converts DOCX body XML to simple HTML that keeps paragraphs and table structure. */
  private fun docxToHtml(docxBytes: ByteArray): String? {
    var bodyXml: String? = null
    try {
      ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          if (!entry.isDirectory && entry.name.lowercase(Locale.US) == "word/document.xml") {
            bodyXml = readZipEntryText(zis)
            break
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "DOCX html build failed: ${e.message}")
      return null
    }
    val xml = bodyXml ?: return null
    val body = xml.substringAfter("<w:body>", xml).substringBefore("</w:body>")
    val html = StringBuilder()
    html.append("<div style=\"font-family:sans-serif;font-size:14px;line-height:1.4\">")
    // Paragraphs -> <p>, table cells -> <td>, keep bold runs visible.
    var rest = body
    val blockRegex = Regex("<w:(p|tr|tc)\\b[^>]*>(.*?)</w:\\1>", RegexOption.DOT_MATCHES_ALL)
    val builder = StringBuilder()
    var idx = 0
    while (idx < rest.length) {
      val open = rest.indexOf("<w:p", idx)
      val openTr = rest.indexOf("<w:tr", idx)
      val next = listOf(open, openTr).filter { it >= 0 }.minOrNull() ?: break
      idx = next
      val tag = if (rest.startsWith("<w:tr", next)) "tr" else "p"
      val closeTag = "</w:$tag>"
      val end = rest.indexOf(closeTag, next)
      if (end < 0) break
      val chunk = rest.substring(next, end + closeTag.length)
      if (tag == "tr") {
        builder.append("<table style=\"border-collapse:collapse;width:100%\">")
        for (cell in Regex("<w:tc\b[^>]*>(.*?)</w:tc>", RegexOption.DOT_MATCHES_ALL).findAll(chunk)) {
          builder.append("<td style=\"border:1px solid #999;padding:4px\">")
          builder.append(runsToHtml(cell.groupValues[1]))
          builder.append("</td>")
        }
        builder.append("</table>")
      } else {
        builder.append("<p>")
        builder.append(runsToHtml(chunk))
        builder.append("</p>")
      }
      idx = end + closeTag.length
    }
    if (builder.isBlank()) builder.append(runsToHtml(body))
    html.append(builder)
    html.append("</div>")
    return html.toString()
  }

  private fun runsToHtml(fragment: String): String {
    val sb = StringBuilder()
    for (run in Regex("<w:t\b[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL).findAll(fragment)) {
      var t = run.groupValues[1]
      t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#160;", " ")
      sb.append(t.replace("<", "&lt;").replace(">", "&gt;"))
    }
    for (br in Regex("<w:(?:br|cr)\b[^>]*/?>").findAll(fragment)) sb.append("<br>")
    return sb.toString()
  }

  /** Renders a DOCX to JPEG data-URL pages via an offscreen WebView (must run on Main). */
  private suspend fun renderDocxPagesAsDataUrls(docxBytes: ByteArray): List<String> =
    withContext(Dispatchers.Main) {
      val html = docxToHtml(docxBytes) ?: return@withContext emptyList()
      val webView = WebView(context)
      try {
        suspendCancellableCoroutine<Unit> { cont ->
          webView.settings.javaScriptEnabled = false
          webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
              if (cont.isActive) cont.resume(Unit)
            }
          }
          val doc = "<html><body style=\"margin:16px;background:white\">$html</body></html>"
          webView.loadDataWithBaseURL(null, doc, "text/html", "UTF-8", null)
        }
        // Lay out at phone width, then slice the tall capture into page-sized bitmaps.
        val width = 1080
        webView.measure(
          android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
          android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
        )
        val fullHeight = webView.measuredHeight.coerceIn(1, 20000)
        webView.layout(0, 0, width, fullHeight)
        val full = Bitmap.createBitmap(width, fullHeight, Bitmap.Config.ARGB_8888)
        Canvas(full).drawColor(Color.WHITE)
        webView.draw(Canvas(full))
        val pageHeight = 1500
        val pages = buildList {
          var y = 0
          while (y < fullHeight) {
            val h = minOf(pageHeight, fullHeight - y)
            val slice = Bitmap.createBitmap(full, 0, y, width, h)
            val out = ByteArrayOutputStream()
            slice.compress(Bitmap.CompressFormat.JPEG, 82, out)
            slice.recycle()
            add("data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
            y += pageHeight
          }
        }
        full.recycle()
        pages
      } finally {
        webView.destroy()
      }
    }

  private fun extractDocxText(docxBytes: ByteArray): String {
    val parts = mutableListOf<String>()
    try {
      ZipInputStream(ByteArrayInputStream(docxBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          val name = entry.name.lowercase(Locale.US)
          if (!entry.isDirectory &&
            (name == "word/document.xml" || name.startsWith("word/header") || name.startsWith("word/footer")) &&
            name.endsWith(".xml")
          ) {
            val xml = readZipEntryText(zis)
            val parsed = parseWordXmlToText(xml)
            if (parsed.isNotBlank()) {
              if (name == "word/document.xml") {
                parts.add(0, parsed)
              } else {
                parts.add(parsed)
              }
            }
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "DOCX extraction fallback failed: ${e.message}")
    }
    val combined = parts.joinToString("\n\n").trim()
    return combined.ifBlank {
      "[DOCX contains no extractable text in word/document.xml. Check Host Upload Reference if host upload succeeded.]"
    }
  }

  private fun readZipEntryText(zis: ZipInputStream): String {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    var total = 0
    val max = 2 * 1024 * 1024
    while (total < max) {
      val n = zis.read(buf, 0, minOf(buf.size, max - total))
      if (n <= 0) break
      out.write(buf, 0, n)
      total += n
    }
    return String(out.toByteArray(), StandardCharsets.UTF_8)
  }

  private fun parseWordXmlToText(xml: String): String {
    return xml
      .replace(Regex("<w:tab\\s*/?>"), "\t")
      .replace(Regex("<w:(?:br|cr)\\s*[^>]*/>"), "\n")
      .replace(Regex("</w:tc>"), " | ")
      .replace(Regex("</w:(?:p|tr)>"), "\n")
      .replace(Regex("<[^>]+>"), "")
      .replace("&amp;", "&")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&#160;", " ")
      .replace(Regex(" \\|\\s*\\n"), "\n")
      .replace(Regex("[ \\t]+\\n"), "\n")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  companion object {
    private const val TAG = "DeviceAttachmentReader"
    private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val MAX_IMAGE_DIM = 1280
    private const val IMAGE_JPEG_QUALITY = 70

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "heic", "bmp")
    private val CODE_AND_TEXT_EXTENSIONS = setOf(
      "txt", "md", "markdown", "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx",
      "json", "xml", "yaml", "yml", "csv", "tsv", "log", "html", "htm", "css", "scss",
      "sh", "bash", "zsh", "ps1", "bat", "gradle", "toml", "ini", "cfg", "conf",
      "properties", "env", "c", "cc", "cpp", "h", "hpp", "rs", "go", "rb", "php",
      "swift", "sql", "r", "lua", "dart"
    )
  }
}
