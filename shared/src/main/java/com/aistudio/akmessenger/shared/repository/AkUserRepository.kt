package com.aistudio.akmessenger.shared.repository

import com.aistudio.akmessenger.shared.crypto.E2eeCryptoManager
import com.aistudio.akmessenger.shared.firebase.AuthSession
import com.aistudio.akmessenger.shared.model.*
import kotlinx.serialization.encodeToString
import java.util.UUID

class AkUserRepository(val sync: AkSyncEngine) {

    suspend fun registerUser(
        fullName: String,
        usernameRaw: String,
        phoneNumber: String,
        email: String,
        password: String,
        avatarBase64: String
    ): Result<UserProfile> {
        val username = usernameRaw.trim().lowercase().removePrefix("@")
        if (username.length < 3 || !username.matches(Regex("^[a-z0-9_]+$"))) {
            return Result.failure(IllegalArgumentException("Username must be at least 3 chars (a-z, 0-9, _)."))
        }
        if (fullName.isBlank() || phoneNumber.isBlank() || email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Fill all fields (password min 6 chars)."))
        }

        val authRes = sync.authClient.signUpWithEmail(email, password)
        val session: AuthSession = authRes.getOrElse { return Result.failure(it) }
        sync.activeToken = session.idToken

        // Check unique username in RTDB
        val existingOwner = sync.rtdbClient.get<String>("usernames/$username", session.idToken).getOrNull()
        if (!existingOwner.isNullOrBlank() && existingOwner != session.uid) {
            sync.authClient.deleteAccount(session.idToken)
            return Result.failure(IllegalStateException("Username @$username is already taken."))
        }

        val (pubKey, privKey) = E2eeCryptoManager.generateIdentityKeyPair()
        val now = System.currentTimeMillis()
        val profile = UserProfile(
            uid = session.uid,
            fullName = fullName.trim(),
            username = username,
            phoneNumber = phoneNumber.trim(),
            email = email.trim(),
            avatarBase64 = avatarBase64,
            publicKey = pubKey,
            isOnline = true,
            lastSeen = now,
            isVerified = false,
            status = "active",
            createdAt = now
        )

        sync.prefs.saveAuth(session.uid, email.trim(), session.idToken, session.refreshToken)
        sync.prefs.saveKeyPair(pubKey, privKey)

        // Write to RTDB + local Room
        sync.rtdbClient.put("usernames/$username", session.uid, session.idToken)
            .onFailure { return Result.failure(it) }
        sync.rtdbClient.put("users/${session.uid}", profile, session.idToken)
            .onFailure { return Result.failure(it) }
        sync.upsertLocal("users", session.uid, sync.json.encodeToString(profile), now)
        return Result.success(profile)
    }

    suspend fun loginUser(email: String, password: String): Result<UserProfile> {
        val session = sync.authClient.signInWithEmail(email, password)
            .getOrElse { return Result.failure(it) }
        sync.activeToken = session.idToken

        val profile = sync.rtdbClient.get<UserProfile>("users/${session.uid}", session.idToken)
            .getOrElse { return Result.failure(it) }
            ?: UserProfile(uid = session.uid, email = email.trim(), fullName = email.substringBefore("@"))

        if (profile.status == "banned") {
            return Result.failure(IllegalStateException("Your account has been banned by AK Messenger Admin."))
        }
        if (profile.status == "suspended") {
            return Result.failure(IllegalStateException("Your account is currently suspended. Contact support."))
        }

        var stored = sync.prefs.current()
        if (stored.e2eePrivateKey.isBlank() || stored.e2eePublicKey.isBlank()) {
            val (pub, priv) = E2eeCryptoManager.generateIdentityKeyPair()
            sync.prefs.saveKeyPair(pub, priv)
            stored = sync.prefs.current()
        }

        val updated = profile.copy(
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            publicKey = stored.e2eePublicKey.ifBlank { profile.publicKey }
        )
        sync.prefs.saveAuth(session.uid, email.trim(), session.idToken, session.refreshToken)
        sync.rtdbClient.put("users/${session.uid}", updated, session.idToken)
        sync.upsertLocal("users", session.uid, sync.json.encodeToString(updated))
        return Result.success(updated)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> =
        sync.authClient.sendPasswordResetEmail(email)

    suspend fun logout(uid: String) {
        val token = sync.activeToken
        if (uid.isNotBlank() && !token.isNullOrBlank()) {
            sync.rtdbClient.patchRaw(
                "users/$uid",
                """{"isOnline":false,"lastSeen":${System.currentTimeMillis()}}""",
                token
            )
        }
        sync.stopRealtimeListener()
        sync.activeToken = null
        sync.prefs.clearAuth()
    }

    suspend fun deleteAccount(uid: String, username: String): Result<Unit> {
        val token = sync.activeToken ?: return Result.failure(IllegalStateException("Not authenticated"))
        if (username.isNotBlank()) sync.rtdbClient.delete("usernames/$username", token)
        sync.rtdbClient.delete("users/$uid", token)
        val res = sync.authClient.deleteAccount(token)
        if (res.isSuccess) {
            sync.stopRealtimeListener()
            sync.activeToken = null
            sync.prefs.clearAuth()
        }
        return res
    }

    suspend fun updateProfile(profile: UserProfile): Result<Unit> {
        val token = sync.activeToken
        sync.upsertLocal("users", profile.uid, sync.json.encodeToString(profile))
        return sync.rtdbClient.put("users/${profile.uid}", profile, token)
    }

    suspend fun setOnlinePresence(uid: String, online: Boolean) {
        val token = sync.activeToken ?: return
        sync.rtdbClient.patchRaw(
            "users/$uid",
            """{"isOnline":$online,"lastSeen":${System.currentTimeMillis()}}""",
            token
        )
    }

    suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        val token = sync.activeToken ?: return
        sync.rtdbClient.put("typing/$chatId/$uid", isTyping, token)
    }

