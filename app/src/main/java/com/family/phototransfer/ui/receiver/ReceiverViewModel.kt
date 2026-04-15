package com.family.phototransfer.ui.receiver

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.data.repository.TransferRepository
import com.family.phototransfer.network.BoreTunnelManager
import com.family.phototransfer.network.BoreTunnelState
import com.family.phototransfer.network.ReceivedFile
import com.family.phototransfer.network.ReceiverStateHolder
import com.family.phototransfer.network.TransferServer
import com.family.phototransfer.ui.settings.dataStore
import com.family.phototransfer.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.NetworkInterface
import javax.inject.Inject

data class ReceivedFileUi(
    val fileName: String,
    val sizeText: String,
    val timeText: String
)

data class ReceiverUiState(
    val isListening:      Boolean = false,
    val myIpAddress:      String  = "",
    val receivedFiles:    List<ReceivedFileUi> = emptyList(),
    val statusMessage:    String  = "수신 대기 중...",
    val autoStartOnBoot:  Boolean = false,
    // bore 터널 상태
    val boreTunnelStatus: String  = "",
    val boreTunnelPort:   Int?    = null
)

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransferRepository,
    private val receiverStateHolder: ReceiverStateHolder,
    private val boreTunnelManager: BoreTunnelManager    // ✅ bore 터널 관리
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    private var server: TransferServer? = null

    private val saveDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    init {
        _uiState.value = _uiState.value.copy(myIpAddress = getLocalIpAddress())
        viewModelScope.launch {
            context.dataStore.data
                .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
                .first()
                .let { prefs ->
                    _uiState.value = _uiState.value.copy(
                        autoStartOnBoot = prefs[booleanPreferencesKey("auto_start_on_boot")] ?: false
                    )
                }
        }

        // bore 상태 변화를 UI에 반영
        viewModelScope.launch {
            boreTunnelManager.state.collectLatest { state ->
                val (statusText, port) = when (state) {
                    is BoreTunnelState.Idle        -> "대기 중" to null
                    is BoreTunnelState.Downloading -> "bore 다운로드 중..." to null
                    is BoreTunnelState.Connecting  -> "터널 연결 중..." to null
                    is BoreTunnelState.Connected   -> "bore.pub:${state.port}" to state.port
                    is BoreTunnelState.Error       -> "오류: ${state.message}" to null
                }
                _uiState.value = _uiState.value.copy(
                    boreTunnelStatus = statusText,
                    boreTunnelPort   = port
                )
            }
        }
    }

    // ── 부팅 자동 시작 토글 ────────────────────────────────────────────
    fun toggleAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("auto_start_on_boot")] = enabled
            }
            _uiState.value = _uiState.value.copy(autoStartOnBoot = enabled)
        }
    }

    // ── 수신 시작 ──────────────────────────────────────────────────────
    fun startListening() {
        if (_uiState.value.isListening) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isListening   = true,
                statusMessage = "수신 대기 중... (포트 9876)"
            )
            receiverStateHolder.setReceiving(true)

            // ✅ bore 터널 자동 시작 (백그라운드, 실패해도 로컬 서버는 계속)
            launch {
                boreTunnelManager.start()
            }

            withContext(Dispatchers.IO) {
                if (!saveDir.exists()) saveDir.mkdirs()

                server = TransferServer(
                    saveDirectory  = saveDir,
                    context        = context,
                    onFileReceived = { received: ReceivedFile ->
                        val timeText = java.text.SimpleDateFormat(
                            "HH:mm", java.util.Locale.getDefault()
                        ).format(java.util.Date())

                        _uiState.value = _uiState.value.copy(
                            receivedFiles = listOf(
                                ReceivedFileUi(
                                    fileName = received.fileName,
                                    sizeText = formatSize(received.fileSize),
                                    timeText = timeText
                                )
                            ) + _uiState.value.receivedFiles,
                            statusMessage = "수신 완료: ${received.fileName}"
                        )

                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            repository.recordReceived(
                                fileName   = received.fileName,
                                fileSize   = received.fileSize,
                                fromDevice = received.sourceDevice.ifBlank { "송신 기기" }
                            )
                        }

                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            val prefs     = context.dataStore.data.first()
                            val showNotif = prefs[booleanPreferencesKey("notify_on_receive")] ?: true
                            NotificationHelper.showReceiveComplete(
                                context  = context,
                                fileName = received.fileName,
                                enabled  = showNotif
                            )
                        }
                    },
                    onError = { errorMsg ->
                        _uiState.value = _uiState.value.copy(statusMessage = "오류: $errorMsg")
                    }
                )
                server?.start()
            }
        }
    }

    // ── 수신 중지 ──────────────────────────────────────────────────────
    fun stopListening() {
        viewModelScope.launch(Dispatchers.IO) {
            server?.stop()
            server = null
        }
        boreTunnelManager.stop()   // ✅ bore 터널도 함께 중지
        receiverStateHolder.setReceiving(false)
        _uiState.value = _uiState.value.copy(
            isListening      = false,
            statusMessage    = "수신 중지됨",
            boreTunnelStatus = "",
            boreTunnelPort   = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
        boreTunnelManager.stop()
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull {
                    !it.isLoopbackAddress &&
                    it is java.net.Inet4Address &&
                    it.hostAddress?.startsWith("192.168") == true
                }
                ?.hostAddress ?: "IP 확인 중..."
        } catch (e: Exception) { "IP 확인 중..." }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024.0)
            else               -> "$bytes B"
        }
    }
}
