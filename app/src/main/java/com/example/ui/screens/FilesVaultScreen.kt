package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FileLinkType
import com.example.model.LinkedFile
import com.example.viewmodel.UiState

@Composable
fun FilesVaultScreen(
  linkedFilesState: UiState<List<LinkedFile>>,
  onAddFile: (displayName: String, uriOrPath: String, linkType: FileLinkType, mime: String, sizeBytes: Long, notes: String, attachToComposer: Boolean) -> Unit,
  onQueueForChat: (LinkedFile) -> Unit,
  onDeleteFile: (String) -> Unit,
) {
  val context = LocalContext.current
  var showHostLinkDialog by remember { mutableStateOf(false) }

  val docPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      val (name, mime, size) = queryDocMeta(context, uri)
      onAddFile(name, uri.toString(), FileLinkType.ANDROID_ATTACHMENT, mime, size, "Attached from Android", false)
    }
  }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val (name, mime, size) = queryDocMeta(context, uri)
      onAddFile(name, uri.toString(), FileLinkType.ANDROID_ATTACHMENT, mime.ifBlank { "image/*" }, size, "Photo attachment", false)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Files & Host Links", style = MaterialTheme.typography.headlineSmall)
    Text(
      text = "Attach documents or photos from this device (zero storage permissions needed), or save host PC paths and URLs to reference in OpenClaw prompts.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { docPicker.launch(arrayOf("*/*")) },
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("vault_attach_doc_button"),
      ) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("File", maxLines = 1)
      }
      OutlinedButton(
        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("vault_attach_photo_button"),
      ) {
        Icon(Icons.Rounded.Image, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Photo", maxLines = 1)
      }
      Button(
        onClick = { showHostLinkDialog = true },
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .testTag("vault_add_link_button"),
      ) {
        Icon(Icons.Rounded.Link, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Link", maxLines = 1)
      }
    }

    when (linkedFilesState) {
      is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      is UiState.Error -> Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = linkedFilesState.message,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(14.dp),
        )
      }
      is UiState.Success -> {
        val files = linkedFilesState.data
        if (files.isEmpty()) {
          Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                Icons.Rounded.AttachFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
              )
              Text("No files or links yet", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Tap File, Photo, or Link above to save resources your bots can hand directly to OpenClaw.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(files, key = { it.id }) { file ->
              Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = when (file.linkTypeEnum) {
                      FileLinkType.ANDROID_ATTACHMENT -> Icons.Rounded.AttachFile
                      else -> Icons.Rounded.Link
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = file.displayName,
                      style = MaterialTheme.typography.titleSmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = file.uriOrPath,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${file.linkTypeEnum.label}${if (file.sizeBytes > 0) " • ${file.sizeBytes / 1024} KB" else ""}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.secondary,
                    )
                  }
                  IconButton(
                    onClick = { onQueueForChat(file) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      Icons.AutoMirrored.Rounded.Send,
                      contentDescription = "Attach to active bot chat",
                      tint = MaterialTheme.colorScheme.primary,
                    )
                  }
                  IconButton(
                    onClick = { onDeleteFile(file.id) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(
                      Icons.Rounded.DeleteOutline,
                      contentDescription = "Delete link",
                      tint = MaterialTheme.colorScheme.error,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showHostLinkDialog) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isUrl by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showHostLinkDialog = false },
      title = { Text("Add Host File Link") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(120) },
            label = { Text("Display Name") },
            placeholder = { Text("e.g., Project Workspace") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = path,
            onValueChange = {
              path = it.take(500)
              isUrl = it.trim().startsWith("http://") || it.trim().startsWith("https://")
            },
            label = { Text("Host Path or URL") },
            placeholder = { Text("C:\\Users\\Me\\Documents or https://...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(250) },
            label = { Text("Notes (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (path.isNotBlank()) {
              val cleanName = name.ifBlank {
                path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Host Path" }
              }
              onAddFile(
                cleanName,
                path.trim(),
                if (isUrl) FileLinkType.CLOUD_URL else FileLinkType.HOST_PATH,
                "",
                0L,
                notes.trim(),
                false,
              )
              showHostLinkDialog = false
            }
          },
        ) {
          Text("Save Link")
        }
      },
      dismissButton = {
        TextButton(onClick = { showHostLinkDialog = false }) { Text("Cancel") }
      },
    )
  }
}

private fun queryDocMeta(context: Context, uri: Uri): Triple<String, String, Long> {
  var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
  var size = 0L
  val mime = context.contentResolver.getType(uri).orEmpty()
  try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (nIdx >= 0) name = cursor.getString(nIdx) ?: name
        if (sIdx >= 0) size = cursor.getLong(sIdx)
      }
    }
  } catch (_: Exception) {
    // ignore
  }
  return Triple(name, mime, size.coerceAtLeast(0L))
}
