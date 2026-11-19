package com.example.ui.avatar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BotExpression
import com.example.model.CompanionBot

private val CafeInk = Color(0xFF20150F)

val NeonPinkPurpleBrush = Brush.verticalGradient(
  colors = listOf(
    Color(0xFF190629),
    Color(0xFF330A4D),
    Color(0xFF6B106E),
    Color(0xFF9C1677),
  )
)

@Composable
fun MultiBotRoomIcon(
  bots: List<CompanionBot>,
  expressions: Map<String, BotExpression> = emptyMap(),
  size: Dp = 54.dp,
  animate: Boolean = true,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = CircleShape,
    color = Color(0xFF2B0A42),
    border = BorderStroke(1.8.dp, Color(0xFFFF58B8)),
    modifier = modifier.size(size),
  ) {
    Box(contentAlignment = Alignment.Center) {
      val shown = bots.take(3)
      when (shown.size) {
        0 -> Unit
        1 -> {
          BotVectorAvatar(
            bot = shown[0],
            expression = expressions[shown[0].id] ?: BotExpression.HAPPY,
            size = size * 0.86f,
            animate = animate,
          )
        }
        2 -> {
          Box(
            modifier = Modifier
              .offset(x = (-size * 0.17f), y = (size * 0.04f))
              .size(size * 0.62f)
              .clip(CircleShape)
              .background(Color(0xFF3A0F56))
              .border(1.dp, Color(0xFFFF79CE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            BotVectorAvatar(
              bot = shown[0],
              expression = expressions[shown[0].id] ?: BotExpression.HAPPY,
              size = size * 0.58f,
              animate = animate,
            )
          }
          Box(
            modifier = Modifier
              .offset(x = (size * 0.17f), y = (-size * 0.04f))
              .size(size * 0.62f)
              .clip(CircleShape)
              .background(Color(0xFF471269))
              .border(1.dp, Color(0xFFFF79CE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            BotVectorAvatar(
              bot = shown[1],
              expression = expressions[shown[1].id] ?: BotExpression.UH_HUH,
              size = size * 0.58f,
              animate = animate,
            )
          }
        }
        else -> {
          Box(
            modifier = Modifier
              .offset(x = 0.dp, y = (-size * 0.17f))
              .size(size * 0.52f)
              .clip(CircleShape)
              .background(Color(0xFF3E105E))
              .border(1.dp, Color(0xFFFF79CE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            BotVectorAvatar(
              bot = shown[2],
              expression = expressions[shown[2].id] ?: BotExpression.HAPPY,
              size = size * 0.48f,
              animate = animate,
            )
          }
          Box(
            modifier = Modifier
              .offset(x = (-size * 0.18f), y = (size * 0.15f))
              .size(size * 0.54f)
              .clip(CircleShape)
              .background(Color(0xFF340C4F))
              .border(1.dp, Color(0xFFFF79CE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            BotVectorAvatar(
              bot = shown[0],
              expression = expressions[shown[0].id] ?: BotExpression.HAPPY,
              size = size * 0.5f,
              animate = animate,
            )
          }
          Box(
            modifier = Modifier
              .offset(x = (size * 0.18f), y = (size * 0.15f))
              .size(size * 0.54f)
              .clip(CircleShape)
              .background(Color(0xFF49126C))
              .border(1.dp, Color(0xFFFF79CE), CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            BotVectorAvatar(
              bot = shown[1],
              expression = expressions[shown[1].id] ?: BotExpression.UH_HUH,
              size = size * 0.5f,
              animate = animate,
            )
          }
        }
      }
    }
  }
}

@Composable
fun CafeBackgroundCanvas(
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val strokeW = (minOf(w, h) * 0.016f).coerceIn(2.2f, 4.5f)
    val ink = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Cozy warm cream-peach cafe wall with subtle neon-pink dusk warmth
    drawRect(
      brush = Brush.verticalGradient(
        colors = listOf(
          Color(0xFFFFE5D0),
          Color(0xFFFFD6C4),
          Color(0xFFF6C2BA),
        )
      )
    )

    // Subtle striped wallpaper lines
    val stripeStep = w / 12f
    for (i in 0..12) {
      drawLine(
        color = Color(0x22D9659E),
        start = Offset(i * stripeStep, 0f),
        end = Offset(i * stripeStep, h),
        strokeWidth = 2f,
      )
    }

    // Center arched window looking out at neon pink-purple evening sky
    val winW = w * 0.42f
    val winH = h * 0.58f
    val winLeft = (w - winW) / 2f
    val winTop = h * 0.18f
    val winPath = Path().apply {
      moveTo(winLeft, winTop + winH)
      lineTo(winLeft, winTop + winW * 0.42f)
      cubicTo(
        winLeft, winTop,
        winLeft + winW, winTop,
        winLeft + winW, winTop + winW * 0.42f,
      )
      lineTo(winLeft + winW, winTop + winH)
      close()
    }
    drawPath(
      path = winPath,
      brush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2D0B4E), Color(0xFF8E2479), Color(0xFFFF70B8)),
        startY = winTop,
        endY = winTop + winH,
      ),
    )
    // Crescent moon & stars in cafe window
    drawCircle(
      color = Color(0xFFFFE8A3),
      radius = winW * 0.08f,
      center = Offset(winLeft + winW * 0.72f, winTop + winH * 0.28f),
    )
    drawCircle(
      color = Color(0xFF421059),
      radius = winW * 0.068f,
      center = Offset(winLeft + winW * 0.76f, winTop + winH * 0.25f),
    )
    listOf(
      Offset(winLeft + winW * 0.26f, winTop + winH * 0.24f),
      Offset(winLeft + winW * 0.44f, winTop + winH * 0.18f),
      Offset(winLeft + winW * 0.34f, winTop + winH * 0.44f),
    ).forEach { star ->
      drawCircle(color = Color(0xFFFFF1C9), radius = 3.2f, center = star)
    }
    // Window frame & mullions
    drawPath(winPath, CafeInk, style = ink)
    drawLine(
      CafeInk,
      Offset(winLeft + winW / 2f, winTop + winW * 0.08f),
      Offset(winLeft + winW / 2f, winTop + winH),
      strokeWidth = strokeW * 0.75f,
    )
    drawLine(
      CafeInk,
      Offset(winLeft, winTop + winH * 0.56f),
      Offset(winLeft + winW, winTop + winH * 0.56f),
      strokeWidth = strokeW * 0.75f,
    )

    // Left menu chalkboard ("CLAW CAFE")
    val boardW = w * 0.18f
    val boardH = h * 0.34f
    val boardLeft = w * 0.055f
    val boardTop = h * 0.28f
    drawRoundRect(
      color = Color(0xFF33262E),
      topLeft = Offset(boardLeft, boardTop),
      size = Size(boardW, boardH),
      cornerRadius = CornerRadius(10f, 10f),
    )
    drawRoundRect(
      color = Color(0xFFD98754),
      topLeft = Offset(boardLeft, boardTop),
      size = Size(boardW, boardH),
      cornerRadius = CornerRadius(10f, 10f),
      style = Stroke(width = strokeW * 1.4f),
    )
    drawRoundRect(
      color = CafeInk,
      topLeft = Offset(boardLeft, boardTop),
      size = Size(boardW, boardH),
      cornerRadius = CornerRadius(10f, 10f),
      style = ink,
    )
    // Chalk squiggles on menu board
    for (row in 0..2) {
      val ly = boardTop + boardH * (0.28f + row * 0.22f)
      drawLine(
        color = Color(0xFFFFC2E2),
        start = Offset(boardLeft + boardW * 0.18f, ly),
        end = Offset(boardLeft + boardW * (0.78f - row * 0.08f), ly),
        strokeWidth = 3f,
        cap = StrokeCap.Round,
      )
    }

    // Right warm glowing pendant lamp & potted plant
    val lampX = w * 0.84f
    drawLine(CafeInk, Offset(lampX, 0f), Offset(lampX, h * 0.22f), strokeWidth = strokeW * 0.8f)
    drawCircle(
      color = Color(0x55FFDF80),
      radius = w * 0.09f,
      center = Offset(lampX, h * 0.27f),
    )
    val shade = Path().apply {
      moveTo(lampX - w * 0.055f, h * 0.27f)
      quadraticTo(lampX, h * 0.18f, lampX + w * 0.055f, h * 0.27f)
      close()
    }
    drawPath(shade, Color(0xFFFF7EB6))
    drawPath(shade, CafeInk, style = ink)

    // Scalloped cafe awning across the top
    val awningH = h * 0.13f
    val stripes = 8
    val stripeW = w / stripes
    for (i in 0 until stripes) {
      val sx = i * stripeW
      val col = if (i % 2 == 0) Color(0xFFFF5E9C) else Color(0xFFFFF4E0)
      val flap = Path().apply {
        moveTo(sx, 0f)
        lineTo(sx + stripeW, 0f)
        lineTo(sx + stripeW, awningH * 0.72f)
        quadraticTo(sx + stripeW * 0.5f, awningH * 1.15f, sx, awningH * 0.72f)
        close()
      }
      drawPath(flap, col)
      drawPath(flap, CafeInk, style = ink)
    }

    // Cozy wooden cafe counter along the bottom
    val counterY = h * 0.76f
    drawRect(
      color = Color(0xFFD58A52),
      topLeft = Offset(0f, counterY),
      size = Size(w, h - counterY),
    )
    drawRect(
      color = Color(0xFFEBA86F),
      topLeft = Offset(0f, counterY),
      size = Size(w, h * 0.06f),
    )
    drawLine(CafeInk, Offset(0f, counterY), Offset(w, counterY), strokeWidth = strokeW)
    drawLine(CafeInk, Offset(0f, counterY + h * 0.06f), Offset(w, counterY + h * 0.06f), strokeWidth = strokeW * 0.7f)

    // Cute steaming coffee cup on right side of counter & croissant on left
    drawCuteMug(
      center = Offset(w * 0.89f, counterY - h * 0.03f),
      scale = (h * 0.0042f).coerceIn(0.4f, 1.1f),
      ink = ink,
    )
    drawCuteMug(
      center = Offset(w * 0.11f, counterY - h * 0.03f),
      scale = (h * 0.0038f).coerceIn(0.35f, 1.0f),
      ink = ink,
      cupColor = Color(0xFF9DE0DC),
    )
  }
}

private fun DrawScope.drawCuteMug(
  center: Offset,
  scale: Float,
  ink: Stroke,
  cupColor: Color = Color(0xFFFFF7EC),
) {
  val mugW = 28f * scale
  val mugH = 22f * scale
  val left = center.x - mugW / 2f
  val top = center.y - mugH / 2f
  // Handle
  drawCircle(
    color = CafeInk,
    radius = 7f * scale,
    center = Offset(left + mugW + 2f * scale, top + mugH * 0.48f),
    style = ink,
  )
  drawRoundRect(
    color = cupColor,
    topLeft = Offset(left, top),
    size = Size(mugW, mugH),
    cornerRadius = CornerRadius(6f * scale, 6f * scale),
  )
  drawRoundRect(
    color = CafeInk,
    topLeft = Offset(left, top),
    size = Size(mugW, mugH),
    cornerRadius = CornerRadius(6f * scale, 6f * scale),
    style = ink,
  )
  // Steam wisps
  listOf(-5f, 5f).forEach { dx ->
    val steam = Path().apply {
      moveTo(center.x + dx * scale, top - 3f * scale)
      cubicTo(
        center.x + (dx - 4f) * scale, top - 10f * scale,
        center.x + (dx + 4f) * scale, top - 16f * scale,
        center.x + dx * scale, top - 22f * scale,
      )
    }
    drawPath(steam, Color(0xCCFFFFFF), style = Stroke(width = 2.6f * scale, cap = StrokeCap.Round))
  }
}
