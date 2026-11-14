package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.akmessenger.shared.model.UserProfile
import com.aistudio.akmessenger.shared.ui.*
import com.aistudio.akmessenger.shared.util.QrCodeHelper
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.VerifiedBlue
import com.example.ui.viewmodel.MessengerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val call = calls.firstOrNull { it.id == callId }
    val myUid = session.uid

    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(call?.type == "video") }
    var isCameraOn by remember { mutableStateOf(call?.type == "video") }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isScreenSharing by remember { mutableStateOf(call?.screenSharing == true) }
    var durationSec by remember { mutableLongStateOf(0L) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        hasCameraPerm = map[Manifest.permission.CAMERA] ?: hasCameraPerm
        hasAudioPerm = map[Manifest.permission.RECORD_AUDIO] ?: hasAudioPerm
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (!hasAudioPerm) add(Manifest.permission.RECORD_AUDIO)
            if (call?.type == "video" && !hasCameraPerm) add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    LaunchedEffect(isSpeaker, isMuted) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = isSpeaker
        audioManager.isMicrophoneMute = isMuted
    }

    LaunchedEffect(call?.status) {
        if (call?.status == "active") {
            while (true) {
                delay(1000)
                durationSec += 1
            }
        } else if (call?.status == "ended" || call?.status == "rejected" || call?.status == "missed") {
            delay(800)
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioManager.isMicrophoneMute = false
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }

    val isIncomingRinging = call != null && call.status == "ringing" && call.callerId != myUid

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1B1E), Color(0xFF060B10))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (call?.type == "video" && isCameraOn && hasCameraPerm) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx)
                },
                update = { previewView ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview)
                        } catch (_: Exception) { }
                    }, ContextCompat.getMainExecutor(context))
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (call?.isGroup == true) "Group ${call.type.uppercase()} Call" else "AK Messenger ${call?.type?.uppercase() ?: "CALL"}",
                    color = Color(0xFF93B7AF),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = call?.callerName ?: "Connecting...",
                    color = Color.White,
                    fontFamily = AkFonts.Outfit,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (call?.status) {
                        "active" -> AkTimeFormatter.formatDuration(durationSec)
                        "ringing" -> if (isIncomingRinging) "Incoming call..." else "Ringing (RTDB signaling active)..."
                        else -> call?.status?.uppercase() ?: "ENDED"
                    },
                    color = Color(0xFF25D366),
                    fontFamily = AkFonts.JetBrainsMono
                )
                if (isScreenSharing) {
                    Spacer(Modifier.height(8.dp))
                    AssistChip(onClick = {}, label = { Text("Screen Sharing Active", color = Color.White) })
                }
            }

            if (isIncomingRinging && call != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                viewModel.repo.updateCall(call.copy(status = "rejected", endedAt = System.currentTimeMillis()))
                                onBack()
                            }
                        },
                        containerColor = CoralAlert,
                        modifier = Modifier.size(64.dp).testTag("reject_call_button")
                    ) { Icon(Icons.Filled.CallEnd, "Reject", tint = Color.White) }

                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                viewModel.repo.updateCall(call.copy(status = "active", answerSdp = "v=0\r\ns=AK-ANSWER"))
                            }
                        },
                        containerColor = EmeraldPrimary,
                        modifier = Modifier.size(64.dp).testTag("accept_call_button")
                    ) { Icon(Icons.Filled.Call, "Accept", tint = Color.White) }
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isMuted = !isMuted }) {
                            Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", tint = Color.White)
                        }
                        IconButton(onClick = { isSpeaker = !isSpeaker }) {
                            Icon(if (isSpeaker) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown, "Speaker", tint = Color.White)
                        }
                        IconButton(onClick = { isCameraOn = !isCameraOn }) {
                            Icon(if (isCameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Camera", tint = Color.White)
                        }
                        IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                            Icon(Icons.Filled.Cameraswitch, "Switch Camera", tint = Color.White)
                        }
                        IconButton(onClick = {
                            isScreenSharing = !isScreenSharing
                            if (call != null) {
                                scope.launch { viewModel.repo.updateCall(call.copy(screenSharing = isScreenSharing)) }
                            }
                        }) {
                            Icon(Icons.Filled.ScreenShare, "Screen Share", tint = if (isScreenSharing) EmeraldPrimary else Color.White)
                        }
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (call != null) {
                                        val finalStatus = if (call.status == "ringing") "missed" else "ended"
                                        viewModel.repo.updateCall(
                                            call.copy(
                                                status = finalStatus,
                                                endedAt = System.currentTimeMillis(),
                                                durationSec = durationSec
                                            )
                                        )
                                    }
                                    onBack()
                                }
                            },
                            containerColor = CoralAlert,
                            modifier = Modifier.size(52.dp).testTag("hangup_call_button")
                        ) { Icon(Icons.Filled.CallEnd, "End Call", tint = Color.White) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me = users.firstOrNull { it.uid == session.uid } ?: UserProfile(uid = session.uid, fullName = session.email)
    val profileLink = "https://akmessenger.app/u/${me.username.ifBlank { me.uid }}?uid=${me.uid}"
    val qrBmp = remember(profileLink) { QrCodeHelper.generateQrBitmap(profileLink) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("My QR Code & Profile Share") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NameWithBadge(me.fullName, me.isVerified)
                    Text("@${me.username} • UID: ${me.uid}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    if (qrBmp != null) {
                        Image(bitmap = qrBmp, contentDescription = "Profile QR Code", modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(profileLink, fontFamily = AkFonts.JetBrainsMono, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("AK Profile", profileLink))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Link")
                }
                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Connect with me on AK Messenger (@${me.username}): $profileLink")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share via Facebook, TikTok, YouTube, Telegram..."))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Share Profile")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    uid: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val user = users.firstOrNull { it.uid == uid }
    val me = users.firstOrNull { it.uid == session.uid } ?: UserProfile(uid = session.uid)

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Profile") }
            )
        }
    ) { padding ->
        if (user == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("User not found") }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                UserAvatar(user.fullName, user.avatarBase64, size = 96.dp, isOnline = user.isOnline)
                Spacer(Modifier.height(12.dp))
                NameWithBadge(user.fullName, user.isVerified)
                Text("@${user.username} • ${user.phoneNumber}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(user.about)
                Spacer(Modifier.height(6.dp))
                Text("UID: ${user.uid}", fontFamily = AkFonts.JetBrainsMono, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Button(onClick = {
                    scope.launch {
                        viewModel.repo.getOrCreateDirectChat(me, user).onSuccess { onOpenChat(it.id) }
                    }
                }) {
                    Text("Message ${user.fullName}")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        scope.launch { viewModel.repo.submitReport(me, "profile", user.uid, user.fullName, "Profile report", "") }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Report Profile") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onGroupCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me = users.firstOrNull { it.uid == session.uid } ?: UserProfile(uid = session.uid, fullName = session.email)

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("New Group") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text("Select Members", fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(users.filter { it.uid != me.uid }, key = { it.uid }) { user ->
                    val checked = selectedUids[user.uid] == true
                    ListItem(
                        leadingContent = { UserAvatar(user.fullName, user.avatarBase64, size = 40.dp) },
                        headlineContent = { NameWithBadge(user.fullName, user.isVerified) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = { selectedUids[user.uid] = it }) },
                        modifier = Modifier.clickable { selectedUids[user.uid] = !checked }
                    )
                }
            }
            Button(
                onClick = {
                    val chosen = users.filter { selectedUids[it.uid] == true }
                    scope.launch {
                        viewModel.repo.createGroup(name, description, me, chosen).onSuccess { grp ->
                            onGroupCreated(grp.id)
                        }
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Create Group") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCommunityScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val me = users.firstOrNull { it.uid == session.uid } ?: UserProfile(uid = session.uid, fullName = session.email)

    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Create Community") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Community Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    scope.launch {
                        viewModel.repo.createCommunity(name, desc, me).onSuccess { onBack() }
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Create Community") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    viewModel: MessengerViewModel,
    onBack: () -> Unit,
    onOpenCall: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val grp = groups.firstOrNull { it.id == groupId }
    val me = users.firstOrNull { it.uid == session.uid } ?: UserProfile(uid = session.uid)

    var newAnnouncement by remember(grp?.announcement) { mutableStateOf(grp?.announcement.orEmpty()) }
    var showAddMember by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(grp?.name ?: "Group Info") }
            )
        }
    ) { padding ->
        if (grp == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Group not found") }
        } else {
            val isAdmin = grp.adminIds[me.uid] == true
            val members = users.filter { grp.memberIds[it.uid] == true }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                item {
                    Text(grp.description.ifBlank { "No description" })
                    Spacer(Modifier.height(8.dp))
                    Text("Invite Code: ${grp.inviteCode}", fontFamily = AkFonts.JetBrainsMono, color = EmeraldPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        OutlinedButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Invite", "https://akmessenger.app/join/${grp.inviteCode}"))
                        }) { Text("Copy Invite Link") }
                        Button(onClick = {
                            scope.launch {
                                viewModel.repo.startCall(me, members.filter { it.uid != me.uid }, "audio", true, grp.id)
                                    .onSuccess { onOpenCall(it.id) }
                            }
                        }) { Text("Group Call") }
                    }
                    if (isAdmin) {
                        OutlinedTextField(
                            value = newAnnouncement,
                            onValueChange = { newAnnouncement = it },
                            label = { Text("Group Announcement") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch { viewModel.repo.updateGroup(grp.copy(announcement = newAnnouncement)) }
                            }) { Text("Save Announcement") }
                            TextButton(onClick = { showAddMember = true }) { Text("Add Member") }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Members (${members.size})", fontWeight = FontWeight.Bold)
                }
                items(members, key = { it.uid }) { member ->
                    ListItem(
                        leadingContent = { UserAvatar(member.fullName, member.avatarBase64, size = 40.dp, isOnline = member.isOnline) },
                        headlineContent = { NameWithBadge(member.fullName, member.isVerified) },
                        supportingContent = { Text(if (grp.adminIds[member.uid] == true) "Group Admin" else "Member") },
                        trailingContent = {
                            if (isAdmin && member.uid != me.uid) {
                                IconButton(onClick = {
                                    scope.launch { viewModel.repo.updateGroup(grp.copy(memberIds = grp.memberIds - member.uid)) }
                                }) { Icon(Icons.Filled.PersonRemove, "Remove", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddMember && grp != null) {
        val candidates = users.filter { grp.memberIds[it.uid] != true }
        AlertDialog(
            onDismissRequest = { showAddMember = false },
            title = { Text("Add Member") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(candidates, key = { it.uid }) { u ->
                        ListItem(
                            headlineContent = { Text(u.fullName) },
                            modifier = Modifier.clickable {
                                scope.launch { viewModel.repo.updateGroup(grp.copy(memberIds = grp.memberIds + (u.uid to true))) }
                                showAddMember = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddMember = false }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    viewModel: MessengerViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val starred = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(chats) {
        starred.clear()
        chats.forEach { chat ->
            viewModel.syncEngine.observeMessages(chat.id).collect { msgs ->
                msgs.filter { it.starredBy[session.uid] == true }.forEach { m ->
                    starred.add(chat.title to (if (m.plaintextFallback.isNotBlank()) m.plaintextFallback else "[Encrypted / Media]"))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Starred Messages") }
            )
        }
    ) { padding ->
        if (starred.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No starred messages yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(starred) { (title, text) ->
                    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.Bold) }, supportingContent = { Text(text) })
                }
            }
        }
    }
}
