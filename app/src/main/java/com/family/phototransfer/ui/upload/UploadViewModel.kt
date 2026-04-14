package com.family.phototransfer.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.network.DiscoveredDevice
import com.family.phototransfer.network.ReceiverStateHolder
import com.family.phototransfer.network.TransferEvent
import com.family.phototransfer.network.TransferManager
import com.family.phototransfer.network.WifiDeviceScanner
import com.family.phototransfer.ui.settings.dataStore
import com.family.phototransfer.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MediaFileUi(
    val id: Long,
    val uri: String,
    val thumbnailUri: String,   // 썸네일 표시용 (동영상은 별도 썸네일 URI)
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val duration: String? = null
)

data class UploadUiState(
    val isLoading: Boolean = false,
    val allFiles: List<MediaFileUi> = emptyList(),
    val filteredFiles: List<MediaFileUi> = emptyList(),
    val selectedFiles: Set<Long> = emptySet(),
    val selectedTab: MediaTab = MediaTab.ALL,
    val cloudSyncActive: Boolean = false,
    val gridColumns: Int = 3,                             // ✅ 3열/5열 전환

    val isScanning: Boolean = false,
    val scanEnabled: Boolean = true,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,

    val isUploading: Boolean = false,
    val currentUploadFile: String = "",
    val uploadTotal: Int = 0,
    val uploadCurrent: Int = 0,
    val uploadProgress: Float = 0f,
    val successCount: Int = 0,
    val duplicateCount: Int = 0,
    val failedCount: Int = 0,
    val isUploadDone: Boolean = false,
    val errorMessage: String? = null
) {
    val selectedSizeText: String get() {
        val totalBytes = allFiles.filter { it.id in selectedFiles }.sumOf { it.size }
        return formatSize(totalBytes)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
    else                   -> "$bytes B"
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val transferManager: TransferManager,
    private val receiverStateHolder: ReceiverStateHolder  // ✅ 수신 상태 공유
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    init {
        // ✅ Receiver 탭의 수신 상태를 자동으로 감지해서 탐색 버튼 제어
        viewModelScope.launch {
            receiverStateHolder.isReceiving.collectLatest { isReceiving ->
                if (isReceiving) {
                    // 수신 시작 → 탐색 비활성화 + 기존 탐색 결과 초기화
                    _uiState.value = _uiState.value.copy(
                        scanEnabled       = false,
                        discoveredDevices = emptyList(),
                        selectedDevice    = null,
                        cloudSyncActive   = false
                    )
                } else {
                    // 수신 중지 → 탐색 다시 활성화
                    _uiState.value = _uiState.value.copy(scanEnabled = true)
                }
            }
        }
    }

    // ── 갤러리 로드 ───────────────────────────────────────────
    fun loadMediaFiles(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val files = withContext(Dispatchers.IO) {
                val result = mutableListOf<MediaFileUi>()
                val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.query(
                    imageUri,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE),
                    null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val contentUri = "$imageUri/$id"
                        result.add(MediaFileUi(
                            id           = id,
                            uri          = contentUri,
                            thumbnailUri = contentUri,   // 사진은 원본 URI 그대로
                            name         = cursor.getString(nameCol),
                            size         = cursor.getLong(sizeCol),
                            isVideo      = false
                        ))
                    }
                }
                val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.query(
                    videoUri,
                    arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION),
                    null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        // 동영상 썸네일: MediaStore Thumbnails URI 사용
                        val thumbUri = android.net.Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "$id"
                        ).toString()
                        result.add(MediaFileUi(
                            id           = id,
                            uri          = "$videoUri/$id",
                            thumbnailUri = thumbUri,
                            name         = cursor.getString(nameCol),
                            size         = cursor.getLong(sizeCol),
                            isVideo      = true,
                            duration     = formatDuration(cursor.getLong(durCol))
                        ))
                    }
                }
                result.sortedByDescending { it.id }
            }
            _uiState.value = _uiState.value.copy(isLoading = false, allFiles = files, filteredFiles = files)
        }
    }

    // ── 탭 / 선택 ─────────────────────────────────────────────
    fun setTab(tab: MediaTab) {
        val filtered = when (tab) {
            MediaTab.ALL   -> _uiState.value.allFiles
            MediaTab.PHOTO -> _uiState.value.allFiles.filter { !it.isVideo }
            MediaTab.VIDEO -> _uiState.value.allFiles.filter { it.isVideo }
        }
        _uiState.value = _uiState.value.copy(selectedTab = tab, filteredFiles = filtered)
    }

    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedFiles.toMutableSet()
        if (id in current) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedFiles = current)
    }

    // ✅ 드래그 선택: 드래그된 ID 집합으로 selectedFiles 덮어쓰기
    fun dragSelect(ids: Set<Long>) {
        _uiState.value = _uiState.value.copy(selectedFiles = ids)
    }

    // ✅ 3열 ↔ 5열 전환
    fun toggleGridColumns() {
        val next = if (_uiState.value.gridColumns == 3) 5 else 3
        _uiState.value = _uiState.value.copy(gridColumns = next)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(selectedFiles = _uiState.value.filteredFiles.map { it.id }.toSet())
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet())
    }

    // ── 수신기기 탐색 제어 (7번 요구사항) ─────────────────────

    /** Receiver 탭에서 수신 시작 시 호출 → 탐색 비활성화 + 선택 기기 해제 */
    fun onReceiverStarted() {
        _uiState.value = _uiState.value.copy(
            scanEnabled       = false,
            discoveredDevices = emptyList(),
            selectedDevice    = null,
            cloudSyncActive   = false
        )
    }

    /** Receiver 탭에서 수신 중지 시 호출 → 탐색 다시 활성화 */
    fun onReceiverStopped() {
        _uiState.value = _uiState.value.copy(scanEnabled = true)
    }

    // ── WiFi 기기 탐색 ────────────────────────────────────────
    fun scanForDevices(context: Context) {
        if (!_uiState.value.scanEnabled) return
        viewModelScope.launch {
            // 원격 연결 모드 확인
            val prefs = context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
            val useRemoteMode = prefs[booleanPreferencesKey("use_remote_mode")] ?: false
            val remoteHost    = prefs[stringPreferencesKey("remote_host")]      ?: ""

            if (useRemoteMode) {
                // 원격 모드: WiFi 스캔 생략 → remote host를 가상 기기로 즉시 설정
                if (remoteHost.isNotBlank()) {
                    val remoteDevice = DiscoveredDevice(
                        ipAddress  = remoteHost,
                        deviceName = "원격 연결 (Tailscale)"
                    )
                    _uiState.value = _uiState.value.copy(
                        discoveredDevices = listOf(remoteDevice),
                        selectedDevice    = remoteDevice,
                        cloudSyncActive   = true,
                        errorMessage      = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "설정 탭에서 원격 호스트 주소(Tailscale)를 먼저 입력해주세요"
                    )
                }
                return@launch
            }

            // 로컬 모드: 기존 WiFi 스캔
            _uiState.value = _uiState.value.copy(isScanning = true, discoveredDevices = emptyList(), selectedDevice = null, errorMessage = null)
            try {
                val scanner = WifiDeviceScanner(context)
                val devices = withContext(Dispatchers.IO) { scanner.scanNetwork() }
                _uiState.value = _uiState.value.copy(
                    isScanning        = false,
                    discoveredDevices = devices,
                    selectedDevice    = if (devices.size == 1) devices.first() else null,
                    cloudSyncActive   = devices.isNotEmpty()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isScanning = false, errorMessage = "기기 탐색 실패: ${e.message}")
            }
        }
    }

    fun selectDevice(device: DiscoveredDevice) {
        _uiState.value = _uiState.value.copy(selectedDevice = device, cloudSyncActive = true)
    }

    // ── 파일 전송 ─────────────────────────────────────────────
    fun startUpload(context: Context) {
        val state = _uiState.value
        val receiverIp = state.selectedDevice?.ipAddress ?: run {
            _uiState.value = _uiState.value.copy(errorMessage = "먼저 수신 기기를 선택해주세요")
            return
        }
        if (state.selectedFiles.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "전송할 파일을 선택해주세요")
            return
        }
        val filePairs = state.allFiles.filter { it.id in state.selectedFiles }.map { Uri.parse(it.uri) to it.name }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true, uploadTotal = filePairs.size, uploadCurrent = 0,
                successCount = 0, duplicateCount = 0, failedCount = 0,
                isUploadDone = false, errorMessage = null
            )

            transferManager.transferFiles(context, filePairs, receiverIp, android.os.Build.MODEL)
                .collect { event ->
                    when (event) {
                        is TransferEvent.Started   -> _uiState.value = _uiState.value.copy(currentUploadFile = event.fileName, uploadCurrent = event.current)
                        is TransferEvent.Completed -> _uiState.value = _uiState.value.copy(successCount = _uiState.value.successCount + 1)
                        is TransferEvent.Duplicate -> _uiState.value = _uiState.value.copy(duplicateCount = _uiState.value.duplicateCount + 1)
                        is TransferEvent.Failed    -> _uiState.value = _uiState.value.copy(failedCount = _uiState.value.failedCount + 1)
                        is TransferEvent.AllDone   -> {
                            val final = _uiState.value
                            _uiState.value = final.copy(isUploading = false, isUploadDone = true, selectedFiles = emptySet())
                            // ✅ 알림 설정값 읽어서 전달
                            val prefs     = context.dataStore.data.first()
                            val showNotif = prefs[booleanPreferencesKey("show_notification")] ?: true
                            NotificationHelper.showTransferComplete(
                                context        = context,
                                successCount   = final.successCount,
                                duplicateCount = final.duplicateCount,
                                failedCount    = final.failedCount,
                                enabled        = showNotif
                            )
                        }
                        else -> {}
                    }
                }
        }
    }

    fun dismissUploadResult() {
        _uiState.value = _uiState.value.copy(isUploadDone = false)
    }

    private fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
    }
}
