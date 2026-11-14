package com.example.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aistudio.akmessenger.shared.firebase.FirebaseConfig
import com.aistudio.akmessenger.shared.model.ChatMessage
import com.aistudio.akmessenger.shared.model.ChatThread
import com.aistudio.akmessenger.shared.model.UserProfile
import com.aistudio.akmessenger.shared.repository.AkSyncEngine
import com.aistudio.akmessenger.shared.repository.AkUserRepository
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.AkMessengerTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.roundToInt

class ChatHeadOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var syncEngine: AkSyncEngine
    private lateinit var repo: AkUserRepository

    private val activeBubbleChatIds = MutableStateFlow<List<String>>(emptyList())
    private val expandedChatId = MutableStateFlow<String?>(null)
    private val isRecordingVoice = MutableStateFlow(false)
    private val recordingSec = MutableStateFlow(0)

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingJob: Job? = null
    private val seenLatestMessageTimestamp = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val config = FirebaseConfig(
            apiKey = BuildConfig.FIREBASE_API_KEY,
            authDomain = BuildConfig.FIREBASE_AUTH_DOMAIN,
            databaseUrl = BuildConfig.FIREBASE_DATABASE_URL,
            projectId = BuildConfig.FIREBASE_PROJECT_ID
        )
        syncEngine = AkSyncEngine(applicationContext, config, "ak_messenger_user.db")
        repo = AkUserRepository(syncEngine)

        startForegroundOverlay()

        serviceScope.launch {
            val session = syncEngine.prefs.current()
            if (session.idToken.isNotBlank()) {
                syncEngine.activeToken = session.idToken
                syncEngine.startRealtimeListener(serviceScope, includeAdminNodes = false) {
                    detectNewIncomingMessages()
                }
            }
        }

        if (Settings.canDrawOverlays(this)) {
            attachOverlayView()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        when (intent?.action) {
            ChatHeadManager.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ChatHeadManager.ACTION_POP_CHAT -> {
                val chatId = intent.getStringExtra(ChatHeadManager.EXTRA_CHAT_ID)
                if (!chatId.isNullOrBlank()) {
                    addBubble(chatId)
                    expandedChatId.value = chatId
                    updateWindowFocusability(expanded = true)
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundOverlay() {
        val channelId = "chat_heads_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Floating Chat Heads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val launchIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 101, launchIntent, PendingIntent.FLAG_IMMUTABLE)
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AK Messenger Chat Heads")
            .setContentText("Floating bubbles active over apps")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, 707, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(707, notif)
        }
    }

    private suspend fun detectNewIncomingMessages() {
        val myUid = syncEngine.prefs.current().uid
        if (myUid.isBlank()) return
        val chats = syncEngine.observeChats().first()
        chats.filter { it.participantIds[myUid] == true && it.lastMessageSenderId != myUid && it.lastMessageTimestamp > 0L }
            .forEach { chat ->
                val prev = seenLatestMessageTimestamp[chat.id]
                if (prev == null) {
                    seenLatestMessageTimestamp[chat.id] = chat.lastMessageTimestamp
                    if ((chat.unreadCounts[myUid] ?: 0) > 0) {
                        addBubble(chat.id)
                    }
                } else if (chat.lastMessageTimestamp > prev) {
                    seenLatestMessageTimestamp[chat.id] = chat.lastMessageTimestamp
                    addBubble(chat.id)
                }
            }
    }

    private fun addBubble(chatId: String) {
        val current = activeBubbleChatIds.value.toMutableList()
        if (!current.contains(chatId)) {
            current.add(0, chatId)
            activeBubbleChatIds.value = current.take(5)
        }
    }

    private fun removeBubble(chatId: String) {
        val next = activeBubbleChatIds.value.filterNot { it == chatId }
        activeBubbleChatIds.value = next
        if (expandedChatId.value == chatId) {
            expandedChatId.value = null
            updateWindowFocusability(expanded = false)
        }
    }

    private fun attachOverlayView() {
        if (overlayView != null) return
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }
        layoutParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ChatHeadOverlayService)
            setViewTreeViewModelStoreOwner(this@ChatHeadOverlayService)
            setViewTreeSavedStateRegistryOwner(this@ChatHeadOverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                val session by syncEngine.prefs.sessionFlow.collectAsState(initial = com.aistudio.akmessenger.shared.local.StoredSession())
                val users by syncEngine.observeUsers().collectAsState(initial = emptyList())
                val chats by syncEngine.observeChats().collectAsState(initial = emptyList())
                val bubbleIds by activeBubbleChatIds.collectAsState()
                val activeId by expandedChatId.collectAsState()
                val recording by isRecordingVoice.collectAsState()
                val recSec by recordingSec.collectAsState()

                val myUid = session.uid
                val me = users.firstOrNull { it.uid == myUid } ?: UserProfile(uid = myUid, fullName = session.email)

                val bubbles = remember(bubbleIds, chats, users, myUid) {
                    bubbleIds.mapNotNull { id ->
                        val chat = chats.firstOrNull { it.id == id } ?: return@mapNotNull null
                        val peerUid = if (!chat.isGroup) chat.participantIds.keys.firstOrNull { it != myUid } else null
                        val peer = users.firstOrNull { it.uid == peerUid }
                        val name = if (chat.isGroup) chat.title else peer?.fullName ?: chat.participantNames[peerUid] ?: chat.title
                        ChatHeadBubbleState(
                            chat = chat,
                            peer = peer,
                            displayName = name,
                            unreadCount = chat.unreadCounts[myUid] ?: 0
                        )
                    }
                }

                val activeHead = bubbles.firstOrNull { it.chat.id == activeId }

                AkMessengerTheme(darkTheme = true) {
                    if (activeHead != null) {
                        val msgs by syncEngine.observeMessages(activeHead.chat.id).collectAsState(initial = emptyList())
                        val peerPubKey = activeHead.peer?.publicKey.orEmpty()

                        LaunchedEffect(activeHead.chat.id, msgs.size) {
                            repo.markChatSeen(activeHead.chat, myUid, msgs)
                        }

                        FloatingChatWindowCard(
                            bubbles = bubbles,
                            activeHead = activeHead,
                            messages = msgs,
                            myUid = myUid,
                            decryptMessage = { msg -> repo.decryptMessageText(msg, session.e2eePrivateKey, peerPubKey) },
                            isRecordingVoice = recording,
                            recordingSec = recSec,
                            onSelectBubble = { cid ->
                                expandedChatId.value = cid
                                updateWindowFocusability(true)
                            },
                            onCloseBubble = { cid -> removeBubble(cid) },
                            onMinimize = {
                                expandedChatId.value = null
                                updateWindowFocusability(false)
                            },
                            onSendText = { text, replyTo ->
                                serviceScope.launch {
                                    repo.sendMessage(activeHead.chat, me, peerPubKey, "text", text, replyTo = replyTo)
                                }
                            },
                            onSendSticker = { sticker ->
                                serviceScope.launch {
                                    repo.sendMessage(activeHead.chat, me, peerPubKey, "sticker", sticker)
                                }
                            },
                            onReactMessage = { msg, emoji ->
                                serviceScope.launch {
                                    repo.updateMessage(activeHead.chat.id, msg.copy(reactions = msg.reactions + (myUid to emoji)))
                                }
                            },
                            onToggleVoiceRecord = {
                                toggleVoiceInOverlay(activeHead.chat, me, peerPubKey)
                            },
                            onOpenFullAppChat = { cid ->
                                expandedChatId.value = null
                                updateWindowFocusability(false)
                                launchMainActivity(ChatHeadManager.EXTRA_OPEN_CHAT_ID, cid)
                            },
                            onStartAudioCall = {
                                serviceScope.launch {
                                    val targets = activeHead.peer?.let { listOf(it) } ?: emptyList()
                                    repo.startCall(me, targets, "audio", activeHead.chat.isGroup, activeHead.chat.groupId)
                                        .onSuccess { call ->
                                            expandedChatId.value = null
                                            updateWindowFocusability(false)
                                            launchMainActivity(ChatHeadManager.EXTRA_OPEN_CALL_ID, call.id)
                                        }
                                }
                            },
                            onStartVideoCall = {
                                serviceScope.launch {
                                    val targets = activeHead.peer?.let { listOf(it) } ?: emptyList()
                                    repo.startCall(me, targets, "video", activeHead.chat.isGroup, activeHead.chat.groupId)
                                        .onSuccess { call ->
                                            expandedChatId.value = null
                                            updateWindowFocusability(false)
                                            launchMainActivity(ChatHeadManager.EXTRA_OPEN_CALL_ID, call.id)
                                        }
                                }
                            },
                            onOpenProfile = { uid ->
                                expandedChatId.value = null
                                updateWindowFocusability(false)
                                launchMainActivity(ChatHeadManager.EXTRA_OPEN_PROFILE_UID, uid)
                            },
                            onDragWindow = { dx, dy ->
                                dragOverlayBy(dx, dy)
                            }
                        )
                    } else if (bubbles.isNotEmpty()) {
                        FloatingChatHeadStrip(
                            bubbles = bubbles,
                            activeChatId = activeId,
                            onSelectBubble = { cid ->
                                expandedChatId.value = cid
                                updateWindowFocusability(true)
                            },
                            onCloseBubble = { cid -> removeBubble(cid) },
                            onDragDelta = { dx, dy -> dragOverlayBy(dx, dy) },
                            onDragEnd = { snapOverlayToEdge() }
                        )
                    }
                }
            }
        }

        overlayView = view
        windowManager.addView(view, params)
    }

    private fun updateWindowFocusability(expanded: Boolean) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        if (expanded) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            params.x = 0
            params.y = 120
        } else {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.x = 24
            params.y = 260
        }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { }
    }

    private fun dragOverlayBy(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { }
    }

    private fun snapOverlayToEdge() {
        val params = layoutParams ?: return
        val view = overlayView ?: return
        val width = resources.displayMetrics.widthPixels
        params.x = if (params.x + 90 < width / 2) 16 else (width - 196).coerceAtLeast(16)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) { }
    }

    private fun toggleVoiceInOverlay(chat: ChatThread, sender: UserProfile, peerPubKey: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            launchMainActivity(ChatHeadManager.EXTRA_OPEN_CHAT_ID, chat.id)
            return
        }
        if (!isRecordingVoice.value) {
            val file = File(cacheDir, "overlay_voice_${System.currentTimeMillis()}.m4a")
            recordingFile = file
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setOutputFile(file.absolutePath)
                rec.prepare()
                rec.start()
                mediaRecorder = rec
                isRecordingVoice.value = true
                recordingSec.value = 0
                recordingJob?.cancel()
                recordingJob = serviceScope.launch {
                    while (isRecordingVoice.value) {
                        delay(1000)
                        recordingSec.value += 1
                    }
                }
            } catch (_: Exception) { }
        } else {
            val dur = recordingSec.value.coerceAtLeast(1)
            recordingJob?.cancel()
            isRecordingVoice.value = false
            try { mediaRecorder?.stop() } catch (_: Exception) { }
            mediaRecorder?.release()
            mediaRecorder = null
            val file = recordingFile ?: return
            serviceScope.launch(Dispatchers.IO) {
                if (file.exists() && file.length() > 0) {
                    val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    repo.sendMessage(
                        chat = chat,
                        sender = sender,
                        peerPublicKey = peerPubKey,
                        type = "voice",
                        plaintext = "Voice note (${dur}s)",
                        mediaBase64 = b64,
                        mediaMimeType = "audio/mp4",
                        fileName = file.name,
                        fileSizeBytes = file.length(),
                        voiceDurationSec = dur
                    )
                }
                file.delete()
            }
        }
    }

    private fun launchMainActivity(extraKey: String, extraVal: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(extraKey, extraVal)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        mediaRecorder?.release()
        syncEngine.stopRealtimeListener()
        serviceScope.cancel()
        store.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
