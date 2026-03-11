package com.family.phototransfer.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── 데이터 모델 (Screen과 ViewModel이 공유) ────────────────────
data class RecentActivity(
    val fileName: String,
    val status: ActivityStatus,
    val fileSize: String,
    val timeAgo: String
)

enum class ActivityStatus { SUCCESS, FAILED, DUPLICATE }

// ── UI 상태 ───────────────────────────────────────────────────
data class DashboardUiState(
    val syncEnabled: Boolean = true,
    val isConnected: Boolean = false,
    val connectedDeviceIp: String = "",
    val transferProgress: Float = 0.78f,
    val currentFileName: String = "IMG_20240315_1236.jpg",
    val totalPhotosSent: Int = 1402,
    val totalSizeGB: Float = 12.4f,
    val duplicatesSkipped: Int = 98,
    val recentActivities: List<RecentActivity> = listOf(
        RecentActivity("IMG_20240315_1234.jpg", ActivityStatus.SUCCESS,   "3.2 MB", "2분 전"),
        RecentActivity("IMG_20240315_1235.jpg", ActivityStatus.SUCCESS,   "4.1 MB", "3분 전"),
        RecentActivity("VID_20240314_0921.mp4", ActivityStatus.DUPLICATE, "128 MB", "5분 전"),
        RecentActivity("IMG_20240314_0055.jpg", ActivityStatus.FAILED,    "2.8 MB", "1시간 전"),
        RecentActivity("IMG_20240313_1847.jpg", ActivityStatus.SUCCESS,   "5.6 MB", "2시간 전"),
    )
)

// ── ViewModel ─────────────────────────────────────────────────
@HiltViewModel
class SenderDashboardViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun toggleSync(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncEnabled = enabled)
        }
    }

    fun updateConnectionStatus(isConnected: Boolean, ip: String = "") {
        _uiState.value = _uiState.value.copy(
            isConnected = isConnected,
            connectedDeviceIp = ip
        )
    }
}
