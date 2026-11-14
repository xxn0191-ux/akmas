package com.aistudio.akmessenger.admin.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aistudio.akmessenger.admin.ui.theme.*
import com.aistudio.akmessenger.admin.ui.viewmodel.AdminViewModel
import com.aistudio.akmessenger.shared.firebase.RtdbStatus
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.repository.AdminDashboardStats
import com.aistudio.akmessenger.shared.ui.AkFonts
import com.aistudio.akmessenger.shared.ui.AkTimeFormatter
import com.aistudio.akmessenger.shared.ui.NameWithBadge
import com.aistudio.akmessenger.shared.ui.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel
) {
    val admin by viewModel.currentAdmin.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val communities by viewModel.communities.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val logs by viewModel.adminLogs.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val rtdbStatus by viewModel.rtdbStatus.collectAsStateWithLifecycle()
    val toast by viewModel.statusToast.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AdminNavy),
                    title = {
                        Column {
                            Text("AK Messenger Admin", fontFamily = AkFonts.SpaceGrotesk, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${admin.email} • $rtdbStatus",
                                fontFamily = AkFonts.JetBrainsMono,
                                fontSize = 11.sp,
                                color = if (rtdbStatus is RtdbStatus.Connected) NeonMint else NeonCyan
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refreshNow() }) { Icon(Icons.Filled.Sync, "Refresh", tint = NeonCyan) }
                    }
                )
                if (toast != null) {
                    Surface(color = NeonCyan.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(toast.orEmpty(), color = NeonCyan, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.clearToast() }) { Text("OK", color = NeonCyan) }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = AdminNavy) {
                val tabs = listOf(
                    "Stats" to Icons.Filled.Dashboard,
                    "Users" to Icons.Filled.ManageAccounts,
                    "Notify" to Icons.Filled.Campaign,
                    "Moderation" to Icons.Filled.Shield,
                    "System" to Icons.Filled.SettingsApplications
                )
                tabs.forEachIndexed { idx, (label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, label) },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("admin_tab_$idx")
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(AdminBlack).padding(padding)) {
            when (selectedTab) {
                0 -> StatsTab(stats)
                1 -> UsersTab(users, viewModel)
                2 -> NotificationTab(users, groups, notifications, viewModel)
                3 -> ModerationTab(groups, communities, reports, viewModel)
                4 -> SystemSettingsTab(settings, logs, viewModel)
            }
        }
    }
}