    suspend fun getOrCreateDirectChat(me: UserProfile, peer: UserProfile): Result<ChatThread> {
        val chatId = listOf(me.uid, peer.uid).sorted().joinToString("_")
        val existing = sync.rtdbClient.get<ChatThread>("chats/$chatId", sync.activeToken).getOrNull()
        if (existing != null) return Result.success(existing.copy(id = chatId))

        val thread = ChatThread(
            id = chatId,
            title = peer.fullName,
            participantIds = mapOf(me.uid to true, peer.uid to true),
            participantNames = mapOf(me.uid to me.fullName, peer.uid to peer.fullName),
            isGroup = false,
            lastMessageTimestamp = System.currentTimeMillis()
        )
        sync.upsertLocal("chats", chatId, sync.json.encodeToString(thread))
        val res = sync.rtdbClient.put("chats/$chatId", thread, sync.activeToken)
        return res.map { thread }
    }

    suspend fun sendMessage(
        chat: ChatThread,
        sender: UserProfile,
        peerPublicKey: String,
        type: String,
        plaintext: String,
        mediaBase64: String = "",
        mediaMimeType: String = "",
        fileName: String = "",
        fileSizeBytes: Long = 0L,
        voiceDurationSec: Int = 0,
        replyTo: ChatMessage? = null,
        forwardedFrom: String = ""
    ): Result<ChatMessage> {
        val now = System.currentTimeMillis()
        val msgId = "msg_${now}_${UUID.randomUUID().toString().take(6)}"
        val myPrivKey = sync.prefs.current().e2eePrivateKey

        val shouldEncrypt = !chat.isGroup && type == "text" && myPrivKey.isNotBlank() && peerPublicKey.isNotBlank()
        val encrypted = if (shouldEncrypt) {
            E2eeCryptoManager.encrypt(plaintext, myPrivKey, peerPublicKey)
        } else null

        val expiresAt = if (chat.disappearingSeconds > 0L) now + chat.disappearingSeconds * 1000L else 0L
        val msg = ChatMessage(
            id = msgId,
            chatId = chat.id,
            senderId = sender.uid,
            senderName = sender.fullName,
            type = type,
            ciphertext = encrypted?.ciphertextBase64.orEmpty(),
            iv = encrypted?.ivBase64.orEmpty(),
            isEncrypted = encrypted != null,
            plaintextFallback = if (encrypted == null) plaintext else "",
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            voiceDurationSec = voiceDurationSec,
            replyToId = replyTo?.id.orEmpty(),
            replyToPreview = replyTo?.let {
                if (it.plaintextFallback.isNotBlank()) it.plaintextFallback.take(60) else "[${it.type}]"
            }.orEmpty(),
            forwardedFrom = forwardedFrom,
            status = "sent",
            expiresAt = expiresAt,
            timestamp = now
        )

        val previewText = when (type) {
            "text" -> if (encrypted != null) "Encrypted message" else plaintext.take(60)
            "voice" -> "Voice message (${voiceDurationSec}s)"
            "image" -> "Photo"
            "video" -> "Video"
            "document" -> "Document: $fileName"
            "sticker" -> "Sticker"
            "gif" -> "GIF"
            else -> plaintext.take(60)
        }

        val updatedUnread = chat.unreadCounts.toMutableMap()
        chat.participantIds.keys.filter { it != sender.uid }.forEach { uid ->
            updatedUnread[uid] = (updatedUnread[uid] ?: 0) + 1
        }
        val updatedChat = chat.copy(
            lastMessagePreview = previewText,
            lastMessageSenderId = sender.uid,
            lastMessageTimestamp = now,
            unreadCounts = updatedUnread
        )

        sync.upsertLocal("msg_${chat.id}", msgId, sync.json.encodeToString(msg), now)
        sync.upsertLocal("chats", chat.id, sync.json.encodeToString(updatedChat), now)

        val token = sync.activeToken
        sync.rtdbClient.put("messages/${chat.id}/$msgId", msg, token)
            .onFailure { return Result.failure(it) }
        sync.rtdbClient.put("chats/${chat.id}", updatedChat, token)
        return Result.success(msg)
    }

