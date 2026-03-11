package com.family.phototransfer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.family.phototransfer.data.db.TransferDao
import com.family.phototransfer.data.db.TransferRecord
import com.family.phototransfer.ui.upload.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── UI 데이터 모델 ─────────────────────────────────────────────
data class HistoryRecordUi(
    val id: Long,
    val fileName: String,
    val status: String,
    val sizeText: String,
    val sourceDevice: String,
    val timeText: String,
    val dateGroup: String,       // 날짜 그룹 키 (예: "2024년 3월 15일")
    val rawBytes: Long
)

// ── UI 상태 ───────────────────────────────────────────────────
data class HistoryUiState(
    val allRecords: List<HistoryRecordUi>      = emptyList(),
    val filteredRecords: List<HistoryRecordUi> = emptyList(),
    val selectedFilter: HistoryFilter          = HistoryFilter.ALL
) {
    val totalCount:     Int  get() = allRecords.size
    val successCount:   Int  get() = allRecords.count { it.status == "SUCCESS" }
    val duplicateCount: Int  get() = allRecords.count { it.status == "SKIPPED_DUPLICATE" }
    val failedCount:    Int  get() = allRecords.count { it.status == "FAILED" }
    val totalSizeText:  String get() = formatSize(
        allRecords.filter { it.status == "SUCCESS" }.sumOf { it.rawBytes }
    )

    // 날짜별 그룹핑
    val groupedRecords: Map<String, List<HistoryRecordUi>>
        get() = filteredRecords.groupBy { it.dateGroup }
}

// ── ViewModel ─────────────────────────────────────────────────
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transferDao: TransferDao
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        transferDao.getAllRecords(),
        _filter
    ) { records, filter ->
        val uiRecords = records.map { it.toUi() }
        val filtered  = applyFilter(uiRecords, filter)
        HistoryUiState(
            allRecords      = uiRecords,
            filteredRecords = filtered,
            selectedFilter  = filter
        )
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5000),
        initialValue  = HistoryUiState()
    )

    // 필터 변경
    fun setFilter(filter: HistoryFilter) {
        _filter.value = filter
    }

    // 전체 기록 삭제
    fun clearAll() {
        viewModelScope.launch {
            transferDao.deleteAll()
        }
    }

    // 필터 적용
    private fun applyFilter(
        records: List<HistoryRecordUi>,
        filter: HistoryFilter
    ): List<HistoryRecordUi> {
        return when (filter) {
            HistoryFilter.ALL       -> records
            HistoryFilter.SUCCESS   -> records.filter { it.status == "SUCCESS" }
            HistoryFilter.DUPLICATE -> records.filter { it.status == "SKIPPED_DUPLICATE" }
            HistoryFilter.FAILED    -> records.filter { it.status == "FAILED" }
        }
    }

    // DB 엔티티 → UI 모델 변환
    private fun TransferRecord.toUi(): HistoryRecordUi {
        val date     = Date(transferredAt)
        val timeFmt  = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt  = SimpleDateFormat("yyyy년 M월 d일", Locale.getDefault())
        val todayStr = dateFmt.format(Date())
        val dateStr  = dateFmt.format(date)

        return HistoryRecordUi(
            id           = id,
            fileName     = fileName,
            status       = status,
            sizeText     = formatSize(fileSize),
            sourceDevice = sourceDevice,
            timeText     = timeFmt.format(date),
            dateGroup    = if (dateStr == todayStr) "오늘" else dateStr,
            rawBytes     = fileSize
        )
    }
}
