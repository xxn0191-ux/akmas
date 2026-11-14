package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.overlay.ChatHeadManager
import com.example.ui.navigation.*
import com.example.ui.screens.*
import com.example.ui.theme.AkMessengerTheme
import com.example.ui.viewmodel.MessengerViewModel

class MainActivity : ComponentActivity() {
    private val pendingOverlayIntent = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOverlayIntent.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOverlayIntent.value = intent
        setContent {
            val viewModel: MessengerViewModel = viewModel()
            val session by viewModel.sessionFlow.collectAsStateWithLifecycle()
            val calls by viewModel.calls.collectAsStateWithLifecycle()
            val navController = rememberNavController()
            val lifecycleOwner = LocalLifecycleOwner.current
            val deepIntent by pendingOverlayIntent

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> viewModel.setOnline(true)
                        Lifecycle.Event.ON_STOP -> viewModel.setOnline(false)
                        else -> {}
                    }
                })
            }

            // Start Floating Chat Heads background overlay service if enabled & permitted
            LaunchedEffect(session.idToken, session.chatHeadsEnabled) {
                if (session.idToken.isNotBlank() && session.chatHeadsEnabled && ChatHeadManager.canDrawOverlays(this@MainActivity)) {
                    ChatHeadManager.startService(this@MainActivity)
                }
            }

            // Route deep links triggered from Floating Chat Window (Audio/Video call, Camera/Gallery, Profile)
            LaunchedEffect(deepIntent, session.idToken) {
                val targetIntent = deepIntent ?: return@LaunchedEffect
                if (session.idToken.isBlank()) return@LaunchedEffect
                val chatId = targetIntent.getStringExtra(ChatHeadManager.EXTRA_OPEN_CHAT_ID)
                val callId = targetIntent.getStringExtra(ChatHeadManager.EXTRA_OPEN_CALL_ID)
                val profileUid = targetIntent.getStringExtra(ChatHeadManager.EXTRA_OPEN_PROFILE_UID)
                when {
                    !callId.isNullOrBlank() -> navController.navigate(CallRoute(callId))
                    !chatId.isNullOrBlank() -> navController.navigate(ChatRoute(chatId))
                    !profileUid.isNullOrBlank() -> navController.navigate(UserProfileRoute(profileUid))
                }
                pendingOverlayIntent.value = null
            }

            // Auto-surface incoming ringing calls when app is open
            val incomingCall = calls.firstOrNull {
                it.status == "ringing" && it.calleeIds[session.uid] == true && it.callerId != session.uid
            }
            LaunchedEffect(incomingCall?.id) {
                if (incomingCall != null && session.uid.isNotBlank()) {
                    navController.navigate(CallRoute(incomingCall.id))
                }
            }

            AkMessengerTheme(darkTheme = session.darkTheme) {
                NavHost(navController = navController, startDestination = SplashRoute) {
                    composable<SplashRoute> {
                        SplashScreen(
                            isLoggedIn = session.idToken.isNotBlank(),
                            onFinished = { loggedIn ->
                                navController.navigate(if (loggedIn) HomeRoute else AuthRoute) {
                                    popUpTo(SplashRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<AuthRoute> {
                        AuthScreen(
                            viewModel = viewModel,
                            onAuthenticated = {
                                navController.navigate(HomeRoute) {
                                    popUpTo(AuthRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<HomeRoute> {
                        HomeTabsScreen(
                            viewModel = viewModel,
                            onOpenChat = { chatId -> navController.navigate(ChatRoute(chatId)) },
                            onOpenCall = { callId -> navController.navigate(CallRoute(callId)) },
                            onOpenGroupDetail = { gid -> navController.navigate(GroupDetailRoute(gid)) },
                            onOpenUserProfile = { uid -> navController.navigate(UserProfileRoute(uid)) },
                            onOpenQrShare = { navController.navigate(QrShareRoute) },
                            onOpenStarred = { navController.navigate(StarredMessagesRoute) },
                            onOpenNewGroup = { navController.navigate(NewGroupRoute) },
                            onOpenNewCommunity = { navController.navigate(NewCommunityRoute) },
                            onLoggedOut = {
                                navController.navigate(AuthRoute) {
                                    popUpTo(HomeRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<ChatRoute> { entry ->
                        val route = entry.toRoute<ChatRoute>()
                        ChatScreen(
                            chatId = route.chatId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenCall = { cid -> navController.navigate(CallRoute(cid)) },
                            onOpenUserProfile = { uid -> navController.navigate(UserProfileRoute(uid)) },
                            onOpenGroupDetail = { gid -> navController.navigate(GroupDetailRoute(gid)) }
                        )
                    }
                    composable<CallRoute> { entry ->
                        val route = entry.toRoute<CallRoute>()
                        CallScreen(
                            callId = route.callId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<GroupDetailRoute> { entry ->
                        val route = entry.toRoute<GroupDetailRoute>()
                        GroupDetailScreen(
                            groupId = route.groupId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenCall = { cid -> navController.navigate(CallRoute(cid)) }
                        )
                    }
                    composable<UserProfileRoute> { entry ->
                        val route = entry.toRoute<UserProfileRoute>()
                        UserProfileScreen(
                            uid = route.uid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenChat = { cid -> navController.navigate(ChatRoute(cid)) }
                        )
                    }
                    composable<QrShareRoute> {
                        QrShareScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<StarredMessagesRoute> {
                        StarredMessagesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable<NewGroupRoute> {
                        NewGroupScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onGroupCreated = { gid ->
                                navController.navigate(ChatRoute(gid)) {
                                    popUpTo(NewGroupRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<NewCommunityRoute> {
                        NewCommunityScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
