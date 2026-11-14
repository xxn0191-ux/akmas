package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object SplashRoute
@Serializable data object AuthRoute
@Serializable data object HomeRoute
@Serializable data class ChatRoute(val chatId: String)
@Serializable data class CallRoute(val callId: String)
@Serializable data class GroupDetailRoute(val groupId: String)
@Serializable data class UserProfileRoute(val uid: String)
@Serializable data object QrShareRoute
@Serializable data object StarredMessagesRoute
@Serializable data object NewGroupRoute
@Serializable data object NewCommunityRoute
