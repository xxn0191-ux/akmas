package com.aistudio.akmessenger.admin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.akmessenger.admin.BuildConfig
import com.aistudio.akmessenger.shared.firebase.FirebaseConfig
import com.aistudio.akmessenger.shared.firebase.RtdbStatus
import com.aistudio.akmessenger.shared.local.StoredSession
import com.aistudio.akmessenger.shared.model.*
import com.aistudio.akmessenger.shared.repository.AdminDashboardStats
import com.aistudio.akmessenger.shared.repository.AkAdminRepository
import com.aistudio.akmessenger.shared.repository.AkSyncEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val config = FirebaseConfig(
        apiKey = BuildConfig.FIREBASE_API_KEY,
        authDomain = BuildConfig.FIREBASE_AUTH_DOMAIN,
        databaseUrl = BuildConfig.FIREBASE_DATABASE_URL,
        projectId = BuildConfig.FIREBASE_PROJECT_ID
    )

    val syncEngine = AkSyncEngine(application, config, "ak_messenger_admin.db")
    val repo = AkAdminRepository(syncEngine)

    val sessionFlow: StateFlow<StoredSession> = syncEngine.prefs.sessionFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StoredSession())

    val rtdbStatus: StateFlow<RtdbStatus> = syncEngine.rtdbStatus
    val appSettings: StateFlow<AppSettings> = syncEngine.appSettings

    val users: StateFlow<List<UserProfile>> = syncEngine.observeUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<GroupInfo>> = syncEngine.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<CommunityInfo>> = syncEngine.observeCommunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stories: StateFlow<List<StoryItem>> = syncEngine.observeStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallSession>> = syncEngine.observeCalls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<AdminNotification>> = syncEngine.observeNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reports: StateFlow<List<ReportItem>> = syncEngine.observeReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminLogs: StateFlow<List<AdminLog>> = syncEngine.observeAdminLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMessagesCount: StateFlow<Int> = syncEngine.observeAllMessagesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val stats: StateFlow<AdminDashboardStats> = combine(
        users, totalMessagesCount, groups, communities, stories, calls
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        repo.computeStats(
            users = arr[0] as List<UserProfile>,
            totalMessages = arr[1] as Int,
            groups = arr[2] as List<GroupInfo>,
            communities = arr[3] as List<CommunityInfo>,
            stories = arr[4] as List<StoryItem>,
            calls = arr[5] as List<CallSession>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdminDashboardStats())

    private val _currentAdmin = MutableStateFlow<AdminRecord?>(null)
    val currentAdmin: StateFlow<AdminRecord?> = _currentAdmin.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    init {
        viewModelScope.launch {
            val s = syncEngine.prefs.current()
            if (s.idToken.isNotBlank() && s.uid.isNotBlank()) {
                syncEngine.activeToken = s.idToken
                val verified = syncEngine.rtdbClient.get<AdminRecord>("admins/${s.uid}", s.idToken).getOrNull()
                if (verified != null) {
                    _currentAdmin.value = verified.copy(uid = s.uid, email = s.email)
                    syncEngine.startRealtimeListener(viewModelScope, includeAdminNodes = true)
                }
            }
        }
    }

    fun clearError() { _authError.value = null }
    fun clearToast() { _statusToast.value = null }

    fun loginAdmin(
        email: String,
        password: String,
        pin: String,
        rememberMe: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _authBusy.value = true
            _authError.value = null
            if (rememberMe) syncEngine.prefs.setRememberAdminEmail(email.trim())
            else syncEngine.prefs.setRememberAdminEmail("")

            val res = repo.loginAdmin(email, password, pin)
            _authBusy.value = false
            res.onSuccess { admin ->
                _currentAdmin.value = admin
                syncEngine.startRealtimeListener(viewModelScope, includeAdminNodes = true)
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Admin login failed"
            }
        }
    }

    fun sendForgotPassword(email: String) {
        viewModelScope.launch {
            val res = syncEngine.authClient.sendPasswordResetEmail(email)
            res.onSuccess { _statusToast.value = "Password reset link sent to $email" }
                .onFailure { _authError.value = it.message }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            _currentAdmin.value?.let { repo.logoutAdmin(it) }
            _currentAdmin.value = null
            onDone()
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            val res = syncEngine.syncAllFromRtdb(includeAdminNodes = true)
            if (res.isFailure) _statusToast.value = res.exceptionOrNull()?.message
        }
    }

    fun performAdminAction(block: suspend (AdminRecord) -> Result<Any?>, successMsg: String) {
        val admin = _currentAdmin.value ?: return
        viewModelScope.launch {
            val res = block(admin)
            _statusToast.value = if (res.isSuccess) successMsg else (res.exceptionOrNull()?.message ?: "Action failed")
        }
    }
}
