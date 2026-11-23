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
import kotlinx.coroutines.flow.first
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
  val urlBookmarks: List<UrlBookmark> = emptyList(),
  val chatDrafts: Map<String, String> = emptyMap(),
)

data class UrlBookmark(val name: String = "", val url: String = "")

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
    val URL_BOOKMARKS = stringPreferencesKey("url_bookmarks_json")
    val CHAT_DRAFTS = stringPreferencesKey("chat_drafts_json")
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
      urlBookmarks = decodeBookmarks(prefs[Keys.URL_BOOKMARKS].orEmpty()),
      chatDrafts = decodeDrafts(prefs[Keys.CHAT_DRAFTS].orEmpty()),
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

  suspend fun setChatDraft(botId: String, text: String) {
    if (botId.isBlank()) return
    context.clawPalDataStore.edit { prefs ->
      val drafts = decodeDrafts(prefs[Keys.CHAT_DRAFTS].orEmpty()).toMutableMap()
      if (text.isBlank()) drafts.remove(botId) else drafts[botId] = text
      val obj = JSONObject()
      drafts.forEach { (k, v) -> obj.put(k, v) }
      prefs[Keys.CHAT_DRAFTS] = obj.toString()
    }
  }

  private fun decodeDrafts(raw: String): Map<String, String> {
    if (raw.isBlank()) return emptyMap()
    return try {
      val obj = JSONObject(raw)
      buildMap {
        obj.keys().forEach { key ->
          val text = obj.optString(key)
          if (text.isNotBlank()) put(key, text)
        }
      }
    } catch (_: Exception) {
      emptyMap()
    }
  }

  private fun decodeBookmarks(raw: String): List<UrlBookmark> {
    if (raw.isBlank()) return emptyList()
    return try {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val url = obj.optString("url").trim()
          if (url.startsWith("http", ignoreCase = true)) {
            add(UrlBookmark(name = obj.optString("name").trim().ifBlank { url }, url = url))
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
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

  suspend fun addUrlBookmark(name: String, url: String) {
    val clean = url.trim()
    if (!clean.startsWith("http", ignoreCase = true)) return
    val existing = prefsFlow.first().urlBookmarks
    if (existing.any { it.url.equals(clean, ignoreCase = true) }) return
    val next = existing + UrlBookmark(name = name.trim().ifBlank { clean }, url = clean)
    context.clawPalDataStore.edit { it[Keys.URL_BOOKMARKS] = encodeBookmarks(next) }
  }

  suspend fun removeUrlBookmark(url: String) {
    val next = prefsFlow.first().urlBookmarks.filterNot { it.url.equals(url, ignoreCase = true) }
    context.clawPalDataStore.edit { it[Keys.URL_BOOKMARKS] = encodeBookmarks(next) }
  }

  private fun encodeBookmarks(list: List<UrlBookmark>): String {
    val arr = JSONArray()
    list.forEach { b ->
      arr.put(JSONObject().put("name", b.name).put("url", b.url))
    }
    return arr.toString()
  }

}
