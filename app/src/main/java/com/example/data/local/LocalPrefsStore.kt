package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.clawPalDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "clawpal_local_prefs"
)

data class LocalUiPrefs(
  val selectedBotId: String = "",
  val showParsedCardExpanded: Boolean = true,
  val takeoverActive: Boolean = false,
  val veniceApiKey: String = "",
  val googleApiKey: String = "",
)

class LocalPrefsStore(private val context: Context) {
  private object Keys {
    val SELECTED_BOT_ID = stringPreferencesKey("selected_bot_id")
    val SHOW_PARSED_EXPANDED = booleanPreferencesKey("show_parsed_expanded")
    val TAKEOVER_ACTIVE = booleanPreferencesKey("takeover_active")
    val VENICE_API_KEY = stringPreferencesKey("venice_api_key")
    val GOOGLE_API_KEY = stringPreferencesKey("google_api_key")
  }

  val prefsFlow: Flow<LocalUiPrefs> = context.clawPalDataStore.data.map { prefs ->
    LocalUiPrefs(
      selectedBotId = prefs[Keys.SELECTED_BOT_ID] ?: "",
      showParsedCardExpanded = prefs[Keys.SHOW_PARSED_EXPANDED] ?: true,
      takeoverActive = prefs[Keys.TAKEOVER_ACTIVE] ?: false,
      veniceApiKey = prefs[Keys.VENICE_API_KEY] ?: "",
      googleApiKey = prefs[Keys.GOOGLE_API_KEY] ?: "",
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

  suspend fun setProviderApiKeys(veniceKey: String, googleKey: String) {
    context.clawPalDataStore.edit {
      it[Keys.VENICE_API_KEY] = veniceKey.trim()
      it[Keys.GOOGLE_API_KEY] = googleKey.trim()
    }
  }
}
