package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dpsThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dps_etah_preferences"
)

data class SavedRoleSession(
    val ownerUid: String,
    val docId: String,
    val userId: String,
    val role: String,
    val cloudflareToken: String = "",
)

class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext

    val isDarkMode: Flow<Boolean> = appContext.dpsThemeDataStore.data.map { prefs ->
        prefs[KEY_DARK_MODE] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        appContext.dpsThemeDataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = enabled
        }
    }

    fun savedRoleSession(expectedOwnerUid: String): Flow<SavedRoleSession?> =
        appContext.dpsThemeDataStore.data.map { prefs ->
            val owner = prefs[KEY_SESSION_OWNER_UID].orEmpty()
            val userId = prefs[KEY_SESSION_USER_ID].orEmpty()
            val role = prefs[KEY_SESSION_ROLE].orEmpty()
            val docId = prefs[KEY_SESSION_DOC_ID].orEmpty()
            val cfToken = prefs[KEY_CF_TOKEN].orEmpty()
            if (owner == expectedOwnerUid && userId.isNotBlank() && role.isNotBlank()) {
                SavedRoleSession(
                    ownerUid = owner,
                    docId = docId,
                    userId = userId,
                    role = role,
                    cloudflareToken = cfToken,
                )
            } else {
                null
            }
        }

    suspend fun saveRoleSession(
        ownerUid: String,
        docId: String,
        userId: String,
        role: String,
        cloudflareToken: String = "",
    ) {
        appContext.dpsThemeDataStore.edit { prefs ->
            prefs[KEY_SESSION_OWNER_UID] = ownerUid
            prefs[KEY_SESSION_DOC_ID] = docId
            prefs[KEY_SESSION_USER_ID] = userId
            prefs[KEY_SESSION_ROLE] = role
            if (cloudflareToken.isNotBlank()) {
                prefs[KEY_CF_TOKEN] = cloudflareToken
            } else {
                prefs.remove(KEY_CF_TOKEN)
            }
        }
    }

    suspend fun clearRoleSession() {
        appContext.dpsThemeDataStore.edit { prefs ->
            prefs.remove(KEY_SESSION_OWNER_UID)
            prefs.remove(KEY_SESSION_DOC_ID)
            prefs.remove(KEY_SESSION_USER_ID)
            prefs.remove(KEY_SESSION_ROLE)
            prefs.remove(KEY_CF_TOKEN)
        }
    }

    val customCloudflareUrl: Flow<String> = appContext.dpsThemeDataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_CF_URL].orEmpty()
    }

    suspend fun setCustomCloudflareUrl(url: String) {
        val cleaned = url.trim()
        appContext.dpsThemeDataStore.edit { prefs ->
            if (cleaned.isNotBlank()) {
                prefs[KEY_CUSTOM_CF_URL] = cleaned
            } else {
                prefs.remove(KEY_CUSTOM_CF_URL)
            }
        }
    }

    suspend fun setCustomCloudflareUrlAndResetSession(url: String) {
        val cleaned = url.trim()
        appContext.dpsThemeDataStore.edit { prefs ->
            prefs.remove(KEY_SESSION_OWNER_UID)
            prefs.remove(KEY_SESSION_DOC_ID)
            prefs.remove(KEY_SESSION_USER_ID)
            prefs.remove(KEY_SESSION_ROLE)
            prefs.remove(KEY_CF_TOKEN)
            if (cleaned.isNotBlank()) {
                prefs[KEY_CUSTOM_CF_URL] = cleaned
            } else {
                prefs.remove(KEY_CUSTOM_CF_URL)
            }
        }
    }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val KEY_SESSION_OWNER_UID = stringPreferencesKey("saved_session_owner_uid")
        val KEY_SESSION_DOC_ID = stringPreferencesKey("saved_session_doc_id")
        val KEY_SESSION_USER_ID = stringPreferencesKey("saved_session_user_id")
        val KEY_SESSION_ROLE = stringPreferencesKey("saved_session_role")
        val KEY_CF_TOKEN = stringPreferencesKey("saved_cloudflare_token")
        val KEY_CUSTOM_CF_URL = stringPreferencesKey("custom_cloudflare_server_url")
    }
}
