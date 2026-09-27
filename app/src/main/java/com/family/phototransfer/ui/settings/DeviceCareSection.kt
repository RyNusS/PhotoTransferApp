package com.family.phototransfer.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.BuildConfig
import com.family.phototransfer.util.AppUpdater
import com.family.phototransfer.util.BootLog
import com.family.phototransfer.util.ReceivePowerSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val DcPrimaryBlue   = Color(0xFF2D6BE4)
private val DcSuccessGreen  = Color(0xFF00C853)
private val DcWarningOrange = Color(0xFFFF6B35)
private val DcErrorRed      = Color(0xFFFF4444)
private val DcTextPrimary   = Color(0xFFFFFFFF)
private val DcTextSecondary = Color(0xFF8B9BB4)

// ── 상태 ────────────────────────────────────────────────────────
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val release: AppUpdater.Release) : UpdateState()
    data class Downloading(val release: AppUpdater.Release, val progress: Float) : UpdateState()
    data class Ready(val release: AppUpdater.Release, val apk: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

data class DeviceCareUiState(
    val lowPowerReceive: Boolean = true,
    val bootEntries: List<BootLog.Entry> = emptyList(),
    val showAllBoots: Boolean = false,
    val update: UpdateState = UpdateState.Idle
)

// ── ViewModel ───────────────────────────────────────────────────
@HiltViewModel
class DeviceCareViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeviceCareUiState(
            lowPowerReceive = ReceivePowerSaver.enabled,
            bootEntries = BootLog.entries(context)
        )
    )
    val uiState: StateFlow<DeviceCareUiState> = _uiState.asStateFlow()

    fun setLowPower(value: Boolean) {
        ReceivePowerSaver.setEnabled(context, value)
        _uiState.update { it.copy(lowPowerReceive = value) }
    }

    fun refreshBoots() {
        _uiState.update { it.copy(bootEntries = BootLog.entries(context)) }
    }

    fun toggleShowAllBoots() {
        _uiState.update { it.copy(showAllBoots = !it.showAllBoots) }
    }

    fun clearBoots() {
        BootLog.clear(context)
        refreshBoots()
    }

    /** 업데이트 버튼: 상태에 따라 확인 → 다운로드 → 설치 순으로 진행 */
    fun onUpdateButton(uiContext: Context) {
        when (val s = _uiState.value.update) {
            is UpdateState.Checking, is UpdateState.Downloading -> Unit
            is UpdateState.Available -> download(s.release, uiContext)
            is UpdateState.Ready -> install(uiContext, s)
            else -> check()
        }
    }

    private fun check() {
        _uiState.update { it.copy(update = UpdateState.Checking) }
        viewModelScope.launch {
            val next = try {
                val latest = AppUpdater.fetchLatest()
                if (latest.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(latest)
                else UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "업데이트 확인 실패")
            }
            _uiState.update { it.copy(update = next) }
        }
    }

    private fun download(release: AppUpdater.Release, uiContext: Context) {
        _uiState.update { it.copy(update = UpdateState.Downloading(release, 0f)) }
        viewModelScope.launch {
            try {
                val apk = AppUpdater.download(context, release) { p ->
                    _uiState.update { it.copy(update = UpdateState.Downloading(release, p)) }
                }
                val ready = UpdateState.Ready(release, apk)
                _uiState.update { it.copy(update = ready) }
                install(uiContext, ready)
            } catch (e: Exception) {
                _uiState.update { it.copy(update = UpdateState.Error(e.message ?: "다운로드 실패")) }
            }
        }
    }

    private fun install(uiContext: Context, ready: UpdateState.Ready) {
        try {
            if (!AppUpdater.canInstall(uiContext)) {
                // 권한 화면에서 허용 후 돌아와 버튼을 다시 누르면 설치 진행
                AppUpdater.openInstallPermissionSettings(uiContext)
            } else {
                AppUpdater.launchInstall(uiContext, ready.apk)
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(update = UpdateState.Error(e.message ?: "설치 화면을 열 수 없습니다")) }
        }
    }
}

