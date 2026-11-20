package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeniceModelPickerPanel(
  selectedModelId: String,
  onSelectModelId: (String) -> Unit,
  availableModels: List<VeniceModelInfo>,
  isFetchingModels: Boolean,
  onFetchLiveModels: () -> Unit,
  modifier: Modifier = Modifier,
  showApiKeyOverride: Boolean = false,
  apiKeyValue: String = "",
  onApiKeyChange: (String) -> Unit = {},
  testTagPrefix: String = "host_venice",
) {
  val mergedModels = remember(availableModels) {
    (VeniceModelCatalog.popularPresets + availableModels).distinctBy { it.id }
  }
  var dropdownExpanded by remember { mutableStateOf(false) }
  var showApiKey by remember { mutableStateOf(false) }

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (showApiKeyOverride) {
      OutlinedTextField(
        value = apiKeyValue,
        onValueChange = { onApiKeyChange(it.take(256)) },
        label = { Text("Venice.ai API Key (Optional Override)") },
        placeholder = { Text("Leave blank to use your Host PC's configured key") },
        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(
            onClick = { showApiKey = !showApiKey },
            modifier = Modifier.testTag("${testTagPrefix}_key_toggle"),
          ) {
            Icon(
              imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
              contentDescription = if (showApiKey) "Hide Venice API key" else "Show Venice API key",
            )
          }
        },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("${testTagPrefix}_key_input"),
      )
    }

    Text(
      text = "Quick-Select Coding & Chat Models",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      VeniceModelCatalog.popularPresets.forEach { preset ->
        val selected = selectedModelId.equals(preset.id, ignoreCase = true)
        Surface(
          onClick = { onSelectModelId(preset.id) },
          shape = RoundedCornerShape(50),
          color = if (selected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.surface
          },
          border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          ),
          modifier = Modifier.testTag("${testTagPrefix}_chip_${preset.id}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
          ) {
            Text(
              text = preset.id,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ExposedDropdownMenuBox(
        expanded = dropdownExpanded,
        onExpandedChange = { dropdownExpanded = !dropdownExpanded },
        modifier = Modifier.weight(1f),
      ) {
        OutlinedTextField(
          value = selectedModelId.ifBlank { VeniceModelCatalog.defaultModelId },
          onValueChange = {},
          readOnly = true,
          label = { Text("Venice Model") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
          singleLine = true,
          modifier = Modifier
            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            .fillMaxWidth()
            .testTag("${testTagPrefix}_model_dropdown"),
        )
        ExposedDropdownMenu(
          expanded = dropdownExpanded,
          onDismissRequest = { dropdownExpanded = false },
        ) {
          mergedModels.forEach { model ->
            DropdownMenuItem(
              text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = model.id,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (model.id == selectedModelId) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                  ) {
                    Text(
                      text = model.category,
                      style = MaterialTheme.typography.labelSmall,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              },
              onClick = {
                onSelectModelId(model.id)
                dropdownExpanded = false
              },
            )
          }
        }
      }

      OutlinedButton(
        onClick = onFetchLiveModels,
        enabled = !isFetchingModels,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = Modifier
          .defaultMinSize(minHeight = 52.dp)
          .testTag("${testTagPrefix}_fetch_models_button"),
      ) {
        if (isFetchingModels) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            Icons.Rounded.Refresh,
            contentDescription = "Fetch live models from Venice.ai",
            modifier = Modifier.size(16.dp),
          )
        }
        Spacer(Modifier.width(5.dp))
        Text(
          text = if (isFetchingModels) "Loading" else "Fetch Live Models",
          style = MaterialTheme.typography.labelMedium,
          maxLines = 1,
        )
      }
    }

    OutlinedTextField(
      value = selectedModelId,
      onValueChange = { onSelectModelId(it.take(120)) },
      label = { Text("Custom Model ID") },
      placeholder = { Text("e.g., claude-sonnet-4-6 or exact Venice slug") },
      supportingText = {
        val extraCount = (mergedModels.size - VeniceModelCatalog.popularPresets.size).coerceAtLeast(0)
        Text(
          if (extraCount > 0) {
            "${mergedModels.size} models available in dropdown ($extraCount fetched live)"
          } else {
            "Tap a preset chip, pick from dropdown, or paste any Venice model slug."
          }
        )
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("${testTagPrefix}_custom_model_input"),
    )
  }
}
