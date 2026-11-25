package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AccessoryStyle
import com.example.model.BotArchetype
import com.example.model.BotExpression
import com.example.model.HatStyle
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.avatar.CaptainContextAvatar

private val ComicInk = Color(0xFF20150F)

enum class WhimsicalNavDestination(
  val route: String,
  val label: String,
  val tileBgHex: Long,
  val tileDeepHex: Long,
  val activeBorderHex: Long,
) {
  BOT_DUDES("home", "Bot Dudes", 0xFFFF2E93, 0xFFB8005E, 0xFFFFEB3B),
  GENERAL("general", "General", 0xFFFF6D00, 0xFFCC4B00, 0xFFFFF59D),
  CAPTAIN_CONTEXT("captain_context", "Cap. Context", 0xFF1E5BFF, 0xFF0E349E, 0xFFFFD54F),
  TAKEOVER("takeover", "Takeover", 0xFF9C27B0, 0xFF620D78, 0xFF80DEEA),
  FILES("files", "Files", 0xFF00BFA5, 0xFF007D6B, 0xFFFFF59D),
  HOST("settings", "Host", 0xFFFFB300, 0xFFC77400, 0xFFFFFFFF),
}

/**
 * Chunky 3x2 comic tile dock taking ~24% of phone screen height with live animated mascots.
 */
@Composable
fun WhimsicalBottomDock(
  currentRoute: String,
  dockHeight: Dp,
  busyRoutes: Set<String> = emptySet(),
  onSelectDestination: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val row1 = remember {
    listOf(
      WhimsicalNavDestination.BOT_DUDES,
      WhimsicalNavDestination.GENERAL,
      WhimsicalNavDestination.CAPTAIN_CONTEXT,
    )
  }
  val row2 = remember {
    listOf(
      WhimsicalNavDestination.TAKEOVER,
      WhimsicalNavDestination.FILES,
      WhimsicalNavDestination.HOST,
    )
  }
  val avatarSize = if (dockHeight >= 186.dp) 46.dp else 40.dp

  Surface(
    color = Color(0xFF240548),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
      HorizontalDivider(thickness = 2.5.dp, color = Color(0xFFFF6BC8))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .height(dockHeight)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color(0xFF390870),
                Color(0xFF230447),
              )
            )
          )
          .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          row1.forEach { dest ->
            WhimsicalDockTile(
              destination = dest,
              selected = currentRoute == dest.route,
              isBusy = dest.route in busyRoutes,
              avatarSize = avatarSize,
              onClick = { onSelectDestination(dest.route) },
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )
          }
        }
        Row(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          row2.forEach { dest ->
            WhimsicalDockTile(
              destination = dest,
              selected = currentRoute == dest.route,
              isBusy = dest.route in busyRoutes,
              avatarSize = avatarSize,
              onClick = { onSelectDestination(dest.route) },
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )
          }
        }
      }
    }
  }
}

@Composable
fun WhimsicalNavRail(
  currentRoute: String,
  busyRoutes: Set<String> = emptySet(),
  onSelectDestination: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = Color(0xFF2A0556),
    border = BorderStroke(2.dp, Color(0xFFFF6BC8)),
    modifier = modifier
      .width(102.dp)
      .fillMaxHeight(),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      WhimsicalNavDestination.entries.forEach { dest ->
        WhimsicalDockTile(
          destination = dest,
          selected = currentRoute == dest.route,
          isBusy = dest.route in busyRoutes,
          avatarSize = 44.dp,
          onClick = { onSelectDestination(dest.route) },
          modifier = Modifier
            .fillMaxWidth()
            .height(86.dp),
        )
      }
    }
  }
}

