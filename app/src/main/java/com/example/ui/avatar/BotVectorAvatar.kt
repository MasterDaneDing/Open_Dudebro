package com.example.ui.avatar

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
import androidx.compose.runtime.getValue
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
import com.example.model.AccessoryStyle
import com.example.model.BotArchetype
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.HatStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val InkColor = Color(0xFF20150F)
private val ShineCream = Color(0xFFFDE6B3)
private val LensTint = Color(0x45FFE8C4)
private val SweatBlue = Color(0xFF56B4F7)
private val SparkGold = Color(0xFFFFC93C)

@Composable
fun BotVectorAvatar(
  bot: CompanionBot,
  expression: BotExpression,
  modifier: Modifier = Modifier,
  size: Dp = 120.dp,
  animate: Boolean = true,
) {
  BotVectorAvatar(
    archetype = bot.archetypeEnum,
    hatStyle = bot.hatEnum,
    accessoryStyle = bot.accessoryEnum,
    primaryColor = parseHexColor(bot.primaryColorHex, Color(0xFFEEA05E)),
    accentColor = parseHexColor(bot.accentColorHex, Color(0xFFDC4D21)),
    expression = expression,
    modifier = modifier,
    size = size,
    animate = animate,
  )
}

@Composable
fun BotVectorAvatar(
  archetype: BotArchetype,
  hatStyle: HatStyle,
  accessoryStyle: AccessoryStyle,
  primaryColor: Color,
  accentColor: Color,
  expression: BotExpression,
  modifier: Modifier = Modifier,
  size: Dp = 120.dp,
  animate: Boolean = true,
) {
  val infinite = rememberInfiniteTransition(label = "bot_idle")
  val bobPhase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = (2f * PI).toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 2200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "bob",
  )
  val blinkCycle by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "blink",
  )
  val sparkPulse by infinite.animateFloat(
    initialValue = 0.75f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "spark",
  )

  val bounceTarget = when (expression) {
    BotExpression.UH_HUH -> 1.07f
    BotExpression.SORRY -> 0.96f
    else -> 1f
  }
  val exprScale by animateFloatAsState(
    targetValue = bounceTarget,
    animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
    label = "expr_scale",
  )

  val isBlinking = animate && blinkCycle > 0.94f &&
    expression != BotExpression.UH_HUH && expression != BotExpression.SORRY

  Box(modifier = modifier.size(size)) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val s = minOf(this.size.width, this.size.height) / 240f
      val bobY = if (animate) sin(bobPhase) * 3.5f else 0f
      val sorryTilt = if (expression == BotExpression.SORRY) -4.5f else 0f
      val inkW = 4.6f * s

      translate(
        left = (this.size.width - 240f * s) / 2f,
        top = (this.size.height - 240f * s) / 2f + bobY * s,
      ) {
        rotate(
          degrees = sorryTilt,
          pivot = Offset(120f * s, 140f * s),
        ) {
          // Subtle scale around face center for expression pop
          val cx = 120f * s
          val cy = 132f * s
          translate(left = cx * (1f - exprScale), top = cy * (1f - exprScale)) {
            drawBotCharacter(
              scale = s * exprScale,
              inkWidth = inkW * exprScale,
              archetype = archetype,
              hatStyle = hatStyle,
              accessoryStyle = accessoryStyle,
              primaryColor = primaryColor,
              accentColor = accentColor,
              expression = expression,
              isBlinking = isBlinking,
              phase = bobPhase,
              sparkPulse = sparkPulse,
              animate = animate,
            )
          }
        }
      }
    }
  }
}

