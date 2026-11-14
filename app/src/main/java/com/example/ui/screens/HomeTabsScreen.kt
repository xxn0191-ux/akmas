package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.akmessenger.shared.firebase.RtdbStatus
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.ui.*
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.VerifiedBlue
import com.example.ui.viewmodel.MessengerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabsScreen(
    viewModel: MessengerViewModel,
    onOpenChat: (String) -> Unit,
    onOpenCall: (String) -> Unit,
    onOpenGroupDetail: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenQrShare: () -> Unit,
    onOpenStarred: () -> Unit,
    onOpenNewGroup: () -> Unit,
    onOpenNewCommunity: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val communities by viewModel.communities.collectAsStateWithLifecycle()
    val stories by viewModel.stories.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val rtdbStatus by viewModel.rtdbStatus.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val infoBanner by viewModel.infoBanner.collectAsStateWithLifecycle()

    val myUid = session.uid
    val me = users.firstOrNull { it.uid == myUid } ?: UserProfile(uid = myUid, email = session.email, fullName = session.email.substringBefore("@"))

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showUserPickerDialog by remember { mutableStateOf(false) }
    var chatFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Unread, 2=Groups, 3=Archived

    // Story dialogs
    var showStoryComposer by remember { mutableStateOf(false) }
    var viewingStory by remember { mutableStateOf<StoryItem?>(null) }

    // Ask notification permission on Android 13+
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (appSettings.maintenanceMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 460.dp)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Build, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Maintenance Mode", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(appSettings.maintenanceMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.refreshNow() }) { Text("Check Again") }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (showSearchBar) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search name, @username, phone, UID...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("global_search_input")
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = appSettings.appName.ifBlank { "AK Messenger" },
                                    fontFamily = AkFonts.Outfit,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                RtdbPill(rtdbStatus)
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) searchQuery = ""
                            },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search, "Search")
                        }
                        IconButton(onClick = { viewModel.refreshNow() }, modifier = Modifier.testTag("sync_button")) {
                            Icon(Icons.Filled.Sync, "Sync Firebase")
                        }
                    }
                )
                val latestNotice = notifications.maxByOrNull { it.sentAt }
                if (latestNotice != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Campaign, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${latestNotice.title}: ${latestNotice.message}",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (infoBanner != null) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(infoBanner.orEmpty(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearInfoBanner() }) { Text("Dismiss") }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = { showUserPickerDialog = true },
                    containerColor = EmeraldPrimary,
                    modifier = Modifier.testTag("new_chat_fab")
                ) { Icon(Icons.AutoMirrored.Filled.Chat, "New Chat", tint = Color.White) }
                1 -> FloatingActionButton(
                    onClick = { showStoryComposer = true },
                    containerColor = EmeraldPrimary,
                    modifier = Modifier.testTag("new_story_fab")
                ) { Icon(Icons.Filled.AddAPhoto, "Add Story", tint = Color.White) }
                2 -> FloatingActionButton(
                    onClick = onOpenNewCommunity,
                    containerColor = EmeraldPrimary,
                    modifier = Modifier.testTag("new_community_fab")
                ) { Icon(Icons.Filled.Groups, "New Community", tint = Color.White) }
            }
        },
        bottomBar = {
            NavigationBar {
                val navItems = listOf(
                    Triple("Chats", Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Filled.Chat),
                    Triple("Updates", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
                    Triple("Communities", Icons.Filled.Groups, Icons.Outlined.Groups),
                    Triple("Calls", Icons.Filled.Call, Icons.Outlined.Call),
                    Triple("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
                )
                navItems.forEachIndexed { idx, (label, filled, outlined) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(if (selectedTab == idx) filled else outlined, label) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("bottom_tab_$idx")
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> ChatsTabContent(
                    me = me,
                    users = users,
                    chats = chats,
                    searchQuery = searchQuery,
                    filter = chatFilter,
                    onFilterChange = { chatFilter = it },
                    onOpenChat = onOpenChat,
                    onStartDirectChat = { peer ->
                        scope.launch {
                            viewModel.repo.getOrCreateDirectChat(me, peer).onSuccess { c ->
                                onOpenChat(c.id)
                            }
                        }
                    },
                    onToggleArchive = { chat ->
                        scope.launch {
                            val map = chat.archivedBy.toMutableMap()
                            map[myUid] = !(map[myUid] == true)
                            viewModel.repo.updateChatThread(chat.copy(archivedBy = map))
                        }
                    }
                )
                1 -> UpdatesTabContent(
                    me = me,
                    stories = stories,
                    onAddStory = { showStoryComposer = true },
                    onViewStory = { st ->
                        viewingStory = st
                        if (st.uid != myUid && !st.viewers.containsKey(myUid)) {
                            scope.launch {
                                val updated = st.copy(viewers = st.viewers + (myUid to System.currentTimeMillis()))
                                viewModel.repo.updateStory(updated)
                            }
                        }
                    }
                )
                2 -> CommunitiesTabContent(
                    communities = communities,
                    groups = groups,
                    onOpenGroup = onOpenGroupDetail,
                    onOpenGroupChat = onOpenChat,
                    onCreateGroup = onOpenNewGroup,
                    onCreateCommunity = onOpenNewCommunity
                )
                3 -> CallsTabContent(
                    myUid = myUid,
                    users = users,
                    calls = calls,
                    onOpenCall = onOpenCall,
                    onStartNewCall = { peer, type ->
                        scope.launch {
                            viewModel.repo.startCall(me, listOf(peer), type, isGroup = false).onSuccess {
                                onOpenCall(it.id)
                            }
                        }
                    }
                )
                4 -> SettingsTabContent(
                    me = me,
                    darkTheme = session.darkTheme,
                    chatHeadsEnabled = session.chatHeadsEnabled,
                    pinkThemeEnabled = session.pinkMessengerTheme,
                    appSettings = appSettings,
                    rtdbStatus = rtdbStatus,
                    onToggleTheme = viewModel::toggleDarkTheme,
                    onToggleChatHeads = viewModel::setChatHeadsEnabled,
                    onTogglePinkTheme = viewModel::setPinkMessengerTheme,
                    onUpdateProfile = { updated -> scope.launch { viewModel.repo.updateProfile(updated) } },
                    onPickAvatar = { uri ->
                        scope.launch {
                            val b64 = viewModel.encodeImageUriToBase64(uri, 320)
                            if (b64.isNotBlank()) viewModel.repo.updateProfile(me.copy(avatarBase64 = b64))
                        }
                    },
                    onOpenQrShare = onOpenQrShare,
                    onOpenStarred = onOpenStarred,
                    onLogout = { viewModel.logout(onLoggedOut) },
                    onDeleteAccount = { viewModel.deleteMyAccount(onLoggedOut) }
                )
            }
        }
    }

    if (showUserPickerDialog) {
        UserDirectoryDialog(
            users = users.filter { it.uid != myUid },
            onDismiss = { showUserPickerDialog = false },
            onCreateGroup = {
                showUserPickerDialog = false
                onOpenNewGroup()
            },
            onSelectUser = { peer ->
                showUserPickerDialog = false
                scope.launch {
                    viewModel.repo.getOrCreateDirectChat(me, peer).onSuccess { c -> onOpenChat(c.id) }
                }
            },
            onInspectProfile = { peer ->
                showUserPickerDialog = false
                onOpenUserProfile(peer.uid)
            }
        )
    }

    if (showStoryComposer) {
        StoryComposerDialog(
            viewModel = viewModel,
            me = me,
            onDismiss = { showStoryComposer = false }
        )
    }

    viewingStory?.let { story ->
        StoryViewerDialog(
            story = story,
            me = me,
            users = users,
            onDismiss = { viewingStory = null },
            onReact = { emoji ->
                scope.launch {
                    val updated = story.copy(reactions = story.reactions + (myUid to emoji))
                    viewModel.repo.updateStory(updated)
                    viewingStory = updated
                }
            },
            onReply = { replyText ->
                val author = users.firstOrNull { it.uid == story.uid }
                if (author != null) {
                    scope.launch {
                        val chat = viewModel.repo.getOrCreateDirectChat(me, author).getOrNull()
                        if (chat != null) {
                            viewModel.repo.sendMessage(
                                chat = chat,
                                sender = me,
                                peerPublicKey = author.publicKey,
                                type = "text",
                                plaintext = "Replied to story: $replyText"
                            )
                            viewingStory = null
                            onOpenChat(chat.id)
                        }
                    }
                }
            },
            onDelete = {
                scope.launch {
                    viewModel.repo.deleteStory(story.id)
                    viewingStory = null
                }
            },
            onReport = {
                scope.launch {
                    viewModel.repo.submitReport(me, "story", story.id, story.authorName, "Inappropriate story", story.textContent)
                    viewingStory = null
                }
            }
        )
    }
}

@Composable
private fun RtdbPill(status: RtdbStatus) {
    val (label, color) = when (status) {
        is RtdbStatus.Connected -> "Live" to Color(0xFF25D366)
        is RtdbStatus.Connecting -> "Syncing" to Color(0xFFFFB300)
        is RtdbStatus.Error -> "Offline/Rule" to Color(0xFFF15C6D)
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ChatsTabContent(
    me: UserProfile,
    users: List<UserProfile>,
    chats: List<ChatThread>,
    searchQuery: String,
    filter: Int,
    onFilterChange: (Int) -> Unit,
    onOpenChat: (String) -> Unit,
    onStartDirectChat: (UserProfile) -> Unit,
    onToggleArchive: (ChatThread) -> Unit
) {
    val myChats = chats.filter { it.participantIds[me.uid] == true }
    val filteredChats = myChats.filter { chat ->
        val archived = chat.archivedBy[me.uid] == true
        val unread = (chat.unreadCounts[me.uid] ?: 0) > 0
        val matchesTab = when (filter) {
            1 -> unread && !archived
            2 -> chat.isGroup && !archived
            3 -> archived
            else -> !archived
        }
        val q = searchQuery.trim().lowercase()
        val matchesSearch = q.isEmpty() || chat.title.lowercase().contains(q) ||
            chat.participantNames.values.any { it.lowercase().contains(q) } ||
            chat.lastMessagePreview.lowercase().contains(q)
        matchesTab && matchesSearch
    }.sortedWith(compareByDescending<ChatThread> { it.pinnedBy[me.uid] == true }.thenByDescending { it.lastMessageTimestamp })

    val matchedUsers = if (searchQuery.trim().length >= 2) {
        val q = searchQuery.trim().lowercase()
        users.filter {
            it.uid != me.uid && (
                it.fullName.lowercase().contains(q) ||
                    it.username.lowercase().contains(q) ||
                    it.phoneNumber.lowercase().contains(q) ||
                    it.uid.lowercase().contains(q)
                )
        }
    } else emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val labels = listOf("All", "Unread", "Groups", "Archived")
            items(labels.size) { idx ->
                FilterChip(
                    selected = filter == idx,
                    onClick = { onFilterChange(idx) },
                    label = { Text(labels[idx]) }
                )
            }
        }

        if (filteredChats.isEmpty() && matchedUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap the green chat button to find users by name, @username, mobile number, or UID.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredChats, key = { it.id }) { chat ->
                    val peerUid = if (!chat.isGroup) chat.participantIds.keys.firstOrNull { it != me.uid } else null
                    val peer = users.firstOrNull { it.uid == peerUid }
                    val displayName = if (chat.isGroup) chat.title else peer?.fullName ?: chat.participantNames[peerUid] ?: chat.title
                    val unread = chat.unreadCounts[me.uid] ?: 0
                    val isPinned = chat.pinnedBy[me.uid] == true
                    val isMuted = chat.mutedBy[me.uid] == true

                    ListItem(
                        leadingContent = {
                            UserAvatar(
                                name = displayName,
                                avatarBase64 = peer?.avatarBase64.orEmpty(),
                                isOnline = peer?.isOnline == true
                            )
                        },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NameWithBadge(
                                    name = displayName,
                                    isVerified = peer?.isVerified == true,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isPinned) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.PushPin, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isMuted) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Filled.VolumeOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                text = chat.lastMessagePreview.ifBlank { "Tap to start encrypted chat" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = AkTimeFormatter.formatClock(chat.lastMessageTimestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (unread > 0) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (unread > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Badge(containerColor = EmeraldPrimary) {
                                        Text(unread.toString(), color = Color.White)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .clickable { onOpenChat(chat.id) }
                            .testTag("chat_row_${chat.id}")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                }

                if (matchedUsers.isNotEmpty()) {
                    item {
                        Text(
                            text = "Directory Matches",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(matchedUsers, key = { "dir_${it.uid}" }) { user ->
                        ListItem(
                            leadingContent = { UserAvatar(user.fullName, user.avatarBase64, isOnline = user.isOnline) },
                            headlineContent = { NameWithBadge(user.fullName, user.isVerified) },
                            supportingContent = { Text("@${user.username} • ${user.phoneNumber}") },
                            modifier = Modifier.clickable { onStartDirectChat(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesTabContent(
    me: UserProfile,
    stories: List<StoryItem>,
    onAddStory: () -> Unit,
    onViewStory: (StoryItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().clickable { onAddStory() }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(me.fullName, me.avatarBase64, size = 54.dp, isOnline = true)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My Status", fontWeight = FontWeight.Bold)
                        Text("Tap to post Text, Photo, or Video Story (24h auto-expire)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.AddCircle, null, tint = EmeraldPrimary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Recent Updates", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        if (stories.isEmpty()) {
            item {
                Text("No active stories in the last 24 hours.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(stories, key = { it.id }) { story ->
                ListItem(
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, EmeraldPrimary, CircleShape)
                                .padding(3.dp)
                        ) {
                            UserAvatar(story.authorName, "", size = 46.dp)
                        }
                    },
                    headlineContent = { NameWithBadge(story.authorName, story.authorVerified) },
                    supportingContent = {
                        Text("${story.type.uppercase()} • ${AkTimeFormatter.formatDateTime(story.createdAt)} • ${story.viewers.size} views")
                    },
                    modifier = Modifier.clickable { onViewStory(story) }
                )
            }
        }
    }
}

@Composable
private fun CommunitiesTabContent(
    communities: List<CommunityInfo>,
    groups: List<GroupInfo>,
    onOpenGroup: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onCreateCommunity: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onCreateCommunity, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Groups, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Community")
                }
                OutlinedButton(onClick = onCreateGroup, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.GroupAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Group")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (communities.isNotEmpty()) {
            item { Text("Communities", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            items(communities, key = { it.id }) { comm ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (comm.description.isNotBlank()) {
                            Text(comm.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { onOpenGroupChat(comm.announcementChatId) }) {
                            Icon(Icons.Filled.Campaign, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Announcement Channel")
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("Groups (${groups.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        items(groups, key = { it.id }) { grp ->
            ListItem(
                leadingContent = { UserAvatar(grp.name, grp.avatarBase64) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(grp.name, fontWeight = FontWeight.SemiBold)
                        if (grp.isSuspended) {
                            Spacer(Modifier.width(8.dp))
                            Text("SUSPENDED", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                supportingContent = { Text("${grp.memberIds.size} members • ${grp.description.ifBlank { "Group chat" }}") },
                trailingContent = {
                    IconButton(onClick = { onOpenGroup(grp.id) }) {
                        Icon(Icons.Filled.Info, "Group Details")
                    }
                },
                modifier = Modifier.clickable { onOpenGroupChat(grp.id) }
            )
        }
    }
}

@Composable
private fun CallsTabContent(
    myUid: String,
    users: List<UserProfile>,
    calls: List<CallSession>,
    onOpenCall: (String) -> Unit,
    onStartNewCall: (UserProfile, String) -> Unit
) {
    val myCalls = calls.filter { it.callerId == myUid || it.calleeIds[myUid] == true }
        .sortedByDescending { it.startedAt }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Quick Call Directory", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(users.filter { it.uid != myUid }, key = { it.uid }) { peer ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(12.dp).width(120.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            UserAvatar(peer.fullName, peer.avatarBase64, isOnline = peer.isOnline)
                            Spacer(Modifier.height(6.dp))
                            Text(peer.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Row {
                                IconButton(onClick = { onStartNewCall(peer, "audio") }) {
                                    Icon(Icons.Filled.Call, "Audio Call", tint = EmeraldPrimary)
                                }
                                IconButton(onClick = { onStartNewCall(peer, "video") }) {
                                    Icon(Icons.Filled.Videocam, "Video Call", tint = VerifiedBlue)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Call History", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (myCalls.isEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                Text("No calls yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(myCalls, key = { it.id }) { call ->
                val isMissed = call.status == "missed" || call.status == "rejected"
                ListItem(
                    leadingContent = {
                        Icon(
                            imageVector = if (call.type == "video") Icons.Filled.Videocam else Icons.Filled.Call,
                            contentDescription = null,
                            tint = if (isMissed) MaterialTheme.colorScheme.error else EmeraldPrimary
                        )
                    },
                    headlineContent = { Text(call.callerName.ifBlank { "AK Call" }) },
                    supportingContent = {
                        Text("${call.status.uppercase()} • ${AkTimeFormatter.formatDateTime(call.startedAt)} • ${AkTimeFormatter.formatDuration(call.durationSec)}")
                    },
                    modifier = Modifier.clickable { onOpenCall(call.id) }
                )
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    me: UserProfile,
    darkTheme: Boolean,
    chatHeadsEnabled: Boolean,
    pinkThemeEnabled: Boolean,
    appSettings: AppSettings,
    rtdbStatus: RtdbStatus,
    onToggleTheme: (Boolean) -> Unit,
    onToggleChatHeads: (Boolean) -> Unit,
    onTogglePinkTheme: (Boolean) -> Unit,
    onUpdateProfile: (UserProfile) -> Unit,
    onPickAvatar: (Uri) -> Unit,
    onOpenQrShare: () -> Unit,
    onOpenStarred: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPolicyDialog by remember { mutableStateOf(false) }
    var hasOverlayPerm by remember { mutableStateOf(com.example.overlay.ChatHeadManager.canDrawOverlays(context)) }
    var ignoringBattery by remember { mutableStateOf(com.example.overlay.ChatHeadManager.isIgnoringBatteryOptimizations(context)) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickAvatar(uri)
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = me.fullName,
                        avatarBase64 = me.avatarBase64,
                        size = 64.dp,
                        isOnline = true,
                        modifier = Modifier.clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        NameWithBadge(me.fullName, me.isVerified)
                        Text("@${me.username} • ${me.phoneNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(me.about, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                    IconButton(onClick = onOpenQrShare, modifier = Modifier.testTag("open_qr_button")) {
                        Icon(Icons.Filled.QrCode2, "My QR Code", tint = EmeraldPrimary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating Chat Heads", fontWeight = FontWeight.Bold)
                            Text("Messenger bubbles over YouTube, Facebook, Chrome & Home", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = chatHeadsEnabled && hasOverlayPerm,
                            onCheckedChange = { wantEnable ->
                                hasOverlayPerm = com.example.overlay.ChatHeadManager.canDrawOverlays(context)
                                if (wantEnable && !hasOverlayPerm) {
                                    context.startActivity(com.example.overlay.ChatHeadManager.requestOverlayPermissionIntent(context))
                                } else {
                                    onToggleChatHeads(wantEnable)
                                }
                            },
                            modifier = Modifier.testTag("chat_heads_switch")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(com.example.overlay.ChatHeadManager.requestOverlayPermissionIntent(context))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (hasOverlayPerm) "Overlay: Granted" else "Allow Display Over Apps", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(com.example.overlay.ChatHeadManager.requestBatteryOptimizationIntent(context))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (ignoringBattery) "Battery: Unrestricted" else "Battery Optimization", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = {
                            hasOverlayPerm = com.example.overlay.ChatHeadManager.canDrawOverlays(context)
                            ignoringBattery = com.example.overlay.ChatHeadManager.isIgnoringBatteryOptimizations(context)
                            if (hasOverlayPerm) com.example.overlay.ChatHeadManager.startService(context)
                            else context.startActivity(com.example.overlay.ChatHeadManager.requestOverlayPermissionIntent(context))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasOverlayPerm) "Refresh / Start Chat Head Service" else "Grant Overlay Permission")
                    }
                }
            }

            ListItem(
                leadingContent = { Icon(Icons.Filled.Palette, null, tint = Color(0xFFFF4B91)) },
                headlineContent = { Text("Pink Messenger Chat Theme") },
                supportingContent = { Text("Messenger-inspired pink gradient bubbles & avatars") },
                trailingContent = { Switch(checked = pinkThemeEnabled, onCheckedChange = onTogglePinkTheme) }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Edit, null) },
                headlineContent = { Text("Edit Profile & Privacy") },
                supportingContent = { Text("Name, About, Last Seen & Photo Privacy") },
                modifier = Modifier.clickable { showEditDialog = true }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.QrCode, null, tint = EmeraldPrimary) },
                headlineContent = { Text("Share Profile & QR Code") },
                supportingContent = { Text("Copy link or share to Facebook, TikTok, YouTube, Telegram") },
                modifier = Modifier.clickable { onOpenQrShare() }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Star, null, tint = Color(0xFFFFB300)) },
                headlineContent = { Text("Starred Messages") },
                modifier = Modifier.clickable { onOpenStarred() }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.DarkMode, null) },
                headlineContent = { Text("Dark Theme") },
                trailingContent = { Switch(checked = darkTheme, onCheckedChange = onToggleTheme) }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Security, null, tint = EmeraldPrimary) },
                headlineContent = { Text("E2EE Public Key Fingerprint") },
                supportingContent = {
                    Text(
                        text = "UID: ${me.uid}\nRTDB: $rtdbStatus",
                        fontFamily = AkFonts.JetBrainsMono,
                        fontSize = 11.sp
                    )
                }
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Policy, null) },
                headlineContent = { Text("Privacy Policy, Terms & Support") },
                supportingContent = { Text(appSettings.supportContact) },
                modifier = Modifier.clickable { showPolicyDialog = true }
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().testTag("logout_button")
            ) {
                Icon(Icons.Filled.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text("Log Out")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete Account Permanently")
            }
        }
    }

    if (showEditDialog) {
        var name by remember { mutableStateOf(me.fullName) }
        var about by remember { mutableStateOf(me.about) }
        var lastSeenPriv by remember { mutableStateOf(me.privacyLastSeen) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") })
                    OutlinedTextField(value = about, onValueChange = { about = it }, label = { Text("About") })
                    Text("Last Seen Privacy: $lastSeenPriv", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("everyone", "contacts", "nobody").forEach { opt ->
                            FilterChip(selected = lastSeenPriv == opt, onClick = { lastSeenPriv = opt }, label = { Text(opt) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateProfile(me.copy(fullName = name.trim(), about = about.trim(), privacyLastSeen = lastSeenPriv))
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPolicyDialog = false },
            title = { Text("AK Messenger Policies") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy Policy", fontWeight = FontWeight.Bold)
                    Text(appSettings.privacyPolicy, style = MaterialTheme.typography.bodySmall)
                    Text("Terms & Conditions", fontWeight = FontWeight.Bold)
                    Text(appSettings.termsAndConditions, style = MaterialTheme.typography.bodySmall)
                    Text("Support: ${appSettings.supportContact}", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { TextButton(onClick = { showPolicyDialog = false }) { Text("Close") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account?") },
            text = { Text("This permanently deletes your Firebase Auth login, username reservation, and profile.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAccount()
                    }
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UserDirectoryDialog(
    users: List<UserProfile>,
    onDismiss: () -> Unit,
    onCreateGroup: () -> Unit,
    onSelectUser: (UserProfile) -> Unit,
    onInspectProfile: (UserProfile) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = users.filter {
        val q = query.trim().lowercase()
        q.isEmpty() || it.fullName.lowercase().contains(q) || it.username.lowercase().contains(q) ||
            it.phoneNumber.lowercase().contains(q) || it.uid.lowercase().contains(q)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Chat") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name, @username, phone, UID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCreateGroup) {
                    Icon(Icons.Filled.GroupAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Group")
                }
                LazyColumn {
                    items(filtered, key = { it.uid }) { user ->
                        ListItem(
                            leadingContent = { UserAvatar(user.fullName, user.avatarBase64, size = 40.dp, isOnline = user.isOnline) },
                            headlineContent = { NameWithBadge(user.fullName, user.isVerified) },
                            supportingContent = { Text("@${user.username} • ${user.phoneNumber}") },
                            trailingContent = {
                                IconButton(onClick = { onInspectProfile(user) }) {
                                    Icon(Icons.Filled.Info, "View Profile")
                                }
                            },
                            modifier = Modifier.clickable { onSelectUser(user) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun StoryComposerDialog(
    viewModel: MessengerViewModel,
    me: UserProfile,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var storyType by remember { mutableStateOf("text") }
    var textContent by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var bgHex by remember { mutableStateOf("#0B6E5B") }
    var privacy by remember { mutableStateOf("everyone") }
    var mediaB64 by remember { mutableStateOf("") }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val res = viewModel.encodeFileUriToBase64(uri)
                if (res != null) {
                    mediaB64 = res.first
                    storyType = if (res.second.startsWith("video")) "video" else "image"
                } else {
                    mediaB64 = viewModel.encodeImageUriToBase64(uri, 640)
                    storyType = "image"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish 24h Story") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = storyType == "text", onClick = { storyType = "text" }, label = { Text("Text") })
                    FilterChip(selected = storyType != "text", onClick = {
                        mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }, label = { Text("Photo / Video") })
                }
                if (storyType == "text") {
                    OutlinedTextField(
                        value = textContent,
                        onValueChange = { textContent = it },
                        label = { Text("Status Text (Bangla / English)") },
                        minLines = 3
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#0B6E5B", "#1A237E", "#880E4F", "#BF360C").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(if (bgHex == hex) 2.5.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { bgHex = hex }
                            )
                        }
                    }
                } else {
                    Text(if (mediaB64.isNotBlank()) "Media ready ($storyType)" else "Pick photo or video", color = EmeraldPrimary)
                    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Caption") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("everyone", "contacts", "private").forEach { p ->
                        FilterChip(selected = privacy == p, onClick = { privacy = p }, label = { Text(p) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.repo.postStory(me, storyType, textContent, bgHex, mediaB64, caption, privacy)
                        onDismiss()
                    }
                },
                enabled = (storyType == "text" && textContent.isNotBlank()) || mediaB64.isNotBlank()
            ) { Text("Post Story") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StoryViewerDialog(
    story: StoryItem,
    me: UserProfile,
    users: List<UserProfile>,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                NameWithBadge(story.authorName, story.authorVerified)
                Row {
                    if (story.uid == me.uid) {
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete Story", tint = MaterialTheme.colorScheme.error) }
                    } else {
                        IconButton(onClick = onReport) { Icon(Icons.Filled.Flag, "Report Story") }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(android.graphics.Color.parseColor(story.bgColorHex.ifBlank { "#0B6E5B" })))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (story.type == "text") {
                        Text(story.textContent, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    } else if (story.mediaBase64.isNotBlank()) {
                        val bmp = remember(story.mediaBase64) {
                            try {
                                val bytes = android.util.Base64.decode(story.mediaBase64, android.util.Base64.NO_WRAP)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (_: Exception) { null }
                        }
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Story media", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text("Video Story: ${story.caption}", color = Color.White)
                        }
                    }
                }
                if (story.caption.isNotBlank()) Text(story.caption)
                Text("Viewers (${story.viewers.size}): ${story.viewers.keys.mapNotNull { uid -> users.firstOrNull { it.uid == uid }?.fullName }.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("❤️", "🔥", "😂", "👏", "😮").forEach { emoji ->
                        Text(emoji, fontSize = 22.sp, modifier = Modifier.clickable { onReact(emoji) })
                    }
                }
                if (story.uid != me.uid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Reply to story...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { if (replyText.isNotBlank()) onReply(replyText) }) {
                            Icon(Icons.Filled.Send, "Send Reply")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
