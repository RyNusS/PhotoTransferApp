package com.family.phototransfer.ui.settings

import android.content.Context
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val SYNC_INTERVAL_HOURS  = intPreferencesKey("sync_interval_hours")     // 1~24
    val SYNC_START_HOUR      = intPreferencesKey("sync_start_hour")         // 0~23
    val SYNC_FROM_DATE       = stringPreferencesKey("sync_from_date")       // "YYYY-MM-DD" or "ALL"
    val PIXEL_IP             = stringPreferencesKey("pixel_ip")
    val SKIP_DUPLICATES      = booleanPreferencesKey("skip_duplicates")
    // ✅ 알림을 송신/수신 각각 분리
    val NOTIFY_ON_SEND       = booleanPreferencesKey("notify_on_send")
    val NOTIFY_ON_RECEIVE    = booleanPreferencesKey("notify_on_receive")
    val SYNC_FOLDERS         = stringPreferencesKey("sync_folders")         // pipe-delimited
}

data class SyncFolder(
    val path:      String,
    val name:      String,
    val enabled:   Boolean = true,
    val itemCount: Int     = 0
)

data class SettingsUiState(
    val autoSyncEnabled:   Boolean         = false,
    val syncIntervalHours: Int             = 6,         // 1~24
    val syncStartHour:     Int             = 8,         // 0~23
    val syncFromDate:      String          = "ALL",     // "YYYY-MM-DD" or "ALL"
    val pixelIpAddress:    String          = "",
    val skipDuplicates:    Boolean         = true,
    // ✅ 알림 설정 분리
    val notifyOnSend:      Boolean         = true,
    val notifyOnReceive:   Boolean         = true,
    val syncFolders:       List<SyncFolder> = emptyList(),
    val isSyncRunning:     Boolean         = false,
    val showDatePicker:    Boolean         = false,
    val showFolderPicker:  Boolean         = false,
    val availableFolders:  List<SyncFolder> = emptyList()
) {
    val syncIntervalText: String get() =
        if (syncIntervalHours == 1) "1시간마다" else "${syncIntervalHours}시간마다"
    val syncStartHourText: String get() = "%02d:00".format(syncStartHour)
    val syncFromDateText:  String get() = if (syncFromDate == "ALL") "전체" else syncFromDate
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
                    syncIntervalHours = prefs[Keys.SYNC_INTERVAL_HOURS] ?: 6,
                    syncStartHour     = prefs[Keys.SYNC_START_HOUR]     ?: 8,
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

    fun setSyncIntervalHours(hours: Int) {
        viewModelScope.launch {
            save(Keys.SYNC_INTERVAL_HOURS, hours.coerceIn(1, 24))
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync()
        }
    }

    fun setSyncStartHour(hour: Int) {
        viewModelScope.launch {
            save(Keys.SYNC_START_HOUR, hour.coerceIn(0, 23))
            if (_uiState.value.autoSyncEnabled) scheduleAutoSync()
        }
    }

    fun setSyncFromDate(date: String) {
        viewModelScope.launch { save(Keys.SYNC_FROM_DATE, date) }
    }

    fun setPixelIp(ip: String) {
        viewModelScope.launch { save(Keys.PIXEL_IP, ip) }
    }

    fun setSkipDuplicates(skip: Boolean) {
        viewModelScope.launch { save(Keys.SKIP_DUPLICATES, skip) }
    }

    // ✅ 알림 설정 - 송신/수신 분리
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
            intervalHours = state.syncIntervalHours,
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
