package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Push-to-talk style voice button: tap to start, tap again to stop.
 * Uses the on-device SpeechRecognizer; partial text streams to [onPartial] so the
 * composer updates live, and the final result is delivered to [onResult].
 */
@Composable
fun VoiceInputButton(
  onPartial: (String) -> Unit,
  onResult: (String) -> Unit,
  modifier: Modifier = Modifier,
  idleTint: Color = Color.White,
  onError: (String) -> Unit = {},
) {
  val context = LocalContext.current
  val partialCb by rememberUpdatedState(onPartial)
  val resultCb by rememberUpdatedState(onResult)
  val errorCb by rememberUpdatedState(onError)

  var listening by remember { mutableStateOf(false) }
  var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (!granted) errorCb("Microphone permission denied")
  }

  DisposableEffect(Unit) {
    onDispose {
      recognizer?.destroy()
      recognizer = null
    }
  }

  fun ensureRecognizer(): SpeechRecognizer {
    recognizer?.let { return it }
    return SpeechRecognizer.createSpeechRecognizer(context).apply {
      setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listening = true }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { listening = false }
        override fun onError(error: Int) {
          listening = false
          if (error != SpeechRecognizer.ERROR_NO_MATCH) errorCb("Voice error ($error)")
        }
        override fun onResults(results: Bundle?) {
          listening = false
          results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(resultCb)
        }
        override fun onPartialResults(partialResults: Bundle?) {
          partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(partialCb)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
      })
      recognizer = this
    }
  }

  fun start() {
    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    if (!hasPerm) {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      errorCb("No speech service on this device")
      return
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    listening = true
    ensureRecognizer().startListening(intent)
  }

  IconButton(
    onClick = {
      if (listening) {
        recognizer?.stopListening()
        listening = false
      } else {
        start()
      }
    },
    colors = IconButtonDefaults.iconButtonColors(
      containerColor = if (listening) Color(0xFFFF2E93) else Color.Transparent,
      contentColor = Color.White,
    ),
    modifier = modifier.size(48.dp).testTag("voice_input_button"),
  ) {
    Icon(
      imageVector = if (listening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
      contentDescription = if (listening) "Stop voice input" else "Start voice input",
      tint = if (listening) Color.White else idleTint,
    )
  }
}
