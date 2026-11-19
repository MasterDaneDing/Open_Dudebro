package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
  primary = TangerinePrimary,
  onPrimary = Color.White,
  primaryContainer = SoftApricotContainer,
  onPrimaryContainer = EspressoInk,
  secondary = TealRobotSecondary,
  onSecondary = Color.White,
  secondaryContainer = SoftMintContainer,
  onSecondaryContainer = Color(0xFF103835),
  tertiary = SunnyHoneyTertiary,
  onTertiary = EspressoInk,
  tertiaryContainer = SoftButterContainer,
  onTertiaryContainer = EspressoInk,
  background = CreamCanvas,
  onBackground = EspressoInk,
  surface = WarmPeachSurface,
  onSurface = EspressoInk,
  surfaceVariant = Color(0xFFF9E9D8),
  onSurfaceVariant = WarmCocoaMuted,
  outline = SoftBorderOutline,
  outlineVariant = Color(0xFFF0DCCB),
  error = AlertRose,
  onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
  primary = DarkTangerine,
  onPrimary = Color(0xFF3E1506),
  primaryContainer = Color(0xFF662912),
  onPrimaryContainer = SoftApricotContainer,
  secondary = DarkTeal,
  onSecondary = Color(0xFF0A3330),
  secondaryContainer = Color(0xFF1D4F4B),
  onSecondaryContainer = SoftMintContainer,
  tertiary = DarkHoney,
  onTertiary = Color(0xFF3B2800),
  tertiaryContainer = Color(0xFF5A3F06),
  onTertiaryContainer = SoftButterContainer,
  background = DarkCocoaBackground,
  onBackground = DarkCreamText,
  surface = DarkCocoaSurface,
  onSurface = DarkCreamText,
  surfaceVariant = DarkCocoaCard,
  onSurfaceVariant = DarkMutedText,
  outline = Color(0xFF594338),
  outlineVariant = Color(0xFF3D2D25),
  error = Color(0xFFFF8989),
  onError = Color(0xFF4D0A0A),
)

val ClawPalShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ClawPalTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = ClawPalTypography,
    shapes = ClawPalShapes,
    content = content,
  )
}
