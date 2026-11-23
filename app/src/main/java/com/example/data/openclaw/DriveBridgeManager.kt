package com.example.data.openclaw

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.files.MediaSaveHelper
import com.example.model.BotExpression
import com.example.model.GeneratedMediaType
import com.example.model.OpenClawHost
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BridgeFolderStatus(
  val valid: Boolean,
  val summary: String,
  val instructionsCount: Int = 0,
  val processedCount: Int = 0,
  val outboxCount: Int = 0,
)

data class BridgeOutboxItem(
  val fileName: String,
  val folder: String,
  val textSummary: String,
  val cachedMediaUri: String? = null,
)

/**
 * Implements the Google Drive / folder-based APK-Bridge protocol (`My Drive/OpenClaw/APK-Bridge/`):
 * - Writes task JSONs (`model: "openclaw/apk"`, `user: <sessionKey>`, `messages`, etc.) to `instructions/`
 * - Watches `processed/` and `outbox/` for task result JSONs and outbound media files.
 *
 * Supports both Android SAF tree URIs (`content://...`) and filesystem directories (`File`).
 */
class DriveBridgeManager(
  private val context: Context? = null,
  private val appFilesDir: File? = context?.filesDir,
  private val pollIntervalMs: Long = 2_000L,
  private val timeoutMs: Long = 90_000L,
) {

  suspend fun inspectBridgeFolders(folderUriOrPath: String): BridgeFolderStatus = withContext(Dispatchers.IO) {
    val clean = folderUriOrPath.trim()
    if (clean.isBlank()) {
      return@withContext BridgeFolderStatus(
        valid = false,
        summary = "Select or enter your OpenClaw APK-Bridge folder first.",
      )
    }
    try {
      val backend = openBackend(clean)
      backend.ensureBridgeSubdirs()
      val instr = backend.listFiles("instructions").size
      val proc = backend.listFiles("processed").size
      val outbox = backend.listFiles("outbox").size
      BridgeFolderStatus(
        valid = true,
        summary = "Bridge ready • instructions: $instr • processed: $proc • outbox: $outbox",
        instructionsCount = instr,
        processedCount = proc,
        outboxCount = outbox,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Bridge inspection failed: ${e.message}", e)
      BridgeFolderStatus(
        valid = false,
        summary = "Could not access bridge folder: ${e.localizedMessage ?: "Permission denied"}",
      )
    }
  }

  suspend fun listProcessedAndOutbox(folderUriOrPath: String): List<BridgeOutboxItem> = withContext(Dispatchers.IO) {
    val clean = folderUriOrPath.trim()
    if (clean.isBlank()) return@withContext emptyList()
    return@withContext try {
      val backend = openBackend(clean)
      backend.ensureBridgeSubdirs()
      val items = mutableListOf<BridgeOutboxItem>()
      listOf("outbox", "processed").forEach { sub ->
        backend.listFiles(sub).forEach { entry ->
          val lower = entry.name.lowercase(Locale.US)
          if (lower.endsWith(".json")) {
            val raw = backend.readText(sub, entry).orEmpty()
            val parsedText = extractTextFromBridgePayload(raw)
            items += BridgeOutboxItem(
              fileName = entry.name,
              folder = sub,
              textSummary = parsedText.take(180),
            )
          } else {
            val cached = copyBridgeMediaToCache(backend, sub, entry)
            items += BridgeOutboxItem(
              fileName = entry.name,
              folder = sub,
              textSummary = "Outbox artifact (${entry.name})",
              cachedMediaUri = cached,
            )
          }
        }
      }
      items
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      emptyList()
    }
  }

  suspend fun dispatchTaskAndAwaitResult(
    folderUriOrPath: String,
    host: OpenClawHost?,
    wireModel: String,
    backendModel: String?,
    sessionKey: String,
    agentId: String,
    deviceId: String,
    userPrompt: String,
    messagesArray: JSONArray,
    refinedInstruction: String? = null,
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val clean = folderUriOrPath.trim()
    if (clean.isBlank()) {
      return@withContext Result.failure(
        OpenClawFormattedException("Drive Bridge is enabled, but no APK-Bridge folder is selected.")
      )
    }

    try {
      val backend = openBackend(clean)
      backend.ensureBridgeSubdirs()

      val taskId = "apk_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
      val taskFileName = "$taskId.json"
      val taskJson = JSONObject().apply {
        put("id", taskId)
        put("taskId", taskId)
        put("createdAtMs", System.currentTimeMillis())
        put("model", wireModel)
        if (!backendModel.isNullOrBlank()) {
          put("xOpenClawModel", backendModel)
        }
        put("user", sessionKey)
        put("sessionKey", sessionKey)
        put("agentId", agentId)
        put("deviceId", deviceId)
        put("stream", false)
        put("prompt", userPrompt)
        put("messages", messagesArray)
      }

      backend.writeText("instructions", taskFileName, "application/json", taskJson.toString(2))
      onPartialText("Queued task in Drive Bridge instructions/$taskFileName — watching processed/ & outbox/...")

      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() <= deadline) {
        val outboxResult = findResultInFolder(backend, "outbox", taskId)
        val processedResult = outboxResult ?: findResultInFolder(backend, "processed", taskId)
        if (processedResult != null) {
          val (folderName, entry, rawText) = processedResult
          val parsed = parseBridgeResultPayload(
            rawPayload = rawText,
            backend = backend,
            sourceFolder = folderName,
            taskId = taskId,
            host = host,
            sessionKey = sessionKey,
            agentId = agentId,
            deviceId = deviceId,
            refinedInstruction = refinedInstruction,
            fallbackModel = backendModel ?: wireModel,
            fallbackPrompt = userPrompt,
          )
          return@withContext Result.success(parsed)
        }

        // Check if an outbox media artifact was dropped directly for this taskId (e.g. <taskId>_out.png)
        val directOutboxMedia = backend.listFiles("outbox").firstOrNull { entry ->
          val lower = entry.name.lowercase(Locale.US)
          entry.name.startsWith(taskId) && !lower.endsWith(".json")
        }
        if (directOutboxMedia != null) {
          val cachedUri = copyBridgeMediaToCache(backend, "outbox", directOutboxMedia)
          if (cachedUri != null) {
            return@withContext Result.success(
              OpenClawDispatchResult(
                replyText = "Received result from Drive Bridge outbox/${directOutboxMedia.name}.",
                parsedInstruction = refinedInstruction,
                expression = BotExpression.UH_HUH,
                takeoverSuggested = false,
                mediaUrl = cachedUri,
                mediaType = inferMediaType(directOutboxMedia.name),
                mediaPrompt = userPrompt.take(1000),
                mediaModel = backendModel ?: wireModel,
              )
            )
          }
        }

        delay(pollIntervalMs)
      }

      Result.failure(
        OpenClawFormattedException(
          "Drive Bridge timed out waiting in processed/ or outbox/ for $taskFileName after ${timeoutMs / 1000}s.",
          isFastConnectFailure = false,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Drive Bridge task failed: ${e.message}", e)
      Result.failure(
        OpenClawFormattedException(
          "Drive Bridge error: ${e.localizedMessage ?: e.javaClass.simpleName}",
          isFastConnectFailure = false,
        )
      )
    }
  }

  private fun findResultInFolder(
    backend: BridgeBackend,
    folder: String,
    taskId: String,
  ): Triple<String, BridgeEntry, String>? {
    val entries = backend.listFiles(folder)
    val exactNames = setOf(
      "$taskId.json",
      "$taskId.result.json",
      "${taskId}_result.json",
      "$taskId.txt",
    )
    entries.firstOrNull { it.name in exactNames }?.let { match ->
      val body = backend.readText(folder, match)
      if (!body.isNullOrBlank()) {
        return Triple(folder, match, body)
      }
    }
    for (entry in entries) {
      if (!entry.name.endsWith(".json", ignoreCase = true)) continue
      val body = backend.readText(folder, entry) ?: continue
      val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
      val foundId = json.optString("taskId").ifBlank { json.optString("id") }
      if (foundId == taskId) {
        return Triple(folder, entry, body)
      }
    }
    return null
  }

  private fun parseBridgeResultPayload(
    rawPayload: String,
    backend: BridgeBackend,
    sourceFolder: String,
    taskId: String,
    host: OpenClawHost?,
    sessionKey: String,
    agentId: String,
    deviceId: String,
    refinedInstruction: String?,
    fallbackModel: String,
    fallbackPrompt: String,
  ): OpenClawDispatchResult {
    val json = runCatching { JSONObject(rawPayload) }.getOrNull()
    if (json == null) {
      val cleaned = rawPayload.trim()
      return OpenClawDispatchResult(
        replyText = cleaned.ifBlank { "No response from Drive Bridge ($sourceFolder)." },
        parsedInstruction = refinedInstruction,
        expression = BotExpression.UH_HUH,
        takeoverSuggested = false,
      )
    }

    val errorObj = json.optJSONObject("error")
    val errorText = errorObj?.optString("message")?.takeIf { it.isNotBlank() }
      ?: json.optString("error").takeIf { it.isNotBlank() && !it.startsWith("{") }
    if (!errorText.isNullOrBlank()) {
      return OpenClawDispatchResult(
        replyText = errorText,
        parsedInstruction = refinedInstruction,
        expression = BotExpression.WORRIED,
        takeoverSuggested = false,
      )
    }

    val choicesText = json.optJSONArray("choices")
      ?.optJSONObject(0)
      ?.optJSONObject("message")
      ?.optString("content")
      ?.takeIf { it.isNotBlank() }

    val rawText = choicesText
      ?: json.optString("reply").takeIf { it.isNotBlank() }
      ?: json.optString("content").takeIf { it.isNotBlank() }
      ?: json.optString("output").takeIf { it.isNotBlank() }
      ?: json.optString("text").takeIf { it.isNotBlank() }
      ?: ""

    val rawMediaCandidate = listOfNotNull(
      json.optString("mediaUrl").takeIf { it.isNotBlank() },
      json.optString("media_url").takeIf { it.isNotBlank() },
      json.optString("mediaPath").takeIf { it.isNotBlank() },
      json.optString("media_path").takeIf { it.isNotBlank() },
      json.optString("outboxFile").takeIf { it.isNotBlank() },
      extractMediaDirective(rawText),
    ).firstOrNull()

    var resolvedMediaUrl: String? = null
    if (!rawMediaCandidate.isNullOrBlank()) {
      val candidateName = rawMediaCandidate.substringAfterLast('/')
      val outboxMatch = backend.listFiles("outbox").firstOrNull {
        it.name == rawMediaCandidate || it.name == candidateName
      }
      resolvedMediaUrl = if (outboxMatch != null) {
        copyBridgeMediaToCache(backend, "outbox", outboxMatch)
      } else if (rawMediaCandidate.startsWith("data:image/", ignoreCase = true)) {
        MediaSaveHelper.cacheBase64ImageToFile(appFilesDir, rawMediaCandidate)
      } else {
        MediaSaveHelper.tryDownloadHostMediaToLocalFile(
          filesDir = appFilesDir,
          rawPathOrUrl = rawMediaCandidate,
          host = host,
          sessionKey = sessionKey,
          agentId = agentId,
          deviceId = deviceId,
        ) ?: MediaSaveHelper.resolveDownloadUrl(rawMediaCandidate, host, sessionKey, agentId)
      }
    }

    val strippedText = stripMediaDirectives(rawText).ifBlank {
      if (resolvedMediaUrl != null) "Attached media from Drive Bridge ($sourceFolder)."
      else "No response from OpenClaw."
    }

    return OpenClawDispatchResult(
      replyText = strippedText,
      parsedInstruction = refinedInstruction,
      expression = BotExpression.UH_HUH,
      takeoverSuggested = false,
      mediaUrl = resolvedMediaUrl,
      mediaType = resolvedMediaUrl?.let { inferMediaType(it) },
      mediaPrompt = fallbackPrompt.take(1000).takeIf { resolvedMediaUrl != null && it.isNotBlank() },
      mediaModel = fallbackModel.takeIf { resolvedMediaUrl != null && it.isNotBlank() },
    )
  }

  private fun extractTextFromBridgePayload(raw: String): String {
    val json = runCatching { JSONObject(raw) }.getOrNull() ?: return raw.trim()
    return json.optString("reply")
      .ifBlank { json.optString("content") }
      .ifBlank { json.optString("output") }
      .ifBlank { json.optString("prompt") }
      .ifBlank { raw.trim() }
  }

  private fun extractMediaDirective(text: String): String? {
    val mediaMatch = Regex("""(?mi)(?:^|\s)MEDIA:\s*([^\s"'`\r\n]+)""").find(text)
    if (mediaMatch != null) return mediaMatch.groupValues[1].trim()
    val pathMatch = Regex("""(?mi)(?:^|\s)PATH=\s*([^\s"'`\r\n]+)""").find(text)
    return pathMatch?.groupValues?.getOrNull(1)?.trim()
  }

  private fun stripMediaDirectives(text: String): String {
    return text
      .replace(Regex("""(?mi)(?:^|(?<=[ \t]))MEDIA:\s*[^\r\n]+\r?\n?"""), "")
      .replace(Regex("""(?mi)(?:^|(?<=[ \t]))PATH=\s*[^\r\n]+\r?\n?"""), "")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  private fun inferMediaType(pathOrUrl: String): GeneratedMediaType {
    val clean = pathOrUrl.substringBefore('?').lowercase(Locale.US)
    return when {
      clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov") -> GeneratedMediaType.VIDEO
      clean.endsWith(".mp3") || clean.endsWith(".wav") || clean.endsWith(".m4a") -> GeneratedMediaType.AUDIO
      clean.endsWith(".pdf") || clean.endsWith(".docx") || clean.endsWith(".doc") -> GeneratedMediaType.DOCUMENT
      else -> GeneratedMediaType.IMAGE
    }
  }

  private fun copyBridgeMediaToCache(
    backend: BridgeBackend,
    folder: String,
    entry: BridgeEntry,
  ): String? {
    val dir = appFilesDir?.let { File(it, "received_media").apply { mkdirs() } } ?: return null
    val bytes = backend.readBytes(folder, entry) ?: return null
    if (bytes.isEmpty()) return null
    val cleanName = entry.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val target = File(dir, "bridge_${UUID.randomUUID().toString().take(8)}_$cleanName")
    FileOutputStream(target).use { it.write(bytes) }
    return "file://${target.absolutePath}"
  }

  private fun openBackend(folderUriOrPath: String): BridgeBackend {
    return if (folderUriOrPath.startsWith("content://", ignoreCase = true)) {
      val ctx = requireNotNull(context) { "Android Context required for SAF content:// Drive Bridge URI." }
      SafBridgeBackend(ctx, Uri.parse(folderUriOrPath))
    } else {
      val cleanPath = folderUriOrPath.removePrefix("file://")
      FileBridgeBackend(File(cleanPath))
    }
  }

  private data class BridgeEntry(
    val name: String,
    val handle: Any,
  )

  private interface BridgeBackend {
    fun ensureBridgeSubdirs()
    fun listFiles(subdir: String): List<BridgeEntry>
    fun writeText(subdir: String, fileName: String, mimeType: String, content: String)
    fun readBytes(subdir: String, entry: BridgeEntry): ByteArray?
    fun readText(subdir: String, entry: BridgeEntry): String? =
      readBytes(subdir, entry)?.toString(Charsets.UTF_8)
  }

  private class FileBridgeBackend(private val rootDir: File) : BridgeBackend {
    override fun ensureBridgeSubdirs() {
      if (!rootDir.exists()) rootDir.mkdirs()
      listOf("instructions", "processed", "outbox").forEach { name ->
        val dir = File(rootDir, name)
        if (!dir.exists()) dir.mkdirs()
      }
    }

    override fun listFiles(subdir: String): List<BridgeEntry> {
      val dir = File(rootDir, subdir)
      if (!dir.exists() || !dir.isDirectory) return emptyList()
      return dir.listFiles()
        ?.filter { it.isFile }
        ?.map { BridgeEntry(name = it.name, handle = it) }
        .orEmpty()
    }

    override fun writeText(subdir: String, fileName: String, mimeType: String, content: String) {
      ensureBridgeSubdirs()
      val dir = File(rootDir, subdir)
      val tmp = File(dir, "$fileName.tmp")
      val target = File(dir, fileName)
      tmp.writeText(content, Charsets.UTF_8)
      if (!tmp.renameTo(target)) {
        target.writeText(content, Charsets.UTF_8)
        tmp.delete()
      }
    }

    override fun readBytes(subdir: String, entry: BridgeEntry): ByteArray? {
      val file = entry.handle as? File ?: return null
      return if (file.exists() && file.isFile) file.readBytes() else null
    }
  }

  private class SafBridgeBackend(
    private val context: Context,
    private val treeUri: Uri,
  ) : BridgeBackend {
    private val resolver = context.contentResolver
    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    override fun ensureBridgeSubdirs() {
      listOf("instructions", "processed", "outbox").forEach { getOrCreateSubdirDocId(it) }
    }

    override fun listFiles(subdir: String): List<BridgeEntry> {
      val dirDocId = findChildDocId(rootDocId, subdir, wantDir = true) ?: return emptyList()
      return queryChildren(dirDocId)
        .filter { !it.isDirectory }
        .map { BridgeEntry(name = it.name, handle = it.documentId) }
    }

    override fun writeText(subdir: String, fileName: String, mimeType: String, content: String) {
      val dirDocId = getOrCreateSubdirDocId(subdir)
      val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, dirDocId)
      val createdUri = DocumentsContract.createDocument(resolver, dirUri, mimeType, fileName)
        ?: throw IllegalStateException("Could not create $subdir/$fileName in Drive Bridge folder.")
      resolver.openOutputStream(createdUri)?.use { out ->
        out.write(content.toByteArray(Charsets.UTF_8))
      } ?: throw IllegalStateException("Could not open output stream for $subdir/$fileName.")
    }

    override fun readBytes(subdir: String, entry: BridgeEntry): ByteArray? {
      val docId = entry.handle as? String ?: return null
      val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
      return resolver.openInputStream(docUri)?.use { it.readBytes() }
    }

    private fun getOrCreateSubdirDocId(name: String): String {
      findChildDocId(rootDocId, name, wantDir = true)?.let { return it }
      val rootUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
      val created = DocumentsContract.createDocument(
        resolver,
        rootUri,
        DocumentsContract.Document.MIME_TYPE_DIR,
        name,
      ) ?: throw IllegalStateException("Failed creating '$name/' inside selected Bridge folder.")
      return DocumentsContract.getDocumentId(created)
    }

    private fun findChildDocId(parentDocId: String, name: String, wantDir: Boolean): String? {
      return queryChildren(parentDocId)
        .firstOrNull { it.name.equals(name, ignoreCase = true) && it.isDirectory == wantDir }
        ?.documentId
    }

    private data class SafChild(val documentId: String, val name: String, val isDirectory: Boolean)

    private fun queryChildren(parentDocId: String): List<SafChild> {
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
      val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
      )
      return buildList {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
          while (cursor.moveToNext()) {
            val id = if (idCol >= 0) cursor.getString(idCol).orEmpty() else ""
            val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
            if (id.isNotBlank() && name.isNotBlank()) {
              add(
                SafChild(
                  documentId = id,
                  name = name,
                  isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                )
              )
            }
          }
        }
      }
    }
  }

  companion object {
    private const val TAG = "DriveBridgeManager"
  }
}
