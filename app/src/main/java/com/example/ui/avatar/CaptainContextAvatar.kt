package com.example.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.BotExpression
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private val HeroInk = Color(0xFF20150F)
private val HeroBlue = Color(0xFF1E5BFF)
private val HeroBlueDark = Color(0xFF1037A8)
private val HeroGold = Color(0xFFFFD54F)
private val HeroGoldBright = Color(0xFFFFEE58)
private val HeroCapeRed = Color(0xFFE53935)
private val HeroCapeDark = Color(0xFFB71C1C)
private val HeroSkin = Color(0xFFF6C395)
private val HeroCheek = Color(0xFFE65339)
private val HeroVisorCyan = Color(0xFF40E0FF)
private val HeroParchment = Color(0xFFFFF5D6)
private val HeroSweatBlue = Color(0xFF56B4F7)

@Composable
fun CaptainContextAvatar(
  expression: BotExpression,
  modifier: Modifier = Modifier,
  size: Dp = 120.dp,
  animate: Boolean = true,
  showCapeAndScroll: Boolean = true,
) {
  val bounceTarget = when (expression) {
    BotExpression.UH_HUH -> 1.08f
    BotExpression.WORKING -> 1.04f
    BotExpression.THINKING -> 1.02f
    BotExpression.ALERT -> 1.06f
    BotExpression.SORRY -> 0.96f
    else -> 1f
  }

  val bobPhase: Float
  val blinkCycle: Float
  val sparkPulse: Float
  val exprScale: Float
  val reactionScale: Float

  if (animate) {
    val infinite = rememberInfiniteTransition(label = "captain_context_idle")
    bobPhase = infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2100, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "hero_bob",
    ).value
    blinkCycle = infinite.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3500, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "hero_blink",
    ).value
    sparkPulse = infinite.animateFloat(
      initialValue = 0.78f,
      targetValue = 1.16f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 680, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "hero_spark",
    ).value
    exprScale = animateFloatAsState(
      targetValue = bounceTarget,
      animationSpec = spring(dampingRatio = 0.48f, stiffness = 320f),
      label = "hero_expr_scale",
    ).value
    val reactionPulse = remember { Animatable(1f) }
    LaunchedEffect(expression) {
      reactionPulse.snapTo(1.14f)
      reactionPulse.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 340f),
      )
    }
    reactionScale = reactionPulse.value
  } else {
    bobPhase = 0f
    blinkCycle = 0f
    sparkPulse = 1f
    exprScale = bounceTarget
    reactionScale = 1f
  }

  val isBlinking = animate && blinkCycle > 0.93f &&
    expression != BotExpression.UH_HUH && expression != BotExpression.SORRY

  Box(modifier = modifier.size(size)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val s = minOf(this.size.width, this.size.height) / 240f
      val bobY = if (!animate) {
        0f
      } else {
        when (expression) {
          BotExpression.THINKING -> sin(bobPhase * 1.25f) * 6.5f
          BotExpression.WORKING -> -abs(sin(bobPhase * 2f)) * 9f
          BotExpression.UH_HUH -> -abs(sin(bobPhase * 2f)) * 10f
          BotExpression.ALERT -> -abs(sin(bobPhase * 2.5f)) * 7f
          BotExpression.WORRIED -> sin(bobPhase * 3f) * 3.5f
          BotExpression.SORRY -> sin(bobPhase) * 4f
          BotExpression.HAPPY -> sin(bobPhase) * 5.5f
        }
      }
      val headTilt = when (expression) {
        BotExpression.THINKING -> if (animate) sin(bobPhase) * 7f else 5f
        BotExpression.WORKING -> if (animate) sin(bobPhase * 2f) * 5f else 0f
        BotExpression.UH_HUH -> if (animate) sin(bobPhase * 2f) * 4.5f else 0f
        BotExpression.WORRIED -> if (animate) sin(bobPhase * 4f) * 4.5f else -3f
        BotExpression.SORRY -> if (animate) -6f + sin(bobPhase) * 3f else -5f
        BotExpression.ALERT -> if (animate) sin(bobPhase * 3f) * 5f else 0f
        BotExpression.HAPPY -> if (animate) sin(bobPhase) * 3f else 0f
      }
      val combinedScale = exprScale * reactionScale
      val inkW = 4.6f * s

      translate(
        left = (this.size.width - 240f * s) / 2f,
        top = (this.size.height - 240f * s) / 2f + bobY * s,
      ) {
        rotate(
          degrees = headTilt,
          pivot = Offset(120f * s, 136f * s),
        ) {
          val cx = 120f * s
          val cy = 128f * s
          translate(left = cx * (1f - combinedScale), top = cy * (1f - combinedScale)) {
            drawCaptainContext(
              scale = s * combinedScale,
              inkWidth = inkW * combinedScale,
              expression = expression,
              isBlinking = isBlinking,
              phase = bobPhase,
              sparkPulse = sparkPulse,
              animate = animate,
              showCapeAndScroll = showCapeAndScroll,
            )
          }
        }
      }
    }
  }
}

