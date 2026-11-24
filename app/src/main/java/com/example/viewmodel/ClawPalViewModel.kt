package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.PromptRefinerService
import com.example.data.files.ContextDocCache
import com.example.data.files.DeviceAttachmentReader
import com.example.data.firebase.ClawPalRepository
import com.example.data.local.LocalPrefsStore
import com.example.data.local.LocalUiPrefs
import com.example.data.openclaw.BridgeFolderStatus
import com.example.data.openclaw.CafeOrchestrator
import com.example.data.openclaw.DriveBridgeManager
import com.example.data.openclaw.HostControlAction
import com.example.data.openclaw.LocalProviderKeys
import com.example.data.openclaw.OpenClawClient
import com.example.data.openclaw.OpenClawDispatchResult
import com.example.data.openclaw.OpenClawFormattedException
import com.example.data.openclaw.OpenClawPingResult
import com.example.model.AiRoutingMode
import com.example.model.BackgroundAgentTask
import com.example.model.BackgroundTaskStatus
import com.example.model.BotExpression
import com.example.model.BotFunctionPreset
import com.example.model.StarterBots
import com.example.model.BotMessage
import com.example.model.CafeSession
import com.example.model.CafeSessionStatus
import com.example.model.CafeEntryKind
import com.example.model.CafeSpeakerRole
import com.example.model.CafeTranscriptEntry
import com.example.model.CaptainContextDocument
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.ContextChatMessage
import com.example.model.ContextChatThread
import com.example.model.ContextDocStatus
import com.example.model.FileLinkType
import com.example.model.GeneralChatMessage
import com.example.model.GeneralChatThread
import com.example.model.GeneratedMediaItem
import com.example.model.GeneratedMediaType
import com.example.model.HostConnectionMode
import com.example.model.HostStatus
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.RoomMessage
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import com.example.model.WheelLlmOption
import com.example.util.WakeLockHelper
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class ConnectionFailureBanner(
  val host: OpenClawHost,
  val failedMode: HostConnectionMode,
  val alternativeMode: HostConnectionMode,
  val alternativeHasUrl: Boolean,
  val cleanError: String,
  val retryAction: (OpenClawHost) -> Unit,
)

private sealed interface ActiveMessageTarget {
  data class BotTarget(
    val botId: String,
    val messageId: String,
    val parsedInstruction: String?,
    val instructionGoal: String?,
    val instructionSteps: List<String>,
  ) : ActiveMessageTarget

  data class GeneralTarget(
    val chatId: String,
    val messageId: String,
    val modelLabel: String,
  ) : ActiveMessageTarget

