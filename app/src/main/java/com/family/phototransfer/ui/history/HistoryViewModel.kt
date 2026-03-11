package com.family.phototransfer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.data.db.TransferDao
import com.family.phototransfer.data.db.TransferRecord
import com.family.phototransfer.data.repository.TransferRepository
import com.family.phototransfer.ui.upload.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class HistoryFilter { ALL, SEND, RECEIVE, FAILED }

data class HistoryRecordUi(
    val id:           Long,
    val fileName:     String,
    val status:       String,       // SUCCESS / FAILED / SKIPPED_DUPLICATE / RECEIVED
    val direction:    String,       // SEND / RECEIVE
    val statusLabel:  String,       // 화면 표시용 한글 레이블
    val sizeText:     String,
    val sourceDevice: String,
    val timeText:     String,
    val dateGroup:    String,
    val rawBytes:     Long
)

data class HistoryUiState(
    val allRecords:      List<HistoryRecordUi> = emptyList(),
    val filteredRecords: List<HistoryRecordUi> = emptyList(),
    val selectedFilter:  HistoryFilter         = HistoryFilter.ALL
) {
    val totalCount:     Int    get() = allRecords.size
    val sendCount:      Int    get() = allRecords.count { it.direction == "SEND" }
    val receiveCount:   Int    get() = allRecords.count { it.direction == "RECEIVE" }
    val failedCount:    Int    get() = allRecords.count { it.status == "FAILED" }
    val totalSizeText:  String get() = formatSize(
        allRecords.filter { it.status == "SUCCESS" || it.status == "RECEIVED" }.sumOf { it.rawBytes }
    )
    val groupedRecords: Map<String, List<HistoryRecordUi>>
        get() = filteredRecords.groupBy { it.dateGroup }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: TransferRepository,
    private val transferDao: TransferDao
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    // ✅ getRecentRecords()로 최근 7일만 표시 (DB는 전체 유지)
    val uiState: StateFlow<HistoryUiState> = combine(
        repository.getRecentRecords(),
        _filter
    ) { records: List<TransferRecord>, filter: HistoryFilter ->
        val uiRecords = records.map { it.toUi() }
        val filtered  = applyFilter(uiRecords, filter)
        HistoryUiState(
            allRecords      = uiRecords,
            filteredRecords = filtered,
            selectedFilter  = filter
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5000),
        initialValue = HistoryUiState()
    )

    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    fun clearAll() {
        viewModelScope.launch { transferDao.deleteAll() }
    }

    private fun applyFilter(records: List<HistoryRecordUi>, filter: HistoryFilter): List<HistoryRecordUi> {
        return when (filter) {
            HistoryFilter.ALL     -> records
            HistoryFilter.SEND    -> records.filter { it.direction == "SEND" }
            HistoryFilter.RECEIVE -> records.filter { it.direction == "RECEIVE" }
            HistoryFilter.FAILED  -> records.filter { it.status == "FAILED" }
        }
    }

    private fun TransferRecord.toUi(): HistoryRecordUi {
        val date    = Date(transferredAt)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault())
        val todayStr = dateFmt.format(Date())
        val dateStr  = dateFmt.format(date)

        // ✅ 방향/상태에 따라 한글 레이블 결정
        val statusLabel = when {
            direction == "RECEIVE" && status == "RECEIVED" -> "수신성공"
            direction == "SEND"   && status == "SUCCESS"   -> "전송성공"
            status == "SKIPPED_DUPLICATE"                  -> "중복"
            status == "FAILED"                             -> "실패"
            else                                           -> status
        }

        return HistoryRecordUi(
            id           = id,
            fileName     = fileName,
            status       = status,
            direction    = direction,
            statusLabel  = statusLabel,
            sizeText     = formatSize(fileSize),
            sourceDevice = sourceDevice,
            timeText     = timeFmt.format(date),
            dateGroup    = if (dateStr == todayStr) "오늘" else dateStr,
            rawBytes     = fileSize
        )
    }
}
