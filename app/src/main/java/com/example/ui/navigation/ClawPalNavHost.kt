package com.example.ui.navigation

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.ai.PromptRefinerService
import com.example.data.files.DeviceAttachmentReader
import com.example.data.firebase.ClawPalRepository
import com.example.data.local.LocalPrefsStore
import com.example.data.openclaw.DriveBridgeManager
import com.example.data.openclaw.OpenClawClient
import com.example.model.FileLinkType
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BotStudioScreen
import com.example.ui.screens.ChatRoomScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.ComputerTakeoverScreen
import com.example.ui.screens.FilesVaultScreen
import com.example.ui.screens.GeneralChatScreen
import com.example.ui.screens.HostSettingsScreen
import com.example.ui.screens.OutlinedArcadeTitle
import com.example.util.WakeLockHelper
import com.example.viewmodel.ClawPalViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ROUTE_HOME = "home"
const val ROUTE_GENERAL = "general"
const val ROUTE_CHAT = "chat"
const val ROUTE_ROOM = "room/{roomId}"
const val ROUTE_TAKEOVER = "takeover"
const val ROUTE_FILES = "files"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_BOT_STUDIO = "bot_studio?botId={botId}"

private data class TopTab(
  val route: String,
  val label: String,
  @DrawableRes val iconRes: Int,
)

