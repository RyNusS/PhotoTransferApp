package com.family.phototransfer.ui.upload

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val thumbnailUri: String,
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
    val gridColumns: Int = 3,

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
    val errorMessage: String? = null,
    val remoteUrl: String? = null   // 딥링크(QR스캔)로 받은 원격 전송 URL
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
    private val receiverStateHolder: ReceiverStateHolder,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    init {
        // Receiver 탭의 수신 상태를 자동으로 감지해서 탐색 버튼 제어
        viewModelScope.launch {
            receiverStateHolder.isReceiving.collectLatest { isReceiving ->
                if (isReceiving) {
                    _uiState.value = _uiState.value.copy(
                        scanEnabled       = false,
                        discoveredDevices = emptyList(),
                        selectedDevice    = null,
                        cloudSyncActive   = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(scanEnabled = true)
                }
            }
        }

        // Settings에서 원격 연결 설정 시 자동으로 selectedDevice 반영
        // (QR 스캔으로 설정된 remoteUrl이 있는 경우는 덮어쓰지 않음)
        viewModelScope.launch {
            appContext.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .collect { prefs ->
                    val useRemote = prefs[booleanPreferencesKey("use_remote_mode")] ?: false
                    val host = (prefs[stringPreferencesKey("remote_host")] ?: "").trim()
                    if (useRemote && host.isNotBlank() && _uiState.value.remoteUrl == null) {
                        _uiState.value = _uiState.value.copy(
                            selectedDevice  = DiscoveredDevice(
                                ipAddress  = host,
                                deviceName = "원격 연결 (Cloudflare Tunnel)"
                            ),
                            cloudSyncActive = true
                        )
                    }
                }
        }
    }

    // ── 딥링크 원격 URL 설정 ────────────────────────────────────
    fun setRemoteUrl(url: String) {
        // QR 스캔으로 받은 URL을 selectedDevice로 설정 + DataStore에도 저장 (Settings 탭에 자동 반영)
        _uiState.value = _uiState.value.copy(
            remoteUrl       = url,
            selectedDevice  = DiscoveredDevice(
                ipAddress  = url,
                deviceName = "원격 연결 (QR 스캔)"
            ),
            cloudSyncActive = true
        )
        viewModelScope.launch {
            appContext.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("use_remote_mode")] = true
                prefs[stringPreferencesKey("remote_host")]      = url
            }
        }
    }

    fun clearRemoteUrl() {
        _uiState.value = _uiState.value.copy(remoteUrl = null)
        // remoteUrl 클리어 후 Settings 원격 연결이 있으면 다시 반영
        viewModelScope.launch {
            val prefs = appContext.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
            val useRemote = prefs[booleanPreferencesKey("use_remote_mode")] ?: false
            val host = (prefs[stringPreferencesKey("remote_host")] ?: "").trim()
            if (useRemote && host.isNotBlank()) {
                _uiState.value = _uiState.value.copy(
                    selectedDevice  = DiscoveredDevice(
                        ipAddress  = host,
                        deviceName = "원격 연결 (Cloudflare Tunnel)"
                    ),
                    cloudSyncActive = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    selectedDevice  = null,
                    cloudSyncActive = false
                )
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
                            thumbnailUri = contentUri,
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

    fun dragSelect(ids: Set<Long>) {
        _uiState.value = _uiState.value.copy(selectedFiles = ids)
    }

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

    // ── 수신기기 탐색 제어 ─────────────────────────────────────

    fun onReceiverStarted() {
        _uiState.value = _uiState.value.copy(
            scanEnabled       = false,
            discoveredDevices = emptyList(),
            selectedDevice    = null,
            cloudSyncActive   = false
        )
    }

    fun onReceiverStopped() {
        _uiState.value = _uiState.value.copy(scanEnabled = true)
    }

    // ── WiFi 기기 탐색 ────────────────────────────────────────
    fun scanForDevices(context: Context) {
        if (!_uiState.value.scanEnabled) return
        viewModelScope.launch {
            val prefs = context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
            val useRemoteMode = prefs[booleanPreferencesKey("use_remote_mode")] ?: false
            val remoteHost    = prefs[stringPreferencesKey("remote_host")]      ?: ""

            if (useRemoteMode) {
                val url = remoteHost.trim()
                if (url.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isScanning   = false,
                        errorMessage = "원격 연결 URL이 설정되지 않았습니다. 수신 측의 Cloudflare 터널 URL을 설정 탭에서 입력해주세요."
                    )
                    return@launch
                }
                val device = DiscoveredDevice(
                    ipAddress  = url,
                    deviceName = "원격 연결 (Cloudflare Tunnel)"
                )
                _uiState.value = _uiState.value.copy(
                    isScanning        = false,
                    discoveredDevices = listOf(device),
                    selectedDevice    = device,
                    cloudSyncActive   = true,
                    errorMessage      = null
                )
                return@launch
            }

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
