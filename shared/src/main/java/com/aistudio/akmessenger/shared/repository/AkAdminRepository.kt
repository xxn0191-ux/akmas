package com.aistudio.akmessenger.shared.repository

import com.aistudio.akmessenger.shared.crypto.E2eeCryptoManager
import com.aistudio.akmessenger.shared.model.*
import kotlinx.serialization.encodeToString
import java.util.UUID

data class AdminDashboardStats(
    val totalUsers: Int = 0,
    val activeUsers: Int = 0,
    val onlineUsers: Int = 0,
    val offlineUsers: Int = 0,
    val newUsersToday: Int = 0,
    val totalMessages: Int = 0,
    val totalGroups: Int = 0,
    val totalCommunities: Int = 0,
    val totalStories: Int = 0,
    val totalCalls: Int = 0,
    val verifiedUsers: Int = 0,
    val blockedUsers: Int = 0
)

class AkAdminRepository(val sync: AkSyncEngine) {

    suspend fun loginAdmin(
        email: String,
        password: String,
        twoFactorPin: String
    ): Result<AdminRecord> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter admin email and password."))
        }
        val session = sync.authClient.signInWithEmail(email.trim(), password).getOrElse { err ->
            logAction("", email.trim(), "ADMIN_LOGIN_FAILED", email.trim(), err.message ?: "Auth failed", false, null)
            return Result.failure(err)
        }

        // Server-side Admin role verification in /admins/{uid}
        val adminRecord = sync.rtdbClient.get<AdminRecord>("admins/${session.uid}", session.idToken)
            .getOrElse { err ->
                logAction(session.uid, email, "ADMIN_VERIFY_ERROR", session.uid, err.message ?: "RTDB error", false, session.idToken)
                return Result.failure(IllegalStateException("Unable to verify admin role: ${err.message}"))
            }

        if (adminRecord == null) {
            logAction(session.uid, email, "UNAUTHORIZED_ADMIN_ACCESS", session.uid, "Regular user attempted admin login", false, session.idToken)
            return Result.failure(IllegalStateException("Access Denied: Account is not registered under /admins/${session.uid} in Firebase RTDB."))
        }

        if (adminRecord.twoFactorPinHash.isNotBlank()) {
            val providedHash = E2eeCryptoManager.sha256Hex(twoFactorPin.trim())
            if (providedHash != adminRecord.twoFactorPinHash) {
                logAction(session.uid, email, "ADMIN_2FA_FAILED", session.uid, "Invalid 2-step verification PIN", false, session.idToken)
                return Result.failure(IllegalStateException("Invalid Two-Step Verification PIN."))
            }
        }

        sync.activeToken = session.idToken
        sync.prefs.saveAuth(session.uid, email.trim(), session.idToken, session.refreshToken)
        logAction(session.uid, email.trim(), "ADMIN_LOGIN_SUCCESS", session.uid, "Admin session started", true, session.idToken)
        return Result.success(adminRecord.copy(uid = session.uid, email = email.trim()))
    }

    suspend fun logoutAdmin(admin: AdminRecord) {
        logAction(admin.uid, admin.email, "ADMIN_LOGOUT", admin.uid, "Admin signed out", true, sync.activeToken)
        sync.stopRealtimeListener()
        sync.activeToken = null
        sync.prefs.clearAuth()
    }

    suspend fun setUserVerified(admin: AdminRecord, user: UserProfile, verified: Boolean): Result<Unit> {
        val updated = user.copy(isVerified = verified)
        sync.upsertLocal("users", user.uid, sync.json.encodeToString(updated), user.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("users/${user.uid}", updated, token)
        if (verified) {
            sync.rtdbClient.put("verified_users/${user.uid}", mapOf("verifiedAt" to System.currentTimeMillis(), "by" to admin.uid), token)
        } else {
            sync.rtdbClient.delete("verified_users/${user.uid}", token)
        }
        logAction(admin.uid, admin.email, if (verified) "VERIFY_USER" else "UNVERIFY_USER", user.uid, "@${user.username}", res.isSuccess, token)
        return res
    }

    suspend fun setUserStatus(admin: AdminRecord, user: UserProfile, status: String): Result<Unit> {
        val updated = user.copy(status = status, isOnline = if (status != "active") false else user.isOnline)
        sync.upsertLocal("users", user.uid, sync.json.encodeToString(updated), user.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("users/${user.uid}", updated, token)
        logAction(admin.uid, admin.email, "USER_STATUS_$status", user.uid, "@${user.username}", res.isSuccess, token)
        return res
    }

    suspend fun updateUsername(admin: AdminRecord, user: UserProfile, newUsernameRaw: String): Result<Unit> {
        val newUsername = newUsernameRaw.trim().lowercase().removePrefix("@")
        if (newUsername.length < 3) return Result.failure(IllegalArgumentException("Username too short"))
        val token = sync.activeToken
        if (user.username.isNotBlank()) sync.rtdbClient.delete("usernames/${user.username}", token)
        sync.rtdbClient.put("usernames/$newUsername", user.uid, token)
        val updated = user.copy(username = newUsername)
        sync.upsertLocal("users", user.uid, sync.json.encodeToString(updated), user.createdAt)
        val res = sync.rtdbClient.put("users/${user.uid}", updated, token)
        logAction(admin.uid, admin.email, "UPDATE_USERNAME", user.uid, "${user.username} -> $newUsername", res.isSuccess, token)
        return res
    }

    suspend fun deleteUserRecord(admin: AdminRecord, user: UserProfile): Result<Unit> {
        val token = sync.activeToken
        if (user.username.isNotBlank()) sync.rtdbClient.delete("usernames/${user.username}", token)
        sync.rtdbClient.delete("verified_users/${user.uid}", token)
        val res = sync.rtdbClient.delete("users/${user.uid}", token)
        if (res.isSuccess) sync.deleteLocal("users", user.uid)
        logAction(admin.uid, admin.email, "DELETE_USER", user.uid, "@${user.username}", res.isSuccess, token)
        return res
    }

    suspend fun sendNotification(
        admin: AdminRecord,
        title: String,
        message: String,
        imageUrl: String,
        targetType: String,
        targetId: String
    ): Result<AdminNotification> {
        if (title.isBlank() || message.isBlank()) {
            return Result.failure(IllegalArgumentException("Title and message are required."))
        }
        val now = System.currentTimeMillis()
        val id = "notif_${now}_${UUID.randomUUID().toString().take(5)}"
        val notif = AdminNotification(
            id = id,
            title = title.trim(),
            message = message.trim(),
            imageUrl = imageUrl.trim(),
            targetType = targetType,
            targetId = targetId.trim(),
            sentByAdminUid = admin.uid,
            sentAt = now
        )
        sync.upsertLocal("notifications", id, sync.json.encodeToString(notif), now)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("notifications/$id", notif, token)
        logAction(admin.uid, admin.email, "SEND_NOTIFICATION", targetType, title.trim(), res.isSuccess, token)
        return res.map { notif }
    }

    suspend fun removeGroupMember(admin: AdminRecord, group: GroupInfo, memberUid: String): Result<Unit> {
        val newMembers = group.memberIds.toMutableMap().apply { remove(memberUid) }
        val newAdmins = group.adminIds.toMutableMap().apply { remove(memberUid) }
        val updated = group.copy(memberIds = newMembers, adminIds = newAdmins)
        sync.upsertLocal("groups", group.id, sync.json.encodeToString(updated), group.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("groups/${group.id}", updated, token)
        logAction(admin.uid, admin.email, "REMOVE_GROUP_MEMBER", group.id, "Removed $memberUid", res.isSuccess, token)
        return res
    }

    suspend fun suspendGroup(admin: AdminRecord, group: GroupInfo, suspended: Boolean): Result<Unit> {
        val updated = group.copy(isSuspended = suspended)
        sync.upsertLocal("groups", group.id, sync.json.encodeToString(updated), group.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("groups/${group.id}", updated, token)
        logAction(admin.uid, admin.email, if (suspended) "SUSPEND_GROUP" else "UNSUSPEND_GROUP", group.id, group.name, res.isSuccess, token)
        return res
    }

    suspend fun deleteGroup(admin: AdminRecord, group: GroupInfo): Result<Unit> {
        val token = sync.activeToken
        val res = sync.rtdbClient.delete("groups/${group.id}", token)
        if (res.isSuccess) {
            sync.deleteLocal("groups", group.id)
            sync.rtdbClient.delete("chats/${group.id}", token)
            sync.deleteLocal("chats", group.id)
        }
        logAction(admin.uid, admin.email, "DELETE_GROUP", group.id, group.name, res.isSuccess, token)
        return res
    }

    suspend fun updateGroupAnnouncement(admin: AdminRecord, group: GroupInfo, announcement: String): Result<Unit> {
        val updated = group.copy(announcement = announcement.trim())
        sync.upsertLocal("groups", group.id, sync.json.encodeToString(updated), group.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("groups/${group.id}", updated, token)
        logAction(admin.uid, admin.email, "GROUP_ANNOUNCEMENT", group.id, announcement.take(40), res.isSuccess, token)
        return res
    }

    suspend fun deleteCommunity(admin: AdminRecord, community: CommunityInfo): Result<Unit> {
        val token = sync.activeToken
        val res = sync.rtdbClient.delete("communities/${community.id}", token)
        if (res.isSuccess) sync.deleteLocal("communities", community.id)
        logAction(admin.uid, admin.email, "DELETE_COMMUNITY", community.id, community.name, res.isSuccess, token)
        return res
    }

    suspend fun resolveReport(admin: AdminRecord, report: ReportItem, status: String, note: String): Result<Unit> {
        val updated = report.copy(status = status, resolutionNote = note.trim(), resolvedBy = admin.email)
        sync.upsertLocal("reports", report.id, sync.json.encodeToString(updated), report.createdAt)
        val token = sync.activeToken
        val res = sync.rtdbClient.put("reports/${report.id}", updated, token)
        logAction(admin.uid, admin.email, "REPORT_$status", report.id, note.take(40), res.isSuccess, token)
        return res
    }

    suspend fun updateAppSettings(admin: AdminRecord, settings: AppSettings): Result<Unit> {
        val token = sync.activeToken
        val res = sync.rtdbClient.put("app_settings", settings, token)
        logAction(admin.uid, admin.email, "UPDATE_APP_SETTINGS", "app_settings", "Maintenance=${settings.maintenanceMode}", res.isSuccess, token)
        return res
    }

    suspend fun logAction(
        adminUid: String,
        adminEmail: String,
        action: String,
        targetId: String,
        details: String,
        success: Boolean,
        token: String?
    ) {
        val now = System.currentTimeMillis()
        val id = "log_${now}_${UUID.randomUUID().toString().take(5)}"
        val log = AdminLog(id, adminUid, adminEmail, action, targetId, details, success, now)
        sync.upsertLocal("admin_logs", id, sync.json.encodeToString(log), now)
        if (!token.isNullOrBlank()) {
            sync.rtdbClient.put("admin_logs/$id", log, token)
        }
    }

    fun computeStats(
        users: List<UserProfile>,
        totalMessages: Int,
        groups: List<GroupInfo>,
        communities: List<CommunityInfo>,
        stories: List<StoryItem>,
        calls: List<CallSession>
    ): AdminDashboardStats {
        val dayAgo = System.currentTimeMillis() - 86_400_000L
        return AdminDashboardStats(
            totalUsers = users.size,
            activeUsers = users.count { it.status == "active" },
            onlineUsers = users.count { it.isOnline },
            offlineUsers = users.count { !it.isOnline },
            newUsersToday = users.count { it.createdAt >= dayAgo },
            totalMessages = totalMessages,
            totalGroups = groups.size,
            totalCommunities = communities.size,
            totalStories = stories.size,
            totalCalls = calls.size,
            verifiedUsers = users.count { it.isVerified },
            blockedUsers = users.count { it.status == "banned" || it.status == "suspended" }
        )
    }
}
