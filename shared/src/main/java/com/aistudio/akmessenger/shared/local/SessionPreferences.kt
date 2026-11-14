package com.aistudio.akmessenger.shared.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.akDataStore: DataStore<Preferences> by preferencesDataStore(name = "ak_messenger_prefs")

data class StoredSession(
    val uid: String = "",
    val email: String = "",
    val idToken: String = "",
    val refreshToken: String = "",
    val e2eePublicKey: String = "",
    val e2eePrivateKey: String = "",
    val darkTheme: Boolean = true,
    val rememberAdminEmail: String = "",
    val chatHeadsEnabled: Boolean = true,
    val pinkMessengerTheme: Boolean = true
)

class SessionPreferences(private val context: Context) {
    private val keyUid = stringPreferencesKey("uid")
    private val keyEmail = stringPreferencesKey("email")
    private val keyIdToken = stringPreferencesKey("id_token")
    private val keyRefreshToken = stringPreferencesKey("refresh_token")
    private val keyPub = stringPreferencesKey("e2ee_pub")
    private val keyPriv = stringPreferencesKey("e2ee_priv")
    private val keyDarkTheme = booleanPreferencesKey("dark_theme")
    private val keyRememberAdmin = stringPreferencesKey("remember_admin_email")
    private val keyChatHeads = booleanPreferencesKey("chat_heads_enabled")
    private val keyPinkTheme = booleanPreferencesKey("pink_messenger_theme")

    val sessionFlow: Flow<StoredSession> = context.akDataStore.data.map { prefs ->
        StoredSession(
            uid = prefs[keyUid].orEmpty(),
            email = prefs[keyEmail].orEmpty(),
            idToken = prefs[keyIdToken].orEmpty(),
            refreshToken = prefs[keyRefreshToken].orEmpty(),
            e2eePublicKey = prefs[keyPub].orEmpty(),
            e2eePrivateKey = prefs[keyPriv].orEmpty(),
            darkTheme = prefs[keyDarkTheme] ?: true,
            rememberAdminEmail = prefs[keyRememberAdmin].orEmpty(),
            chatHeadsEnabled = prefs[keyChatHeads] ?: true,
            pinkMessengerTheme = prefs[keyPinkTheme] ?: true
        )
    }

    suspend fun current(): StoredSession = sessionFlow.first()

    suspend fun saveAuth(uid: String, email: String, idToken: String, refreshToken: String) {
        context.akDataStore.edit { prefs ->
            prefs[keyUid] = uid
            if (email.isNotBlank()) prefs[keyEmail] = email
            prefs[keyIdToken] = idToken
            prefs[keyRefreshToken] = refreshToken
        }
    }

    suspend fun saveKeyPair(publicKey: String, privateKey: String) {
        context.akDataStore.edit { prefs ->
            prefs[keyPub] = publicKey
            prefs[keyPriv] = privateKey
        }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.akDataStore.edit { prefs ->
            prefs[keyDarkTheme] = enabled
        }
    }

    suspend fun setChatHeadsEnabled(enabled: Boolean) {
        context.akDataStore.edit { prefs ->
            prefs[keyChatHeads] = enabled
        }
    }

    suspend fun setPinkMessengerTheme(enabled: Boolean) {
        context.akDataStore.edit { prefs ->
            prefs[keyPinkTheme] = enabled
        }
    }

    suspend fun setRememberAdminEmail(email: String) {
        context.akDataStore.edit { prefs ->
            prefs[keyRememberAdmin] = email
        }
    }

    suspend fun clearAuth() {
        context.akDataStore.edit { prefs ->
            prefs.remove(keyUid)
            prefs.remove(keyEmail)
            prefs.remove(keyIdToken)
            prefs.remove(keyRefreshToken)
        }
    }
}
