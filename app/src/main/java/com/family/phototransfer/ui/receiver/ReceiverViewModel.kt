package com.family.phototransfer.ui.receiver

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.data.repository.TransferRepository
import com.family.phototransfer.network.CloudflaredManager
import com.family.phototransfer.network.CloudflaredState
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
    val isListening:   Boolean = false,
    val myIpAddress:   String  = "",
    val receivedFiles: List<ReceivedFileUi> = emptyList(),
    val statusMessage: String  = "수신 대기 중...",
    val autoStartOnBoot: Boolean = false,
    val tunnelStatus:  String  = "",    // cloudflared 상태 메시지
    val tunnelUrl:     String? = null   // 연결된 trycloudflare.com URL
)

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransferRepository,
    private val receiverStateHolder: ReceiverStateHolder,
    private val cloudflaredManager: CloudflaredManager
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

        viewModelScope.launch {
            cloudflaredManager.state.collectLatest { state ->
                val (statusText, url) = when (state) {
                    is CloudflaredState.Idle       -> "" to null
                    is CloudflaredState.Connecting -> "터널 연결 중..." to null
                    is CloudflaredState.Connected  -> "연결됨" to state.url
                    is CloudflaredState.Error      -> "오류: ${state.message}" to null
                }
                _uiState.value = _uiState.value.copy(
                    tunnelStatus = statusText,
                    tunnelUrl    = url
                )
            }
        }
    }

    fun toggleAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[booleanPreferencesKey("auto_start_on_boot")] = enabled
            }
            _uiState.value = _uiState.value.copy(autoStartOnBoot = enabled)
        }
    }

    fun startListening() {
        if (_uiState.value.isListening) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isListening   = true,
                statusMessage = "수신 대기 중... (포트 9876/9875)"
            )
            receiverStateHolder.setReceiving(true)

            launch { cloudflaredManager.start() }

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

    fun stopListening() {
        viewModelScope.launch(Dispatchers.IO) {
            server?.stop()
            server = null
        }
        cloudflaredManager.stop()
        receiverStateHolder.setReceiving(false)
        _uiState.value = _uiState.value.copy(
            isListening   = false,
            statusMessage = "수신 중지됨",
            tunnelStatus  = "",
            tunnelUrl     = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
        cloudflaredManager.stop()
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
