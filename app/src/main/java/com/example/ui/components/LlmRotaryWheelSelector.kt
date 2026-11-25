package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.WheelLlmOption
import com.example.ui.util.ReplyHaptics
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LlmRotaryWheelSelector(
  selectedOption: WheelLlmOption,
  onSelectOption: (WheelLlmOption) -> Unit,
  modifier: Modifier = Modifier,
  darkSurface: Boolean = false,
  accentColor: Color = Color(0xFFD6006E),
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val options = WheelLlmOption.entries
  val selectedIdx = options.indexOf(selectedOption).coerceAtLeast(0)

  val stepAngle = 360f / options.size.coerceAtLeast(1)

  val wheelAngle by animateFloatAsState(
    targetValue = selectedIdx * stepAngle,
    animationSpec = spring(dampingRatio = 0.48f, stiffness = 360f),
    label = "llm_rotary_wheel_angle",
  )

  val containerColor = if (darkSurface) Color(0xDC23083A) else Color(0xF7FFF9F2)
  val borderColor = if (darkSurface) Color(0xAAFF85D4) else Color(0xFF280842)
  val titleColor = if (darkSurface) Color(0xFFFFD8F3) else Color(0xFF280842)

  Surface(
    shape = RoundedCornerShape(22.dp),
    color = containerColor,
    border = BorderStroke(2.dp, borderColor),
    shadowElevation = 4.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("llm_rotary_wheel_bar"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        // Interactive Spinning Rotary Dial
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
              role = Role.Button,
              onClickLabel = "Spin model wheel",
            ) {
              ReplyHaptics.triggerSoftTick(context, haptic)
              val next = options[(selectedIdx + 1) % options.size]
              onSelectOption(next)
            }
            .semantics {
              contentDescription = "Rotating LLM wheel set to ${selectedOption.fullLabel}. Tap to spin to next OpenClaw model."
            }
            .testTag("llm_rotary_dial_button"),
        ) {
          Canvas(modifier = Modifier.size(46.dp)) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
              color = Color(0xFF2A0845),
              radius = radius,
              center = center,
            )
            drawCircle(
              color = Color(0xFFFFD54F),
              radius = radius - 2.2f,
              center = center,
              style = Stroke(width = 3f),
            )

            rotate(degrees = wheelAngle, pivot = center) {
              val spokeColors = listOf(
                Color(0xFFFF6D00),
                Color(0xFFFF3DA8),
                Color(0xFF4EE3B5),
                Color(0xFF4285F4),
              )
              for (i in options.indices) {
                val spokeColor = spokeColors[i % spokeColors.size]
                val angleRad = Math.toRadians((i * stepAngle.toDouble()) - 90.0)
                val end = Offset(
                  x = center.x + (radius * 0.74f * cos(angleRad)).toFloat(),
                  y = center.y + (radius * 0.74f * sin(angleRad)).toFloat(),
                )
                drawLine(
                  color = spokeColor,
                  start = center,
                  end = end,
                  strokeWidth = 3.8f,
                  cap = StrokeCap.Round,
                )
                drawCircle(
                  color = Color.White,
                  radius = 4.2f,
                  center = end,
                )
                drawCircle(
                  color = spokeColor,
                  radius = 3.0f,
                  center = end,
                )
              }
            }

            drawCircle(
              color = Color.White,
              radius = 6f,
              center = center,
            )
            drawCircle(
              color = accentColor,
              radius = 3.2f,
              center = center,
            )
          }
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text(
            text = "Spin Model Wheel",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = selectedOption.fullLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        options.forEach { option ->
          val isSelected = option == selectedOption
          Surface(
            onClick = {
              if (option != selectedOption) {
                ReplyHaptics.triggerSoftTick(context, haptic)
                onSelectOption(option)
              }
            },
            shape = RoundedCornerShape(50),
            color = when {
              isSelected -> accentColor
              darkSurface -> Color(0x77361252)
              else -> Color(0xFFFFE9FA)
            },
            border = BorderStroke(
              width = if (isSelected) 1.8.dp else 1.2.dp,
              color = if (isSelected) Color(0xFF280842) else accentColor.copy(alpha = 0.45f),
            ),
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 36.dp)
              .testTag("wheel_option_${option.name}"),
          ) {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 6.dp),
            ) {
              Text(
                text = option.wheelLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else titleColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}
