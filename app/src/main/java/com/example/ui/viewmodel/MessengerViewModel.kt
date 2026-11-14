package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.akmessenger.shared.firebase.FirebaseConfig
import com.aistudio.akmessenger.shared.firebase.RtdbStatus
import com.aistudio.akmessenger.shared.local.StoredSession
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.repository.AkSyncEngine
import com.aistudio.akmessenger.shared.repository.AkUserRepository
import com.aistudio.akmessenger.shared.util.AkNotificationManager
import com.example.BuildConfig
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MessengerViewModel(application: Application) : AndroidViewModel(application) {
    private val config = FirebaseConfig(
        apiKey = BuildConfig.FIREBASE_API_KEY,
        authDomain = BuildConfig.FIREBASE_AUTH_DOMAIN,
        databaseUrl = BuildConfig.FIREBASE_DATABASE_URL,
        projectId = BuildConfig.FIREBASE_PROJECT_ID
    )

    val syncEngine = AkSyncEngine(application, config, "ak_messenger_user.db")
    val repo = AkUserRepository(syncEngine)

    val sessionFlow: StateFlow<StoredSession> = syncEngine.prefs.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StoredSession())

    val rtdbStatus: StateFlow<RtdbStatus> = syncEngine.rtdbStatus
    val appSettings: StateFlow<AppSettings> = syncEngine.appSettings
    val typingMap: StateFlow<Map<String, Map<String, Boolean>>> = syncEngine.typingMap

    val users: StateFlow<List<UserProfile>> = syncEngine.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chats: StateFlow<List<ChatThread>> = syncEngine.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupInfo>> = syncEngine.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<CommunityInfo>> = syncEngine.observeCommunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stories: StateFlow<List<StoryItem>> = syncEngine.observeStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallSession>> = syncEngine.observeCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AdminNotification>> = syncEngine.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingTimerJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    private var lastNotifiedNotifId: String = ""
    private var lastNotifiedCallId: String = ""
    private val seenIncomingMsgTimestamp = mutableMapOf<String, Long>()

    init {
        AkNotificationManager.ensureChannels(application)
        viewModelScope.launch {
            val session = syncEngine.prefs.current()
            if (session.idToken.isNotBlank()) {
                syncEngine.activeToken = session.idToken
                val syncRes = syncEngine.syncAllFromRtdb()
                if (syncRes.isFailure) {
                    syncEngine.refreshSessionIfPossible()
                }
                startRealtimeSync()
            }
        }
    }

    fun startRealtimeSync() {
        syncEngine.startRealtimeListener(viewModelScope, includeAdminNodes = false) {
            checkIncomingAlerts()
        }
    }

    private suspend fun checkIncomingAlerts() {
        val myUid = sessionFlow.value.uid
        if (myUid.isBlank()) return
        val ctx = getApplication<Application>()

        val latestNotif = notifications.value.maxByOrNull { it.sentAt }
        if (latestNotif != null && latestNotif.id != lastNotifiedNotifId &&
            (latestNotif.targetType == "all" || latestNotif.targetId == myUid)
        ) {
            lastNotifiedNotifId = latestNotif.id
            AkNotificationManager.showNotification(
                ctx,
                AkNotificationManager.CHANNEL_ADMIN_NOTICES,
                latestNotif.id.hashCode(),
                latestNotif.title,
                latestNotif.message,
                R.mipmap.ic_launcher
            )
        }

        val ringingCall = calls.value.firstOrNull {
            it.status == "ringing" && it.calleeIds[myUid] == true && it.callerId != myUid
        }
        if (ringingCall != null && ringingCall.id != lastNotifiedCallId) {
            lastNotifiedCallId = ringingCall.id
            AkNotificationManager.showNotification(
                ctx,
                AkNotificationManager.CHANNEL_CALLS,
                ringingCall.id.hashCode(),
                if (ringingCall.type == "video") "Incoming Video Call" else "Incoming Audio Call",
                "${ringingCall.callerName} is calling you on AK Messenger",
                R.mipmap.ic_launcher
            )
        }

        val useChatHeads = sessionFlow.value.chatHeadsEnabled && com.example.overlay.ChatHeadManager.canDrawOverlays(ctx)
        chats.value
            .filter { it.participantIds[myUid] == true && it.lastMessageSenderId != myUid && it.lastMessageTimestamp > 0L }
            .forEach { chat ->
                val prev = seenIncomingMsgTimestamp[chat.id]
                seenIncomingMsgTimestamp[chat.id] = chat.lastMessageTimestamp
                if (prev != null && chat.lastMessageTimestamp > prev && !useChatHeads) {
                    val senderName = chat.participantNames[chat.lastMessageSenderId] ?: chat.title.ifBlank { "AK Messenger" }
                    AkNotificationManager.showNotification(
                        ctx,
                        AkNotificationManager.CHANNEL_MESSAGES,
                        chat.id.hashCode(),
                        senderName,
                        chat.lastMessagePreview.ifBlank { "New message received" },
                        R.mipmap.ic_launcher
                    )
                }
            }
    }

    fun currentUserProfile(): UserProfile? {
        val uid = sessionFlow.value.uid
        return users.value.firstOrNull { it.uid == uid }
    }

    fun messagesForChat(chatId: String): Flow<List<ChatMessage>> =
        syncEngine.observeMessages(chatId)

    fun clearAuthError() { _authError.value = null }
    fun clearInfoBanner() { _infoBanner.value = null }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch { syncEngine.prefs.setDarkTheme(enabled) }
    }

    fun setChatHeadsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            syncEngine.prefs.setChatHeadsEnabled(enabled)
            val ctx = getApplication<Application>()
            if (enabled && com.example.overlay.ChatHeadManager.canDrawOverlays(ctx)) {
                com.example.overlay.ChatHeadManager.startService(ctx)
            } else {
                com.example.overlay.ChatHeadManager.stopService(ctx)
            }
        }
    }

    fun setPinkMessengerTheme(enabled: Boolean) {
        viewModelScope.launch { syncEngine.prefs.setPinkMessengerTheme(enabled) }
    }

    fun register(
        fullName: String,
        username: String,
        phone: String,
        email: String,
        password: String,
        avatarBase64: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            val res = repo.registerUser(fullName, username, phone, email, password, avatarBase64)
            _authBusy.value = false
            res.onSuccess {
                startRealtimeSync()
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Registration failed"
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            val res = repo.loginUser(email, password)
            _authBusy.value = false
            res.onSuccess {
                startRealtimeSync()
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Login failed"
            }
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _authBusy.value = true
            val res = repo.sendPasswordReset(email)
            _authBusy.value = false
            res.onSuccess {
                _infoBanner.value = "Password recovery email sent to $email"
            }.onFailure { err ->
                _authError.value = err.message ?: "Failed to send reset email"
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout(sessionFlow.value.uid)
            onDone()
        }
    }

    fun deleteMyAccount(onDone: () -> Unit) {
        viewModelScope.launch {
            val me = currentUserProfile()
            val res = repo.deleteAccount(sessionFlow.value.uid, me?.username.orEmpty())
            res.onSuccess { onDone() }
                .onFailure { _infoBanner.value = it.message ?: "Delete account failed" }
        }
    }

    fun setOnline(online: Boolean) {
        val uid = sessionFlow.value.uid
        if (uid.isNotBlank()) {
            viewModelScope.launch { repo.setOnlinePresence(uid, online) }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val res = syncEngine.syncAllFromRtdb()
            if (res.isFailure) {
                _infoBanner.value = res.exceptionOrNull()?.message ?: "Sync failed"
            }
        }
    }

    suspend fun encodeImageUriToBase64(uri: Uri, maxDim: Int = 512): String = withContext(Dispatchers.IO) {
        try {
            val ctx = getApplication<Application>()
            val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext ""
            val original = BitmapFactory.decodeStream(input) ?: return@withContext ""
            input.close()
            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val w = (original.width * scale).toInt().coerceAtLeast(1)
            val h = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun encodeFileUriToBase64(uri: Uri, maxBytes: Int = 650_000): Triple<String, String, Long>? =
        withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
                if (bytes.size > maxBytes) return@withContext null
                Triple(Base64.encodeToString(bytes, Base64.NO_WRAP), mime, bytes.size.toLong())
            } catch (_: Exception) {
                null
            }
        }

    fun startVoiceRecording() {
        if (_isRecordingVoice.value) return
        val ctx = getApplication<Application>()
        val file = File(ctx.cacheDir, "ak_voice_${System.currentTimeMillis()}.m4a")
        recordingFile = file
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(22050)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            _isRecordingVoice.value = true
            _recordingSeconds.value = 0
            recordingTimerJob?.cancel()
            recordingTimerJob = viewModelScope.launch {
                while (_isRecordingVoice.value) {
                    delay(1000)
                    _recordingSeconds.value += 1
                }
            }
        } catch (e: Exception) {
            _infoBanner.value = "Mic recording failed: ${e.message}"
        }
    }

    fun stopAndSendVoice(chat: ChatThread, sender: UserProfile, peerPubKey: String) {
        if (!_isRecordingVoice.value) return
        val duration = _recordingSeconds.value.coerceAtLeast(1)
        recordingTimerJob?.cancel()
        _isRecordingVoice.value = false
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }

        val file = recordingFile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists() && file.length() > 0) {
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                repo.sendMessage(
                    chat = chat,
                    sender = sender,
                    peerPublicKey = peerPubKey,
                    type = "voice",
                    plaintext = "Voice note (${duration}s)",
                    mediaBase64 = b64,
                    mediaMimeType = "audio/mp4",
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    voiceDurationSec = duration
                )
            }
            file.delete()
        }
    }

    fun cancelVoiceRecording() {
        recordingTimerJob?.cancel()
        _isRecordingVoice.value = false
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
        recordingFile?.delete()
    }

    fun togglePlayVoiceMessage(message: ChatMessage) {
        if (_playingMessageId.value == message.id) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            _playingMessageId.value = null
            return
        }
        if (message.mediaBase64.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(message.mediaBase64, Base64.NO_WRAP)
                val temp = File(getApplication<Application>().cacheDir, "play_${message.id}.m4a")
                temp.writeBytes(bytes)
                withContext(Dispatchers.Main) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(temp.absolutePath)
                        prepare()
                        start()
                        _playingMessageId.value = message.id
                        setOnCompletionListener {
                            _playingMessageId.value = null
                            release()
                            mediaPlayer = null
                        }
                    }
                }
            } catch (e: Exception) {
                _infoBanner.value = "Playback error: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}
