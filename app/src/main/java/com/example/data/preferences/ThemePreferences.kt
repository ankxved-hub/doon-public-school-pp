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
import org.json.JSONArray
import org.json.JSONObject

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

data class SavedAccountEntry(
    val ownerUid: String,
    val docId: String,
    val userId: String,
    val fullName: String,
    val role: String,
    val classInfo: String = "",
    val cloudflareToken: String = "",
    val loginTicketId: String = "",
    val lastUsedAt: Long = System.currentTimeMillis(),
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

    fun savedAccounts(expectedOwnerUid: String): Flow<List<SavedAccountEntry>> =
        appContext.dpsThemeDataStore.data.map { prefs ->
            parseSavedAccounts(prefs[KEY_SAVED_ACCOUNTS_JSON].orEmpty())
                .filter { it.ownerUid == expectedOwnerUid && it.userId.isNotBlank() }
                .sortedByDescending { it.lastUsedAt }
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

    suspend fun upsertSavedAccount(
        entry: SavedAccountEntry,
        makeActive: Boolean = true,
    ) {
        appContext.dpsThemeDataStore.edit { prefs ->
            val current = parseSavedAccounts(prefs[KEY_SAVED_ACCOUNTS_JSON].orEmpty()).toMutableList()
            val existingIdx = current.indexOfFirst { existing ->
                existing.ownerUid == entry.ownerUid && (
                    (entry.docId.isNotBlank() && existing.docId == entry.docId) ||
                        (existing.userId.equals(entry.userId, ignoreCase = true) && existing.role == entry.role)
                )
            }
            val merged = if (existingIdx >= 0) {
                val prev = current[existingIdx]
                entry.copy(
                    fullName = entry.fullName.ifBlank { prev.fullName },
                    classInfo = entry.classInfo.ifBlank { prev.classInfo },
                    cloudflareToken = entry.cloudflareToken.ifBlank { prev.cloudflareToken },
                    loginTicketId = entry.loginTicketId.ifBlank { prev.loginTicketId },
                    lastUsedAt = System.currentTimeMillis(),
                )
            } else {
                entry.copy(lastUsedAt = System.currentTimeMillis())
            }
            if (existingIdx >= 0) {
                current[existingIdx] = merged
            } else {
                current.add(0, merged)
            }
            prefs[KEY_SAVED_ACCOUNTS_JSON] = serializeSavedAccounts(current.take(12))

            if (makeActive) {
                prefs[KEY_SESSION_OWNER_UID] = merged.ownerUid
                prefs[KEY_SESSION_DOC_ID] = merged.docId
                prefs[KEY_SESSION_USER_ID] = merged.userId
                prefs[KEY_SESSION_ROLE] = merged.role
                if (merged.cloudflareToken.isNotBlank()) {
                    prefs[KEY_CF_TOKEN] = merged.cloudflareToken
                } else {
                    prefs.remove(KEY_CF_TOKEN)
                }
            }
        }
    }

    suspend fun removeSavedAccount(ownerUid: String, docId: String, userId: String) {
        appContext.dpsThemeDataStore.edit { prefs ->
            val current = parseSavedAccounts(prefs[KEY_SAVED_ACCOUNTS_JSON].orEmpty())
            val remaining = current.filterNot { item ->
                item.ownerUid == ownerUid && (
                    (docId.isNotBlank() && item.docId == docId) ||
                        item.userId.equals(userId, ignoreCase = true)
                )
            }
            if (remaining.isEmpty()) {
                prefs.remove(KEY_SAVED_ACCOUNTS_JSON)
            } else {
                prefs[KEY_SAVED_ACCOUNTS_JSON] = serializeSavedAccounts(remaining)
            }
            val activeDoc = prefs[KEY_SESSION_DOC_ID].orEmpty()
            val activeUid = prefs[KEY_SESSION_USER_ID].orEmpty()
            if ((docId.isNotBlank() && activeDoc == docId) || activeUid.equals(userId, ignoreCase = true)) {
                prefs.remove(KEY_SESSION_OWNER_UID)
                prefs.remove(KEY_SESSION_DOC_ID)
                prefs.remove(KEY_SESSION_USER_ID)
                prefs.remove(KEY_SESSION_ROLE)
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
            prefs.remove(KEY_SAVED_ACCOUNTS_JSON)
            if (cleaned.isNotBlank()) {
                prefs[KEY_CUSTOM_CF_URL] = cleaned
            } else {
                prefs.remove(KEY_CUSTOM_CF_URL)
            }
        }
    }

    private fun parseSavedAccounts(rawJson: String): List<SavedAccountEntry> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val userId = obj.optString("userId").trim()
                    val role = obj.optString("role").trim()
                    if (userId.isBlank() || role.isBlank()) continue
                    add(
                        SavedAccountEntry(
                            ownerUid = obj.optString("ownerUid"),
                            docId = obj.optString("docId"),
                            userId = userId,
                            fullName = obj.optString("fullName").ifBlank { userId },
                            role = role,
                            classInfo = obj.optString("classInfo"),
                            cloudflareToken = obj.optString("cloudflareToken"),
                            loginTicketId = obj.optString("loginTicketId"),
                            lastUsedAt = obj.optLong("lastUsedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeSavedAccounts(accounts: List<SavedAccountEntry>): String {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("ownerUid", acc.ownerUid)
                    put("docId", acc.docId)
                    put("userId", acc.userId)
                    put("fullName", acc.fullName)
                    put("role", acc.role)
                    put("classInfo", acc.classInfo)
                    put("cloudflareToken", acc.cloudflareToken)
                    put("loginTicketId", acc.loginTicketId)
                    put("lastUsedAt", acc.lastUsedAt)
                }
            )
        }
        return arr.toString()
    }

    private companion object {
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val KEY_SESSION_OWNER_UID = stringPreferencesKey("saved_session_owner_uid")
        val KEY_SESSION_DOC_ID = stringPreferencesKey("saved_session_doc_id")
        val KEY_SESSION_USER_ID = stringPreferencesKey("saved_session_user_id")
        val KEY_SESSION_ROLE = stringPreferencesKey("saved_session_role")
        val KEY_CF_TOKEN = stringPreferencesKey("saved_cloudflare_token")
        val KEY_CUSTOM_CF_URL = stringPreferencesKey("custom_cloudflare_server_url")
        val KEY_SAVED_ACCOUNTS_JSON = stringPreferencesKey("saved_multi_accounts_json")
    }
}
