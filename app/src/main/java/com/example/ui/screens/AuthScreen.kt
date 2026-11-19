package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.model.AccessoryStyle
import com.example.model.BotArchetype
import com.example.model.BotExpression
import com.example.model.HatStyle
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.theme.SuperArcadeFontFamily
import com.example.ui.util.ReplyHaptics
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val AUTH_TAG = "ClawPalAuth"

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val tapExpressions = remember {
    listOf(
      BotExpression.HAPPY,
      BotExpression.UH_HUH,
      BotExpression.THINKING,
      BotExpression.WORRIED,
      BotExpression.SORRY,
      BotExpression.ALERT,
    )
  }
  var tapIndex by remember { mutableIntStateOf(0) }
  val heroExpression = tapExpressions[tapIndex % tapExpressions.size]
  val headScale = remember { Animatable(1f) }
  val headTilt = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = onAuthSuccess,
      onUnauthenticated = {},
      scope = scope,
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
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
      .systemBarsPadding()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      val isLandscape = maxWidth > maxHeight && maxWidth >= 520.dp
      val portraitHeadSize = minOf(maxWidth * 0.96f, maxHeight * 0.54f)
        .coerceAtLeast(300.dp)
        .coerceAtMost(480.dp)
      val landscapeHeadSize = minOf(maxWidth * 0.42f, maxHeight * 0.86f)
        .coerceIn(190.dp, 460.dp)
      val titleFontSp = if (isLandscape) {
        minOf(maxWidth.value * 0.056f, maxHeight.value * 0.145f).coerceIn(32f, 58f)
      } else {
        minOf(maxWidth.value * 0.10f, 42f).coerceAtLeast(28f)
      }

      @Composable
      fun MascotHeadBox(headSize: androidx.compose.ui.unit.Dp) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(headSize + 4.dp)
            .scale(headScale.value)
            .rotate(headTilt.value)
            .clip(CircleShape)
            .clickable(
              role = Role.Button,
              onClickLabel = "Tap character head to react",
            ) {
              tapIndex = (tapIndex + 1) % tapExpressions.size
              ReplyHaptics.triggerSoftTick(context, haptic)
              val wobbleDir = if (tapIndex % 2 == 0) 9f else -9f
              scope.launch {
                headScale.snapTo(0.88f)
                headScale.animateTo(
                  targetValue = 1f,
                  animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
                )
              }
              scope.launch {
                headTilt.snapTo(wobbleDir)
                headTilt.animateTo(
                  targetValue = 0f,
                  animationSpec = spring(dampingRatio = 0.45f, stiffness = 360f),
                )
              }
            }
            .semantics {
              contentDescription = "Claw Dudebro mascot head, currently ${heroExpression.label}. Tap to react."
            }
            .testTag("auth_hero_avatar"),
        ) {
          BotVectorAvatar(
            archetype = BotArchetype.GLASSES_FELLOW,
            hatStyle = HatStyle.NONE,
            accessoryStyle = AccessoryStyle.FOREHEAD_GLASSES,
            primaryColor = Color(0xFFEEA05E),
            accentColor = Color(0xFFDC4D21),
            expression = heroExpression,
            size = headSize,
          )
        }
      }

      @Composable
      fun SignInCard(modifier: Modifier = Modifier) {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = Color(0xFAFFF9FC)),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Computer,
                contentDescription = null,
                tint = Color(0xFFD6006E),
              )
              Text(
                text = "Sync your bots & host profiles",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF280B2E),
              )
            }

            if (errorMessage != null) {
              Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = errorMessage.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            Button(
              onClick = {
                errorMessage = null
                isLoading = true
                onGoogleSignInClicked(
                  context = context,
                  credentialManager = credentialManager,
                  onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isLoading = false
                    errorMessage = msg
                  },
                  scope = scope,
                  onAuthCancelled = {
                    isLoading = false
                  },
                )
              },
              enabled = !isLoading,
              shape = RoundedCornerShape(16.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD6006E),
                contentColor = Color.White,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button"),
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = Color.White,
                  strokeWidth = 2.5.dp,
                )
              } else {
                Icon(Icons.Rounded.Login, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Sign in with Google", style = MaterialTheme.typography.titleSmall)
              }
            }
          }
        }
      }

      if (isLandscape) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          Box(
            modifier = Modifier.weight(0.45f),
            contentAlignment = Alignment.Center,
          ) {
            MascotHeadBox(headSize = landscapeHeadSize)
          }

          Column(
            modifier = Modifier
              .weight(0.55f)
              .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
          ) {
            OutlinedArcadeTitle(
              title = "Claw Dudebro",
              fontSizeSp = titleFontSp,
            )
            SignInCard()
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          MascotHeadBox(headSize = portraitHeadSize)

          OutlinedArcadeTitle(
            title = "Claw Dudebro",
            fontSizeSp = titleFontSp,
          )

          SignInCard()
        }
      }
    }
  }
}

@Composable
internal fun OutlinedArcadeTitle(
  title: String,
  fontSizeSp: Float = 38f,
  modifier: Modifier = Modifier,
) {
  val strokePx = with(LocalDensity.current) { (fontSizeSp * 0.16f).dp.toPx() }
  val baseStyle = MaterialTheme.typography.displaySmall.copy(
    fontFamily = SuperArcadeFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp + 8f).sp,
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    // Crisp black outline + subtle arcade drop shadow
    Text(
      text = title,
      style = baseStyle.copy(
        drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
        shadow = Shadow(
          color = Color(0xAA000000),
          offset = Offset(2.5f, 4.5f),
          blurRadius = 3f,
        ),
      ),
      color = Color.Black,
      textAlign = TextAlign.Center,
    )
    // Vibrant lavender purple fill
    Text(
      text = title,
      style = baseStyle,
      color = Color(0xFFCFA6FF),
      textAlign = TextAlign.Center,
    )
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onUnauthenticated()
    return
  }
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  scope.launch {
    try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
    return
  }
  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      val activityContext = context as? Activity
        ?: throw IllegalStateException("Activity context required for Credential Manager.")
      val cred = credentialManager.getCredential(activityContext, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type returned.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e(AUTH_TAG, "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed")
    }
  }
}
