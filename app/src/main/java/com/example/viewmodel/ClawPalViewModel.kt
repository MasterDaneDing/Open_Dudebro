package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.PromptRefinerService
import com.example.data.firebase.ClawPalRepository
import com.example.data.local.LocalPrefsStore
import com.example.data.local.LocalUiPrefs
import com.example.data.openclaw.HostControlAction
import com.example.data.openclaw.LocalProviderKeys
import com.example.data.openclaw.OpenClawClient
import com.example.data.openclaw.OpenClawPingResult
import com.example.model.AiRoutingMode
import com.example.model.BotExpression
import com.example.model.BotFunctionPreset
import com.example.model.BotMessage
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.FileLinkType
import com.example.model.HostStatus
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.RoomMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClawPalViewModel(
  private val repository: ClawPalRepository,
  private val refinerService: PromptRefinerService,
  private val openClawClient: OpenClawClient,
  private val localPrefsStore: LocalPrefsStore,
  val currentUserId: String,
) : ViewModel() {

  val localPrefs: StateFlow<LocalUiPrefs> = localPrefsStore.prefsFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalUiPrefs())

  val botsState: StateFlow<UiState<List<CompanionBot>>> = repository.observeBots(currentUserId)
    .map<List<CompanionBot>, UiState<List<CompanionBot>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("bots", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val hostsState: StateFlow<UiState<List<OpenClawHost>>> = repository.observeHosts(currentUserId)
    .map<List<OpenClawHost>, UiState<List<OpenClawHost>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("hosts", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val linkedFilesState: StateFlow<UiState<List<LinkedFile>>> = repository.observeLinkedFiles(currentUserId)
    .map<List<LinkedFile>, UiState<List<LinkedFile>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("linked files", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val chatRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeChatRooms(currentUserId)
    .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("chat rooms", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _selectedBotId = MutableStateFlow("")
  val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

  private val _selectedRoomId = MutableStateFlow("")
  val selectedRoomId: StateFlow<String> = _selectedRoomId.asStateFlow()

  val messagesState: StateFlow<UiState<List<BotMessage>>> = _selectedBotId
    .flatMapLatest { botId ->
      if (botId.isBlank()) {
        flowOf(UiState.Success(emptyList()))
      } else {
        repository.observeMessages(currentUserId, botId)
          .map<List<BotMessage>, UiState<List<BotMessage>>> { UiState.Success(it) }
          .catch { error -> emit(unwrapFirestoreError("messages", error)) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val roomMessagesState: StateFlow<UiState<List<RoomMessage>>> = _selectedRoomId
    .flatMapLatest { roomId ->
      if (roomId.isBlank()) {
        flowOf(UiState.Success(emptyList()))
      } else {
        repository.observeRoomMessages(currentUserId, roomId)
          .map<List<RoomMessage>, UiState<List<RoomMessage>>> { UiState.Success(it) }
          .catch { error -> emit(unwrapFirestoreError("room messages", error)) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _roomActiveExpressions = MutableStateFlow<Map<String, BotExpression>>(emptyMap())
  val roomActiveExpressions: StateFlow<Map<String, BotExpression>> = _roomActiveExpressions.asStateFlow()

  private val _liveExpression = MutableStateFlow(BotExpression.HAPPY)
  val liveExpression: StateFlow<BotExpression> = _liveExpression.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _pendingAttachments = MutableStateFlow<List<LinkedFile>>(emptyList())
  val pendingAttachments: StateFlow<List<LinkedFile>> = _pendingAttachments.asStateFlow()

  private val _pingResult = MutableStateFlow<OpenClawPingResult?>(null)
  val pingResult: StateFlow<OpenClawPingResult?> = _pingResult.asStateFlow()

  private val _controlLog = MutableStateFlow<List<String>>(emptyList())
  val controlLog: StateFlow<List<String>> = _controlLog.asStateFlow()

  init {
    viewModelScope.launch {
      repository.seedStarterWorkspaceIfEmpty(currentUserId)
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to initialize workspace." }
    }
    viewModelScope.launch {
      localPrefs.collect { prefs ->
        if (_selectedBotId.value.isBlank() && prefs.selectedBotId.isNotBlank()) {
          _selectedBotId.value = prefs.selectedBotId
        }
      }
    }
    viewModelScope.launch {
      botsState.collect { state ->
        if (state is UiState.Success && state.data.isNotEmpty()) {
          val current = _selectedBotId.value
          if (current.isBlank() || state.data.none { it.id == current }) {
            selectBot(state.data.first().id)
          }
        }
      }
    }
  }

  fun selectBot(botId: String) {
    _selectedBotId.value = botId
    _liveExpression.value = BotExpression.HAPPY
    viewModelScope.launch {
      localPrefsStore.setSelectedBotId(botId)
    }
  }

  fun selectRoom(roomId: String) {
    _selectedRoomId.value = roomId
    _roomActiveExpressions.value = emptyMap()
  }

  fun setLiveExpression(expression: BotExpression) {
    _liveExpression.value = expression
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun togglePendingAttachment(file: LinkedFile) {
    val cur = _pendingAttachments.value
    _pendingAttachments.value = if (cur.any { it.uriOrPath == file.uriOrPath }) {
      cur.filterNot { it.uriOrPath == file.uriOrPath }
    } else {
      (cur + file).take(10)
    }
  }

  fun clearPendingAttachments() {
    _pendingAttachments.value = emptyList()
  }

  fun sendCasualMessage(
    rawText: String,
    bot: CompanionBot,
    activeHost: OpenClawHost?,
  ) {
    val trimmed = rawText.trim()
    if (trimmed.isBlank() || bot.id.isBlank()) return

    val attachmentsSnapshot = _pendingAttachments.value
    val attachedLabels = attachmentsSnapshot
      .filter { it.linkTypeEnum == FileLinkType.ANDROID_ATTACHMENT }
      .map { it.displayName }
    val linkedPathLabels = attachmentsSnapshot
      .filter { it.linkTypeEnum != FileLinkType.ANDROID_ATTACHMENT }
      .map { it.uriOrPath }

    _pendingAttachments.value = emptyList()

    viewModelScope.launch {
      _isBusy.value = true
      _liveExpression.value = BotExpression.THINKING

      val userMsg = BotMessage(
        userId = currentUserId,
        botId = bot.id,
        sender = MessageSender.USER.name,
        rawText = trimmed,
        expression = BotExpression.HAPPY.name,
        status = MessageStatus.IDLE.name,
        attachedFiles = attachedLabels,
        linkedPaths = linkedPathLabels,
      )
      repository.addMessage(currentUserId, bot.id, userMsg)
        .onFailure {
          _liveExpression.value = BotExpression.SORRY
          _statusBanner.value = it.localizedMessage ?: "Could not save message."
          _isBusy.value = false
          return@launch
        }

      val botMsgId = repository.addMessage(
        currentUserId,
        bot.id,
        BotMessage(
          userId = currentUserId,
          botId = bot.id,
          sender = MessageSender.BOT.name,
          rawText = "Hmm... let me translate your request into clear OpenClaw steps!",
          expression = BotExpression.THINKING.name,
          status = MessageStatus.PARSING.name,
          attachedFiles = attachedLabels,
          linkedPaths = linkedPathLabels,
        ),
      ).getOrElse { err ->
        _liveExpression.value = BotExpression.SORRY
        _statusBanner.value = err.localizedMessage ?: "Failed to create bot response."
        _isBusy.value = false
        return@launch
      }

      val mode = activeHost?.aiModeEnum ?: AiRoutingMode.HYBRID_GEMINI_OPENCLAW
      val providerKeys = currentProviderKeys()

      if (mode == AiRoutingMode.OPENCLAW_ONLY && activeHost != null) {
        _liveExpression.value = BotExpression.WORKING
        val dispatchRes = openClawClient.dispatchInstruction(
          host = activeHost,
          bot = bot,
          casualMessage = trimmed,
          refinedInstruction = null,
          attachedFiles = attachedLabels,
          linkedPaths = linkedPathLabels,
          providerKeys = providerKeys,
        )
        dispatchRes.onSuccess { res ->
          _liveExpression.value = res.expression
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = botMsgId,
            rawText = res.replyText,
            parsedInstruction = res.parsedInstruction,
            instructionGoal = "Dispatched to OpenClaw (${bot.openClawAgentId})",
            instructionSteps = emptyList(),
            expression = res.expression,
            status = MessageStatus.COMPLETED,
            takeoverSuggested = res.takeoverSuggested,
          )
          repository.updateBotPreview(currentUserId, bot.id, res.replyText)
        }.onFailure { err ->
          _liveExpression.value = BotExpression.SORRY
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = botMsgId,
            rawText = "Sorry! I couldn't reach OpenClaw at ${activeHost.baseUrl}: ${err.localizedMessage ?: "Connection failed"}",
            parsedInstruction = trimmed,
            instructionGoal = "OpenClaw host unreachable",
            instructionSteps = emptyList(),
            expression = BotExpression.SORRY,
            status = MessageStatus.FAILED,
            takeoverSuggested = true,
          )
        }
        _isBusy.value = false
        return@launch
      }

      // Step 1: Parse casual chat into clear OpenClaw instruction via Gemini (Firebase AI Logic)
      val parseResult = refinerService.parseCasualRequest(
        bot = bot,
        casualMessage = trimmed,
        attachedFiles = attachedLabels,
        linkedPaths = linkedPathLabels,
      )

      parseResult.onFailure { err ->
        _liveExpression.value = BotExpression.SORRY
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = botMsgId,
          rawText = "Sorry! Prompt parsing failed: ${err.localizedMessage ?: "Unknown error"}",
          parsedInstruction = null,
          instructionGoal = "Parsing failed",
          instructionSteps = emptyList(),
          expression = BotExpression.SORRY,
          status = MessageStatus.FAILED,
          takeoverSuggested = false,
        )
        _isBusy.value = false
        return@launch
      }

      val parsed = parseResult.getOrThrow()

      if (mode == AiRoutingMode.GEMINI_PARSER_PREVIEW || activeHost == null) {
        val expr = if (parsed.needsHostTakeover) BotExpression.ALERT else parsed.recommendedExpression
        _liveExpression.value = expr
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = botMsgId,
          rawText = parsed.friendlyBotReply,
          parsedInstruction = parsed.clearInstruction,
          instructionGoal = parsed.goal,
          instructionSteps = parsed.steps,
          expression = expr,
          status = MessageStatus.COMPLETED,
          takeoverSuggested = parsed.needsHostTakeover,
        )
        repository.updateBotPreview(currentUserId, bot.id, parsed.friendlyBotReply)
        _isBusy.value = false
        return@launch
      }

      // Step 2 (HYBRID_GEMINI_OPENCLAW): Update card with parsed instruction, then dispatch to OpenClaw
      _liveExpression.value = BotExpression.WORKING
      repository.updateMessageExecution(
        userId = currentUserId,
        botId = bot.id,
        messageId = botMsgId,
        rawText = "${parsed.friendlyBotReply}\nSending instructions to ${activeHost.label}...",
        parsedInstruction = parsed.clearInstruction,
        instructionGoal = parsed.goal,
        instructionSteps = parsed.steps,
        expression = BotExpression.WORKING,
        status = MessageStatus.DISPATCHED,
        takeoverSuggested = parsed.needsHostTakeover,
      )

      val dispatchResult = openClawClient.dispatchInstruction(
        host = activeHost,
        bot = bot,
        casualMessage = trimmed,
        refinedInstruction = parsed.clearInstruction,
        attachedFiles = attachedLabels,
        linkedPaths = linkedPathLabels,
        providerKeys = providerKeys,
      )

      dispatchResult.onSuccess { res ->
        val finalExpr = if (res.takeoverSuggested) BotExpression.ALERT else res.expression
        _liveExpression.value = finalExpr
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = botMsgId,
          rawText = res.replyText,
          parsedInstruction = parsed.clearInstruction,
          instructionGoal = parsed.goal,
          instructionSteps = parsed.steps,
          expression = finalExpr,
          status = MessageStatus.COMPLETED,
          takeoverSuggested = res.takeoverSuggested || parsed.needsHostTakeover,
        )
        repository.updateBotPreview(currentUserId, bot.id, res.replyText)
      }.onFailure { err ->
        _liveExpression.value = BotExpression.WORRIED
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = botMsgId,
          rawText = "${parsed.friendlyBotReply}\n\nHeads up — OpenClaw host (${activeHost.baseUrl}) didn't respond yet (${err.localizedMessage ?: "unreachable"}). Your structured instruction is saved below so you can dispatch it once your host is online!",
          parsedInstruction = parsed.clearInstruction,
          instructionGoal = parsed.goal,
          instructionSteps = parsed.steps,
          expression = BotExpression.WORRIED,
          status = MessageStatus.WARNING,
          takeoverSuggested = parsed.needsHostTakeover,
        )
      }

      _isBusy.value = false
    }
  }

  fun retryDispatchMessage(
    message: BotMessage,
    bot: CompanionBot,
    host: OpenClawHost?,
  ) {
    if (message.parsedInstruction.isNullOrBlank()) {
      val currentMessages = (messagesState.value as? UiState.Success)?.data.orEmpty()
      val msgIdx = currentMessages.indexOfFirst { it.id == message.id }
      val originalUserPrompt = if (msgIdx > 0) {
        currentMessages.subList(0, msgIdx).lastOrNull { it.senderEnum == MessageSender.USER }?.rawText
      } else {
        currentMessages.lastOrNull { it.senderEnum == MessageSender.USER }?.rawText
      }
      if (originalUserPrompt.isNullOrBlank()) {
        _statusBanner.value = "Could not find original prompt to retry."
        return
      }
      viewModelScope.launch {
        _isBusy.value = true
        _liveExpression.value = BotExpression.THINKING
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = message.id,
          rawText = "Retrying... let me translate your request into clear OpenClaw steps!",
          parsedInstruction = null,
          instructionGoal = null,
          instructionSteps = emptyList(),
          expression = BotExpression.THINKING,
          status = MessageStatus.PARSING,
          takeoverSuggested = false,
        )
        val parseResult = refinerService.parseCasualRequest(
          bot = bot,
          casualMessage = originalUserPrompt,
          attachedFiles = message.attachedFiles,
          linkedPaths = message.linkedPaths,
        )
        parseResult.onFailure { err ->
          _liveExpression.value = BotExpression.SORRY
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = message.id,
            rawText = "Sorry! Prompt parsing failed: ${err.localizedMessage ?: "Unknown error"}",
            parsedInstruction = null,
            instructionGoal = "Parsing failed",
            instructionSteps = emptyList(),
            expression = BotExpression.SORRY,
            status = MessageStatus.FAILED,
            takeoverSuggested = false,
          )
          _isBusy.value = false
          return@launch
        }
        val parsed = parseResult.getOrThrow()
        val mode = host?.aiModeEnum ?: AiRoutingMode.HYBRID_GEMINI_OPENCLAW
        if (mode == AiRoutingMode.GEMINI_PARSER_PREVIEW || host == null) {
          val expr = if (parsed.needsHostTakeover) BotExpression.ALERT else parsed.recommendedExpression
          _liveExpression.value = expr
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = message.id,
            rawText = parsed.friendlyBotReply,
            parsedInstruction = parsed.clearInstruction,
            instructionGoal = parsed.goal,
            instructionSteps = parsed.steps,
            expression = expr,
            status = MessageStatus.COMPLETED,
            takeoverSuggested = parsed.needsHostTakeover,
          )
          repository.updateBotPreview(currentUserId, bot.id, parsed.friendlyBotReply)
          _isBusy.value = false
          return@launch
        }

        _liveExpression.value = BotExpression.WORKING
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = message.id,
          rawText = "${parsed.friendlyBotReply}\nSending instructions to ${host.label}...",
          parsedInstruction = parsed.clearInstruction,
          instructionGoal = parsed.goal,
          instructionSteps = parsed.steps,
          expression = BotExpression.WORKING,
          status = MessageStatus.DISPATCHED,
          takeoverSuggested = parsed.needsHostTakeover,
        )
        val dispatchResult = openClawClient.dispatchInstruction(
          host = host,
          bot = bot,
          casualMessage = originalUserPrompt,
          refinedInstruction = parsed.clearInstruction,
          attachedFiles = message.attachedFiles,
          linkedPaths = message.linkedPaths,
          providerKeys = currentProviderKeys(),
        )
        dispatchResult.onSuccess { res ->
          val finalExpr = if (res.takeoverSuggested) BotExpression.ALERT else res.expression
          _liveExpression.value = finalExpr
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = message.id,
            rawText = res.replyText,
            parsedInstruction = parsed.clearInstruction,
            instructionGoal = parsed.goal,
            instructionSteps = parsed.steps,
            expression = finalExpr,
            status = MessageStatus.COMPLETED,
            takeoverSuggested = res.takeoverSuggested || parsed.needsHostTakeover,
          )
          repository.updateBotPreview(currentUserId, bot.id, res.replyText)
        }.onFailure { err ->
          _liveExpression.value = BotExpression.WORRIED
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = message.id,
            rawText = "${parsed.friendlyBotReply}\n\nHeads up — OpenClaw host (${host.baseUrl}) didn't respond yet (${err.localizedMessage ?: "unreachable"}). Your structured instruction is saved below so you can dispatch it once your host is online!",
            parsedInstruction = parsed.clearInstruction,
            instructionGoal = parsed.goal,
            instructionSteps = parsed.steps,
            expression = BotExpression.WORRIED,
            status = MessageStatus.WARNING,
            takeoverSuggested = parsed.needsHostTakeover,
          )
        }
        _isBusy.value = false
      }
      return
    }

    if (host == null) {
      _statusBanner.value = "Configure an OpenClaw host in Host Settings first."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      _liveExpression.value = BotExpression.WORKING
      val res = openClawClient.dispatchInstruction(
        host = host,
        bot = bot,
        casualMessage = message.rawText,
        refinedInstruction = message.parsedInstruction,
        attachedFiles = message.attachedFiles,
        linkedPaths = message.linkedPaths,
        providerKeys = currentProviderKeys(),
      )
      res.onSuccess { reply ->
        _liveExpression.value = reply.expression
        repository.updateMessageExecution(
          userId = currentUserId,
          botId = bot.id,
          messageId = message.id,
          rawText = reply.replyText,
          parsedInstruction = message.parsedInstruction,
          instructionGoal = message.instructionGoal,
          instructionSteps = message.instructionSteps,
          expression = reply.expression,
          status = MessageStatus.COMPLETED,
          takeoverSuggested = reply.takeoverSuggested,
        )
      }.onFailure { err ->
        _liveExpression.value = BotExpression.SORRY
        _statusBanner.value = "Retry failed: ${err.localizedMessage ?: "Unreachable"}"
      }
      _isBusy.value = false
    }
  }

  private suspend fun enrichBotWithTranslatedInstructions(bot: CompanionBot): CompanionBot {
    val translated = refinerService.compileBotProfileForOpenClaw(bot)
    val shouldAdoptFunction = bot.functionPresetEnum == BotFunctionPreset.GENERAL ||
      translated.inferredFunction != BotFunctionPreset.GENERAL
    val finalFn = if (shouldAdoptFunction) translated.inferredFunction else bot.functionPresetEnum
    val finalAgentId = if (
      bot.openClawAgentId.isBlank() ||
      (bot.openClawAgentId == "main" && finalFn != BotFunctionPreset.GENERAL)
    ) {
      translated.suggestedAgentId
    } else {
      bot.openClawAgentId
    }
    val finalRole = if (bot.roleTitle.isBlank() || bot.roleTitle == BotFunctionPreset.GENERAL.displayName) {
      finalFn.displayName
    } else {
      bot.roleTitle
    }
    return bot.copy(
      functionPreset = finalFn.name,
      openClawAgentId = finalAgentId,
      roleTitle = finalRole,
      systemPersona = translated.translatedInstructions,
    )
  }

  fun saveBot(bot: CompanionBot, onDone: (String) -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      val enriched = enrichBotWithTranslatedInstructions(bot)
      val res = if (enriched.id.isBlank()) {
        repository.createBot(currentUserId, enriched)
      } else {
        repository.updateBot(currentUserId, enriched).map { enriched.id }
      }
      res.onSuccess { id ->
        selectBot(id)
        val savedBot = enriched.copy(id = id)
        val activeHost = currentPrimaryHost()
        if (activeHost != null) {
          val syncRes = openClawClient.syncBotProfile(activeHost, savedBot, currentProviderKeys())
          syncRes.onSuccess { info ->
            repository.updateBotSyncStatus(currentUserId, id, true)
            _statusBanner.value = "${savedBot.name} translated as ${savedBot.functionPresetEnum.displayName} & synced to ${activeHost.label} (${info.resolvedModelLabel})."
          }.onFailure {
            repository.updateBotSyncStatus(currentUserId, id, false)
            _statusBanner.value = "${savedBot.name} saved as ${savedBot.functionPresetEnum.displayName}! Host offline—translated instructions will send with every chat."
          }
        } else {
          _statusBanner.value = "${savedBot.name} saved (${savedBot.functionPresetEnum.displayName})! Add an OpenClaw host in Host Settings to register it."
        }
        onDone(id)
      }.onFailure { err ->
        _statusBanner.value = err.localizedMessage ?: "Failed to save bot."
      }
      _isBusy.value = false
    }
  }

  fun syncBotWithOpenClaw(bot: CompanionBot, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
    val activeHost = currentPrimaryHost()
    if (activeHost == null) {
      val msg = "Configure an OpenClaw host in Host Settings first."
      _statusBanner.value = msg
      onResult(false, msg)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val enriched = enrichBotWithTranslatedInstructions(bot)
      if (enriched.id.isNotBlank()) {
        repository.updateBot(currentUserId, enriched)
      }
      val res = openClawClient.syncBotProfile(activeHost, enriched, currentProviderKeys())
      res.onSuccess { info ->
        if (enriched.id.isNotBlank()) {
          repository.updateBotSyncStatus(currentUserId, enriched.id, true)
        }
        val msg = if (info.createdHostAgentRecord) {
          "Translated '${enriched.displayMission}' -> ${enriched.functionPresetEnum.displayName} & registered ${enriched.name} on ${activeHost.label} (${info.resolvedModelLabel})."
        } else {
          "Translated '${enriched.displayMission}' -> ${enriched.functionPresetEnum.displayName} & primed ${enriched.name} on ${activeHost.label} (${info.resolvedModelLabel})."
        }
        _statusBanner.value = msg
        onResult(true, msg)
      }.onFailure { err ->
        if (enriched.id.isNotBlank()) {
          repository.updateBotSyncStatus(currentUserId, enriched.id, false)
        }
        val msg = "Translated to ${enriched.functionPresetEnum.displayName}, but couldn't reach ${activeHost.baseUrl}: ${err.localizedMessage ?: "host offline"}."
        _statusBanner.value = msg
        onResult(false, msg)
      }
      _isBusy.value = false
    }
  }

  fun renameBot(bot: CompanionBot, newName: String) {
    val trimmed = newName.trim().take(40)
    if (trimmed.isBlank() || bot.id.isBlank()) {
      _statusBanner.value = "Please enter a name."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateBot(currentUserId, bot.copy(name = trimmed))
        .onSuccess {
          _statusBanner.value = "Renamed to $trimmed!"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to rename character."
        }
      _isBusy.value = false
    }
  }

  fun deleteBot(botId: String) {
    val targetBot = (botsState.value as? UiState.Success)?.data?.firstOrNull { it.id == botId }
    val activeHost = currentPrimaryHost()
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteBot(currentUserId, botId)
        .onSuccess {
          if (_selectedBotId.value == botId) {
            _selectedBotId.value = ""
          }
          if (activeHost != null && targetBot != null) {
            openClawClient.unregisterBotProfile(activeHost, targetBot)
          }
          _statusBanner.value = "Bot removed."
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not remove bot."
        }
      _isBusy.value = false
    }
  }

  fun updateBotMission(bot: CompanionBot, newMission: String) {
    val trimmed = newMission.trim().take(160)
    if (trimmed.isBlank() || bot.id.isBlank()) {
      _statusBanner.value = "Please enter a mission for ${bot.name}."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val enriched = enrichBotWithTranslatedInstructions(bot.copy(mission = trimmed))
      repository.updateBot(currentUserId, enriched)
        .onSuccess {
          val activeHost = currentPrimaryHost()
          if (activeHost != null) {
            openClawClient.syncBotProfile(activeHost, enriched, currentProviderKeys())
              .onSuccess { repository.updateBotSyncStatus(currentUserId, bot.id, true) }
          }
          _statusBanner.value = "${bot.name}'s mission translated (${enriched.functionPresetEnum.displayName}) & synced!"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to update mission."
        }
      _isBusy.value = false
    }
  }

  fun createChatRoom(
    title: String,
    topic: String,
    botIds: List<String>,
    onCreated: (String) -> Unit = {},
  ) {
    val cleanBots = botIds.filter { it.isNotBlank() }.distinct()
    if (cleanBots.isEmpty()) {
      _statusBanner.value = "Pick at least one bot for the room."
      return
    }
    val room = ChatRoom(
      userId = currentUserId,
      title = title.trim().take(60).ifBlank { "Cafe Room" },
      topic = topic.trim().take(160).ifBlank { "Collaborate & chat together" },
      botIds = cleanBots,
      lastMessagePreview = "Bots arrived at the cafe!",
    )
    viewModelScope.launch {
      _isBusy.value = true
      repository.createChatRoom(currentUserId, room)
        .onSuccess { newId ->
          selectRoom(newId)
          _statusBanner.value = "${room.title} opened!"
          onCreated(newId)
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not create chat room."
        }
      _isBusy.value = false
    }
  }

  fun updateChatRoom(room: ChatRoom) {
    if (room.id.isBlank() || room.botIds.isEmpty()) return
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateChatRoom(currentUserId, room)
        .onSuccess { _statusBanner.value = "Room updated!" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not update room." }
      _isBusy.value = false
    }
  }

  fun deleteChatRoom(roomId: String, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.deleteChatRoom(currentUserId, roomId)
        .onSuccess {
          if (_selectedRoomId.value == roomId) {
            _selectedRoomId.value = ""
          }
          _statusBanner.value = "Chat room removed."
          onDeleted()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not remove chat room." }
      _isBusy.value = false
    }
  }

  fun sendRoomMessage(
    room: ChatRoom,
    participants: List<CompanionBot>,
    history: List<RoomMessage>,
    promptText: String,
    postUserBubble: Boolean = true,
  ) {
    val trimmed = promptText.trim()
    if (trimmed.isBlank() || room.id.isBlank() || participants.isEmpty()) return

    viewModelScope.launch {
      _isBusy.value = true
      _roomActiveExpressions.value = participants.associate { it.id to BotExpression.THINKING }

      if (postUserBubble) {
        repository.addRoomMessage(
          userId = currentUserId,
          roomId = room.id,
          message = RoomMessage(
            userId = currentUserId,
            roomId = room.id,
            senderType = MessageSender.USER.name,
            senderBotName = "You",
            rawText = trimmed,
            expression = BotExpression.HAPPY.name,
          ),
          previewText = "You: $trimmed",
        ).onFailure { err ->
          _roomActiveExpressions.value = participants.associate { it.id to BotExpression.SORRY }
          _statusBanner.value = err.localizedMessage ?: "Failed to post room message."
          _isBusy.value = false
          return@launch
        }
      }

      val aiRes = refinerService.runChatRoomTurn(
        room = room,
        participants = participants,
        recentHistory = history,
        userPrompt = trimmed,
      )

      aiRes.onFailure { err ->
        _roomActiveExpressions.value = participants.associate { it.id to BotExpression.WORRIED }
        _statusBanner.value = err.localizedMessage ?: "Bots couldn't reply right now."
        _isBusy.value = false
        return@launch
      }

      val turns = aiRes.getOrThrow().turns
      turns.forEachIndexed { idx, turn ->
        val speaker = participants.firstOrNull {
          it.name.equals(turn.botName, ignoreCase = true)
        } ?: participants[idx % participants.size]

        _roomActiveExpressions.value = participants.associate { bot ->
          if (bot.id == speaker.id) bot.id to turn.expression
          else bot.id to BotExpression.THINKING
        }

        val mentionPrefix = turn.replyToBotName?.let { "@$it " } ?: ""
        repository.addRoomMessage(
          userId = currentUserId,
          roomId = room.id,
          message = RoomMessage(
            userId = currentUserId,
            roomId = room.id,
            senderType = MessageSender.BOT.name,
            senderBotId = speaker.id,
            senderBotName = speaker.name,
            replyToBotName = turn.replyToBotName,
            rawText = turn.message,
            parsedInstruction = turn.parsedInstruction,
            expression = turn.expression.name,
          ),
          previewText = "$mentionPrefix${speaker.name}: ${turn.message}",
        )
        if (idx < turns.lastIndex) {
          delay(350L)
        }
      }

      _roomActiveExpressions.value = participants.associate { it.id to BotExpression.UH_HUH }
      _isBusy.value = false
    }
  }

  fun dispatchRoomInstructionToHost(
    bot: CompanionBot,
    host: OpenClawHost?,
    instructionText: String,
  ) {
    if (host == null) {
      _statusBanner.value = "Configure an OpenClaw host in Host Settings first."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      _roomActiveExpressions.value = _roomActiveExpressions.value + (bot.id to BotExpression.WORKING)
      val res = openClawClient.dispatchInstruction(
        host = host,
        bot = bot,
        casualMessage = instructionText,
        refinedInstruction = instructionText,
        attachedFiles = emptyList(),
        linkedPaths = emptyList(),
        providerKeys = currentProviderKeys(),
      )
      res.onSuccess { reply ->
        _roomActiveExpressions.value = _roomActiveExpressions.value + (bot.id to reply.expression)
        _statusBanner.value = "Sent to ${host.label}: ${reply.replyText.take(80)}"
      }.onFailure { err ->
        _roomActiveExpressions.value = _roomActiveExpressions.value + (bot.id to BotExpression.WORRIED)
        _statusBanner.value = "OpenClaw host unreachable: ${err.localizedMessage ?: "offline"}"
      }
      _isBusy.value = false
    }
  }

  fun addLinkedFile(
    displayName: String,
    uriOrPath: String,
    linkType: FileLinkType,
    mimeOrExt: String,
    sizeBytes: Long,
    notes: String,
    attachToComposer: Boolean = true,
  ) {
    val file = LinkedFile(
      userId = currentUserId,
      displayName = displayName,
      uriOrPath = uriOrPath,
      linkType = linkType.name,
      mimeOrExt = mimeOrExt,
      sizeBytes = sizeBytes,
      notes = notes,
    )
    viewModelScope.launch {
      repository.addLinkedFile(currentUserId, file)
        .onSuccess { newId ->
          if (attachToComposer) {
            togglePendingAttachment(file.copy(id = newId))
          }
          _statusBanner.value = "Linked $displayName"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not link file."
        }
    }
  }

  fun deleteLinkedFile(fileId: String) {
    viewModelScope.launch {
      repository.deleteLinkedFile(currentUserId, fileId)
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not remove file." }
    }
  }

  fun saveHost(
    host: OpenClawHost,
    veniceApiKey: String = localPrefs.value.veniceApiKey,
    googleApiKey: String = localPrefs.value.googleApiKey,
    onSaved: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      localPrefsStore.setProviderApiKeys(veniceApiKey, googleApiKey)
      repository.saveHost(currentUserId, host)
        .onSuccess {
          _statusBanner.value = "${host.label} & default LLM (${host.defaultLlmProviderEnum.displayName}) saved!"
          onSaved()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to save host."
        }
      _isBusy.value = false
    }
  }

  private fun currentPrimaryHost(): OpenClawHost? =
    (hostsState.value as? UiState.Success)?.data?.firstOrNull()

  private fun currentProviderKeys(): LocalProviderKeys {
    val prefs = localPrefs.value
    return LocalProviderKeys(
      veniceApiKey = prefs.veniceApiKey,
      googleApiKey = prefs.googleApiKey,
    )
  }

  fun deleteHost(hostId: String) {
    viewModelScope.launch {
      repository.deleteHost(currentUserId, hostId)
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to delete host." }
    }
  }

  fun pingHost(host: OpenClawHost) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = openClawClient.pingHost(host)
      _pingResult.value = res
      val newStatus = if (res.reachable) HostStatus.ONLINE else HostStatus.OFFLINE
      if (host.id.isNotBlank()) {
        repository.updateHostStatus(currentUserId, host.id, newStatus)
      }
      _controlLog.value = (listOf("[PING] ${res.detail}") + _controlLog.value).take(25)
      _isBusy.value = false
    }
  }

  fun sendHostControlAction(host: OpenClawHost?, action: HostControlAction, input: String = "") {
    if (host == null) {
      _statusBanner.value = "Add an OpenClaw host first."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      if (action == HostControlAction.TAKEOVER_START) {
        localPrefsStore.setTakeoverActive(true)
        _liveExpression.value = BotExpression.ALERT
      } else if (action == HostControlAction.TAKEOVER_RELEASE) {
        localPrefsStore.setTakeoverActive(false)
        _liveExpression.value = BotExpression.HAPPY
      }

      val res = openClawClient.sendControlAction(host, action, input)
      res.onSuccess { ok ->
        _controlLog.value = (listOf("[OK] ${action.label}: ${ok.summary}") + _controlLog.value).take(25)
      }.onFailure { err ->
        _controlLog.value = (listOf("[HOST] ${action.label}: ${err.localizedMessage ?: "Unreachable"}") + _controlLog.value).take(25)
        _statusBanner.value = err.localizedMessage ?: "Host control failed."
      }
      _isBusy.value = false
    }
  }

  private fun <T> unwrapFirestoreError(label: String, error: Throwable): UiState<T> {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: error
    Log.w(TAG, "Error observing $label", rootError)
    return UiState.Error(rootError.message ?: "Failed to observe $label")
  }

  private companion object {
    const val TAG = "ClawPalViewModel"
  }
}
