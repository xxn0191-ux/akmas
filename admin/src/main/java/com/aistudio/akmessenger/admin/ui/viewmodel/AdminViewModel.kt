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

    private val _currentAdmin = MutableStateFlow(
        AdminRecord(
            uid = "ak_direct_admin",
            email = "Direct Admin Console",
            role = "superadmin"
        )
    )
    val currentAdmin: StateFlow<AdminRecord> = _currentAdmin.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    init {
        syncEngine.startRealtimeListener(viewModelScope, includeAdminNodes = true)
    }

    fun clearToast() { _statusToast.value = null }

    fun refreshNow() {
        viewModelScope.launch {
            val res = syncEngine.syncAllFromRtdb(includeAdminNodes = true)
            if (res.isFailure) _statusToast.value = res.exceptionOrNull()?.message
        }
    }

    fun performAdminAction(block: suspend (AdminRecord) -> Result<Any?>, successMsg: String) {
        val admin = _currentAdmin.value
        viewModelScope.launch {
            val res = block(admin)
            _statusToast.value = if (res.isSuccess) successMsg else (res.exceptionOrNull()?.message ?: "Action failed")
        }
    }
}
