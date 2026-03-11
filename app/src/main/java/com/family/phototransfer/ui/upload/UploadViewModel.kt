package com.family.phototransfer.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.network.DiscoveredDevice
import com.family.phototransfer.network.TransferEvent
import com.family.phototransfer.network.TransferManager
import com.family.phototransfer.network.WifiDeviceScanner
import com.family.phototransfer.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── UI 데이터 모델 ─────────────────────────────────────────────
data class MediaFileUi(
    val id: Long,
    val uri: String,
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

    val isScanning: Boolean = false,
    val discoveredDevices: List<DiscoveredDevice> = emptyList(),
    val selectedDevice: DiscoveredDevice? = null,

    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val currentUploadFile: String = "",
    val uploadTotal: Int = 0,
    val uploadCurrent: Int = 0,
    val successCount: Int = 0,
    val duplicateCount: Int = 0,
    val failedCount: Int = 0,
    val isUploadDone: Boolean = false,

    val errorMessage: String? = null
) {
    val selectedSizeText: String
        get() {
            val totalBytes = allFiles.filter { it.id in selectedFiles }.sumOf { it.size }
            return formatSize(totalBytes)
        }
}

fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024         -> "%.1f KB".format(bytes / 1_024.0)
        else                   -> "$bytes B"
    }
}

@HiltViewModel
class UploadViewModel @Inject constructor(
    private val transferManager: TransferManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    // ── 갤러리 로드 (실제 폰 - MediaStore 직접 조회) ───────────
    fun loadMediaFiles(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val files = withContext(Dispatchers.IO) {
                val result = mutableListOf<MediaFileUi>()

                // 사진
                val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.query(
                    imageUri,
                    arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.SIZE
                    ),
                    null, null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        result.add(
                            MediaFileUi(
                                id      = id,
                                uri     = "$imageUri/$id",
                                name    = cursor.getString(nameCol),
                                size    = cursor.getLong(sizeCol),
                                isVideo = false
                            )
                        )
                    }
                }

                // 동영상
                val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                context.contentResolver.query(
                    videoUri,
                    arrayOf(
                        MediaStore.Video.Media._ID,
                        MediaStore.Video.Media.DISPLAY_NAME,
                        MediaStore.Video.Media.SIZE,
                        MediaStore.Video.Media.DURATION
                    ),
                    null, null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val sizeCol     = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        result.add(
                            MediaFileUi(
                                id       = id,
                                uri      = "$videoUri/$id",
                                name     = cursor.getString(nameCol),
                                size     = cursor.getLong(sizeCol),
                                isVideo  = true,
                                duration = formatDuration(cursor.getLong(durationCol))
                            )
                        )
                    }
                }

                result.sortedByDescending { it.id }
            }

            _uiState.value = _uiState.value.copy(
                isLoading     = false,
                allFiles      = files,
                filteredFiles = files
            )
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

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedFiles = _uiState.value.filteredFiles.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet())
    }

    // ── WiFi 기기 탐색 ────────────────────────────────────────
    fun scanForDevices(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning        = true,
                discoveredDevices = emptyList(),
                selectedDevice    = null,
                errorMessage      = null
            )
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
                _uiState.value = _uiState.value.copy(
                    isScanning   = false,
                    errorMessage = "기기 탐색 실패: ${e.message}"
                )
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

        val filePairs: List<Pair<Uri, String>> = state.allFiles
            .filter { it.id in state.selectedFiles }
            .map { Uri.parse(it.uri) to it.name }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading    = true,
                uploadTotal    = filePairs.size,
                uploadCurrent  = 0,
                successCount   = 0,
                duplicateCount = 0,
                failedCount    = 0,
                isUploadDone   = false,
                errorMessage   = null
            )

            transferManager.transferFiles(
                context          = context,
                files            = filePairs,
                receiverIp       = receiverIp,
                sourceDeviceName = android.os.Build.MODEL
            ).collect { event ->
                when (event) {
                    is TransferEvent.Started -> {
                        _uiState.value = _uiState.value.copy(
                            currentUploadFile = event.fileName,
                            uploadCurrent     = event.current
                        )
                    }
                    is TransferEvent.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            successCount = _uiState.value.successCount + 1
                        )
                    }
                    is TransferEvent.Duplicate -> {
                        _uiState.value = _uiState.value.copy(
                            duplicateCount = _uiState.value.duplicateCount + 1
                        )
                    }
                    is TransferEvent.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            failedCount = _uiState.value.failedCount + 1
                        )
                    }
                    is TransferEvent.AllDone -> {
                        val final = _uiState.value
                        _uiState.value = final.copy(
                            isUploading   = false,
                            isUploadDone  = true,
                            selectedFiles = emptySet()
                        )
                        // ✅ 포그라운드/백그라운드 무관하게 알림 표시
                        NotificationHelper.showTransferComplete(
                            context        = context,
                            successCount   = final.successCount,
                            duplicateCount = final.duplicateCount,
                            failedCount    = final.failedCount
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
