package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun SectionSettingsIconButton(
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = Color(0xFFFFD8F3),
) {
  IconButton(
    onClick = onClick,
    modifier = modifier.size(48.dp),
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_min_settings),
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(23.dp),
    )
  }
}
