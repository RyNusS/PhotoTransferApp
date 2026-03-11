package com.family.phototransfer.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 컬러 상수 ─────────────────────────────────────────────────
private val BgDark        = Color(0xFF0D1117)
private val CardBg        = Color(0xFF1C2333)
private val CardBorder    = Color(0xFF2D3748)
private val PrimaryBlue   = Color(0xFF2D6BE4)
private val SuccessGreen  = Color(0xFF00C853)
private val WarningOrange = Color(0xFFFF6B35)
private val ErrorRed      = Color(0xFFFF4444)
private val TextPrimary   = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8B9BB4)

enum class HistoryFilter { ALL, SUCCESS, DUPLICATE, FAILED }

// ── 메인 화면 ─────────────────────────────────────────────────
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = CardBg,
            title = { Text("기록 삭제", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("전송 기록을 모두 삭제할까요?\n중복 감지 기록도 초기화됩니다.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); showClearDialog = false }) {
                    Text("삭제", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("취소", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // 1) 헤더
        HistoryHeader(onClearClick = { showClearDialog = true })

        // 2) 통계 요약 카드
        StatsSummaryCard(
            totalCount     = uiState.totalCount,
            successCount   = uiState.successCount,
            duplicateCount = uiState.duplicateCount,
            failedCount    = uiState.failedCount,
            totalSizeText  = uiState.totalSizeText
        )

        // 3) 필터 탭
        HistoryFilterRow(
            selectedFilter = uiState.selectedFilter,
            onFilterSelect = { viewModel.setFilter(it) },
            allCount       = uiState.totalCount,
            successCount   = uiState.successCount,
            duplicateCount = uiState.duplicateCount,
            failedCount    = uiState.failedCount
        )

        // 4) 기록 목록
        if (uiState.filteredRecords.isEmpty()) {
            HistoryEmptyState(filter = uiState.selectedFilter)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.groupedRecords.forEach { (date, records) ->
                    item {
                        Text(
                            text = date,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(records, key = { it.id }) { record ->
                        HistoryRecordItem(record = record)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

// ── 헤더 ──────────────────────────────────────────────────────
@Composable
fun HistoryHeader(onClearClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("전송 기록", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("파일 전송 히스토리", color = TextSecondary, fontSize = 12.sp)
        }
        IconButton(onClick = onClearClick) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "기록 삭제", tint = TextSecondary)
        }
    }
}

// ── 통계 요약 카드 ─────────────────────────────────────────────
@Composable
fun StatsSummaryCard(
    totalCount: Int,
    successCount: Int,
    duplicateCount: Int,
    failedCount: Int,
    totalSizeText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("전체 통계", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(totalSizeText, color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatChip(value = "$totalCount",     label = "전체", color = PrimaryBlue)
                StatChip(value = "$successCount",   label = "성공", color = SuccessGreen)
                StatChip(value = "$duplicateCount", label = "중복", color = WarningOrange)
                StatChip(value = "$failedCount",    label = "실패", color = ErrorRed)
            }
        }
    }
}

@Composable
fun StatChip(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextSecondary, fontSize = 11.sp)
    }
}

// ── 필터 탭 행 ────────────────────────────────────────────────
@Composable
fun HistoryFilterRow(
    selectedFilter: HistoryFilter,
    onFilterSelect: (HistoryFilter) -> Unit,
    allCount: Int,
    successCount: Int,
    duplicateCount: Int,
    failedCount: Int
) {
    val filters = listOf(
        Triple(HistoryFilter.ALL,       "전체 $allCount",       PrimaryBlue),
        Triple(HistoryFilter.SUCCESS,   "성공 $successCount",   SuccessGreen),
        Triple(HistoryFilter.DUPLICATE, "중복 $duplicateCount", WarningOrange),
        Triple(HistoryFilter.FAILED,    "실패 $failedCount",    ErrorRed),
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (filter, label, color) ->
            val isSelected = filter == selectedFilter
            // ✅ clickable을 Box modifier 체인에 직접 추가 (오류 수정)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) color.copy(alpha = 0.2f) else CardBg)
                    .border(1.dp, if (isSelected) color else CardBorder, RoundedCornerShape(20.dp))
                    .clickable { onFilterSelect(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) color else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ── 기록 아이템 ───────────────────────────────────────────────
@Composable
fun HistoryRecordItem(record: HistoryRecordUi) {
    val (icon, iconTint, statusText) = when (record.status) {
        "SUCCESS"           -> Triple(Icons.Default.CheckCircle, SuccessGreen,  "전송 성공")
        "SKIPPED_DUPLICATE" -> Triple(Icons.Default.ContentCopy, WarningOrange, "중복 건너뜀")
        else                -> Triple(Icons.Default.Error,        ErrorRed,      "전송 실패")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .border(0.5.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.fileName,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(statusText, color = iconTint, fontSize = 11.sp)
                Text("·", color = TextSecondary, fontSize = 11.sp)
                Text(record.sizeText, color = TextSecondary, fontSize = 11.sp)
                if (record.sourceDevice.isNotEmpty()) {
                    Text("·", color = TextSecondary, fontSize = 11.sp)
                    Text(record.sourceDevice, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Text(text = record.timeText, color = TextSecondary, fontSize = 11.sp)
    }
}

// ── 빈 상태 ───────────────────────────────────────────────────
@Composable
fun HistoryEmptyState(filter: HistoryFilter) {
    val (icon, message) = when (filter) {
        HistoryFilter.ALL       -> Icons.Default.History      to "전송 기록이 없습니다"
        HistoryFilter.SUCCESS   -> Icons.Default.CheckCircle  to "성공한 전송이 없습니다"
        HistoryFilter.DUPLICATE -> Icons.Default.ContentCopy  to "중복 건너뜀 기록이 없습니다"
        HistoryFilter.FAILED    -> Icons.Default.Error        to "실패한 전송이 없습니다"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = TextSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text("사진을 전송하면 여기에 기록됩니다", color = TextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}