private fun DrawScope.drawBotCharacter(
  scale: Float,
  inkWidth: Float,
  archetype: BotArchetype,
  hatStyle: HatStyle,
  accessoryStyle: AccessoryStyle,
  primaryColor: Color,
  accentColor: Color,
  expression: BotExpression,
  isBlinking: Boolean,
  phase: Float,
  sparkPulse: Float,
  animate: Boolean,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val stroke = Stroke(width = inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val thinStroke = Stroke(width = inkWidth * 0.72f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // 1. Background soft aura circle
  drawCircle(
    color = accentColor.copy(alpha = 0.12f),
    radius = r(98f),
    center = p(120f, 134f),
  )

  // 2. Retro robot top coil antenna if robot archetype and no dish hat
  if (archetype == BotArchetype.RETRO_ROBOT && hatStyle == HatStyle.NONE) {
    val wobble = if (animate) sin(phase * 2f) * 3f else 0f
    val antPath = Path().apply {
      moveTo(r(120f), r(52f))
      cubicTo(r(112f), r(42f), r(128f), r(34f), r(120f + wobble), r(24f))
    }
    drawPath(antPath, InkColor, style = stroke)
    drawCircle(color = accentColor, radius = r(9f), center = p(120f + wobble, 20f))
    drawCircle(color = InkColor, radius = r(9f), center = p(120f + wobble, 20f), style = thinStroke)
    drawCircle(color = ShineCream, radius = r(3f), center = p(117f + wobble, 17f))
  }

  // 2b. Back-head traits (Dino dorsal plates, Pigtail Girl twin puffs, Chinese Master topknot)
  when (archetype) {
    BotArchetype.DINOSAUR -> {
      val spikeColor = accentColor
      listOf(
        Triple(Offset(68f, 64f), Offset(50f, 38f), Offset(86f, 52f)),
        Triple(Offset(44f, 92f), Offset(22f, 76f), Offset(52f, 70f)),
        Triple(Offset(38f, 124f), Offset(18f, 118f), Offset(42f, 104f)),
      ).forEach { (a, tip, b) ->
        val spike = Path().apply {
          moveTo(r(a.x), r(a.y))
          lineTo(r(tip.x), r(tip.y))
          lineTo(r(b.x), r(b.y))
          close()
        }
        drawPath(spike, spikeColor)
        drawPath(spike, InkColor, style = stroke)
      }
    }
    BotArchetype.PIGTAIL_GIRL -> {
      val hairColor = Color(0xFF4A2E20)
      // Left & right bouncy pigtails
      val leftTail = Path().apply {
        moveTo(r(44f), r(98f))
        cubicTo(r(12f), r(80f), r(8f), r(132f), r(28f), r(148f))
        cubicTo(r(40f), r(142f), r(46f), r(118f), r(44f), r(98f))
        close()
      }
      val rightTail = Path().apply {
        moveTo(r(196f), r(98f))
        cubicTo(r(228f), r(80f), r(232f), r(132f), r(212f), r(148f))
        cubicTo(r(200f), r(142f), r(194f), r(118f), r(196f), r(98f))
        close()
      }
      drawPath(leftTail, hairColor)
      drawPath(leftTail, InkColor, style = stroke)
      drawPath(rightTail, hairColor)
      drawPath(rightTail, InkColor, style = stroke)
      // Cute hair bobbles
      drawCircle(color = accentColor, radius = r(7.5f), center = p(43f, 97f))
      drawCircle(color = InkColor, radius = r(7.5f), center = p(43f, 97f), style = thinStroke)
      drawCircle(color = accentColor, radius = r(7.5f), center = p(197f, 97f))
      drawCircle(color = InkColor, radius = r(7.5f), center = p(197f, 97f), style = thinStroke)
    }
    BotArchetype.CHINESE_MASTER -> {
      if (hatStyle == HatStyle.NONE) {
        drawCircle(color = Color(0xFF231F20), radius = r(16f), center = p(120f, 43f))
        drawCircle(color = InkColor, radius = r(16f), center = p(120f, 43f), style = stroke)
        // Hairpin rod
        drawLine(SparkGold, p(96f, 42f), p(144f, 42f), strokeWidth = r(4f), cap = StrokeCap.Round)
      }
    }
    else -> Unit
  }

  // 3. Ears / Side Dials
  when (archetype) {
    BotArchetype.RETRO_ROBOT -> {
      // Chrome retro side ear-knobs
      drawRoundRect(
        color = Color(0xFFD6E2E8),
        topLeft = p(26f, 114f),
        size = Size(r(18f), r(42f)),
        cornerRadius = CornerRadius(r(8f), r(8f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(26f, 114f),
        size = Size(r(18f), r(42f)),
        cornerRadius = CornerRadius(r(8f), r(8f)),
        style = stroke,
      )
      drawRoundRect(
        color = Color(0xFFD6E2E8),
        topLeft = p(196f, 114f),
        size = Size(r(18f), r(42f)),
        cornerRadius = CornerRadius(r(8f), r(8f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(196f, 114f),
        size = Size(r(18f), r(42f)),
        cornerRadius = CornerRadius(r(8f), r(8f)),
        style = stroke,
      )
    }
    BotArchetype.GOBLIN -> {
      // Long pointed goblin ears
      val leftEar = Path().apply {
        moveTo(r(42f), r(112f))
        lineTo(r(10f), r(96f))
        cubicTo(r(18f), r(120f), r(28f), r(136f), r(42f), r(140f))
        close()
      }
      val rightEar = Path().apply {
        moveTo(r(198f), r(112f))
        lineTo(r(230f), r(96f))
        cubicTo(r(222f), r(120f), r(212f), r(136f), r(198f), r(140f))
        close()
      }
      drawPath(leftEar, primaryColor)
      drawPath(leftEar, InkColor, style = stroke)
      drawPath(rightEar, primaryColor)
      drawPath(rightEar, InkColor, style = stroke)
      drawLine(InkColor, p(22f, 108f), p(36f, 122f), strokeWidth = r(2.8f), cap = StrokeCap.Round)
      drawLine(InkColor, p(218f, 108f), p(204f, 122f), strokeWidth = r(2.8f), cap = StrokeCap.Round)
    }
    BotArchetype.DINOSAUR -> Unit
    else -> {
      // Chubby left & right ears (left slightly larger like the original fellow)
      drawOval(color = primaryColor, topLeft = p(25f, 114f), size = Size(r(28f), r(36f)))
      drawOval(color = InkColor, topLeft = p(25f, 114f), size = Size(r(28f), r(36f)), style = stroke)
      drawArc(
        color = InkColor,
        startAngle = 150f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(33f, 124f),
        size = Size(r(12f), r(16f)),
        style = thinStroke,
      )

      drawOval(color = primaryColor, topLeft = p(188f, 118f), size = Size(r(24f), r(32f)))
      drawOval(color = InkColor, topLeft = p(188f, 118f), size = Size(r(24f), r(32f)), style = stroke)
    }
  }

  // 4. Main Chubby Head Shape
  val headRect = Rect(p(38f, 52f), Size(r(164f), r(154f)))
  if (archetype == BotArchetype.RETRO_ROBOT) {
    drawRoundRect(
      color = primaryColor,
      topLeft = headRect.topLeft,
      size = headRect.size,
      cornerRadius = CornerRadius(r(68f), r(68f)),
    )
    // Retro metallic forehead sheen
    drawArc(
      color = Color.White.copy(alpha = 0.35f),
      startAngle = 205f,
      sweepAngle = 70f,
      useCenter = false,
      topLeft = p(54f, 64f),
      size = Size(r(132f), r(70f)),
      style = Stroke(width = r(7f), cap = StrokeCap.Round),
    )
    drawRoundRect(
      color = InkColor,
      topLeft = headRect.topLeft,
      size = headRect.size,
      cornerRadius = CornerRadius(r(68f), r(68f)),
      style = stroke,
    )
    // Cute corner rivets
    listOf(p(58f, 82f), p(182f, 82f), p(56f, 182f), p(184f, 182f)).forEach { pt ->
      drawCircle(color = Color(0xFF475B63), radius = r(3.2f), center = pt)
    }
  } else {
    drawOval(color = primaryColor, topLeft = headRect.topLeft, size = headRect.size)
    // Soft upper highlight
    drawOval(
      color = Color.White.copy(alpha = 0.16f),
      topLeft = p(70f, 62f),
      size = Size(r(98f), r(32f)),
    )
    drawOval(color = InkColor, topLeft = headRect.topLeft, size = headRect.size, style = stroke)
  }

  // 5. Archetype Forehead / Hair / Horns Details
  when (archetype) {
    BotArchetype.PROFESSOR -> {
      listOf(p(42f, 96f), p(37f, 108f), p(198f, 96f), p(203f, 108f)).forEach { tuft ->
        drawCircle(color = Color(0xFFFDF8F0), radius = r(12f), center = tuft)
        drawCircle(color = InkColor, radius = r(12f), center = tuft, style = thinStroke)
      }
    }
    BotArchetype.GOBLIN -> {
      // Cute tiny curved ivory horns
      val leftHorn = Path().apply {
        moveTo(r(80f), r(64f))
        cubicTo(r(74f), r(48f), r(82f), r(38f), r(90f), r(44f))
        cubicTo(r(92f), r(54f), r(90f), r(62f), r(80f), r(64f))
        close()
      }
      val rightHorn = Path().apply {
        moveTo(r(160f), r(64f))
        cubicTo(r(166f), r(48f), r(158f), r(38f), r(150f), r(44f))
        cubicTo(r(148f), r(54f), r(150f), r(62f), r(160f), r(64f))
        close()
      }
      drawPath(leftHorn, Color(0xFFFFF3D6))
      drawPath(leftHorn, InkColor, style = thinStroke)
      drawPath(rightHorn, Color(0xFFFFF3D6))
      drawPath(rightHorn, InkColor, style = thinStroke)
    }
    BotArchetype.DINOSAUR -> {
      // Cute dino cheek spots & snout dimples
      drawCircle(color = accentColor.copy(alpha = 0.25f), radius = r(7f), center = p(64f, 88f))
      drawCircle(color = accentColor.copy(alpha = 0.25f), radius = r(4.5f), center = p(76f, 76f))
      drawCircle(color = InkColor, radius = r(2.2f), center = p(112f, 114f))
      drawCircle(color = InkColor, radius = r(2.2f), center = p(128f, 114f))
    }
    BotArchetype.YANKI_BIKER -> {
      // Iconic 80s Japanese "Regent" (リーゼント) sweeping pompadour!
      val pompadour = Path().apply {
        moveTo(r(42f), r(106f))
        cubicTo(r(36f), r(62f), r(64f), r(24f), r(118f), r(18f))
        cubicTo(r(164f), r(14f), r(198f), r(34f), r(190f), r(62f))
        cubicTo(r(180f), r(82f), r(146f), r(78f), r(118f), r(74f))
        cubicTo(r(88f), r(70f), r(58f), r(84f), r(42f), r(106f))
        close()
      }
      drawPath(pompadour, Color(0xFF1F2433))
      drawPath(pompadour, InkColor, style = stroke)
      // Pompadour glossy sheen curve
      drawArc(
        color = Color(0xFF6B7BA4),
        startAngle = 210f,
        sweepAngle = 95f,
        useCenter = false,
        topLeft = p(64f, 28f),
        size = Size(r(98f), r(34f)),
        style = Stroke(width = r(4.5f), cap = StrokeCap.Round),
      )
      // Sideburns
      drawLine(Color(0xFF1F2433), p(45f, 100f), p(45f, 126f), strokeWidth = r(6f), cap = StrokeCap.Round)
    }
    BotArchetype.PIGTAIL_GIRL -> {
      // Sweet curved bangs across forehead
      val bangs = Path().apply {
        moveTo(r(42f), r(98f))
        cubicTo(r(46f), r(52f), r(92f), r(48f), r(120f), r(48f))
        cubicTo(r(148f), r(48f), r(194f), r(52f), r(198f), r(98f))
        cubicTo(r(180f), r(76f), r(154f), r(74f), r(136f), r(84f))
        cubicTo(r(126f), r(74f), r(114f), r(74f), r(104f), r(84f))
        cubicTo(r(86f), r(74f), r(60f), r(76f), r(42f), r(98f))
        close()
      }
      drawPath(bangs, Color(0xFF4A2E20))
      drawPath(bangs, InkColor, style = stroke)
    }
    else -> Unit
  }

  // 6. Chin stubble (Signature detail from uploaded fellow!)
  if (archetype == BotArchetype.GLASSES_FELLOW || accessoryStyle == AccessoryStyle.STUBBLE) {
    for (angleDeg in 32..148 step 6) {
      val rad = (angleDeg * PI / 180.0).toFloat()
      val rx = 79f
      val ry = 74f
      val cx = 120f + cos(rad) * rx
      val cy = 129f + sin(rad) * ry
      drawLine(
        color = InkColor.copy(alpha = 0.65f),
        start = p(cx, cy),
        end = p(cx + cos(rad) * 4.5f, cy + sin(rad) * 5.2f),
        strokeWidth = r(1.3f),
        cap = StrokeCap.Round,
      )
    }
  }

  // 7. Big Rosy Cheeks (Classic Anpan-style overlapping round cheeks)
  val cheekLeftCenter = p(76f, 147f)
  val cheekRightCenter = p(164f, 147f)
  val cheekColor = accentColor.copy(alpha = 0.92f)
  drawCircle(color = cheekColor, radius = r(23f), center = cheekLeftCenter)
  drawCircle(color = cheekColor, radius = r(23f), center = cheekRightCenter)
  // Upper outer cheek crease arcs just like the reference image
  drawArc(
    color = InkColor,
    startAngle = 125f,
    sweepAngle = 190f,
    useCenter = false,
    topLeft = p(53f, 124f),
    size = Size(r(46f), r(46f)),
    style = thinStroke,
  )
  drawArc(
    color = InkColor,
    startAngle = 225f,
    sweepAngle = 190f,
    useCenter = false,
    topLeft = p(141f, 124f),
    size = Size(r(46f), r(46f)),
    style = thinStroke,
  )

  // 8. Eyes & Eyebrows per expression
  val eyeOffsetX = when (expression) {
    BotExpression.THINKING -> 2.5f
    else -> 0f
  }
  val eyeOffsetY = when (expression) {
    BotExpression.THINKING -> -3f
    else -> 0f
  }
  drawEyesAndBrows(
    scale = scale,
    inkWidth = inkWidth,
    archetype = archetype,
    expression = expression,
    isBlinking = isBlinking,
    eyeOffsetX = eyeOffsetX,
    eyeOffsetY = eyeOffsetY,
  )

  // 9. Facial hair / whiskers (Professor cloud mustache & Chinese Master flowing whiskers)
  if (archetype == BotArchetype.PROFESSOR) {
    val mustachePath = Path().apply {
      moveTo(r(120f), r(148f))
      cubicTo(r(104f), r(146f), r(88f), r(158f), r(92f), r(166f))
      cubicTo(r(98f), r(172f), r(112f), r(166f), r(120f), r(159f))
      cubicTo(r(128f), r(166f), r(142f), r(172f), r(148f), r(166f))
      cubicTo(r(152f), r(158f), r(136f), r(146f), r(120f), r(148f))
      close()
    }
    drawPath(mustachePath, Color(0xFFFDF8F0))
    drawPath(mustachePath, InkColor, style = thinStroke)
  } else if (archetype == BotArchetype.CHINESE_MASTER) {
    val whiteHair = Color(0xFFF8F4EC)
    // Flowing Fu-Manchu whiskers on both sides + pointed chin beard
    val leftWhisker = Path().apply {
      moveTo(r(110f), r(148f))
      cubicTo(r(92f), r(148f), r(78f), r(166f), r(76f), r(192f))
      cubicTo(r(86f), r(184f), r(94f), r(166f), r(112f), r(156f))
      close()
    }
    val rightWhisker = Path().apply {
      moveTo(r(130f), r(148f))
      cubicTo(r(148f), r(148f), r(162f), r(166f), r(164f), r(192f))
      cubicTo(r(154f), r(184f), r(146f), r(166f), r(128f), r(156f))
      close()
    }
    val chinBeard = Path().apply {
      moveTo(r(110f), r(190f))
      cubicTo(r(112f), r(208f), r(116f), r(220f), r(120f), r(224f))
      cubicTo(r(124f), r(220f), r(128f), r(208f), r(130f), r(190f))
      close()
    }
    drawPath(chinBeard, whiteHair)
    drawPath(chinBeard, InkColor, style = thinStroke)
    drawPath(leftWhisker, whiteHair)
    drawPath(leftWhisker, InkColor, style = thinStroke)
    drawPath(rightWhisker, whiteHair)
    drawPath(rightWhisker, InkColor, style = thinStroke)
  }

  // 10. Signature Big Shiny Round Nose
  val noseCenter = p(120f, 138f)
  val noseColor = if (archetype == BotArchetype.RETRO_ROBOT) Color(0xFFFFA826) else accentColor
  drawCircle(color = noseColor, radius = r(21f), center = noseCenter)
  drawCircle(color = InkColor, radius = r(21f), center = noseCenter, style = stroke)
  // Creamy nose shine dot
  drawCircle(color = ShineCream, radius = r(5.8f), center = p(114f, 130f))

  // 11. Expressive Mouth (signature "3" shape when happy/thinking, morphs per emotion)
  drawMouth(scale = scale, inkWidth = inkWidth, archetype = archetype, expression = expression)
  if (archetype == BotArchetype.GOBLIN && expression != BotExpression.WORRIED) {
    // Cheeky little goblin fang peeking up
    val fang = Path().apply {
      moveTo(r(125f), r(176f))
      lineTo(r(129f), r(168f))
      lineTo(r(133f), r(176f))
      close()
    }
    drawPath(fang, Color(0xFFFFF9EB))
    drawPath(fang, InkColor, style = Stroke(width = r(2.2f), join = StrokeJoin.Round))
  }

  // 12. Accessories (Glasses, Monocle, Bowtie, Scarf, Badge)
  drawAccessories(
    scale = scale,
    inkWidth = inkWidth,
    accessoryStyle = accessoryStyle,
    accentColor = accentColor,
  )

  // 13. Hats (on top of forehead/crown)
  drawHat(scale = scale, inkWidth = inkWidth, hatStyle = hatStyle, accentColor = accentColor, phase = phase, animate = animate)

  // 14. Dynamic Expression Overlays (Thinking bubbles, Uh-huh lightbulb, Worried sweat drop, Sorry bandage/tears)
  drawExpressionOverlays(
    scale = scale,
    inkWidth = inkWidth,
    expression = expression,
    phase = phase,
    sparkPulse = sparkPulse,
    animate = animate,
  )
}

private fun DrawScope.drawEyesAndBrows(
  scale: Float,
  inkWidth: Float,
  archetype: BotArchetype,
  expression: BotExpression,
  isBlinking: Boolean,
  eyeOffsetX: Float,
  eyeOffsetY: Float,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val browStroke = Stroke(width = inkWidth * 1.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
  val eyeStroke = Stroke(width = inkWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  // Eyebrows
  when (expression) {
    BotExpression.UH_HUH -> {
      drawLine(InkColor, p(78f, 89f), p(104f, 83f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
      drawLine(InkColor, p(136f, 83f), p(162f, 89f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
    }
    BotExpression.WORRIED, BotExpression.SORRY -> {
      drawLine(InkColor, p(78f, 97f), p(105f, 89f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
      drawLine(InkColor, p(135f, 89f), p(162f, 97f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
    }
    BotExpression.THINKING -> {
      drawLine(InkColor, p(79f, 94f), p(105f, 98f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
      drawLine(InkColor, p(136f, 88f), p(162f, 86f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
    }
    else -> {
      // Classic bold angled brows from the uploaded character
      drawLine(InkColor, p(79f, 92f), p(106f, 99f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
      drawLine(InkColor, p(134f, 99f), p(161f, 92f), strokeWidth = browStroke.width, cap = StrokeCap.Round)
    }
  }

  // Eyes
  val lx = 94f + eyeOffsetX
  val rx = 146f + eyeOffsetX
  val ey = 114f + eyeOffsetY

  when {
    isBlinking -> {
      drawLine(InkColor, p(lx - 7f, ey), p(lx + 7f, ey), strokeWidth = eyeStroke.width, cap = StrokeCap.Round)
      drawLine(InkColor, p(rx - 7f, ey), p(rx + 7f, ey), strokeWidth = eyeStroke.width, cap = StrokeCap.Round)
    }
    expression == BotExpression.UH_HUH -> {
      // Happy ^ ^ arcs
      drawArc(
        color = InkColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(lx - 9f, ey - 5f),
        size = Size(r(18f), r(14f)),
        style = eyeStroke,
      )
      drawArc(
        color = InkColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(rx - 9f, ey - 5f),
        size = Size(r(18f), r(14f)),
        style = eyeStroke,
      )
    }
    expression == BotExpression.SORRY -> {
      // Cute apologetic > < eyes
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
      drawPath(lPath, InkColor, style = eyeStroke)
      drawPath(rPath, InkColor, style = eyeStroke)
    }
    archetype == BotArchetype.RETRO_ROBOT -> {
      // Glowing retro CRT pill eyes
      drawRoundRect(
        color = InkColor,
        topLeft = p(lx - 9f, ey - 10f),
        size = Size(r(18f), r(20f)),
        cornerRadius = CornerRadius(r(9f), r(9f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(rx - 9f, ey - 10f),
        size = Size(r(18f), r(20f)),
        cornerRadius = CornerRadius(r(9f), r(9f)),
      )
      drawCircle(color = SparkGold, radius = r(3.5f), center = p(lx - 2f, ey - 3f))
      drawCircle(color = SparkGold, radius = r(3.5f), center = p(rx - 2f, ey - 3f))
    }
    else -> {
      // Classic dark oval eyes
      drawOval(color = InkColor, topLeft = p(lx - 7f, ey - 9f), size = Size(r(14f), r(18f)))
      drawOval(color = InkColor, topLeft = p(rx - 7f, ey - 9f), size = Size(r(14f), r(18f)))
      if (archetype == BotArchetype.PIGTAIL_GIRL) {
        drawCircle(color = Color.White, radius = r(2.6f), center = p(lx - 2f, ey - 3f))
        drawCircle(color = Color.White, radius = r(2.6f), center = p(rx - 2f, ey - 3f))
        drawLine(InkColor, p(lx - 7f, ey - 5f), p(lx - 12f, ey - 9f), strokeWidth = r(2.4f), cap = StrokeCap.Round)
        drawLine(InkColor, p(rx + 7f, ey - 5f), p(rx + 12f, ey - 9f), strokeWidth = r(2.4f), cap = StrokeCap.Round)
      } else if (expression == BotExpression.WORRIED || expression == BotExpression.ALERT) {
        drawCircle(color = Color.White, radius = r(2.6f), center = p(lx - 2f, ey - 3f))
        drawCircle(color = Color.White, radius = r(2.6f), center = p(rx - 2f, ey - 3f))
      }
    }
  }
}

private fun DrawScope.drawMouth(
  scale: Float,
  inkWidth: Float,
  archetype: BotArchetype,
  expression: BotExpression,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val stroke = Stroke(width = inkWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  if (archetype == BotArchetype.RETRO_ROBOT && expression == BotExpression.HAPPY) {
    // Cute retro speaker smile
    drawArc(
      color = InkColor,
      startAngle = 15f,
      sweepAngle = 150f,
      useCenter = false,
      topLeft = p(105f, 162f),
      size = Size(r(30f), r(20f)),
      style = stroke,
    )
    return
  }

  when (expression) {
    BotExpression.UH_HUH -> {
      // Joyful open D-smile
      val path = Path().apply {
        moveTo(r(104f), r(168f))
        lineTo(r(136f), r(168f))
        cubicTo(r(136f), r(189f), r(104f), r(189f), r(104f), r(168f))
        close()
      }
      drawPath(path, Color(0xFF9E2B25))
      drawPath(path, InkColor, style = stroke)
    }
    BotExpression.ALERT -> {
      drawOval(color = Color(0xFF9E2B25), topLeft = p(111f, 166f), size = Size(r(18f), r(20f)))
      drawOval(color = InkColor, topLeft = p(111f, 166f), size = Size(r(18f), r(20f)), style = stroke)
    }
    BotExpression.WORRIED, BotExpression.SORRY -> {
      val wavy = Path().apply {
        moveTo(r(104f), r(177f))
        cubicTo(r(110f), r(170f), r(115f), r(183f), r(120f), r(176f))
        cubicTo(r(125f), r(169f), r(130f), r(183f), r(136f), r(176f))
      }
      drawPath(wavy, InkColor, style = stroke)
    }
    else -> {
      // Iconic "3" mouth from the uploaded character!
      val threePath = Path().apply {
        moveTo(r(113f), r(165f))
        cubicTo(r(127f), r(161f), r(128f), r(175f), r(116f), r(176f))
        cubicTo(r(129f), r(177f), r(129f), r(192f), r(112f), r(188f))
      }
      drawPath(threePath, InkColor, style = stroke)
    }
  }
}

private fun DrawScope.drawAccessories(
  scale: Float,
  inkWidth: Float,
  accessoryStyle: AccessoryStyle,
  accentColor: Color,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val glassStroke = Stroke(width = inkWidth * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  when (accessoryStyle) {
    AccessoryStyle.FOREHEAD_GLASSES -> {
      // Exact recreation of the glasses perched on top of his head
      val leftLens = Rect(p(78f, 60f), Size(r(34f), r(21f)))
      val rightLens = Rect(p(126f, 60f), Size(r(34f), r(21f)))

      // Temple arm curving down left side
      val armPath = Path().apply {
        moveTo(r(78f), r(70f))
        cubicTo(r(64f), r(72f), r(56f), r(80f), r(48f), r(92f))
      }
      drawPath(armPath, InkColor, style = Stroke(width = r(4.5f), cap = StrokeCap.Round))

      // Lens fills
      drawRoundRect(LensTint, leftLens.topLeft, leftLens.size, CornerRadius(r(10f), r(10f)))
      drawRoundRect(LensTint, rightLens.topLeft, rightLens.size, CornerRadius(r(10f), r(10f)))

      // Diagonal creamy glare highlight
      drawLine(ShineCream, p(88f, 64f), p(96f, 76f), strokeWidth = r(4.2f), cap = StrokeCap.Round)
      drawLine(ShineCream, p(136f, 64f), p(144f, 76f), strokeWidth = r(4.2f), cap = StrokeCap.Round)

      // Frames + bridge
      drawRoundRect(InkColor, leftLens.topLeft, leftLens.size, CornerRadius(r(10f), r(10f)), style = glassStroke)
      drawRoundRect(InkColor, rightLens.topLeft, rightLens.size, CornerRadius(r(10f), r(10f)), style = glassStroke)
      drawArc(
        color = InkColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = p(111f, 64f),
        size = Size(r(16f), r(10f)),
        style = glassStroke,
      )
    }
    AccessoryStyle.ROUND_GLASSES -> {
      drawCircle(LensTint, radius = r(16f), center = p(94f, 114f))
      drawCircle(LensTint, radius = r(16f), center = p(146f, 114f))
      drawCircle(InkColor, radius = r(16f), center = p(94f, 114f), style = glassStroke)
      drawCircle(InkColor, radius = r(16f), center = p(146f, 114f), style = glassStroke)
      drawLine(InkColor, p(110f, 114f), p(130f, 114f), strokeWidth = glassStroke.width)
    }
    AccessoryStyle.MONOCLE -> {
      drawCircle(LensTint, radius = r(17f), center = p(146f, 114f))
      drawCircle(SparkGold, radius = r(17f), center = p(146f, 114f), style = glassStroke)
      drawLine(SparkGold, p(163f, 118f), p(170f, 152f), strokeWidth = r(2.2f), cap = StrokeCap.Round)
    }
    AccessoryStyle.BOWTIE -> {
      val leftWing = Path().apply {
        moveTo(r(120f), r(204f))
        lineTo(r(95f), r(193f))
        lineTo(r(95f), r(215f))
        close()
      }
      val rightWing = Path().apply {
        moveTo(r(120f), r(204f))
        lineTo(r(145f), r(193f))
        lineTo(r(145f), r(215f))
        close()
      }
      drawPath(leftWing, accentColor)
      drawPath(rightWing, accentColor)
      drawPath(leftWing, InkColor, style = glassStroke)
      drawPath(rightWing, InkColor, style = glassStroke)
      drawCircle(SparkGold, radius = r(6f), center = p(120f, 204f))
      drawCircle(InkColor, radius = r(6f), center = p(120f, 204f), style = glassStroke)
    }
    AccessoryStyle.SCARF -> {
      drawRoundRect(
        color = accentColor,
        topLeft = p(72f, 194f),
        size = Size(r(96f), r(18f)),
        cornerRadius = CornerRadius(r(9f), r(9f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(72f, 194f),
        size = Size(r(96f), r(18f)),
        cornerRadius = CornerRadius(r(9f), r(9f)),
        style = glassStroke,
      )
      drawRoundRect(
        color = accentColor,
        topLeft = p(134f, 202f),
        size = Size(r(20f), r(24f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(134f, 202f),
        size = Size(r(20f), r(24f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
        style = glassStroke,
      )
    }
    AccessoryStyle.SPARK_BADGE -> {
      drawCircle(SparkGold, radius = r(13f), center = p(165f, 186f))
      drawCircle(InkColor, radius = r(13f), center = p(165f, 186f), style = glassStroke)
      drawCircle(accentColor, radius = r(5f), center = p(165f, 186f))
    }
    AccessoryStyle.NONE, AccessoryStyle.STUBBLE -> Unit
  }
}

private fun DrawScope.drawHat(
  scale: Float,
  inkWidth: Float,
  hatStyle: HatStyle,
  accentColor: Color,
  phase: Float,
  animate: Boolean,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val stroke = Stroke(width = inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

  when (hatStyle) {
    HatStyle.NONE -> Unit
    HatStyle.BERET -> {
      drawOval(color = accentColor, topLeft = p(64f, 26f), size = Size(r(116f), r(38f)))
      drawOval(color = InkColor, topLeft = p(64f, 26f), size = Size(r(116f), r(38f)), style = stroke)
      drawLine(InkColor, p(122f, 26f), p(126f, 17f), strokeWidth = inkWidth, cap = StrokeCap.Round)
    }
    HatStyle.CAP -> {
      drawArc(
        color = Color(0xFF427AA1),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = p(72f, 26f),
        size = Size(r(96f), r(52f)),
      )
      drawArc(
        color = InkColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = p(72f, 26f),
        size = Size(r(96f), r(52f)),
        style = stroke,
      )
      // Brim
      drawRoundRect(
        color = Color(0xFF2D5978),
        topLeft = p(72f, 47f),
        size = Size(r(114f), r(12f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(72f, 47f),
        size = Size(r(114f), r(12f)),
        cornerRadius = CornerRadius(r(6f), r(6f)),
        style = stroke,
      )
    }
    HatStyle.PROPELLER -> {
      drawArc(
        color = SparkGold,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = p(76f, 28f),
        size = Size(r(88f), r(48f)),
      )
      drawArc(
        color = InkColor,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = p(76f, 28f),
        size = Size(r(88f), r(48f)),
        style = stroke,
      )
      val spinW = if (animate) (14f + 16f * kotlin.math.abs(cos(phase * 3f))) else 28f
      drawLine(InkColor, p(120f, 28f), p(120f, 16f), strokeWidth = inkWidth)
      drawOval(color = accentColor, topLeft = p(120f - spinW, 11f), size = Size(r(spinW * 2f), r(8f)))
      drawOval(color = InkColor, topLeft = p(120f - spinW, 11f), size = Size(r(spinW * 2f), r(8f)), style = stroke)
    }
    HatStyle.MORTARBOARD -> {
      val board = Path().apply {
        moveTo(r(120f), r(18f))
        lineTo(r(182f), r(36f))
        lineTo(r(120f), r(52f))
        lineTo(r(58f), r(36f))
        close()
      }
      drawPath(board, Color(0xFF33313B))
      drawPath(board, InkColor, style = stroke)
      drawLine(SparkGold, p(168f, 38f), p(172f, 64f), strokeWidth = r(3.5f), cap = StrokeCap.Round)
      drawCircle(SparkGold, radius = r(4f), center = p(172f, 66f))
    }
    HatStyle.CROWN -> {
      val crown = Path().apply {
        moveTo(r(86f), r(52f))
        lineTo(r(82f), r(24f))
        lineTo(r(104f), r(38f))
        lineTo(r(120f), r(18f))
        lineTo(r(136f), r(38f))
        lineTo(r(158f), r(24f))
        lineTo(r(154f), r(52f))
        close()
      }
      drawPath(crown, SparkGold)
      drawPath(crown, InkColor, style = stroke)
      drawCircle(accentColor, radius = r(4f), center = p(120f, 38f))
    }
    HatStyle.HEADBAND -> {
      drawRoundRect(
        color = accentColor,
        topLeft = p(56f, 52f),
        size = Size(r(128f), r(14f)),
        cornerRadius = CornerRadius(r(7f), r(7f)),
      )
      drawRoundRect(
        color = InkColor,
        topLeft = p(56f, 52f),
        size = Size(r(128f), r(14f)),
        cornerRadius = CornerRadius(r(7f), r(7f)),
        style = stroke,
      )
    }
    HatStyle.ANTENNA_DISH -> {
      drawLine(InkColor, p(120f, 52f), p(120f, 34f), strokeWidth = inkWidth)
      drawArc(
        color = Color(0xFFD6E2E8),
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = true,
        topLeft = p(94f, 14f),
        size = Size(r(52f), r(30f)),
      )
      drawArc(
        color = InkColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = true,
        topLeft = p(94f, 14f),
        size = Size(r(52f), r(30f)),
        style = stroke,
      )
      drawCircle(accentColor, radius = r(5f), center = p(120f, 18f))
    }
  }
}

private fun DrawScope.drawExpressionOverlays(
  scale: Float,
  inkWidth: Float,
  expression: BotExpression,
  phase: Float,
  sparkPulse: Float,
  animate: Boolean,
) {
  fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
  fun r(v: Float) = v * scale
  val thinStroke = Stroke(width = inkWidth * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

  when (expression) {
    BotExpression.THINKING -> {
      val drift = if (animate) sin(phase * 2f) * 3f else 0f
      drawCircle(Color.White, radius = r(5f), center = p(188f, 64f + drift))
      drawCircle(InkColor, radius = r(5f), center = p(188f, 64f + drift), style = thinStroke)
      drawCircle(Color.White, radius = r(8f), center = p(202f, 48f - drift))
      drawCircle(InkColor, radius = r(8f), center = p(202f, 48f - drift), style = thinStroke)
      drawCircle(Color.White, radius = r(12f), center = p(214f, 28f + drift))
      drawCircle(InkColor, radius = r(12f), center = p(214f, 28f + drift), style = thinStroke)
    }
    BotExpression.UH_HUH -> {
      // Glowing "Aha!" lightbulb/sparkle bursts on top right
      val rad = 11f * (if (animate) sparkPulse else 1f)
      drawCircle(SparkGold, radius = r(rad), center = p(200f, 44f))
      drawCircle(InkColor, radius = r(rad), center = p(200f, 44f), style = thinStroke)
      for (i in 0 until 6) {
        val a = (i * 60.0 * PI / 180.0).toFloat()
        drawLine(
          color = SparkGold,
          start = p(200f + cos(a) * 16f, 44f + sin(a) * 16f),
          end = p(200f + cos(a) * 23f, 44f + sin(a) * 23f),
          strokeWidth = r(3f),
          cap = StrokeCap.Round,
        )
      }
    }
    BotExpression.WORRIED -> {
      val dropShift = if (animate) ((phase / (2f * PI.toFloat())) * 10f) else 0f
      val dropPath = Path().apply {
        moveTo(r(186f), r(92f + dropShift))
        cubicTo(r(194f), r(104f + dropShift), r(194f), r(115f + dropShift), r(186f), r(115f + dropShift))
        cubicTo(r(178f), r(115f + dropShift), r(178f), r(104f + dropShift), r(186f), r(92f + dropShift))
        close()
      }
      drawPath(dropPath, SweatBlue)
      drawPath(dropPath, InkColor, style = thinStroke)
    }
    BotExpression.SORRY -> {
      // Cute pink adhesive bandage on cheek
      rotate(degrees = -22f, pivot = p(76f, 162f)) {
        drawRoundRect(
          color = Color(0xFFFFD4C2),
          topLeft = p(64f, 156f),
          size = Size(r(24f), r(11f)),
          cornerRadius = CornerRadius(r(4f), r(4f)),
        )
        drawRoundRect(
          color = InkColor,
          topLeft = p(64f, 156f),
          size = Size(r(24f), r(11f)),
          cornerRadius = CornerRadius(r(4f), r(4f)),
          style = thinStroke,
        )
      }
    }
    BotExpression.ALERT -> {
      drawRoundRect(
        color = Color(0xFFE53935),
        topLeft = p(196f, 30f),
        size = Size(r(10f), r(24f)),
        cornerRadius = CornerRadius(r(5f), r(5f)),
      )
      drawCircle(color = Color(0xFFE53935), radius = r(5.5f), center = p(201f, 64f))
    }
    BotExpression.WORKING -> {
      val spin = if (animate) phase * 45f else 0f
      rotate(degrees = spin, pivot = p(202f, 44f)) {
        drawCircle(SparkGold, radius = r(10f), center = p(202f, 44f), style = thinStroke)
        drawCircle(Color(0xFF475B63), radius = r(4f), center = p(202f, 44f))
      }
    }
    BotExpression.HAPPY -> Unit
  }
}

fun parseHexColor(hex: String, fallback: Color): Color {
  return try {
    val clean = hex.trim().removePrefix("#")
    if (clean.length == 6) {
      Color(("FF$clean").toLong(16))
    } else {
      fallback
    }
  } catch (_: Exception) {
    fallback
  }
}
