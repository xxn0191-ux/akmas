const { onValueCreated } = require("firebase-functions/v2/database");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");

admin.initializeApp();

/**
 * Send FCM Push Notification whenever a new message is written to /messages/{chatId}/{msgId}
 */
exports.onNewMessageSendFcm = onValueCreated("/messages/{chatId}/{msgId}", async (event) => {
  const msg = event.data.val();
  if (!msg) return;

  const chatId = event.params.chatId;
  const chatSnap = await admin.database().ref(`/chats/${chatId}`).get();
  if (!chatSnap.exists()) return;

  const chat = chatSnap.val();
  const participants = chat.participantIds || {};
  const senderId = msg.senderId;
  const senderName = msg.senderName || "AK Messenger";

  const preview = msg.isEncrypted
    ? "New encrypted message"
    : (msg.plaintextFallback || msg.type || "New message");

  const tokens = [];
  for (const uid of Object.keys(participants)) {
    if (uid === senderId) continue;
    const userSnap = await admin.database().ref(`/users/${uid}/fcmToken`).get();
    if (userSnap.exists() && userSnap.val()) {
      tokens.push(userSnap.val());
    }
  }

  if (tokens.length === 0) return;

  await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: senderName,
      body: preview,
    },
    data: {
      type: "message",
      chatId: String(chatId),
      senderId: String(senderId || ""),
    },
    android: {
      priority: "high",
      notification: {
        channelId: "messages_channel",
      },
    },
  });
});

/**
 * Send high-priority FCM alert for incoming audio/video calls
 */
exports.onIncomingCallSendFcm = onValueCreated("/calls/{callId}", async (event) => {
  const call = event.data.val();
  if (!call || call.status !== "ringing") return;

  const calleeIds = call.calleeIds || {};
  const tokens = [];
  for (const uid of Object.keys(calleeIds)) {
    if (uid === call.callerId) continue;
    const tokenSnap = await admin.database().ref(`/users/${uid}/fcmToken`).get();
    if (tokenSnap.exists() && tokenSnap.val()) {
      tokens.push(tokenSnap.val());
    }
  }
  if (tokens.length === 0) return;

  await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: call.type === "video" ? "Incoming Video Call" : "Incoming Audio Call",
      body: `${call.callerName || "Someone"} is calling on AK Messenger`,
    },
    data: {
      type: "call",
      callId: String(event.params.callId),
      callType: String(call.type || "audio"),
    },
    android: {
      priority: "high",
      notification: {
        channelId: "calls_channel",
      },
    },
  });
});

/**
 * Send Admin Official Broadcast Notification via /notifications/{notifId}
 */
exports.onAdminNotificationBroadcast = onValueCreated("/notifications/{notifId}", async (event) => {
  const notif = event.data.val();
  if (!notif || !notif.sentByAdminUid) return;

  const adminSnap = await admin.database().ref(`/admins/${notif.sentByAdminUid}`).get();
  if (!adminSnap.exists()) {
    console.warn("Unauthorized notification attempt rejected:", notif.sentByAdminUid);
    return;
  }

  const payload = {
    notification: {
      title: notif.title || "AK MESSENGER OFFICIAL NOTICE",
      body: notif.message || "",
      ...(notif.imageUrl ? { imageUrl: notif.imageUrl } : {}),
    },
    data: {
      type: "admin_notice",
      notifId: String(event.params.notifId),
    },
    android: {
      priority: "high",
      notification: {
        channelId: "admin_notices_channel",
      },
    },
  };

  if (notif.targetType === "user" && notif.targetId) {
    const tokenSnap = await admin.database().ref(`/users/${notif.targetId}/fcmToken`).get();
    if (tokenSnap.exists() && tokenSnap.val()) {
      await admin.messaging().send({ ...payload, token: tokenSnap.val() });
    }
  } else if (notif.targetType === "group" && notif.targetId) {
    const membersSnap = await admin.database().ref(`/groups/${notif.targetId}/memberIds`).get();
    if (membersSnap.exists()) {
      const tokens = [];
      for (const uid of Object.keys(membersSnap.val() || {})) {
        const tokenSnap = await admin.database().ref(`/users/${uid}/fcmToken`).get();
        if (tokenSnap.exists() && tokenSnap.val()) tokens.push(tokenSnap.val());
      }
      if (tokens.length > 0) {
        await admin.messaging().sendEachForMulticast({ ...payload, tokens });
      }
    }
  } else {
    await admin.messaging().send({ ...payload, topic: "ak_messenger_all" });
  }
});

/**
 * Automatically remove stories older than 24 hours
 */
exports.cleanupExpiredStories = onSchedule("every 60 minutes", async () => {
  const now = Date.now();
  const snap = await admin.database().ref("/stories").orderByChild("expiresAt").endAt(now).get();
  if (!snap.exists()) return;

  const updates = {};
  snap.forEach((child) => {
    updates[child.key] = null;
  });
  await admin.database().ref("/stories").update(updates);
});
