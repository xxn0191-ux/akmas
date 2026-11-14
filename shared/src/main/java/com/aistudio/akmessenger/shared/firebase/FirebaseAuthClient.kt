package com.aistudio.akmessenger.shared.firebase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Serializable
data class AuthSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresInSec: Long
)

class FirebaseAuthClient(
    private val config: FirebaseConfig,
    private val http: OkHttpClient
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val jsonParser = Json { ignoreUnknownKeys = true }

    suspend fun signUpWithEmail(email: String, password: String): Result<AuthSession> =
        authRequest(
            "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${config.apiKey}",
            JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }
        )

    suspend fun signInWithEmail(email: String, password: String): Result<AuthSession> =
        authRequest(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${config.apiKey}",
            JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }
        )

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("requestType", "PASSWORD_RESET")
                put("email", email.trim())
            }.toString().toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${config.apiKey}")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException(extractErrorMessage(text)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshIdToken(refreshToken: String): Result<AuthSession> = withContext(Dispatchers.IO) {
        try {
            val payload = "grant_type=refresh_token&refresh_token=$refreshToken"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url("https://securetoken.googleapis.com/v1/token?key=${config.apiKey}")
                .post(payload)
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException(extractErrorMessage(text)))
                }
                val obj = JSONObject(text)
                Result.success(
                    AuthSession(
                        uid = obj.optString("user_id"),
                        email = "",
                        idToken = obj.optString("id_token"),
                        refreshToken = obj.optString("refresh_token", refreshToken),
                        expiresInSec = obj.optString("expires_in", "3600").toLongOrNull() ?: 3600L
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply { put("idToken", idToken) }.toString().toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${config.apiKey}")
                .post(body)
                .build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException(extractErrorMessage(text)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun authRequest(url: String, payload: JSONObject): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            if (config.apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Firebase API key is not configured."))
            }
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException(extractErrorMessage(text)))
                    }
                    val parsed = jsonParser.decodeFromString<IdentityResponse>(text)
                    Result.success(
                        AuthSession(
                            uid = parsed.localId,
                            email = parsed.email,
                            idToken = parsed.idToken,
                            refreshToken = parsed.refreshToken,
                            expiresInSec = parsed.expiresIn.toLongOrNull() ?: 3600L
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun extractErrorMessage(raw: String): String {
        return try {
            val msg = JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
            when {
                msg.contains("EMAIL_EXISTS") -> "An account with this email already exists."
                msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("INVALID_PASSWORD") ->
                    "Invalid email or password."
                msg.contains("EMAIL_NOT_FOUND") -> "No account found with this email."
                msg.contains("USER_DISABLED") -> "This account has been disabled by Admin."
                msg.contains("OPERATION_NOT_ALLOWED") ->
                    "Email/Password provider is not enabled in Firebase Console (nagad-5b149)."
                msg.contains("TOO_MANY_ATTEMPTS") -> "Too many attempts. Please try again later."
                msg.isNotBlank() -> msg
                else -> "Authentication error"
            }
        } catch (_: Exception) {
            "Authentication request failed"
        }
    }

    @Serializable
    private data class IdentityResponse(
        val localId: String = "",
        val email: String = "",
        val idToken: String = "",
        val refreshToken: String = "",
        val expiresIn: String = "3600"
    )
}