@Composable
private fun WhimsicalDockTile(
  destination: WhimsicalNavDestination,
  selected: Boolean,
  isBusy: Boolean,
  avatarSize: Dp,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = when {
      pressed -> 0.94f
      selected -> 1.03f
      else -> 1f
    },
    animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
    label = "tile_scale_${destination.route}",
  )

  val busyAlpha = if (isBusy) {
    val infinite = rememberInfiniteTransition(label = "tile_pulse_${destination.route}")
    infinite.animateFloat(
      initialValue = 0.45f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 550),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "busy_badge_alpha",
    ).value
  } else {
    1.0f
  }

  val expression = when {
    isBusy -> BotExpression.WORKING
    selected -> BotExpression.UH_HUH
    else -> BotExpression.HAPPY
  }

  val baseColor = Color(destination.tileBgHex)
  val deepColor = Color(destination.tileDeepHex)
  val activeRing = Color(destination.activeBorderHex)

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(16.dp),
    color = Color.Transparent,
    shadowElevation = if (selected) 6.dp else 1.dp,
    border = BorderStroke(
      width = if (selected) 3.dp else 2.dp,
      color = if (selected) activeRing else ComicInk,
    ),
    modifier = modifier
      .minimumInteractiveComponentSize()
      .scale(scale)
      .testTag("nav_tab_${destination.route}"),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = if (selected) {
              listOf(baseColor, deepColor)
            } else {
              listOf(
                baseColor.copy(alpha = 0.62f),
                deepColor.copy(alpha = 0.72f),
              )
            }
          )
        )
        .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        NavTileCharacterAvatar(
          destination = destination,
          expression = expression,
          size = avatarSize,
          animate = selected || isBusy,
        )
        Text(
          text = destination.label,
          style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 11.5.sp,
            lineHeight = 13.sp,
            shadow = Shadow(
              color = ComicInk,
              offset = Offset(0f, 1.5f),
              blurRadius = 2.5f,
            ),
          ),
          fontWeight = FontWeight.ExtraBold,
          color = if (selected) Color.White else Color(0xFFFFF4FA),
          textAlign = TextAlign.Center,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Clip,
        )
      }

      if (isBusy) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(3.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFEB3B).copy(alpha = busyAlpha)),
        )
      } else if (selected) {
        Box(
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(3.dp)
            .size(8.dp)
            .clip(CircleShape)
            .background(activeRing),
        )
      }
    }
  }
}

@Composable
private fun NavTileCharacterAvatar(
  destination: WhimsicalNavDestination,
  expression: BotExpression,
  size: Dp,
  animate: Boolean,
) {
  when (destination) {
    WhimsicalNavDestination.BOT_DUDES -> BotVectorAvatar(
      archetype = BotArchetype.CHINESE_MASTER,
      hatStyle = HatStyle.NONE,
      accessoryStyle = AccessoryStyle.SPARK_BADGE,
      primaryColor = Color(0xFFF3B277),
      accentColor = Color(0xFFFF2E93),
      expression = expression,
      size = size,
      animate = animate,
    )
    WhimsicalNavDestination.GENERAL -> BotVectorAvatar(
      archetype = BotArchetype.GLASSES_FELLOW,
      hatStyle = HatStyle.NONE,
      accessoryStyle = AccessoryStyle.FOREHEAD_GLASSES,
      primaryColor = Color(0xFFEEA05E),
      accentColor = Color(0xFFFF6D00),
      expression = expression,
      size = size,
      animate = animate,
    )
    WhimsicalNavDestination.CAPTAIN_CONTEXT -> CaptainContextAvatar(
      expression = expression,
      size = size,
      animate = animate,
      showCapeAndScroll = false,
    )
    WhimsicalNavDestination.TAKEOVER -> BotVectorAvatar(
      archetype = BotArchetype.RETRO_ROBOT,
      hatStyle = HatStyle.ANTENNA_DISH,
      accessoryStyle = AccessoryStyle.NONE,
      primaryColor = Color(0xFF90D5EC),
      accentColor = Color(0xFFFFC93C),
      expression = expression,
      size = size,
      animate = animate,
    )
    WhimsicalNavDestination.FILES -> BotVectorAvatar(
      archetype = BotArchetype.GOBLIN,
      hatStyle = HatStyle.CAP,
      accessoryStyle = AccessoryStyle.ROUND_GLASSES,
      primaryColor = Color(0xFF81C784),
      accentColor = Color(0xFFFF8A65),
      expression = expression,
      size = size,
      animate = animate,
    )
    WhimsicalNavDestination.HOST -> BotVectorAvatar(
      archetype = BotArchetype.PROFESSOR,
      hatStyle = HatStyle.MORTARBOARD,
      accessoryStyle = AccessoryStyle.MONOCLE,
      primaryColor = Color(0xFFF4C592),
      accentColor = Color(0xFF5C6BC0),
      expression = expression,
      size = size,
      animate = animate,
    )
  }
}
