package com.example.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.akmessenger.shared.model.ChatMessage
import com.aistudio.akmessenger.shared.model.ChatThread
import com.aistudio.akmessenger.shared.model.UserProfile
import com.aistudio.akmessenger.shared.ui.AkFonts
import com.aistudio.akmessenger.shared.ui.AkTimeFormatter
import com.aistudio.akmessenger.shared.ui.NameWithBadge
import com.aistudio.akmessenger.shared.ui.UserAvatar

val MessengerPinkStart = Color(0xFFFF4B91)
val MessengerPinkEnd = Color(0xFFD63384)
val MessengerPlum = Color(0xFF9C27B0)
val MessengerBgDark = Color(0xFF141118)
val MessengerIncomingDark = Color(0xFF27212E)

data class ChatHeadBubbleState(
    val chat: ChatThread,
    val peer: UserProfile?,
    val displayName: String,
    val unreadCount: Int
)

@Composable
fun FloatingChatHeadStrip(
    bubbles: List<ChatHeadBubbleState>,
    activeChatId: String?,
    onSelectBubble: (String) -> Unit,
    onCloseBubble: (String) -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
    ) {
        bubbles.forEach { head ->
            val isSelected = head.chat.id == activeChatId
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clickable { onSelectBubble(head.chat.id) }
                    .testTag("floating_bubble_${head.chat.id}")
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .align(Alignment.Center)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 2.dp,
                            brush = Brush.linearGradient(listOf(MessengerPinkStart, MessengerPlum)),
                            shape = CircleShape
                        )
                        .background(MessengerBgDark),
                    contentAlignment = Alignment.Center
                ) {
                    UserAvatar(
                        name = head.displayName,
                        avatarBase64 = head.peer?.avatarBase64.orEmpty(),
                        size = 54.dp,
                        isOnline = head.peer?.isOnline == true,
                        accentColor = MessengerPinkStart
                    )
                }

                // Unread count badge
                if (head.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF2E63))
                            .border(1.5.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (head.unreadCount > 9) "9+" else head.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Remove bubble button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1F1B24))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                        .clickable { onCloseBubble(head.chat.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close bubble", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
fun FloatingChatWindowCard(
    bubbles: List<ChatHeadBubbleState>,
    activeHead: ChatHeadBubbleState,
    messages: List<ChatMessage>,
    myUid: String,
    decryptMessage: (ChatMessage) -> String,
    isRecordingVoice: Boolean,
    recordingSec: Int,
    onSelectBubble: (String) -> Unit,
    onCloseBubble: (String) -> Unit,
    onMinimize: () -> Unit,
    onSendText: (String, ChatMessage?) -> Unit,
    onSendSticker: (String) -> Unit,
    onReactMessage: (ChatMessage, String) -> Unit,
    onToggleVoiceRecord: () -> Unit,
    onOpenFullAppChat: (String) -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onDragWindow: (Float, Float) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showEmojiTray by remember { mutableStateOf(false) }
    var reactingMsg by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Horizontal active bubbles bar above window
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragWindow(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            items(bubbles, key = { it.chat.id }) { head ->
                val selected = head.chat.id == activeHead.chat.id
                Box(modifier = Modifier.clickable { onSelectBubble(head.chat.id) }) {
                    UserAvatar(
                        name = head.displayName,
                        avatarBase64 = head.peer?.avatarBase64.orEmpty(),
                        size = if (selected) 52.dp else 44.dp,
                        isOnline = head.peer?.isOnline == true,
                        accentColor = MessengerPinkStart,
                        modifier = Modifier
                            .border(
                                if (selected) 2.5.dp else 1.dp,
                                if (selected) MessengerPinkStart else Color.White.copy(alpha = 0.5f),
                                CircleShape
                            )
                    )
                }
            }
        }

        // Floating Messenger Window Card (Pink Premium Theme)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MessengerBgDark,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 380.dp, max = 490.dp)
                .border(1.5.dp, MessengerPinkStart.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Pink Gradient Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(MessengerPinkStart, MessengerPinkEnd, MessengerPlum)))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        name = activeHead.displayName,
                        avatarBase64 = activeHead.peer?.avatarBase64.orEmpty(),
                        size = 38.dp,
                        isOnline = activeHead.peer?.isOnline == true,
                        accentColor = Color.White,
                        modifier = Modifier.clickable {
                            activeHead.peer?.uid?.let(onOpenProfile)
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeHead.peer?.uid?.let(onOpenProfile) }
                    ) {
                        NameWithBadge(
                            name = activeHead.displayName,
                            isVerified = activeHead.peer?.isVerified == true,
                            color = Color.White
                        )
                        Text(
                            text = if (activeHead.peer?.isOnline == true) "Active now" else AkTimeFormatter.formatLastSeen(false, activeHead.peer?.lastSeen ?: 0L),
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onStartAudioCall, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Call, "Audio Call", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onStartVideoCall, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Videocam, "Video Call", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { activeHead.peer?.uid?.let(onOpenProfile) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Info, "Profile Info", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = onMinimize, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages.filter { it.deletedFor[myUid] != true }, key = { it.id }) { msg ->
                        val isMine = msg.senderId == myUid
                        val decoded = decryptMessage(msg)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            if (!isMine) {
                                UserAvatar(
                                    name = activeHead.displayName,
                                    avatarBase64 = activeHead.peer?.avatarBase64.orEmpty(),
                                    size = 26.dp,
                                    accentColor = MessengerPinkStart
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isMine) 18.dp else 4.dp,
                                        bottomEnd = if (isMine) 4.dp else 18.dp
                                    ),
                                    color = if (isMine) Color.Transparent else MessengerIncomingDark,
                                    modifier = Modifier
                                        .widthIn(max = 235.dp)
                                        .then(
                                            if (isMine) {
                                                Modifier.background(
                                                    Brush.linearGradient(listOf(MessengerPinkStart, MessengerPinkEnd)),
                                                    RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
                                                )
                                            } else Modifier
                                        )
                                        .clickable { reactingMsg = msg }
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        if (msg.replyToPreview.isNotBlank()) {
                                            Text(
                                                text = "↩ ${msg.replyToPreview}",
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.78f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (msg.type == "voice") "🎤 Voice (${msg.voiceDurationSec}s)" else decoded,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                                if (msg.reactions.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF2C2535),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = msg.reactions.values.joinToString(" "),
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Reaction + Reply Picker for tapped message
                reactingMsg?.let { target ->
                    Surface(color = Color(0xFF231C2B), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("❤️", "😂", "👍", "🔥", "😮", "😢").forEach { emoji ->
                                    Text(emoji, fontSize = 20.sp, modifier = Modifier.clickable {
                                        onReactMessage(target, emoji)
                                        reactingMsg = null
                                    })
                                }
                            }
                            Row {
                                TextButton(onClick = {
                                    replyingTo = target
                                    reactingMsg = null
                                }) { Text("Reply", color = MessengerPinkStart) }
                                IconButton(onClick = { reactingMsg = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Reply bar
                replyingTo?.let { rep ->
                    Surface(color = Color(0xFF231C2B), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Replying: ${decryptMessage(rep)}",
                                color = MessengerPinkStart,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Emoji & Sticker Tray
                AnimatedVisibility(visible = showEmojiTray) {
                    Surface(color = Color(0xFF1F1927), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(listOf("❤️", "😘", "😂", "🔥", "😍", "👍", "🎉", "🙏", "🇧🇩")) { e ->
                                    Text(e, fontSize = 22.sp, modifier = Modifier.clickable { inputText += e })
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(listOf("Sticker: Love 💖", "Sticker: Wave 👋", "Sticker: Star ⭐")) { st ->
                                    AssistChip(
                                        onClick = {
                                            onSendSticker(st)
                                            showEmojiTray = false
                                        },
                                        label = { Text(st, fontSize = 11.sp, color = Color.White) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Pink Messenger Input Toolbar (Camera, Gallery, Mic, Emoji, Input, Send/Heart)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1520))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onOpenFullAppChat(activeHead.chat.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.CameraAlt, "Camera / Media in Chat", tint = MessengerPinkStart, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onOpenFullAppChat(activeHead.chat.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Image, "Gallery", tint = MessengerPinkStart, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onToggleVoiceRecord, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isRecordingVoice) Icons.Filled.StopCircle else Icons.Filled.Mic,
                            "Voice",
                            tint = if (isRecordingVoice) Color.Red else MessengerPinkStart,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF2A2233),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = {
                                    Text(
                                        if (isRecordingVoice) "Recording ${recordingSec}s..." else "Aa (বাংলা / English)...",
                                        fontSize = 13.sp,
                                        color = Color(0xFFB39EB5)
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                maxLines = 3,
                                modifier = Modifier.weight(1f).testTag("floating_reply_input")
                            )
                            IconButton(onClick = { showEmojiTray = !showEmojiTray }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Filled.EmojiEmotions, "Emoji", tint = MessengerPinkStart, modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendText(inputText.trim(), replyingTo)
                                inputText = ""
                                replyingTo = null
                            } else {
                                onSendText("❤️", null)
                            }
                        },
                        modifier = Modifier.size(38.dp).testTag("floating_send_button")
                    ) {
                        Icon(
                            imageVector = if (inputText.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Filled.Favorite,
                            contentDescription = if (inputText.isNotBlank()) "Send" else "Send Heart",
                            tint = MessengerPinkStart
                        )
                    }
                }
            }
        }
    }
}
