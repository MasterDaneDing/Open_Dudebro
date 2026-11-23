package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.clawPalDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "clawpal_local_prefs"
)

data class LocalUiPrefs(
  val selectedBotId: String = "",
  val showParsedCardExpanded: Boolean = true,
  val takeoverActive: Boolean = false,
  val hostPaused: Boolean = false,
  val veniceApiKey: String = "",
  val googleApiKey: String = "",
  val globalVeniceModel: String = VeniceModelCatalog.defaultModelId,
  val cachedVeniceModels: List<VeniceModelInfo> = emptyList(),
  val devicePairingId: String = "claw-dudebro-apk",
  val driveBridgeEnabled: Boolean = false,
  val driveBridgeFolderUri: String = "",
)

class LocalPrefsStore(private val context: Context) {
  private object Keys {
    val SELECTED_BOT_ID = stringPreferencesKey("selected_bot_id")
    val SHOW_PARSED_EXPANDED = booleanPreferencesKey("show_parsed_expanded")
    val TAKEOVER_ACTIVE = booleanPreferencesKey("takeover_active")
    val HOST_PAUSED = booleanPreferencesKey("host_paused")
    val VENICE_API_KEY = stringPreferencesKey("venice_api_key")
    val GOOGLE_API_KEY = stringPreferencesKey("google_api_key")
    val GLOBAL_VENICE_MODEL = stringPreferencesKey("global_venice_model")
    val CACHED_VENICE_MODELS = stringPreferencesKey("cached_venice_models_json")
    val DEVICE_PAIRING_ID = stringPreferencesKey("device_pairing_id")
    val DRIVE_BRIDGE_ENABLED = booleanPreferencesKey("drive_bridge_enabled")
    val DRIVE_BRIDGE_FOLDER_URI = stringPreferencesKey("drive_bridge_folder_uri")
  }

  val prefsFlow: Flow<LocalUiPrefs> = context.clawPalDataStore.data.map { prefs ->
    LocalUiPrefs(
      selectedBotId = prefs[Keys.SELECTED_BOT_ID] ?: "",
      showParsedCardExpanded = prefs[Keys.SHOW_PARSED_EXPANDED] ?: true,
      takeoverActive = prefs[Keys.TAKEOVER_ACTIVE] ?: false,
      hostPaused = prefs[Keys.HOST_PAUSED] ?: false,
      veniceApiKey = prefs[Keys.VENICE_API_KEY] ?: "",
      googleApiKey = prefs[Keys.GOOGLE_API_KEY] ?: "",
      globalVeniceModel = prefs[Keys.GLOBAL_VENICE_MODEL]?.takeIf { it.isNotBlank() }
        ?: VeniceModelCatalog.defaultModelId,
      cachedVeniceModels = decodeModels(prefs[Keys.CACHED_VENICE_MODELS].orEmpty()),
      devicePairingId = prefs[Keys.DEVICE_PAIRING_ID]?.takeIf { it.isNotBlank() }
        ?: "claw-dudebro-apk",
      driveBridgeEnabled = prefs[Keys.DRIVE_BRIDGE_ENABLED] ?: false,
      driveBridgeFolderUri = prefs[Keys.DRIVE_BRIDGE_FOLDER_URI] ?: "",
    )
  }

  suspend fun setSelectedBotId(botId: String) {
    context.clawPalDataStore.edit { it[Keys.SELECTED_BOT_ID] = botId }
  }

  suspend fun setShowParsedExpanded(expanded: Boolean) {
    context.clawPalDataStore.edit { it[Keys.SHOW_PARSED_EXPANDED] = expanded }
  }

  suspend fun setTakeoverActive(active: Boolean) {
    context.clawPalDataStore.edit { it[Keys.TAKEOVER_ACTIVE] = active }
  }

  suspend fun setHostPaused(paused: Boolean) {
    context.clawPalDataStore.edit { it[Keys.HOST_PAUSED] = paused }
  }

  suspend fun setProviderApiKeys(
    veniceKey: String,
    googleKey: String,
    globalVeniceModel: String? = null,
  ) {
    context.clawPalDataStore.edit {
      it[Keys.VENICE_API_KEY] = veniceKey.trim()
      it[Keys.GOOGLE_API_KEY] = googleKey.trim()
      if (!globalVeniceModel.isNullOrBlank()) {
        it[Keys.GLOBAL_VENICE_MODEL] = globalVeniceModel.trim()
      }
    }
  }

  suspend fun setDeviceAndBridgeConfig(
    devicePairingId: String,
    bridgeEnabled: Boolean,
    bridgeFolderUri: String,
  ) {
    context.clawPalDataStore.edit {
      it[Keys.DEVICE_PAIRING_ID] = devicePairingId.trim().ifBlank { "claw-dudebro-apk" }
      it[Keys.DRIVE_BRIDGE_ENABLED] = bridgeEnabled
      it[Keys.DRIVE_BRIDGE_FOLDER_URI] = bridgeFolderUri.trim()
    }
  }

  suspend fun setCachedVeniceModels(models: List<VeniceModelInfo>) {
    val arr = JSONArray()
    models.take(250).forEach { m ->
      arr.put(
        JSONObject()
          .put("id", m.id)
          .put("label", m.label)
          .put("category", m.category)
      )
    }
    context.clawPalDataStore.edit {
      it[Keys.CACHED_VENICE_MODELS] = arr.toString()
    }
  }

  private fun decodeModels(raw: String): List<VeniceModelInfo> {
    if (raw.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val id = obj.optString("id").trim()
          if (id.isNotEmpty()) {
            add(
              VeniceModelInfo(
                id = id,
                label = obj.optString("label", id).ifBlank { id },
                category = obj.optString("category", "Text").ifBlank { "Text" },
              )
            )
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }
}