private fun DrawScope.drawCaptainContext(
  scale: Float,
  inkWidth: Float,
  expression: BotExpression,
  isBlinking: Boolean,
  phase: Float,
  sparkPulse: Float,
  animate: Boolean,
  showCapeAndScroll: Boolean,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val stroke = Stroke(width = inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val thinStroke = Stroke(width = inkWidth * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // 1. Comic halftone hero aura + starburst ring
  drawCircle(
    color = HeroGold.copy(alpha = 0.22f),
    radius = r(102f),
    center = p(120f, 128f),
  )
  drawCircle(
    color = HeroBlue.copy(alpha = 0.18f),
    radius = r(86f),
    center = p(120f, 128f),
  )

  // 2. Fluttering Crimson Cape behind torso/head
  val capeWave = if (animate) sin(phase * 2.5f) * 9f else 0f
  val capeWave2 = if (animate) cos(phase * 2.1f) * 7f else 0f
  val capePath = Path().apply {
    moveTo(r(62f), r(132f))
    cubicTo(r(26f), r(150f + capeWave * 0.4f), r(18f + capeWave), r(202f), r(36f + capeWave), r(222f))
    quadraticTo(r(82f), r(212f + capeWave2 * 0.5f), r(120f), r(226f))
    quadraticTo(r(160f), r(212f - capeWave2 * 0.5f), r(204f - capeWave * 0.8f), r(222f))
    cubicTo(r(222f - capeWave * 0.8f), r(200f), r(214f), r(150f - capeWave * 0.3f), r(178f), r(132f))
    close()
  }
  drawPath(capePath, HeroCapeRed)
  val capeShadow = Path().apply {
    moveTo(r(58f), r(152f))
    quadraticTo(r(42f + capeWave * 0.5f), r(192f), r(56f + capeWave * 0.6f), r(214f))
    quadraticTo(r(74f), r(198f), r(84f), r(172f))
    close()
  }
  drawPath(capeShadow, HeroCapeDark.copy(alpha = 0.55f))
  drawPath(capePath, HeroInk, style = stroke)

  // 3. Hero Shoulders & Chest Emblem ("C")
  val torsoPath = Path().apply {
    moveTo(r(58f), r(172f))
    quadraticTo(r(120f), r(158f), r(182f), r(172f))
    lineTo(r(190f), r(220f))
    quadraticTo(r(120f), r(232f), r(50f), r(220f))
    close()
  }
  drawPath(torsoPath, HeroBlue)
  drawPath(torsoPath, HeroInk, style = stroke)

  // Gold cape clasp straps
  drawCircle(HeroGold, radius = r(9f), center = p(76f, 174f))
  drawCircle(HeroInk, radius = r(9f), center = p(76f, 174f), style = thinStroke)
  drawCircle(HeroGold, radius = r(9f), center = p(164f, 174f))
  drawCircle(HeroInk, radius = r(9f), center = p(164f, 174f), style = thinStroke)

  // Center Gold "C" Crest Medallion
  drawCircle(HeroGold, radius = r(20f), center = p(120f, 198f))
  drawCircle(HeroInk, radius = r(20f), center = p(120f, 198f), style = thinStroke)
  drawArc(
    color = HeroBlueDark,
    startAngle = 45f,
    sweepAngle = 270f,
    useCenter = false,
    topLeft = p(110f, 188f),
    size = Size(r(20f), r(20f)),
    style = Stroke(width = inkWidth * 1.05f, cap = StrokeCap.Round),
  )

  // 4. Pointed Superhero Ear-Fins
  val leftFin = Path().apply {
    moveTo(r(48f), r(96f))
    lineTo(r(18f), r(62f))
    lineTo(r(44f), r(126f))
    close()
  }
  val rightFin = Path().apply {
    moveTo(r(192f), r(96f))
    lineTo(r(222f), r(62f))
    lineTo(r(196f), r(126f))
    close()
  }
  drawPath(leftFin, HeroGold)
  drawPath(leftFin, HeroInk, style = stroke)
  drawPath(rightFin, HeroGold)
  drawPath(rightFin, HeroInk, style = stroke)

  // 5. Rounded Comic Cowl Head
  val headRect = Rect(p(42f, 44f), Size(r(156f), r(132f)))
  drawOval(HeroBlue, topLeft = headRect.topLeft, size = headRect.size)
  // Top hero crest fin
  val crownFin = Path().apply {
    moveTo(r(110f), r(48f))
    lineTo(r(120f), r(20f))
    lineTo(r(132f), r(48f))
    close()
  }
  drawPath(crownFin, HeroGold)
  drawPath(crownFin, HeroInk, style = stroke)
  drawOval(HeroInk, topLeft = headRect.topLeft, size = headRect.size, style = stroke)

  // 6. Chubby Warm Face Cutout inside Cowl
  val faceCutout = Path().apply {
    moveTo(r(56f), r(92f))
    quadraticTo(r(120f), r(82f), r(184f), r(92f))
    cubicTo(r(196f), r(126f), r(176f), r(170f), r(120f), r(172f))
    cubicTo(r(64f), r(170f), r(44f), r(126f), r(56f), r(92f))
    close()
  }
  drawPath(faceCutout, HeroSkin)
  drawPath(faceCutout, HeroInk, style = thinStroke)

  // Rosy Superhero Cheeks
  val cheekAlpha = if (expression == BotExpression.UH_HUH) 0.56f else 0.42f
  drawOval(
    color = HeroCheek.copy(alpha = cheekAlpha),
    topLeft = p(62f, 128f),
    size = Size(r(26f), r(16f)),
  )
  drawOval(
    color = HeroCheek.copy(alpha = cheekAlpha),
    topLeft = p(152f, 128f),
    size = Size(r(26f), r(16f)),
  )

  // 7. Expressive Hero Eyes + Translucent Gold/Cyan Visor Goggles
  val eyeOffsetX = when (expression) {
    BotExpression.THINKING -> 4f
    BotExpression.ALERT -> 2f
    else -> 0f
  }
  val eyeOffsetY = when (expression) {
    BotExpression.THINKING -> -3f
    else -> 0f
  }
  val lx = 94f + eyeOffsetX
  val rx = 146f + eyeOffsetX
  val ey = 114f + eyeOffsetY
  val eyeStroke = Stroke(width = inkWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Visor Frame (bold gold hero mask goggles across eyes)
  val visorRectLeft = Rect(p(64f, 94f), Size(r(48f), r(34f)))
  val visorRectRight = Rect(p(128f, 94f), Size(r(48f), r(34f)))
  drawRoundRect(
    color = HeroGold,
    topLeft = p(56f, 88f),
    size = Size(r(128f), r(44f)),
    cornerRadius = CornerRadius(r(20f), r(20f)),
  )
  drawRoundRect(
    color = HeroInk,
    topLeft = p(56f, 88f),
    size = Size(r(128f), r(44f)),
    cornerRadius = CornerRadius(r(20f), r(20f)),
    style = stroke,
  )
  // Cyan glowing visor lenses
  drawRoundRect(
    color = HeroVisorCyan.copy(alpha = 0.86f),
    topLeft = visorRectLeft.topLeft,
    size = visorRectLeft.size,
    cornerRadius = CornerRadius(r(14f), r(14f)),
  )
  drawRoundRect(
    color = HeroVisorCyan.copy(alpha = 0.86f),
    topLeft = visorRectRight.topLeft,
    size = visorRectRight.size,
    cornerRadius = CornerRadius(r(14f), r(14f)),
  )
  drawRoundRect(
    color = HeroInk,
    topLeft = visorRectLeft.topLeft,
    size = visorRectLeft.size,
    cornerRadius = CornerRadius(r(14f), r(14f)),
    style = thinStroke,
  )
  drawRoundRect(
    color = HeroInk,
    topLeft = visorRectRight.topLeft,
    size = visorRectRight.size,
    cornerRadius = CornerRadius(r(14f), r(14f)),
    style = thinStroke,
  )

  // Eyes inside lenses
  when {
    isBlinking -> {
      drawLine(HeroInk, p(lx - 8f, ey), p(lx + 8f, ey), strokeWidth = eyeStroke.width, cap = StrokeCap.Round)
      drawLine(HeroInk, p(rx - 8f, ey), p(rx + 8f, ey), strokeWidth = eyeStroke.width, cap = StrokeCap.Round)
    }
    expression == BotExpression.UH_HUH -> {
      drawArc(
        color = HeroInk,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(lx - 10f, ey - 6f),
        size = Size(r(20f), r(14f)),
        style = eyeStroke,
      )
      drawArc(
        color = HeroInk,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(rx - 10f, ey - 6f),
        size = Size(r(20f), r(14f)),
        style = eyeStroke,
      )
    }
    expression == BotExpression.SORRY -> {
      val lPath = Path().apply {
        moveTo(r(lx - 7f), r(ey - 5f))
        lineTo(r(lx + 5f), r(ey))
        lineTo(r(lx - 7f), r(ey + 5f))
      }
      val rPath = Path().apply {
        moveTo(r(rx + 7f), r(ey - 5f))
        lineTo(r(rx - 5f), r(ey))
        lineTo(r(rx + 7f), r(ey + 5f))
      }
      drawPath(lPath, HeroInk, style = eyeStroke)
      drawPath(rPath, HeroInk, style = eyeStroke)
    }
    else -> {
      drawOval(HeroInk, topLeft = p(lx - 7f, ey - 9f), size = Size(r(14f), r(18f)))
      drawOval(HeroInk, topLeft = p(rx - 7f, ey - 9f), size = Size(r(14f), r(18f)))
      drawCircle(Color.White, radius = r(3f), center = p(lx - 2f, ey - 3f))
      drawCircle(Color.White, radius = r(3f), center = p(rx - 2f, ey - 3f))
    }
  }

  // Animated visor gleam streak
  val gleamShift = if (animate) sin(phase * 1.4f) * 6f else 0f
  drawLine(
    color = Color.White.copy(alpha = 0.68f),
    start = p(72f + gleamShift, 100f),
    end = p(84f + gleamShift, 120f),
    strokeWidth = r(4f),
    cap = StrokeCap.Round,
  )
  drawLine(
    color = Color.White.copy(alpha = 0.68f),
    start = p(136f + gleamShift, 100f),
    end = p(148f + gleamShift, 120f),
    strokeWidth = r(4f),
    cap = StrokeCap.Round,
  )

  // 8. Chubby Superhero Mouth ("3" smirk by default matching Claw Dudebro crew)
  val mouthStroke = Stroke(width = inkWidth * 0.88f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  when (expression) {
    BotExpression.UH_HUH -> {
      val path = Path().apply {
        moveTo(r(104f), r(142f))
        lineTo(r(136f), r(142f))
        cubicTo(r(136f), r(162f), r(104f), r(162f), r(104f), r(142f))
        close()
      }
      drawPath(path, Color(0xFF9E2B25))
      drawPath(path, HeroInk, style = mouthStroke)
    }
    BotExpression.ALERT -> {
      drawOval(Color(0xFF9E2B25), topLeft = p(111f, 141f), size = Size(r(18f), r(19f)))
      drawOval(HeroInk, topLeft = p(111f, 141f), size = Size(r(18f), r(19f)), style = mouthStroke)
    }
    BotExpression.WORRIED, BotExpression.SORRY -> {
      val wavy = Path().apply {
        moveTo(r(104f), r(150f))
        cubicTo(r(110f), r(143f), r(115f), r(156f), r(120f), r(149f))
        cubicTo(r(125f), r(142f), r(130f), r(156f), r(136f), r(149f))
      }
      drawPath(wavy, HeroInk, style = mouthStroke)
    }
    else -> {
      val threePath = Path().apply {
        moveTo(r(113f), r(140f))
        cubicTo(r(127f), r(136f), r(128f), r(149f), r(116f), r(150f))
        cubicTo(r(129f), r(151f), r(129f), r(165f), r(112f), r(161f))
      }
      drawPath(threePath, HeroInk, style = mouthStroke)
    }
  }

  // 9. Orbiting Glowing Context Scroll (mini parchment folio)
  if (showCapeAndScroll) {
    val orbitX = 24f + if (animate) cos(phase * 1.5f) * 5f else 0f
    val orbitY = 148f + if (animate) sin(phase * 2f) * 6f else 0f
    rotate(degrees = if (animate) sin(phase) * 9f else -8f, pivot = p(orbitX + 12f, orbitY + 14f)) {
      drawRoundRect(
        color = HeroParchment,
        topLeft = p(orbitX, orbitY),
        size = Size(r(26f), r(32f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
      )
      drawRoundRect(
        color = HeroInk,
        topLeft = p(orbitX, orbitY),
        size = Size(r(26f), r(32f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
        style = thinStroke,
      )
      drawLine(HeroBlue, p(orbitX + 6f, orbitY + 10f), p(orbitX + 20f, orbitY + 10f), strokeWidth = r(2.6f), cap = StrokeCap.Round)
      drawLine(HeroBlue, p(orbitX + 6f, orbitY + 16f), p(orbitX + 18f, orbitY + 16f), strokeWidth = r(2.6f), cap = StrokeCap.Round)
      drawLine(HeroCapeRed, p(orbitX + 6f, orbitY + 22f), p(orbitX + 15f, orbitY + 22f), strokeWidth = r(2.6f), cap = StrokeCap.Round)
    }
  }

  // 10. Expression comic overlays
  when (expression) {
    BotExpression.THINKING -> {
      val drift = if (animate) sin(phase * 2f) * 3.5f else 0f
      drawCircle(Color.White, radius = r(5.5f), center = p(184f, 64f + drift))
      drawCircle(HeroInk, radius = r(5.5f), center = p(184f, 64f + drift), style = thinStroke)
      drawCircle(Color.White, radius = r(8.5f), center = p(197f, 48f - drift))
      drawCircle(HeroInk, radius = r(8.5f), center = p(197f, 48f - drift), style = thinStroke)
      val cloudCenter = p(211f, 26f + drift * 0.6f)
      drawOval(Color.White, topLeft = p(189f, 12f + drift * 0.6f), size = Size(r(44f), r(28f)))
      drawOval(HeroInk, topLeft = p(189f, 12f + drift * 0.6f), size = Size(r(44f), r(28f)), style = thinStroke)
      for (i in 0..2) {
        val pulse = if (animate) (0.7f + 0.55f * max(0f, sin(phase * 3f - i * 1.1f))) else 1f
        drawCircle(
          color = HeroBlue,
          radius = r(2.8f * pulse),
          center = Offset(cloudCenter.x + r((i - 1) * 9.5f), cloudCenter.y - r(1f)),
        )
      }
    }
    BotExpression.UH_HUH -> {
      val rad = 11f * (if (animate) sparkPulse else 1f)
      drawCircle(HeroGoldBright, radius = r(rad), center = p(200f, 38f))
      drawCircle(HeroInk, radius = r(rad), center = p(200f, 38f), style = thinStroke)
      for (i in 0 until 6) {
        val a = (i * 60.0 * PI / 180.0).toFloat()
        drawLine(
          color = HeroGoldBright,
          start = p(200f + cos(a) * 16f, 38f + sin(a) * 16f),
          end = p(200f + cos(a) * 23f, 38f + sin(a) * 23f),
          strokeWidth = r(3f),
          cap = StrokeCap.Round,
        )
      }
    }
    BotExpression.WORRIED -> {
      val dropShift = if (animate) ((phase / (2f * PI.toFloat())) * 10f) else 0f
      val dropPath = Path().apply {
        moveTo(r(186f), r(84f + dropShift))
        cubicTo(r(194f), r(96f + dropShift), r(194f), r(107f + dropShift), r(186f), r(107f + dropShift))
        cubicTo(r(178f), r(107f + dropShift), r(178f), r(96f + dropShift), r(186f), r(84f + dropShift))
        close()
      }
      drawPath(dropPath, HeroSweatBlue)
      drawPath(dropPath, HeroInk, style = thinStroke)
    }
    BotExpression.SORRY -> {
      rotate(degrees = -22f, pivot = p(76f, 146f)) {
        drawRoundRect(
          color = Color(0xFFFFD4C2),
          topLeft = p(64f, 140f),
          size = Size(r(24f), r(11f)),
          cornerRadius = CornerRadius(r(4f), r(4f)),
        )
        drawRoundRect(
          color = HeroInk,
          topLeft = p(64f, 140f),
          size = Size(r(24f), r(11f)),
          cornerRadius = CornerRadius(r(4f), r(4f)),
          style = thinStroke,
        )
      }
    }
    BotExpression.ALERT -> {
      val alertPulse = if (animate) sparkPulse else 1f
      rotate(degrees = if (animate) sin(phase * 3f) * 8f else 0f, pivot = p(201f, 38f)) {
        drawRoundRect(
          color = HeroCapeRed,
          topLeft = p(196f, 18f),
          size = Size(r(10f * alertPulse), r(24f * alertPulse)),
          cornerRadius = CornerRadius(r(5f), r(5f)),
        )
        drawCircle(HeroCapeRed, radius = r(5.5f * alertPulse), center = p(201f, 53f))
      }
    }
    BotExpression.WORKING -> {
      val spinDeg = if (animate) (phase * 180f / PI.toFloat()) * 2f else 0f
      val gx = 202f
      val gy = 38f
      rotate(degrees = spinDeg, pivot = p(gx, gy)) {
        for (i in 0 until 6) {
          val a = (i * 60.0 * PI / 180.0).toFloat()
          drawLine(
            color = HeroGoldBright,
            start = p(gx + cos(a) * 9f, gy + sin(a) * 9f),
            end = p(gx + cos(a) * 16f, gy + sin(a) * 16f),
            strokeWidth = r(5f),
            cap = StrokeCap.Round,
          )
        }
        drawCircle(HeroGoldBright, radius = r(10f), center = p(gx, gy))
        drawCircle(HeroInk, radius = r(10f), center = p(gx, gy), style = thinStroke)
        drawCircle(HeroBlueDark, radius = r(4f), center = p(gx, gy))
      }
    }
    BotExpression.HAPPY -> Unit
  }
}
