package com.family.phototransfer.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.scheduler.AutoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// DataStore 확장 (앱 전체에서 1개만 생성)
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// DataStore 키
private object Keys {
    val AUTO_SYNC_ENABLED  = booleanPreferencesKey("auto_sync_enabled")
    val SYNC_INTERVAL_IDX  = intPreferencesKey("sync_interval_index")
    val SYNC_RANGE_IDX     = intPreferencesKey("sync_range_index")
    val WIFI_ONLY          = booleanPreferencesKey("wifi_only")
    val PIXEL_IP           = stringPreferencesKey("pixel_ip")
    val SKIP_DUPLICATES    = booleanPreferencesKey("skip_duplicates")
    val SHOW_NOTIFICATION  = booleanPreferencesKey("show_notification")
}

// 동기화 주기 (시간 단위)
private val INTERVAL_HOURS = listOf(1, 6, 12, 24)
private val INTERVAL_TEXTS = listOf("1시간마다", "6시간마다", "12시간마다", "매일")

// 동기화 범위 (일 단위, -1 = 전체)
private val RANGE_DAYS  = listOf(7, 30, 90, -1)
private val RANGE_TEXTS = listOf("7일 이내", "30일 이내", "90일 이내", "전체")

// ── UI 상태 ───────────────────────────────────────────────────
data class SettingsUiState(
    val autoSyncEnabled:  Boolean = false,
    val syncIntervalIndex: Int    = 3,   // 기본: 매일
    val syncRangeIndex:    Int    = 1,   // 기본: 30일
    val wifiOnly:          Boolean = true,
    val pixelIpAddress:    String  = "",
    val skipDuplicates:    Boolean = true,
    val showNotification:  Boolean = true,
    val isSyncRunning:     Boolean = false
) {
    val syncIntervalText: String get() = INTERVAL_TEXTS.getOrElse(syncIntervalIndex) { "매일" }
    val syncRangeText:    String get() = RANGE_TEXTS.getOrElse(syncRangeIndex) { "30일 이내" }
}

// ── ViewModel ─────────────────────────────────────────────────
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // DataStore에서 저장된 설정 불러오기
        viewModelScope.launch {
            context.dataStore.data.catch { emit(emptyPreferences()) }
                .collect { prefs ->
                    _uiState.value = SettingsUiState(
                        autoSyncEnabled   = prefs[Keys.AUTO_SYNC_ENABLED]  ?: false,
                        syncIntervalIndex = prefs[Keys.SYNC_INTERVAL_IDX]  ?: 3,
                        syncRangeIndex    = prefs[Keys.SYNC_RANGE_IDX]     ?: 1,
                        wifiOnly          = prefs[Keys.WIFI_ONLY]           ?: true,
                        pixelIpAddress    = prefs[Keys.PIXEL_IP]            ?: "",
                        skipDuplicates    = prefs[Keys.SKIP_DUPLICATES]     ?: true,
                        showNotification  = prefs[Keys.SHOW_NOTIFICATION]   ?: true
                    )
                }
        }
    }

    // 자동 동기화 ON/OFF
    fun setAutoSync(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            save(Keys.AUTO_SYNC_ENABLED, enabled)
            if (enabled) {
                scheduleAutoSync(context)
            } else {
                AutoSyncScheduler.cancel(context)
            }
        }
    }

    // 동기화 주기 변경
    fun setSyncInterval(index: Int, context: Context) {
        viewModelScope.launch {
            save(Keys.SYNC_INTERVAL_IDX, index)
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync(context)
        }
    }

    // 동기화 범위 변경
    fun setSyncRange(index: Int) {
        viewModelScope.launch { save(Keys.SYNC_RANGE_IDX, index) }
    }

    // WiFi 전용 토글
    fun setWifiOnly(wifiOnly: Boolean, context: Context) {
        viewModelScope.launch {
            save(Keys.WIFI_ONLY, wifiOnly)
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync(context)
        }
    }

    // Pixel IP 저장
    fun setPixelIp(ip: String) {
        viewModelScope.launch { save(Keys.PIXEL_IP, ip) }
    }

    // 중복 건너뜀 설정
    fun setSkipDuplicates(skip: Boolean) {
        viewModelScope.launch { save(Keys.SKIP_DUPLICATES, skip) }
    }

    // 알림 설정
    fun setShowNotification(show: Boolean) {
        viewModelScope.launch { save(Keys.SHOW_NOTIFICATION, show) }
    }

    // 즉시 1회 동기화 실행
    fun runSyncNow(context: Context) {
        val state = _uiState.value
        _uiState.value = state.copy(isSyncRunning = true)

        AutoSyncScheduler.runOnce(
            context    = context,
            receiverIp = state.pixelIpAddress,
            daysBack   = RANGE_DAYS.getOrElse(state.syncRangeIndex) { 30 }
                .let { if (it == -1) 3650 else it }
        )

        // WorkManager는 비동기이므로 UI 상태는 잠시 후 복원
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(isSyncRunning = false)
        }
    }

    // WorkManager 스케줄 등록 헬퍼
    private fun scheduleAutoSync(context: Context) {
        val state = _uiState.value
        val intervalHours = INTERVAL_HOURS.getOrElse(state.syncIntervalIndex) { 24 }
        val daysBack = RANGE_DAYS.getOrElse(state.syncRangeIndex) { 30 }
            .let { if (it == -1) 3650 else it }

        AutoSyncScheduler.schedule(
            context       = context,
            intervalHours = intervalHours,
            receiverIp    = state.pixelIpAddress,
            daysBack      = daysBack,
            wifiOnly      = state.wifiOnly
        )
    }

    // DataStore 저장 헬퍼
    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }
}
