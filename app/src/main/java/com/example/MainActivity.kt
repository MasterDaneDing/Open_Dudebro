package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import com.example.data.firebase.configureAppCheck
import com.example.ui.navigation.ClawPalRoot
import com.example.ui.theme.ClawPalTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Must run before setContent, ViewModels, or any Firebase operations
    configureAppCheck(this, intent)

    enableEdgeToEdge()
    setContent {
      ClawPalTheme {
        Surface {
          ClawPalRoot()
        }
      }
    }
  }
}
