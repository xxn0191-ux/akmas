package com.aistudio.akmessenger.shared.repository

import android.content.Context
import com.aistudio.akmessenger.shared.firebase.FirebaseAuthClient
import com.aistudio.akmessenger.shared.firebase.FirebaseConfig
import com.aistudio.akmessenger.shared.firebase.RtdbClient
import com.aistudio.akmessenger.shared.firebase.RtdbStatus
import com.aistudio.akmessenger.shared.local.AkDatabase
import com.aistudio.akmessenger.shared.local.CachedRecordEntity
import com.aistudio.akmessenger.shared.local.SessionPreferences
import com.aistudio.akmessenger.shared.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AkSyncEngine(
    context: Context,
    val config: FirebaseConfig,
    dbName: String = "ak_messenger_cache.db"
) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val authClient = FirebaseAuthClient(config, httpClient)
    val rtdbClient = RtdbClient(config, httpClient)
    val database = AkDatabase.getInstance(context, dbName)
    val dao = database.cachedRecordDao()
    val prefs = SessionPreferences(context)

    private val _rtdbStatus = MutableStateFlow<RtdbStatus>(RtdbStatus.Connecting)
    val rtdbStatus: StateFlow<RtdbStatus> = _rtdbStatus.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _typingMap = MutableStateFlow<Map<String, Map<String, Boolean>>>(emptyMap())
    val typingMap: StateFlow<Map<String, Map<String, Boolean>>> = _typingMap.asStateFlow()

    @Volatile
    var activeToken: String? = null

    private var sseJob: Job? = null

    fun observeUsers(): Flow<List<UserProfile>> = observeBucket("users")
    fun observeChats(): Flow<List<ChatThread>> = observeBucket("chats")
    fun observeGroups(): Flow<List<GroupInfo>> = observeBucket("groups")
    fun observeCommunities(): Flow<List<CommunityInfo>> = observeBucket("communities")
    fun observeCalls(): Flow<List<CallSession>> = observeBucket("calls")
    fun observeNotifications(): Flow<List<AdminNotification>> = observeBucket("notifications")
    fun observeReports(): Flow<List<ReportItem>> = observeBucket("reports")
    fun observeAdminLogs(): Flow<List<AdminLog>> = observeBucket("admin_logs")

    fun observeStories(): Flow<List<StoryItem>> = observeBucket<StoryItem>("stories")
        .map { list ->
            val now = System.currentTimeMillis()
            list.filter { it.expiresAt == 0L || it.expiresAt > now }
                .sortedByDescending { it.createdAt }
        }

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> =
        observeBucket<ChatMessage>("msg_$chatId").map { list ->
            val now = System.currentTimeMillis()
            list.filter { it.expiresAt == 0L || it.expiresAt > now }
                .sortedBy { it.timestamp }
        }

    fun observeAllMessagesCount(): Flow<Int> =
        dao.observeBucket("stats_msg_count").map { rows ->
            rows.firstOrNull()?.jsonPayload?.toIntOrNull() ?: 0
        }

    private inline fun <reified T> observeBucket(bucket: String): Flow<List<T>> =
        dao.observeBucket(bucket).map { records ->
            records.mapNotNull { row ->
                try {
                    json.decodeFromString<T>(row.jsonPayload)
                } catch (_: Exception) {
                    null
                }
            }
        }

    suspend fun ensureFreshToken(): String? {
        val session = prefs.current()
        if (session.idToken.isNotBlank()) {
            activeToken = session.idToken
            return session.idToken
        }
        return null
    }

    suspend fun refreshSessionIfPossible(): Boolean {
        val session = prefs.current()
        if (session.refreshToken.isBlank()) return false
        val refreshed = authClient.refreshIdToken(session.refreshToken)
        return refreshed.fold(
            onSuccess = { newAuth ->
                prefs.saveAuth(
                    uid = newAuth.uid.ifBlank { session.uid },
                    email = session.email,
                    idToken = newAuth.idToken,
                    refreshToken = newAuth.refreshToken
                )
                activeToken = newAuth.idToken
                true
            },
            onFailure = { false }
        )
    }

    suspend fun upsertLocal(bucket: String, key: String, jsonStr: String, ts: Long = System.currentTimeMillis()) {
        dao.upsertAll(listOf(CachedRecordEntity("$bucket:$key", bucket, jsonStr, ts)))
    }

    suspend fun deleteLocal(bucket: String, key: String) {
        dao.deleteByKey("$bucket:$key")
    }

    private suspend inline fun <reified T> syncMapNode(
        path: String,
        bucket: String,
        token: String?,
        crossinline idExtractor: (String, T) -> Pair<T, Long>
    ): Throwable? {
        val res = rtdbClient.get<Map<String, T>>(path, token)
        res.onSuccess { map ->
            val now = System.currentTimeMillis()
            if (map == null) {
                dao.clearBucket(bucket)
            } else {
                val rows = map.entries.map { (k, v) ->
                    val (fixed, ts) = idExtractor(k, v)
                    CachedRecordEntity(
                        key = "$bucket:$k",
                        bucket = bucket,
                        jsonPayload = json.encodeToString(kotlinx.serialization.serializer<T>(), fixed),
                        updatedAt = if (ts > 0L) ts else now
                    )
                }
                dao.clearBucket(bucket)
                if (rows.isNotEmpty()) dao.upsertAll(rows)
            }
        }
        return res.exceptionOrNull()
    }

    suspend fun syncAllFromRtdb(includeAdminNodes: Boolean = false): Result<Unit> {
        val token = activeToken ?: prefs.current().idToken.takeIf { it.isNotBlank() }
        var firstError: Throwable? = null
        fun captureError(err: Throwable?) {
            if (firstError == null && err != null) firstError = err
        }

        captureError(syncMapNode<UserProfile>("users", "users", token) { k, v -> v.copy(uid = v.uid.ifBlank { k }) to v.createdAt })
        captureError(syncMapNode<ChatThread>("chats", "chats", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.lastMessageTimestamp })
        captureError(syncMapNode<GroupInfo>("groups", "groups", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.createdAt })
        captureError(syncMapNode<CommunityInfo>("communities", "communities", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.createdAt })
        captureError(syncMapNode<StoryItem>("stories", "stories", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.createdAt })
        captureError(syncMapNode<CallSession>("calls", "calls", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.startedAt })
        captureError(syncMapNode<AdminNotification>("notifications", "notifications", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.sentAt })

        // Sync messages grouped by chatId
        rtdbClient.get<Map<String, Map<String, ChatMessage>>>("messages", token).onSuccess { allChats ->
            var totalMsgs = 0
            allChats?.forEach { (chatId, msgMap) ->
                totalMsgs += msgMap.size
                val bucket = "msg_$chatId"
                val rows = msgMap.entries.map { (msgId, msg) ->
                    val fixed = msg.copy(id = msg.id.ifBlank { msgId }, chatId = chatId)
                    CachedRecordEntity(
                        key = "$bucket:$msgId",
                        bucket = bucket,
                        jsonPayload = json.encodeToString(ChatMessage.serializer(), fixed),
                        updatedAt = fixed.timestamp
                    )
                }
                dao.clearBucket(bucket)
                if (rows.isNotEmpty()) dao.upsertAll(rows)
            }
            upsertLocal("stats_msg_count", "total", totalMsgs.toString())
        }.onFailure { err ->
            if (firstError == null) firstError = err
        }

        // Typing indicators
        rtdbClient.get<Map<String, Map<String, Boolean>>>("typing", token).onSuccess { typing ->
            _typingMap.value = typing.orEmpty()
        }

        // App settings
        rtdbClient.get<AppSettings>("app_settings", token).onSuccess { settings ->
            if (settings != null) _appSettings.value = settings
        }

        if (includeAdminNodes) {
            captureError(syncMapNode<ReportItem>("reports", "reports", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.createdAt })
            captureError(syncMapNode<AdminLog>("admin_logs", "admin_logs", token) { k, v -> v.copy(id = v.id.ifBlank { k }) to v.timestamp })
        }

        return if (firstError == null) {
            _rtdbStatus.value = RtdbStatus.Connected
            Result.success(Unit)
        } else {
            val msg = firstError?.localizedMessage ?: "Firebase RTDB sync error"
            _rtdbStatus.value = RtdbStatus.Error(msg)
            Result.failure(firstError!!)
        }
    }

    fun startRealtimeListener(
        scope: CoroutineScope,
        includeAdminNodes: Boolean = false,
        onNewEvent: suspend () -> Unit = {}
    ) {
        sseJob?.cancel()
        sseJob = scope.launch(Dispatchers.IO) {
            syncAllFromRtdb(includeAdminNodes)
            rtdbClient.streamEvents(
                path = "",
                idTokenProvider = { activeToken },
                onStatus = { status -> _rtdbStatus.value = status }
            ).collect {
                syncAllFromRtdb(includeAdminNodes)
                onNewEvent()
            }
        }
    }

    fun stopRealtimeListener() {
        sseJob?.cancel()
        sseJob = null
    }
}
