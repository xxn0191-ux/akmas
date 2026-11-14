package com.aistudio.akmessenger.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val username: String = "",
    val phoneNumber: String = "",
    val email: String = "",
    val about: String = "Hey there! I am using AK Messenger.",
    val avatarBase64: String = "",
    val avatarUrl: String = "",
    val publicKey: String = "",
    val fcmToken: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val isVerified: Boolean = false,
    val status: String = "active", // active | suspended | banned
    val privacyLastSeen: String = "everyone", // everyone | contacts | nobody
    val privacyProfilePhoto: String = "everyone",
    val privacyAbout: String = "everyone",
    val createdAt: Long = 0L
)

@Serializable
data class ChatThread(
    val id: String = "",
    val title: String = "",
    val participantIds: Map<String, Boolean> = emptyMap(),
    val participantNames: Map<String, String> = emptyMap(),
    val isGroup: Boolean = false,
    val groupId: String = "",
    val lastMessagePreview: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCounts: Map<String, Int> = emptyMap(),
    val archivedBy: Map<String, Boolean> = emptyMap(),
    val mutedBy: Map<String, Boolean> = emptyMap(),
    val pinnedBy: Map<String, Boolean> = emptyMap(),
    val disappearingSeconds: Long = 0L,
    val blockedBy: Map<String, Boolean> = emptyMap()
)

@Serializable
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val type: String = "text", // text | image | video | voice | document | sticker | gif | call_event
    val ciphertext: String = "",
    val iv: String = "",
    val isEncrypted: Boolean = false,
    val plaintextFallback: String = "",
    val mediaBase64: String = "",
    val mediaMimeType: String = "",
    val fileName: String = "",
    val fileSizeBytes: Long = 0L,
    val voiceDurationSec: Int = 0,
    val replyToId: String = "",
    val replyToPreview: String = "",
    val forwardedFrom: String = "",
    val reactions: Map<String, String> = emptyMap(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0L,
    val deletedFor: Map<String, Boolean> = emptyMap(),
    val deletedForEveryone: Boolean = false,
    val isPinned: Boolean = false,
    val starredBy: Map<String, Boolean> = emptyMap(),
    val status: String = "sent", // sent | delivered | seen
    val expiresAt: Long = 0L,
    val timestamp: Long = 0L
)

@Serializable
data class GroupInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val avatarBase64: String = "",
    val communityId: String = "",
    val creatorId: String = "",
    val adminIds: Map<String, Boolean> = emptyMap(),
    val memberIds: Map<String, Boolean> = emptyMap(),
    val inviteCode: String = "",
    val announcement: String = "",
    val isSuspended: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class CommunityInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val creatorId: String = "",
    val adminIds: Map<String, Boolean> = emptyMap(),
    val groupIds: Map<String, Boolean> = emptyMap(),
    val announcementChatId: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class StoryItem(
    val id: String = "",
    val uid: String = "",
    val authorName: String = "",
    val authorUsername: String = "",
    val authorVerified: Boolean = false,
    val type: String = "text", // text | image | video
    val textContent: String = "",
    val bgColorHex: String = "#0B6E5B",
    val mediaBase64: String = "",
    val caption: String = "",
    val privacy: String = "everyone", // everyone | contacts | private
    val viewers: Map<String, Long> = emptyMap(),
    val reactions: Map<String, String> = emptyMap(),
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L
)

@Serializable
data class CallSession(
    val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeIds: Map<String, Boolean> = emptyMap(),
    val groupId: String = "",
    val type: String = "audio", // audio | video
    val isGroup: Boolean = false,
    val status: String = "ringing", // ringing | active | ended | rejected | missed
    val offerSdp: String = "",
    val answerSdp: String = "",
    val screenSharing: Boolean = false,
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val durationSec: Long = 0L
)

@Serializable
data class AdminNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val imageUrl: String = "",
    val targetType: String = "all", // all | user | group
    val targetId: String = "",
    val sentByAdminUid: String = "",
    val sentAt: Long = 0L
)

@Serializable
data class ReportItem(
    val id: String = "",
    val reporterUid: String = "",
    val reporterName: String = "",
    val targetType: String = "user", // user | message | story | group | profile
    val targetId: String = "",
    val targetLabel: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending", // pending | resolved | dismissed
    val resolutionNote: String = "",
    val resolvedBy: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class AdminRecord(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val role: String = "super_admin",
    val twoFactorPinHash: String = "",
    val lastLoginAt: Long = 0L
)

@Serializable
data class AdminLog(
    val id: String = "",
    val adminUid: String = "",
    val adminEmail: String = "",
    val action: String = "",
    val targetId: String = "",
    val details: String = "",
    val success: Boolean = true,
    val timestamp: Long = 0L
)

@Serializable
data class AppSettings(
    val appName: String = "AK Messenger",
    val logoText: String = "AK MESSENGER",
    val accentTheme: String = "emerald", // emerald | ocean | royal
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "Scheduled maintenance in progress. Please check back shortly.",
    val updateNotice: String = "",
    val privacyPolicy: String = "AK Messenger protects your 1:1 messages with ECDH P-256 + AES-256-GCM end-to-end encryption.",
    val termsAndConditions: String = "Use AK Messenger responsibly. Harassment, spam, and impersonation result in account suspension.",
    val supportContact: String = "support@akmessenger.app"
)