    fun decryptMessageText(msg: ChatMessage, myPrivKey: String, peerPublicKey: String): String {
        if (msg.deletedForEveryone) return "This message was deleted"
        if (!msg.isEncrypted) return msg.plaintextFallback
        if (myPrivKey.isBlank() || peerPublicKey.isBlank()) return "[Encrypted message]"
        return E2eeCryptoManager.decrypt(msg.ciphertext, msg.iv, myPrivKey, peerPublicKey)
            ?: "[Unable to decrypt with current key]"
    }

    suspend fun updateMessage(chatId: String, msg: ChatMessage): Result<Unit> {
        sync.upsertLocal("msg_$chatId", msg.id, sync.json.encodeToString(msg), msg.timestamp)
        return sync.rtdbClient.put("messages/$chatId/${msg.id}", msg, sync.activeToken)
    }

    suspend fun updateChatThread(chat: ChatThread): Result<Unit> {
        sync.upsertLocal("chats", chat.id, sync.json.encodeToString(chat), chat.lastMessageTimestamp)
        return sync.rtdbClient.put("chats/${chat.id}", chat, sync.activeToken)
    }

    suspend fun markChatSeen(chat: ChatThread, myUid: String, messages: List<ChatMessage>) {
        val token = sync.activeToken ?: return
        if ((chat.unreadCounts[myUid] ?: 0) > 0) {
            val map = chat.unreadCounts.toMutableMap().apply { this[myUid] = 0 }
            updateChatThread(chat.copy(unreadCounts = map))
        }
        messages.filter { it.senderId != myUid && it.status != "seen" }.forEach { m ->
            val seen = m.copy(status = "seen")
            sync.upsertLocal("msg_${chat.id}", m.id, sync.json.encodeToString(seen), m.timestamp)
            sync.rtdbClient.patchRaw("messages/${chat.id}/${m.id}", """{"status":"seen"}""", token)
        }
    }

    suspend fun createGroup(
        name: String,
        description: String,
        creator: UserProfile,
        memberProfiles: List<UserProfile>,
        communityId: String = ""
    ): Result<GroupInfo> {
        val now = System.currentTimeMillis()
        val groupId = "grp_${now}_${UUID.randomUUID().toString().take(5)}"
        val members = (memberProfiles + creator).distinctBy { it.uid }
        val memberMap = members.associate { it.uid to true }
        val nameMap = members.associate { it.uid to it.fullName }

        val group = GroupInfo(
            id = groupId,
            name = name.trim(),
            description = description.trim(),
            communityId = communityId,
            creatorId = creator.uid,
            adminIds = mapOf(creator.uid to true),
            memberIds = memberMap,
            inviteCode = "AK-${UUID.randomUUID().toString().take(8).uppercase()}",
            announcement = "Welcome to ${name.trim()}!",
            createdAt = now
        )
        val groupChat = ChatThread(
            id = groupId,
            title = name.trim(),
            participantIds = memberMap,
            participantNames = nameMap,
            isGroup = true,
            groupId = groupId,
            lastMessagePreview = "Group created by ${creator.fullName}",
            lastMessageSenderId = creator.uid,
            lastMessageTimestamp = now
        )

        sync.upsertLocal("groups", groupId, sync.json.encodeToString(group), now)
        sync.upsertLocal("chats", groupId, sync.json.encodeToString(groupChat), now)
        val token = sync.activeToken
        sync.rtdbClient.put("groups/$groupId", group, token).onFailure { return Result.failure(it) }
        sync.rtdbClient.put("chats/$groupId", groupChat, token)
        return Result.success(group)
    }