@Composable
private fun StatsTab(stats: AdminDashboardStats) {
    val cards = listOf(
        Triple("Total Users", stats.totalUsers.toString(), Icons.Filled.People to NeonCyan),
        Triple("Active Users", stats.activeUsers.toString(), Icons.Filled.CheckCircle to NeonMint),
        Triple("Online Now", stats.onlineUsers.toString(), Icons.Filled.Wifi to NeonMint),
        Triple("Offline Users", stats.offlineUsers.toString(), Icons.Filled.WifiOff to Color(0xFF8FA7C6)),
        Triple("New Today", stats.newUsersToday.toString(), Icons.Filled.PersonAdd to NeonBlue),
        Triple("Total Messages", stats.totalMessages.toString(), Icons.Filled.Forum to NeonCyan),
        Triple("Total Groups", stats.totalGroups.toString(), Icons.Filled.Groups to NeonBlue),
        Triple("Communities", stats.totalCommunities.toString(), Icons.Filled.Hub to NeonCyan),
        Triple("Active Stories", stats.totalStories.toString(), Icons.Filled.AutoAwesome to AdminWarn),
        Triple("Total Calls", stats.totalCalls.toString(), Icons.Filled.Call to NeonMint),
        Triple("Verified Blue", stats.verifiedUsers.toString(), Icons.Filled.Verified to Color(0xFF1DA1F2)),
        Triple("Banned/Susp.", stats.blockedUsers.toString(), Icons.Filled.Block to AdminAlert)
    )

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Text("Live Platform Telemetry", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(Modifier.height(10.dp))
        PlatformBarChart(stats)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(cards) { (title, value, iconPair) ->
                StatCard(title = title, value = value, icon = iconPair.first, accent = iconPair.second)
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: ImageVector, accent: Color) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AdminNavyCard),
        modifier = Modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
                Text(value, fontFamily = AkFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlatformBarChart(stats: AdminDashboardStats) {
    val values = listOf(
        "Users" to stats.totalUsers.toFloat(),
        "Msgs" to stats.totalMessages.toFloat(),
        "Groups" to stats.totalGroups.toFloat(),
        "Stories" to stats.totalStories.toFloat(),
        "Calls" to stats.totalCalls.toFloat(),
        "Verified" to stats.verifiedUsers.toFloat()
    )
    val maxVal = (values.maxOfOrNull { it.second } ?: 1f).coerceAtLeast(1f)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AdminNavyCard),
        modifier = Modifier.fillMaxWidth().border(1.dp, NeonCyan.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
                val barW = size.width / (values.size * 2f)
                values.forEachIndexed { idx, (_, v) ->
                    val ratio = (v / maxVal).coerceIn(0.06f, 1f)
                    val h = size.height * ratio
                    val x = idx * (barW * 2f) + barW * 0.5f
                    drawRoundRect(
                        color = if (idx % 2 == 0) NeonCyan else NeonBlue,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                values.forEach { (label, _) ->
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UsersTab(users: List<UserProfile>, viewModel: AdminViewModel) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }

    val filtered = users.filter { u ->
        val q = query.trim().lowercase()
        val matchQ = q.isEmpty() || u.fullName.lowercase().contains(q) || u.username.lowercase().contains(q) ||
            u.phoneNumber.lowercase().contains(q) || u.uid.lowercase().contains(q)
        val matchF = when (filter) {
            "verified" -> u.isVerified
            "online" -> u.isOnline
            "restricted" -> u.status != "active"
            else -> true
        }
        matchQ && matchF
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by Name, @username, Phone, or UID") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = NeonCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("admin_user_search")
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            items(listOf("all", "verified", "online", "restricted")) { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.uppercase()) })
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.uid }) { user ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AdminNavyCard),
                    modifier = Modifier.fillMaxWidth().clickable { selectedUser = user }
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { UserAvatar(user.fullName, user.avatarBase64, isOnline = user.isOnline, accentColor = NeonCyan) },
                        headlineContent = { NameWithBadge(user.fullName, user.isVerified) },
                        supportingContent = {
                            Text("@${user.username} • ${user.phoneNumber}\nStatus: ${user.status.uppercase()} • UID: ${user.uid.take(10)}...", fontSize = 12.sp)
                        },
                        trailingContent = { Icon(Icons.Filled.ChevronRight, null, tint = NeonCyan) }
                    )
                }
            }
        }
    }

    selectedUser?.let { u ->
        var editedUsername by remember(u.uid) { mutableStateOf(u.username) }
        AlertDialog(
            onDismissRequest = { selectedUser = null },
            title = { NameWithBadge(u.fullName, u.isVerified) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("UID: ${u.uid}", fontFamily = AkFonts.JetBrainsMono, fontSize = 11.sp)
                    Text("Email: ${u.email}\nPhone: ${u.phoneNumber}\nStatus: ${u.status}\nRegistered: ${AkTimeFormatter.formatDateTime(u.createdAt)}", fontSize = 12.sp)
                    OutlinedTextField(
                        value = editedUsername,
                        onValueChange = { editedUsername = it },
                        label = { Text("Manage Username") },
                        singleLine = true
                    )
                    TextButton(onClick = {
                        viewModel.performAdminAction({ admin -> viewModel.repo.updateUsername(admin, u, editedUsername) }, "Username updated")
                        selectedUser = null
                    }) { Text("Update Username") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.performAdminAction({ admin -> viewModel.repo.setUserVerified(admin, u, !u.isVerified) }, "Verification updated")
                                selectedUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (u.isVerified) "Unverify" else "Verify Tick") }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AdminWarn, contentColor = AdminBlack),
                            onClick = {
                                val next = if (u.status == "suspended") "active" else "suspended"
                                viewModel.performAdminAction({ admin -> viewModel.repo.setUserStatus(admin, u, next) }, "Status -> $next")
                                selectedUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (u.status == "suspended") "Activate" else "Suspend") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AdminAlert),
                            onClick = {
                                val next = if (u.status == "banned") "active" else "banned"
                                viewModel.performAdminAction({ admin -> viewModel.repo.setUserStatus(admin, u, next) }, "Status -> $next")
                                selectedUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (u.status == "banned") "Unban" else "Ban User") }
                        OutlinedButton(
                            onClick = {
                                viewModel.performAdminAction({ admin -> viewModel.repo.deleteUserRecord(admin, u) }, "User record deleted")
                                selectedUser = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete", color = AdminAlert) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedUser = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun NotificationTab(
    users: List<UserProfile>,
    groups: List<GroupInfo>,
    notifications: List<AdminNotification>,
    viewModel: AdminViewModel
) {
    var title by remember { mutableStateOf("ADMIN OFFICIAL NOTICE") }
    var message by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("all") }
    var targetId by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Global Push Broadcast", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Notification Title") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Notification Message (বাংলা / English)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Optional Image URL") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All Users", "user" to "Selected User", "group" to "Selected Group").forEach { (key, label) ->
                    FilterChip(selected = targetType == key, onClick = { targetType = key; targetId = "" }, label = { Text(label) })
                }
            }
            if (targetType == "user") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(users, key = { it.uid }) { u ->
                        FilterChip(selected = targetId == u.uid, onClick = { targetId = u.uid }, label = { Text(u.fullName) })
                    }
                }
            } else if (targetType == "group") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.id }) { g ->
                        FilterChip(selected = targetId == g.id, onClick = { targetId = g.id }, label = { Text(g.name) })
                    }
                }
            }

            // Live Notification Bar Preview
            Card(
                colors = CardDefaults.cardColors(containerColor = AdminNavyCard),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("NOTIFICATION PREVIEW", fontFamily = AkFonts.JetBrainsMono, fontSize = 10.sp, color = NeonCyan)
                    Spacer(Modifier.height(4.dp))
                    Text("AK MESSENGER", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(title.ifBlank { "Title" }, fontWeight = FontWeight.Bold)
                    Text(message.ifBlank { "Your broadcast message preview..." }, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(
                onClick = {
                    viewModel.performAdminAction(
                        { admin -> viewModel.repo.sendNotification(admin, title, message, imageUrl, targetType, targetId) },
                        "Broadcast published to RTDB /notifications"
                    )
                    message = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AdminBlack),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_send_notification_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(8.dp))
                Text("Send Notification Now", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Broadcast History (${notifications.size})", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        }
        items(notifications.sortedByDescending { it.sentAt }, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = AdminNavyCard), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(n.title, fontWeight = FontWeight.Bold, color = NeonCyan)
                    Text(n.message, style = MaterialTheme.typography.bodySmall)
                    Text("Target: ${n.targetType} • ${AkTimeFormatter.formatDateTime(n.sentAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModerationTab(
    groups: List<GroupInfo>,
    communities: List<CommunityInfo>,
    reports: List<ReportItem>,
    viewModel: AdminViewModel
) {
    var subTab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Groups (${groups.size})", "Communities (${communities.size})", "Reports (${reports.size})").forEachIndexed { idx, label ->
                SegmentedButton(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 3)
                ) { Text(label, maxLines = 1, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            when (subTab) {
                0 -> items(groups, key = { it.id }) { g ->
                    Card(colors = CardDefaults.cardColors(containerColor = AdminNavyCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(g.name, fontWeight = FontWeight.Bold)
                            Text("${g.memberIds.size} members • Suspended: ${g.isSuspended}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    viewModel.performAdminAction({ a -> viewModel.repo.suspendGroup(a, g, !g.isSuspended) }, "Group updated")
                                }) { Text(if (g.isSuspended) "Unsuspend" else "Suspend") }
                                TextButton(onClick = {
                                    viewModel.performAdminAction({ a -> viewModel.repo.deleteGroup(a, g) }, "Group deleted")
                                }) { Text("Delete", color = AdminAlert) }
                            }
                        }
                    }
                }
                1 -> items(communities, key = { it.id }) { c ->
                    Card(colors = CardDefaults.cardColors(containerColor = AdminNavyCard), modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(c.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(c.description) },
                            trailingContent = {
                                IconButton(onClick = {
                                    viewModel.performAdminAction({ a -> viewModel.repo.deleteCommunity(a, c) }, "Community deleted")
                                }) { Icon(Icons.Filled.Delete, null, tint = AdminAlert) }
                            }
                        )
                    }
                }
                2 -> items(reports.sortedByDescending { it.createdAt }, key = { it.id }) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = AdminNavyCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("[${r.status.uppercase()}] ${r.targetType.uppercase()}: ${r.targetLabel}", fontWeight = FontWeight.Bold, color = if (r.status == "pending") AdminWarn else NeonMint)
                            Text("Reason: ${r.reason} • By: ${r.reporterName}", fontSize = 12.sp)
                            if (r.details.isNotBlank()) Text(r.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.status == "pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        viewModel.performAdminAction({ a -> viewModel.repo.resolveReport(a, r, "resolved", "Action taken by Admin") }, "Report resolved")
                                    }) { Text("Mark Resolved") }
                                    TextButton(onClick = {
                                        viewModel.performAdminAction({ a -> viewModel.repo.resolveReport(a, r, "dismissed", "Dismissed") }, "Report dismissed")
                                    }) { Text("Dismiss") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemSettingsTab(
    settings: AppSettings,
    logs: List<AdminLog>,
    viewModel: AdminViewModel
) {
    var subTab by remember { mutableIntStateOf(0) }
    var appName by remember(settings.appName) { mutableStateOf(settings.appName) }
    var maintenanceMode by remember(settings.maintenanceMode) { mutableStateOf(settings.maintenanceMode) }
    var maintenanceMsg by remember(settings.maintenanceMessage) { mutableStateOf(settings.maintenanceMessage) }
    var updateNotice by remember(settings.updateNotice) { mutableStateOf(settings.updateNotice) }
    var supportContact by remember(settings.supportContact) { mutableStateOf(settings.supportContact) }
    var privacyPolicy by remember(settings.privacyPolicy) { mutableStateOf(settings.privacyPolicy) }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("App Settings", "Security Logs (${logs.size})").forEachIndexed { idx, label ->
                SegmentedButton(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    shape = SegmentedButtonDefaults.itemShape(idx, 2)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (subTab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                item {
                    OutlinedTextField(value = appName, onValueChange = { appName = it }, label = { Text("App Name") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text("Maintenance Mode (Locks User App)", fontWeight = FontWeight.SemiBold)
                        Switch(checked = maintenanceMode, onCheckedChange = { maintenanceMode = it })
                    }
                    OutlinedTextField(value = maintenanceMsg, onValueChange = { maintenanceMsg = it }, label = { Text("Maintenance Message") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = updateNotice, onValueChange = { updateNotice = it }, label = { Text("App Update Notice") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = supportContact, onValueChange = { supportContact = it }, label = { Text("Support Contact") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = privacyPolicy, onValueChange = { privacyPolicy = it }, label = { Text("Privacy Policy") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.performAdminAction({ a ->
                                viewModel.repo.updateAppSettings(
                                    a,
                                    settings.copy(
                                        appName = appName,
                                        maintenanceMode = maintenanceMode,
                                        maintenanceMessage = maintenanceMsg,
                                        updateNotice = updateNotice,
                                        supportContact = supportContact,
                                        privacyPolicy = privacyPolicy
                                    )
                                )
                            }, "Global app_settings saved")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = AdminBlack),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Save Global App Settings", fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(logs.sortedByDescending { it.timestamp }, key = { it.id }) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = AdminNavyCard), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${log.action} (${if (log.success) "OK" else "FAILED"})",
                                fontFamily = AkFonts.JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                                color = if (log.success) NeonMint else AdminAlert
                            )
                            Text("${log.adminEmail} • ${log.details}", fontSize = 12.sp)
                            Text(AkTimeFormatter.formatDateTime(log.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
