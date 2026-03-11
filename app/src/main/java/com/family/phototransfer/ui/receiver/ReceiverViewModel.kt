package com.family.phototransfer.ui.receiver

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.network.ReceivedFile
import com.family.phototransfer.network.TransferServer
import com.family.phototransfer.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val myIpAddress:   String = "",
    val receivedFiles: List<ReceivedFileUi> = emptyList(),
    val statusMessage: String = "수신 대기 중..."
)

@HiltViewModel
class ReceiverViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    private var server: TransferServer? = null

    // ✅ /Pictures 폴더에 저장 → 구글 포토 자동 감지
    private val saveDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

    init {
        _uiState.value = _uiState.value.copy(myIpAddress = getLocalIpAddress())
    }

    fun startListening() {
        if (_uiState.value.isListening) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isListening   = true,
                statusMessage = "수신 대기 중... (포트 9876)"
            )

            withContext(Dispatchers.IO) {
                if (!saveDir.exists()) saveDir.mkdirs()

                server = TransferServer(
                    saveDirectory  = saveDir,
                    context        = context,       // ✅ MediaStore 등록용 context 전달
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

                        NotificationHelper.showReceiveComplete(context, received.fileName)
                    },
                    onError = { errorMsg ->
                        _uiState.value = _uiState.value.copy(
                            statusMessage = "오류: $errorMsg"
                        )
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
        _uiState.value = _uiState.value.copy(
            isListening   = false,
            statusMessage = "수신 중지됨"
        )
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
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
        } catch (e: Exception) {
            "IP 확인 중..."
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024     -> "%.1f KB".format(bytes / 1_024.0)
            else               -> "$bytes B"
        }
    }
}