    suspend fun updateGroup(group: GroupInfo, updatedChat: ChatThread? = null): Result<Unit> {
        sync.upsertLocal("groups", group.id, sync.json.encodeToString(group), group.createdAt)
        val res = sync.rtdbClient.put("groups/${group.id}", group, sync.activeToken)
        if (updatedChat != null) updateChatThread(updatedChat)
        return res
    }

    suspend fun createCommunity(
        name: String,
        description: String,
        creator: UserProfile
    ): Result<CommunityInfo> {
        val now = System.currentTimeMillis()
        val commId = "comm_${now}_${UUID.randomUUID().toString().take(5)}"
        val announcementGroup = createGroup("$name Announcements", description, creator, emptyList(), commId)
            .getOrElse { return Result.failure(it) }
        val community = CommunityInfo(
            id = commId,
            name = name.trim(),
            description = description.trim(),
            creatorId = creator.uid,
            adminIds = mapOf(creator.uid to true),
            groupIds = mapOf(announcementGroup.id to true),
            announcementChatId = announcementGroup.id,
            createdAt = now
        )
        sync.upsertLocal("communities", commId, sync.json.encodeToString(community), now)
        val res = sync.rtdbClient.put("communities/$commId", community, sync.activeToken)
        return res.map { community }
    }

    suspend fun postStory(
        author: UserProfile,
        type: String,
        textContent: String,
        bgColorHex: String,
        mediaBase64: String,
        caption: String,
        privacy: String
    ): Result<StoryItem> {
        val now = System.currentTimeMillis()
        val storyId = "st_${now}_${UUID.randomUUID().toString().take(5)}"
        val story = StoryItem(
            id = storyId,
            uid = author.uid,
            authorName = author.fullName,
            authorUsername = author.username,
            authorVerified = author.isVerified,
            type = type,
            textContent = textContent,
            bgColorHex = bgColorHex,
            mediaBase64 = mediaBase64,
            caption = caption,
            privacy = privacy,
            createdAt = now,
            expiresAt = now + 86_400_000L
        )
        sync.upsertLocal("stories", storyId, sync.json.encodeToString(story), now)
        val res = sync.rtdbClient.put("stories/$storyId", story, sync.activeToken)
        return res.map { story }
    }

    suspend fun updateStory(story: StoryItem): Result<Unit> {
        sync.upsertLocal("stories", story.id, sync.json.encodeToString(story), story.createdAt)
        return sync.rtdbClient.put("stories/${story.id}", story, sync.activeToken)
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        sync.deleteLocal("stories", storyId)
        return sync.rtdbClient.delete("stories/$storyId", sync.activeToken)
    }

    suspend fun startCall(
        caller: UserProfile,
        callees: List<UserProfile>,
        type: String,
        isGroup: Boolean,
        groupId: String = ""
    ): Result<CallSession> {
        val now = System.currentTimeMillis()
        val callId = "call_${now}_${UUID.randomUUID().toString().take(5)}"
        val session = CallSession(
            id = callId,
            callerId = caller.uid,
            callerName = caller.fullName,
            calleeIds = callees.associate { it.uid to true },
            groupId = groupId,
            type = type,
            isGroup = isGroup,
            status = "ringing",
            offerSdp = "v=0\r\no=akmessenger $now 2 IN IP4 0.0.0.0\r\ns=AK-$type",
            startedAt = now
        )
        sync.upsertLocal("calls", callId, sync.json.encodeToString(session), now)
        val res = sync.rtdbClient.put("calls/$callId", session, sync.activeToken)
        return res.map { session }
    }

    suspend fun updateCall(call: CallSession): Result<Unit> {
        sync.upsertLocal("calls", call.id, sync.json.encodeToString(call), call.startedAt)
        return sync.rtdbClient.put("calls/${call.id}", call, sync.activeToken)
    }

    suspend fun submitReport(
        reporter: UserProfile,
        targetType: String,
        targetId: String,
        targetLabel: String,
        reason: String,
        details: String
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val id = "rep_${now}_${UUID.randomUUID().toString().take(5)}"
        val report = ReportItem(
            id = id,
            reporterUid = reporter.uid,
            reporterName = reporter.fullName,
            targetType = targetType,
            targetId = targetId,
            targetLabel = targetLabel,
            reason = reason,
            details = details,
            status = "pending",
            createdAt = now
        )
        sync.upsertLocal("reports", id, sync.json.encodeToString(report), now)
        return sync.rtdbClient.put("reports/$id", report, sync.activeToken)
    }
}
