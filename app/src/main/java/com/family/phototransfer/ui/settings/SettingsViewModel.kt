package com.family.phototransfer.ui.settings

import android.content.Context
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.network.WifiDeviceScanner
import com.family.phototransfer.scheduler.AutoSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private object Keys {
    val AUTO_SYNC_ENABLED    = booleanPreferencesKey("auto_sync_enabled")
    val SYNC_INTERVAL_HOURS  = intPreferencesKey("sync_interval_hours")
    val SYNC_START_HOUR      = intPreferencesKey("sync_start_hour")         // 0~23
    val SYNC_START_MINUTE    = intPreferencesKey("sync_start_minute")       // ✅ 0 or 30
    val SYNC_FROM_DATE       = stringPreferencesKey("sync_from_date")       // "YYYY-MM-DD", "ALL", "RECENT_3", "RECENT_7"
    val PIXEL_IP             = stringPreferencesKey("pixel_ip")
    val SKIP_DUPLICATES      = booleanPreferencesKey("skip_duplicates")
    val NOTIFY_ON_SEND       = booleanPreferencesKey("notify_on_send")
    val NOTIFY_ON_RECEIVE    = booleanPreferencesKey("notify_on_receive")
    val SYNC_FOLDERS         = stringPreferencesKey("sync_folders")
}

// ✅ 동기화 주기 옵션 (시간 단위로 저장)
enum class SyncIntervalOption(val hours: Int, val label: String) {
    H6  (6,    "6시간"),
    H12 (12,   "12시간"),
    H24 (24,   "24시간"),
    D2  (48,   "2일"),
    D3  (72,   "3일"),
    D7  (168,  "7일");

    companion object {
        fun fromHours(hours: Int): SyncIntervalOption =
            values().firstOrNull { it.hours == hours } ?: H24
    }
}

data class SyncFolder(
    val path:      String,
    val name:      String,
    val enabled:   Boolean = true,
    val itemCount: Int     = 0
)

