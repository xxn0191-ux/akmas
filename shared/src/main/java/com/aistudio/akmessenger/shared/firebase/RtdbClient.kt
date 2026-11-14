package com.aistudio.akmessenger.shared.firebase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed interface RtdbStatus {
    data object Connecting : RtdbStatus
    data object Connected : RtdbStatus
    data class Error(val message: String) : RtdbStatus
}

data class SseEvent(
    val event: String,
    val path: String,
    val dataJson: String
)

/**
 * Real-time Firebase Realtime Database client using REST + Server-Sent Events (SSE)
 * directly against the user's configured Firebase RTDB URL.
 */
class RtdbClient(
    private val config: FirebaseConfig,
    private val http: OkHttpClient
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val sseClient: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private fun buildUrl(path: String, idToken: String?, queryExtra: String = ""): String {
        val base = config.databaseUrl.trimEnd('/')
        val cleanPath = path.trim('/')
        val urlBase = if (cleanPath.isEmpty()) "$base/.json" else "$base/$cleanPath.json"
        val params = mutableListOf<String>()
        if (!idToken.isNullOrBlank()) params.add("auth=$idToken")
        if (queryExtra.isNotBlank()) params.add(queryExtra)
        return if (params.isEmpty()) urlBase else "$urlBase?${params.joinToString("&")}"
    }

    suspend fun getRaw(path: String, idToken: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(buildUrl(path, idToken)).get().build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) Result.success(body)
                else Result.failure(IllegalStateException("RTDB HTTP ${resp.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend inline fun <reified T> get(path: String, idToken: String?): Result<T?> {
        val rawResult = getRaw(path, idToken)
        return rawResult.mapCatching { body ->
            if (body.isBlank() || body == "null") null
            else json.decodeFromString<T>(body)
        }
    }

    suspend fun putRaw(path: String, jsonBody: String, idToken: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(buildUrl(path, idToken))
                    .put(jsonBody.toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(IllegalStateException("RTDB HTTP ${resp.code}: $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend inline fun <reified T> put(path: String, value: T, idToken: String?): Result<Unit> =
        putRaw(path, json.encodeToString(value), idToken)

    suspend fun patchRaw(path: String, jsonBody: String, idToken: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(buildUrl(path, idToken))
                    .patch(jsonBody.toRequestBody(jsonMedia))
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(IllegalStateException("RTDB HTTP ${resp.code}: $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun delete(path: String, idToken: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(buildUrl(path, idToken))
                    .delete()
                    .build()
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) Result.success(Unit)
                    else Result.failure(IllegalStateException("RTDB HTTP ${resp.code}: $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Opens a live Firebase RTDB Server-Sent Events stream for real-time updates.
     */
    fun streamEvents(
        path: String,
        idTokenProvider: () -> String?,
        onStatus: (RtdbStatus) -> Unit
    ): Flow<SseEvent> = flow {
        var backoffMs = 1500L
        while (currentCoroutineContext().isActive) {
            onStatus(RtdbStatus.Connecting)
            try {
                val req = Request.Builder()
                    .url(buildUrl(path, idTokenProvider()))
                    .header("Accept", "text/event-stream")
                    .build()
                sseClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val err = resp.body?.string().orEmpty()
                        onStatus(RtdbStatus.Error("HTTP ${resp.code}: $err"))
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(15000L)
                        return@use
                    }
                    backoffMs = 1500L
                    onStatus(RtdbStatus.Connected)
                    val stream = resp.body?.byteStream() ?: return@use
                    val reader = BufferedReader(InputStreamReader(stream))
                    var currentEvent = ""
                    val dataBuilder = StringBuilder()
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        when {
                            line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                if (dataBuilder.isNotEmpty()) dataBuilder.append('\n')
                                dataBuilder.append(line.removePrefix("data:").trim())
                            }
                            line.isBlank() -> {
                                if (currentEvent.isNotBlank() && dataBuilder.isNotEmpty()) {
                                    val payload = dataBuilder.toString()
                                    if (currentEvent == "put" || currentEvent == "patch") {
                                        emit(SseEvent(currentEvent, path, payload))
                                    }
                                }
                                currentEvent = ""
                                dataBuilder.clear()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                onStatus(RtdbStatus.Error(e.localizedMessage ?: "Realtime stream disconnected"))
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(15000L)
        }
    }.flowOn(Dispatchers.IO)
}