private val topTabs = listOf(
  TopTab(ROUTE_HOME, "Bot Dudes", R.drawable.ic_min_bot_dudes),
  TopTab(ROUTE_GENERAL, "General", R.drawable.ic_min_general),
  TopTab(ROUTE_TAKEOVER, "Takeover", R.drawable.ic_min_takeover),
  TopTab(ROUTE_FILES, "Files", R.drawable.ic_min_files),
  TopTab(ROUTE_SETTINGS, "Host", R.drawable.ic_min_host),
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun ClawPalRoot(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  var showSplash by remember { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    delay(1350L)
    showSplash = false
  }

  Crossfade(
    targetState = showSplash,
    animationSpec = tween(durationMillis = 420),
    label = "splash_crossfade",
  ) { displayingSplash ->
    if (displayingSplash) {
      SplashLoadingScreen()
    } else {
      val user = currentUser
      if (user == null) {
        AuthScreen(onAuthSuccess = {})
      } else {
        key(user.uid) {
          AuthenticatedWorkspace(currentUserId = user.uid, userEmail = user.email)
        }
      }
    }
  }
}

@Composable
private fun SplashLoadingScreen() {
  val infiniteTransition = rememberInfiniteTransition(label = "splash_glow")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.72f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "splash_alpha",
  )

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF1A052A))
      .testTag("clawpal_splash_screen"),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_splash_bots_clothed),
      contentDescription = "Claw Dudebro companion bots giving a thumbs up",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xBB1F0533),
              Color.Transparent,
              Color.Transparent,
              Color(0xDD1A0429),
            )
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      OutlinedArcadeTitle(
        title = "Claw Dudebro",
        fontSizeSp = 32f,
      )

      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC24063A),
        border = BorderStroke(1.dp, Color(0x88FF6BC8)),
        modifier = Modifier
          .fillMaxWidth()
          .alpha(pulseAlpha),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Waking up your crew...",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFFFC7EE),
            fontWeight = FontWeight.SemiBold,
          )
          LinearProgressIndicator(
            color = Color(0xFFFF389E),
            trackColor = Color(0x44FFFFFF),
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun AuthenticatedWorkspace(
  currentUserId: String,
  userEmail: String?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: ClawPalViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val bridgeManager = DriveBridgeManager(app, app.filesDir)
        ClawPalViewModel(
          repository = ClawPalRepository(db),
          refinerService = PromptRefinerService(),
          openClawClient = OpenClawClient(app.filesDir, bridgeManager),
          localPrefsStore = LocalPrefsStore(app),
          attachmentReader = DeviceAttachmentReader(app),
          currentUserId = currentUserId,
          wakeLockHelper = WakeLockHelper(app),
          driveBridgeManager = bridgeManager,
        )
      }
    },
  )

  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

  val botsState by viewModel.botsState.collectAsStateWithLifecycle()
  val chatRoomsState by viewModel.chatRoomsState.collectAsStateWithLifecycle()
  val generalChatsState by viewModel.generalChatsState.collectAsStateWithLifecycle()
  val selectedBotId by viewModel.selectedBotId.collectAsStateWithLifecycle()
  val selectedGeneralChatId by viewModel.selectedGeneralChatId.collectAsStateWithLifecycle()
  val selectedWheelModel by viewModel.selectedWheelModel.collectAsStateWithLifecycle()
  val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
  val roomMessagesState by viewModel.roomMessagesState.collectAsStateWithLifecycle()
  val generalMessagesState by viewModel.generalMessagesState.collectAsStateWithLifecycle()
  val roomActiveExpressions by viewModel.roomActiveExpressions.collectAsStateWithLifecycle()
  val hostsState by viewModel.hostsState.collectAsStateWithLifecycle()
  val linkedFilesState by viewModel.linkedFilesState.collectAsStateWithLifecycle()
  val generatedMediaState by viewModel.generatedMediaState.collectAsStateWithLifecycle()
  val liveExpression by viewModel.liveExpression.collectAsStateWithLifecycle()
  val pendingAttachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
  val busyBotIds by viewModel.busyBotIds.collectAsStateWithLifecycle()
  val isSelectedBotBusy by viewModel.isSelectedBotBusy.collectAsStateWithLifecycle()
  val selectedBotElapsedSec by viewModel.selectedBotElapsedSec.collectAsStateWithLifecycle()
  val selectedBotStreamingPreview by viewModel.selectedBotStreamingPreview.collectAsStateWithLifecycle()
  val isSelectedGeneralBusy by viewModel.isSelectedGeneralBusy.collectAsStateWithLifecycle()
  val generalElapsedSec by viewModel.generalElapsedSec.collectAsStateWithLifecycle()
  val generalStreamingPreview by viewModel.generalStreamingPreview.collectAsStateWithLifecycle()
  val backgroundTasks by viewModel.backgroundTasks.collectAsStateWithLifecycle()
  val cafeSessions by viewModel.cafeSessions.collectAsStateWithLifecycle()
  val isRoomBusy by viewModel.isRoomBusy.collectAsStateWithLifecycle()
  val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
  val localPrefs by viewModel.localPrefs.collectAsStateWithLifecycle()
  val bridgeStatus by viewModel.bridgeStatus.collectAsStateWithLifecycle()
  val pingResult by viewModel.pingResult.collectAsStateWithLifecycle()
  val controlLog by viewModel.controlLog.collectAsStateWithLifecycle()
  val veniceLiveModels by viewModel.veniceLiveModels.collectAsStateWithLifecycle()
  val isFetchingVeniceModels by viewModel.isFetchingVeniceModels.collectAsStateWithLifecycle()
  val pendingChatDraft by viewModel.pendingChatDraft.collectAsStateWithLifecycle()
  val connectionFailureBanner by viewModel.connectionFailureBanner.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(statusBanner) {
    statusBanner?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearBanner()
    }
  }

  val bots = (botsState as? UiState.Success)?.data.orEmpty()
  val rooms = (chatRoomsState as? UiState.Success)?.data.orEmpty()
  val hosts = (hostsState as? UiState.Success)?.data.orEmpty()
  val primaryHost = hosts.firstOrNull()
  val activeBot = bots.firstOrNull { it.id == selectedBotId } ?: bots.firstOrNull()
  val isHomeRoute = currentRoute == ROUTE_HOME
  val isGeneralRoute = currentRoute == ROUTE_GENERAL
  val isTakeoverRoute = currentRoute == ROUTE_TAKEOVER
  val isFilesRoute = currentRoute == ROUTE_FILES
  val isSettingsRoute = currentRoute == ROUTE_SETTINGS
  val isNeonPinkRoute = currentRoute == ROUTE_CHAT || isTakeoverRoute
  val useNeonChrome = true
  val showNavChrome = currentRoute in setOf(ROUTE_HOME, ROUTE_GENERAL, ROUTE_TAKEOVER, ROUTE_FILES, ROUTE_SETTINGS)

  val navigateToTopTab: (String) -> Unit = { targetRoute ->
    if (targetRoute == ROUTE_HOME) {
      if (currentRoute != ROUTE_HOME) {
        val popped = navController.popBackStack(ROUTE_HOME, inclusive = false)
        if (!popped) {
          navController.navigate(ROUTE_HOME) {
            popUpTo(navController.graph.findStartDestination().id) {
              inclusive = true
            }
            launchSingleTop = true
          }
        }
      }
    } else if (currentRoute != targetRoute) {
      navController.navigate(targetRoute) {
        popUpTo(navController.graph.findStartDestination().id) {
          saveState = true
        }
        launchSingleTop = true
        restoreState = true
      }
    }
  }

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxSize()
      .then(
        when {
          isNeonPinkRoute -> {
            Modifier
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    Color(0xFFFF1A8C),
                    Color(0xFFFF3DA8),
                    Color(0xFFE6007A),
                  )
                )
              )
              .background(
                Brush.radialGradient(
                  colors = listOf(
                    Color(0x55FF9CE6),
                    Color.Transparent,
                  ),
                  center = Offset(540f, 380f),
                  radius = 850f,
                )
              )
          }
          isGeneralRoute -> {
            Modifier.background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF6A00F4),
                  Color(0xFFFF6D00),
                )
              )
            )
          }
          isFilesRoute || isSettingsRoute -> {
            Modifier.background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0xFF5D00D6),
                  Color(0xFF8E1CFF),
                  Color(0xFF42009E),
                )
              )
            )
          }
          else -> Modifier
        }
      ),
  ) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      containerColor = when {
        isNeonPinkRoute || isGeneralRoute || isFilesRoute || isSettingsRoute -> Color.Transparent
        isHomeRoute -> Color(0xFF280054)
        else -> MaterialTheme.colorScheme.background
      },
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (showNavChrome && !useRail) {
          NavigationBar(
            windowInsets = WindowInsets.navigationBars,
            containerColor = Color(0xFF5800C7),
          ) {
            topTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTopTab(tab.route) },
                icon = {
                  Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else Color(0xFFFFA8E6),
                    modifier = Modifier.size(25.dp),
                  )
                },
                label = {
                  Text(
                    text = tab.label,
                    fontSize = 11.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                  )
                },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  indicatorColor = Color(0xAAFF2E93),
                  unselectedIconColor = Color(0xFFFFA8E6),
                  unselectedTextColor = Color(0xFFFFC7EE),
                ),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (showNavChrome && useRail) {
          NavigationRail(
            modifier = Modifier
              .fillMaxHeight()
              .background(
                Brush.verticalGradient(
                  colors = listOf(
                    Color(0xFF6B00F5),
                    Color(0xFF8B1EFF),
                    Color(0xFF4A00AE),
                  )
                )
              ),
            containerColor = Color.Transparent,
          ) {
            Spacer(Modifier.height(6.dp))
            topTabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTopTab(tab.route) },
                icon = {
                  Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else Color(0xFFFFA8E6),
                    modifier = Modifier.size(26.dp),
                  )
                },
                label = {
                  Text(
                    text = tab.label,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                  )
                },
                colors = NavigationRailItemDefaults.colors(
                  selectedIconColor = Color.White,
                  selectedTextColor = Color.White,
                  indicatorColor = if (tab.route == ROUTE_GENERAL) Color(0xCCFF6D00) else Color(0xCCFF2E93),
                  unselectedIconColor = Color(0xFFFFA8E6),
                  unselectedTextColor = Color(0xFFFFC7EE),
                ),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(
            navController = navController,
            startDestination = ROUTE_HOME,
          ) {
            composable(ROUTE_HOME) {
              ChatsHomeScreen(
                botsState = botsState,
                chatRoomsState = chatRoomsState,
                userEmail = userEmail,
                busyBotIds = busyBotIds,
                onOpenBotChat = { bot ->
                  viewModel.selectBot(bot.id)
                  navController.navigate(ROUTE_CHAT)
                },
                onOpenChatRoom = { room ->
                  viewModel.selectRoom(room.id)
                  navController.navigate("room/${room.id}")
                },
                onAddNewBot = { navController.navigate("bot_studio?botId=") },
                onCreateChatRoom = { title, topic, botIds, maxTurns ->
                  viewModel.createChatRoom(title, topic, botIds, maxTurns) { newRoomId ->
                    navController.navigate("room/$newRoomId")
                  }
                },
                onCreateTranslationRoom = { viewModel.createTranslationRoom() },
                onCreateProofreadingRoom = { viewModel.createProofreadingRoom() },
                onCreateCodingReviewRoom = { viewModel.createCodingReviewRoom() },
                  onUpdateBotMission = viewModel::updateBotMission,
                onDeleteBot = viewModel::deleteBot,
                onDeleteRoom = { roomId -> viewModel.deleteChatRoom(roomId) },
                onOpenHostSettings = { navigateToTopTab(ROUTE_SETTINGS) },
              )
            }

            composable(ROUTE_GENERAL) {
              GeneralChatScreen(
                generalChatsState = generalChatsState,
                selectedChatId = selectedGeneralChatId,
                messagesState = generalMessagesState,
                hostsState = hostsState,
                linkedFilesState = linkedFilesState,
                pendingAttachments = pendingAttachments,
                selectedWheelModel = selectedWheelModel,
                isBusy = isSelectedGeneralBusy,
                activeElapsedSec = generalElapsedSec,
                streamingPreviewText = generalStreamingPreview,
                connectionFailureBanner = connectionFailureBanner,
                onSelectChat = viewModel::selectGeneralChat,
                onCreateNewChat = { wheel -> viewModel.createGeneralChat(wheel) },
                onTogglePinChat = viewModel::togglePinGeneralChat,
                onDeleteChat = viewModel::deleteGeneralChat,
                onSelectWheelModel = viewModel::selectWheelModel,
                onToggleHostConnectionMode = { host -> viewModel.toggleHostConnectionMode(host) },
                onSwitchModeAndRetry = { banner ->
                  viewModel.toggleHostConnectionMode(banner.host) { updatedHost ->
                    banner.retryAction(updatedHost)
                  }
                },
                onDismissFailureBanner = viewModel::dismissConnectionFailureBanner,
                onAbortRequest = { viewModel.abortGeneralRequest() },
                onSendMessage = { thread, text, wheel, host ->
                  viewModel.sendGeneralMessage(thread, text, wheel, host)
                },
                onAttachDeviceFile = { name, uri, mime, size ->
                  viewModel.addLinkedFile(
                    displayName = name,
                    uriOrPath = uri,
                    linkType = FileLinkType.ANDROID_ATTACHMENT,
                    mimeOrExt = mime,
                    sizeBytes = size,
                    notes = "Attached in general chat",
                    attachToComposer = true,
                  )
                },
                onToggleAttachment = viewModel::togglePendingAttachment,
                onShowBanner = viewModel::showBanner,
                onOpenHostSettings = { navigateToTopTab(ROUTE_SETTINGS) },
              )
            }

            composable(ROUTE_CHAT) {
              ChatWorkspaceScreen(
                botsState = botsState,
                selectedBotId = selectedBotId,
                messagesState = messagesState,
                hostsState = hostsState,
                linkedFilesState = linkedFilesState,
                liveExpression = liveExpression,
                pendingAttachments = pendingAttachments,
                selectedWheelModel = selectedWheelModel,
                isBusy = isSelectedBotBusy,
                busyBotIds = busyBotIds,
                backgroundTasks = backgroundTasks,
                activeElapsedSec = selectedBotElapsedSec,
                streamingPreviewText = selectedBotStreamingPreview,
                connectionFailureBanner = connectionFailureBanner,
                pendingChatDraft = pendingChatDraft,
                savedChatDraft = localPrefs.chatDrafts[selectedBotId],
                onChatDraftChanged = viewModel::saveChatDraft,
                onConsumePendingChatDraft = viewModel::consumePendingChatDraft,
                onShowBanner = viewModel::showBanner,
                onSelectBot = viewModel::selectBot,
                onSelectWheelModel = { option -> viewModel.selectWheelModel(option, null) },
                onSwitchModeAndRetry = { banner ->
                  viewModel.toggleHostConnectionMode(banner.host) { updatedHost ->
                    banner.retryAction(updatedHost)
                  }
                },
                onDismissFailureBanner = viewModel::dismissConnectionFailureBanner,
                onAbortRequest = { viewModel.abortBotRequest() },
                onSpawnBackgroundTask = viewModel::spawnBackgroundTaskForBot,
                onAbortBackgroundTask = viewModel::abortBackgroundTask,
                onBack = { navController.popBackStack() },
                onAddBotClick = { navController.navigate("bot_studio?botId=") },
                onEditBotClick = { bot -> navController.navigate("bot_studio?botId=${bot.id}") },
                onRenameBot = viewModel::renameBot,
                onUpdateMission = viewModel::updateBotMission,
                onDeleteBotClick = viewModel::deleteBot,
                onSendMessage = { text, bot, host, wheel ->
                  viewModel.sendCasualMessage(text, bot, host, wheel)
                },
                onRetryMessage = viewModel::retryDispatchMessage,
                onAttachDeviceFile = { name, uri, mime, size ->
                  viewModel.addLinkedFile(
                    displayName = name,
                    uriOrPath = uri,
                    linkType = FileLinkType.ANDROID_ATTACHMENT,
                    mimeOrExt = mime,
                    sizeBytes = size,
                    notes = "Attached in chat",
                    attachToComposer = true,
                  )
                },
                onAddHostPathLink = { name, path ->
                  val isUrl = path.startsWith("http://") || path.startsWith("https://")
                  viewModel.addLinkedFile(
                    displayName = name,
                    uriOrPath = path,
                    linkType = if (isUrl) FileLinkType.CLOUD_URL else FileLinkType.HOST_PATH,
                    mimeOrExt = "",
                    sizeBytes = 0L,
                    notes = "Linked in chat",
                    attachToComposer = true,
                  )
                },
                onToggleAttachment = viewModel::togglePendingAttachment,
                onPreviewExpression = viewModel::setLiveExpression,
                onOpenTakeover = { navController.navigate(ROUTE_TAKEOVER) },
                onOpenHostSettings = { navigateToTopTab(ROUTE_SETTINGS) },
              )
            }

            composable(
              route = ROUTE_ROOM,
              arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
            ) { backStackEntry ->
              val roomId = backStackEntry.arguments?.getString("roomId").orEmpty()
              LaunchedEffect(roomId) {
                if (roomId.isNotBlank()) viewModel.selectRoom(roomId)
              }
              val room = rooms.firstOrNull { it.id == roomId }
              ChatRoomScreen(
                room = room,
                allBots = bots,
                roomMessagesState = roomMessagesState,
                hostsState = hostsState,
                activeExpressions = roomActiveExpressions,
                selectedWheelModel = selectedWheelModel,
                isBusy = isRoomBusy,
                cafeSession = cafeSessions[roomId],
                onShowBanner = viewModel::showBanner,
                onBack = { navController.popBackStack() },
                onSelectWheelModel = { option -> viewModel.selectWheelModel(option, null) },
                onPauseCafe = viewModel::pauseCafeDiscussion,
                onResumeCafe = viewModel::resumeCafeDiscussion,
                onStopCafe = viewModel::stopCafeDiscussion,
                onSendRoomMessage = { r, p, h, prompt, postBubble, wheel, targetId ->
                  viewModel.sendRoomMessage(r, p, h, prompt, postBubble, wheel, targetId)
                },
                onExportTranscript = { room, msgs, parts, host ->
                  viewModel.exportRoomTranscript(room, msgs, parts, host)
                },
                onUpdateRoom = viewModel::updateChatRoom,
                onUpdateBotMission = viewModel::updateBotMission,
                onDispatchToOpenClaw = viewModel::dispatchRoomInstructionToHost,
              )
            }

            composable(
              route = ROUTE_BOT_STUDIO,
              arguments = listOf(navArgument("botId") { type = NavType.StringType; defaultValue = "" }),
            ) { backStackEntry ->
              val botId = backStackEntry.arguments?.getString("botId").orEmpty()
              val existing = bots.firstOrNull { it.id == botId }
              BotStudioScreen(
                existingBot = existing,
                currentUserId = currentUserId,
                activeHost = primaryHost,
                globalVeniceModel = localPrefs.globalVeniceModel,
                veniceLiveModels = veniceLiveModels,
                isFetchingVeniceModels = isFetchingVeniceModels,
                onFetchVeniceModels = { viewModel.fetchLiveVeniceModels() },
                onSaveBot = { bot ->
                  viewModel.saveBot(bot) {
                    navController.popBackStack()
                  }
                },
                onSyncWithOpenClaw = viewModel::syncBotWithOpenClaw,
                onDeleteBot = { id ->
                  viewModel.deleteBot(id)
                  navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
              )
            }

            composable(ROUTE_TAKEOVER) {
              ComputerTakeoverScreen(
                hostsState = hostsState,
                activeBot = activeBot,
                takeoverActive = localPrefs.takeoverActive,
                hostPaused = localPrefs.hostPaused,
                pingResult = pingResult,
                controlLog = controlLog,
                onPingHost = viewModel::pingHost,
                onSendControl = viewModel::sendHostControlAction,
                bookmarks = localPrefs.urlBookmarks,
                onAddBookmark = viewModel::addUrlBookmark,
                onRemoveBookmark = viewModel::removeUrlBookmark,
                onOpenHostSettings = { navigateToTopTab(ROUTE_SETTINGS) },
              )
            }

            composable(ROUTE_FILES) {
              FilesVaultScreen(
                linkedFilesState = linkedFilesState,
                generatedMediaState = generatedMediaState,
                activeHost = primaryHost,
                onAddFile = viewModel::addLinkedFile,
                onQueueForChat = { file ->
                  viewModel.togglePendingAttachment(file)
                  navController.navigate(ROUTE_CHAT)
                },
                onSendMediaToChat = { item ->
                  viewModel.queueMediaDraftToBotChat(item)
                  navController.navigate(ROUTE_CHAT)
                },
                onDeleteFile = viewModel::deleteLinkedFile,
                onDeleteMedia = viewModel::deleteGeneratedMedia,
                onShowBanner = viewModel::showBanner,
                onOpenHostSettings = { navigateToTopTab(ROUTE_SETTINGS) },
              )
            }

            composable(ROUTE_SETTINGS) {
              HostSettingsScreen(
                hostsState = hostsState,
                currentUserId = currentUserId,
                userEmail = userEmail,
                pingResult = pingResult,
                savedVeniceApiKey = localPrefs.veniceApiKey,
                savedGoogleApiKey = localPrefs.googleApiKey,
                savedGlobalVeniceModel = localPrefs.globalVeniceModel,
                savedDevicePairingId = localPrefs.devicePairingId,
                savedBridgeEnabled = localPrefs.driveBridgeEnabled,
                savedBridgeFolderUri = localPrefs.driveBridgeFolderUri,
                bridgeStatus = bridgeStatus,
                onInspectBridgeFolder = viewModel::inspectBridgeFolder,
                veniceLiveModels = veniceLiveModels,
                isFetchingVeniceModels = isFetchingVeniceModels,
                onFetchVeniceModels = viewModel::fetchLiveVeniceModels,
                onBack = { navigateToTopTab(ROUTE_HOME) },
                onSaveHost = { host, veniceKey, googleKey, globalModel, deviceId, bridgeEnabled, bridgeUri ->
                  viewModel.saveHost(
                    host = host,
                    veniceApiKey = veniceKey,
                    googleApiKey = googleKey,
                    globalVeniceModel = globalModel,
                    devicePairingId = deviceId,
                    driveBridgeEnabled = bridgeEnabled,
                    driveBridgeFolderUri = bridgeUri,
                  )
                },
                onDeleteHost = viewModel::deleteHost,
                onPingHost = viewModel::pingHost,
                onSignOut = {
                  scope.launch {
                    withContext(NonCancellable) {
                      try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                      } catch (e: Exception) {
                        Log.w("ClawPalAuth", "Failed to clear credential state: ${e.message}")
                      } finally {
                        Firebase.auth.signOut()
                      }
                    }
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}
