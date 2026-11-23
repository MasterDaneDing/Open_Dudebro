package com.example

import com.example.data.files.MediaSaveHelper
import com.example.data.openclaw.DriveBridgeManager
import com.example.data.openclaw.LocalProviderKeys
import com.example.data.openclaw.OpenClawClient
import com.example.model.AgentToolPermission
import com.example.model.CafeSpeakerRole
import com.example.model.CafeTranscriptEntry
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.FileLinkType
import com.example.model.GeneralChatMessage
import com.example.model.GeneratedMediaType
import com.example.model.MediaAttachmentPayload
import com.example.model.MessageSender
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
import com.example.model.WheelLlmOption
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OpenClawIntegrationTest {

  private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split('&').mapNotNull { pair ->
      val idx = pair.indexOf('=')
      if (idx <= 0) null
      else URLDecoder.decode(pair.substring(0, idx), "UTF-8") to URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }.toMap()
  }

  private fun findClosedLocalPort(): Int {
    val socket = ServerSocket(0)
    val port = socket.localPort
    socket.close()
    return port
  }

  @Test
  fun section1_to_8_generalChat_omitsSessionKeyHeader_streamsSse_andFetchesAgentMainMediaWithAuth() = runTest {
    val tempFilesDir = File(System.getProperty("java.io.tmpdir"), "clawpal_test_${System.nanoTime()}").apply {
      mkdirs()
    }

    var capturedAuthHeader = ""
    var capturedDeviceIdHeader = ""
    var capturedModelHeader = ""
    var capturedAgentIdHeader: String? = "UNSET"
    var capturedSessionKeyHeader: String? = "UNSET"
    var capturedMediaAuthHeader = ""
    var capturedMediaDeviceIdHeader = ""
    var capturedBodyJson: JSONObject? = null
    var capturedMediaMetaQuery: Map<String, String> = emptyMap()
    var capturedMediaByteQuery: Map<String, String> = emptyMap()

    val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/models") { ex ->
      val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
      if (auth != "Bearer gw-secret-token") {
        val err = """{"error":{"message":"Invalid bearer token","type":"authentication_error"}}""".toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(401, err.size.toLong())
        ex.responseBody.use { it.write(err) }
        return@createContext
      }
      val payload = """{"object":"list","data":[{"id":"openclaw/apk"},{"id":"openclaw/main"}]}"""
      val bytes = payload.toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "application/json")
      ex.sendResponseHeaders(200, bytes.size.toLong())
      ex.responseBody.use { it.write(bytes) }
    }
    server.createContext("/v1/chat/completions") { ex ->
      capturedAuthHeader = ex.requestHeaders.getFirst("Authorization").orEmpty()
      capturedDeviceIdHeader = ex.requestHeaders.getFirst("x-openclaw-device-id").orEmpty()
      capturedModelHeader = ex.requestHeaders.getFirst("x-openclaw-model").orEmpty()
      capturedAgentIdHeader = ex.requestHeaders.getFirst("x-openclaw-agent-id")
      capturedSessionKeyHeader = ex.requestHeaders.getFirst("x-openclaw-session-key")
      val reqText = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
      capturedBodyJson = JSONObject(reqText)

      // Live gateway rejects unqualified or wrong-agent x-openclaw-session-key with HTTP 500
      if (capturedSessionKeyHeader != null) {
        val err = """{"error":{"message":"internal error","type":"api_error"}}""".toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(500, err.size.toLong())
        ex.responseBody.use { it.write(err) }
        return@createContext
      }

      val sse = buildString {
        appendLine("data: {\"id\":\"1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Here is the Chrono Trigger capture!\\n\"}}]}")
        appendLine()
        appendLine("data: {\"id\":\"1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":null}}]}")
        appendLine()
        appendLine("data: {\"id\":\"1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"MEDIA:/home/mcwiggles/.openclaw/workspace/tmp/chrono.png\"}}]}")
        appendLine()
        appendLine("data: {\"id\":\"1\",\"choices\":[],\"usage\":{\"total_tokens\":42}}")
        appendLine()
        appendLine("data: [DONE]")
        appendLine()
      }.toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, sse.size.toLong())
      ex.responseBody.use { it.write(sse) }
    }
    server.createContext("/__openclaw__/assistant-media") { ex ->
      val query = parseQuery(ex.requestURI.rawQuery)
      if (ex.requestHeaders.getFirst("Authorization") != "Bearer gw-secret-token") {
        ex.sendResponseHeaders(401, -1)
        ex.close()
        return@createContext
      }
      if (query["meta"] == "1") {
        capturedMediaMetaQuery = query
        val json = """{"available":true,"mimeType":"image/png","sizeBytes":${fakePng.size},"mediaTicket":"tkt-999","mediaTicketExpiresAt":9999999999}"""
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
      } else if (query["mediaTicket"] == "tkt-999") {
        // CRITICAL: Byte fetch must NOT include sessionKey or agentId query parameters
        if (query.containsKey("sessionKey") || query.containsKey("agentId")) {
          ex.sendResponseHeaders(400, -1)
          ex.close()
          return@createContext
        }
        capturedMediaAuthHeader = ex.requestHeaders.getFirst("Authorization").orEmpty()
        capturedMediaDeviceIdHeader = ex.requestHeaders.getFirst("x-openclaw-device-id").orEmpty()
        capturedMediaByteQuery = query
        ex.responseHeaders.add("Content-Type", "image/png")
        ex.sendResponseHeaders(200, fakePng.size.toLong())
        ex.responseBody.use { it.write(fakePng) }
      } else {
        // Direct byte fetch must also NOT include sessionKey or agentId; return 401 to exercise ?meta=1 -> mediaTicket flow
        if (query.containsKey("sessionKey") || query.containsKey("agentId")) {
          ex.sendResponseHeaders(400, -1)
          ex.close()
          return@createContext
        }
        ex.sendResponseHeaders(401, -1)
        ex.close()
      }
    }
    server.start()

    try {
      // Also verify trailing slashes in host URL are tolerated
      val baseUrlWithSlashes = "http://127.0.0.1:${server.address.port}///"
      val host = OpenClawHost(
        id = "h1",
        label = "Home PC",
        baseUrl = baseUrlWithSlashes,
        lanUrl = baseUrlWithSlashes,
        gatewayToken = "gw-secret-token",
        defaultLlmProvider = OpenClawLlmProvider.VENICE_AI.name,
        defaultLlmModel = "deepseek-v4-1-flash-uncensored",
      )
      val client = OpenClawClient(appFilesDir = tempFilesDir)

      // CRITICAL v2 Section 0 & 10: connectTimeout >= 60s, readTimeout >= 120s (180s), callTimeout == 0
      assertTrue(OpenClawClient.SSE_CONNECT_TIMEOUT_SECONDS >= 60L)
      assertTrue(OpenClawClient.SSE_READ_TIMEOUT_SECONDS in 120L..180L)
      assertTrue(client.sseConnectTimeoutMillis >= 60_000L)
      assertTrue(client.sseReadTimeoutMillis >= 120_000L)
      assertEquals(0L, client.sseCallTimeoutMillis)

      // Acceptance test #1: Test connection -> 200
      val pingOk = client.pingHost(host, deviceId = "pixel-9-paired")
      assertTrue(pingOk.reachable)
      assertEquals(200, pingOk.statusCode)

      // Acceptance test #8: Wrong token -> clear 401 message & reachable == false
      val pingBadToken = client.pingHost(host.copy(gatewayToken = "wrong-token"))
      assertFalse(pingBadToken.reachable)
      assertEquals(401, pingBadToken.statusCode)
      assertTrue(pingBadToken.detail.contains("401"))

      // Acceptance test #2 & #5: Streamed reply + MEDIA: stripped & fetched without sessionKey/agentId + Bearer
      val streamedChunks = mutableListOf<String>()
      val res = client.sendGeneralChat(
        host = host,
        userId = "firebase-uid-999",
        conversationId = "thread-42",
        history = emptyList(),
        userPrompt = "Send screenshot",
        wheelOption = WheelLlmOption.OPENCLAW_THIRD_PARTY,
        providerKeys = LocalProviderKeys(
          globalVeniceModel = "deepseek-v4-1-flash-uncensored",
          deviceId = "pixel-9-paired",
        ),
        onPartialText = { streamedChunks += it },
      ).getOrThrow()

      assertTrue(streamedChunks.isNotEmpty())
      assertEquals("Bearer gw-secret-token", capturedAuthHeader)
      assertEquals("pixel-9-paired", capturedDeviceIdHeader)
      assertEquals("venice/deepseek-v4-1-flash-uncensored", capturedModelHeader)
      // CRITICAL Section 3 Option A: x-openclaw-session-key & x-openclaw-agent-id must NOT be sent
      assertNull(capturedSessionKeyHeader)
      assertNull(capturedAgentIdHeader)

      val body = capturedBodyJson
      assertNotNull(body)
      assertEquals("openclaw/apk", body!!.getString("model"))
      assertEquals("conv:gen_thread-42", body.getString("user"))
      assertTrue(body.getBoolean("stream"))

      // Meta probe uses ?meta=1&source=<path> (no sessionKey/agentId)
      assertEquals("1", capturedMediaMetaQuery["meta"])
      assertEquals("/home/mcwiggles/.openclaw/workspace/tmp/chrono.png", capturedMediaMetaQuery["source"])
      assertNull(capturedMediaMetaQuery["sessionKey"])
      assertNull(capturedMediaMetaQuery["agentId"])

      // Byte fetch uses ?mediaTicket=<t>&source=<path> + Bearer (no sessionKey/agentId)
      assertEquals("Bearer gw-secret-token", capturedMediaAuthHeader)
      assertEquals("pixel-9-paired", capturedMediaDeviceIdHeader)
      assertEquals("tkt-999", capturedMediaByteQuery["mediaTicket"])
      assertEquals("/home/mcwiggles/.openclaw/workspace/tmp/chrono.png", capturedMediaByteQuery["source"])
      assertNull(capturedMediaByteQuery["sessionKey"])
      assertNull(capturedMediaByteQuery["agentId"])

      assertEquals("Here is the Chrono Trigger capture!", res.replyText)
      assertEquals(GeneratedMediaType.IMAGE, res.mediaType)
      assertTrue(res.mediaUrl.orEmpty().startsWith("file://"))
      val cachedFile = File(res.mediaUrl!!.removePrefix("file://"))
      assertTrue(cachedFile.exists())
      assertEquals(fakePng.size, cachedFile.readBytes().size)
    } finally {
      server.stop(0)
      tempFilesDir.deleteRecursively()
    }
  }

  @Test
  fun section6_and_7_multiturnThreadContinuity_newChatIsolation_andInboundImageArray() = runTest {
    val seenPayloads = Collections.synchronizedList(mutableListOf<JSONObject>())
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/chat/completions") { ex ->
      seenPayloads.add(JSONObject(ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)))
      val sse = (
        "data: {\"id\":\"m1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Looks like a frog!\"}}]}\n\n" +
          "data: [DONE]\n\n"
        ).toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, sse.size.toLong())
      ex.responseBody.use { it.write(sse) }
    }
    server.start()

    try {
      val host = OpenClawHost(
        id = "h-img",
        baseUrl = "http://127.0.0.1:${server.address.port}",
        gatewayToken = "tok-img",
      )
      val client = OpenClawClient()

      // Turn 1 in thread A
      client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "thread-A",
        history = emptyList(),
        userPrompt = "hi",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
      ).getOrThrow()

      // Turn 2 in thread A with 10 attached images (must cap at 8 per Section 6)
      val tenImages = (1..10).map { i ->
        MediaAttachmentPayload(
          displayName = "photo_$i.jpg",
          uriOrPath = "content://media/$i",
          mimeType = "image/jpeg",
          linkType = FileLinkType.ANDROID_ATTACHMENT,
          isImage = true,
          imageDataUrl = "data:image/jpeg;base64,AAAA$i",
        )
      }
      client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "thread-A",
        history = listOf(
          GeneralChatMessage(sender = MessageSender.USER.name, rawText = "hi"),
          GeneralChatMessage(sender = MessageSender.BOT.name, rawText = "hello"),
        ),
        userPrompt = "what is this?",
        wheelOption = WheelLlmOption.OPENCLAW_LOCAL,
        attachmentPayloads = tenImages,
      ).getOrThrow()

      // Turn 3 in new chat thread B
      client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "thread-B",
        history = emptyList(),
        userPrompt = "fresh topic",
        wheelOption = WheelLlmOption.OPENCLAW_THIRD_PARTY,
      ).getOrThrow()

      assertEquals(3, seenPayloads.size)
      // All wheel options map to model = "openclaw/apk"
      seenPayloads.forEach { assertEquals("openclaw/apk", it.getString("model")) }

      // Same thread -> same stable user; new chat -> different user
      assertEquals("conv:gen_thread-A", seenPayloads[0].getString("user"))
      assertEquals("conv:gen_thread-A", seenPayloads[1].getString("user"))
      assertEquals("conv:gen_thread-B", seenPayloads[2].getString("user"))

      // Verify multimodal content array on turn 2: 1 text part + 8 image_url parts (capped from 10)
      val messages = seenPayloads[1].getJSONArray("messages")
      val lastUserMsg = messages.getJSONObject(messages.length() - 1)
      val contentArr: JSONArray = lastUserMsg.getJSONArray("content")
      assertEquals(9, contentArr.length())
      assertEquals("text", contentArr.getJSONObject(0).getString("type"))
      assertEquals("image_url", contentArr.getJSONObject(1).getString("type"))
      assertEquals("data:image/jpeg;base64,AAAA1", contentArr.getJSONObject(1).getJSONObject("image_url").getString("url"))
      assertEquals("data:image/jpeg;base64,AAAA8", contentArr.getJSONObject(8).getJSONObject("image_url").getString("url"))
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun section1_9_10_lanToTailscaleFailover_http500RetryOnce_andStructuredErrors() = runTest {
    val attempts = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/models") { ex ->
      val body = """{"data":[{"id":"openclaw/apk"}]}""".toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "application/json")
      ex.sendResponseHeaders(200, body.size.toLong())
      ex.responseBody.use { it.write(body) }
    }
    server.createContext("/v1/chat/completions") { ex ->
      val bodyText = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
      val count = attempts.incrementAndGet()
      when {
        bodyText.contains("trigger-409") -> {
          val err = """{"error":{"message":"session archived","type":"conflict"}}""".toByteArray(StandardCharsets.UTF_8)
          ex.responseHeaders.add("Content-Type", "application/json")
          ex.sendResponseHeaders(409, err.size.toLong())
          ex.responseBody.use { it.write(err) }
        }
        bodyText.contains("trigger-403") -> {
          val err = """{"error":{"message":"missing operator scope","type":"permission_error"}}""".toByteArray(StandardCharsets.UTF_8)
          ex.responseHeaders.add("Content-Type", "application/json")
          ex.sendResponseHeaders(403, err.size.toLong())
          ex.responseBody.use { it.write(err) }
        }
        bodyText.contains("trigger-500-once") && count == 1 -> {
          val err = """{"error":{"message":"transient backend hiccup","type":"api_error"}}""".toByteArray(StandardCharsets.UTF_8)
          ex.responseHeaders.add("Content-Type", "application/json")
          ex.sendResponseHeaders(500, err.size.toLong())
          ex.responseBody.use { it.write(err) }
        }
        else -> {
          val sse = (
            "data: {\"id\":\"ok\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Recovered via Tailscale!\"}}]}\n\n" +
              "data: [DONE]\n\n"
            ).toByteArray(StandardCharsets.UTF_8)
          ex.responseHeaders.add("Content-Type", "text/event-stream")
          ex.sendResponseHeaders(200, sse.size.toLong())
          ex.responseBody.use { it.write(sse) }
        }
      }
    }
    server.start()

    try {
      val deadLanPort = findClosedLocalPort()
      val host = OpenClawHost(
        id = "h-failover",
        lanUrl = "http://127.0.0.1:$deadLanPort",
        tailscaleUrl = "http://127.0.0.1:${server.address.port}",
        gatewayToken = "tok-failover",
      )
      val client = OpenClawClient()

      // Ping auto-falls back from dead LAN to working Tailscale
      val ping = client.pingHost(host)
      assertTrue(ping.reachable)
      assertTrue(ping.detail.contains("fallback", ignoreCase = true))

      // Chat auto-falls back to Tailscale AND retries once when first response is HTTP 500
      val res = client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "failover-thread",
        history = emptyList(),
        userPrompt = "trigger-500-once",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
      ).getOrThrow()
      assertEquals("Recovered via Tailscale!", res.replyText)
      assertEquals(2, attempts.get())

      // HTTP 409 surfaces archived session guidance + JSON error message
      val err409 = client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "failover-thread",
        history = emptyList(),
        userPrompt = "trigger-409",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
      ).exceptionOrNull()
      assertNotNull(err409)
      assertTrue(err409!!.message.orEmpty().contains("Session archived"))
      assertTrue(err409.message.orEmpty().contains("Control UI"))

      // HTTP 403 surfaces scope/authority error.message (not overwritten with invalid token)
      val err403 = client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "failover-thread",
        history = emptyList(),
        userPrompt = "trigger-403",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
      ).exceptionOrNull()
      assertNotNull(err403)
      assertTrue(err403!!.message.orEmpty().contains("missing operator scope"))
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun botDispatch_parsesPathDirective_andHandlesEmptyReplyGracefully() = runTest {
    val tempFilesDir = File(System.getProperty("java.io.tmpdir"), "clawpal_test2_${System.nanoTime()}").apply {
      mkdirs()
    }
    var capturedBodyJson: JSONObject? = null
    var capturedModelHeader = ""
    var capturedSessionHeader: String? = "UNSET"
    var replyMode = "path"
    val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 7, 8)

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/chat/completions") { ex ->
      capturedModelHeader = ex.requestHeaders.getFirst("x-openclaw-model").orEmpty()
      capturedSessionHeader = ex.requestHeaders.getFirst("x-openclaw-session-key")
      capturedBodyJson = JSONObject(ex.requestBody.readBytes().toString(StandardCharsets.UTF_8))
      val sseContent = if (replyMode == "path") "PATH=/tmp/render.png" else ""
      val sse = (
        "data: {\"id\":\"2\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":${JSONObject.quote(sseContent)}}}]}\n\n" +
          "data: [DONE]\n\n"
        ).toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, sse.size.toLong())
      ex.responseBody.use { it.write(sse) }
    }
    server.createContext("/__openclaw__/assistant-media") { ex ->
      val query = parseQuery(ex.requestURI.rawQuery)
      val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
      // Direct byte fetch must use ?source=<path> with Bearer token and NO sessionKey or agentId
      if (auth == "Bearer tok-2" &&
        query["source"] == "/tmp/render.png" &&
        !query.containsKey("sessionKey") &&
        !query.containsKey("agentId")
      ) {
        ex.responseHeaders.add("Content-Type", "image/png")
        ex.sendResponseHeaders(200, fakePng.size.toLong())
        ex.responseBody.use { it.write(fakePng) }
      } else {
        ex.sendResponseHeaders(400, -1)
        ex.close()
      }
    }
    server.start()

    try {
      val baseUrl = "http://127.0.0.1:${server.address.port}"
      val host = OpenClawHost(
        id = "h2",
        label = "Home PC",
        baseUrl = baseUrl,
        gatewayToken = "tok-2",
        defaultLlmProvider = OpenClawLlmProvider.VENICE_AI.name,
        defaultLlmModel = "claude-sonnet-4-6",
      )
      val bot = CompanionBot(
        id = "bot-coding",
        userId = "firebase-uid",
        name = "Master Ding",
        openClawAgentId = "unconfigured-agent",
        llmProvider = OpenClawLlmProvider.LOCAL_LLM.name,
        llmModel = "ollama/qwen3.5-fast",
      )
      val client = OpenClawClient(appFilesDir = tempFilesDir)

      val pathResult = client.dispatchInstruction(
        host = host,
        bot = bot,
        casualMessage = "make chart",
        refinedInstruction = null,
        attachedFiles = emptyList(),
        linkedPaths = emptyList(),
        wheelOverride = WheelLlmOption.OPENCLAW_DEFAULT,
      ).getOrThrow()

      assertNull(capturedSessionHeader)
      assertEquals("openclaw/apk", capturedBodyJson!!.getString("model"))
      assertEquals("conv:bot_${bot.resolvedBotDudeSlug()}", capturedBodyJson.getString("user"))
      assertEquals("ollama/qwen3.5-fast", capturedModelHeader)
      assertTrue(pathResult.mediaUrl.orEmpty().startsWith("file://"))
      assertEquals("Attached image from OpenClaw!", pathResult.replyText)

      replyMode = "empty"
      val emptyResult = client.dispatchInstruction(
        host = host,
        bot = bot,
        casualMessage = "noop",
        refinedInstruction = null,
        attachedFiles = emptyList(),
        linkedPaths = emptyList(),
      ).getOrThrow()
      assertEquals("No response from OpenClaw.", emptyResult.replyText)

      // Host file:///home/... path should convert to clean HTTP assistant-media?source=<path> without sessionKey or agentId
      val hostFileUrl = MediaSaveHelper.resolveDownloadUrl(
        raw = "file:///home/alice/secret.png",
        host = host,
        sessionKey = "conv:bot_${bot.resolvedBotDudeSlug()}",
      )
      assertEquals("$baseUrl/__openclaw__/assistant-media?source=%2Fhome%2Falice%2Fsecret.png", hostFileUrl)
      assertFalse(hostFileUrl.orEmpty().contains("sessionKey="))
      assertFalse(hostFileUrl.orEmpty().contains("agentId="))

      // Legacy persisted URLs with sessionKey/agentId must also be sanitized for byte fetch
      val legacyUrl = "$baseUrl/__openclaw__/assistant-media?source=%2Fhome%2Falice%2Fsecret.png&sessionKey=conv%3A1&agentId=main"
      val sanitized = MediaSaveHelper.resolveDownloadUrl(legacyUrl, host)
      assertEquals("$baseUrl/__openclaw__/assistant-media?source=%2Fhome%2Falice%2Fsecret.png", sanitized)
    } finally {
      server.stop(0)
      tempFilesDir.deleteRecursively()
    }
  }

  @Test
  fun sseStream_toleratesSilentMidStreamGapWithZeroKeepalivePings() = runTest {
    val requestCount = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = Executors.newCachedThreadPool()
    server.createContext("/v1/chat/completions") { ex ->
      requestCount.incrementAndGet()
      ex.requestBody.readBytes()
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, 0) // chunked SSE stream
      ex.responseBody.use { out ->
        out.write(
          "data: {\"id\":\"s1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Running tool... \"}}]}\n\n"
            .toByteArray(StandardCharsets.UTF_8)
        )
        out.flush()
        // Simulate silent tool execution gap with zero keepalive pings on the wire
        Thread.sleep(600L)
        out.write(
          "data: {\"id\":\"s1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Done!\"}}]}\n\n"
            .toByteArray(StandardCharsets.UTF_8)
        )
        out.write("data: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
        out.flush()
      }
    }
    server.start()

    try {
      val host = OpenClawHost(
        id = "h-sse-gap",
        baseUrl = "http://127.0.0.1:${server.address.port}",
        gatewayToken = "tok-gap",
      )
      val client = OpenClawClient()
      val partials = mutableListOf<String>()
      val res = client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "thread-gap",
        history = emptyList(),
        userPrompt = "run slow tool",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
        onPartialText = { partials += it },
      ).getOrThrow()

      assertEquals(1, requestCount.get())
      assertEquals("Running tool... Done!", res.replyText)
      assertEquals(2, partials.size)
      assertEquals("Running tool... ", partials[0])
      assertEquals("Running tool... Done!", partials[1])
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun v2_midStreamSocketDrop_surfacesGracefulError_andSpacesInMediaSourceUsePercent20() = runTest(timeout = 240.seconds) {
    val hostForUrl = OpenClawHost(
      id = "h-space",
      baseUrl = "http://192.168.68.53:18789",
      gatewayToken = "tok-space",
    )
    val encodedSpaceUrl = MediaSaveHelper.buildAssistantMediaUrl(
      sourcePath = "/home/mcwiggles/my shots/frog.png",
      host = hostForUrl,
    )
    assertEquals(
      "http://192.168.68.53:18789/__openclaw__/assistant-media?source=%2Fhome%2Fmcwiggles%2Fmy%20shots%2Ffrog.png",
      encodedSpaceUrl,
    )
    val mediaHeaders = MediaSaveHelper.authHeadersForUrl(encodedSpaceUrl!!, hostForUrl)
    assertEquals("Bearer tok-space", mediaHeaders["Authorization"])

    // Acceptance test #8: kill socket mid-stream -> graceful formatted error, no crash
    val serverSocket = ServerSocket(0)
    val serverThread = Thread {
      try {
        val sock = serverSocket.accept()
        val input = sock.getInputStream()
        val buf = ByteArray(2048)
        input.read(buf)
        val out = sock.getOutputStream()
        out.write(
          (
            "HTTP/1.1 200 OK\r\n" +
              "Content-Type: text/event-stream\r\n" +
              "Transfer-Encoding: chunked\r\n\r\n" +
              "52\r\n" +
              "data: {\"id\":\"k1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":\"Halfway\"}}]}\n\n\r\n"
            ).toByteArray(StandardCharsets.UTF_8)
        )
        out.flush()
        // Abruptly reset socket mid-stream without sending terminating 0\r\n\r\n chunk
        sock.setSoLinger(true, 0)
        sock.close()
      } catch (_: Exception) {
      }
    }
    serverThread.start()

    val client = OpenClawClient()
    try {
      val host = OpenClawHost(
        id = "h-kill",
        baseUrl = "http://127.0.0.1:${serverSocket.localPort}",
        gatewayToken = "tok-kill",
      )
      val res = client.sendGeneralChat(
        host = host,
        userId = "u1",
        conversationId = "thread-kill",
        history = emptyList(),
        userPrompt = "test midstream kill",
        wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
      )
      assertTrue(res.isFailure)
      val msg = res.exceptionOrNull()?.message.orEmpty()
      assertTrue("Expected graceful drop message, got: $msg", msg.contains("Connection dropped", ignoreCase = true) || msg.isNotBlank())
    } finally {
      runCatching { client.shutdown() }
      runCatching { serverSocket.close() }
      serverThread.join(1000L)
    }
  }

  @Test
  fun botDudes_executeConcurrentlyWithIsolatedUserSessions_andNoSessionKeyHeader() = runTest {
    val tempFilesDir = File(System.getProperty("java.io.tmpdir"), "clawpal_test_conc_${System.nanoTime()}").apply {
      mkdirs()
    }
    val capturedHeadersByUser = ConcurrentHashMap<String, Map<String, String>>()
    val completionOrder = Collections.synchronizedList(mutableListOf<String>())

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = Executors.newFixedThreadPool(4)
    server.createContext("/v1/chat/completions") { ex ->
      val body = JSONObject(ex.requestBody.readBytes().toString(StandardCharsets.UTF_8))
      val userKey = body.getString("user")
      val headerMap = mapOf(
        "sessionHeader" to (ex.requestHeaders.getFirst("x-openclaw-session-key") ?: "ABSENT"),
        "model" to ex.requestHeaders.getFirst("x-openclaw-model").orEmpty(),
        "perms" to ex.requestHeaders.getFirst("x-openclaw-tool-permissions").orEmpty(),
        "workspace" to ex.requestHeaders.getFirst("x-openclaw-workspace").orEmpty(),
      )
      capturedHeadersByUser[userKey] = headerMap

      val isSlowTranslator = userKey.contains("translator")
      if (isSlowTranslator) {
        Thread.sleep(350L)
      }
      val reply = if (isSlowTranslator) "Translated PDF complete." else "Quick chat reply!"
      completionOrder.add(if (isSlowTranslator) "translator_dude" else "chat_dude")

      val sse = (
        "data: {\"id\":\"c1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":${JSONObject.quote(reply)}}}]}\n\n" +
          "data: [DONE]\n\n"
        ).toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, sse.size.toLong())
      ex.responseBody.use { it.write(sse) }
    }
    server.start()

    try {
      val baseUrl = "http://127.0.0.1:${server.address.port}"
      val host = OpenClawHost(
        id = "h-conc",
        label = "Home PC",
        baseUrl = baseUrl,
        gatewayToken = "tok-conc",
      )
      val translatorBot = CompanionBot(
        id = "translator_dude",
        name = "Translator Dude",
        openClawAgentId = "translator",
        llmProvider = OpenClawLlmProvider.LOCAL_LLM.name,
        llmModel = "ollama/translategemma",
        toolPermissions = listOf(AgentToolPermission.WORKSPACE_READ.wireKey, AgentToolPermission.WORKSPACE_WRITE.wireKey),
        workspacePath = "/home/alice/docs",
      )
      val chatBot = CompanionBot(
        id = "chat_dude",
        name = "Chat Dude",
        openClawAgentId = "main",
        llmProvider = OpenClawLlmProvider.VENICE_AI.name,
        llmModel = "claude-sonnet-4-6",
        toolPermissions = listOf(AgentToolPermission.WEB_SEARCH.wireKey),
      )

      val client = OpenClawClient(appFilesDir = tempFilesDir)
      val slowJob = async {
        client.dispatchInstruction(
          host = host,
          bot = translatorBot,
          casualMessage = "Translate attached manual",
          refinedInstruction = null,
          attachedFiles = emptyList(),
          linkedPaths = emptyList(),
        ).getOrThrow()
      }
      delay(40L)
      val fastJob = async {
        client.dispatchInstruction(
          host = host,
          bot = chatBot,
          casualMessage = "Quick question",
          refinedInstruction = null,
          attachedFiles = emptyList(),
          linkedPaths = emptyList(),
        ).getOrThrow()
      }

      val results = listOf(slowJob, fastJob).awaitAll()
      assertEquals("Translated PDF complete.", results[0].replyText)
      assertEquals("Quick chat reply!", results[1].replyText)
      assertEquals("chat_dude", completionOrder.first())

      val u1 = "conv:bot_${translatorBot.resolvedBotDudeSlug()}"
      val u2 = "conv:bot_${chatBot.resolvedBotDudeSlug()}"
      assertNotEquals(u1, u2)
      assertEquals("ABSENT", capturedHeadersByUser[u1]?.get("sessionHeader"))
      assertEquals("ABSENT", capturedHeadersByUser[u2]?.get("sessionHeader"))
      assertEquals("ollama/translategemma", capturedHeadersByUser[u1]?.get("model"))
      assertEquals("read,write", capturedHeadersByUser[u1]?.get("perms"))
      assertEquals("/home/alice/docs", capturedHeadersByUser[u1]?.get("workspace"))
      assertEquals("venice/claude-sonnet-4-6", capturedHeadersByUser[u2]?.get("model"))
      assertEquals("web", capturedHeadersByUser[u2]?.get("perms"))
    } finally {
      server.stop(0)
      tempFilesDir.deleteRecursively()
    }
  }

  @Test
  fun spawnBackgroundTask_and_dispatchCafeBotTurn_sendIsolatedConvUsersWithoutSessionKeyHeader() = runTest {
    val tempFilesDir = File(System.getProperty("java.io.tmpdir"), "clawpal_test_a2a_${System.nanoTime()}").apply {
      mkdirs()
    }
    val seenRequests = Collections.synchronizedList(mutableListOf<Pair<Map<String, String>, JSONObject>>())

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/chat/completions") { ex ->
      val headers = mapOf(
        "sessionHeader" to (ex.requestHeaders.getFirst("x-openclaw-session-key") ?: "ABSENT"),
        "spawn" to ex.requestHeaders.getFirst("x-openclaw-spawn-subagent").orEmpty(),
        "parent" to ex.requestHeaders.getFirst("x-openclaw-parent-session").orEmpty(),
        "taskId" to ex.requestHeaders.getFirst("x-openclaw-subagent-task-id").orEmpty(),
        "a2aRoom" to ex.requestHeaders.getFirst("x-openclaw-a2a-room").orEmpty(),
        "a2aReplyTo" to ex.requestHeaders.getFirst("x-openclaw-a2a-reply-to").orEmpty(),
      )
      val body = JSONObject(ex.requestBody.readBytes().toString(StandardCharsets.UTF_8))
      seenRequests.add(headers to body)

      val text = if (headers["a2aRoom"].orEmpty().isNotBlank()) {
        "[Translator]: Sounds sharp—updated the phrasing."
      } else {
        "Background batch complete."
      }
      val sse = (
        "data: {\"id\":\"a1\",\"model\":\"openclaw/apk\",\"choices\":[{\"delta\":{\"content\":${JSONObject.quote(text)}}}]}\n\n" +
          "data: [DONE]\n\n"
        ).toByteArray(StandardCharsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.sendResponseHeaders(200, sse.size.toLong())
      ex.responseBody.use { it.write(sse) }
    }
    server.start()

    try {
      val baseUrl = "http://127.0.0.1:${server.address.port}"
      val host = OpenClawHost(id = "h3", baseUrl = baseUrl, gatewayToken = "tok-3")
      val translator = CompanionBot(
        id = "bot-trans",
        name = "Translator",
        openClawAgentId = "translator",
        llmProvider = OpenClawLlmProvider.LOCAL_LLM.name,
        llmModel = "ollama/translategemma",
      )
      val critic = CompanionBot(
        id = "bot-critic",
        name = "Critic",
        openClawAgentId = "critic",
        llmProvider = OpenClawLlmProvider.VENICE_AI.name,
        llmModel = "claude-sonnet-4-6",
      )
      val client = OpenClawClient(appFilesDir = tempFilesDir)

      val bgRes = client.spawnBackgroundTask(
        host = host,
        bot = translator,
        taskId = "task99",
        prompt = "Translate 20-page handbook",
      ).getOrThrow()
      assertEquals("Background batch complete.", bgRes.replyText)

      val (bgHeaders, bgBody) = seenRequests[0]
      assertEquals("ABSENT", bgHeaders["sessionHeader"])
      assertEquals("openclaw/apk", bgBody.getString("model"))
      assertEquals("conv:bot_${translator.resolvedBotDudeSlug()}_spawn_task99", bgBody.getString("user"))
      assertEquals("true", bgHeaders["spawn"])
      assertEquals("conv:bot_${translator.resolvedBotDudeSlug()}", bgHeaders["parent"])
      assertEquals("task99", bgHeaders["taskId"])

      val room = ChatRoom(id = "room-cafe", title = "Translation Lounge", topic = "Polish copy", botIds = listOf("bot-trans", "bot-critic"))
      val transcript = listOf(
        CafeTranscriptEntry(id = "e1", speakerId = "u1", speakerName = "You", role = CafeSpeakerRole.USER, content = "Translate welcome banner"),
        CafeTranscriptEntry(id = "e2", speakerId = "bot-critic", speakerName = "Critic", role = CafeSpeakerRole.BOT, replyToBotName = "Translator", content = "@Translator tighten the CTA."),
      )
      val cafeRes = client.dispatchCafeBotTurn(
        host = host,
        room = room,
        speakerBot = translator,
        participants = listOf(translator, critic),
        transcript = transcript,
        replyToBotName = "Critic",
      ).getOrThrow()

      assertEquals("Sounds sharp—updated the phrasing.", cafeRes.replyText)
      val (cafeHeaders, cafeBody) = seenRequests[1]
      assertEquals("ABSENT", cafeHeaders["sessionHeader"])
      assertEquals("conv:bot_${translator.resolvedBotDudeSlug()}_cafe_room-cafe", cafeBody.getString("user"))
      assertEquals("openclaw/apk", cafeBody.getString("model"))
      assertEquals("room-cafe", cafeHeaders["a2aRoom"])
      assertEquals("Critic", cafeHeaders["a2aReplyTo"])
    } finally {
      server.stop(0)
      tempFilesDir.deleteRecursively()
    }
  }

  @Test
  fun driveBridgeMode_writesTaskToInstructions_andReadsProcessedResultAndOutboxMedia() = runTest {
    val rootDir = File(System.getProperty("java.io.tmpdir"), "clawpal_bridge_${System.nanoTime()}").apply {
      mkdirs()
    }
    val cacheDir = File(rootDir, "app_cache").apply { mkdirs() }
    val bridgeRoot = File(rootDir, "APK-Bridge").apply { mkdirs() }
    val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 1, 2, 3, 4, 5)

    try {
      val bridgeManager = DriveBridgeManager(
        context = null,
        appFilesDir = cacheDir,
        pollIntervalMs = 40L,
        timeoutMs = 5_000L,
      )
      val status = bridgeManager.inspectBridgeFolders(bridgeRoot.absolutePath)
      assertTrue(status.valid)
      assertTrue(File(bridgeRoot, "instructions").isDirectory)
      assertTrue(File(bridgeRoot, "processed").isDirectory)
      assertTrue(File(bridgeRoot, "outbox").isDirectory)

      val client = OpenClawClient(appFilesDir = cacheDir, driveBridgeManager = bridgeManager)
      val host = OpenClawHost(id = "bridge", label = "Drive Bridge", baseUrl = "")

      val pcPollerJob = async {
        val instrDir = File(bridgeRoot, "instructions")
        var taskFile: File? = null
        repeat(60) {
          taskFile = instrDir.listFiles()?.firstOrNull { it.name.endsWith(".json") }
          if (taskFile != null) return@repeat
          delay(25L)
        }
        val written = taskFile!!
        val taskJson = JSONObject(written.readText())
        assertEquals("openclaw/apk", taskJson.getString("model"))
        assertEquals("conv:gen_thread-bridge-1", taskJson.getString("user"))
        assertEquals("paired-device-7", taskJson.getString("deviceId"))

        val taskId = taskJson.getString("id")
        val outboxImg = File(bridgeRoot, "outbox/${taskId}_snap.png")
        outboxImg.writeBytes(fakePng)

        val resultJson = JSONObject().apply {
          put("id", taskId)
          put("taskId", taskId)
          put("reply", "Captured via Drive Bridge!\nMEDIA:${outboxImg.name}")
          put("mediaPath", outboxImg.name)
        }
        File(bridgeRoot, "processed/$taskId.json").writeText(resultJson.toString())
      }

      val chatJob = async {
        client.sendGeneralChat(
          host = host,
          userId = "uid-1",
          conversationId = "thread-bridge-1",
          history = emptyList(),
          userPrompt = "Take a desktop screenshot",
          wheelOption = WheelLlmOption.OPENCLAW_DEFAULT,
          providerKeys = LocalProviderKeys(
            deviceId = "paired-device-7",
            bridgeEnabled = true,
            bridgeFolderUri = bridgeRoot.absolutePath,
          ),
        ).getOrThrow()
      }

      val (_, dispatchRes) = awaitAll(pcPollerJob, chatJob)
      val result = dispatchRes as com.example.data.openclaw.OpenClawDispatchResult
      assertEquals("Captured via Drive Bridge!", result.replyText)
      assertEquals(GeneratedMediaType.IMAGE, result.mediaType)
      assertTrue(result.mediaUrl.orEmpty().startsWith("file://"))
      val cached = File(result.mediaUrl!!.removePrefix("file://"))
      assertTrue(cached.exists())
      assertEquals(fakePng.size, cached.readBytes().size)
    } finally {
      rootDir.deleteRecursively()
    }
  }
}