// ── 화면 ───────────────────────────────────────────────────────
@Composable
fun DeviceCareSection(viewModel: DeviceCareViewModel = hiltViewModel()) {
    val ui by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshBoots() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // 1) 전원 안정화
        SettingsSection(title = "전원 안정화 (수신 기기)") {
            SettingsToggleRow(
                icon            = Icons.Default.Warning,
                title           = "저전력 수신 모드",
                subtitle        = "수신 속도를 초당 약 2MB로 제한하고 파일 사이에 잠깐 쉬어 " +
                                  "순간 전력 사용을 줄입니다. 전원이 불안정해 자주 꺼지면 켜 두세요.",
                checked         = ui.lowPowerReceive,
                onCheckedChange = { viewModel.setLowPower(it) }
            )
        }

        // 2) 재부팅 기록
        SettingsSection(title = "재부팅 기록") {
            BootLogContent(ui, viewModel)
        }

        // 3) 앱 업데이트
        SettingsSection(title = "앱 업데이트") {
            UpdateContent(ui.update) { viewModel.onUpdateButton(context) }
        }
    }
}

@Composable
private fun BootLogContent(ui: DeviceCareUiState, viewModel: DeviceCareViewModel) {
    val entries = ui.bootEntries
    val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val last24h = entries.count { it.bootTime >= dayAgo }
    val last7d = entries.count { it.bootTime >= weekAgo }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Refresh, null, tint = DcPrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("최근 24시간 ${last24h}회 · 7일 ${last7d}회", color = DcTextPrimary,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("앱이 켜질 때마다 새 부팅인지 확인해 기록합니다", color = DcTextSecondary, fontSize = 11.sp)
            }
        }

        if (entries.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("아직 기록이 없습니다", color = DcTextSecondary, fontSize = 12.sp,
                modifier = Modifier.padding(start = 32.dp))
        } else {
            val shown = if (ui.showAllBoots) entries else entries.take(5)
            Spacer(Modifier.height(8.dp))
            shown.forEach { e -> BootEntryRow(e) }

            Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp)) {
                if (entries.size > 5) {
                    TextButton(onClick = { viewModel.toggleShowAllBoots() }) {
                        Text(if (ui.showAllBoots) "접기" else "전체 보기 (${entries.size})",
                            color = DcPrimaryBlue, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearBoots() }) {
                    Text("기록 지우기", color = DcTextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BootEntryRow(e: BootLog.Entry) {
    val fmt = remember { SimpleDateFormat("M/d HH:mm", Locale.KOREA) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    val offText = if (e.lastAlive in 1 until e.bootTime) {
        val offMin = (e.bootTime - e.lastAlive) / 60_000
        "직전 동작 ${timeFmt.format(Date(e.lastAlive))} (약 ${offMin}분 꺼짐)"
    } else {
        "직전 동작 시각 알 수 없음"
    }
    Column(modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)) {
        Text("${fmt.format(Date(e.bootTime))} 부팅", color = DcTextPrimary, fontSize = 13.sp)
        Text("$offText · 원인: ${e.reason}", color = DcTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun UpdateContent(state: UpdateState, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = DcPrimaryBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("현재 버전 v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    color = DcTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                val (msg, color) = when (state) {
                    is UpdateState.Idle        -> "버튼을 눌러 새 버전을 확인하세요" to DcTextSecondary
                    is UpdateState.Checking    -> "확인 중..." to DcTextSecondary
                    is UpdateState.UpToDate    -> "최신 버전입니다" to DcSuccessGreen
                    is UpdateState.Available   -> "새 버전 build ${state.release.versionCode} 이(가) 있습니다" to DcWarningOrange
                    is UpdateState.Downloading -> "내려받는 중... ${(state.progress * 100).toInt()}%" to DcTextSecondary
                    is UpdateState.Ready       -> "설치 화면에서 [설치]를 눌러 주세요. 권한 화면이 뜨면 허용 후 돌아와 다시 누르세요" to DcTextSecondary
                    is UpdateState.Error       -> state.message to DcErrorRed
                }
                Text(msg, color = color, fontSize = 11.sp)
            }
        }

        if (state is UpdateState.Downloading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                color = DcPrimaryBlue
            )
        }

        Spacer(Modifier.height(10.dp))
        val label = when (state) {
            is UpdateState.Available   -> "내려받고 설치"
            is UpdateState.Ready       -> "설치 화면 다시 열기"
            is UpdateState.Checking    -> "확인 중..."
            is UpdateState.Downloading -> "내려받는 중..."
            else                       -> "업데이트 확인"
        }
        val busy = state is UpdateState.Checking || state is UpdateState.Downloading
        Button(
            onClick  = onClick,
            enabled  = !busy,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
            shape    = RoundedCornerShape(10.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = DcPrimaryBlue)
        ) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