  data class ContextTarget(
    val chatId: String,
    val messageId: String,
    val modelLabel: String,
  ) : ActiveMessageTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClawPalViewModel(
  private val repository: ClawPalRepository,
  private val refinerService: PromptRefinerService,
  private val openClawClient: OpenClawClient,
  private val localPrefsStore: LocalPrefsStore,
  private val attachmentReader: DeviceAttachmentReader,
  val currentUserId: String,
  private val wakeLockHelper: WakeLockHelper? = null,
  private val driveBridgeManager: DriveBridgeManager? = null,
  private val contextDocCache: ContextDocCache? = null,
) : ViewModel() {

  private val _bridgeStatus = MutableStateFlow<BridgeFolderStatus?>(null)
  val bridgeStatus: StateFlow<BridgeFolderStatus?> = _bridgeStatus.asStateFlow()

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

  val generatedMediaState: StateFlow<UiState<List<GeneratedMediaItem>>> = repository.observeGeneratedMedia(currentUserId)
    .map<List<GeneratedMediaItem>, UiState<List<GeneratedMediaItem>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("generated media", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val chatRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeChatRooms(currentUserId)
    .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("chat rooms", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val generalChatsState: StateFlow<UiState<List<GeneralChatThread>>> = repository.observeGeneralChats(currentUserId)
    .map<List<GeneralChatThread>, UiState<List<GeneralChatThread>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("general chats", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val contextChatsState: StateFlow<UiState<List<ContextChatThread>>> = repository.observeContextChats(currentUserId)
    .map<List<ContextChatThread>, UiState<List<ContextChatThread>>> { UiState.Success(it) }
    .catch { error -> emit(unwrapFirestoreError("captain context chats", error)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  private val _selectedBotId = MutableStateFlow("")
  val selectedBotId: StateFlow<String> = _selectedBotId.asStateFlow()

  private val _selectedRoomId = MutableStateFlow("")
  val selectedRoomId: StateFlow<String> = _selectedRoomId.asStateFlow()

  private val _selectedGeneralChatId = MutableStateFlow("")
  val selectedGeneralChatId: StateFlow<String> = _selectedGeneralChatId.asStateFlow()

  private val _selectedContextChatId = MutableStateFlow("")
  val selectedContextChatId: StateFlow<String> = _selectedContextChatId.asStateFlow()

  private val _selectedWheelModel = MutableStateFlow(WheelLlmOption.OPENCLAW_DEFAULT)
  val selectedWheelModel: StateFlow<WheelLlmOption> = _selectedWheelModel.asStateFlow()

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

  val generalMessagesState: StateFlow<UiState<List<GeneralChatMessage>>> = _selectedGeneralChatId
    .flatMapLatest { chatId ->
      if (chatId.isBlank()) {
        flowOf(UiState.Success(emptyList()))
      } else {
        repository.observeGeneralMessages(currentUserId, chatId)
          .map<List<GeneralChatMessage>, UiState<List<GeneralChatMessage>>> { UiState.Success(it) }
          .catch { error -> emit(unwrapFirestoreError("general messages", error)) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  val contextMessagesState: StateFlow<UiState<List<ContextChatMessage>>> = _selectedContextChatId
    .flatMapLatest { chatId ->
      if (chatId.isBlank()) {
        flowOf(UiState.Success(emptyList()))
      } else {
        repository.observeContextMessages(currentUserId, chatId)
          .map<List<ContextChatMessage>, UiState<List<ContextChatMessage>>> { UiState.Success(it) }
          .catch { error -> emit(unwrapFirestoreError("captain context messages", error)) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

  // Concurrent per-Bot Dude lanes (isolated execution so one bot never blocks another)
  private val botJobs = ConcurrentHashMap<String, Job>()
  private val botElapsedJobs = ConcurrentHashMap<String, Job>()
  private val botTargets = ConcurrentHashMap<String, ActiveMessageTarget.BotTarget>()
  private val _busyBotIds = MutableStateFlow<Set<String>>(emptySet())
  val busyBotIds: StateFlow<Set<String>> = _busyBotIds.asStateFlow()

  private val _botStreamingPreviews = MutableStateFlow<Map<String, String>>(emptyMap())
  private val _botElapsedSecs = MutableStateFlow<Map<String, Int>>(emptyMap())
  private val _botExpressions = MutableStateFlow<Map<String, BotExpression>>(emptyMap())

  val isSelectedBotBusy: StateFlow<Boolean> = combine(_selectedBotId, _busyBotIds) { id, set ->
    id.isNotBlank() && id in set
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

  val selectedBotStreamingPreview: StateFlow<String?> = combine(_selectedBotId, _botStreamingPreviews) { id, map ->
    map[id]
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val selectedBotElapsedSec: StateFlow<Int> = combine(_selectedBotId, _botElapsedSecs) { id, map ->
    map[id] ?: 0
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

  val liveExpression: StateFlow<BotExpression> = combine(_selectedBotId, _botExpressions) { id, map ->
    map[id] ?: BotExpression.HAPPY
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), BotExpression.HAPPY)

  // Concurrent per-General-Chat lanes
  private val generalJobs = ConcurrentHashMap<String, Job>()
  private val generalElapsedJobs = ConcurrentHashMap<String, Job>()
  private val generalTargets = ConcurrentHashMap<String, ActiveMessageTarget.GeneralTarget>()
  private val _busyGeneralChatIds = MutableStateFlow<Set<String>>(emptySet())
  private val _generalStreamingPreviews = MutableStateFlow<Map<String, String>>(emptyMap())
  private val _generalElapsedSecs = MutableStateFlow<Map<String, Int>>(emptyMap())

  val isSelectedGeneralBusy: StateFlow<Boolean> = combine(_selectedGeneralChatId, _busyGeneralChatIds) { id, set ->
    id.isNotBlank() && id in set
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

  val generalStreamingPreview: StateFlow<String?> = combine(_selectedGeneralChatId, _generalStreamingPreviews) { id, map ->
    map[id]
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val generalElapsedSec: StateFlow<Int> = combine(_selectedGeneralChatId, _generalElapsedSecs) { id, map ->
    map[id] ?: 0
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

  // Concurrent per-Captain-Context-Case lanes + Document Vault state
  private val contextJobs = ConcurrentHashMap<String, Job>()
  private val contextElapsedJobs = ConcurrentHashMap<String, Job>()
  private val contextTargets = ConcurrentHashMap<String, ActiveMessageTarget.ContextTarget>()
  private val _busyContextChatIds = MutableStateFlow<Set<String>>(emptySet())
  private val _contextStreamingPreviews = MutableStateFlow<Map<String, String>>(emptyMap())
  private val _contextElapsedSecs = MutableStateFlow<Map<String, Int>>(emptyMap())
  private val _contextDocsByChatId = MutableStateFlow<Map<String, List<CaptainContextDocument>>>(emptyMap())

  val isSelectedContextBusy: StateFlow<Boolean> = combine(_selectedContextChatId, _busyContextChatIds) { id, set ->
    id.isNotBlank() && id in set
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

  val contextStreamingPreview: StateFlow<String?> = combine(_selectedContextChatId, _contextStreamingPreviews) { id, map ->
    map[id]
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  val contextElapsedSec: StateFlow<Int> = combine(_selectedContextChatId, _contextElapsedSecs) { id, map ->
    map[id] ?: 0
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

  val activeContextDocs: StateFlow<List<CaptainContextDocument>> = combine(_selectedContextChatId, _contextDocsByChatId) { id, map ->
    map[id.ifBlank { "__draft__" }].orEmpty()
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  // Isolated background sub-agent tasks (`agent:<bot_dude_id>:spawn_<taskId>`)
  private val _backgroundTasks = MutableStateFlow<List<BackgroundAgentTask>>(emptyList())
  val backgroundTasks: StateFlow<List<BackgroundAgentTask>> = _backgroundTasks.asStateFlow()
  private val backgroundTaskJobs = ConcurrentHashMap<String, Job>()
  private val backgroundTaskElapsedJobs = ConcurrentHashMap<String, Job>()

  // Cafe Mode A2A Orchestrator
  val cafeOrchestrator = CafeOrchestrator()
  val cafeSessions: StateFlow<Map<String, CafeSession>> = cafeOrchestrator.sessions

  val isRoomBusy: StateFlow<Boolean> = combine(_selectedRoomId, cafeSessions) { roomId, map ->
    roomId.isNotBlank() && map[roomId]?.status == CafeSessionStatus.RUNNING
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

  private val _roomActiveExpressions = MutableStateFlow<Map<String, BotExpression>>(emptyMap())
  val roomActiveExpressions: StateFlow<Map<String, BotExpression>> = _roomActiveExpressions.asStateFlow()

  private val _nonChatBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = combine(
    _busyBotIds,
    _busyGeneralChatIds,
    _busyContextChatIds,
    cafeSessions,
    _nonChatBusy,
  ) { botsBusy, generalBusy, contextBusy, roomsMap, nonChat ->
    botsBusy.isNotEmpty() ||
      generalBusy.isNotEmpty() ||
      contextBusy.isNotEmpty() ||
      roomsMap.values.any { it.status == CafeSessionStatus.RUNNING } ||
      nonChat
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

  private val _statusBanner = MutableStateFlow<String?>(null)
  val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

  private val _pendingAttachments = MutableStateFlow<List<LinkedFile>>(emptyList())
  val pendingAttachments: StateFlow<List<LinkedFile>> = _pendingAttachments.asStateFlow()

  private val _pingResult = MutableStateFlow<OpenClawPingResult?>(null)
  val pingResult: StateFlow<OpenClawPingResult?> = _pingResult.asStateFlow()

  private val _controlLog = MutableStateFlow<List<String>>(emptyList())
  val controlLog: StateFlow<List<String>> = _controlLog.asStateFlow()

  private val _isFetchingVeniceModels = MutableStateFlow(false)
  val isFetchingVeniceModels: StateFlow<Boolean> = _isFetchingVeniceModels.asStateFlow()

  private val _fetchedVeniceModels = MutableStateFlow<List<VeniceModelInfo>>(emptyList())
  val veniceLiveModels: StateFlow<List<VeniceModelInfo>> = combine(
    _fetchedVeniceModels,
    localPrefs,
  ) { fetched, prefs ->
    (VeniceModelCatalog.popularPresets + fetched + prefs.cachedVeniceModels).distinctBy { it.id }
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000L),
    VeniceModelCatalog.popularPresets,
  )

  private val _pendingChatDraft = MutableStateFlow<String?>(null)
  val pendingChatDraft: StateFlow<String?> = _pendingChatDraft.asStateFlow()

  private val _connectionFailureBanner = MutableStateFlow<ConnectionFailureBanner?>(null)
  val connectionFailureBanner: StateFlow<ConnectionFailureBanner?> = _connectionFailureBanner.asStateFlow()

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
      repository.observeUserVenicePreferences(currentUserId)
        .catch { /* keep local defaults if offline */ }
        .collect { remote ->
          if (remote != null) {
            val cur = localPrefs.value
            val keyToSave = remote.veniceApiKeyOverride.ifBlank { cur.veniceApiKey }
            localPrefsStore.setProviderApiKeys(
              veniceKey = keyToSave,
              googleKey = cur.googleApiKey,
              globalVeniceModel = remote.globalVeniceModel,
            )
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
    viewModelScope.launch {
      combine(
        _selectedRoomId,
        chatRoomsState,
        botsState,
        roomMessagesState,
      ) { roomId, roomsUi, botsUi, msgsUi ->
        val rooms = (roomsUi as? UiState.Success)?.data.orEmpty()
        val room = rooms.firstOrNull { it.id == roomId }
        val bots = (botsUi as? UiState.Success)?.data.orEmpty()
        val msgs = (msgsUi as? UiState.Success)?.data.orEmpty()
        Triple(room, bots, msgs)
      }.collect { (room, bots, msgs) ->
        if (room != null && bots.isNotEmpty()) {
          val byId = bots.associateBy { it.id }
          val participants = room.botIds.mapNotNull { byId[it] }.ifEmpty { bots.take(2) }
          val host = currentPrimaryHost()
          val providerKeys = currentProviderKeys()
          val dudes = participants.map { bot ->
            val resolvedModel = if (host != null) {
              val llm = openClawClient.resolveEffectiveLlm(bot, host, _selectedWheelModel.value, providerKeys)
              llm.modelId.ifBlank { llm.provider.displayName }
            } else {
              bot.llmModel.ifBlank { bot.llmProviderEnum.displayName }
            }
            bot.toBotDude(resolvedModelId = resolvedModel, resolvedProvider = bot.llmProviderEnum)
          }
          val entries = msgs.map { it.toCafeTranscriptEntry() }
          cafeOrchestrator.syncInitialTranscript(room, dudes, entries)
        }
      }
    }
    viewModelScope.launch {
      combine(_selectedRoomId, cafeSessions) { roomId, map ->
        map[roomId]
      }.collect { session ->
        if (session == null) return@collect
        when (session.status) {
          CafeSessionStatus.RUNNING -> {
            val activeId = session.activeSpeakerBotId
            _roomActiveExpressions.value = session.activeBotDudes.associate { dude ->
              if (dude.id == activeId) dude.id to BotExpression.WORKING
              else dude.id to BotExpression.THINKING
            }
          }
          CafeSessionStatus.COMPLETED -> {
            _roomActiveExpressions.value = session.activeBotDudes.associate { it.id to BotExpression.UH_HUH }
          }
          CafeSessionStatus.PAUSED -> {
            _roomActiveExpressions.value = session.activeBotDudes.associate { it.id to BotExpression.WORRIED }
          }
          CafeSessionStatus.STOPPED -> {
            _roomActiveExpressions.value = session.activeBotDudes.associate { it.id to BotExpression.SORRY }
          }
          CafeSessionStatus.IDLE -> Unit
        }
      }
    }
    viewModelScope.launch {
      combine(_selectedContextChatId, contextChatsState) { chatId, chatsUi ->
        val list = (chatsUi as? UiState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == chatId }
      }.collect { thread ->
        if (thread != null) {
          hydrateDocsForThread(thread)
        }
      }
    }
  }

  fun selectBot(botId: String) {
    _selectedBotId.value = botId
    if (botId !in _busyBotIds.value) {
      setBotExpression(botId, BotExpression.HAPPY)
    }
    viewModelScope.launch {
      localPrefsStore.setSelectedBotId(botId)
    }
  }

  /** Persists the in-progress composer text for a bot so it survives navigation. */
  fun saveChatDraft(botId: String, text: String) {
    if (botId.isBlank()) return
    viewModelScope.launch {
      localPrefsStore.setChatDraft(botId, text)
    }
  }

  fun selectRoom(roomId: String) {
    _selectedRoomId.value = roomId
    if (cafeSessions.value[roomId]?.status != CafeSessionStatus.RUNNING) {
      _roomActiveExpressions.value = emptyMap()
    }
  }

  fun selectGeneralChat(chatId: String) {
    _selectedGeneralChatId.value = chatId
    val thread = (generalChatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
    if (thread != null) {
      _selectedWheelModel.value = thread.wheelModelEnum
    }
  }

  fun selectWheelModel(option: WheelLlmOption, activeGeneralChatId: String? = null) {
    _selectedWheelModel.value = option
    val chatId = activeGeneralChatId?.takeIf { it.isNotBlank() } ?: _selectedGeneralChatId.value
    if (chatId.isNotBlank()) {
      viewModelScope.launch {
        repository.updateGeneralChatWheel(currentUserId, chatId, option)
      }
    }
  }

  fun selectContextChat(chatId: String) {
    _selectedContextChatId.value = chatId
    val thread = (contextChatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
    if (thread != null) {
      _selectedWheelModel.value = thread.wheelModelEnum
      hydrateDocsForThread(thread)
    }
  }

  fun selectContextWheelModel(option: WheelLlmOption, activeContextChatId: String? = null) {
    _selectedWheelModel.value = option
    val chatId = activeContextChatId?.takeIf { it.isNotBlank() } ?: _selectedContextChatId.value
    if (chatId.isNotBlank()) {
      viewModelScope.launch {
        repository.updateContextChatWheel(currentUserId, chatId, option)
      }
    }
  }

  fun setLiveExpression(expression: BotExpression) {
    val botId = _selectedBotId.value
    if (botId.isNotBlank()) {
      setBotExpression(botId, expression)
    }
  }

  private fun setBotExpression(botId: String, expression: BotExpression) {
    if (botId.isBlank()) return
    _botExpressions.update { it + (botId to expression) }
  }

  fun clearBanner() {
    _statusBanner.value = null
  }

  fun showBanner(message: String) {
    _statusBanner.value = message
  }

  fun dismissConnectionFailureBanner() {
    _connectionFailureBanner.value = null
  }

  fun toggleHostConnectionMode(host: OpenClawHost, onSwitched: (OpenClawHost) -> Unit = {}) {
    val current = host.connectionModeEnum
    val next = if (current == HostConnectionMode.LAN) HostConnectionMode.TAILSCALE else HostConnectionMode.LAN
    if (next == HostConnectionMode.TAILSCALE && host.effectiveTailscaleUrl.isBlank()) {
      _statusBanner.value = "Add your Outside Mode (Tailscale) URL in Host Settings first."
      return
    }
    if (next == HostConnectionMode.LAN && host.effectiveLanUrl.isBlank()) {
      _statusBanner.value = "Add your Home Mode (LAN) URL in Host Settings first."
      return
    }
    viewModelScope.launch {
      repository.updateHostConnectionMode(currentUserId, host, next)
        .onSuccess { updated ->
          _connectionFailureBanner.value = null
          _statusBanner.value = "Switched to ${next.displayName} (${updated.effectiveBaseUrl})"
          onSwitched(updated)
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to switch connection mode."
        }
    }
  }

  fun abortBotRequest(botId: String = _selectedBotId.value) {
    if (botId.isBlank()) return
    val bot = (botsState.value as? UiState.Success)?.data?.firstOrNull { it.id == botId }
    bot?.effectivePrimarySessionKey?.let { openClawClient.abortSession(it) }
    botJobs.remove(botId)?.cancel()
    stopBotElapsedTimer(botId)
    _busyBotIds.update { it - botId }
    setBotExpression(botId, BotExpression.SORRY)
    val target = botTargets.remove(botId)
    if (target != null) {
      viewModelScope.launch {
        withContext(NonCancellable) {
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = target.botId,
            messageId = target.messageId,
            rawText = "Aborted by user.",
            parsedInstruction = target.parsedInstruction,
            instructionGoal = target.instructionGoal,
            instructionSteps = target.instructionSteps,
            expression = BotExpression.SORRY,
            status = MessageStatus.WARNING,
            takeoverSuggested = false,
          )
        }
      }
    }
    _controlLog.value = (listOf("[ABORT] Cancelled ${bot?.name ?: botId} active run cleanly.") + _controlLog.value).take(25)
    _statusBanner.value = "Cancelled ${bot?.name ?: "bot"} request."
  }

  fun abortGeneralRequest(chatId: String = _selectedGeneralChatId.value) {
    if (chatId.isBlank()) return
    openClawClient.abortSession("conv:gen_$chatId")
    generalJobs.remove(chatId)?.cancel()
    stopGeneralElapsedTimer(chatId)
    _busyGeneralChatIds.update { it - chatId }
    val target = generalTargets.remove(chatId)
    if (target != null) {
      viewModelScope.launch {
        withContext(NonCancellable) {
          repository.updateGeneralMessage(
            userId = currentUserId,
            chatId = target.chatId,
            messageId = target.messageId,
            rawText = "Aborted by user.",
            modelLabel = target.modelLabel,
            status = MessageStatus.WARNING,
          )
        }
      }
    }
    _controlLog.value = (listOf("[ABORT] Cancelled General Chat stream cleanly.") + _controlLog.value).take(25)
    _statusBanner.value = "General Chat request aborted."
  }

  fun abortBackgroundTask(taskId: String) {
    val task = _backgroundTasks.value.firstOrNull { it.id == taskId } ?: return
    openClawClient.abortSession(task.subSessionKey)
    backgroundTaskJobs.remove(taskId)?.cancel()
    backgroundTaskElapsedJobs.remove(taskId)?.cancel()
    _backgroundTasks.update { list ->
      list.map { item ->
        if (item.id == taskId && item.status == BackgroundTaskStatus.RUNNING) {
          item.copy(status = BackgroundTaskStatus.ABORTED)
        } else item
      }
    }
    _controlLog.value = (listOf("[ABORT] Stopped sub-agent ${task.subSessionKey}.") + _controlLog.value).take(25)
  }

  fun abortContextRequest(chatId: String = _selectedContextChatId.value) {
    if (chatId.isBlank()) return
    openClawClient.abortSession("conv:ctx_$chatId")
    contextJobs.remove(chatId)?.cancel()
    stopContextElapsedTimer(chatId)
    _busyContextChatIds.update { it - chatId }
    val target = contextTargets.remove(chatId)
    if (target != null) {
      viewModelScope.launch {
        withContext(NonCancellable) {
          repository.updateContextMessage(
            userId = currentUserId,
            chatId = target.chatId,
            messageId = target.messageId,
            rawText = "Aborted by user.",
            modelLabel = target.modelLabel,
            status = MessageStatus.WARNING,
          )
        }
      }
    }
    _controlLog.value = (listOf("[ABORT] Cancelled Captain Context stream cleanly.") + _controlLog.value).take(25)
    _statusBanner.value = "Captain Context request aborted."
  }

  fun abortActiveRequest() {
    openClawClient.abortActiveRequest()
    botJobs.keys.toList().forEach { abortBotRequest(it) }
    generalJobs.keys.toList().forEach { abortGeneralRequest(it) }
    contextJobs.keys.toList().forEach { abortContextRequest(it) }
    backgroundTaskJobs.keys.toList().forEach { abortBackgroundTask(it) }
    cafeOrchestrator.abortAll { sessionKey -> openClawClient.abortSession(sessionKey) }
    wakeLockHelper?.forceReleaseAll()
    _controlLog.value = (listOf("[ABORT] Cancelled all active OpenClaw requests cleanly.") + _controlLog.value).take(25)
    _statusBanner.value = "Active requests aborted."
  }

  private fun startBotElapsedTimer(botId: String) {
    botElapsedJobs.remove(botId)?.cancel()
    _botElapsedSecs.update { it + (botId to 0) }
    _botStreamingPreviews.update { it - botId }
    botElapsedJobs[botId] = viewModelScope.launch {
      while (true) {
        delay(1000L)
        _botElapsedSecs.update { map -> map + (botId to ((map[botId] ?: 0) + 1)) }
      }
    }
  }

  private fun stopBotElapsedTimer(botId: String) {
    botElapsedJobs.remove(botId)?.cancel()
    _botElapsedSecs.update { it - botId }
    _botStreamingPreviews.update { it - botId }
  }

  private fun startGeneralElapsedTimer(chatId: String) {
    generalElapsedJobs.remove(chatId)?.cancel()
    _generalElapsedSecs.update { it + (chatId to 0) }
    _generalStreamingPreviews.update { it - chatId }
    generalElapsedJobs[chatId] = viewModelScope.launch {
      while (true) {
        delay(1000L)
        _generalElapsedSecs.update { map -> map + (chatId to ((map[chatId] ?: 0) + 1)) }
      }
    }
  }

  private fun stopGeneralElapsedTimer(chatId: String) {
    generalElapsedJobs.remove(chatId)?.cancel()
    _generalElapsedSecs.update { it - chatId }
    _generalStreamingPreviews.update { it - chatId }
  }

  private suspend fun <T> withBotRequestLifecycle(botId: String, block: suspend () -> T): T {
    startBotElapsedTimer(botId)
    return try {
      if (wakeLockHelper != null) {
        wakeLockHelper.withPartialWakeLock { block() }
      } else {
        block()
      }
    } finally {
      stopBotElapsedTimer(botId)
      botTargets.remove(botId)
    }
  }

  private suspend fun <T> withGeneralRequestLifecycle(chatId: String, block: suspend () -> T): T {
    startGeneralElapsedTimer(chatId)
    return try {
      if (wakeLockHelper != null) {
        wakeLockHelper.withPartialWakeLock { block() }
      } else {
        block()
      }
    } finally {
      stopGeneralElapsedTimer(chatId)
      generalTargets.remove(chatId)
    }
  }

  private fun startContextElapsedTimer(chatId: String) {
    contextElapsedJobs.remove(chatId)?.cancel()
    _contextElapsedSecs.update { it + (chatId to 0) }
    _contextStreamingPreviews.update { it - chatId }
    contextElapsedJobs[chatId] = viewModelScope.launch {
      while (true) {
        delay(1000L)
        _contextElapsedSecs.update { map -> map + (chatId to ((map[chatId] ?: 0) + 1)) }
      }
    }
  }

  private fun stopContextElapsedTimer(chatId: String) {
    contextElapsedJobs.remove(chatId)?.cancel()
    _contextElapsedSecs.update { it - chatId }
    _contextStreamingPreviews.update { it - chatId }
  }

  private suspend fun <T> withContextRequestLifecycle(chatId: String, block: suspend () -> T): T {
    startContextElapsedTimer(chatId)
    return try {
      if (wakeLockHelper != null) {
        wakeLockHelper.withPartialWakeLock { block() }
      } else {
        block()
      }
    } finally {
      stopContextElapsedTimer(chatId)
      contextTargets.remove(chatId)
    }
  }

  private fun maybeShowFastConnectBanner(
    err: Throwable,
    host: OpenClawHost,
    retryWithHost: (OpenClawHost) -> Unit,
  ): String {
    val cleanError = OpenClawClient.cleanRawErrorText(err.localizedMessage, 0, host.effectiveBaseUrl)
    val isFastFail = (err as? OpenClawFormattedException)?.isFastConnectFailure == true
    if (isFastFail) {
      val failedMode = host.connectionModeEnum
      val altMode = if (failedMode == HostConnectionMode.LAN) HostConnectionMode.TAILSCALE else HostConnectionMode.LAN
      val hasAlt = if (altMode == HostConnectionMode.TAILSCALE) {
        host.effectiveTailscaleUrl.isNotBlank()
      } else {
        host.effectiveLanUrl.isNotBlank()
      }
      _connectionFailureBanner.value = ConnectionFailureBanner(
        host = host,
        failedMode = failedMode,
        alternativeMode = altMode,
        alternativeHasUrl = hasAlt,
        cleanError = cleanError,
        retryAction = retryWithHost,
      )
    }
    return cleanError
  }

  fun queueMediaDraftToBotChat(item: GeneratedMediaItem) {
    _pendingChatDraft.value = if (item.mediaTypeEnum == GeneratedMediaType.IMAGE) {
      "Animate this image with lively cinematic motion: ${item.mediaUrl}"
    } else {
      "Check out this generated video clip: ${item.mediaUrl}"
    }
  }

  fun consumePendingChatDraft(): String? {
    val draft = _pendingChatDraft.value
    _pendingChatDraft.value = null
    return draft
  }

  fun fetchLiveVeniceModels(apiKeyOverride: String = localPrefs.value.veniceApiKey) {
    if (_isFetchingVeniceModels.value) return
    viewModelScope.launch {
      _isFetchingVeniceModels.value = true
      val key = apiKeyOverride.ifBlank { localPrefs.value.veniceApiKey }
      openClawClient.fetchVeniceLiveModels(key)
        .onSuccess { live ->
          _fetchedVeniceModels.value = live
          localPrefsStore.setCachedVeniceModels(live)
          _statusBanner.value = "Loaded ${live.size} live Venice.ai models!"
        }
        .onFailure { err ->
          _statusBanner.value = "Could not fetch Venice models: ${err.localizedMessage ?: "Network error"}"
        }
      _isFetchingVeniceModels.value = false
    }
  }

  fun deleteGeneratedMedia(mediaId: String) {
    viewModelScope.launch {
      repository.deleteGeneratedMedia(currentUserId, mediaId)
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Failed to remove media item." }
    }
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
    wheelOverride: WheelLlmOption? = _selectedWheelModel.value,
  ) {
    if (localPrefs.value.hostPaused) {
      _statusBanner.value = "Host queue is paused — tap Resume in Take Over PC before sending new automated tasks."
      return
    }
    if (botJobs[bot.id]?.isActive == true) {
      // Bot Dude's primary lane is already busy; automatically spawn an isolated background sub-agent
      spawnBackgroundTaskForBot(rawText, bot, activeHost, wheelOverride)
      return
    }
    val attachmentsSnapshot = _pendingAttachments.value
    val trimmed = rawText.trim().ifBlank {
      if (attachmentsSnapshot.isNotEmpty()) "Please analyze the attached file(s)." else ""
    }
    if (trimmed.isBlank() || bot.id.isBlank()) return
    val attachedLabels = attachmentsSnapshot
      .filter { it.linkTypeEnum == FileLinkType.ANDROID_ATTACHMENT }
      .map { it.displayName }
    val linkedPathLabels = attachmentsSnapshot
      .filter { it.linkTypeEnum != FileLinkType.ANDROID_ATTACHMENT }
      .map { it.uriOrPath }
    val botHistory = if (bot.id == _selectedBotId.value) {
      (messagesState.value as? UiState.Success)?.data.orEmpty()
    } else emptyList()

    _pendingAttachments.value = emptyList()
    _connectionFailureBanner.value = null

    val job = viewModelScope.launch {
      _busyBotIds.update { it + bot.id }
      setBotExpression(bot.id, BotExpression.THINKING)

      try {
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
            setBotExpression(bot.id, BotExpression.SORRY)
            _statusBanner.value = it.localizedMessage ?: "Could not save message."
            return@launch
          }

        val botMsgId = repository.addMessage(
          currentUserId,
          bot.id,
          BotMessage(
            userId = currentUserId,
            botId = bot.id,
            sender = MessageSender.BOT.name,
            rawText = "Thinking / running tools on host...",
            expression = BotExpression.THINKING.name,
            status = MessageStatus.PARSING.name,
            attachedFiles = attachedLabels,
            linkedPaths = linkedPathLabels,
          ),
        ).getOrElse { err ->
          setBotExpression(bot.id, BotExpression.SORRY)
          _statusBanner.value = err.localizedMessage ?: "Failed to create bot response."
          return@launch
        }

        val mode = activeHost?.aiModeEnum ?: AiRoutingMode.HYBRID_GEMINI_OPENCLAW
        val providerKeys = currentProviderKeys()
        val attachmentPayloads = if (attachmentsSnapshot.isNotEmpty()) {
          attachmentReader.preparePayloads(attachmentsSnapshot, activeHost, trimmed)
        } else {
          emptyList()
        }

        if (mode == AiRoutingMode.OPENCLAW_ONLY && activeHost != null) {
          setBotExpression(bot.id, BotExpression.WORKING)
          botTargets[bot.id] = ActiveMessageTarget.BotTarget(bot.id, botMsgId, null, "Dispatched to OpenClaw", emptyList())
          val dispatchRes = withBotRequestLifecycle(bot.id) {
            openClawClient.dispatchInstruction(
              host = activeHost,
              bot = bot,
              casualMessage = trimmed,
              refinedInstruction = null,
              attachedFiles = attachedLabels,
              linkedPaths = linkedPathLabels,
              providerKeys = providerKeys,
              wheelOverride = wheelOverride,
              attachmentPayloads = attachmentPayloads,
              history = botHistory,
              onPartialText = { partial ->
                _botStreamingPreviews.update { it + (bot.id to partial) }
              },
            )
          }
          dispatchRes.onSuccess { res ->
            setBotExpression(bot.id, res.expression)
            repository.updateMessageExecution(
              userId = currentUserId,
              botId = bot.id,
              messageId = botMsgId,
              rawText = res.replyText,
              parsedInstruction = res.parsedInstruction,
              instructionGoal = "Dispatched to OpenClaw (${bot.effectivePrimarySessionKey})",
              instructionSteps = emptyList(),
              expression = res.expression,
              status = MessageStatus.COMPLETED,
              takeoverSuggested = res.takeoverSuggested,
              mediaUrl = res.mediaUrl,
              mediaType = res.mediaType,
              mediaPrompt = res.mediaPrompt,
              mediaModel = res.mediaModel,
              mediaDurationSec = res.mediaDurationSec,
            )
            archiveGeneratedMediaIfPresent(res, bot.name, trimmed)
            repository.updateBotPreview(currentUserId, bot.id, res.replyText)
          }.onFailure { err ->
            val cleanMsg = maybeShowFastConnectBanner(err, activeHost) { switchedHost ->
              sendCasualMessage(trimmed, bot, switchedHost, wheelOverride)
            }
            setBotExpression(bot.id, BotExpression.SORRY)
            repository.updateMessageExecution(
              userId = currentUserId,
              botId = bot.id,
              messageId = botMsgId,
              rawText = "Could not reach OpenClaw (${activeHost.effectiveBaseUrl}): $cleanMsg",
              parsedInstruction = trimmed,
              instructionGoal = "OpenClaw host unreachable",
              instructionSteps = emptyList(),
              expression = BotExpression.SORRY,
              status = MessageStatus.FAILED,
              takeoverSuggested = true,
            )
          }
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
          setBotExpression(bot.id, BotExpression.SORRY)
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
          return@launch
        }

        val parsed = parseResult.getOrThrow()

        if (mode == AiRoutingMode.GEMINI_PARSER_PREVIEW || activeHost == null) {
          val expr = if (parsed.needsHostTakeover) BotExpression.ALERT else parsed.recommendedExpression
          setBotExpression(bot.id, expr)
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
          return@launch
        }

        // Step 2 (HYBRID_GEMINI_OPENCLAW): Update card with parsed instruction, then dispatch to OpenClaw
        setBotExpression(bot.id, BotExpression.WORKING)
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

        botTargets[bot.id] = ActiveMessageTarget.BotTarget(bot.id, botMsgId, parsed.clearInstruction, parsed.goal, parsed.steps)
        val dispatchResult = withBotRequestLifecycle(bot.id) {
          openClawClient.dispatchInstruction(
            host = activeHost,
            bot = bot,
            casualMessage = trimmed,
            refinedInstruction = parsed.clearInstruction,
            attachedFiles = attachedLabels,
            linkedPaths = linkedPathLabels,
            providerKeys = providerKeys,
            wheelOverride = wheelOverride,
            attachmentPayloads = attachmentPayloads,
            history = botHistory,
            onPartialText = { partial ->
              _botStreamingPreviews.update { it + (bot.id to partial) }
            },
          )
        }

        dispatchResult.onSuccess { res ->
          val finalExpr = if (res.takeoverSuggested) BotExpression.ALERT else res.expression
          setBotExpression(bot.id, finalExpr)
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
            mediaUrl = res.mediaUrl,
            mediaType = res.mediaType,
            mediaPrompt = res.mediaPrompt,
            mediaModel = res.mediaModel,
            mediaDurationSec = res.mediaDurationSec,
          )
          archiveGeneratedMediaIfPresent(res, bot.name, parsed.clearInstruction)
          repository.updateBotPreview(currentUserId, bot.id, res.replyText)
        }.onFailure { err ->
          val cleanMsg = maybeShowFastConnectBanner(err, activeHost) { switchedHost ->
            sendCasualMessage(trimmed, bot, switchedHost, wheelOverride)
          }
          setBotExpression(bot.id, BotExpression.WORRIED)
          repository.updateMessageExecution(
            userId = currentUserId,
            botId = bot.id,
            messageId = botMsgId,
            rawText = "${parsed.friendlyBotReply}\n\nHeads up — OpenClaw host (${activeHost.effectiveBaseUrl}) didn't respond ($cleanMsg). Your structured instruction is saved below so you can dispatch it once online!",
            parsedInstruction = parsed.clearInstruction,
            instructionGoal = parsed.goal,
            instructionSteps = parsed.steps,
            expression = BotExpression.WORRIED,
            status = MessageStatus.WARNING,
            takeoverSuggested = parsed.needsHostTakeover,
          )
        }
      } finally {
        _busyBotIds.update { it - bot.id }
      }
    }
    botJobs[bot.id] = job
    job.invokeOnCompletion { botJobs.remove(bot.id, job) }
  }

  /**
   * Spawns an isolated background sub-agent lane (`agent:<bot_dude_id>:spawn_<taskId>`) for long-running work
   * without blocking the Bot Dude's primary conversation lane or any other Bot Dude.
   */
  fun spawnBackgroundTaskForBot(
    rawText: String,
    bot: CompanionBot,
    activeHost: OpenClawHost?,
    wheelOverride: WheelLlmOption? = _selectedWheelModel.value,
  ) {
    if (localPrefs.value.hostPaused) {
      _statusBanner.value = "Host queue is paused — tap Resume in Take Over PC before spawning sub-agents."
      return
    }
    val attachmentsSnapshot = _pendingAttachments.value
    val trimmed = rawText.trim().ifBlank {
      if (attachmentsSnapshot.isNotEmpty()) "Analyze the attached file(s) in background." else ""
    }
    if (trimmed.isBlank() || bot.id.isBlank()) return
    if (activeHost == null || activeHost.effectiveBaseUrl.isBlank()) {
      _statusBanner.value = "Configure an OpenClaw host in Host Settings to spawn background sub-agents."
      return
    }

    _pendingAttachments.value = emptyList()
    val taskId = "bg_${System.currentTimeMillis().toString(36)}_${(100..999).random()}"
    val parentSessionKey = bot.effectivePrimarySessionKey
    val subSessionKey = bot.effectiveSpawnSessionKey(taskId)
    val providerKeys = currentProviderKeys()
    val llm = openClawClient.resolveEffectiveLlm(bot, activeHost, wheelOverride, providerKeys)
    val modelLabel = llm.modelId.ifBlank { llm.provider.displayName }

    val initialTask = BackgroundAgentTask(
      id = taskId,
      botId = bot.id,
      botName = bot.name,
      parentSessionKey = parentSessionKey,
      subSessionKey = subSessionKey,
      prompt = trimmed,
      modelLabel = modelLabel,
      status = BackgroundTaskStatus.RUNNING,
    )
    _backgroundTasks.update { (listOf(initialTask) + it).take(12) }
    _statusBanner.value = "Spawned background sub-agent for ${bot.name} ($modelLabel)"

    val timerJob = viewModelScope.launch {
      while (true) {
        delay(1000L)
        _backgroundTasks.update { list ->
          list.map { item ->
            if (item.id == taskId && item.status == BackgroundTaskStatus.RUNNING) {
              item.copy(elapsedSec = item.elapsedSec + 1)
            } else item
          }
        }
      }
    }
    backgroundTaskElapsedJobs[taskId] = timerJob

    val job = viewModelScope.launch {
      try {
        val attachmentPayloads = if (attachmentsSnapshot.isNotEmpty()) {
          attachmentReader.preparePayloads(attachmentsSnapshot, activeHost, trimmed)
        } else {
          emptyList()
        }
        val res = if (wakeLockHelper != null) {
          wakeLockHelper.withPartialWakeLock {
            openClawClient.spawnBackgroundTask(
              host = activeHost,
              bot = bot,
              taskId = taskId,
              prompt = trimmed,
              providerKeys = providerKeys,
              wheelOverride = wheelOverride,
              attachmentPayloads = attachmentPayloads,
              onPartialText = { partial ->
                _backgroundTasks.update { list ->
                  list.map { item ->
                    if (item.id == taskId) item.copy(previewText = partial.take(240)) else item
                  }
                }
              },
            )
          }
        } else {
          openClawClient.spawnBackgroundTask(
            host = activeHost,
            bot = bot,
            taskId = taskId,
            prompt = trimmed,
            providerKeys = providerKeys,
            wheelOverride = wheelOverride,
            attachmentPayloads = attachmentPayloads,
            onPartialText = { partial ->
              _backgroundTasks.update { list ->
                list.map { item ->
                  if (item.id == taskId) item.copy(previewText = partial.take(240)) else item
                }
              }
            },
          )
        }

        res.onSuccess { reply ->
          _backgroundTasks.update { list ->
            list.map { item ->
              if (item.id == taskId) {
                item.copy(
                  status = BackgroundTaskStatus.COMPLETED,
                  resultText = reply.replyText,
                )
              } else item
            }
          }
          repository.addMessage(
            currentUserId,
            bot.id,
            BotMessage(
              userId = currentUserId,
              botId = bot.id,
              sender = MessageSender.BOT.name,
              rawText = "[Background Sub-Agent Completed • $subSessionKey]\n\n${reply.replyText}",
              parsedInstruction = trimmed,
              instructionGoal = "Sub-Agent ($modelLabel)",
              expression = reply.expression.name,
              status = MessageStatus.COMPLETED.name,
              mediaUrl = reply.mediaUrl,
              mediaType = reply.mediaType?.name,
              mediaPrompt = reply.mediaPrompt,
              mediaModel = reply.mediaModel,
              mediaDurationSec = reply.mediaDurationSec,
            ),
          )
          archiveGeneratedMediaIfPresent(reply, "${bot.name} (Sub-Agent)", trimmed)
          _statusBanner.value = "${bot.name}'s background sub-agent finished!"
        }.onFailure { err ->
          val clean = OpenClawClient.cleanRawErrorText(err.localizedMessage, 0, activeHost.effectiveBaseUrl)
          _backgroundTasks.update { list ->
            list.map { item ->
              if (item.id == taskId) {
                item.copy(status = BackgroundTaskStatus.FAILED, resultText = clean)
              } else item
            }
          }
          _statusBanner.value = "${bot.name} sub-agent failed: $clean"
        }
      } finally {
        backgroundTaskElapsedJobs.remove(taskId)?.cancel()
      }
    }
    backgroundTaskJobs[taskId] = job
    job.invokeOnCompletion { backgroundTaskJobs.remove(taskId, job) }
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
      val job = viewModelScope.launch {
        _busyBotIds.update { it + bot.id }
        setBotExpression(bot.id, BotExpression.THINKING)
        try {
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
            setBotExpression(bot.id, BotExpression.SORRY)
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
            return@launch
          }
          val parsed = parseResult.getOrThrow()
          val mode = host?.aiModeEnum ?: AiRoutingMode.HYBRID_GEMINI_OPENCLAW
          if (mode == AiRoutingMode.GEMINI_PARSER_PREVIEW || host == null) {
            val expr = if (parsed.needsHostTakeover) BotExpression.ALERT else parsed.recommendedExpression
            setBotExpression(bot.id, expr)
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
            return@launch
          }

          setBotExpression(bot.id, BotExpression.WORKING)
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
          botTargets[bot.id] = ActiveMessageTarget.BotTarget(bot.id, message.id, parsed.clearInstruction, parsed.goal, parsed.steps)
          val dispatchResult = withBotRequestLifecycle(bot.id) {
            openClawClient.dispatchInstruction(
              host = host,
              bot = bot,
              casualMessage = originalUserPrompt,
              refinedInstruction = parsed.clearInstruction,
              attachedFiles = message.attachedFiles,
              linkedPaths = message.linkedPaths,
              providerKeys = currentProviderKeys(),
              wheelOverride = _selectedWheelModel.value,
              onPartialText = { partial -> _botStreamingPreviews.update { it + (bot.id to partial) } },
            )
          }
          dispatchResult.onSuccess { res ->
            val finalExpr = if (res.takeoverSuggested) BotExpression.ALERT else res.expression
            setBotExpression(bot.id, finalExpr)
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
              mediaUrl = res.mediaUrl,
              mediaType = res.mediaType,
              mediaPrompt = res.mediaPrompt,
              mediaModel = res.mediaModel,
              mediaDurationSec = res.mediaDurationSec,
            )
            archiveGeneratedMediaIfPresent(res, bot.name, parsed.clearInstruction)
            repository.updateBotPreview(currentUserId, bot.id, res.replyText)
          }.onFailure { err ->
            val cleanMsg = maybeShowFastConnectBanner(err, host) { switchedHost ->
              retryDispatchMessage(message, bot, switchedHost)
            }
            setBotExpression(bot.id, BotExpression.WORRIED)
            repository.updateMessageExecution(
              userId = currentUserId,
              botId = bot.id,
              messageId = message.id,
              rawText = "${parsed.friendlyBotReply}\n\nHeads up — OpenClaw host (${host.effectiveBaseUrl}) didn't respond ($cleanMsg). Your structured instruction is saved below so you can dispatch it once online!",
              parsedInstruction = parsed.clearInstruction,
              instructionGoal = parsed.goal,
              instructionSteps = parsed.steps,
              expression = BotExpression.WORRIED,
              status = MessageStatus.WARNING,
              takeoverSuggested = parsed.needsHostTakeover,
            )
          }
        } finally {
          _busyBotIds.update { it - bot.id }
        }
      }
      botJobs[bot.id] = job
      job.invokeOnCompletion { botJobs.remove(bot.id, job) }
      return
    }

    if (host == null) {
      _statusBanner.value = "Configure an OpenClaw host in Host Settings first."
      return
    }
    if (localPrefs.value.hostPaused) {
      _statusBanner.value = "Host queue is paused — tap Resume in Take Over PC before retrying."
      return
    }
    val job = viewModelScope.launch {
      _busyBotIds.update { it + bot.id }
      setBotExpression(bot.id, BotExpression.WORKING)
      try {
        botTargets[bot.id] = ActiveMessageTarget.BotTarget(bot.id, message.id, message.parsedInstruction, message.instructionGoal, message.instructionSteps)
        val res = withBotRequestLifecycle(bot.id) {
          openClawClient.dispatchInstruction(
            host = host,
            bot = bot,
            casualMessage = message.rawText,
            refinedInstruction = message.parsedInstruction,
            attachedFiles = message.attachedFiles,
            linkedPaths = message.linkedPaths,
            providerKeys = currentProviderKeys(),
            wheelOverride = _selectedWheelModel.value,
            onPartialText = { partial -> _botStreamingPreviews.update { it + (bot.id to partial) } },
          )
        }
        res.onSuccess { reply ->
          setBotExpression(bot.id, reply.expression)
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
            mediaUrl = reply.mediaUrl,
            mediaType = reply.mediaType,
            mediaPrompt = reply.mediaPrompt,
            mediaModel = reply.mediaModel,
            mediaDurationSec = reply.mediaDurationSec,
          )
          archiveGeneratedMediaIfPresent(reply, bot.name, message.parsedInstruction ?: message.rawText)
        }.onFailure { err ->
          val cleanMsg = maybeShowFastConnectBanner(err, host) { switchedHost ->
            retryDispatchMessage(message, bot, switchedHost)
          }
          setBotExpression(bot.id, BotExpression.SORRY)
          _statusBanner.value = "Retry failed: $cleanMsg"
        }
      } finally {
        _busyBotIds.update { it - bot.id }
      }
    }
    botJobs[bot.id] = job
    job.invokeOnCompletion { botJobs.remove(bot.id, job) }
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
      _nonChatBusy.value = true
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
      _nonChatBusy.value = false
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
      _nonChatBusy.value = true
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
      _nonChatBusy.value = false
    }
  }

  fun renameBot(bot: CompanionBot, newName: String) {
    val trimmed = newName.trim().take(40)
    if (trimmed.isBlank() || bot.id.isBlank()) {
      _statusBanner.value = "Please enter a name."
      return
    }
    viewModelScope.launch {
      _nonChatBusy.value = true
      repository.updateBot(currentUserId, bot.copy(name = trimmed))
        .onSuccess {
          _statusBanner.value = "Renamed to $trimmed!"
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to rename character."
        }
      _nonChatBusy.value = false
    }
  }

  fun deleteBot(botId: String) {
    val targetBot = (botsState.value as? UiState.Success)?.data?.firstOrNull { it.id == botId }
    val activeHost = currentPrimaryHost()
    if (botId in _busyBotIds.value) {
      abortBotRequest(botId)
    }
    viewModelScope.launch {
      _nonChatBusy.value = true
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
      _nonChatBusy.value = false
    }
  }

  fun updateBotMission(bot: CompanionBot, newMission: String) {
    val trimmed = newMission.trim().take(160)
    if (trimmed.isBlank() || bot.id.isBlank()) {
      _statusBanner.value = "Please enter a mission for ${bot.name}."
      return
    }
    viewModelScope.launch {
      _nonChatBusy.value = true
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
      _nonChatBusy.value = false
    }
  }

  /** Creates the one-tap translation room using the user's translator + critic bots. */
  fun createTranslationRoom(onCreated: (String) -> Unit = {}) {
    viewModelScope.launch {
      val bots = (botsState.value as? UiState.Success)?.data.orEmpty()
      if (bots.isEmpty()) {
        _statusBanner.value = "Register a translator and a critic bot first."
        return@launch
      }
      val translator = bots.firstOrNull { it.functionPresetEnum == BotFunctionPreset.TRANSLATION }
        ?: bots.firstOrNull { it.openClawAgentId.equals("translator", ignoreCase = true) }
      val critic = bots.firstOrNull { it.functionPresetEnum == BotFunctionPreset.CRITIQUING }
        ?: bots.firstOrNull { it.openClawAgentId.equals("critic", ignoreCase = true) }
        ?: bots.firstOrNull { it.id != translator?.id }
      if (translator == null || critic == null) {
        _statusBanner.value = "Need one Translation bot and one Critiquing bot for this room."
        return@launch
      }
      val room = StarterBots.translationRoom(currentUserId, translator.id, critic.id)
      _nonChatBusy.value = true
      repository.createChatRoom(currentUserId, room)
        .onSuccess { newId ->
          selectRoom(newId)
          _statusBanner.value = "${room.title} opened — drop a text to translate."
          onCreated(newId)
        }
        .onFailure { _statusBanner.value = "Could not open room: ${it.message}" }
      _nonChatBusy.value = false
    }
  }

  fun createProofreadingRoom(onCreated: (String) -> Unit = {}) {
    createReviewPairRoom(
      missingMsg = "Register a proofreader and a critic bot first.",
      needMsg = "Need one Proofreading bot and one Critiquing bot for this room.",
      primary = { it.functionPresetEnum == BotFunctionPreset.PROOFREADING },
      build = { p, c -> StarterBots.proofreadingRoom(currentUserId, p.id, c.id) },
      onCreated = onCreated,
    )
  }

  fun createCodingReviewRoom(onCreated: (String) -> Unit = {}) {
    createReviewPairRoom(
      missingMsg = "Register a coder and a critic bot first.",
      needMsg = "Need one Coding bot and one Critiquing bot for this room.",
      primary = { it.functionPresetEnum == BotFunctionPreset.CODING },
      build = { p, c -> StarterBots.codingReviewRoom(currentUserId, p.id, c.id) },
      onCreated = onCreated,
    )
  }

  private fun createReviewPairRoom(
    missingMsg: String,
    needMsg: String,
    primary: (CompanionBot) -> Boolean,
    build: (CompanionBot, CompanionBot) -> ChatRoom,
    onCreated: (String) -> Unit,
  ) {
    viewModelScope.launch {
      val bots = (botsState.value as? UiState.Success)?.data.orEmpty()
      if (bots.isEmpty()) {
        _statusBanner.value = missingMsg
        return@launch
      }
      val lead = bots.firstOrNull(primary)
      val critic = bots.firstOrNull { it.functionPresetEnum == BotFunctionPreset.CRITIQUING }
      if (lead == null || critic == null || lead.id == critic.id) {
        _statusBanner.value = needMsg
        return@launch
      }
      val room = build(lead, critic)
      _nonChatBusy.value = true
      repository.createChatRoom(currentUserId, room)
        .onSuccess { newId ->
          selectRoom(newId)
          _statusBanner.value = "${room.title} opened."
          onCreated(newId)
        }
        .onFailure { _statusBanner.value = "Could not open room: ${it.message}" }
      _nonChatBusy.value = false
    }
  }

  fun createChatRoom(
    title: String,
    topic: String,
    botIds: List<String>,
    maxPingPongTurns: Int = 3,
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
      maxPingPongTurns = maxPingPongTurns.coerceIn(1, 6),
      lastMessagePreview = "Bots arrived at the cafe!",
    )
    viewModelScope.launch {
      _nonChatBusy.value = true
      repository.createChatRoom(currentUserId, room)
        .onSuccess { newId ->
          selectRoom(newId)
          _statusBanner.value = "${room.title} opened!"
          onCreated(newId)
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not create chat room."
        }
      _nonChatBusy.value = false
    }
  }

  fun updateChatRoom(room: ChatRoom) {
    if (room.id.isBlank() || room.botIds.isEmpty()) return
    viewModelScope.launch {
      _nonChatBusy.value = true
      repository.updateChatRoom(currentUserId, room)
        .onSuccess { _statusBanner.value = "Room updated!" }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not update room." }
      _nonChatBusy.value = false
    }
  }

  fun deleteChatRoom(roomId: String, onDeleted: () -> Unit = {}) {
    cafeOrchestrator.stop(roomId) { openClawClient.abortSession(it) }
    viewModelScope.launch {
      _nonChatBusy.value = true
      repository.deleteChatRoom(currentUserId, roomId)
        .onSuccess {
          if (_selectedRoomId.value == roomId) {
            _selectedRoomId.value = ""
          }
          _statusBanner.value = "Chat room removed."
          onDeleted()
        }
        .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Could not remove chat room." }
      _nonChatBusy.value = false
    }
  }

  fun exportRoomTranscript(
    room: ChatRoom,
    messages: List<RoomMessage>,
    participants: List<CompanionBot>,
    host: OpenClawHost?,
  ) {
    if (host == null) {
      _statusBanner.value = "Add an OpenClaw host before saving a transcript."
      return
    }
    if (messages.isEmpty()) {
      _statusBanner.value = "Nothing to save yet."
      return
    }
    val safeTitle = room.title.ifBlank { "room" }
      .replace(Regex("[^A-Za-z0-9_-]+"), "_")
      .trim('_')
      .take(40)
      .ifBlank { "room" }
    val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
      .format(java.util.Date())
    val path = "~/clawpal_exports/${safeTitle}_$stamp.md"
    val body = buildString {
      appendLine("# ${room.title.ifBlank { "Room" }}")
      appendLine()
      if (room.topic.isNotBlank()) {
        appendLine("> ${room.topic}")
        appendLine()
      }
      appendLine("Participants: ${participants.joinToString { it.name }.ifBlank { "(none)" }}")
      appendLine()
      messages.forEach { m ->
        val who = if (m.senderTypeEnum == com.example.model.MessageSender.USER) "You" else m.senderBotName
        appendLine("**$who:**")
        appendLine(m.rawText)
        appendLine()
      }
    }
    val prompt = "Write the following Markdown to the file $path , creating the directory if needed. " +
      "Use a heredoc so quoting survives. Then reply with one short line giving the saved path.\n\n" +
      body
    sendHostControlAction(host, HostControlAction.RUN_COMMAND, prompt)
  }


  fun pauseCafeDiscussion(roomId: String) {
    cafeOrchestrator.pause(roomId) { sessionKey ->
      openClawClient.abortSession(sessionKey)
    }
  }

  fun stopCafeDiscussion(roomId: String) {
    cafeOrchestrator.stop(roomId) { sessionKey ->
      openClawClient.abortSession(sessionKey)
    }
  }

  fun resumeCafeDiscussion(
    room: ChatRoom,
    participants: List<CompanionBot>,
    history: List<RoomMessage>,
    wheelOverride: WheelLlmOption = _selectedWheelModel.value,
  ) {
    launchCafeRound(
      room = room,
      participants = participants,
      history = history,
      userEntry = null,
      wheelOverride = wheelOverride,
      targetBotId = null,
      isResume = true,
    )
  }

  fun sendRoomMessage(
    room: ChatRoom,
    participants: List<CompanionBot>,
    history: List<RoomMessage>,
    promptText: String,
    postUserBubble: Boolean = true,
    wheelOverride: WheelLlmOption = _selectedWheelModel.value,
    targetBotId: String? = null,
  ) {
    val trimmed = promptText.trim()
    if (trimmed.isBlank() || room.id.isBlank() || participants.isEmpty()) return
    if (localPrefs.value.takeoverActive) {
      _statusBanner.value = "You have host control — release Take Over PC before sending to the room."
      return
    }

    // Immediately interrupt any in-flight Cafe turn so user interjections take priority cleanly.
    cafeOrchestrator.stop(room.id) { sessionKey -> openClawClient.abortSession(sessionKey) }

    viewModelScope.launch {
      var appendedUserEntry: CafeTranscriptEntry? = null
      if (postUserBubble) {
        val saveRes = repository.addRoomMessage(
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
        )
        val userDocId = saveRes.getOrElse { err ->
          _roomActiveExpressions.value = participants.associate { it.id to BotExpression.SORRY }
          _statusBanner.value = err.localizedMessage ?: "Failed to post room message."
          return@launch
        }
        appendedUserEntry = CafeTranscriptEntry(
          id = userDocId,
          speakerId = currentUserId,
          speakerName = "You",
          role = CafeSpeakerRole.USER,
          content = trimmed,
        )
      } else {
        appendedUserEntry = CafeTranscriptEntry(
          id = "prompt_${System.nanoTime()}",
          speakerId = currentUserId,
          speakerName = "Moderator",
          role = CafeSpeakerRole.SYSTEM,
          content = trimmed,
        )
      }

      launchCafeRound(
        room = room,
        participants = participants,
        history = history,
        userEntry = appendedUserEntry,
        wheelOverride = wheelOverride,
        targetBotId = targetBotId,
        isResume = false,
      )
    }
  }

  private fun launchCafeRound(
    room: ChatRoom,
    participants: List<CompanionBot>,
    history: List<RoomMessage>,
    userEntry: CafeTranscriptEntry?,
    wheelOverride: WheelLlmOption,
    targetBotId: String?,
    isResume: Boolean,
  ) {
    if (localPrefs.value.takeoverActive && !isResume) {
      _statusBanner.value = "You have host control — release Take Over PC before starting a room round."
      return
    }
    val activeHost = currentPrimaryHost()
    val providerKeys = currentProviderKeys()
    val botById = participants.associateBy { it.id }
    val participantDudes = participants.map { bot ->
      val resolvedModel = if (activeHost != null) {
        val llm = openClawClient.resolveEffectiveLlm(bot, activeHost, wheelOverride, providerKeys)
        llm.modelId.ifBlank { llm.provider.displayName }
      } else {
        bot.llmModel.ifBlank { bot.llmProviderEnum.displayName }
      }
      bot.toBotDude(resolvedModelId = resolvedModel, resolvedProvider = bot.llmProviderEnum)
    }
    val transcript = history.map { it.toCafeTranscriptEntry() }

    cafeOrchestrator.startOrInterjectDiscussion(
      scope = viewModelScope,
      room = room,
      participants = participantDudes,
      existingTranscript = transcript,
      userEntry = userEntry,
      targetBotId = targetBotId,
      isResume = isResume,
      onAbortSession = { sessionKey -> openClawClient.abortSession(sessionKey) },
      dispatchBotTurn = { speaker, currentTranscript, replyToName, onPartial ->
        val speakerBot = speaker.sourceBot ?: botById[speaker.id] ?: participants.first()
        val useOpenClaw = activeHost != null &&
          activeHost.effectiveBaseUrl.isNotBlank() &&
          (wheelOverride != WheelLlmOption.OPENCLAW_DEFAULT || activeHost.aiModeEnum != AiRoutingMode.GEMINI_PARSER_PREVIEW)

        if (useOpenClaw && activeHost != null) {
          val runDispatch: suspend () -> Result<OpenClawDispatchResult> = {
            openClawClient.dispatchCafeBotTurn(
              host = activeHost,
              room = room,
              speakerBot = speakerBot,
              participants = participants,
              transcript = currentTranscript,
              replyToBotName = replyToName,
              wheelOption = wheelOverride,
              providerKeys = providerKeys,
              onPartialText = onPartial,
            )
          }
          if (wakeLockHelper != null) {
            wakeLockHelper.withPartialWakeLock { runDispatch() }
          } else {
            runDispatch()
          }
        } else {
          // Offline / Gemini Refiner preview fallback: run only the chosen speaker for this turn
          val synthHistory = currentTranscript.map { entry ->
            RoomMessage(
              id = entry.id,
              senderType = if (entry.role == CafeSpeakerRole.USER) MessageSender.USER.name else MessageSender.BOT.name,
              senderBotId = entry.speakerId,
              senderBotName = entry.speakerName,
              replyToBotName = entry.replyToBotName,
              rawText = entry.content,
              entryKind = entry.kind.name,
            )
          }
          val fallbackPrompt = currentTranscript.lastOrNull()?.content ?: room.topic
          refinerService.runChatRoomTurn(
            room = room,
            participants = listOf(speakerBot),
            recentHistory = synthHistory,
            userPrompt = fallbackPrompt,
          ).map { round ->
            val firstTurn = round.turns.firstOrNull()
            OpenClawDispatchResult(
              replyText = firstTurn?.message ?: "I reviewed the thread.",
              parsedInstruction = firstTurn?.parsedInstruction,
              expression = firstTurn?.expression ?: BotExpression.UH_HUH,
              takeoverSuggested = false,
              mediaModel = "Gemini Refiner",
            )
          }
        }
      },
      onCommitBotTurn = { roomId, speaker, replyToName, result ->
        val mentionPrefix = replyToName?.let { "@$it " } ?: ""
        repository.addRoomMessage(
          userId = currentUserId,
          roomId = roomId,
          message = RoomMessage(
            userId = currentUserId,
            roomId = roomId,
            senderType = MessageSender.BOT.name,
            senderBotId = speaker.id,
            senderBotName = speaker.name,
            replyToBotName = replyToName,
            rawText = result.replyText,
            parsedInstruction = result.parsedInstruction,
            expression = result.expression.name,
            mediaUrl = result.mediaUrl,
            mediaType = result.mediaType?.name,
            mediaPrompt = result.mediaPrompt,
            mediaModel = result.mediaModel,
            mediaDurationSec = result.mediaDurationSec,
            entryKind = CafeEntryKind.forPreset(speaker.functionPreset).name,
          ),
          previewText = "$mentionPrefix${speaker.name}: ${result.replyText}",
        )
        archiveGeneratedMediaIfPresent(result, speaker.name, room.topic)
      },
      onError = { _, speaker, err ->
        _statusBanner.value = "${speaker.name} couldn't reply: ${err.localizedMessage ?: "host offline"}"
      },
    )
  }

  private fun RoomMessage.toCafeTranscriptEntry(): CafeTranscriptEntry {
    val role = when (senderTypeEnum) {
      MessageSender.USER -> CafeSpeakerRole.USER
      MessageSender.BOT -> CafeSpeakerRole.BOT
      MessageSender.SYSTEM -> CafeSpeakerRole.SYSTEM
    }
    return CafeTranscriptEntry(
      id = id.ifBlank { "hist_${System.nanoTime()}" },
      speakerId = senderBotId ?: senderBotName,
      speakerName = senderBotName,
      role = role,
      replyToBotName = replyToBotName,
      content = rawText,
      modelUsed = mediaModel.orEmpty(),
      expression = expressionEnum,
      parsedInstruction = parsedInstruction,
      mediaUrl = mediaUrl,
      mediaType = if (hasMedia) mediaTypeEnum else null,
      kind = CafeEntryKind.safeValueOf(entryKind),
    )
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
    }
  }

  fun createGeneralChat(
    wheelOption: WheelLlmOption = _selectedWheelModel.value,
    onCreated: (String) -> Unit = {},
  ) {
    viewModelScope.launch {
      _nonChatBusy.value = true
      val thread = GeneralChatThread(
        userId = currentUserId,
        title = "New General Chat",
        pinned = false,
        lastMessagePreview = "",
        wheelModel = wheelOption.name,
      )
      repository.createGeneralChat(currentUserId, thread)
        .onSuccess { newId ->
          _selectedGeneralChatId.value = newId
          _selectedWheelModel.value = wheelOption
          onCreated(newId)
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not start general chat."
        }
      _nonChatBusy.value = false
    }
  }

  fun togglePinGeneralChat(chatId: String, pinned: Boolean) {
    viewModelScope.launch {
      repository.togglePinGeneralChat(currentUserId, chatId, pinned)
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not update pin state."
        }
    }
  }

  fun deleteGeneralChat(chatId: String) {
    if (chatId in _busyGeneralChatIds.value) {
      abortGeneralRequest(chatId)
    }
    viewModelScope.launch {
      repository.deleteGeneralChat(currentUserId, chatId)
        .onSuccess {
          if (_selectedGeneralChatId.value == chatId) {
            _selectedGeneralChatId.value = ""
          }
          _statusBanner.value = "Chat deleted."
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not delete chat."
        }
    }
  }

  fun sendGeneralMessage(
    chat: GeneralChatThread?,
    rawText: String,
    wheelOption: WheelLlmOption,
    activeHost: OpenClawHost?,
    onThreadReady: (String) -> Unit = {},
  ) {
    if (localPrefs.value.hostPaused) {
      _statusBanner.value = "Host queue is paused — tap Resume in Take Over PC before sending."
      return
    }
    val attachmentsSnapshot = _pendingAttachments.value
    val trimmed = rawText.trim().ifBlank {
      if (attachmentsSnapshot.isNotEmpty()) "Please inspect the attached file(s)." else ""
    }
    if (trimmed.isBlank()) return

    val history = (generalMessagesState.value as? UiState.Success)?.data.orEmpty()
    _pendingAttachments.value = emptyList()
    _connectionFailureBanner.value = null

    viewModelScope.launch {
      val resolvedChatId: String
      val isDefaultTitle: Boolean

      if (chat == null || chat.id.isBlank()) {
        val initialTitle = trimmed.replace('\n', ' ').trim().take(48).ifBlank { "General Chat" }
        val newIdRes = repository.createGeneralChat(
          currentUserId,
          GeneralChatThread(
            userId = currentUserId,
            title = initialTitle,
            pinned = false,
            lastMessagePreview = trimmed.take(120),
            wheelModel = wheelOption.name,
          ),
        )
        resolvedChatId = newIdRes.getOrElse { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not create chat."
          return@launch
        }
        _selectedGeneralChatId.value = resolvedChatId
        onThreadReady(resolvedChatId)
        isDefaultTitle = false
      } else {
        resolvedChatId = chat.id
        isDefaultTitle = chat.title == "New General Chat"
      }

      if (generalJobs[resolvedChatId]?.isActive == true) {
        _statusBanner.value = "This General Chat already has a reply streaming — abort or start a new chat!"
        return@launch
      }

      val streamJob = viewModelScope.launch {
        _busyGeneralChatIds.update { it + resolvedChatId }
        try {
          val attachSummary = if (attachmentsSnapshot.isNotEmpty()) {
            "\n\n[Attached: ${attachmentsSnapshot.joinToString(", ") { it.displayName }}]"
          } else ""
          val userDisplayText = "$trimmed$attachSummary".take(4000)

          val autoTitle = if (isDefaultTitle) trimmed.replace('\n', ' ').trim().take(48) else null
          repository.addGeneralMessage(
            userId = currentUserId,
            chatId = resolvedChatId,
            message = GeneralChatMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              sender = MessageSender.USER.name,
              rawText = userDisplayText,
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.IDLE.name,
            ),
            newTitleIfDefault = autoTitle,
          ).onFailure { err ->
            _statusBanner.value = err.localizedMessage ?: "Could not save message."
            return@launch
          }

          val assistantMsgId = repository.addGeneralMessage(
            userId = currentUserId,
            chatId = resolvedChatId,
            message = GeneralChatMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              sender = MessageSender.BOT.name,
              rawText = "Thinking / running tools on host...",
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.DISPATCHED.name,
            ),
          ).getOrElse { err ->
            _statusBanner.value = err.localizedMessage ?: "Could not create reply."
            return@launch
          }

          val bridgeActive = localPrefs.value.driveBridgeEnabled && localPrefs.value.driveBridgeFolderUri.isNotBlank()
          val effectiveHost = activeHost ?: if (bridgeActive) {
            OpenClawHost(id = "bridge", label = "Drive Bridge", baseUrl = "")
          } else null
          if (effectiveHost == null || (effectiveHost.effectiveBaseUrl.isBlank() && !bridgeActive)) {
            repository.updateGeneralMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = "OpenClaw host URL isn't configured yet. Add your LAN or Tailscale gateway URL (or enable Drive Bridge) in the Host tab.",
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.WARNING,
            )
            return@launch
          }

          val attachmentPayloads = if (attachmentsSnapshot.isNotEmpty()) {
            attachmentReader.preparePayloads(attachmentsSnapshot, effectiveHost, trimmed)
          } else {
            emptyList()
          }

          generalTargets[resolvedChatId] = ActiveMessageTarget.GeneralTarget(resolvedChatId, assistantMsgId, wheelOption.fullLabel)
          val replyRes = withGeneralRequestLifecycle(resolvedChatId) {
            openClawClient.sendGeneralChat(
              host = effectiveHost,
              userId = currentUserId,
              conversationId = resolvedChatId,
              history = history,
              userPrompt = trimmed,
              wheelOption = wheelOption,
              providerKeys = currentProviderKeys(),
              attachmentPayloads = attachmentPayloads,
              onPartialText = { partial ->
                _generalStreamingPreviews.update { it + (resolvedChatId to partial) }
              },
            )
          }

          replyRes.onSuccess { res ->
            repository.updateGeneralMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = res.replyText,
              modelLabel = res.mediaModel?.takeIf { it.isNotBlank() } ?: wheelOption.fullLabel,
              status = MessageStatus.COMPLETED,
              mediaUrl = res.mediaUrl,
              mediaType = res.mediaType,
              mediaPrompt = res.mediaPrompt,
              mediaModel = res.mediaModel,
              mediaDurationSec = res.mediaDurationSec,
            )
            archiveGeneratedMediaIfPresent(res, "General (${wheelOption.wheelLabel})", trimmed)
          }.onFailure { err ->
            val cleanMsg = maybeShowFastConnectBanner(err, effectiveHost) { switchedHost ->
              sendGeneralMessage(chat, trimmed, wheelOption, switchedHost, onThreadReady)
            }
            repository.updateGeneralMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = cleanMsg,
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.FAILED,
            )
          }
        } finally {
          _busyGeneralChatIds.update { it - resolvedChatId }
        }
      }
      generalJobs[resolvedChatId] = streamJob
      streamJob.invokeOnCompletion { generalJobs.remove(resolvedChatId, streamJob) }
    }
  }

  private fun hydrateDocsForThread(thread: ContextChatThread) {
    val chatKey = thread.id.ifBlank { return }
    val refs = thread.docRefs
    if (refs.isEmpty()) return
    val current = _contextDocsByChatId.value[chatKey].orEmpty()
    val currentByRef = current.associateBy { it.uriOrPath }
    val missingRefs = refs.filter { ref -> currentByRef[ref] == null }
    if (missingRefs.isEmpty()) return

    val initialList = refs.mapIndexed { idx, ref ->
      val label = thread.docLabels.getOrNull(idx).orEmpty()
      currentByRef[ref]
        ?: contextDocCache?.load(currentUserId, ref)
        ?: CaptainContextDocument(
          id = "ctxdoc_${ref.hashCode().toUInt()}",
          displayName = label.ifBlank { ref.substringAfterLast('/').ifBlank { "Document" } },
          uriOrPath = ref,
          linkType = if (ref.startsWith("http", true)) FileLinkType.CLOUD_URL
          else if (ref.startsWith("content://", true)) FileLinkType.ANDROID_ATTACHMENT
          else FileLinkType.HOST_PATH,
          status = ContextDocStatus.LOADING,
          statusDetail = "Loading document...",
        )
    }
    _contextDocsByChatId.update { it + (chatKey to initialList) }

    val toRead = initialList.filter { it.status == ContextDocStatus.LOADING }
    if (toRead.isEmpty()) return
    val host = currentPrimaryHost()
    viewModelScope.launch {
      toRead.forEach { stub ->
        val loaded = attachmentReader.readContextDocument(
          docRef = stub.uriOrPath,
          displayNameHint = stub.displayName,
          linkType = stub.linkType,
          host = host,
        )
        if (loaded.status == ContextDocStatus.READY) {
          contextDocCache?.save(currentUserId, loaded)
        }
        _contextDocsByChatId.update { map ->
          val list = map[chatKey].orEmpty().map { existing ->
            if (existing.uriOrPath == stub.uriOrPath) loaded else existing
          }
          map + (chatKey to list)
        }
      }
    }
  }

  fun createContextChat(
    wheelOption: WheelLlmOption = _selectedWheelModel.value,
    initialDocs: List<CaptainContextDocument> = emptyList(),
    onCreated: (String) -> Unit = {},
  ) {
    viewModelScope.launch {
      _nonChatBusy.value = true
      val draftDocs = initialDocs.ifEmpty { _contextDocsByChatId.value["__draft__"].orEmpty() }
      val title = draftDocs.firstOrNull()?.displayName?.take(60)?.ifBlank { "New Context Case" }
        ?: "New Context Case"
      val thread = ContextChatThread(
        userId = currentUserId,
        title = title,
        pinned = false,
        lastMessagePreview = if (draftDocs.isNotEmpty()) "${draftDocs.size} doc(s) loaded" else "",
        wheelModel = wheelOption.name,
        docLabels = draftDocs.map { it.displayName },
        docRefs = draftDocs.map { it.uriOrPath },
      )
      repository.createContextChat(currentUserId, thread)
        .onSuccess { newId ->
          if (draftDocs.isNotEmpty()) {
            _contextDocsByChatId.update { map ->
              (map - "__draft__") + (newId to draftDocs)
            }
          }
          _selectedContextChatId.value = newId
          _selectedWheelModel.value = wheelOption
          onCreated(newId)
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not create Captain Context case."
        }
      _nonChatBusy.value = false
    }
  }

  fun togglePinContextChat(chatId: String, pinned: Boolean) {
    viewModelScope.launch {
      repository.togglePinContextChat(currentUserId, chatId, pinned)
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not update pin state."
        }
    }
  }

  fun deleteContextChat(chatId: String) {
    if (chatId in _busyContextChatIds.value) {
      abortContextRequest(chatId)
    }
    viewModelScope.launch {
      repository.deleteContextChat(currentUserId, chatId)
        .onSuccess {
          _contextDocsByChatId.update { it - chatId }
          if (_selectedContextChatId.value == chatId) {
            _selectedContextChatId.value = ""
          }
          _statusBanner.value = "Context case deleted."
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not delete context case."
        }
    }
  }

  private suspend fun ensureContextChatForDocs(
    requestedChatId: String,
    initialLabel: String,
  ): String? {
    if (requestedChatId.isNotBlank()) return requestedChatId
    val draftDocs = _contextDocsByChatId.value["__draft__"].orEmpty()
    val createdId = repository.createContextChat(
      currentUserId,
      ContextChatThread(
        userId = currentUserId,
        title = initialLabel.take(60).ifBlank { "New Context Case" },
        pinned = false,
        lastMessagePreview = "Loading document vault...",
        wheelModel = _selectedWheelModel.value.name,
        docLabels = draftDocs.map { it.displayName },
        docRefs = draftDocs.map { it.uriOrPath },
      ),
    ).getOrElse { err ->
      _statusBanner.value = err.localizedMessage ?: "Could not create Captain Context case."
      return null
    }
    if (draftDocs.isNotEmpty()) {
      _contextDocsByChatId.update { (it - "__draft__") + (createdId to draftDocs) }
    }
    _selectedContextChatId.value = createdId
    return createdId
  }

  private suspend fun syncContextDocsToFirestore(chatId: String, docs: List<CaptainContextDocument>) {
    if (chatId.isBlank() || chatId == "__draft__") return
    repository.updateContextChatDocs(
      userId = currentUserId,
      chatId = chatId,
      docLabels = docs.map { it.displayName },
      docRefs = docs.map { it.uriOrPath },
    )
  }

  fun addDeviceDocsToContext(
    chatId: String,
    items: List<Triple<String, String, String>>,
  ) {
    if (items.isEmpty()) return
    viewModelScope.launch {
      val resolvedChatId = ensureContextChatForDocs(chatId, items.first().first) ?: return@launch
      val host = currentPrimaryHost()
      items.forEach { (name, uri, mime) ->
        val stub = CaptainContextDocument(
          id = "ctxdoc_${uri.hashCode().toUInt()}",
          displayName = name.ifBlank { "Document" },
          uriOrPath = uri,
          linkType = FileLinkType.ANDROID_ATTACHMENT,
          mimeType = mime,
          status = ContextDocStatus.LOADING,
          statusDetail = "Reading file...",
        )
        upsertContextDocInMemory(resolvedChatId, stub)
        val loaded = attachmentReader.readContextDocument(
          docRef = uri,
          displayNameHint = name,
          mimeHint = mime,
          linkType = FileLinkType.ANDROID_ATTACHMENT,
          host = host,
        )
        if (loaded.status == ContextDocStatus.READY) {
          contextDocCache?.save(currentUserId, loaded)
          _statusBanner.value = "Captain Context loaded ${loaded.displayName}!"
        } else {
          _statusBanner.value = "${loaded.displayName}: ${loaded.statusDetail}"
        }
        val updated = upsertContextDocInMemory(resolvedChatId, loaded)
        syncContextDocsToFirestore(resolvedChatId, updated)
      }
    }
  }

  fun addDriveOrCloudDocToContext(
    chatId: String,
    rawUrl: String,
    customTitle: String = "",
  ) {
    val cleanUrl = rawUrl.trim()
    if (cleanUrl.isBlank()) return
    viewModelScope.launch {
      val hintName = customTitle.trim().ifBlank {
        cleanUrl.substringBefore('?').substringAfterLast('/').ifBlank { "Google Drive Document" }
      }
      val resolvedChatId = ensureContextChatForDocs(chatId, hintName) ?: return@launch
      val stub = CaptainContextDocument(
        id = "ctxdoc_${cleanUrl.hashCode().toUInt()}",
        displayName = hintName,
        uriOrPath = cleanUrl,
        linkType = FileLinkType.CLOUD_URL,
        status = ContextDocStatus.LOADING,
        statusDetail = "Fetching document...",
        isGoogleDriveUrl = com.example.data.files.GoogleDriveDocumentFetcher.isGoogleDriveOrDocsUrl(cleanUrl),
      )
      upsertContextDocInMemory(resolvedChatId, stub)

      val loaded = attachmentReader.readContextDocument(
        docRef = cleanUrl,
        displayNameHint = customTitle.trim(),
        linkType = FileLinkType.CLOUD_URL,
        host = currentPrimaryHost(),
      )
      if (loaded.status == ContextDocStatus.READY) {
        contextDocCache?.save(currentUserId, loaded)
        _statusBanner.value = "Loaded ${loaded.displayName} (${loaded.charCount} chars)!"
      } else {
        _statusBanner.value = loaded.statusDetail.ifBlank { "Could not read $hintName" }
      }
      val updated = upsertContextDocInMemory(resolvedChatId, loaded)
      syncContextDocsToFirestore(resolvedChatId, updated)
    }
  }

  fun addVaultOrHostDocToContext(
    chatId: String,
    linkedFile: LinkedFile,
  ) {
    viewModelScope.launch {
      val resolvedChatId = ensureContextChatForDocs(chatId, linkedFile.displayName) ?: return@launch
      val stub = CaptainContextDocument(
        id = "ctxdoc_${linkedFile.uriOrPath.hashCode().toUInt()}",
        displayName = linkedFile.displayName,
        uriOrPath = linkedFile.uriOrPath,
        linkType = linkedFile.linkTypeEnum,
        mimeType = linkedFile.mimeOrExt,
        status = ContextDocStatus.LOADING,
        statusDetail = "Loading from Vault...",
      )
      upsertContextDocInMemory(resolvedChatId, stub)
      val loaded = attachmentReader.readContextDocument(
        docRef = linkedFile.uriOrPath,
        displayNameHint = linkedFile.displayName,
        mimeHint = linkedFile.mimeOrExt,
        linkType = linkedFile.linkTypeEnum,
        host = currentPrimaryHost(),
      )
      if (loaded.status == ContextDocStatus.READY) {
        contextDocCache?.save(currentUserId, loaded)
        _statusBanner.value = "Loaded ${loaded.displayName} into Captain Context!"
      }
      val updated = upsertContextDocInMemory(resolvedChatId, loaded)
      syncContextDocsToFirestore(resolvedChatId, updated)
    }
  }

  fun refreshContextDoc(chatId: String, docId: String) {
    val chatKey = chatId.ifBlank { return }
    val target = _contextDocsByChatId.value[chatKey].orEmpty().firstOrNull { it.id == docId } ?: return
    viewModelScope.launch {
      upsertContextDocInMemory(
        chatKey,
        target.copy(status = ContextDocStatus.LOADING, statusDetail = "Refreshing document..."),
      )
      val refreshed = attachmentReader.readContextDocument(
        docRef = target.uriOrPath,
        displayNameHint = target.displayName,
        mimeHint = target.mimeType,
        linkType = target.linkType,
        host = currentPrimaryHost(),
      )
      if (refreshed.status == ContextDocStatus.READY) {
        contextDocCache?.save(currentUserId, refreshed)
        _statusBanner.value = "Refreshed ${refreshed.displayName}!"
      } else {
        _statusBanner.value = refreshed.statusDetail
      }
      val updated = upsertContextDocInMemory(chatKey, refreshed)
      syncContextDocsToFirestore(chatKey, updated)
    }
  }

  fun removeContextDoc(chatId: String, docId: String) {
    val chatKey = chatId.ifBlank { "__draft__" }
    val removed = _contextDocsByChatId.value[chatKey].orEmpty().firstOrNull { it.id == docId }
    val remaining = _contextDocsByChatId.value[chatKey].orEmpty().filterNot { it.id == docId }
    _contextDocsByChatId.update { it + (chatKey to remaining) }
    if (removed != null) {
      contextDocCache?.remove(currentUserId, removed.uriOrPath)
    }
    if (chatKey != "__draft__") {
      viewModelScope.launch {
        syncContextDocsToFirestore(chatKey, remaining)
      }
    }
  }

  private fun upsertContextDocInMemory(
    chatKey: String,
    doc: CaptainContextDocument,
  ): List<CaptainContextDocument> {
    var resultList: List<CaptainContextDocument> = emptyList()
    _contextDocsByChatId.update { map ->
      val current = map[chatKey].orEmpty()
      val replaced = if (current.any { it.uriOrPath == doc.uriOrPath }) {
        current.map { if (it.uriOrPath == doc.uriOrPath) doc else it }
      } else {
        (current + doc).take(20)
      }
      resultList = replaced
      map + (chatKey to replaced)
    }
    return resultList
  }

  private fun extractCitedDocsFromReply(
    replyText: String,
    docs: List<CaptainContextDocument>,
  ): List<String> {
    if (docs.isEmpty()) return emptyList()
    val explicitSources = Regex("""\[Source:\s*([^\]]+)\]""", RegexOption.IGNORE_CASE)
      .findAll(replyText)
      .map { it.groupValues[1].trim() }
      .toList()
    return docs.mapNotNull { doc ->
      val label = doc.displayName
      val matched = explicitSources.any { src ->
        src.equals(label, ignoreCase = true) ||
          src.contains(label, ignoreCase = true) ||
          label.contains(src, ignoreCase = true)
      } || replyText.contains(label, ignoreCase = true)
      if (matched) label else null
    }.distinct().take(20)
  }

  fun sendCaptainContextMessage(
    chat: ContextChatThread?,
    rawText: String,
    wheelOption: WheelLlmOption,
    activeHost: OpenClawHost?,
    onThreadReady: (String) -> Unit = {},
  ) {
    if (localPrefs.value.hostPaused) {
      _statusBanner.value = "Host queue is paused — tap Resume in Takeover before sending."
      return
    }
    val trimmed = rawText.trim()
    if (trimmed.isBlank()) return

    val history = (contextMessagesState.value as? UiState.Success)?.data.orEmpty()
    _connectionFailureBanner.value = null

    viewModelScope.launch {
      val resolvedChatId: String
      val isDefaultTitle: Boolean
      val initialDocs = if (chat != null && chat.id.isNotBlank()) {
        _contextDocsByChatId.value[chat.id].orEmpty()
      } else {
        _contextDocsByChatId.value["__draft__"].orEmpty()
      }

      if (chat == null || chat.id.isBlank()) {
        val initialTitle = trimmed.replace('\n', ' ').trim().take(48).ifBlank { "Context Case" }
        val newIdRes = repository.createContextChat(
          currentUserId,
          ContextChatThread(
            userId = currentUserId,
            title = initialTitle,
            pinned = false,
            lastMessagePreview = trimmed.take(120),
            wheelModel = wheelOption.name,
            docLabels = initialDocs.map { it.displayName },
            docRefs = initialDocs.map { it.uriOrPath },
          ),
        )
        resolvedChatId = newIdRes.getOrElse { err ->
          _statusBanner.value = err.localizedMessage ?: "Could not create Captain Context case."
          return@launch
        }
        if (initialDocs.isNotEmpty()) {
          _contextDocsByChatId.update { (it - "__draft__") + (resolvedChatId to initialDocs) }
        }
        _selectedContextChatId.value = resolvedChatId
        onThreadReady(resolvedChatId)
        isDefaultTitle = false
      } else {
        resolvedChatId = chat.id
        isDefaultTitle = chat.title == "New Context Case"
      }

      if (contextJobs[resolvedChatId]?.isActive == true) {
        _statusBanner.value = "Captain Context is already analyzing this case — Abort or open another case!"
        return@launch
      }

      val streamJob = viewModelScope.launch {
        _busyContextChatIds.update { it + resolvedChatId }
        try {
          val docsBeforeSend = _contextDocsByChatId.value[resolvedChatId].orEmpty()
          // Ensure any LOADING docs are fetched before sending to OpenClaw
          val preparedDocs = docsBeforeSend.map { doc ->
            if (doc.status == ContextDocStatus.LOADING) {
              val read = attachmentReader.readContextDocument(
                docRef = doc.uriOrPath,
                displayNameHint = doc.displayName,
                mimeHint = doc.mimeType,
                linkType = doc.linkType,
                host = activeHost,
                promptHint = trimmed,
              )
              if (read.status == ContextDocStatus.READY) {
                contextDocCache?.save(currentUserId, read)
              }
              upsertContextDocInMemory(resolvedChatId, read)
              read
            } else {
              doc
            }
          }

          val docLabelsUsed = preparedDocs.filter { it.status == ContextDocStatus.READY }.map { it.displayName }
          val autoTitle = if (isDefaultTitle) trimmed.replace('\n', ' ').trim().take(48) else null
          repository.addContextMessage(
            userId = currentUserId,
            chatId = resolvedChatId,
            message = ContextChatMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              sender = MessageSender.USER.name,
              rawText = trimmed.take(4000),
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.IDLE.name,
              citedDocs = docLabelsUsed,
            ),
            newTitleIfDefault = autoTitle,
          ).onFailure { err ->
            _statusBanner.value = err.localizedMessage ?: "Could not save message."
            return@launch
          }

          val assistantMsgId = repository.addContextMessage(
            userId = currentUserId,
            chatId = resolvedChatId,
            message = ContextChatMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              sender = MessageSender.BOT.name,
              rawText = "Captain Context is scanning ${preparedDocs.size} document(s)...",
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.DISPATCHED.name,
              citedDocs = docLabelsUsed,
            ),
          ).getOrElse { err ->
            _statusBanner.value = err.localizedMessage ?: "Could not create Captain Context reply."
            return@launch
          }

          val bridgeActive = localPrefs.value.driveBridgeEnabled && localPrefs.value.driveBridgeFolderUri.isNotBlank()
          val effectiveHost = activeHost ?: if (bridgeActive) {
            OpenClawHost(id = "bridge", label = "Drive Bridge", baseUrl = "")
          } else null
          if (effectiveHost == null || (effectiveHost.effectiveBaseUrl.isBlank() && !bridgeActive)) {
            repository.updateContextMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = "OpenClaw host URL isn't configured yet. Add your LAN or Tailscale gateway URL (or enable Drive Bridge) in the Host tab.",
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.WARNING,
            )
            return@launch
          }

          contextTargets[resolvedChatId] = ActiveMessageTarget.ContextTarget(
            resolvedChatId,
            assistantMsgId,
            wheelOption.fullLabel,
          )
          val replyRes = withContextRequestLifecycle(resolvedChatId) {
            openClawClient.sendCaptainContextChat(
              host = effectiveHost,
              userId = currentUserId,
              conversationId = resolvedChatId,
              contextDocs = preparedDocs,
              history = history,
              userPrompt = trimmed,
              wheelOption = wheelOption,
              providerKeys = currentProviderKeys(),
              onPartialText = { partial ->
                _contextStreamingPreviews.update { it + (resolvedChatId to partial) }
              },
            )
          }

          replyRes.onSuccess { res ->
            val cited = extractCitedDocsFromReply(res.replyText, preparedDocs)
              .ifEmpty { docLabelsUsed }
            repository.updateContextMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = res.replyText,
              modelLabel = res.mediaModel?.takeIf { it.isNotBlank() } ?: wheelOption.fullLabel,
              status = MessageStatus.COMPLETED,
              citedDocs = cited,
              mediaUrl = res.mediaUrl,
              mediaType = res.mediaType,
              mediaPrompt = res.mediaPrompt,
              mediaModel = res.mediaModel,
              mediaDurationSec = res.mediaDurationSec,
            )
            archiveGeneratedMediaIfPresent(res, "Captain Context", trimmed)
          }.onFailure { err ->
            val cleanMsg = maybeShowFastConnectBanner(err, effectiveHost) { switchedHost ->
              sendCaptainContextMessage(chat, trimmed, wheelOption, switchedHost, onThreadReady)
            }
            repository.updateContextMessage(
              userId = currentUserId,
              chatId = resolvedChatId,
              messageId = assistantMsgId,
              rawText = cleanMsg,
              modelLabel = wheelOption.fullLabel,
              status = MessageStatus.FAILED,
            )
          }
        } finally {
          _busyContextChatIds.update { it - resolvedChatId }
        }
      }
      contextJobs[resolvedChatId] = streamJob
      streamJob.invokeOnCompletion { contextJobs.remove(resolvedChatId, streamJob) }
    }
  }

  private suspend fun archiveGeneratedMediaIfPresent(
    res: OpenClawDispatchResult,
    sourceBotName: String,
    fallbackPrompt: String,
  ) {
    val url = res.mediaUrl?.takeIf { it.isNotBlank() } ?: return
    val mediaItem = GeneratedMediaItem(
      userId = currentUserId,
      mediaUrl = url,
      mediaType = (res.mediaType ?: GeneratedMediaType.IMAGE).name,
      prompt = res.mediaPrompt?.takeIf { it.isNotBlank() } ?: fallbackPrompt,
      modelUsed = res.mediaModel?.takeIf { it.isNotBlank() } ?: localPrefs.value.globalVeniceModel,
      durationSec = res.mediaDurationSec ?: 0,
      sourceBotName = sourceBotName,
    )
    repository.addGeneratedMedia(currentUserId, mediaItem)
  }

  fun addUrlBookmark(name: String, url: String) {
    viewModelScope.launch {
      localPrefsStore.addUrlBookmark(name, url)
      _statusBanner.value = "Bookmarked ${name.ifBlank { url }}."
    }
  }

  fun removeUrlBookmark(url: String) {
    viewModelScope.launch { localPrefsStore.removeUrlBookmark(url) }
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
    val tempAttachKey = "pending_${System.currentTimeMillis()}_${uriOrPath.hashCode()}"
    val file = LinkedFile(
      id = if (attachToComposer) tempAttachKey else "",
      userId = currentUserId,
      displayName = displayName,
      uriOrPath = uriOrPath,
      linkType = linkType.name,
      mimeOrExt = mimeOrExt,
      sizeBytes = sizeBytes,
      notes = notes,
    )
    if (attachToComposer) {
      val current = _pendingAttachments.value
      if (current.none { it.uriOrPath == uriOrPath }) {
        _pendingAttachments.value = current + file
      }
    }
    viewModelScope.launch {
      repository.addLinkedFile(currentUserId, file)
        .onSuccess { newId ->
          if (attachToComposer) {
            _pendingAttachments.value = _pendingAttachments.value.map { item ->
              if (item.uriOrPath == uriOrPath) item.copy(id = newId) else item
            }
          }
          _statusBanner.value = "Attached $displayName"
        }
        .onFailure { err ->
          _statusBanner.value = if (attachToComposer) {
            "Ready to send $displayName (cloud vault sync deferred: ${err.localizedMessage ?: "offline"})"
          } else {
            err.localizedMessage ?: "Could not link file."
          }
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
    globalVeniceModel: String = if (host.defaultLlmProviderEnum == com.example.model.OpenClawLlmProvider.VENICE_AI && host.defaultLlmModel.isNotBlank()) {
      host.defaultLlmModel
    } else {
      localPrefs.value.globalVeniceModel
    },
    devicePairingId: String = localPrefs.value.devicePairingId,
    driveBridgeEnabled: Boolean = localPrefs.value.driveBridgeEnabled,
    driveBridgeFolderUri: String = localPrefs.value.driveBridgeFolderUri,
    onSaved: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _nonChatBusy.value = true
      val resolvedVeniceModel = globalVeniceModel.trim().ifBlank { VeniceModelCatalog.defaultModelId }
      localPrefsStore.setProviderApiKeys(veniceApiKey, googleApiKey, resolvedVeniceModel)
      localPrefsStore.setDeviceAndBridgeConfig(devicePairingId, driveBridgeEnabled, driveBridgeFolderUri)
      if (driveBridgeEnabled && driveBridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        _bridgeStatus.value = driveBridgeManager.inspectBridgeFolders(driveBridgeFolderUri)
      }
      repository.saveUserVenicePreferences(currentUserId, resolvedVeniceModel, veniceApiKey)
      repository.saveHost(currentUserId, host)
        .onSuccess {
          val extraModel = if (host.defaultLlmProviderEnum == com.example.model.OpenClawLlmProvider.VENICE_AI) {
            " ($resolvedVeniceModel)"
          } else ""
          val bridgeNote = if (driveBridgeEnabled) " • Drive Bridge ON" else ""
          _statusBanner.value = "${host.label} & default LLM (${host.defaultLlmProviderEnum.displayName}$extraModel)$bridgeNote saved!"
          onSaved()
        }
        .onFailure { err ->
          _statusBanner.value = err.localizedMessage ?: "Failed to save host."
        }
      _nonChatBusy.value = false
    }
  }

  fun inspectBridgeFolder(folderUriOrPath: String) {
    val manager = driveBridgeManager ?: return
    viewModelScope.launch {
      _nonChatBusy.value = true
      val status = manager.inspectBridgeFolders(folderUriOrPath)
      _bridgeStatus.value = status
      _statusBanner.value = status.summary
      _nonChatBusy.value = false
    }
  }

  private fun currentPrimaryHost(): OpenClawHost? =
    (hostsState.value as? UiState.Success)?.data?.firstOrNull()

  private fun currentProviderKeys(): LocalProviderKeys {
    val prefs = localPrefs.value
    return LocalProviderKeys(
      veniceApiKey = prefs.veniceApiKey,
      googleApiKey = prefs.googleApiKey,
      globalVeniceModel = prefs.globalVeniceModel,
      deviceId = prefs.devicePairingId,
      bridgeEnabled = prefs.driveBridgeEnabled,
      bridgeFolderUri = prefs.driveBridgeFolderUri,
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
      _nonChatBusy.value = true
      val res = openClawClient.pingHost(host, deviceId = localPrefs.value.devicePairingId)
      _pingResult.value = res
      val newStatus = if (res.reachable) HostStatus.ONLINE else HostStatus.OFFLINE
      if (host.id.isNotBlank()) {
        repository.updateHostStatus(currentUserId, host.id, newStatus)
      }
      _controlLog.value = (listOf("[PING] ${res.detail}") + _controlLog.value).take(25)
      _nonChatBusy.value = false
    }
  }

  fun sendHostControlAction(host: OpenClawHost?, action: HostControlAction, input: String = "") {
    if (action == HostControlAction.ABORT_TASK) {
      abortActiveRequest()
      return
    }
    if (host == null) {
      _statusBanner.value = "Add an OpenClaw host first."
      return
    }
    viewModelScope.launch {
      _nonChatBusy.value = true
      when (action) {
        HostControlAction.TAKEOVER_START -> {
          localPrefsStore.setTakeoverActive(true)
          localPrefsStore.setHostPaused(true)
          setLiveExpression(BotExpression.ALERT)
        }
        HostControlAction.TAKEOVER_RELEASE -> {
          localPrefsStore.setTakeoverActive(false)
          localPrefsStore.setHostPaused(false)
          setLiveExpression(BotExpression.HAPPY)
        }
        HostControlAction.PAUSE_AGENT -> {
          localPrefsStore.setHostPaused(true)
          setLiveExpression(BotExpression.WORRIED)
        }
        HostControlAction.RESUME_AGENT -> {
          localPrefsStore.setHostPaused(false)
          setLiveExpression(BotExpression.HAPPY)
        }
        HostControlAction.OPEN_URL -> {
          if (input.isBlank() || !input.startsWith("http", ignoreCase = true)) {
            _statusBanner.value = "Enter a full http(s) URL to open."
            _nonChatBusy.value = false
            return@launch
          }
        }
        else -> Unit
      }

      val res = openClawClient.sendControlAction(host, action, input)
      res.onSuccess { ok ->
        _controlLog.value = (listOf("[OK] ${action.label}: ${ok.summary}") + _controlLog.value).take(25)
        _statusBanner.value = ok.summary
      }.onFailure { err ->
        val clean = OpenClawClient.cleanRawErrorText(err.localizedMessage, 0, host.effectiveBaseUrl)
        _controlLog.value = (listOf("[HOST] ${action.label}: $clean") + _controlLog.value).take(25)
        _statusBanner.value = clean
      }
      _nonChatBusy.value = false
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
