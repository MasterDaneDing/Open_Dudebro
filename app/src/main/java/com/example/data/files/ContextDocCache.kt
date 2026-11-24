package com.example.data.files

import com.example.model.CaptainContextDocument
import com.example.model.ContextDocStatus
import com.example.model.FileLinkType
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class ContextDocCache(private val appFilesDir: File?) {

  private fun cacheDir(): File? {
    val root = appFilesDir ?: return null
    return File(root, "captain_context_cache").apply { mkdirs() }
  }

  private fun keyFile(userId: String, uriOrPath: String): File? {
    val dir = cacheDir() ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
      .digest("$userId|$uriOrPath".toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
      .take(32)
    return File(dir, "$digest.json")
  }

  fun save(userId: String, doc: CaptainContextDocument) {
    val file = keyFile(userId, doc.uriOrPath) ?: return
    runCatching {
      val json = JSONObject().apply {
        put("id", doc.id)
        put("displayName", doc.displayName)
        put("uriOrPath", doc.uriOrPath)
        put("linkType", doc.linkType.name)
        put("mimeType", doc.mimeType)
        put("extractedText", doc.extractedText.take(45_000))
        put("status", doc.status.name)
        put("statusDetail", doc.statusDetail)
        put("uploadedHostId", doc.uploadedHostId)
        put("isGoogleDriveUrl", doc.isGoogleDriveUrl)
        put("driveExportUrl", doc.driveExportUrl)
        put("isPdf", doc.isPdf)
        put("isDocx", doc.isDocx)
        put("isImage", doc.isImage)
        // Skip very huge base64 blobs on disk if they'd make cache JSON enormous; keep up to 400KB
        put("imageDataUrl", if (doc.imageDataUrl.length <= 450_000) doc.imageDataUrl else "")
        put(
          "pageImageDataUrls",
          JSONArray().apply {
            doc.pageImageDataUrls.take(4).forEach { if (it.length <= 450_000) put(it) }
          },
        )
        put("charCount", doc.charCount)
        put("updatedEpochMs", doc.updatedEpochMs)
      }
      file.writeText(json.toString(), Charsets.UTF_8)
    }
  }

  fun load(userId: String, uriOrPath: String): CaptainContextDocument? {
    val file = keyFile(userId, uriOrPath) ?: return null
    if (!file.exists()) return null
    return runCatching {
      val json = JSONObject(file.readText(Charsets.UTF_8))
      val pagesArr = json.optJSONArray("pageImageDataUrls")
      val pages = buildList {
        if (pagesArr != null) {
          for (i in 0 until pagesArr.length()) {
            val item = pagesArr.optString(i)
            if (item.isNotBlank()) add(item)
          }
        }
      }
      val status = runCatching {
        ContextDocStatus.valueOf(json.optString("status", ContextDocStatus.READY.name))
      }.getOrDefault(ContextDocStatus.READY)
      CaptainContextDocument(
        id = json.optString("id").ifBlank { uriOrPath.hashCode().toString() },
        displayName = json.optString("displayName", "Document"),
        uriOrPath = json.optString("uriOrPath", uriOrPath),
        linkType = FileLinkType.safeValueOf(json.optString("linkType", FileLinkType.CLOUD_URL.name)),
        mimeType = json.optString("mimeType", ""),
        extractedText = json.optString("extractedText", ""),
        status = status,
        statusDetail = json.optString("statusDetail", ""),
        uploadedHostId = json.optString("uploadedHostId", ""),
        isGoogleDriveUrl = json.optBoolean("isGoogleDriveUrl", false),
        driveExportUrl = json.optString("driveExportUrl", ""),
        isPdf = json.optBoolean("isPdf", false),
        isDocx = json.optBoolean("isDocx", false),
        isImage = json.optBoolean("isImage", false),
        imageDataUrl = json.optString("imageDataUrl", ""),
        pageImageDataUrls = pages,
        charCount = json.optInt("charCount", json.optString("extractedText", "").length),
        updatedEpochMs = json.optLong("updatedEpochMs", file.lastModified()),
      )
    }.getOrNull()
  }

  fun remove(userId: String, uriOrPath: String) {
    keyFile(userId, uriOrPath)?.let { runCatching { it.delete() } }
  }
}