data class SettingsUiState(
    val autoSyncEnabled:   Boolean              = false,
    val syncInterval:      SyncIntervalOption   = SyncIntervalOption.H24,
    val syncStartHour:     Int                  = 8,
    val syncStartMinute:   Int                  = 0,
    val syncFromDate:      String               = "ALL",
    val pixelIpAddress:    String               = "",
    val skipDuplicates:    Boolean              = true,
    val notifyOnSend:      Boolean              = true,
    val notifyOnReceive:   Boolean              = true,
    val syncFolders:       List<SyncFolder>     = emptyList(),
    val isSyncRunning:     Boolean              = false,
    val showDatePicker:    Boolean              = false,
    val showFolderPicker:  Boolean              = false,
    val availableFolders:  List<SyncFolder>     = emptyList(),
    // ✅ 4번 - 기기 탐색
    val isScanning:        Boolean              = false,
    val discoveredDevices: List<com.family.phototransfer.network.DiscoveredDevice> = emptyList(),
    val scanError:         String?              = null
) {
    val syncIntervalText: String get() = syncInterval.label
    // AM/PM 12시간 표시
    val syncStartAmPm: String get() = if (syncStartHour < 12) "AM" else "PM"
    val syncStartHour12: Int get() = when (syncStartHour) {
        0        -> 12
        in 1..12 -> syncStartHour
        else     -> syncStartHour - 12
    }
    val syncStartTimeText: String get() =
        "%02d:%02d %s".format(syncStartHour12, syncStartMinute, syncStartAmPm)
    val syncFromDateText: String get() = when (syncFromDate) {
        "ALL"      -> "전체"
        "RECENT_3" -> "최근 3일"
        "RECENT_7" -> "최근 7일"
        else       -> syncFromDate
    }
    // ✅ 실제 필터용 날짜 계산 (오늘 기준)
    val syncFromDateResolved: String get() {
        val cal = java.util.Calendar.getInstance()
        return when (syncFromDate) {
            "ALL"      -> "ALL"
            "RECENT_3" -> { cal.add(java.util.Calendar.DAY_OF_YEAR, -2); "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH)+1, cal.get(java.util.Calendar.DAY_OF_MONTH)) }
            "RECENT_7" -> { cal.add(java.util.Calendar.DAY_OF_YEAR, -6); "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH)+1, cal.get(java.util.Calendar.DAY_OF_MONTH)) }
            else       -> syncFromDate
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            context.dataStore.data.catch { emit(emptyPreferences()) }.collect { prefs ->
                _uiState.value = SettingsUiState(
                    autoSyncEnabled   = prefs[Keys.AUTO_SYNC_ENABLED]   ?: false,
                    syncInterval      = SyncIntervalOption.fromHours(prefs[Keys.SYNC_INTERVAL_HOURS] ?: 24),
                    syncStartHour     = prefs[Keys.SYNC_START_HOUR]     ?: 8,
                    syncStartMinute   = prefs[Keys.SYNC_START_MINUTE]   ?: 0,
                    syncFromDate      = prefs[Keys.SYNC_FROM_DATE]      ?: "ALL",
                    pixelIpAddress    = prefs[Keys.PIXEL_IP]            ?: "",
                    skipDuplicates    = prefs[Keys.SKIP_DUPLICATES]     ?: true,
                    notifyOnSend      = prefs[Keys.NOTIFY_ON_SEND]      ?: true,
                    notifyOnReceive   = prefs[Keys.NOTIFY_ON_RECEIVE]   ?: true,
                    syncFolders       = parseFolders(prefs[Keys.SYNC_FOLDERS] ?: ""),
                    availableFolders  = _uiState.value.availableFolders
                )
            }
        }
        loadAvailableFolders()
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            save(Keys.AUTO_SYNC_ENABLED, enabled)
            if (enabled) scheduleAutoSync() else AutoSyncScheduler.cancel(context)
        }
    }

    // ✅ 7번 - 주기를 SyncIntervalOption enum으로 설정
    fun setSyncInterval(option: SyncIntervalOption) {
        viewModelScope.launch {
            save(Keys.SYNC_INTERVAL_HOURS, option.hours)
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync()
        }
    }

    // ✅ 6번 - 시작 시각: 시(hour) 설정
    fun setSyncStartHour(hour: Int) {
        viewModelScope.launch {
            save(Keys.SYNC_START_HOUR, hour.coerceIn(0, 23))
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync()
        }
    }

    // ✅ 6번 - 시작 시각: 분(minute) 설정 (0 or 30)
    fun setSyncStartMinute(minute: Int) {
        viewModelScope.launch {
            save(Keys.SYNC_START_MINUTE, if (minute < 30) 0 else 30)
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync()
        }
    }

    fun setSyncFromDate(date: String) {
        viewModelScope.launch { save(Keys.SYNC_FROM_DATE, date) }
    }

    fun setPixelIp(ip: String) {
        viewModelScope.launch { save(Keys.PIXEL_IP, ip) }
    }

    // ✅ 4번 - 수신기기 WiFi 탐색
    fun scanForDevices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                discoveredDevices = emptyList(),
                scanError = null
            )
            try {
                val scanner = WifiDeviceScanner(context)
                val devices = withContext(Dispatchers.IO) { scanner.scanNetwork() }
                _uiState.value = _uiState.value.copy(
                    isScanning        = false,
                    discoveredDevices = devices,
                    scanError         = if (devices.isEmpty()) "수신 기기를 찾지 못했습니다" else null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanError  = "탐색 실패: ${e.message}"
                )
            }
        }
    }

    // 탐색된 기기 선택 → IP 저장
    fun selectDiscoveredDevice(ip: String) {
        viewModelScope.launch {
            save(Keys.PIXEL_IP, ip)
            _uiState.value = _uiState.value.copy(discoveredDevices = emptyList())
        }
    }

    fun setSkipDuplicates(skip: Boolean) {
        viewModelScope.launch { save(Keys.SKIP_DUPLICATES, skip) }
    }

    fun setNotifyOnSend(enabled: Boolean) {
        viewModelScope.launch { save(Keys.NOTIFY_ON_SEND, enabled) }
    }

    fun setNotifyOnReceive(enabled: Boolean) {
        viewModelScope.launch { save(Keys.NOTIFY_ON_RECEIVE, enabled) }
    }

    // ── 폴더 관리 ─────────────────────────────────────────────
    fun showFolderPicker() { _uiState.value = _uiState.value.copy(showFolderPicker = true) }
    fun hideFolderPicker() { _uiState.value = _uiState.value.copy(showFolderPicker = false) }

    fun addFolders(folders: List<SyncFolder>) {
        val current = _uiState.value.syncFolders.toMutableList()
        folders.forEach { folder ->
            if (current.none { it.path == folder.path }) current.add(folder.copy(enabled = true))
        }
        saveFolders(current)
        _uiState.value = _uiState.value.copy(showFolderPicker = false)
    }

    fun removeFolder(path: String) {
        saveFolders(_uiState.value.syncFolders.filter { it.path != path })
    }

    fun toggleFolderEnabled(path: String) {
        saveFolders(_uiState.value.syncFolders.map {
            if (it.path == path) it.copy(enabled = !it.enabled) else it
        })
    }

    // ── 날짜 피커 ─────────────────────────────────────────────
    fun showDatePicker() { _uiState.value = _uiState.value.copy(showDatePicker = true) }
    fun hideDatePicker() { _uiState.value = _uiState.value.copy(showDatePicker = false) }
    fun setDateAll()     {
        setSyncFromDate("ALL")
        _uiState.value = _uiState.value.copy(showDatePicker = false)
    }

    // ── 즉시 동기화 ───────────────────────────────────────────
    fun runSyncNow() {
        val state = _uiState.value
        _uiState.value = state.copy(isSyncRunning = true)
        AutoSyncScheduler.runOnce(
            context    = context,
            receiverIp = state.pixelIpAddress
        )
        viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.value = _uiState.value.copy(isSyncRunning = false)
        }
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────
    private fun scheduleAutoSync() {
        val state = _uiState.value
        AutoSyncScheduler.schedule(
            context       = context,
            intervalHours = state.syncInterval.hours,
            receiverIp    = state.pixelIpAddress
        )
    }

    private fun saveFolders(folders: List<SyncFolder>) {
        val str = folders.joinToString("|") { "${it.path}::${it.name}::${it.enabled}::${it.itemCount}" }
        viewModelScope.launch { save(Keys.SYNC_FOLDERS, str) }
    }

    private fun parseFolders(raw: String): List<SyncFolder> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|").mapNotNull {
            val p = it.split("::")
            if (p.size >= 2) SyncFolder(
                path      = p[0],
                name      = p[1],
                enabled   = p.getOrNull(2)?.toBoolean() ?: true,
                itemCount = p.getOrNull(3)?.toIntOrNull() ?: 0
            ) else null
        }
    }

    private fun loadAvailableFolders() {
        viewModelScope.launch {
            val folders = withContext(Dispatchers.IO) {
                val result = mutableListOf<SyncFolder>()
                val uri    = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val proj   = arrayOf(
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.DATA
                )
                context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val seen    = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        val folderName = cursor.getString(nameCol) ?: continue
                        val filePath   = cursor.getString(dataCol)  ?: continue
                        val folderPath = File(filePath).parent      ?: continue
                        if (seen.add(folderPath)) {
                            // 해당 폴더 아이템 수 간략 집계
                            val count = result.count { it.path == folderPath }
                            result.add(SyncFolder(path = folderPath, name = folderName, itemCount = count))
                        }
                    }
                }
                // 아이템 수 재집계 (같은 path가 여러 번 나왔을 때)
                val countMap = mutableMapOf<String, Int>()
                context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    while (cursor.moveToNext()) {
                        val fp = File(cursor.getString(dataCol) ?: continue).parent ?: continue
                        countMap[fp] = (countMap[fp] ?: 0) + 1
                    }
                }
                result.map { it.copy(itemCount = countMap[it.path] ?: 0) }.sortedBy { it.name }
            }
            _uiState.value = _uiState.value.copy(availableFolders = folders)
        }
    }

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { prefs -> prefs[key] = value }
    }
}
