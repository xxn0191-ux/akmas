package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.akmessenger.shared.crypto.E2eeCryptoManager
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.ui.*
import com.example.overlay.ChatHeadManager
import com.example.overlay.MessengerPinkEnd
import com.example.overlay.MessengerPinkStart
import com.example.ui.theme.*
import com.example.ui.viewmodel.MessengerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenCall: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenGroupDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val typingMap by viewModel.typingMap.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val recordingSec by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val playingId by viewModel.playingMessageId.collectAsStateWithLifecycle()

    val messages by viewModel.messagesForChat(chatId).collectAsStateWithLifecycle(initialValue = emptyList())
    val chat = chats.firstOrNull { it.id == chatId }
    val myUid = session.uid
    val me = users.firstOrNull { it.uid == myUid } ?: UserProfile(uid = myUid, fullName = session.email)

    val peerUid = if (chat != null && !chat.isGroup) chat.participantIds.keys.firstOrNull { it != myUid } else null
    val peer = users.firstOrNull { it.uid == peerUid }
    val peerPubKey = peer?.publicKey.orEmpty()
    val chatTitle = if (chat?.isGroup == true) chat.title else peer?.fullName ?: chat?.title ?: "Chat"

    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showEmojiTray by remember { mutableStateOf(false) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMsgForMenu by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardingMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }

    val visibleMessages = remember(messages, searchQuery, myUid) {
        messages.filter { msg ->
            msg.deletedFor[myUid] != true
        }.filter { msg ->
            if (searchQuery.isBlank()) true
            else {
                val decoded = viewModel.repo.decryptMessageText(msg, session.e2eePrivateKey, peerPubKey)
                decoded.contains(searchQuery, ignoreCase = true) || msg.fileName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val pinnedMsg = remember(visibleMessages) { visibleMessages.lastOrNull { it.isPinned && !it.deletedForEveryone } }

    LaunchedEffect(chat?.id, messages.size) {
        if (chat != null) {
            viewModel.repo.markChatSeen(chat, myUid, messages)
        }
        if (visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    LaunchedEffect(inputText) {
        if (myUid.isNotBlank()) {
            viewModel.repo.setTyping(chatId, myUid, inputText.isNotBlank())
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceRecording()
    }

    val visualMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && chat != null) {
            scope.launch {
                val res = viewModel.encodeFileUriToBase64(uri)
                if (res != null) {
                    val isVideo = res.second.startsWith("video")
                    viewModel.repo.sendMessage(
                        chat = chat,
                        sender = me,
                        peerPublicKey = peerPubKey,
                        type = if (isVideo) "video" else "image",
                        plaintext = if (isVideo) "Video clip" else "Photo",
                        mediaBase64 = res.first,
                        mediaMimeType = res.second,
                        fileSizeBytes = res.third
                    )
                } else {
                    val imgB64 = viewModel.encodeImageUriToBase64(uri, 720)
                    if (imgB64.isNotBlank()) {
                        viewModel.repo.sendMessage(
                            chat = chat,
                            sender = me,
                            peerPublicKey = peerPubKey,
                            type = "image",
                            plaintext = "Photo",
                            mediaBase64 = imgB64,
                            mediaMimeType = "image/jpeg"
                        )
                    }
                }
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && chat != null) {
            scope.launch {
                var fileName = "document"
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) fileName = c.getString(idx)
                }
                val fileRes = viewModel.encodeFileUriToBase64(uri)
                if (fileRes != null) {
                    viewModel.repo.sendMessage(
                        chat = chat,
                        sender = me,
                        peerPublicKey = peerPubKey,
                        type = "document",
                        plaintext = fileName,
                        mediaBase64 = fileRes.first,
                        mediaMimeType = fileRes.second,
                        fileName = fileName,
                        fileSizeBytes = fileRes.third
                    )
                }
            }
        }
    }

    val isBlockedByMe = chat?.blockedBy?.get(myUid) == true
    val isPeerTyping = typingMap[chatId]?.entries?.any { it.key != myUid && it.value } == true

    val isDark = session.darkTheme
    val isPinkTheme = session.pinkMessengerTheme
    val accentColor = if (isPinkTheme) MessengerPinkStart else EmeraldPrimary
    val doodleColor = when {
        isPinkTheme && isDark -> Color(0xFF231628)
        isPinkTheme -> Color(0xFFF6D9E7)
        isDark -> Color(0xFF15222A)
        else -> Color(0xFFE3ECE8)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (chat?.isGroup == true) onOpenGroupDetail(chat.groupId.ifBlank { chat.id })
                                else if (peer != null) onOpenUserProfile(peer.uid)
                            }
                        ) {
                            UserAvatar(
                                name = chatTitle,
                                avatarBase64 = peer?.avatarBase64.orEmpty(),
                                size = 38.dp,
                                isOnline = peer?.isOnline == true,
                                accentColor = accentColor
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                NameWithBadge(chatTitle, peer?.isVerified == true)
                                Text(
                                    text = when {
                                        isPeerTyping -> "typing..."
                                        chat?.isGroup == true -> "${chat.participantIds.size} participants"
                                        peer != null -> AkTimeFormatter.formatLastSeen(peer.isOnline, peer.lastSeen)
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPeerTyping) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (ChatHeadManager.canDrawOverlays(context)) {
                                    viewModel.setChatHeadsEnabled(true)
                                    ChatHeadManager.startService(context, popChatId = chatId)
                                } else {
                                    context.startActivity(ChatHeadManager.requestOverlayPermissionIntent(context))
                                }
                            },
                            modifier = Modifier.testTag("pop_chat_head_button")
                        ) {
                            Icon(Icons.Filled.BubbleChart, "Open as Floating Chat Head", tint = accentColor)
                        }
                        IconButton(onClick = {
                            if (chat != null) {
                                scope.launch {
                                    val callees = users.filter { it.uid != myUid && chat.participantIds[it.uid] == true }
                                    viewModel.repo.startCall(me, callees, "audio", chat.isGroup, chat.groupId)
                                        .onSuccess { onOpenCall(it.id) }
                                }
                            }
                        }) { Icon(Icons.Filled.Call, "Audio Call", tint = accentColor) }
                        IconButton(onClick = {
                            if (chat != null) {
                                scope.launch {
                                    val callees = users.filter { it.uid != myUid && chat.participantIds[it.uid] == true }
                                    viewModel.repo.startCall(me, callees, "video", chat.isGroup, chat.groupId)
                                        .onSuccess { onOpenCall(it.id) }
                                }
                            }
                        }) { Icon(Icons.Filled.Videocam, "Video Call", tint = accentColor) }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Search Messages") }, onClick = { showMenu = false; showSearch = !showSearch })
                            DropdownMenuItem(text = { Text("E2EE Safety Number") }, onClick = { showMenu = false; showSafetyDialog = true })
                            DropdownMenuItem(text = { Text("Disappearing Messages") }, onClick = { showMenu = false; showDisappearingDialog = true })
                            if (chat != null) {
                                val muted = chat.mutedBy[myUid] == true
                                val pinned = chat.pinnedBy[myUid] == true
                                val archived = chat.archivedBy[myUid] == true
                                DropdownMenuItem(text = { Text(if (muted) "Unmute Chat" else "Mute Chat") }, onClick = {
                                    showMenu = false
                                    scope.launch { viewModel.repo.updateChatThread(chat.copy(mutedBy = chat.mutedBy + (myUid to !muted))) }
                                })
                                DropdownMenuItem(text = { Text(if (pinned) "Unpin Chat" else "Pin Chat") }, onClick = {
                                    showMenu = false
                                    scope.launch { viewModel.repo.updateChatThread(chat.copy(pinnedBy = chat.pinnedBy + (myUid to !pinned))) }
                                })
                                DropdownMenuItem(text = { Text(if (archived) "Unarchive Chat" else "Archive Chat") }, onClick = {
                                    showMenu = false
                                    scope.launch { viewModel.repo.updateChatThread(chat.copy(archivedBy = chat.archivedBy + (myUid to !archived))) }
                                })
                                DropdownMenuItem(text = { Text(if (isBlockedByMe) "Unblock User" else "Block User") }, onClick = {
                                    showMenu = false
                                    scope.launch { viewModel.repo.updateChatThread(chat.copy(blockedBy = chat.blockedBy + (myUid to !isBlockedByMe))) }
                                })
                                if (peer != null) {
                                    DropdownMenuItem(text = { Text("Report User") }, onClick = {
                                        showMenu = false
                                        scope.launch { viewModel.repo.submitReport(me, "user", peer.uid, peer.fullName, "Reported from chat", "") }
                                    })
                                }
                            }
                        }
                    }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search in conversation...") },
                        singleLine = true,
                        trailingIcon = { IconButton(onClick = { searchQuery = ""; showSearch = false }) { Icon(Icons.Filled.Close, null) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                if (pinnedMsg != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PushPin, null, tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = viewModel.repo.decryptMessageText(pinnedMsg, session.e2eePrivateKey, peerPubKey),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    val step = 48.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        var x = 0f
                        while (x < size.width) {
                            drawCircle(doodleColor, radius = 3f, center = Offset(x + 12f, y + 12f))
                            drawLine(doodleColor, Offset(x + 28f, y + 20f), Offset(x + 38f, y + 28f), strokeWidth = 1.5f)
                            x += step
                        }
                        y += step
                    }
                }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleMessages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == myUid
                    val senderProfile = users.firstOrNull { it.uid == msg.senderId } ?: peer
                    val decodedText = viewModel.repo.decryptMessageText(msg, session.e2eePrivateKey, peerPubKey)
                    MessageBubble(
                        msg = msg,
                        decodedText = decodedText,
                        isMine = isMine,
                        isDark = isDark,
                        isPinkTheme = isPinkTheme,
                        senderName = msg.senderName.ifBlank { chatTitle },
                        senderAvatarBase64 = senderProfile?.avatarBase64.orEmpty(),
                        isPlayingVoice = playingId == msg.id,
                        onToggleVoice = { viewModel.togglePlayVoiceMessage(msg) },
                        onQuickHeartReact = {
                            scope.launch {
                                viewModel.repo.updateMessage(chatId, msg.copy(reactions = msg.reactions + (myUid to "❤️")))
                            }
                        },
                        onLongPress = { selectedMsgForMenu = msg }
                    )
                }
            }

            if (isBlockedByMe) {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "You blocked this conversation. Unblock from menu to send messages.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                // Reply or Edit preview strip
                val bannerMsg = editingMsg ?: replyingTo
                if (bannerMsg != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (editingMsg != null) Icons.Filled.Edit else Icons.Filled.Reply, null, tint = EmeraldPrimary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = viewModel.repo.decryptMessageText(bannerMsg, session.e2eePrivateKey, peerPubKey),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { replyingTo = null; editingMsg = null }) {
                                Icon(Icons.Filled.Close, "Cancel")
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showEmojiTray) {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(listOf("😀", "😂", "❤️", "👍", "🔥", "🎉", "😢", "🙏", "😍", "🤝", "🇧🇩", "✨")) { emoji ->
                                    Text(emoji, fontSize = 24.sp, modifier = Modifier.clickable { inputText += emoji })
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf("Sticker: Wave 👋", "Sticker: Heart 💖", "GIF: Celebrate 🥳", "GIF: Thumbs Up 👍")) { pack ->
                                    AssistChip(
                                        onClick = {
                                            if (chat != null) {
                                                val isGif = pack.startsWith("GIF:")
                                                scope.launch {
                                                    viewModel.repo.sendMessage(
                                                        chat, me, peerPubKey,
                                                        type = if (isGif) "gif" else "sticker",
                                                        plaintext = pack
                                                    )
                                                }
                                            }
                                            showEmojiTray = false
                                        },
                                        label = { Text(pack) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Composer Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRecording) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Mic, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text("Recording ${AkTimeFormatter.formatDuration(recordingSec.toLong())}", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { viewModel.cancelVoiceRecording() }) { Text("Cancel") }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        FloatingActionButton(
                            onClick = { if (chat != null) viewModel.stopAndSendVoice(chat, me, peerPubKey) },
                            containerColor = accentColor,
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Filled.Send, "Send Voice", tint = Color.White) }
                    } else {
                        IconButton(
                            onClick = { showAttachSheet = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.CameraAlt, "Camera & Attachments", tint = accentColor)
                        }
                        IconButton(
                            onClick = {
                                visualMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.Image, "Gallery", tint = accentColor)
                        }
                        IconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (granted) viewModel.startVoiceRecording()
                                else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.size(40.dp).testTag("chat_mic_button")
                        ) {
                            Icon(Icons.Filled.Mic, "Record Voice", tint = accentColor)
                        }
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                                TextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = { Text("Aa (বাংলা / English)...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    maxLines = 4,
                                    modifier = Modifier.weight(1f).testTag("chat_message_input")
                                )
                                IconButton(onClick = { showEmojiTray = !showEmojiTray }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.EmojiEmotions, "Emoji & Stickers", tint = accentColor)
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        FloatingActionButton(
                            onClick = {
                                if (chat != null) {
                                    if (inputText.isNotBlank()) {
                                        val text = inputText.trim()
                                        inputText = ""
                                        val editing = editingMsg
                                        editingMsg = null
                                        val reply = replyingTo
                                        replyingTo = null
                                        scope.launch {
                                            if (editing != null) {
                                                val updated = editing.copy(
                                                    plaintextFallback = text,
                                                    isEncrypted = false,
                                                    isEdited = true,
                                                    editedAt = System.currentTimeMillis()
                                                )
                                                viewModel.repo.updateMessage(chat.id, updated)
                                            } else {
                                                viewModel.repo.sendMessage(
                                                    chat = chat,
                                                    sender = me,
                                                    peerPublicKey = peerPubKey,
                                                    type = "text",
                                                    plaintext = text,
                                                    replyTo = reply
                                                )
                                            }
                                        }
                                    } else if (isPinkTheme) {
                                        scope.launch {
                                            viewModel.repo.sendMessage(
                                                chat = chat,
                                                sender = me,
                                                peerPublicKey = peerPubKey,
                                                type = "text",
                                                plaintext = "❤️"
                                            )
                                        }
                                    } else {
                                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (granted) viewModel.startVoiceRecording()
                                        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            containerColor = accentColor,
                            modifier = Modifier.size(46.dp).testTag("chat_send_or_mic_button")
                        ) {
                            Icon(
                                imageVector = when {
                                    inputText.isNotBlank() -> Icons.Filled.Send
                                    isPinkTheme -> Icons.Filled.Favorite
                                    else -> Icons.Filled.Mic
                                },
                                contentDescription = if (inputText.isNotBlank()) "Send" else if (isPinkTheme) "Send Heart" else "Record Voice",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAttachSheet) {
        AlertDialog(
            onDismissRequest = { showAttachSheet = false },
            title = { Text("Share Media or Files") },
            text = {
                Column {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.PhotoLibrary, null, tint = EmeraldPrimary) },
                        headlineContent = { Text("Photo or Video (Photo Picker)") },
                        modifier = Modifier.clickable {
                            showAttachSheet = false
                            visualMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.InsertDriveFile, null, tint = VerifiedBlue) },
                        headlineContent = { Text("Document / File") },
                        modifier = Modifier.clickable {
                            showAttachSheet = false
                            docPicker.launch(arrayOf("*/*"))
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAttachSheet = false }) { Text("Close") } }
        )
    }

    selectedMsgForMenu?.let { msg ->
        val decoded = viewModel.repo.decryptMessageText(msg, session.e2eePrivateKey, peerPubKey)
        AlertDialog(
            onDismissRequest = { selectedMsgForMenu = null },
            title = { Text("Message Actions") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                            Text(emoji, fontSize = 24.sp, modifier = Modifier.clickable {
                                scope.launch {
                                    viewModel.repo.updateMessage(chatId, msg.copy(reactions = msg.reactions + (myUid to emoji)))
                                }
                                selectedMsgForMenu = null
                            })
                        }
                    }
                    ListItem(headlineContent = { Text("Reply") }, modifier = Modifier.clickable { replyingTo = msg; selectedMsgForMenu = null })
                    ListItem(headlineContent = { Text("Forward") }, modifier = Modifier.clickable { forwardingMsg = msg; selectedMsgForMenu = null })
                    ListItem(headlineContent = { Text("Copy") }, modifier = Modifier.clickable {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AK Message", decoded))
                        selectedMsgForMenu = null
                    })
                    ListItem(headlineContent = { Text(if (msg.isPinned) "Unpin" else "Pin Message") }, modifier = Modifier.clickable {
                        scope.launch { viewModel.repo.updateMessage(chatId, msg.copy(isPinned = !msg.isPinned)) }
                        selectedMsgForMenu = null
                    })
                    val isStarred = msg.starredBy[myUid] == true
                    ListItem(headlineContent = { Text(if (isStarred) "Unstar" else "Star Message") }, modifier = Modifier.clickable {
                        scope.launch { viewModel.repo.updateMessage(chatId, msg.copy(starredBy = msg.starredBy + (myUid to !isStarred))) }
                        selectedMsgForMenu = null
                    })
                    if (msg.senderId == myUid && !msg.deletedForEveryone) {
                        ListItem(headlineContent = { Text("Edit Message") }, modifier = Modifier.clickable {
                            editingMsg = msg
                            inputText = decoded
                            selectedMsgForMenu = null
                        })
                        ListItem(headlineContent = { Text("Delete for Everyone", color = MaterialTheme.colorScheme.error) }, modifier = Modifier.clickable {
                            scope.launch { viewModel.repo.updateMessage(chatId, msg.copy(deletedForEveryone = true, plaintextFallback = "", ciphertext = "")) }
                            selectedMsgForMenu = null
                        })
                    }
                    ListItem(headlineContent = { Text("Delete for Me") }, modifier = Modifier.clickable {
                        scope.launch { viewModel.repo.updateMessage(chatId, msg.copy(deletedFor = msg.deletedFor + (myUid to true))) }
                        selectedMsgForMenu = null
                    })
                    ListItem(headlineContent = { Text("Report Message") }, modifier = Modifier.clickable {
                        scope.launch { viewModel.repo.submitReport(me, "message", msg.id, msg.senderName, "Reported message", decoded.take(100)) }
                        selectedMsgForMenu = null
                    })
                }
            },
            confirmButton = { TextButton(onClick = { selectedMsgForMenu = null }) { Text("Close") } }
        )
    }

    forwardingMsg?.let { fMsg ->
        val decoded = viewModel.repo.decryptMessageText(fMsg, session.e2eePrivateKey, peerPubKey)
        AlertDialog(
            onDismissRequest = { forwardingMsg = null },
            title = { Text("Forward To...") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(chats.filter { it.participantIds[myUid] == true }, key = { it.id }) { targetChat ->
                        val targetPeerUid = if (!targetChat.isGroup) targetChat.participantIds.keys.firstOrNull { it != myUid } else null
                        val targetPeerPub = users.firstOrNull { it.uid == targetPeerUid }?.publicKey.orEmpty()
                        ListItem(
                            headlineContent = { Text(targetChat.title) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    viewModel.repo.sendMessage(
                                        chat = targetChat,
                                        sender = me,
                                        peerPublicKey = targetPeerPub,
                                        type = fMsg.type,
                                        plaintext = decoded,
                                        mediaBase64 = fMsg.mediaBase64,
                                        mediaMimeType = fMsg.mediaMimeType,
                                        fileName = fMsg.fileName,
                                        forwardedFrom = fMsg.senderName
                                    )
                                }
                                forwardingMsg = null
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { forwardingMsg = null }) { Text("Cancel") } }
        )
    }

    if (showSafetyDialog) {
        val safety = E2eeCryptoManager.computeSafetyNumber(session.e2eePublicKey, peerPubKey)
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("End-to-End Encryption") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your 1:1 text messages are encrypted with ECDH (secp256r1) + AES-256-GCM.")
                    Text("Safety Number:", fontWeight = FontWeight.Bold)
                    Text(safety, fontFamily = AkFonts.JetBrainsMono, fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("OK") } }
        )
    }

    if (showDisappearingDialog && chat != null) {
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            title = { Text("Disappearing Messages") },
            text = {
                Column {
                    listOf(0L to "Off", 86400L to "24 Hours", 604800L to "7 Days").forEach { (sec, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            trailingContent = { if (chat.disappearingSeconds == sec) Icon(Icons.Filled.Check, null, tint = EmeraldPrimary) },
                            modifier = Modifier.clickable {
                                scope.launch { viewModel.repo.updateChatThread(chat.copy(disappearingSeconds = sec)) }
                                showDisappearingDialog = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDisappearingDialog = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    decodedText: String,
    isMine: Boolean,
    isDark: Boolean,
    isPinkTheme: Boolean,
    senderName: String,
    senderAvatarBase64: String,
    isPlayingVoice: Boolean,
    onToggleVoice: () -> Unit,
    onQuickHeartReact: () -> Unit,
    onLongPress: () -> Unit
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )
    val bubbleColor = when {
        isPinkTheme && isMine -> Color.Transparent
        isPinkTheme && !isMine -> if (isDark) Color(0xFF27212E) else Color(0xFFF3EBF4)
        isMine && isDark -> DarkOutgoingBubble
        isMine && !isDark -> LightOutgoingBubble
        !isMine && isDark -> DarkIncomingBubble
        else -> LightIncomingBubble
    }
    val textColor = when {
        isPinkTheme && isMine -> Color.White
        isDark -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val metaColor = if (isPinkTheme && isMine) Color.White.copy(alpha = 0.80f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMine && isPinkTheme) {
            UserAvatar(
                name = senderName,
                avatarBase64 = senderAvatarBase64,
                size = 26.dp,
                accentColor = MessengerPinkStart
            )
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                tonalElevation = if (isPinkTheme && isMine) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .then(
                        if (isPinkTheme && isMine) {
                            Modifier.background(
                                Brush.linearGradient(listOf(MessengerPinkStart, MessengerPinkEnd)),
                                bubbleShape
                            )
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            if (msg.type == "voice") onToggleVoice()
                        },
                        onDoubleClick = onQuickHeartReact,
                        onLongClick = onLongPress
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (msg.forwardedFrom.isNotBlank()) {
                        Text("Forwarded", fontStyle = FontStyle.Italic, fontSize = 11.sp, color = metaColor)
                    }
                    if (msg.replyToPreview.isNotBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Text(msg.replyToPreview, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = textColor, modifier = Modifier.padding(6.dp))
                        }
                    }
                    if (!msg.deletedForEveryone) {
                        when (msg.type) {
                            "image" -> {
                                val bmp = remember(msg.mediaBase64) {
                                    try {
                                        val bytes = Base64.decode(msg.mediaBase64, Base64.NO_WRAP)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                    } catch (_: Exception) { null }
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "Shared photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Text(decodedText, color = textColor)
                            }
                            "voice" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isPlayingVoice) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                        contentDescription = "Play voice message",
                                        tint = if (isPinkTheme && isMine) Color.White else if (isPinkTheme) MessengerPinkStart else EmeraldPrimary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Voice (${msg.voiceDurationSec}s)", fontWeight = FontWeight.Medium, color = textColor)
                                }
                            }
                            "document", "video" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (msg.type == "video") Icons.Filled.VideoFile else Icons.Filled.Description,
                                        null,
                                        tint = if (isPinkTheme && isMine) Color.White else EmeraldPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(msg.fileName.ifBlank { decodedText }, fontWeight = FontWeight.Medium, color = textColor)
                                }
                            }
                            else -> {
                                Text(decodedText, fontSize = 15.sp, color = textColor)
                            }
                        }
                    } else {
                        Text(
                            text = "This message was deleted",
                            fontStyle = FontStyle.Italic,
                            color = metaColor
                        )
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (msg.isEncrypted) {
                            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(10.dp), tint = metaColor)
                            Spacer(Modifier.width(3.dp))
                        }
                        if (msg.isEdited) {
                            Text("edited • ", fontSize = 10.sp, color = metaColor)
                        }
                        Text(AkTimeFormatter.formatClock(msg.timestamp), fontSize = 10.sp, color = metaColor)
                        if (isMine) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = if (msg.status == "sent") Icons.Filled.Done else Icons.Filled.DoneAll,
                                contentDescription = msg.status,
                                tint = if (msg.status == "seen") Color(0xFF8BE9FD) else metaColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            if (msg.reactions.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(msg.reactions.values.joinToString(" "), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}
